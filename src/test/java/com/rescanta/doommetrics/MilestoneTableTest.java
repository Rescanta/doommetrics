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
