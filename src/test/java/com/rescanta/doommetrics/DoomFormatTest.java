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
}
