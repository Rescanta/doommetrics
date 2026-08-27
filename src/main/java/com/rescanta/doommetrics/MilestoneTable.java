package com.rescanta.doommetrics;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The lifetime record of how deep this character has been, one row per ten delves.
 *
 * <p>A row appears the first time its delve is cleared and never goes away again, so the set of
 * rows is the set of milestones you have ever reached. {@code kc} counts the clears. {@code pbTicks}
 * is the shortest time from the start of a run through to that clear, held in game ticks.
 *
 * <p>Times are stored in ticks rather than milliseconds because that is the unit the game itself
 * counts delves in - {@code DOM_LAST_LEVEL_DURATION} reports 151 ticks for the 1:30.60 it announced
 * - so a tick is the finest distinction worth keeping and the numbers stay small on disk.
 */
class MilestoneTable
{
	/** Rows exist for every tenth delve: 10, 20, 30 and so on. */
	static final int INTERVAL = 10;

	static final class Row
	{
		int kc;

		/** Ticks from the run start to the clear, or 0 when no trusted time has been banked. */
		int pbTicks;

		boolean hasPb()
		{
			return pbTicks > 0;
		}
	}

	private final NavigableMap<Integer, Row> rows = new TreeMap<>();

	static boolean isMilestone(int delve)
	{
		return delve >= INTERVAL && delve % INTERVAL == 0;
	}

	/** The deepest milestone at or below {@code delve}, or 0 when there is none. */
	static int milestoneAtOrBelow(int delve)
	{
		return delve < INTERVAL ? 0 : delve - (delve % INTERVAL);
	}

	/**
	 * Banks a clear of a milestone delve.
	 *
	 * @param pbTicks elapsed ticks from the run start to this clear, or 0 if no time can be trusted
	 * @return true if this beat the stored personal best
	 */
	boolean record(int delve, int pbTicks)
	{
		Row row = rows.computeIfAbsent(delve, d -> new Row());
		row.kc++;

		if (pbTicks <= 0 || (row.hasPb() && pbTicks >= row.pbTicks))
		{
			return false;
		}

		row.pbTicks = pbTicks;
		return true;
	}

	/**
	 * Marks every milestone up to {@code deepest} as reached, without inventing a kill count or a
	 * time for any of them. Carries over the delves the game already knows you have been to, so a
	 * returning player does not start from a blank table. Rows that already hold real numbers are
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
	void replaceAll(Map<Integer, Row> loaded)
	{
		rows.clear();

		if (loaded == null)
		{
			return;
		}

		loaded.forEach((delve, row) ->
		{
			if (delve != null && row != null && delve > 0)
			{
				rows.put(delve, row);
			}
		});
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
