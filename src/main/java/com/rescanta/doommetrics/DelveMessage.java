package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * A delve boundary the game announced in chat.
 *
 * <p>Every delve is bracketed by two game messages, and the closing one carries the delve number
 * and the fight length to a tenth of a second. That makes chat the only signal a run needs, and
 * unlike the Doom varplayers it never fires on login.
 *
 * <pre>
 * Delve level: 3                                                     started, delve 3
 * Delve level: 8+ (15)                                               started, delve 15
 * Delve level: 3 duration: 1:05.40. Personal best: 0:37.80           cleared, delve 3
 * Delve level: 2 duration: 0:23.40 (new personal best)               cleared, delve 2
 * Delve level: 8+ (15) duration: 1:30.60. Personal best: 0:48.60     cleared, delve 15
 * </pre>
 *
 * <p>The milestone line "Delve level 1 - 8 duration: ..." has no colon after "level", so it does
 * not match and is ignored.
 */
final class DelveMessage
{
	private static final Pattern PATTERN = Pattern.compile(
		"Delve level: (\\d+)(?:\\+ \\((\\d+)\\))?"
			+ "(?: duration: (?:(\\d+):)?(\\d+):(\\d+)\\.(\\d{2}))?");

	/** Chat colour templates the game embeds, which {@link Text#removeTags} does not strip. */
	private static final Pattern COLOUR_TEMPLATE = Pattern.compile("@[A-Za-z0-9_]+@");

	private final int level;
	private final Duration fight;

	private DelveMessage(int level, Duration fight)
	{
		this.level = level;
		this.fight = fight;
	}

	/** Returns null when the message is not a delve boundary. */
	static DelveMessage parse(String rawMessage)
	{
		if (rawMessage == null)
		{
			return null;
		}

		String message = COLOUR_TEMPLATE.matcher(Text.removeTags(rawMessage)).replaceAll("").trim();
		Matcher matcher = PATTERN.matcher(message);

		if (!matcher.lookingAt())
		{
			return null;
		}

		// Delves past 8 are reported as "8+ (15)", so the number in brackets wins when present.
		int level = matcher.group(2) != null
			? Integer.parseInt(matcher.group(2))
			: Integer.parseInt(matcher.group(1));

		return new DelveMessage(level, matcher.group(4) == null ? null : parseFight(matcher));
	}

	private static Duration parseFight(Matcher matcher)
	{
		long hours = matcher.group(3) == null ? 0 : Long.parseLong(matcher.group(3));

		return Duration.ofMillis(hours * 3_600_000L
			+ Long.parseLong(matcher.group(4)) * 60_000L
			+ Long.parseLong(matcher.group(5)) * 1000L
			+ Long.parseLong(matcher.group(6)) * 10L);
	}

	int getLevel()
	{
		return level;
	}

	/** The fight length for a completion, or null when this message opened the delve instead. */
	Duration getFight()
	{
		return fight;
	}

	boolean isCleared()
	{
		return fight != null;
	}
}
