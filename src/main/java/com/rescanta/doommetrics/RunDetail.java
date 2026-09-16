package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One run broken down delve by delve: what each delve took, and what each delve's gear and
 * spellbook gave back.
 *
 * <p>This is what {@link DelveChart} plots and what {@link RunLegendPanel} lists, and it exists
 * so neither of them ever reads a {@link DelveRun} the client thread is still writing to. Built on
 * the client thread from a run, immutable once built, then handed to Swing.
 *
 * <p>Only cleared delves are in here. The delve being fought has no length yet - its segment does
 * not close until it is cleared - and half a delve's counters plotted against whole ones would
 * read as a collapse at the end of every run. So a run's newest column appears when the delve
 * behind it is banked, which is also the only moment anything already drawn can change.
 *
 * <p>Nothing here is written to disk. The window shows the run you are on or the one you just
 * finished, and both of those are in memory already - see the note in {@link RunHistoryStore} for
 * what is still recorded and why the two are separate concerns.
 */
final class RunDetail
{
	/** One cleared delve. */
	static final class Delve
	{
		/** The delve number the game announced. */
		final int level;

		/**
		 * Wall clock from the previous clear to this one, which is what the pace figures are built
		 * on - so the restocking and the drop down the hole are in here, charged to the delve they
		 * precede.
		 */
		final Duration segment;

		/**
		 * The fight length the game reported, or null if we never saw it. Always at or under
		 * {@link #segment}, and the difference between the two is the time spent not fighting.
		 */
		final Duration fight;

		/** What this delve alone earned. Never null; empty for a delve that earned nothing. */
		final CombatTotals combat;

		Delve(int level, Duration segment, Duration fight, CombatTotals combat)
		{
			this.level = level;
			this.segment = segment;
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

		/**
		 * False once the run is over without this having been claimed: died with it in the pile,
		 * or left it there. It still dropped where it dropped, so it is still drawn - faded.
		 */
		final boolean kept;

		Drop(int level, int itemId, String name, int quantity, boolean kept)
		{
			this.level = level;
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.kept = kept;
		}

		/**
		 * Whether the game only signalled a unique here without saying which - see
		 * {@link DelveRun#uniqueSignalled}. Drawn as a question mark rather than an item.
		 */
		boolean isUnknown()
		{
			return itemId == DelveRun.UNKNOWN_UNIQUE;
		}
	}

	/**
	 * What an unknown unique could have been, for the line under one: the three tradeable uniques,
	 * which always make the hole glow, and the pet, which only does the first time.
	 */
	static final String UNKNOWN_UNIQUE_CANDIDATES =
		"Avernic treads, Mokhaiotl cloth or Eye of ayak - or Dom, if it was your first";

	private static final RunDetail EMPTY = new RunDetail(Collections.emptyList(),
		Collections.emptyList(), new CombatTotals(), false, false, 0);

	private final List<Delve> delves;

	/** The run's notable drops, in the order they landed, on cleared delves only. */
	private final List<Drop> drops;

	private final CombatTotals totals;

	/**
	 * Whether there is a run behind this at all, which is not the same as having a delve in it: a
	 * run walked into and straight back out of is a run that banked nothing, and reads differently
	 * from a session that has not been down there yet.
	 */
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

	/**
	 * Takes a run apart into its delves. Reads the run once, on the thread that owns it, and
	 * copies everything it keeps.
	 */
	static RunDetail of(DelveRun run)
	{
		if (run == null)
		{
			return EMPTY;
		}

		List<DelveRun.Split> splits = run.getSplits();
		List<Delve> delves = new ArrayList<>(splits.size());

		// Summed from the delves rather than read off the run, so the column of totals beside the
		// chart is the sum of exactly the columns on it. The run's own tally also holds whatever
		// the delve in progress has earned, and a total that ran ahead of the plot would have the
		// legend and the chart disagreeing for the length of every delve.
		CombatTotals totals = new CombatTotals();

		for (DelveRun.Split split : splits)
		{
			CombatTotals earned = run.combatOn(split.level).copy();
			totals.addAll(earned);
			delves.add(new Delve(split.level, split.segment, split.fight, earned));
		}

		boolean died = run.isFinished() && run.getEndReason() == EndReason.DIED;

		return new RunDetail(Collections.unmodifiableList(delves), dropsOf(run), totals, true,
			run.isFinished(), died ? Math.max(0, run.getDiedOnLevel()) : 0);
	}

	/**
	 * The run's drops, each marked with whether it was walked out with.
	 *
	 * <p>A drop is only on the chart once the delve it came off is: the pile can be seen growing a
	 * moment before the chat line that clears the delve, and there is no column for it until then.
	 *
	 * <p>A drop is kept while the run is still going - it is in the pile, and nothing has lost it
	 * yet - and afterwards only if the claim reached it. The second eye of a run is kept by a claim
	 * of two and not by a claim of one, which is why each drop carries the count it brought the
	 * pile to rather than a flag for its item.
	 *
	 * <p>An unknown unique is kept the same way, and so is only ever kept while the run is going:
	 * nothing is claimed under its made-up item id, and every way of walking out with it would have
	 * named it and put the real item in its place.
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

	/**
	 * The first delve cleared: 1 for a run watched from the start, later for one joined part way
	 * through, and 1 for a run that has cleared none.
	 */
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
	 * Enough of this to tell one snapshot from the next, so a run that has not banked anything
	 * since the last look is not rebuilt and pushed across again.
	 *
	 * <p>Built out of the run rather than out of a detail, because the point is to decide whether
	 * taking a snapshot is worth it. The deepest delve cleared is enough to cover the figures as
	 * well as the columns: a snapshot holds only cleared delves, and a cleared delve's tally can
	 * never move again, because every source counted is cleared when the delve is.
	 *
	 * <p>Which is what makes this cheap on the tick. A run four hundred delves deep is taken apart
	 * once per clear rather than once per heal, and never at all on the ticks where nothing was
	 * banked - which is almost all of them.
	 *
	 * <p>The drops are the one thing that can move between clears: the pile a delve's drop lands
	 * in can arrive after the clear that banked the delve, and a claim decides which drops were
	 * kept. Both are rare enough that counting them into the key costs nothing.
	 *
	 * <p>The run's identity leads, because none of the rest says which run it is: a run that ends
	 * unseen and is replaced by a new one at the same depth, with nothing banked and no drops,
	 * would otherwise keep the old run's snapshot on screen.
	 */
	static String keyFor(DelveRun run)
	{
		if (run == null)
		{
			return "";
		}

		return System.identityHashCode(run) + "|" + run.lastLevel() + "|" + run.isFinished() + "|"
			+ run.getDiedOnLevel() + "|" + run.lootChanges();
	}
}
