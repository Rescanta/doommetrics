package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TargetPredictionTest
{
	private static final Instant START = Instant.EPOCH;

	/** Delves 1-8 in 10:00, then delves 9 and 10 at 1:30 each: 13:00 through delve 10. */
	private static DelveRun run()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(8, START.plusSeconds(600), null);
		run.complete(9, START.plusSeconds(690), null);
		run.complete(10, START.plusSeconds(780), null);
		return run;
	}

	@Test
	public void eachChoiceDrawsItsOwnRowsOnTheWay()
	{
		DelveRun run = run();

		assertEquals("Target", TargetPrediction.targetLabel(run, 20));

		assertEquals("To go", TargetPrediction.REMAINING.remainingLabel(run, 20));
		assertNull(TargetPrediction.REMAINING.totalLabel(run));

		assertNull(TargetPrediction.FULL_RUN.remainingLabel(run, 20));
		assertEquals("Total", TargetPrediction.FULL_RUN.totalLabel(run));

		assertEquals("To go", TargetPrediction.BOTH.remainingLabel(run, 20));
		assertEquals("Total", TargetPrediction.BOTH.totalLabel(run));

		// Ten delves at 1:30 still to come, on top of the 13:00 so far.
		Instant now = START.plusSeconds(780);
		assertEquals("15:00", TargetPrediction.remainingValue(run, 20, now));
		assertEquals("28:00", TargetPrediction.totalValue(run, 20, now));
	}

	/**
	 * Once there, the target row says so, the countdown has nothing left to say and goes, and the
	 * total is what the run actually took.
	 */
	@Test
	public void aReachedTargetDropsTheCountdownAndSaysSoOnTheTargetRow()
	{
		DelveRun run = run();
		Instant now = START.plusSeconds(2000);

		assertEquals("Target reached", TargetPrediction.targetLabel(run, 9));

		for (TargetPrediction prediction : TargetPrediction.values())
		{
			assertNull(prediction + " has no countdown", prediction.remainingLabel(run, 9));
		}

		assertEquals("Total", TargetPrediction.BOTH.totalLabel(run));
		assertEquals("11:30", TargetPrediction.totalValue(run, 9, now));
	}

	@Test
	public void aRunWithNothingToPredictFromReadsAsADash()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(8, START.plusSeconds(600), null);

		assertEquals("-", TargetPrediction.remainingValue(run, 20, START.plusSeconds(700)));
		assertEquals("-", TargetPrediction.totalValue(run, 20, START.plusSeconds(700)));
	}

	/**
	 * Joined part way through: the total is marked as a guess, and a target already behind the run
	 * when it was joined still reads as reached.
	 */
	@Test
	public void aPartialRunMarksItsTotalAsAGuess()
	{
		DelveRun run = new DelveRun(START, 12, true);
		run.complete(12, START.plusSeconds(90), Duration.ofSeconds(88));

		assertEquals("Total*", TargetPrediction.FULL_RUN.totalLabel(run));
		assertEquals("Reached", TargetPrediction.totalValue(run, 10, START.plusSeconds(100)));
	}
}
