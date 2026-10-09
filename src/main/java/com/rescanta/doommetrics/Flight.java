package com.rescanta.doommetrics;

/**
 * A projectile's cycles as game ticks. The client counts a projectile's start and end in cycles
 * of 20ms; the game deals in ticks of 30 of them. A projectile is first reported when it starts
 * to move, a tick or two after the attack that fired it.
 */
final class Flight
{
	static final int CYCLES_PER_TICK = 30;

	/** A bow's flight is 5 cycles a tile and 5 over, and its hit a tick later from 3 tiles on. */
	private static final int OWN_LEAD = 10;

	/** A thrall's hit is ahead of its projectile by about this much. */
	private static final int THRALL_LAG = 15;

	private Flight()
	{
	}

	/** The tick a projectile started to move on. One seen as it starts did so now. */
	static int startedTick(int tick, int cycle, int startCycle)
	{
		return tick - Math.max(0, cycle - startCycle) / CYCLES_PER_TICK;
	}

	/**
	 * The tick a projectile of ours lands its hit on, from the tick it started on and the cycles
	 * it flies for. Measured with a bow: 10 and 15 cycles land on the tick it starts, 20 to 45 the
	 * tick after, 50 the one after that.
	 */
	static int landingTick(int started, int length)
	{
		return started + (Math.max(0, length) + OWN_LEAD) / CYCLES_PER_TICK;
	}

	/** A thrall's: up to 40 cycles land on the tick it starts, 45 to 60 the tick after. */
	static int thrallLandingTick(int started, int length)
	{
		return started + Math.max(0, length - THRALL_LAG) / CYCLES_PER_TICK;
	}
}
