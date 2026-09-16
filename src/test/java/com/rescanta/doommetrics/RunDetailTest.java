package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.runelite.api.gameval.ItemID;
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
	 * started, and what the delve died on counted is in the run's tally alone.
	 */
	@Test
	public void theDelveDiedOnIsNotCharted()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(80));
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 40, at(100));
		run.end(EndReason.DIED, at(120), 2);

		RunDetail detail = RunDetail.of(run);
		assertEquals(1, detail.delves().size());
		assertEquals(Duration.ofSeconds(80), detail.at(1).fullTime);
		assertEquals(0, detail.totals().get(CombatMetric.ZCB_DAMAGE));
		assertEquals(40, run.getCombat().get(CombatMetric.ZCB_DAMAGE));
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

	/**
	 * The pile can be seen growing a moment before the chat line that clears the delve, and there is
	 * no column for the drop until that line lands.
	 */
	@Test
	public void aDropIsChartedOnceItsDelveIsBanked()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(60));
		run.sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1);

		assertTrue(RunDetail.of(run).drops().isEmpty());

		run.complete(2, at(120), null);

		List<RunDetail.Drop> drops = RunDetail.of(run).drops();
		assertEquals(1, drops.size());
		assertEquals(2, drops.get(0).level);
		assertEquals(ItemID.AVERNIC_TREADS, drops.get(0).itemId);
		assertEquals("Avernic treads", drops.get(0).name);
	}

	/** Nothing has lost a drop while the run is still going - it is in the pile. */
	@Test
	public void aDropIsKeptWhileTheRunIsGoing()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.sawInPile(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 1);

		assertTrue(RunDetail.of(run).drops().get(0).kept);
	}

	/** Dying loses the pile, so what was in it is drawn as lost - the pet included. */
	@Test
	public void aDeathLosesWhatWasStillInThePileAndThePet()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		run.sawInPile(ItemID.DOMPET, "Dom", run.held(ItemID.DOMPET) + 1);
		run.end(EndReason.DIED, at(90), 2);

		List<RunDetail.Drop> drops = RunDetail.of(run).drops();
		assertFalse("the treads went with the run", drops.get(0).kept);
		assertFalse("so did the pet", drops.get(1).kept);
	}

	/** The pet is only announced in chat, and the claim takes what the run saw land. */
	@Test
	public void aClaimKeepsThePetTheRunSawLand()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.sawInPile(ItemID.DOMPET, "Dom", run.held(ItemID.DOMPET) + 1);
		run.recordLoot(ItemID.DOMPET, "Dom", run.held(ItemID.DOMPET));
		run.end(EndReason.FINISHED, at(90), -1);

		assertTrue(RunDetail.of(run).drops().get(0).kept);
	}

	/** An unknown unique is in the pile while the run goes, and lost once it is over unnamed. */
	@Test
	public void anUnknownUniqueIsLostWhenTheRunEndsWithoutNamingIt()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.uniqueSignalled();

		RunDetail.Drop going = RunDetail.of(run).drops().get(0);
		assertTrue(going.isUnknown());
		assertTrue(going.kept);

		run.end(EndReason.DIED, at(90), 2);
		assertFalse(RunDetail.of(run).drops().get(0).kept);
	}

	/** A claim keeps the drops it reached: a claim of one eye keeps the first eye and not the second. */
	@Test
	public void aClaimKeepsTheDropsItReached()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.sawInPile(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak", 1);
		run.complete(2, at(120), null);
		run.sawInPile(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak", 2);
		run.recordLoot(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak", 1);
		run.end(EndReason.FINISHED, at(150), -1);

		List<RunDetail.Drop> drops = RunDetail.of(run).drops();
		assertTrue(drops.get(0).kept);
		assertFalse(drops.get(1).kept);
	}

	@Test
	public void aRunClaimedInFullKeepsEverything()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		run.recordLoot(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		run.end(EndReason.FINISHED, at(90), -1);

		assertTrue(RunDetail.of(run).drops().get(0).kept);
	}

	/**
	 * A drop can land after the clear that banked its delve, and the window only redraws when the
	 * key moves - so a drop has to move it.
	 */
	@Test
	public void theKeyMovesWhenADropLandsOrIsClaimed()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		String banked = RunDetail.keyFor(run);
		run.sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		assertNotEquals(banked, RunDetail.keyFor(run));

		String landed = RunDetail.keyFor(run);
		run.sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		assertEquals("the same pile sent again changes nothing", landed, RunDetail.keyFor(run));

		run.recordLoot(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		assertNotEquals(landed, RunDetail.keyFor(run));
	}
}
