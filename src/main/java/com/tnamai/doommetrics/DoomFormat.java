package com.tnamai.doommetrics;

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

	static String pace(Double perHour)
	{
		return perHour == null ? "-" : String.format(Locale.US, "%.1f/hr", perHour);
	}
}
