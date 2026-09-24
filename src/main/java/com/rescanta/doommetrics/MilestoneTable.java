package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The lifetime record of how deep this character has been, one row per ten delves, and the running
 * counts the resets card is worked out from. Every figure is a total, so the table grows with how
 * deep the character has been and never with how many runs it has done. Times are in game ticks,
 * the unit the game counts delves in.
 */
class MilestoneTable
{
	/** Rows exist for every tenth delve: 10, 20, 30 and so on. */
	static final int INTERVAL = 10;

	/** How many of the latest times to the target are kept, for the card's recent average. */
	static final int RECENT = 10;

	static final class Row
	{
		int kc;

		/** Ticks from the run start to the clear, or 0 when no trusted time has been banked. */
		int pbTicks;

		/**
		 * Clears by runs watched from delve 1 since the reset counters began. Unlike {@link #kc}
		 * it leaves out rows cleared before them, so it can be set against
		 * {@link MilestoneTable#runs}.
		 */
		int counted;

		/** Clears with an exact time, and those times added up, for the average. */
		int timed;
		long sumTicks;

		boolean hasPb()
		{
			return pbTicks > 0;
		}
	}

	/** The whole table as it is stored. Fields missing from an older value read as zero. */
	static final class Saved
	{
		Map<Integer, Row> rows;

		/** Runs watched from delve 1 since the reset counters began. */
		int runs;

		/** Uniques claimed since the reset counters began. */
		int uniques;

		/** Deaths by the milestone being fought towards: a death on 95 or 100 is under 100. */
		Map<Integer, Integer> deaths;

		/** Which milestone {@link #recent} is for, and its latest exact times, oldest first. */
		int recentTarget;
		List<Integer> recent;
	}

	private final NavigableMap<Integer, Row> rows = new TreeMap<>();
	private final NavigableMap<Integer, Integer> deaths = new TreeMap<>();
	private final List<Integer> recent = new ArrayList<>();
	private int recentTarget;
	private int runs;
	private int uniques;

	static boolean isMilestone(int delve)
	{
		return delve >= INTERVAL && delve % INTERVAL == 0;
	}

	/** The deepest milestone at or below {@code delve}, or 0 when there is none. */
	static int milestoneAtOrBelow(int delve)
	{
		return delve < INTERVAL ? 0 : delve - (delve % INTERVAL);
	}

	/** The milestone a delve counts towards: 91 to 100 all lead to 100. */
	static int milestoneTowards(int delve)
	{
		return Math.max(1, (delve + INTERVAL - 1) / INTERVAL) * INTERVAL;
	}

	/** Banks a clear from a run watched from delve 1. */
	boolean record(int delve, int pbTicks)
	{
		return record(delve, pbTicks, true);
	}

	/**
	 * Banks a clear of a milestone delve.
	 *
	 * @param pbTicks elapsed ticks from the run start to this clear, or 0 if no time can be trusted
	 * @param whole   whether the run was watched from delve 1. Only such a run is counted towards
	 *                the reach rate and the average: a joined run may be a trip counted already,
	 *                and its time is an upper bound, fair as a best it can only fail to beat
	 * @return true if this beat the stored personal best
	 */
	boolean record(int delve, int pbTicks, boolean whole)
	{
		Row row = rows.computeIfAbsent(delve, d -> new Row());
		row.kc++;

		if (whole)
		{
			row.counted++;

			if (pbTicks > 0)
			{
				row.timed++;
				row.sumTicks += pbTicks;
			}
		}

		if (pbTicks <= 0 || (row.hasPb() && pbTicks >= row.pbTicks))
		{
			return false;
		}

		row.pbTicks = pbTicks;
		return true;
	}

	void runStarted()
	{
		runs++;
	}

	/** Takes back a run walked out of before its first clear: it was never an attempt. */
	void runAbandoned()
	{
		runs = Math.max(0, runs - 1);
	}

