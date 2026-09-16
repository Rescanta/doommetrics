package com.rescanta.doommetrics;

import com.google.gson.Gson;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DelveTotalsTest
{
	private static final double DELTA = 0.01;

	private final TotalsStore store = new TotalsStore(null, new Gson());

	@Test
	public void nothingBankedHasNoRate()
	{
		DelveTotals totals = new DelveTotals();

		assertTrue(totals.isEmpty());
		assertNull(totals.kph());
	}

	/** Twelve deep delves in an hour is twelve an hour. An hour is 6000 ticks. */
	@Test
	public void theRateIsDeepDelvesPerHourOfRunTime()
	{
		DelveTotals totals = new DelveTotals();
		totals.add(12, 6000);

		assertEquals(12.0, totals.kph(), DELTA);
	}

	@Test
	public void runsAccumulate()
	{
		DelveTotals totals = new DelveTotals();
		totals.add(12, 6000);
		totals.add(6, 6000);

		assertEquals(18, totals.deep);
		assertEquals(12_000, totals.ticks);
		assertEquals(9.0, totals.kph(), DELTA);
	}

	/**
	 * Time with no deep delve in it is not a rate of zero, it is no rate at all - the same answer
	 * {@link DelveRun#fullPace} gives. The time is still kept, and starts counting the moment a
	 * later run banks something.
	 */
	@Test
	public void timeWithoutDeepDelvesIsKeptButDoesNotReport()
	{
		DelveTotals totals = new DelveTotals();
		totals.add(0, 3000);

		assertFalse(totals.isEmpty());
		assertNull(totals.kph());

		totals.add(6, 3000);

		// Six deep delves, but charged the full hour including the run that banked none.
		assertEquals(6.0, totals.kph(), DELTA);
	}

	/**
	 * The whole point of the session figure: one run on its own has to read what that run's own
	 * Full pace reads, so a player who does a single run sees one number, not two that disagree.
	 */
	@Test
	public void oneRunReadsTheSameAsThatRunsFullPace()
	{
		// Delves 1-7 in eight minutes, then twelve deep delves at ninety seconds each.
		DelveRun run = new DelveRun(Instant.EPOCH, 1, false);

		for (int level = 1; level <= 7; level++)
		{
			run.complete(level, Instant.EPOCH.plusSeconds(level * 480L / 7), null);
		}

		for (int level = 8; level <= 19; level++)
		{
			run.complete(level, Instant.EPOCH.plusSeconds(480 + (level - 7) * 90L), null);
		}

		// Banked the way the plugin banks it: a clear at a time, each charged its segment.
		DelveRun banking = new DelveRun(Instant.EPOCH, 1, false);
		DelveTotals session = new DelveTotals();

		for (DelveRun.Split split : run.getSplits())
		{
			banking.complete(split.level, split.completedAt, null);
			session.add(split.level >= DelveRun.DEEP_DELVE_LEVEL ? 1 : 0, banking.lastClearTicks());
		}

		assertEquals(DoomFormat.toTicks(run.clearedElapsed()), session.ticks);

		assertEquals(12, session.deep);
		assertEquals(run.fullPace(), session.kph(), DELTA);
	}

	@Test
	public void aTotalSurvivesTheRoundTrip()
	{
		DelveTotals totals = new DelveTotals();
		totals.add(1234, 987_654);

		DelveTotals restored = store.decode(store.encode(totals));

		assertEquals(1234, restored.deep);
		assertEquals(987_654, restored.ticks);
		assertEquals(DelveTotals.VERSION, restored.v);
	}

	@Test
	public void nothingStoredReadsAsNothing()
	{
		assertNull(store.decode(null));
		assertNull(store.decode(""));
	}

	/** A value we cannot parse must start the character over, not wedge the panel. */
	@Test
	public void unreadableStorageIsDiscarded()
	{
		assertNull(store.decode("{not json"));
	}

	/** A negative count is not a rate anybody earned, so it is not carried for a lifetime. */
	@Test
	public void nonsensicalStorageIsDiscarded()
	{
		assertNull(store.decode("{\"v\":1,\"deep\":-3,\"ticks\":600}"));
		assertNull(store.decode("{\"v\":1,\"deep\":3,\"ticks\":-600}"));
	}
}
