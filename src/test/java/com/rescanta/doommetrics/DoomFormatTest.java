package com.rescanta.doommetrics;

import java.time.Duration;
import org.junit.Assert;
import org.junit.Test;

public class DoomFormatTest
{
	@Test
	public void elapsed()
	{
		Assert.assertEquals("0:00", DoomFormat.elapsed(Duration.ZERO));
		Assert.assertEquals("21:40", DoomFormat.elapsed(Duration.ofSeconds(1300)));
		Assert.assertEquals("28:00", DoomFormat.elapsed(Duration.ofSeconds(1680)));
		Assert.assertEquals("1:14:20", DoomFormat.elapsed(Duration.ofSeconds(4460)));
		Assert.assertEquals("0:00", DoomFormat.elapsed(Duration.ofSeconds(-5)));
	}

	@Test
	public void fight()
	{
		Assert.assertEquals("1:30.6", DoomFormat.fight(Duration.ofMillis(90_600)));
		Assert.assertEquals("0:23.4", DoomFormat.fight(Duration.ofMillis(23_400)));
		Assert.assertEquals("1:31.2", DoomFormat.fight(Duration.ofMillis(91_200)));
		Assert.assertEquals("2:52:00.0", DoomFormat.fight(Duration.ofMillis(10_320_000)));
	}

	@Test
	public void pace()
	{
		Assert.assertEquals("40.0/hr", DoomFormat.pace(40.0));
		Assert.assertEquals("27.9/hr", DoomFormat.pace(27.857142857));
		Assert.assertEquals("-", DoomFormat.pace(null));
		Assert.assertEquals("-", DoomFormat.pace(0.0));
		Assert.assertEquals("40.1", DoomFormat.compactPace(40.06));
		Assert.assertEquals("-", DoomFormat.compactPace(null));
	}

	@Test
	public void count()
	{
		Assert.assertEquals("1,204", DoomFormat.count(1204));
		Assert.assertEquals("12,470", DoomFormat.count(12470));
		Assert.assertEquals("6,694", DoomFormat.count(6694));
	}

	@Test
	public void compact()
	{
		Assert.assertEquals("999", DoomFormat.compact(999));
		Assert.assertEquals("1.2k", DoomFormat.compact(1204));
		Assert.assertEquals("12k", DoomFormat.compact(12_470));
	}

	@Test
	public void compactDuration()
	{
		Assert.assertEquals("14:18", DoomFormat.compactDuration(Duration.ofSeconds(858)));
		Assert.assertEquals("1h23", DoomFormat.compactDuration(Duration.ofSeconds(4980)));
		Assert.assertEquals("10h", DoomFormat.compactDuration(Duration.ofSeconds(36_000)));
	}

	@Test
	public void prediction()
	{
		Assert.assertEquals("Reached", DoomFormat.prediction(Duration.ofSeconds(60), true));
		Assert.assertEquals("-", DoomFormat.prediction(null, false));
		Assert.assertEquals("54:00", DoomFormat.prediction(Duration.ofSeconds(3240), false));
	}

	@Test
	public void tickRoundTrip()
	{
		Assert.assertEquals(151, DoomFormat.toTicks(Duration.ofMillis(90_600)));
		Assert.assertEquals(Duration.ofMillis(90_600), DoomFormat.tickDuration(151));
		Assert.assertEquals("1:30.6", DoomFormat.ticks(151));
		Assert.assertEquals("1:31.2", DoomFormat.ticks(152));
		Assert.assertEquals("-", DoomFormat.ticks(0));
	}
}
