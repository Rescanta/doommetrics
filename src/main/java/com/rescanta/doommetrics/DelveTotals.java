package com.rescanta.doommetrics;

/**
 * Deep delves banked, and the run time they were banked in, summed over any number of runs.
 *
 * <p>Used twice over: once in memory for the session, and once on the RuneScape profile for the
 * character's lifetime. Both are the same sum of the same two numbers, so both answer with the
 * same figure - a session holding a single run reads exactly what that run's Run pace does.
 *
 * <p>The lifetime copy is written to config, so the field names here are the stored format:
 * renaming one silently drops that number out of every character's saved total. {@link #v} exists
 * so a later change can tell old values from new ones without guessing.
 */
class DelveTotals
{
	/** The schema this value was written under. */
	static final int VERSION = 1;

	int v = VERSION;

	/** Delves banked at or past the deep level - the numerator of the rate. */
	int deep;

	/**
	 * The run time those delves were banked in, in game ticks.
	 *
	 * <p>Each run contributes the span from its start through to its last clear, which is the same
	 * span every other figure in this plugin is built on. That charges the shallow warm-up and the
	 * restocking between delves against the rate, because both are real time spent and a rate that
	 * ignored them would flatter you.
	 *
	 * <p>Ticks rather than millis because a tick is the finest distinction the game itself draws,
	 * and it keeps a lifetime of runs to a number that stays small on disk.
	 */
	long ticks;

	void add(int deep, long ticks)
	{
		this.deep += deep;
		this.ticks += ticks;
	}

	/** This total with one more run's worth added, leaving this one untouched. */
	DelveTotals plus(int deep, long ticks)
	{
		DelveTotals sum = new DelveTotals();
		sum.deep = this.deep + deep;
		sum.ticks = this.ticks + ticks;
		return sum;
	}

	/**
	 * Deep delves per hour, or null when nothing has been banked yet.
	 *
	 * <p>Time with no deep delve in it is not an answer of zero, it is no answer - the same way
	 * {@link DelveRun#runPace} declines to report on a run that has not banked one. The time is
	 * still kept, and starts counting against the rate as soon as a later run banks something.
	 */
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
