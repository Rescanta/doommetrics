package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;

/**
 * One line the plugin posts to chat, held as the words and figures it is made of rather than as
 * the tagged string RuneLite is handed - so it can be read in a test and drawn by the preview
 * harness, as well as sent.
 *
 * <p>Worded the way the game's own delve messages are: the words in the normal chat colour and
 * each figure in the highlight red, as in {@code Deep delves completed: 6,777}. Every line opens
 * with Doom, so it is not taken for one of the game's.
 *
 * <p>The red is RuneLite's game message highlight rather than a colour of our own, so it is the
 * game's red out of the box, is adjusted for the transparent chatbox, and follows the player if
 * they have recoloured it in the Chat Color plugin.
 */
final class ChatAnnouncement
{
	/**
	 * What the line is sent as. {@code CONSOLE}, not {@code GAMEMESSAGE}: RuneLite keeps the game
	 * message colours - the words' colour and the highlight red - against {@code CONSOLE} alone, so
	 * a {@code GAMEMESSAGE} has neither tag swapped, the client drops them, and every figure comes
	 * out in the plain white of the words.
	 */
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
	 * Whether clearing {@code level} is announced: every {@code interval}th delve once past the
	 * shallow ones, so an interval of 5 reports at delve 10, 15, 20 and so on.
	 *
	 * <p>Landing on the target delve is reported whatever the interval says, including when the
	 * messages are switched off altogether. A target of 52 read against an interval of 5 would
	 * otherwise pass in silence, and the one delve of a run you asked to be told about is a poor
	 * one to leave unannounced. The delves in between it are still the interval's business.
	 *
	 * <p>Exactly the target rather than at or past it, so a run joined already deeper than the
	 * target does not open with an announcement of an arrival nobody watched.
	 */
	static boolean isDue(int level, int interval, int target)
	{
		return level == target
			|| interval > 0 && level >= DelveRun.DEEP_DELVE_LEVEL && level % interval == 0;
	}

	/**
	 * The line for a delve just cleared, worded as the target when it is the one being aimed for.
	 * A target of 0 is none.
	 *
	 * <p>The delve's own time is left out: the game posts it in the line just above.
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

	/**
	 * The summary of a run that is over. A death is marked, but not the delve it came on, which is
	 * the one past the delve the line already gives.
	 */
	static ChatAnnouncement runEnded(DelveRun run, EndReason reason, PaceMode mode)
	{
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
