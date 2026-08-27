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
}
