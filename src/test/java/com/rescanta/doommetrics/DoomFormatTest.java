package com.rescanta.doommetrics;

import java.time.Duration;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DoomFormatTest
{
	@Test
	public void durationDropsTheHourFieldUntilItIsNeeded()
	{
		assertEquals("0:07", DoomFormat.duration(Duration.ofSeconds(7)));
		assertEquals("22:47", DoomFormat.duration(Duration.ofSeconds(1367)));
		assertEquals("1:00:00", DoomFormat.duration(Duration.ofHours(1)));
	}

	/** The game reports delve times to a tenth, so the round trip has to survive intact. */
	@Test
	public void preciseDurationKeepsTheTenth()
	{
		assertEquals("1:30.6", DoomFormat.preciseDuration(Duration.ofMillis(90_600)));
		assertEquals("0:23.4", DoomFormat.preciseDuration(Duration.ofMillis(23_400)));
		assertEquals("0:48.6", DoomFormat.preciseDuration(Duration.ofMillis(48_600)));
		assertEquals("1:00:00.0", DoomFormat.preciseDuration(Duration.ofHours(1)));
	}

	@Test
	public void paceReadsAsADashUntilThereIsOne()
	{
		assertEquals("-", DoomFormat.pace(null));
		assertEquals("3.9/hr", DoomFormat.pace(3.94));
	}

	/** The three states of a target: on the way, arrived, and nothing yet to predict from. */
	@Test
	public void predictionSaysWhichOfTheThreeStatesATargetIsIn()
	{
		assertEquals("45:00", DoomFormat.prediction(Duration.ofMinutes(45), false));
		assertEquals("Reached", DoomFormat.prediction(null, true));
		assertEquals("-", DoomFormat.prediction(null, false));

		// Arriving wins over any time left over, so the two can never be shown contradicting.
		assertEquals("Reached", DoomFormat.prediction(Duration.ofMinutes(45), true));
	}

	/** The game's own unit: 151 ticks is the 1:30.60 it reported for that delve. */
	@Test
	public void ticksRoundToTheNearestTick()
	{
		assertEquals(151, DoomFormat.toTicks(Duration.ofMillis(90_600)));
		assertEquals(1, DoomFormat.toTicks(Duration.ofMillis(300)));
		assertEquals(0, DoomFormat.toTicks(Duration.ofMillis(299)));
		assertEquals(0, DoomFormat.toTicks(Duration.ofMillis(-5000)));
	}

	@Test
	public void storedBestsReadAsATimeWithTheHourOnlyWhenNeeded()
	{
		assertEquals("1:30.6", DoomFormat.ticks(151));
		assertEquals("-", DoomFormat.ticks(0));
		assertEquals("1:00:00.0", DoomFormat.ticks(6000));
		assertEquals("2:52:00.0", DoomFormat.ticks(17_200));
	}

	@Test
	public void countsAreGroupedSoALifetimeIsLegible()
	{
		assertEquals("0", DoomFormat.count(0));
		assertEquals("940", DoomFormat.count(940));
		assertEquals("1,204,318", DoomFormat.count(1_204_318));
	}

	/**
	 * A gridline is a floor the dots above it are read against, so a label has to round down. A
	 * shortened 12,600 reading "13k" would sit above dots it is meant to sit under.
	 */
	@Test
	public void shortenedCountsNeverRoundUpPastTheirValue()
	{
		assertEquals("940", DoomFormat.compact(940));
		assertEquals("1.0k", DoomFormat.compact(1_000));
		assertEquals("9.9k", DoomFormat.compact(9_999));
		assertEquals("12k", DoomFormat.compact(12_600));
		assertEquals("999k", DoomFormat.compact(999_999));
		assertEquals("1.0m", DoomFormat.compact(1_000_000));
		assertEquals("9.9m", DoomFormat.compact(9_999_999));
		assertEquals("12m", DoomFormat.compact(12_600_000));
	}
}
