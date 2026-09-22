package com.rescanta.doommetrics;

/**
 * Deep delves completed and the run time they took, for the session and the lifetime. The field
 * names are the stored format; {@link #v} versions it.
 */
class DelveTotals
{
	/** The schema this value was written under. */
	static final int VERSION = 1;

	int v = VERSION;

	/** Delves completed at or past the deep level - the numerator of the rate. */
	int deep;

	/**
	 * The run time those delves were banked in, in game ticks, shallow delves and restocking
	 * included.
	 */
	long ticks;

	void add(int deep, long ticks)
	{
		this.deep += deep;
		this.ticks += ticks;
	}

	/** Deep delves per hour, or null until one has been completed. */
	Double kph()
	{
		if (deep <= 0 || ticks <= 0)
		{
			return null;
		}

		double seconds = ticks * DoomFormat.TICK_MILLIS / 1000.0;
		return deep * 3600.0 / seconds;
	}

	/** Whether anything at all has been banked, deep delves or merely time. */
	boolean isEmpty()
	{
		return deep <= 0 && ticks <= 0;
	}
}
