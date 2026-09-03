package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the game's delve bracket messages.
 *
 * <p>Every delve is bracketed by two game messages on {@code GAMEMESSAGE}: an open
 * ({@code Delve level: 3}) and a close carrying the fight length
 * ({@code Delve level: 3 duration: 1:05.40 ...}). Chat is the signal a run is driven by:
 * it is the only thing carrying both the delve number and the game's own timing, and unlike the
 * varplayers it never fires on login.
 *
 * <p>Past delve 8 the real number moves into brackets ({@code Delve level: 8+ (15)}) and the
 * leading number sticks at 8 — the bracketed one wins whenever present.
 */
public final class DelveChatParser
{
	private DelveChatParser()
	{
	}

	private static final Pattern BOUNDARY = Pattern.compile(
		"Delve level: (\\d+)(?:\\+ \\((\\d+)\\))?(?: duration: (?:(\\d+):)?(\\d+):(\\d+)\\.(\\d{2}))?");

	/** One parsed boundary: an open (fight empty) or a close (fight present). */
	public static final class DelveBoundary
	{
		private final int level;
		private final Duration fight;

		DelveBoundary(int level, Duration fight)
		{
			this.level = level;
			this.fight = fight;
		}

		/** Real delve number: the bracketed one past delve 8, else the leading one. */
		public int getLevel()
		{
			return level;
		}

		/** Game-reported fight length, or null for an open. */
		public Duration getFight()
		{
			return fight;
		}

		public boolean isClear()
		{
			return fight != null;
		}
	}

	/**
	 * Parses a raw chat message. Empty means "not a delve boundary" — including the milestone
	 * summary ({@code Delve level 1 - 8 duration: ...}, no colon), the running total
	 * ({@code Total duration: ...}), lifetime chatter ({@code Deep delves completed: ...}),
	 * our own {@code [Doom]} echoes, and null/empty input.
	 */
	public static Optional<DelveBoundary> parse(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return Optional.empty();
		}
		if (raw.contains("[Doom]"))
		{
			return Optional.empty();
		}
		String stripped = raw.replaceAll("<[^>]*>", "").replaceAll("@[A-Za-z0-9_]+@", "").trim();
		Matcher m = BOUNDARY.matcher(stripped);
		if (!m.lookingAt())
		{
			return Optional.empty();
		}
		int level;
		try
		{
			level = m.group(2) != null ? Integer.parseInt(m.group(2)) : Integer.parseInt(m.group(1));
		}
		catch (NumberFormatException e)
		{
			return Optional.empty();
		}
		if (level <= 0 || level > DoomMetricsConfig.MAX_DELVE)
		{
			return Optional.empty();
		}
		if (m.group(4) == null)
		{
			return Optional.of(new DelveBoundary(level, null));
		}
		long hours = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
		long minutes = Long.parseLong(m.group(4));
		long seconds = Long.parseLong(m.group(5));
		long centis = Long.parseLong(m.group(6));
		Duration fight = Duration.ofHours(hours)
			.plusMinutes(minutes)
			.plusSeconds(seconds)
			.plusMillis(centis * 10);
		return Optional.of(new DelveBoundary(level, fight));
	}
}
