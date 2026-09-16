package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * The figures here are the ones a real trip produced: potion drips of one prayer point on an exact
 * twelve-tick grid, hitpoints coming back one at a time on an exact hundred-tick one, Eldritch
 * restores of five to twenty-five and godsword heals of eighteen to twenty-five landing off both.
 * The natural level is ninety-nine throughout, which is where a drip stops and the gear carries on.
 */
public class RegenerationTest
{
	private static final int NATURAL = 99;

	/** The prayer regeneration potion's cadence. */
	private static final int POTION = 12;

	/** The standard hitpoints cadence, a point a minute. */
	private static final int NATURALLY = 100;

	/** Nothing dripping at all - no dose in effect. */
	private static final int NONE = 0;

	private final Regeneration regeneration = new Regeneration();

	@Test
	public void aDripInTheOpenIsNotCounted()
	{
		assertEquals(0, regeneration.without(70, 71, NATURAL, 100, POTION, true));
	}

	@Test
	public void aLonePointWithNothingDrippingIsCounted()
	{
		assertEquals(1, regeneration.without(70, 71, NATURAL, 100, NONE, true));
	}

	/**
	 * The case no filter on the size alone can catch: the drip lands on the tick a restore does and
	 * the game reports one number. The grid says a point of it was the potion's.
	 */
	@Test
	public void aRestoreSharingADripsTickIsCountedAPointShort()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);

		assertEquals(18, regeneration.without(71, 90, NATURAL, 112, POTION, false));
	}

	@Test
	public void aRestoreBetweenTwoDripsIsCountedWhole()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);

		assertEquals(18, regeneration.without(71, 89, NATURAL, 105, POTION, false));
	}

	/**
	 * A drip that fell inside a window is still a drip. This is the one that was being credited to
	 * the staff: a point of potion two ticks after a spec, on the grid the drips before it laid.
	 */
	@Test
	public void aDripInsideAWindowIsCaughtByTheGrid()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);

		assertEquals(0, regeneration.without(71, 72, NATURAL, 112, POTION, false));
	}

	/**
	 * The potion stops at the level the player trained and the staff does not, so a rise that began
	 * there has no drip in it however neatly it lands on the grid.
	 */
	@Test
	public void aRiseFromAboveTheNaturalLevelIsAllTheGears()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);

		assertEquals(1, regeneration.without(119, 120, NATURAL, 112, POTION, false));
		assertEquals(1, regeneration.without(99, 100, NATURAL, 124, POTION, false));
	}

	/** The last point a drip can reach is still the drip's. */
	@Test
	public void thePointOntoTheNaturalLevelIsTheDrips()
	{
		assertEquals(0, regeneration.without(98, 99, NATURAL, 100, POTION, true));
	}

	/** A rise through the natural level is a point of each, and the gear is credited with its one. */
	@Test
	public void aRiseThroughTheNaturalLevelIsSharedWithTheGear()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);

		assertEquals(1, regeneration.without(98, 100, NATURAL, 112, POTION, false));
	}

	/**
	 * A single hitpoint can be a blood barrage on a small hit, so one that a window could explain
	 * must not lay the grid - a grid laid on a real heal docks points off real heals for as long as
	 * it stands. Only a point that fell in the open lays it.
	 */
	@Test
	public void aPointAWindowCouldExplainDoesNotLayTheGrid()
	{
		assertEquals(1, regeneration.without(80, 81, NATURAL, 100, NATURALLY, false));

		// If the point above had laid the grid, this heal a hundred ticks later would be docked.
		assertEquals(20, regeneration.without(81, 101, NATURAL, 200, NATURALLY, false));
	}

	/** Hitpoints come back a point a minute, and the run of a fight does not shift the grid. */
	@Test
	public void hitpointsThatCameBackOnTheirOwnAreNotCounted()
	{
		assertEquals(0, regeneration.without(80, 81, NATURAL, 4225, NATURALLY, true));

		assertEquals(20, regeneration.without(81, 101, NATURAL, 4281, NATURALLY, false));
		assertEquals(0, regeneration.without(81, 82, NATURAL, 4625, NATURALLY, true));
		assertEquals(25, regeneration.without(70, 95, NATURAL, 4720, NATURALLY, false));
		assertEquals(0, regeneration.without(95, 96, NATURAL, 4725, NATURALLY, true));
	}

	/** Rapid Heal, a cape or a bracelet only change how often it drips, not what is done about it. */
	@Test
	public void aFasterCadenceIsTheSameRule()
	{
		regeneration.without(80, 81, NATURAL, 100, 25, true);

		assertEquals(19, regeneration.without(81, 101, NATURAL, 125, 25, false));
		assertEquals(20, regeneration.without(81, 101, NATURAL, 140, 25, false));
	}

	/** A grid laid before a login is against a tick counter that no longer runs, so it is dropped. */
	@Test
	public void aTickCounterThatWentBackwardsForgetsTheGrid()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);

		assertEquals(19, regeneration.without(71, 90, NATURAL, 40, POTION, false));
		assertEquals(19, regeneration.without(71, 90, NATURAL, 52, POTION, false));
	}

	@Test
	public void aGridThatWasNeverLaidTakesNothing()
	{
		assertEquals(19, regeneration.without(71, 90, NATURAL, 112, POTION, false));
	}

	@Test
	public void resetForgetsTheGrid()
	{
		regeneration.without(70, 71, NATURAL, 100, POTION, true);
		regeneration.reset();

		assertEquals(19, regeneration.without(71, 90, NATURAL, 112, POTION, false));
	}
}
