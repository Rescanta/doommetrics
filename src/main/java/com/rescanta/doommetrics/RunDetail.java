package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of a run, delve by delve, for the chart and legend. Built on the client
 * thread. A delve is its kill and the wait after it; only killed delves have a column.
 */
final class RunDetail
{
	/** One cleared delve. */
	static final class Delve
	{
		/** The delve number the game announced. */
		final int level;

		/** From this delve starting to the next one starting - see {@link DelveRun#timeline}. */
		final Duration fullTime;

		/** The fight length the game reported, or null if we never saw it. */
		final Duration fight;

		/** What this delve alone earned. Never null; empty for a delve that earned nothing. */
		final CombatTotals combat;

		/** False for a delve cleared while the plugin was off, which has a time and nothing else. */
		final boolean watched;

		/** Whether {@link #fullTime} is an even share of a stretch nobody watched. */
		final boolean estimated;

		Delve(int level, Duration fullTime, Duration fight, CombatTotals combat, boolean watched,
			boolean estimated)
		{
			this.level = level;
			this.fullTime = fullTime;
			this.fight = fight;
			this.combat = combat;
			this.watched = watched;
			this.estimated = estimated;
		}
	}

	private static final RunDetail EMPTY = new RunDetail(Collections.emptyList(),
		new CombatTotals(), false, false);

	private final List<Delve> delves;

	private final CombatTotals totals;

	/** Whether there is a run behind this at all, delves or not. */
	private final boolean started;

	private final boolean finished;

	private RunDetail(List<Delve> delves, CombatTotals totals, boolean started, boolean finished)
	{
		this.delves = delves;
		this.totals = totals;
		this.started = started;
		this.finished = finished;
	}

	/** What the window shows before this session has a run in it. */
	static RunDetail empty()
	{
		return EMPTY;
	}

	/** Takes a run apart on the thread that owns it, copying everything it keeps. */
	static RunDetail of(DelveRun run)
	{
		if (run == null)
		{
			return EMPTY;
		}

		List<DelveRun.Split> splits = run.getSplits();
		List<Delve> delves = new ArrayList<>(splits.size());

		// Summed from the columns, not read off the run, so the totals match the plot exactly.
		CombatTotals totals = new CombatTotals();

		List<CombatTotals> earned = new ArrayList<>(splits.size());

		for (DelveRun.Split split : splits)
		{
			earned.add(run.combatOn(split.level).copy());
		}

		for (int level : run.combatLevels())
		{
			int column = columnFor(splits, level, run.isFinished());

			if (column >= 0 && splits.get(column).level != level)
			{
				earned.get(column).addAll(run.combatOn(level));
			}
		}

		for (CombatTotals each : earned)
		{
			totals.addAll(each);
		}

		int column = 0;

		for (DelveRun.DelveTime time : run.timeline())
		{
			delves.add(time.split == null
				? new Delve(time.level, time.fullTime, null, new CombatTotals(), false, true)
				: new Delve(time.level, time.fullTime, time.split.fight, earned.get(column++), true,
					time.estimated));
		}

		return new RunDetail(Collections.unmodifiableList(delves), totals, true, run.isFinished());
	}

	/**
	 * The column what was counted on {@code level} is drawn in, or -1 for none. A delve with no
	 * clear goes on the next cleared one, or the last; the delve being fought waits for its own.
	 */
	private static int columnFor(List<DelveRun.Split> splits, int level, boolean finished)
	{
		if (splits.isEmpty())
		{
			return -1;
		}

		for (int i = 0; i < splits.size(); i++)
		{
			if (splits.get(i).level >= level)
			{
				return i;
			}
		}

		return finished ? splits.size() - 1 : -1;
	}

	List<Delve> delves()
	{
		return delves;
	}

	/** What the whole run earned - the sum of every delve's tally. */
	CombatTotals totals()
	{
		return totals;
	}

	boolean isEmpty()
	{
		return delves.isEmpty();
	}

	/** Whether this holds a run at all, banked delves or not. */
	boolean hasRun()
	{
		return started;
	}

	boolean isFinished()
	{
		return finished;
	}

	/** The delves with counters to plot: every one but those cleared while the plugin was off. */
	List<Delve> watchedDelves()
	{
		List<Delve> watched = new ArrayList<>(delves.size());

		for (Delve delve : delves)
		{
			if (delve.watched)
			{
				watched.add(delve);
			}
		}

		return watched;
	}

	int shallowest()
	{
		return delves.isEmpty() ? 1 : delves.get(0).level;
	}

	/** The deepest delve cleared, or 0 for a run that has cleared none. */
	int deepest()
	{
		return delves.isEmpty() ? 0 : delves.get(delves.size() - 1).level;
	}

	/** The delve at {@code level}, or null if the run never cleared it. */
	Delve at(int level)
	{
		for (Delve delve : delves)
		{
			if (delve.level == level)
			{
				return delve;
			}
		}

		return null;
	}

	/**
	 * Changes whenever a new snapshot would differ: the run itself, its deepest clear, anything
	 * counted in a wait, and whether a wait is going. Cheap, since it runs every tick.
	 */
	static String keyFor(DelveRun run)
	{
		if (run == null)
		{
			return "";
		}

		return System.identityHashCode(run) + "|" + run.lastLevel() + "|" + run.isBetweenDelves()
			+ "|" + run.bankedCombatChanges() + "|" + run.isFinished() + "|" + run.getDiedOnLevel();
	}
}
