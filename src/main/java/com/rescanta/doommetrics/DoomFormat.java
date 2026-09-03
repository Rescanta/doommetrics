package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.Locale;

/**
 * Pure formatting for every surface (overlay, infobox, panel, chat, history).
 *
 * <p>All methods are static and total: null/negative inputs render as the
 * spec's empty figure rather than throwing.
 */
public final class DoomFormat
{
	private DoomFormat()
	{
	}

	static final long TICK_MILLIS = 600L;

	/** Run elapsed / prediction / linger clock: {@code m:ss} under an hour, {@code h:mm:ss} at/above. */
	public static String elapsed(Duration d)
	{
		if (d == null || d.isNegative())
		{
			return "0:00";
		}
		long totalSeconds = d.getSeconds();
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0)
		{
			return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format(Locale.US, "%d:%02d", minutes, seconds);
	}

	/** Game-reported fight length: {@code m:ss.t}, with an hours field on long delves. */
	public static String fight(Duration d)
	{
		if (d == null || d.isNegative())
		{
			return "-";
		}
		long millis = d.toMillis();
		long hours = millis / 3_600_000;
		long minutes = (millis % 3_600_000) / 60_000;
		long seconds = (millis % 60_000) / 1_000;
		long tenth = (millis % 1_000) / 100;
		if (hours > 0)
		{
			return String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenth);
		}
		return String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenth);
	}

	/** Pace figure: {@code 40.0/hr}, or {@code -} with nothing to average yet. */
	public static String pace(Double delvesPerHour)
	{
		if (delvesPerHour == null || delvesPerHour <= 0 || !Double.isFinite(delvesPerHour))
		{
			return "-";
		}
		return String.format(Locale.US, "%.1f/hr", delvesPerHour);
	}

	/** Compact pace for the infobox square: {@code 40.1}, or {@code -}. */
	public static String compactPace(Double delvesPerHour)
	{
		if (delvesPerHour == null || delvesPerHour <= 0 || !Double.isFinite(delvesPerHour))
		{
			return "-";
		}
		return String.format(Locale.US, "%.1f", delvesPerHour);
	}

	/** Counter figure with thousands separators: {@code 12,470}. */
	public static String count(long amount)
	{
		return String.format(Locale.US, "%,d", amount);
	}

	/** Counter shortened for the infobox square: {@code 1.2k}. Truncates, never rounds up a magnitude. */
	public static String compact(long amount)
	{
		if (amount < 1000)
		{
			return Long.toString(amount);
		}
		if (amount < 10_000)
		{
			double truncated = Math.floor(amount / 100.0) / 10;
			return String.format(Locale.US, "%.1fk", truncated);
		}
		if (amount < 1_000_000)
		{
			return String.format(Locale.US, "%dk", amount / 1000);
		}
		if (amount < 10_000_000)
		{
			double m = amount / 1_000_000.0;
			return String.format(Locale.US, "%.1fm", Math.floor(m * 10) / 10);
		}
		return String.format(Locale.US, "%dm", amount / 1_000_000);
	}

	/**
	 * Run timer shortened for the infobox square: seconds dropped past the hour
	 * ({@code 1h23}), hours alone past ten ({@code 10h}).
	 */
	public static String compactDuration(Duration d)
	{
		if (d == null || d.isNegative())
		{
			return "-";
		}
		long totalSeconds = d.getSeconds();
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		if (hours >= 10)
		{
			return String.format(Locale.US, "%dh", hours);
		}
		if (hours > 0)
		{
			return String.format(Locale.US, "%dh%02d", hours, minutes);
		}
		return elapsed(d);
	}

	/** Prediction row: {@code Reached} once behind, {@code -} with no mean, else the span. */
	public static String prediction(Duration remaining, boolean reached)
	{
		if (reached)
		{
			return "Reached";
		}
		if (remaining == null)
		{
			return "-";
		}
		return elapsed(remaining);
	}

	/** Game ticks for a span. The game counts delves in 600 ms ticks. */
	public static long toTicks(Duration d)
	{
		if (d == null || d.isNegative() || d.isZero())
		{
			return 0;
		}
		return d.toMillis() / TICK_MILLIS;
	}

	/** Span for a tick count. */
	public static Duration tickDuration(long ticks)
	{
		if (ticks <= 0)
		{
			return Duration.ZERO;
		}
		return Duration.ofMillis(ticks * TICK_MILLIS);
	}

	/** Milestone PB cell: {@code h:mm:ss.t}; tick-quantised so the tenth lands on multiples of six. */
	public static String ticks(long tickCount)
	{
		if (tickCount <= 0)
		{
			return "-";
		}
		return fight(tickDuration(tickCount));
	}
}
