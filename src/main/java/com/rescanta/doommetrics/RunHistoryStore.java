package com.rescanta.doommetrics;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

/**
 * The lifetime history of finished runs, one file per character under
 * {@code .runelite/doommetrics/}.
 *
 * <p>This does not live in the config the way {@link MilestoneStore} does, because config is the
 * wrong shape for it twice over. RuneLite's config service caps a single value at 262144
 * characters, which a per-run history reaches somewhere around four thousand runs and then starts
 * silently dropping writes; and every save rewrites and re-syncs the whole value, so the cost of
 * ending a run would climb with every run already ended. The milestone table stays in config
 * because it is an aggregate that stops growing.
 *
 * <p>Records are appended one JSON object per line rather than held in a single array. An append
 * then costs the same whether the file holds ten runs or ten thousand, and a write torn by a crash
 * costs the last line instead of the whole history. At around sixty bytes a run, twenty thousand
 * runs is about a megabyte, so there is no cap - losing the oldest runs would defeat the point of
 * keeping them.
 *
 * <p>All disk access runs on the shared executor. Reads hand their result back on that thread, so
 * callers marshal onto whichever thread owns what they are updating.
 */
@Slf4j
@Singleton
class RunHistoryStore
{
	static final String DIRECTORY = "doommetrics";

	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final ConfigManager configManager;
	private final File directory;

	@Inject
	RunHistoryStore(Gson gson, ScheduledExecutorService executor, ConfigManager configManager)
	{
		this.gson = gson;
		this.executor = executor;
		this.configManager = configManager;
		this.directory = new File(RuneLite.RUNELITE_DIR, DIRECTORY);
	}

	/** Whether a character is logged in, and so whether there is a file to read or write. */
	boolean hasProfile()
	{
		return configManager.getRSProfileKey() != null;
	}

	/**
	 * Appends one run to the current character's history.
	 *
	 * <p>The file is resolved on the calling thread, so a profile change racing the write cannot
	 * land the record under the wrong character.
	 */
	void append(RunRecord record)
	{
		File file = currentFile();

		if (file == null)
		{
			log.debug("No profile to record a run against, dropping it");
			return;
		}

		String line = encode(record);
		executor.execute(() -> appendLine(file, line));
	}

	/**
	 * Reads the current character's history, oldest run first.
	 *
	 * <p>The callback runs on the executor thread, and gets an empty list when there is no profile
	 * or no file - "nothing recorded yet" and "nobody logged in" look the same to a reader.
	 */
	void load(Consumer<List<RunRecord>> callback)
	{
		File file = currentFile();

		if (file == null)
		{
			executor.execute(() -> callback.accept(Collections.emptyList()));
			return;
		}

		executor.execute(() -> callback.accept(readFile(file)));
	}

	private File currentFile()
	{
		String profile = configManager.getRSProfileKey();
		return profile == null ? null : new File(directory, fileName(profile));
	}

	private void appendLine(File file, String line)
	{
		try
		{
			Files.createDirectories(directory.toPath());
			Files.write(file.toPath(), Collections.singletonList(line), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not append a run to {}", file, e);
		}
	}

	private List<RunRecord> readFile(File file)
	{
		List<RunRecord> records = new ArrayList<>();

		if (!file.exists())
		{
			return records;
		}

		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			String line;
			int skipped = 0;

			while ((line = reader.readLine()) != null)
			{
				RunRecord record = decode(line);

				if (record == null)
				{
					skipped++;
					continue;
				}

				records.add(record);
			}

			if (skipped > 0)
			{
				// One line per run is what buys this: a bad line costs a run, not the history.
				log.warn("Skipped {} unreadable run(s) in {}", skipped, file);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not read run history from {}", file, e);
		}

		return records;
	}

	String encode(RunRecord record)
	{
		// Newlines in the JSON would break the one-record-per-line contract. Gson does not emit
		// them unless asked for pretty printing, which the injected instance is not.
		return gson.toJson(record).replace('\n', ' ');
	}

	/** Parses one line, or null if it holds nothing usable. */
	RunRecord decode(String line)
	{
		if (line == null || line.trim().isEmpty())
		{
			return null;
		}

		try
		{
			RunRecord record = gson.fromJson(line, RunRecord.class);

			// A run that cleared nothing is still a run and is kept - walking straight back out is
			// a real thing that happened. A negative depth is not, so it is a corrupt line.
			return record == null || record.delve < 0 ? null : record;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * The profile key is an account hash for a Jagex account and a display name for an old one, so
	 * it is folded to a form that is safe in a filename either way.
	 */
	static String fileName(String profileKey)
	{
		// Trimming the separators matters as much as folding them: it is what turns a key with
		// nothing usable in it into "unknown" rather than a file called "_".
		String safe = profileKey.toLowerCase()
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("^_+|_+$", "");

		return (safe.isEmpty() ? "unknown" : safe) + ".jsonl";
	}
}
