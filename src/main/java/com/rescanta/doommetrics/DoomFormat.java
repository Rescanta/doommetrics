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
	 * The clock shortened for an infobox square: {@code 14:18}, {@code 1h23}, then whole hours past
	 * ten.
	 */
	static String compactDuration(Duration duration)
	{
		long total = Math.max(0, duration.getSeconds());
		long hours = total / 3600;

		if (hours >= 10)
		{
			return hours + "h";
		}

		return hours > 0
			? String.format(Locale.US, "%dh%02d", hours, (total % 3600) / 60)
			: duration(duration);
	}

	/** A predicted time, or a dash while there is nothing to predict from. */
	static String prediction(Duration remaining)
	{
		return remaining == null ? "-" : duration(remaining);
	}

	/** {@link #duration} with tenths: {@code 1:30.6}. */
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

	static String count(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}

	/**
	 * A count shortened for gridline labels. Truncates rather than rounds, since a gridline is a
	 * floor.
	 */
	static String compact(long value)
	{
		long magnitude = Math.abs(value);

		if (magnitude < 1_000)
		{
			return Long.toString(value);
		}

		if (magnitude < 10_000)
		{
			return String.format(Locale.US, "%.1fk", value / 100 / 10d);
		}

		if (magnitude < 1_000_000)
		{
			return value / 1_000 + "k";
		}

		return magnitude < 10_000_000
			? String.format(Locale.US, "%.1fm", value / 100_000 / 10d)
			: value / 1_000_000 + "m";
	}

	static String pace(Double perHour)
	{
		return perHour == null ? "-" : String.format(Locale.US, "%.1f/hr", perHour);
	}

	/** A pace without {@code /hr}, for the infobox square. */
	static String compactPace(Double perHour)
	{
		return perHour == null ? "-" : String.format(Locale.US, "%.1f", perHour);
	}

	/** A game tick, the unit the milestone table stores its personal bests in. */
	static final long TICK_MILLIS = 600;

	static int toTicks(Duration duration)
	{
		long millis = Math.max(0, duration.toMillis());
		return (int) ((millis + TICK_MILLIS / 2) / TICK_MILLIS);
	}

	/** A personal best with tenths: 152 ticks reads {@code 1:31.2}. */
	static String ticks(int ticks)
	{
		return ticks <= 0 ? "-" : preciseDuration(Duration.ofMillis(ticks * TICK_MILLIS));
	}

	/** A span of ticks without tenths, for summed run time. */
	static String tickDuration(long ticks)
	{
		return ticks <= 0 ? "-" : duration(Duration.ofMillis(ticks * TICK_MILLIS));
	}
}
