package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MilestoneTrackerTest
{
	/** Log 2026-09-22 23:37-23:39: the counter read 12798 on delve 1 and went up with each clear. */
	@Test
	public void aTripPickedUpAgainIsTheTripCountedAtItsStart()
	{
		int counted = MilestoneTracker.trip(12_798, 1);

		// Picked up on delve 3 after a session reset, by the chat line or by the delve varp.
		assertTrue(MilestoneTracker.isSameTrip(counted, MilestoneTracker.trip(12_800, 3)));

		// On delve 2's clear line: the counter has gone up, the delve read is the one cleared.
		assertTrue(MilestoneTracker.isSameTrip(counted, MilestoneTracker.trip(12_800, 2)));
	}

	@Test
	public void aLaterTripIsNotTheOneCounted()
	{
		int counted = MilestoneTracker.trip(12_798, 1);

		// The counted trip cleared three, then a new one was joined on delve 3.
		assertFalse(MilestoneTracker.isSameTrip(counted, MilestoneTracker.trip(12_803, 3)));
	}

	@Test
	public void nothingMatchesBeforeTheCounterArrives()
	{
		assertEquals(-1, MilestoneTracker.trip(0, 4));
		assertFalse(MilestoneTracker.isSameTrip(-1, MilestoneTracker.trip(12_800, 3)));
		assertFalse(MilestoneTracker.isSameTrip(-1, -1));
	}
}
