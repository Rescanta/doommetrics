package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DelveRunTest
{
	private static final double DELTA = 0.01;
	private static final Instant START = Instant.EPOCH;

	/**
	 * The reference run: delves 1-7 in 8:00, delve 8 in 2:00, then twelve delves at 1:30 each
	 * for a 28:00 total ending on delve 20.
	 */
	private static DelveRun referenceRun()
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 6; level++)
		{
			run.complete(level, at(level * 60), null);
		}

		run.complete(7, at(480), null);
		run.complete(8, at(600), null);

		for (int level = 9; level <= 20; level++)
		{
			run.complete(level, at(600 + (level - 8) * 90), null);
		}

		return run;
	}

	private static Instant at(long seconds)
	{
		return START.plusSeconds(seconds);
	}

	@Test
	public void segmentsAreContiguousAndSumToTheTotal()
	{
		DelveRun run = referenceRun();

		assertEquals(20, run.lastLevel());
		assertEquals(21, run.currentLevel());
		assertEquals(Duration.ofMinutes(28), run.clearedElapsed());

		long summed = run.getSplits().stream().mapToLong(s -> s.segment.getSeconds()).sum();
		assertEquals(run.clearedElapsed().getSeconds(), summed);
	}

	/**
	 * The delve number comes from the game, not from counting clears, so a run joined part way
	 * through still reports where you actually are.
	 */
	@Test
	public void currentLevelTracksTheDelveTheGameAnnounced()
	{
		DelveRun run = new DelveRun(START, 12, true);
		assertEquals(12, run.currentLevel());
		assertEquals(0, run.lastLevel());

		run.complete(12, at(90), Duration.ofMillis(88_200));
		assertEquals(13, run.currentLevel());

		run.enterLevel(13, at(100));
		assertEquals(13, run.currentLevel());
	}

	/** Every clear of a run watched from the start has a measured segment to charge to a rate. */
	@Test
	public void everyClearOfAWatchedRunIsTimed()
	{
		DelveRun run = new DelveRun(START, 1, false);
		assertFalse(run.lastClearTimed());

		run.complete(1, at(60), Duration.ofSeconds(58));
		assertTrue(run.lastClearTimed());

		run.complete(2, at(120), Duration.ofSeconds(55));
		assertTrue(run.lastClearTimed());
	}

	/** A joined run's first segment starts wherever we picked it up, so only later ones count. */
	@Test
	public void theFirstClearOfAJoinedRunIsNotTimed()
	{
		DelveRun run = new DelveRun(START, 12, true);

		run.complete(12, at(90), Duration.ofMillis(88_200));
		assertFalse(run.lastClearTimed());

		run.complete(13, at(180), Duration.ofMillis(86_000));
		assertTrue(run.lastClearTimed());
	}

	/** Picked up in the wait after delve 3, the run sees delve 4 start and times it from there. */
	@Test
	public void aRunJoinedBetweenDelvesTimesTheNextOne()
	{
		DelveRun run = new DelveRun(START, 3, true);

		run.enterLevel(4, at(10));
		run.complete(4, at(100), Duration.ofSeconds(88));

		assertTrue(run.lastClearTimed());
		assertEquals(DoomFormat.toTicks(Duration.ofSeconds(90)), run.lastClearTicks());
	}

	/** The delve it was picked up in announced again is not a start it saw. */
	@Test
	public void aRepeatOfTheJoinedDelveIsNotAStart()
	{
		DelveRun run = new DelveRun(START, 12, true);

		run.enterLevel(12, at(10));
		run.complete(12, at(90), Duration.ofMillis(88_200));

		assertFalse(run.lastClearTimed());
	}

	/** Joined on a delve's own chat line, the run has watched that delve from its start. */
	@Test
	public void aRunJoinedAtADelveStartTimesItsFirstClear()
	{
		DelveRun run = new DelveRun(START, 12, true);
		run.watchedFromDelveStart(START);

		run.complete(12, at(90), Duration.ofMillis(88_200));

		assertTrue(run.lastClearTimed());
	}

	/**
	 * The delve 1 clear from the log at 22:07:26: the chat line landed at 22:06:37, the game's own
	 * clock started at 22:06:39, and it reported 0:47.4. Anchoring on the chat line charged the
	 * two second walk-in to the delve and read 0:49 instead.
	 */
	@Test
	public void reanchoringMakesTheFirstSegmentMatchTheGame()
	{
		DelveRun run = new DelveRun(START, 1, false);

		assertTrue(run.reanchorStart(at(2)));

		DelveRun.Split split = run.complete(1, at(49), Duration.ofMillis(47_400));
		assertEquals(Duration.ofSeconds(47), split.segment);
		assertEquals(Duration.ofSeconds(47), run.clearedElapsed());
	}

	@Test
	public void reanchoringOnlyEverMovesAnUnbankedRunForwards()
	{
		DelveRun run = new DelveRun(START.plusSeconds(10), 1, false);

		// Never backwards, however late the signal arrives.
		assertFalse(run.reanchorStart(START));
		assertEquals(Duration.ofSeconds(30), run.liveElapsed(at(40)));

		// And never once a delve has been banked, which would corrupt the banked segment.
		run.complete(1, at(70), null);
		assertFalse(run.reanchorStart(at(80)));
		assertEquals(Duration.ofSeconds(60), run.clearedElapsed());
	}

	/**
	 * A run joined part way through has a start time that is far too late, which would hand out a
	 * personal best nobody earned. The anchor - a moment the run provably had not begun by - drags
	 * the measured time back out to something that can only be too long.
	 */
	@Test
	public void personalBestsOnAPartialRunAreMeasuredFromTheAnchor()
	{
		// Really entered at 0:00, but the plugin only started watching at 10:00.
		DelveRun run = new DelveRun(at(600), 12, true, START);
		run.complete(12, at(700), null);

		assertEquals(Duration.ofSeconds(100), run.clearedElapsed());
		assertEquals(Duration.ofSeconds(700), run.pbElapsed());
	}

	/** A joined run with nothing to bound its start cannot be timed, rather than timed too short. */
	@Test
	public void aPartialRunWithNoAnchorHasNoPersonalBestTime()
	{
		DelveRun run = new DelveRun(at(600), 12, true, null);
		run.complete(12, at(700), null);

		assertNull(run.pbElapsed());
	}

	/** With nothing to correct, a personal best spans exactly what every other figure does. */
	@Test
	public void personalBestsOnACompleteRunMatchTheRunTimer()
	{
		DelveRun run = referenceRun();
		assertEquals(run.clearedElapsed(), run.pbElapsed());

		// An anchor later than the real start is ignored rather than trusted.
		DelveRun anchored = new DelveRun(START, 1, false, at(300));
		anchored.complete(1, at(60), null);
		assertEquals(Duration.ofSeconds(60), anchored.pbElapsed());
	}

	@Test
	public void keepsTheFightLengthTheGameReported()
	{
		DelveRun run = new DelveRun(START, 15, true);
		DelveRun.Split split = run.complete(15, at(120), Duration.ofMillis(90_600));

		assertEquals(Duration.ofMillis(90_600), split.fight);
		// The segment charges the walk and restock on top of the fight itself.
		assertEquals(Duration.ofSeconds(120), split.segment);
	}

	@Test
	public void fullPaceCountsDelveEightAndChargesForTheShallowDelves()
	{
		// Thirteen deep delves (8 through 20) completed in 28:00.
		assertEquals(27.86, referenceRun().fullPace(), DELTA);
	}

	@Test
	public void deepPaceAveragesNineAndAboveOnly()
	{
		// Twelve delves at a flat 1:30 each, delve 8 excluded.
		assertEquals(40.0, referenceRun().deepPace(), DELTA);
	}

	@Test
	public void fullPaceClimbsAsTheShallowDelvesAmortise()
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 6; level++)
		{
			run.complete(level, at(level * 60), null);
		}
		run.complete(7, at(480), null);
		run.complete(8, at(600), null);

		for (int level = 9; level <= 20; level++)
		{
			run.complete(level, at(600 + (level - 8) * 90), null);

			if (level == 10)
			{
				assertEquals(13.85, run.fullPace(), DELTA);
			}
			else if (level == 15)
			{
				assertEquals(23.41, run.fullPace(), DELTA);
			}
		}

		assertEquals(27.86, run.fullPace(), DELTA);

		// Deep pace is flat throughout because every delve 9+ took the same 1:30.
		assertEquals(40.0, run.deepPace(), DELTA);
	}

	@Test
	public void deepPaceIsUnavailableUntilANineIsCleared()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(8, at(600), null);

		assertNull(run.deepPace());
		// Delve 8 still counts towards full pace: one deep delve in 10:00.
		assertEquals(6.0, run.fullPace(), DELTA);
	}

	/**
	 * Picked up ten seconds before the kill on delve 12, a run that counted that delve would credit
	 * it to those ten seconds and read 360/hr. It is paced from that clear on instead.
	 */
	@Test
	public void aRunJoinedPartWayIntoADelveIsPacedFromItsClear()
	{
		DelveRun run = new DelveRun(START, 12, true);
		run.complete(12, at(10), null);

		assertNull(run.fullPace());
		assertNull(run.deepPace());
		assertNull(run.untilTarget(20, at(10)));

		run.complete(13, at(100), null);

		assertEquals(40.0, run.fullPace(), DELTA);
		assertEquals(40.0, run.deepPace(), DELTA);
		assertEquals(Duration.ofSeconds(630), run.untilTarget(20, at(100)));
	}

	/** Joined on a delve's own chat line, the run saw that delve start, and paces it as usual. */
	@Test
	public void aRunJoinedAtADelveStartIsPacedFromIt()
	{
		DelveRun run = new DelveRun(START, 12, true);
		run.watchedFromDelveStart(START);
		run.complete(12, at(90), null);

		assertEquals(40.0, run.fullPace(), DELTA);
		assertEquals(40.0, run.deepPace(), DELTA);
	}

	/**
	 * The reference run sits on delve 20 having cleared each of the last twelve in 1:30, so the
	 * time to a target is a flat 1:30 a delve less however long the current one has been going.
	 */
	@Test
	public void predictsTheTimeToATargetFromTheDeepAverage()
	{
		DelveRun run = referenceRun();

		// Delve 21 is the one in progress, so 30 delves stand between the run and delve 50.
		assertEquals(Duration.ofMinutes(45), run.untilTarget(50, at(1680)));

		// Half a minute into delve 21, and the estimate is half a minute shorter.
		assertEquals(Duration.ofSeconds(2670), run.untilTarget(50, at(1710)));
	}

	/** No target set is not a target of nothing - there is simply no figure to give. */
	@Test
	public void predictsNothingWithoutATarget()
	{
		assertNull(referenceRun().untilTarget(0, at(1680)));
	}

	@Test
	public void predictsNothingUntilANineIsCleared()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(8, at(600), null);

		assertNull(run.untilTarget(50, at(650)));
	}

	/** A dead run has no time left to run, whether or not it got where it was going. */
	@Test
	public void predictsNothingOnceTheRunIsOver()
	{
		DelveRun run = referenceRun();
		run.enterLevel(21, at(1690));
		run.end(EndReason.DIED, at(1700), 21);

		assertNull(run.untilTarget(50, at(1700)));
		assertFalse(run.hasReached(50));
		assertTrue(run.hasReached(20));
	}

	@Test
	public void aReachedTargetHasNoTimeLeftToPredict()
	{
		DelveRun run = referenceRun();

		assertTrue(run.hasReached(20));
		assertTrue(run.hasReached(15));
		assertFalse(run.hasReached(21));
		assertNull(run.untilTarget(20, at(1680)));
		assertNull(run.untilTarget(15, at(1680)));
	}

	/**
	 * A delve that overruns the average stalls the estimate on what the delves after it must take,
	 * rather than counting down into nothing and jumping back up on the clear.
	 */
	@Test
	public void anOverrunningDelveStallsTheEstimateRatherThanReversingIt()
	{
		DelveRun run = referenceRun();

		// Three delves to go at 1:30 each, less the 1:29 delve 21 has been going: 3:01.
		assertEquals(Duration.ofSeconds(181), run.untilTarget(23, at(1680 + 89)));

		// Past 1:30 the estimate holds at the two delves still to come after this one.
		assertEquals(Duration.ofMinutes(3), run.untilTarget(23, at(1680 + 91)));
		assertEquals(Duration.ofMinutes(3), run.untilTarget(23, at(1680 + 600)));

		// And a target one delve out floors at nothing rather than going backwards.
		assertEquals(Duration.ZERO, run.untilTarget(21, at(1680 + 600)));
	}

	/** The run so far and the time still to go, which holds still while delve 21 keeps to pace. */
	@Test
	public void predictsTheWholeRunToATarget()
	{
		DelveRun run = referenceRun();

		// 28:00 through delve 20, and thirty delves at 1:30 to go.
		assertEquals(Duration.ofMinutes(73), run.runToTarget(50, at(1680)));
		assertEquals(Duration.ofMinutes(73), run.runToTarget(50, at(1710)));

		assertNull(run.runToTarget(0, at(1680)));
	}

	/** An overrunning delve is the run getting slower, so the total counts up while it lasts. */
	@Test
	public void anOverrunningDelveAddsToTheWholeRun()
	{
		DelveRun run = referenceRun();

		// 28:00 and three delves at 1:30.
		assertEquals(Duration.ofSeconds(1950), run.runToTarget(23, at(1680 + 90)));
		assertEquals(Duration.ofSeconds(1960), run.runToTarget(23, at(1680 + 100)));
	}

	/** Once there, the total is the real time to it, however much deeper the run goes. */
	@Test
	public void aReachedTargetKeepsTheTimeItActuallyTook()
	{
		DelveRun run = referenceRun();

		// Delve 15 was cleared 10:00 in plus seven delves at 1:30.
		assertEquals(Duration.ofSeconds(1230), run.runToTarget(15, at(1680)));
		assertEquals(Duration.ofSeconds(1230), run.runToTarget(15, at(5000)));

		run.enterLevel(21, at(1690));
		run.end(EndReason.DIED, at(1700), 21);
		assertEquals("the run ending does not take it away",
			Duration.ofSeconds(1230), run.runToTarget(15, at(1700)));
		assertNull("a run over short of its target has nothing to predict",
			run.runToTarget(50, at(1700)));
	}

	@Test
	public void predictsNoWholeRunUntilANineIsCleared()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(8, at(600), null);

		assertNull(run.runToTarget(50, at(650)));
	}

	/** A run joined past its target was never seen clearing it, so there is no time to give. */
	@Test
	public void aTargetClearedBeforeTheRunWasJoinedHasNoTime()
	{
		DelveRun run = new DelveRun(START, 12, true);
		run.complete(12, at(90), null);

		assertTrue(run.hasReached(10));
		assertNull(run.runToTarget(10, at(100)));
	}

	@Test
	public void dyingReportsTheTimeThroughThePreviousDelve()
	{
		DelveRun run = referenceRun();
		run.enterLevel(21, at(1690));
		run.end(EndReason.DIED, at(1700), 21);

		// The partial delve 21 contributes nothing: totals and pace stop at delve 20.
		assertEquals(Duration.ofMinutes(28), run.clearedElapsed());
		assertEquals(20, run.lastLevel());
		assertEquals(21, run.getDiedOnLevel());
		assertEquals(27.86, run.fullPace(), DELTA);
		assertEquals(40.0, run.deepPace(), DELTA);
	}

	/** A finished run freezes on the time through its last clear, so it matches the pace shown. */
	@Test
	public void displayElapsedFreezesOnceTheRunIsOver()
	{
		DelveRun run = referenceRun();
		assertEquals(Duration.ofMinutes(40), run.displayElapsed(at(2400)));

		run.end(EndReason.DIED, at(1700), 21);
		assertEquals(Duration.ofMinutes(28), run.displayElapsed(at(2400)));
	}

	@Test
	public void paceModeSelectsBetweenTheTwoFigures()
	{
		DelveRun run = referenceRun();

		assertEquals(27.86, run.pace(PaceMode.RUN_THROUGHPUT), DELTA);
		assertEquals(40.0, run.pace(PaceMode.DEEP_AVERAGE), DELTA);
	}

	@Test
	public void anEndedRunIsReadByFullPace()
	{
		DelveRun run = referenceRun();
		assertEquals(PaceMode.DEEP_AVERAGE, run.paceMode(PaceMode.DEEP_AVERAGE));

		run.end(EndReason.FINISHED, at(1700), -1);
		assertEquals(PaceMode.RUN_THROUGHPUT, run.paceMode(PaceMode.DEEP_AVERAGE));
		assertEquals(PaceMode.RUN_THROUGHPUT, run.paceMode(PaceMode.RUN_THROUGHPUT));
	}

	/**
	 * The 15-delve trip from the log that was previously reported as a single delve in 22:47.
	 * Delve boundaries are the wall clock times the game messages landed at.
	 */
	@Test
	public void replaysTheLoggedFifteenDelveTrip()
	{
		int[] clearedAt = {56, 90, 194, 275, 343, 421, 534, 647, 731, 818, 941, 1052, 1155, 1305, 1406};
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 15; level++)
		{
			run.complete(level, at(clearedAt[level - 1]), null);
		}

		assertEquals(15, run.lastLevel());
		assertEquals(Duration.ofSeconds(1406), run.clearedElapsed());

		// Eight deep delves (8 through 15) in 23:26, not the single delve the old code counted.
		assertEquals(20.48, run.fullPace(), DELTA);
		assertEquals(33.20, run.deepPace(), DELTA);
	}

	/** Turned off on delve 4, back on at delve 10: every delve cleared since is accounted for. */
	@Test
	public void aRunContinuesOnlyWhereTheClearsSinceWouldHaveTakenIt()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(2, at(120), null);
		run.complete(3, at(180), null);

		// On delve 4 when suspended; six clears later it is on delve 10, or reads 9 between delves.
		assertTrue(run.continuesAt(10, 6, false));
		assertTrue(run.continuesAt(9, 6, true));
		assertFalse("a new trip would be on delve 7", run.continuesAt(7, 6, true));
		assertFalse(run.continuesAt(11, 6, true));
		assertFalse(run.continuesAt(4, -1, true));
	}

	/**
	 * Turned off right after delve 1, and the trip ended with no more clears. A later trip on its
	 * delve 3 has cleared two, which one short would match - but it is mid-delve, not between.
	 */
	@Test
	public void oneShortOnlyMatchesBetweenDelves()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		assertFalse(run.continuesAt(3, 2, false));
		assertFalse("a later trip's delve 1", run.continuesAt(1, 0, false));
		assertTrue("still waiting to go down to delve 2", run.continuesAt(1, 0, true));
	}

	/**
	 * Turned off and on again on the same delve: nothing went by unwatched, so the delve's segment
	 * still runs from the last clear and is banked like any other. From a trip's log, where a
	 * toggle on delve 2 left it out of the pace with a 0:28 segment for a 0:42 fight.
	 */
	@Test
	public void aRunResumedWithNothingMissedCarriesOnAsItWas()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(45), null);
		run.enterLevel(2, at(51));

		run.resumeOn(2, at(81), 0);
		run.watchedFromDelveStart(at(81));
		run.complete(2, at(96), null);

		assertTrue(run.lastClearTimed());
		assertEquals(Duration.ofSeconds(51), run.getSplits().get(1).segment);
	}

	/** Turned off between delves and on again once the next one had started. */
	@Test
	public void aRunResumedWithNothingMissedMovesIntoTheDelveUnderWay()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(45), null);
		assertEquals(1, run.dropLevel());

		run.resumeOn(2, at(60), 0);

		assertFalse(run.isBetweenDelves());
		assertEquals(2, run.dropLevel());
	}

	/**
	 * Carried on in the second between delve 7's chat line and the varp moving, so read as delve 6:
	 * the varp moving puts it right, without timing a delve whose start it missed.
	 */
	@Test
	public void theDelveVarpMovesARunPickedUpOneShortOnToItsDelve()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(2, at(120), null);
		run.complete(3, at(180), null);

		run.resumeOn(6, at(400), 3);
		assertTrue(run.caughtUpTo(7));
		assertEquals(7, run.currentLevel());
		assertFalse("already there", run.caughtUpTo(7));
		assertFalse("never backwards", run.caughtUpTo(6));

		run.complete(7, at(480), null);
		assertFalse(run.lastClearTimed());
		assertEquals(7, run.lastLevel());
	}

	/** In the ordinary way the chat line has moved the run on before the varp does. */
	@Test
	public void theDelveVarpLeavesARunThatSawItsDelveStartAlone()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		assertFalse("between delves, the varp still reads delve 1", run.caughtUpTo(1));

		run.enterLevel(2, at(66));
		assertFalse(run.caughtUpTo(2));
		assertEquals(2, run.currentLevel());
	}

	/** The clear after the unwatched delves has no measured segment, so pace leaves it out. */
	@Test
	public void theFirstClearAfterAResumeIsNotTimed()
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 9; level++)
		{
			run.complete(level, at(level * 60), null);
		}

		// Delves 10-14 go by unwatched; picked up again part way into delve 15.
		run.resumeOn(15, at(1500), 5);
		run.complete(15, at(1560), null);

		assertFalse(run.lastClearTimed());
		assertEquals(15, run.lastLevel());
		assertEquals(Duration.ofSeconds(1560), run.clearedElapsed());

		run.complete(16, at(1650), null);

		assertTrue(run.lastClearTimed());
		// Deep delves 8, 9 and 16 over the measured 9 x 60 + 90 seconds, leaving out the gap.
		assertEquals(3 * 3600.0 / 630, run.fullPace(), DELTA);
		// Delves 9 and 16 average 75 seconds.
		assertEquals(Duration.ofSeconds(75), run.meanDeepSegment());
	}

	/** Picked up between delves, the next delve is seen to start, so its clear is timed. */
	@Test
	public void aRunResumedBetweenDelvesTimesTheNextOne()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		run.resumeOn(5, at(600), 3);
		run.enterLevel(6, at(620));
		run.complete(6, at(700), null);

		assertTrue(run.lastClearTimed());
		assertEquals(Duration.ofSeconds(80), run.getSplits().get(1).segment);
		assertEquals(Duration.ofSeconds(700), run.clearedElapsed());
	}
}
