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
	 * What a predicted time to a target delve reads as: the span itself, {@code Reached} once the
	 * target is behind you, or a dash while there is no deep average to predict from.
	 *
	 * <p>Here rather than at either call site because the overlay and the side panel both draw this
	 * figure from the run themselves, and the one thing they must not do is word the same state
	 * differently.
	 */
	static String prediction(Duration remaining, boolean reached)
	{
		if (reached)
		{
			return "Reached";
		}

		return remaining == null ? "-" : duration(remaining);
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

	/**
	 * A plain count with thousands separated, for anywhere there is room to read the whole number.
	 * A lifetime of delving reaches seven figures of damage, and {@code 1,204,318} is legible where
	 * {@code 1204318} is not.
	 */
	static String count(long value)
	{
		return String.format(Locale.US, "%,d", value);
	}

	/**
	 * A count shortened to three or four characters, for a chart's gridline labels where the
	 * separated form would not fit.
	 *
	 * <p>Deliberately truncating rather than rounding: a gridline is a floor the dots above it are
	 * read against, and {@code 12k} rounded up from 12,600 would sit above dots it is meant to sit
	 * under. One decimal is kept below ten thousand, where the step between gridlines is often
	 * small enough that whole thousands would repeat a label.
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
