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

		/** From this delve starting to the next one starting - see {@link DelveRun#fullTime}. */
		final Duration fullTime;

		/** The fight length the game reported, or null if we never saw it. */
		final Duration fight;

		/** What this delve alone earned. Never null; empty for a delve that earned nothing. */
		final CombatTotals combat;

		Delve(int level, Duration fullTime, Duration fight, CombatTotals combat)
		{
			this.level = level;
			this.fullTime = fullTime;
			this.fight = fight;
			this.combat = combat;
		}
	}

	/** A notable drop, on the delve it came off. */
	static final class Drop
	{
		final int level;
		final int itemId;
		final String name;

		/** How many landed on that delve at once - almost always one, and drawn as one icon. */
		final int quantity;

		/** False once the run is over without this having been claimed. Drawn faded. */
		final boolean kept;

		Drop(int level, int itemId, String name, int quantity, boolean kept)
		{
			this.level = level;
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.kept = kept;
		}

		/** A glow mark nothing has named - see {@link DelveRun#uniqueSignalled}. */
		boolean isUnknown()
		{
			return itemId == DelveRun.UNKNOWN_UNIQUE;
		}
	}

	static final String UNKNOWN_UNIQUE_CANDIDATES =
		"Avernic treads, Mokhaiotl cloth or Eye of ayak - or Dom, if it was your first";

	private static final RunDetail EMPTY = new RunDetail(Collections.emptyList(),
		Collections.emptyList(), new CombatTotals(), false, false, 0);

	private final List<Delve> delves;

	/** The run's notable drops, in the order they landed, on cleared delves only. */
	private final List<Drop> drops;

	private final CombatTotals totals;

	/** Whether there is a run behind this at all, delves or not. */
	private final boolean started;

	private final boolean finished;

	/** The delve the player died on, or 0 for a run that is going or ended any other way. */
	private final int diedOn;

	private RunDetail(List<Delve> delves, List<Drop> drops, CombatTotals totals, boolean started,
		boolean finished, int diedOn)
	{
		this.delves = delves;
		this.drops = drops;
		this.totals = totals;
		this.started = started;
		this.finished = finished;
		this.diedOn = diedOn;
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

		for (int i = 0; i < splits.size(); i++)
		{
			DelveRun.Split split = splits.get(i);
			totals.addAll(earned.get(i));
			delves.add(new Delve(split.level, run.fullTime(i), split.fight, earned.get(i)));
		}

		boolean died = run.isFinished() && run.getEndReason() == EndReason.DIED;

		return new RunDetail(Collections.unmodifiableList(delves), dropsOf(run), totals, true,
			run.isFinished(), died ? Math.max(0, run.getDiedOnLevel()) : 0);
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

	/**
	 * The run's drops, once their delve has a column. Kept while the run goes on; afterwards only
	 * if the claim reached the drop's {@code heldAfter}.
	 */
	private static List<Drop> dropsOf(DelveRun run)
	{
		List<Drop> drops = new ArrayList<>();
		int deepest = run.lastLevel();

		for (DelveRun.Landed landed : run.getLanded())
		{
			if (landed.level < 1 || landed.level > deepest)
			{
				continue;
			}

			boolean kept = !run.isFinished() || run.claimed(landed.itemId) >= landed.heldAfter;
			drops.add(new Drop(landed.level, landed.itemId, landed.name, landed.quantity, kept));
		}

		return Collections.unmodifiableList(drops);
	}

	List<Delve> delves()
	{
		return delves;
	}

	/** The run's notable drops, in the order they landed. */
	List<Drop> drops()
	{
		return drops;
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

	int diedOn()
	{
		return diedOn;
	}

	/** The first delve cleared, or 1 for a run that has cleared none. */
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
	 * counted in a wait, whether a wait is going, and the drops. Cheap, since it runs every tick.
	 */
	static String keyFor(DelveRun run)
	{
		if (run == null)
		{
			return "";
		}

		return System.identityHashCode(run) + "|" + run.lastLevel() + "|" + run.isBetweenDelves()
			+ "|" + run.bankedCombatChanges() + "|" + run.isFinished() + "|" + run.getDiedOnLevel()
			+ "|" + run.lootChanges();
	}
}
