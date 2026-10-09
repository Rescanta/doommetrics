package com.rescanta.doommetrics;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FlightTest
{
	@Test
	public void aProjectileSeenAsItStartsStartedThisTick()
	{
		assertEquals(100, Flight.startedTick(100, 5000, 5000));
		assertEquals(100, Flight.startedTick(100, 5001, 5000));
	}

	@Test
	public void aProjectileFirstSeenMidFlightStartedEarlier()
	{
		assertEquals(99, Flight.startedTick(100, 5000, 4965));
		assertEquals(100, Flight.startedTick(100, 5000, 4990));
	}

	/** As logged with a bow: by the cycles the arrow flies for, the ticks from its start to the hit. */
	@Test
	public void ourShotLandsByHowLongItFlies()
	{
		assertEquals(100, Flight.landingTick(100, 10));
		assertEquals(100, Flight.landingTick(100, 15));
		assertEquals(101, Flight.landingTick(100, 20));
		assertEquals(101, Flight.landingTick(100, 45));
		assertEquals(102, Flight.landingTick(100, 50));
	}

	/** As logged with a ghost. */
	@Test
	public void aGhostsShotLandsAheadOfItsProjectile()
	{
		assertEquals(100, Flight.thrallLandingTick(100, 15, true));
		assertEquals(100, Flight.thrallLandingTick(100, 40, true));
		assertEquals(101, Flight.thrallLandingTick(100, 45, true));
		assertEquals(101, Flight.thrallLandingTick(100, 60, true));
	}

	/** As logged with a skeleton, which lands a tick later than a ghost from 35 to 40 cycles. */
	@Test
	public void aSkeletonsShotIsLessAheadOfItsProjectile()
	{
		assertEquals(100, Flight.thrallLandingTick(100, 25, false));
		assertEquals(100, Flight.thrallLandingTick(100, 30, false));
		assertEquals(101, Flight.thrallLandingTick(100, 35, false));
		assertEquals(101, Flight.thrallLandingTick(100, 60, false));
	}
}
