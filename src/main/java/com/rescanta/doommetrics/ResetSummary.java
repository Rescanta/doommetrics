package com.rescanta.doommetrics;

/**
 * How runs aimed at one milestone have gone, for the side panel's resets card. Worked out from the
 * milestone table's running counts, so every figure is since those counts began. Immutable.
 */
final class ResetSummary
{
	/** The milestone the runs are aimed at: the target delve, rounded down to a row. */
	final int target;

	/** Runs started. */
	final int runs;

	/** Of those, how many cleared the target. */
	final int reached;

	/** Ticks to the target on an average run and on the best one, or 0 for none yet. */
	final int averageTicks;
	final int bestTicks;

	/** How many of the latest exact times {@link #recentTicks} averages, up to ten. */
	final int recentCount;
	final int recentTicks;

	/** Targets cleared since the client started. */
	final int sessionResets;

	ResetSummary(int target, int runs, int reached, int averageTicks, int bestTicks,
		int recentCount, int recentTicks, int sessionResets)
	{
		this.target = target;
		this.runs = runs;
		this.reached = reached;
		this.averageTicks = averageTicks;
		this.bestTicks = bestTicks;
		this.recentCount = recentCount;
		this.recentTicks = recentTicks;
		this.sessionResets = sessionResets;
	}

	/** The share of runs that reached the target, from 0 to 1, or -1 before any run is counted. */
	double reachRate()
	{
		return runs <= 0 ? -1 : Math.min(1, (double) reached / runs);
	}

	/** Enough to tell one repaint from the next. */
	String key()
	{
		return target + "|" + runs + "|" + reached + "|" + averageTicks + "|" + bestTicks + "|"
			+ recentCount + "|" + recentTicks + "|" + sessionResets;
	}
}
