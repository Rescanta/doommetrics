package com.rescanta.doommetrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Energy in the game's own units: 1000 is full, an AGS spec costs 500, regeneration adds 100. */
public class SpecEnergyTest
{
	private final SpecEnergy energy = new SpecEnergy();

	@Test
	public void aFallIsASpec()
	{
		energy.seed(1000);

		assertTrue(energy.spent(500));
	}

	@Test
	public void regenerationIsNotASpec()
	{
		energy.seed(500);

		assertFalse(energy.spent(600));
		assertFalse(energy.spent(700));
	}

	@Test
	public void specsInARowAreEachCounted()
	{
		energy.seed(1000);

		assertTrue(energy.spent(500));
		assertTrue(energy.spent(0));
	}

	@Test
	public void aSpecAfterRegenerationIsReadAgainstTheRegeneratedLevel()
	{
		energy.seed(400);
		energy.spent(500);

		assertTrue(energy.spent(0));
	}

	/**
	 * The trip that found this: the plugin turned off and on mid-run with full energy. Nothing is
	 * sent at full energy, so the first AGS was the first level seen and went uncounted.
	 */
	@Test
	public void aFirstLevelWithNothingToReadItAgainstIsNotASpec()
	{
		energy.forget();

		assertFalse(energy.spent(500));
	}

	@Test
	public void seedingOnTheRunsStartCountsTheFirstSpec()
	{
		energy.forget();
		energy.seed(1000);

		assertTrue(energy.spent(500));
	}
}
