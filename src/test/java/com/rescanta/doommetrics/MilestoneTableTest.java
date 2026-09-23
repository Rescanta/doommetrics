package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MilestoneTableTest
{
	private static final int TICKS_PER_MINUTE = 100;

	@Test
	public void onlyEveryTenthDelveIsAMilestone()
	{
		assertFalse(MilestoneTable.isMilestone(9));
		assertTrue(MilestoneTable.isMilestone(10));
		assertFalse(MilestoneTable.isMilestone(11));
		assertTrue(MilestoneTable.isMilestone(170));

		// Delve 0 is not a row, so nothing rounds down into one.
		assertFalse(MilestoneTable.isMilestone(0));
		assertEquals(0, MilestoneTable.milestoneAtOrBelow(9));
		assertEquals(170, MilestoneTable.milestoneAtOrBelow(172));
		assertEquals(170, MilestoneTable.milestoneAtOrBelow(170));
	}

	/**
	 * The worked example: one run to delve 172 touches every row from 10 to 170, because it cleared
	 * every delve below the one it died on.
	 */
	@Test
	public void aRunToDelve172BanksEveryRowUpTo170()
	{
		MilestoneTable table = new MilestoneTable();

		for (int delve = 1; delve <= 171; delve++)
		{
			if (MilestoneTable.isMilestone(delve))
			{
				table.record(delve, delve * TICKS_PER_MINUTE);
			}
		}

		assertEquals(rows(10, 170), new ArrayList<>(table.getRows().keySet()));
		assertEquals(1, table.getRows().get(10).kc);
		assertEquals(1, table.getRows().get(170).kc);
		assertEquals(170 * TICKS_PER_MINUTE, table.getRows().get(170).pbTicks);
	}

	@Test
	public void killCountRisesEveryTimeAndTheBestTimeOnlyFalls()
	{
		MilestoneTable table = new MilestoneTable();

		assertTrue(table.record(10, 900));
		assertFalse("a slower run must not overwrite the best", table.record(10, 1200));
		assertTrue(table.record(10, 850));
		assertFalse("matching the best is not beating it", table.record(10, 850));

		assertEquals(4, table.getRows().get(10).kc);
		assertEquals(850, table.getRows().get(10).pbTicks);
	}

	/** A run whose start could not be trusted still counts as a clear, it just cannot win. */
	@Test
	public void anUntimedClearStillCountsTowardsKillCount()
	{
		MilestoneTable table = new MilestoneTable();

		assertFalse(table.record(20, 0));
		assertEquals(1, table.getRows().get(20).kc);
		assertFalse(table.getRows().get(20).hasPb());

		assertTrue(table.record(20, 2000));
		assertEquals(2, table.getRows().get(20).kc);
	}

	@Test
	public void seedingFillsReachedRowsWithoutInventingNumbers()
	{
		MilestoneTable table = new MilestoneTable();

		assertTrue(table.seedReached(72));
		assertEquals(rows(10, 70), new ArrayList<>(table.getRows().keySet()));

		for (MilestoneTable.Row row : table.getRows().values())
		{
			assertEquals(0, row.kc);
			assertFalse(row.hasPb());
		}
	}

	@Test
	public void seedingNeverDisturbsRowsThatHoldRealNumbers()
	{
		MilestoneTable table = new MilestoneTable();
		table.record(10, 900);

		assertTrue(table.seedReached(35));

		assertEquals(1, table.getRows().get(10).kc);
		assertEquals(900, table.getRows().get(10).pbTicks);
		assertEquals(0, table.getRows().get(30).kc);
	}

	@Test
	public void seedingIsANoOpBelowTheFirstMilestone()
	{
		MilestoneTable table = new MilestoneTable();

		assertFalse(table.seedReached(9));
		assertTrue(table.isEmpty());
	}

	@Test
	public void aDeathCountsTowardsTheMilestoneBeingFoughtFor()
	{
		assertEquals(10, MilestoneTable.milestoneTowards(1));
		assertEquals(10, MilestoneTable.milestoneTowards(10));
		assertEquals(100, MilestoneTable.milestoneTowards(91));
		assertEquals(100, MilestoneTable.milestoneTowards(100));
		assertEquals(110, MilestoneTable.milestoneTowards(101));
	}

	/** Dying on the target itself is short of it; dying past it is not. */
	@Test
	public void diedShortCountsEveryDeathUpToAndOnTheTarget()
	{
		MilestoneTable table = new MilestoneTable();
		table.died(3);
		table.died(57);
		table.died(100);
		table.died(101);

		assertEquals(3, table.summary(100, 0).diedShort);
		assertEquals(1, table.summary(10, 0).diedShort);
	}

	@Test
	public void theReachRateIsClearsCountedOverRunsStarted()
	{
		MilestoneTable table = new MilestoneTable();

		for (int i = 0; i < 4; i++)
		{
			table.runStarted();
		}

		table.record(100, 60_000);
		table.record(100, 62_000);
		table.record(100, 64_000);

		ResetSummary summary = table.summary(100, 2);
		assertEquals(4, summary.runs);
		assertEquals(3, summary.reached);
		assertEquals(0.75, summary.reachRate(), 1e-9);
		assertEquals(62_000, summary.averageTicks);
		assertEquals(60_000, summary.bestTicks);
		assertEquals(2, summary.sessionResets);
	}

	/**
	 * Kill counts from before the reset counters began are kept but not set against runs, which
	 * those counters never saw.
	 */
	@Test
	public void clearsFromBeforeTheCountersDoNotInflateTheReachRate()
	{
		MilestoneTable.Saved old = new MilestoneTable.Saved();
		MilestoneTable.Row row = new MilestoneTable.Row();
		row.kc = 500;
		row.pbTicks = 55_000;
		old.rows = new java.util.TreeMap<>();
		old.rows.put(100, row);

		MilestoneTable table = new MilestoneTable();
		table.replaceAll(old);
		table.runStarted();
		table.record(100, 61_000);

		ResetSummary summary = table.summary(100, 0);
		assertEquals(501, table.getRows().get(100).kc);
		assertEquals(1, summary.reached);
		assertEquals(1.0, summary.reachRate(), 1e-9);
		assertEquals(61_000, summary.averageTicks);
		assertEquals(55_000, summary.bestTicks);
	}

	/**
	 * A joined run can set a best and adds to the kill count, but it may be a trip already counted,
	 * picked up again after a reset, so it stays out of the reach rate and the average.
	 */
	@Test
	public void aJoinedRunCountsTowardsKcAndBestButNotTheCard()
	{
		MilestoneTable table = new MilestoneTable();
		table.record(100, 70_000, true);
		table.record(100, 65_000, false);

		ResetSummary summary = table.summary(100, 0);
		assertEquals(2, table.getRows().get(100).kc);
		assertEquals(65_000, summary.bestTicks);
		assertEquals(1, summary.reached);
		assertEquals(70_000, summary.averageTicks);
	}

	@Test
	public void theRecentAverageKeepsTheLatestTenAndStartsOverForANewTarget()
	{
		MilestoneTable table = new MilestoneTable();

		for (int i = 1; i <= 12; i++)
		{
			table.recordRecent(100, i * 1_000);
		}

		ResetSummary summary = table.summary(100, 0);
		assertEquals(MilestoneTable.RECENT, summary.recentCount);
		// 3,000 to 12,000.
		assertEquals(7_500, summary.recentTicks);

		assertEquals("another target has no recent times", 0, table.summary(50, 0).recentCount);

		table.recordRecent(50, 4_000);
		assertEquals(1, table.summary(50, 0).recentCount);
		assertEquals("the old target's list is gone", 0, table.summary(100, 0).recentCount);
	}

	@Test
	public void nothingCountedHasNoReachRate()
	{
		assertEquals(-1, new MilestoneTable().summary(100, 0).reachRate(), 1e-9);
	}

		private static List<Integer> rows(int from, int to)
	{
		List<Integer> expected = new ArrayList<>();

		for (int delve = from; delve <= to; delve += MilestoneTable.INTERVAL)
		{
			expected.add(delve);
		}

		return expected;
	}
}
