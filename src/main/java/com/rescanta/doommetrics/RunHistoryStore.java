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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

/**
 * Finished runs, one JSON line each, in a file per character under {@code .runelite/doommetrics/}.
 * Not in config: values are size-capped and rewritten whole. Nothing reads it back yet. Disk access
 * runs on the shared executor.
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
		return currentProfile() != null;
	}

	/** The character the client is on, or null when nobody is logged in. */
	String currentProfile()
	{
		return configManager.getRSProfileKey();
	}

	/**
	 * Appends one run to the named character's history - the one who made it, not whoever is logged
	 * in now.
	 */
	CompletableFuture<Void> append(RunRecord record, String profileKey)
	{
		File file = fileFor(profileKey);

		if (file == null)
		{
			log.debug("No profile to record a run against, dropping it");
			return CompletableFuture.completedFuture(null);
		}

		String line = encode(record);
		return CompletableFuture.runAsync(() -> appendLine(file, line), executor);
	}

	/**
	 * Reads the current character's history, oldest first. Calls back on the executor thread, with
	 * an empty list when there is no profile or file.
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
		return fileFor(currentProfile());
	}

	private File fileFor(String profileKey)
	{
		return profileKey == null ? null : new File(directory, fileName(profileKey));
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

	/** The profile key (account hash or display name), made safe for a filename. */
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
