package com.rescanta.doommetrics;

import net.runelite.api.gameval.SpotanimID;

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

	/** The Eye of Ayak's bolt flies 5 cycles a tile with none over, a tick later from 3 tiles too. */
	private static final int AYAK_LEAD = 20;

	/** A dart flies 5 cycles a tile and lands a tick later only from 6 tiles on. */
	private static final int DART_LEAD = 0;

	/** The toxic blowpipe's spec flies 7 cycles a tile, a tick later from 4 tiles on. */
	private static final int BLOWPIPE_SPEC_LEAD = 2;

	/**
	 * The scorching bow's spec flies 4 cycles a tile and 4 over: a tick behind a bow's shot at
	 * any distance, and one more from 6 tiles on.
	 */
	private static final int SCORCHING_SPEC_LEAD = 32;

	/** A thrall's hit is ahead of its projectile: a ghost's by about this much, a skeleton's less. */
	private static final int GHOST_LAG = 15;
	private static final int SKELETON_LAG = 5;

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
	 * tick after, 50 the one after that. What is not a bow's arrow or a crossbow's bolt keeps its
	 * own time for the same distance.
	 */
	static int landingTick(int started, int length, int projectile)
	{
		return started + (Math.max(0, length) + lead(projectile)) / CYCLES_PER_TICK;
	}

	private static int lead(int projectile)
	{
		switch (projectile)
		{
			case SpotanimID.VFX_AYAK_NORMAL_PROJECTILE:
				return AYAK_LEAD;
			case SpotanimID.TOXIC_BLOWPIPE_SPECIALATTACK:
				return BLOWPIPE_SPEC_LEAD;
			case SpotanimID.VFX_SCORCHING_BOW_PROJECTILE:
				return SCORCHING_SPEC_LEAD;
			case SpotanimID.BRONZE_DART_TRAVEL:
			case SpotanimID.IRON_DART_TRAVEL:
			case SpotanimID.STEEL_DART_TRAVEL:
			case SpotanimID.BLACK_DART_TRAVEL:
			case SpotanimID.MITHRIL_DART_TRAVEL:
			case SpotanimID.ADAMANT_DART_TRAVEL:
			case SpotanimID.RUNE_DART_TRAVEL:
			case SpotanimID.AMETHYST_DART_TRAVEL:
			case SpotanimID.II_DRAGON_DART_PROJANIM:
				return DART_LEAD;
			default:
				return OWN_LEAD;
		}
	}

	/**
	 * A thrall's. A ghost's lands on the tick it starts up to 40 cycles and the tick after from
	 * 45 to 60; a skeleton's on the tick it starts up to 30 and the tick after from 35 to 60.
	 */
	static int thrallLandingTick(int started, int length, boolean ghost)
	{
		return started + Math.max(0, length - (ghost ? GHOST_LAG : SKELETON_LAG)) / CYCLES_PER_TICK;
	}
}
