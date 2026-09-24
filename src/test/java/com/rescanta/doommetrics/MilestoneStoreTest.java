package com.rescanta.doommetrics;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The encoding is what has to survive a client update, so it is pinned here. Only
 * {@link MilestoneStore#encode} and {@link MilestoneStore#decode} are exercised - neither touches
 * the config manager, so the store can be built without one.
 */
public class MilestoneStoreTest
{
	private final MilestoneStore store = new MilestoneStore(null, new Gson());

	@Test
	public void aTableSurvivesTheRoundTrip()
	{
		MilestoneTable table = new MilestoneTable();
		table.record(10, 900);
		table.record(10, 850);
		table.record(20, 0);
		table.seedReached(35);

		MilestoneTable restored = new MilestoneTable();
		restored.replaceAll(store.decode(store.encode(table)));

		assertEquals(table.getRows().keySet(), restored.getRows().keySet());

		assertEquals(2, restored.getRows().get(10).kc);
		assertEquals(850, restored.getRows().get(10).pbTicks);

		// Cleared once, but with no time behind it.
		assertEquals(1, restored.getRows().get(20).kc);
		assertFalse(restored.getRows().get(20).hasPb());

		// Seeded: reached, nothing measured.
		assertEquals(0, restored.getRows().get(30).kc);
		assertFalse(restored.getRows().get(30).hasPb());
	}

	@Test
	public void delveNumbersComeBackAsNumbersNotText()
	{
		MilestoneTable table = new MilestoneTable();
		table.record(170, 17_200);

		MilestoneTable restored = new MilestoneTable();
		restored.replaceAll(store.decode(store.encode(table)));

		assertTrue(restored.getRows().containsKey(170));
		assertEquals(170, restored.getRows().firstKey().intValue());
		assertEquals(17_200, restored.getRows().get(170).pbTicks);
	}

	@Test
	public void nothingStoredReadsAsNothing()
	{
		assertNull(store.decode(null));
		assertNull(store.decode(""));
	}

	/** A value we cannot parse must start the table over, not wedge the panel. */
	@Test
	public void unreadableStorageIsDiscarded()
	{
		assertNull(store.decode("{not json"));
	}

	@Test
	public void anEmptyTableEncodesAndReadsBackEmpty()
	{
		MilestoneTable restored = new MilestoneTable();
		restored.replaceAll(store.decode(store.encode(new MilestoneTable())));

		assertTrue(restored.isEmpty());
	}

	@Test
	public void theResetCountersSurviveTheRoundTrip()
	{
		MilestoneTable table = new MilestoneTable();
		table.runStarted();
		table.runStarted();
		table.record(100, 61_000);
		table.recordRecent(100, 61_000);

		MilestoneTable restored = new MilestoneTable();
		restored.replaceAll(store.decode(store.encode(table)));

		ResetSummary summary = restored.summary(100, 0);
		assertEquals(2, summary.runs);
		assertEquals(1, summary.reached);
		assertEquals(61_000, summary.averageTicks);
		assertEquals(1, summary.recentCount);
		assertEquals(61_000, summary.recentTicks);
	}

	/** A value written before the reset counters existed reads back with them at zero. */
	@Test
	public void aTableFromBeforeTheResetCountersStillReads()
	{
		MilestoneTable restored = new MilestoneTable();
		restored.replaceAll(store.decode("{\"rows\":{\"10\":{\"kc\":7,\"pbTicks\":900}}}"));

		assertEquals(7, restored.getRows().get(10).kc);
		assertEquals(900, restored.getRows().get(10).pbTicks);
		assertEquals(0, restored.getRows().get(10).counted);
		assertEquals(0, restored.summary(10, 0).runs);
	}
}
