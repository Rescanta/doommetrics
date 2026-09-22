package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;

/**
 * One chat line, held as its words and figures so it can be tested and previewed. Worded like the
 * game's delve messages, figures in the game's highlight red.
 */
final class ChatAnnouncement
{
	/** {@code CONSOLE}: RuneLite only recolours the highlight tags on console messages. */
	static final ChatMessageType TYPE = ChatMessageType.CONSOLE;

	/** A stretch of the line: either words, or a figure drawn in the highlight colour. */
	static final class Part
	{
		final String text;
		final boolean figure;

		private Part(String text, boolean figure)
		{
			this.text = text;
			this.figure = figure;
		}
	}

	private final List<Part> parts;

	private ChatAnnouncement(List<Part> parts)
	{
		this.parts = Collections.unmodifiableList(parts);
	}

	/**
	 * Every {@code interval}th delve past the shallow ones, and always exactly the target delve.
	 */
	static boolean isDue(int level, int interval, int target)
	{
		return level == target
			|| interval > 0 && level >= DelveRun.DEEP_DELVE_LEVEL && level % interval == 0;
	}

	/**
	 * The line for a delve just cleared. A target of 0 is none. The game posts the delve's time
	 * itself.
	 */
	static ChatAnnouncement delveCleared(DelveRun run, int level, int target, PaceMode mode)
	{
		return of((level == target
				? "Doom target delve %s reached! Run time: %s, "
				: "Doom delve %s cleared, run time: %s, ")
				+ label(mode) + ": %s.",
			level,
			DoomFormat.duration(run.clearedElapsed()),
			DoomFormat.pace(run.pace(mode)));
	}

	/** The summary of a run that is over. */
	static ChatAnnouncement runEnded(DelveRun run, EndReason reason, PaceMode configured)
	{
		PaceMode mode = run.paceMode(configured);

		return of((reason == EndReason.DIED ? "Doom run over (died)" : "Doom run over")
				+ ": cleared delve %s in %s, " + label(mode) + ": %s.",
			run.lastLevel(),
			DoomFormat.duration(run.clearedElapsed()),
			DoomFormat.pace(run.pace(mode)));
	}

	/** The pace setting's name as it reads part way through a sentence, e.g. {@code deep pace}. */
	private static String label(PaceMode mode)
	{
		return mode.toString().toLowerCase(Locale.US);
	}

	/** Every {@code %s} in the wording is filled by the next figure, in order. */
	private static ChatAnnouncement of(String wording, Object... figures)
	{
		String[] words = wording.split("%s", -1);
		List<Part> parts = new ArrayList<>();
		parts.add(new Part(words[0], false));

		for (int i = 1; i < words.length; i++)
		{
			parts.add(new Part(String.valueOf(figures[i - 1]), true));
			parts.add(new Part(words[i], false));
		}

		return new ChatAnnouncement(parts);
	}

	List<Part> parts()
	{
		return parts;
	}

	/** The line with its colours left out, as it reads. */
	String text()
	{
		StringBuilder text = new StringBuilder();

		for (Part part : parts)
		{
			text.append(part.text);
		}

		return text.toString();
	}

	/** The line tagged for RuneLite, which swaps each tag for the player's chat colour. */
	String formatted()
	{
		ChatMessageBuilder message = new ChatMessageBuilder();

		for (Part part : parts)
		{
			message.append(part.figure ? ChatColorType.HIGHLIGHT : ChatColorType.NORMAL)
				.append(part.text);
		}

		return message.build();
	}
}
