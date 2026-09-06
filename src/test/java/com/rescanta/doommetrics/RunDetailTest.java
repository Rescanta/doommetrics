package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * How a run is split up for the detail window: which delve each counter lands on, and when a
 * snapshot is worth taking at all.
 */
public class RunDetailTest
{
	private static final Instant START = Instant.EPOCH;

	private static Instant at(long seconds)
	{
		return START.plusSeconds(seconds);
	}

	/**
	 * The delve being fought when a counter fires is the delve it belongs to, and it stays there
	 * once that delve is banked.
	 */
	@Test
	public void aCounterLandsOnTheDelveThatWasBeingFought()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordCombat(CombatMetric.ZCB_DAMAGE, 120);
		run.complete(1, at(60), null);

		run.recordCombat(CombatMetric.ZCB_DAMAGE, 80);
		run.recordCombat(CombatMetric.BLOOD_BARRAGE_HEAL, 44);
		run.complete(2, at(120), null);

		assertEquals(120, run.combatOn(1).get(CombatMetric.ZCB_DAMAGE));
		assertEquals(80, run.combatOn(2).get(CombatMetric.ZCB_DAMAGE));
		assertEquals(44, run.combatOn(2).get(CombatMetric.BLOOD_BARRAGE_HEAL));

		// Nothing was credited to delve 1's barrage, and asking is not an error.
		assertEquals(0, run.combatOn(1).get(CombatMetric.BLOOD_BARRAGE_HEAL));
	}

	/** The delves have to come to the run, or the chart and the counters disagree. */
	@Test
	public void theDelvesSumToWhatTheRunCounted()
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 5; level++)
		{
			run.recordCombat(CombatMetric.ELDRITCH_PRAYER, level * 10);
			run.complete(level, at(level * 60), null);
		}

		assertEquals(150, run.getCombat().get(CombatMetric.ELDRITCH_PRAYER));

		long summed = 0;

		for (int level = 1; level <= 5; level++)
		{
			summed += run.combatOn(level).get(CombatMetric.ELDRITCH_PRAYER);
		}

		assertEquals(150, summed);
	}

	/** A delve that earned nothing reads zero rather than throwing or handing back null. */
	@Test
	public void aDelveThatEarnedNothingReadsZero()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		for (CombatMetric metric : CombatMetric.values())
		{
			assertEquals(0, run.combatOn(1).get(metric));
			assertEquals(0, run.combatOn(99).get(metric));
		}
	}

	@Test
	public void aSnapshotHoldsEveryClearedDelveAndTheTimeItTook()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.AGS_HEAL, 60);
		run.complete(1, at(90), Duration.ofSeconds(72));
		run.complete(2, at(200), Duration.ofSeconds(95));

		RunDetail detail = RunDetail.of(run);

		assertEquals(2, detail.delves().size());
		assertEquals(2, detail.deepest());
		assertTrue(detail.hasRun());
		assertFalse(detail.isEmpty());

		RunDetail.Delve first = detail.delves().get(0);
		assertEquals(1, first.level);
		assertEquals(Duration.ofSeconds(90), first.segment);
		assertEquals(Duration.ofSeconds(72), first.fight);
		assertEquals(60, first.combat.get(CombatMetric.AGS_HEAL));

		// The restocking, the walk in and the drop down the hole - everything the segment holds
		// that the fight does not, which is the band the time strip fills.
		assertEquals(Duration.ofSeconds(18), first.segment.minus(first.fight));

		RunDetail.Delve second = detail.delves().get(1);
		assertEquals(Duration.ofSeconds(110), second.segment);
		assertEquals(Duration.ofSeconds(15), second.segment.minus(second.fight));
	}

	/**
	 * The delve in progress is left out. Half a delve's counters plotted against whole ones would
	 * read as a collapse at the end of every run.
	 */
	@Test
	public void theDelveInProgressIsNotChartedAndDoesNotCountTowardsTheTotals()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 100);
		run.complete(1, at(60), null);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 30);

		RunDetail detail = RunDetail.of(run);

		assertEquals(1, detail.delves().size());
		assertEquals(100, detail.totals().get(CombatMetric.ZCB_DAMAGE));

		// The run itself still counts it - that is what the side panel and the lifetime bank.
		assertEquals(130, run.getCombat().get(CombatMetric.ZCB_DAMAGE));
	}

	/** A snapshot is a copy, so the client thread carrying on cannot move what Swing is drawing. */
	@Test
	public void aSnapshotDoesNotMoveUnderTheWindow()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.BLOWPIPE_HEAL, 20);
		run.complete(1, at(60), null);

		RunDetail detail = RunDetail.of(run);

		run.enterLevel(1);
		run.recordCombat(CombatMetric.BLOWPIPE_HEAL, 500);

		assertEquals(20, detail.delves().get(0).combat.get(CombatMetric.BLOWPIPE_HEAL));
		assertEquals(20, detail.totals().get(CombatMetric.BLOWPIPE_HEAL));
	}

	@Test
	public void aRunThatBankedNothingIsStillARun()
	{
		RunDetail detail = RunDetail.of(new DelveRun(START, 1, false));

		assertTrue(detail.hasRun());
		assertTrue(detail.isEmpty());
		assertEquals(0, detail.deepest());

		assertFalse(RunDetail.empty().hasRun());
		assertTrue(RunDetail.empty().isEmpty());
	}

	@Test
	public void aDeathIsCarriedThroughToTheWindow()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.end(EndReason.DIED, at(90), 2);

		RunDetail detail = RunDetail.of(run);

		assertTrue(detail.isFinished());
		assertEquals(2, detail.diedOn());
	}

	/** Walking out is not dying, and the delve you walked out on is not one you died on. */
	@Test
	public void aRunWalkedOutOfDiedOnNothing()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.end(EndReason.FINISHED, at(90), -1);

		assertEquals(0, RunDetail.of(run).diedOn());
	}

	@Test
	public void atFindsADelveAndDeclinesToInventOne()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(2, at(120), null);

		RunDetail detail = RunDetail.of(run);

		assertEquals(2, detail.at(2).level);
		assertNull(detail.at(3));
		assertNull(detail.at(0));
	}

	/**
	 * The key is what keeps a four hundred delve run from being taken apart on every tick, so it
	 * has to move on everything a snapshot holds and on nothing else.
	 */
	@Test
	public void theKeyMovesWhenADelveIsBankedAndNotWhenAHealLands()
	{
		DelveRun run = new DelveRun(START, 1, false);
		String before = RunDetail.keyFor(run);

		run.recordCombat(CombatMetric.ZCB_DAMAGE, 200);
		assertEquals("a heal on the delve in progress changes nothing on the chart",
			before, RunDetail.keyFor(run));

		run.complete(1, at(60), null);
		assertNotEquals(before, RunDetail.keyFor(run));

		String banked = RunDetail.keyFor(run);
		run.end(EndReason.DIED, at(90), 2);
		assertNotEquals("a run ending changes how it is drawn", banked, RunDetail.keyFor(run));
	}

	@Test
	public void noRunHasAKeyOfItsOwn()
	{
		assertEquals("", RunDetail.keyFor(null));
		assertNotEquals("", RunDetail.keyFor(new DelveRun(START, 1, false)));
	}
}