	/** @param delve the delve the character died on */
	void died(int delve)
	{
		deaths.merge(milestoneTowards(delve), 1, Integer::sum);
	}

	void claimed(int count)
	{
		uniques += Math.max(0, count);
	}

	/**
	 * Keeps an exact time to the milestone being reset at. Changing that milestone starts the list
	 * over, since times to different depths cannot be averaged together.
	 */
	void recordRecent(int target, int ticks)
	{
		if (target != recentTarget)
		{
			recent.clear();
			recentTarget = target;
		}

		recent.add(ticks);

		if (recent.size() > RECENT)
		{
			recent.remove(0);
		}
	}

	/**
	 * Marks milestones up to {@code deepest} as reached, with no kill count or time. Real rows are
	 * left alone.
	 *
	 * @return true if any row was added
	 */
	boolean seedReached(int deepest)
	{
		boolean added = false;

		for (int delve = INTERVAL; delve <= milestoneAtOrBelow(deepest); delve += INTERVAL)
		{
			if (rows.putIfAbsent(delve, new Row()) == null)
			{
				added = true;
			}
		}

		return added;
	}

	/** Swaps in a table read back from disk, keeping this instance's identity. */
	void replaceAll(Saved loaded)
	{
		rows.clear();
		deaths.clear();
		recent.clear();
		recentTarget = 0;
		runs = 0;
		uniques = 0;

		if (loaded == null)
		{
			return;
		}

		if (loaded.rows != null)
		{
			loaded.rows.forEach((delve, row) ->
			{
				if (delve != null && row != null && delve > 0)
				{
					rows.put(delve, row);
				}
			});
		}

		if (loaded.deaths != null)
		{
			loaded.deaths.forEach((delve, count) ->
			{
				if (delve != null && count != null && delve > 0 && count > 0)
				{
					deaths.put(delve, count);
				}
			});
		}

		if (loaded.recent != null && loaded.recentTarget > 0)
		{
			recentTarget = loaded.recentTarget;

			for (Integer ticks : loaded.recent)
			{
				if (ticks != null && ticks > 0)
				{
					recent.add(ticks);
				}
			}

			while (recent.size() > RECENT)
			{
				recent.remove(0);
			}
		}

		runs = Math.max(0, loaded.runs);
		uniques = Math.max(0, loaded.uniques);
	}

	/** Everything the table holds, in the shape it is stored in. */
	Saved save()
	{
		Saved saved = new Saved();
		saved.rows = getRows();
		saved.runs = runs;
		saved.uniques = uniques;
		saved.deaths = Collections.unmodifiableNavigableMap(deaths);
		saved.recentTarget = recentTarget;
		saved.recent = Collections.unmodifiableList(recent);
		return saved;
	}

	/**
	 * The resets card's figures for runs aimed at {@code target}.
	 *
	 * @param target a milestone delve
	 */
	ResetSummary summary(int target, int sessionResets)
	{
		Row row = rows.get(target);
		int diedShort = 0;

		for (int count : deaths.headMap(target, true).values())
		{
			diedShort += count;
		}

		int recentAverage = 0;

		if (recentTarget == target && !recent.isEmpty())
		{
			long sum = 0;

			for (int ticks : recent)
			{
				sum += ticks;
			}

			recentAverage = (int) (sum / recent.size());
		}

		return new ResetSummary(target, runs,
			row == null ? 0 : row.counted,
			diedShort,
			row == null || row.timed == 0 ? 0 : (int) (row.sumTicks / row.timed),
			row == null ? 0 : row.pbTicks,
			recentTarget == target ? recent.size() : 0,
			recentAverage,
			sessionResets,
			uniques);
	}

	NavigableMap<Integer, Row> getRows()
	{
		return Collections.unmodifiableNavigableMap(rows);
	}

	boolean isEmpty()
	{
		return rows.isEmpty();
	}
}
