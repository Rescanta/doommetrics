package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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

		run.enterLevel(13);
		assertEquals(13, run.currentLevel());
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
	public void runPaceCountsDelveEightAndChargesForTheWarmUp()
	{
		// Thirteen deep delves (8 through 20) banked in 28:00.
		assertEquals(27.86, referenceRun().runPace(), DELTA);
	}

	@Test
	public void deepPaceAveragesNineAndAboveOnly()
	{
		// Twelve delves at a flat 1:30 each, delve 8 excluded.
		assertEquals(40.0, referenceRun().deepPace(), DELTA);
	}

	@Test
	public void runPaceClimbsAsTheWarmUpAmortises()
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
				assertEquals(13.85, run.runPace(), DELTA);
			}
			else if (level == 15)
			{
				assertEquals(23.41, run.runPace(), DELTA);
			}
		}

		assertEquals(27.86, run.runPace(), DELTA);

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
		// Delve 8 still counts towards run pace: one deep delve in 10:00.
		assertEquals(6.0, run.runPace(), DELTA);
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
		run.enterLevel(21);
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

	@Test
	public void dyingReportsTheTimeThroughThePreviousDelve()
	{
		DelveRun run = referenceRun();
		run.enterLevel(21);
		run.end(EndReason.DIED, at(1700), 21);

		// The partial delve 21 contributes nothing: totals and pace stop at delve 20.
		assertEquals(Duration.ofMinutes(28), run.clearedElapsed());
		assertEquals(20, run.lastLevel());
		assertEquals(21, run.getDiedOnLevel());
		assertEquals(27.86, run.runPace(), DELTA);
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
		assertEquals(20.48, run.runPace(), DELTA);
		assertEquals(33.20, run.deepPace(), DELTA);
	}


	@Test
	public void lootIsListedInTheOrderItWasFirstSeen()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordLoot(31109, "Mokhaiotl cloth", 1);
		run.recordLoot(31130, "Dom", 1);
		run.recordLoot(31113, "Eye of ayak", 1);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Dom", "Eye of ayak"), run.getLoot());
	}

	/** A deep run can roll the same unique more than once, and both of them happened. */
	@Test
	public void aDropEarnedTwiceIsListedTwice()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordLoot(31109, "Mokhaiotl cloth", 2);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Mokhaiotl cloth"), run.getLoot());
	}

	/**
	 * The loot pile is read both on the claim click and on the game's claim script, and the pet is
	 * announced in chat as well as being an item, so the sources overlap by design. Seeing the same
	 * pile twice is not the same as earning its contents twice.
	 */
	@Test
	public void seeingTheSamePileTwiceDoesNotInflateTheCount()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordLoot(31109, "Mokhaiotl cloth", 2);
		run.recordLoot(31109, "Mokhaiotl cloth", 2);
		run.recordLoot(31130, "Dom", 1);
		run.recordLoot(31130, "Dom", 1);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Mokhaiotl cloth", "Dom"), run.getLoot());
	}

	/** A pile that has grown since it was last read is the second drop landing in it. */
	@Test
	public void aPileSeenHoldingMoreRaisesTheCount()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordLoot(31088, "Avernic treads", 1);
		run.recordLoot(31088, "Avernic treads", 3);

		assertEquals(3, run.getLoot().size());
	}

	/** A read that saw fewer than a previous one is a partial view, not a drop being taken back. */
	@Test
	public void aPileSeenHoldingLessDoesNotLowerTheCount()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordLoot(31088, "Avernic treads", 2);
		run.recordLoot(31088, "Avernic treads", 1);

		assertEquals(2, run.getLoot().size());
	}

	/** A drop the item cache could not name is dropped rather than listed as a blank. */
	@Test
	public void anUnnamedDropIsNotListed()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.recordLoot(31088, null, 1);

		assertTrue(run.getLoot().isEmpty());
	}

	@Test
	public void aRunWithNoLootListsNone()
	{
		assertTrue(referenceRun().getLoot().isEmpty());
	}
}
