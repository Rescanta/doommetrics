package com.rescanta.doommetrics;

/**
 * Deep delves completed, and the run time they were completed in, summed over any number of runs.
 *
 * <p>Used twice over: once in memory for the session, and once on the RuneScape profile for the
 * character's lifetime. Both are the same sum of the same two numbers, so both answer with the
 * same figure - a session holding a single run reads exactly what that run's Full pace does.
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

	/** Delves completed at or past the deep level - the numerator of the rate. */
	int deep;

	/**
	 * The run time those delves were banked in, in game ticks.
	 *
	 * <p>Added a delve at a time, each clear bringing its segment, so each run contributes the span
	 * from its start through to its last clear, which is the same span every other figure in this
	 * plugin is built on. That charges the shallow delves and the restocking between delves against
	 * the rate, because both are real time spent and a rate that ignored them would flatter you. A
	 * run joined part way into a delve leaves that first, unmeasured, clear out.
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

	/**
	 * Deep delves per hour, or null when no deep delve has been completed yet.
	 *
	 * <p>Time with no deep delve in it is not an answer of zero, it is no answer - the same way
	 * {@link DelveRun#fullPace} declines to report on a run that has not completed one. The time is
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
