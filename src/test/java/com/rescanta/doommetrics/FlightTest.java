package com.rescanta.doommetrics;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FlightTest
{
	@Test
	public void aProjectileNotYetMovingWasFiredThisTick()
	{
		assertEquals(100, Flight.firedTick(100, 5000, 5041));
	}

	@Test
	public void aProjectileFirstSeenMidFlightWasFiredEarlier()
	{
		assertEquals(99, Flight.firedTick(100, 5000, 4965));
		assertEquals(100, Flight.firedTick(100, 5000, 4990));
	}

	@Test
	public void theLandingTickIsWholeTicksOfFlightAway()
	{
		assertEquals(100, Flight.landingTick(100, 5000, 5029));
		assertEquals(101, Flight.landingTick(100, 5000, 5030));
		assertEquals(103, Flight.landingTick(100, 5000, 5095));
	}

	@Test
	public void aProjectilePastItsEndLandsNow()
	{
		assertEquals(100, Flight.landingTick(100, 5000, 4980));
	}
}
