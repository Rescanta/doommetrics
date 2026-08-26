package com.tnamai.doommetrics;

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
		assertEquals(27.86, referenceRun().runPace(8), DELTA);
	}

	@Test
	public void deepPaceAveragesNineAndAboveOnly()
	{
		// Twelve delves at a flat 1:30 each, delve 8 excluded.
		assertEquals(40.0, referenceRun().deepPace(9), DELTA);
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
				assertEquals(13.85, run.runPace(8), DELTA);
			}
			else if (level == 15)
			{
				assertEquals(23.41, run.runPace(8), DELTA);
			}
		}

		assertEquals(27.86, run.runPace(8), DELTA);

		// Deep pace is flat throughout because every delve 9+ took the same 1:30.
		assertEquals(40.0, run.deepPace(9), DELTA);
	}

	@Test
	public void deepPaceIsUnavailableUntilANineIsCleared()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.complete(8, at(600), null);

		assertNull(run.deepPace(9));
		// Delve 8 still counts towards run pace: one deep delve in 10:00.
		assertEquals(6.0, run.runPace(8), DELTA);
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
		assertEquals(27.86, run.runPace(8), DELTA);
		assertEquals(40.0, run.deepPace(9), DELTA);
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

		assertEquals(27.86, run.pace(PaceMode.RUN_THROUGHPUT, 8, 9), DELTA);
		assertEquals(40.0, run.pace(PaceMode.DEEP_AVERAGE, 8, 9), DELTA);
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
		assertEquals(20.48, run.runPace(8), DELTA);
		assertEquals(33.20, run.deepPace(9), DELTA);
	}
}
