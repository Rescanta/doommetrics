package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

		run.recordCombat(CombatMetric.ZCB_DAMAGE, 120, Instant.EPOCH);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(60));

		run.recordCombat(CombatMetric.ZCB_DAMAGE, 80, Instant.EPOCH);
		run.recordCombat(CombatMetric.BLOOD_BARRAGE_HEAL, 44, Instant.EPOCH);
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
			if (level > 1)
			{
				run.enterLevel(level, at((level - 1) * 60));
			}

			run.recordCombat(CombatMetric.ELDRITCH_PRAYER, level * 10, Instant.EPOCH);
			run.complete(level, at(level * 60), null);
			assertEquals(level * 10, run.combatOn(level).get(CombatMetric.ELDRITCH_PRAYER));
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
		run.recordCombat(CombatMetric.AGS_HEAL, 60, Instant.EPOCH);
		run.complete(1, at(90), Duration.ofSeconds(72));
		run.complete(2, at(200), Duration.ofSeconds(95));

		RunDetail detail = RunDetail.of(run);

		assertEquals(2, detail.delves().size());
		assertEquals(2, detail.deepest());
		assertTrue(detail.hasRun());
		assertFalse(detail.isEmpty());

		RunDetail.Delve first = detail.delves().get(0);
		assertEquals(1, first.level);
		assertEquals(Duration.ofSeconds(90), first.fullTime);
		assertEquals(Duration.ofSeconds(72), first.fight);
		assertEquals(60, first.combat.get(CombatMetric.AGS_HEAL));

		// Everything the full time holds that the fight does not, which is the band the time strip
		// fills. With no start seen for delve 2, a column runs kill to kill.
		assertEquals(Duration.ofSeconds(18), first.fullTime.minus(first.fight));

		RunDetail.Delve second = detail.delves().get(1);
		assertEquals(Duration.ofSeconds(110), second.fullTime);
		assertEquals(Duration.ofSeconds(15), second.fullTime.minus(second.fight));
	}

	/**
	 * A delve is its kill and the wait after it. Delve 10 killed in 1:30 and gone down from 10
	 * seconds later took 1:40. Delve 11, killed in 1:25 with 35 seconds of restocking and an
	 * eldritch spec before going down, took 2:00 and has the spec. Delve 12, killed in 1:30 and
	 * claimed 15 seconds later, took 1:45 and has nothing.
	 */
	@Test
	public void aDelveIsItsKillAndTheWaitAfterIt()
	{
		DelveRun run = new DelveRun(START, 10, true);
		run.complete(10, at(90), Duration.ofSeconds(90));
		run.enterLevel(11, at(100));
		run.complete(11, at(185), Duration.ofSeconds(85));
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 25, at(200));
		run.enterLevel(12, at(220));
		run.complete(12, at(310), Duration.ofSeconds(90));
		run.end(EndReason.FINISHED, at(325), -1);

		RunDetail detail = RunDetail.of(run);
		assertEquals(Duration.ofSeconds(100), detail.at(10).fullTime);
		assertEquals(Duration.ofSeconds(120), detail.at(11).fullTime);
		assertEquals(Duration.ofSeconds(105), detail.at(12).fullTime);

		assertEquals(25, detail.at(11).combat.get(CombatMetric.ELDRITCH_PRAYER));
		assertEquals(0, detail.at(12).combat.get(CombatMetric.ELDRITCH_PRAYER));

		// The pace figures still charge each wait to the delve after it.
		assertEquals(Duration.ofSeconds(95), run.getSplits().get(1).segment);
	}

	/**
	 * A delve killed and still in its wait runs to its kill for now. The next delve starting ends
	 * the wait, and the snapshot has to be taken again to show it.
	 */
	@Test
	public void theWaitJoinsItsDelveWhenTheNextOneStarts()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		String waiting = RunDetail.keyFor(run);
		assertEquals(Duration.ofSeconds(60), RunDetail.of(run).at(1).fullTime);

		run.enterLevel(2, at(90));

		assertNotEquals(waiting, RunDetail.keyFor(run));
		assertEquals(Duration.ofSeconds(90), RunDetail.of(run).at(1).fullTime);
	}

	/** An announcement sent again is not the delve starting again. */
	@Test
	public void aDelveAnnouncedAgainKeepsTheStartItHad()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(70));
		run.enterLevel(2, at(200));
		run.complete(2, at(250), null);

		RunDetail detail = RunDetail.of(run);
		assertEquals(Duration.ofSeconds(70), detail.at(1).fullTime);
		assertEquals(Duration.ofSeconds(180), detail.at(2).fullTime);
	}

	/**
	 * What is counted in the wait after a kill - a restore still on its way, a spec fired at what is
	 * left before going down - is the killed delve's, and moves a column already drawn.
	 */
	@Test
	public void whatIsCountedInTheWaitBelongsToTheDelveJustKilled()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 12, at(50));
		run.complete(1, at(60), null);
		String killed = RunDetail.keyFor(run);

		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 25, at(70));

		assertNotEquals("the killed delve's column has moved", killed, RunDetail.keyFor(run));
		assertEquals(37, run.combatOn(1).get(CombatMetric.ELDRITCH_PRAYER));
		assertEquals(0, run.combatOn(2).get(CombatMetric.ELDRITCH_PRAYER));

		run.enterLevel(2, at(80));
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 25, at(100));

		assertEquals(37, run.combatOn(1).get(CombatMetric.ELDRITCH_PRAYER));
		assertEquals(25, run.combatOn(2).get(CombatMetric.ELDRITCH_PRAYER));
	}

	/**
	 * A run claimed in the wait after its last kill ends that wait with it, so everything the run
	 * counted is on the chart, and the window's totals are the run's own.
	 */
	@Test
	public void aRunEndedInTheWaitAddsUpToWhatItCounted()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 100, at(30));
		run.complete(1, at(60), null);
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 25, at(70));
		run.end(EndReason.FINISHED, at(95), -1);

		RunDetail detail = RunDetail.of(run);
		assertEquals(Duration.ofSeconds(95), detail.at(1).fullTime);

		for (CombatMetric metric : CombatMetric.values())
		{
			assertEquals(metric.key(), run.getCombat().get(metric), detail.totals().get(metric));
		}
	}

	/**
	 * The delve died on has no kill, so no column: the delve before it ends where the one died on
	 * started, and what the delve died on counted is drawn on it rather than left off.
	 */
	@Test
	public void theDelveDiedOnJoinsTheLastColumn()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 10, at(30));
		run.complete(1, at(60), null);
		run.enterLevel(2, at(80));
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 40, at(100));

		// Still being fought, so not yet.
		assertEquals(10, RunDetail.of(run).totals().get(CombatMetric.ZCB_DAMAGE));

		run.end(EndReason.DIED, at(120), 2);

		RunDetail detail = RunDetail.of(run);
		assertEquals(1, detail.delves().size());
		assertEquals(Duration.ofSeconds(80), detail.at(1).fullTime);
		assertEquals(50, detail.at(1).combat.get(CombatMetric.ZCB_DAMAGE));
		assertEquals(50, detail.totals().get(CombatMetric.ZCB_DAMAGE));
	}

	/**
	 * Picked up in the wait after delve 3, what was counted there has no delve 3 column, so it is
	 * drawn on delve 4 - once delve 4 is cleared, and not while it is being fought.
	 */
	@Test
	public void theWaitARunWasPickedUpInJoinsTheNextDelve()
	{
		DelveRun run = new DelveRun(START, 3, true);
		run.recordCombat(CombatMetric.AGS_HEAL, 25, at(5));
		run.enterLevel(4, at(10));
		run.recordCombat(CombatMetric.AGS_HEAL, 30, at(50));

		assertTrue(RunDetail.of(run).isEmpty());

		run.complete(4, at(100), null);

		RunDetail detail = RunDetail.of(run);
		assertEquals(4, detail.shallowest());
		assertEquals(55, detail.at(4).combat.get(CombatMetric.AGS_HEAL));
		assertEquals(55, detail.totals().get(CombatMetric.AGS_HEAL));
	}

	/** Counted on a delve read one short, then that delve cleared: onto the delve that cleared. */
	@Test
	public void aDelveReadShortJoinsTheOneThatCleared()
	{
		DelveRun run = new DelveRun(START, 11, true);
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 12, at(5));
		run.complete(12, at(60), null);

		assertEquals(12, RunDetail.of(run).at(12).combat.get(CombatMetric.ELDRITCH_PRAYER));
	}

	/**
	 * The delve in progress is left out. Half a delve's counters plotted against whole ones would
	 * read as a collapse at the end of every run.
	 */
	@Test
	public void theDelveInProgressIsNotChartedAndDoesNotCountTowardsTheTotals()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 100, Instant.EPOCH);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(70));
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 30, Instant.EPOCH);

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
		run.recordCombat(CombatMetric.BLOWPIPE_HEAL, 20, Instant.EPOCH);
		run.complete(1, at(60), null);

		RunDetail detail = RunDetail.of(run);

		// Counted in the wait, so onto the very delve the snapshot holds.
		run.recordCombat(CombatMetric.BLOWPIPE_HEAL, 500, Instant.EPOCH);
		assertEquals(520, run.combatOn(1).get(CombatMetric.BLOWPIPE_HEAL));

		assertEquals(20, detail.delves().get(0).combat.get(CombatMetric.BLOWPIPE_HEAL));
		assertEquals(20, detail.totals().get(CombatMetric.BLOWPIPE_HEAL));
	}

	/** A run joined part way through starts its chart at the first delve it saw, not at delve 1. */
	@Test
	public void aJoinedRunStartsAtTheFirstDelveItCleared()
	{
		DelveRun run = new DelveRun(START, 30, true);
		run.complete(30, at(60), null);
		run.enterLevel(31, at(60));
		run.complete(31, at(120), null);

		RunDetail detail = RunDetail.of(run);
		assertEquals(30, detail.shallowest());
		assertEquals(31, detail.deepest());

		assertEquals(1, RunDetail.of(new DelveRun(START, 1, false)).shallowest());
		assertEquals(1, RunDetail.empty().shallowest());
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
	public void anEndedRunIsCarriedThroughToTheWindow()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.end(EndReason.DIED, at(90), 2);

		assertTrue(RunDetail.of(run).isFinished());
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

		run.recordCombat(CombatMetric.ZCB_DAMAGE, 200, Instant.EPOCH);
		assertEquals("a heal on the delve in progress changes nothing on the chart",
			before, RunDetail.keyFor(run));

		run.complete(1, at(60), null);
		assertNotEquals(before, RunDetail.keyFor(run));

		String banked = RunDetail.keyFor(run);
		run.end(EndReason.DIED, at(90), 2);
		assertNotEquals("a run ending changes how it is drawn", banked, RunDetail.keyFor(run));
	}

	/** Two runs that look alike so far are still two runs, and the window has to see the swap. */
	@Test
	public void aDifferentRunInTheSameStateHasADifferentKey()
	{
		DelveRun first = new DelveRun(START, 1, false);
		DelveRun second = new DelveRun(START, 1, false);

		assertNotEquals(RunDetail.keyFor(first), RunDetail.keyFor(second));
	}

	@Test
	public void noRunHasAKeyOfItsOwn()
	{
		assertEquals("", RunDetail.keyFor(null));
		assertNotEquals("", RunDetail.keyFor(new DelveRun(START, 1, false)));
	}

	/** Delves 1-3 watched, the plugin off until part way into delve 7, then delve 8 watched. */
	private static DelveRun resumedMidDelve()
	{
		DelveRun run = new DelveRun(Instant.EPOCH, 1, false);
		run.complete(1, Instant.EPOCH.plusSeconds(60), null);
		run.enterLevel(2, Instant.EPOCH.plusSeconds(70));
		run.complete(2, Instant.EPOCH.plusSeconds(120), null);
		run.enterLevel(3, Instant.EPOCH.plusSeconds(130));
		run.complete(3, Instant.EPOCH.plusSeconds(180), null);
		run.resumeOn(7, Instant.EPOCH.plusSeconds(500), 3);
		run.complete(7, Instant.EPOCH.plusSeconds(580), Duration.ofSeconds(64));
		run.enterLevel(8, Instant.EPOCH.plusSeconds(600));
		run.complete(8, Instant.EPOCH.plusSeconds(700), null);
		return run;
	}

	/**
	 * The time between delve 3's kill and delve 8's start is shared evenly by 4-7, rather than all
	 * of it landing on delve 7, whose start was not seen.
	 */
	@Test
	public void unwatchedDelvesShareTheirTimeEvenly()
	{
		List<RunDetail.Delve> delves = RunDetail.of(resumedMidDelve()).delves();

		assertEquals(8, delves.size());
		assertEquals(Duration.ofSeconds(50), delves.get(2).fullTime);

		for (int level = 4; level <= 7; level++)
		{
			RunDetail.Delve delve = delves.get(level - 1);
			assertEquals(level, delve.level);
			assertEquals(Duration.ofSeconds(105), delve.fullTime);
			assertTrue(delve.estimated);
			assertEquals(level == 7, delve.watched);
		}

		assertEquals(Duration.ofSeconds(64), delves.get(6).fight);
		assertNull(delves.get(3).fight);
		assertEquals(Duration.ofSeconds(100), delves.get(7).fullTime);
		assertFalse(delves.get(7).estimated);
	}

	/** Counters are only plotted where they were counted. */
	@Test
	public void unwatchedDelvesHaveNoCounterColumn()
	{
		List<RunDetail.Delve> watched = RunDetail.of(resumedMidDelve()).watchedDelves();

		assertEquals(5, watched.size());
		assertEquals(7, watched.get(3).level);
	}

	/** Picked up between delves, the next delve's start was seen, so its time is its own. */
	@Test
	public void aDelveSeenStartingAfterAGapKeepsItsOwnTime()
	{
		DelveRun run = new DelveRun(Instant.EPOCH, 1, false);
		run.complete(1, Instant.EPOCH.plusSeconds(60), null);
		run.enterLevel(2, Instant.EPOCH.plusSeconds(70));
		run.complete(2, Instant.EPOCH.plusSeconds(120), null);
		run.enterLevel(3, Instant.EPOCH.plusSeconds(130));
		run.complete(3, Instant.EPOCH.plusSeconds(180), null);
		run.resumeOn(6, Instant.EPOCH.plusSeconds(450), 3);
		run.enterLevel(7, Instant.EPOCH.plusSeconds(480));
		run.complete(7, Instant.EPOCH.plusSeconds(600), null);

		List<RunDetail.Delve> delves = RunDetail.of(run).delves();

		assertEquals(Duration.ofSeconds(100), delves.get(3).fullTime);
		assertEquals(Duration.ofSeconds(100), delves.get(5).fullTime);
		assertEquals(Duration.ofSeconds(120), delves.get(6).fullTime);
		assertFalse(delves.get(6).estimated);
	}
}
