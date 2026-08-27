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
}
