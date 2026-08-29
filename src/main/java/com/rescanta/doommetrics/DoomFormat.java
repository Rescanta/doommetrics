package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.Locale;

final class DoomFormat
{
	private DoomFormat()
	{
	}

	static String duration(Duration duration)
	{
		long total = Math.max(0, duration.getSeconds());
		long hours = total / 3600;
		long minutes = (total % 3600) / 60;
		long seconds = total % 60;

		return hours > 0
			? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
			: String.format(Locale.US, "%d:%02d", minutes, seconds);
	}

	/**
	 * Same as {@link #duration} but with tenths, for the per-delve times the game hands us to that
	 * precision. A delve that took 90.6 seconds reads {@code 1:30.6}.
	 */
	static String preciseDuration(Duration duration)
	{
		long tenths = Math.max(0, duration.toMillis() / 100);
		long total = tenths / 10;
		long hours = total / 3600;
		long minutes = (total % 3600) / 60;
		long seconds = total % 60;

		return hours > 0
			? String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths % 10)
			: String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths % 10);
	}

	static String pace(Double perHour)
	{
		return perHour == null ? "-" : String.format(Locale.US, "%.1f/hr", perHour);
	}

	/** A game tick, the unit the milestone table stores its personal bests in. */
	static final long TICK_MILLIS = 600;

	static int toTicks(Duration duration)
	{
		long millis = Math.max(0, duration.toMillis());
		return (int) ((millis + TICK_MILLIS / 2) / TICK_MILLIS);
	}

	/**
	 * A stored personal best, as hours, minutes, seconds and a tenth. Tick resolution means the
	 * tenth only ever lands on a multiple of six, so 152 ticks reads {@code 1:31.2}.
	 */
	static String ticks(int ticks)
	{
		return ticks <= 0 ? "-" : preciseDuration(Duration.ofMillis(ticks * TICK_MILLIS));
	}

	/**
	 * A span of ticks as hours, minutes and seconds. Used for the summed run time behind a rate,
	 * where the tenth {@link #ticks} shows would be noise against a total measured in hours.
	 */
	static String tickDuration(long ticks)
	{
		return ticks <= 0 ? "-" : duration(Duration.ofMillis(ticks * TICK_MILLIS));
	}
}
