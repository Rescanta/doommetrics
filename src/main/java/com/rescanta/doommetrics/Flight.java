package com.rescanta.doommetrics;

/**
 * A projectile's cycles as game ticks. The client counts a projectile's start and end in cycles
 * of 20ms; the game deals in ticks of 30 of them.
 */
final class Flight
{
	static final int CYCLES_PER_TICK = 30;

	private Flight()
	{
	}

	/** The tick a projectile was fired on. One seen before it starts moving was fired now. */
	static int firedTick(int tick, int cycle, int startCycle)
	{
		return tick - Math.max(0, cycle - startCycle) / CYCLES_PER_TICK;
	}

	/** The tick a projectile comes down on. One already past its end comes down now. */
	static int landingTick(int tick, int cycle, int endCycle)
	{
		return tick + Math.max(0, endCycle - cycle) / CYCLES_PER_TICK;
	}
}
