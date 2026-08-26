package com.tnamai.doommetrics;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
		DelveRun run = new DelveRun(START, false);

		for (int level = 1; level <= 6; level++)
		{
			run.complete(level, at(level * 60));
		}

		run.complete(7, at(480));
		run.complete(8, at(600));

		for (int level = 9; level <= 20; level++)
		{
			run.complete(level, at(600 + (level - 8) * 90));
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

		long summed = run.getSplits().stream().mapToLong(s -> s.duration.getSeconds()).sum();
		assertEquals(run.clearedElapsed().getSeconds(), summed);
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
		DelveRun run = new DelveRun(START, false);

		for (int level = 1; level <= 6; level++)
		{
			run.complete(level, at(level * 60));
		}
		run.complete(7, at(480));
		run.complete(8, at(600));

		for (int level = 9; level <= 20; level++)
		{
			run.complete(level, at(600 + (level - 8) * 90));

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
		DelveRun run = new DelveRun(START, false);
		run.complete(1, at(60));
		run.complete(8, at(600));

		assertNull(run.deepPace(9));
		// Delve 8 still counts towards run pace: one deep delve in 10:00.
		assertEquals(6.0, run.runPace(8), DELTA);
	}

	@Test
	public void dyingReportsTheTimeThroughThePreviousDelve()
	{
		DelveRun run = referenceRun();
		run.end(EndReason.DIED, 21);

		// The partial delve 21 contributes nothing: totals and pace stop at delve 20.
		assertEquals(Duration.ofMinutes(28), run.clearedElapsed());
		assertEquals(20, run.lastLevel());
		assertEquals(27.86, run.runPace(8), DELTA);
		assertEquals(40.0, run.deepPace(9), DELTA);
	}

	@Test
	public void paceModeSelectsBetweenTheTwoFigures()
	{
		DelveRun run = referenceRun();

		assertEquals(27.86, run.pace(PaceMode.RUN_THROUGHPUT, 8, 9), DELTA);
		assertEquals(40.0, run.pace(PaceMode.DEEP_AVERAGE, 8, 9), DELTA);
	}
}
