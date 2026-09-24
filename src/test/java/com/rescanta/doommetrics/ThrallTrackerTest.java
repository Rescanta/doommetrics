package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

/** Delays as measured on 2026-09-25: zombie +1, ghost +1..2, skeleton +2..3. */
public class ThrallTrackerTest
{
	private final ThrallTracker thralls = new ThrallTracker();

	@Test
	public void aZombieHitsTheNextTick()
	{
		thralls.attacked(ThrallTracker.Style.MELEE, 100);

		assertFalse(thralls.isThrallHit(2, 100));
		assertTrue(thralls.isThrallHit(2, 101));
	}

	@Test
	public void eachAttackTakesOneHit()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 100);

		assertTrue(thralls.isThrallHit(3, 101));
		assertFalse(thralls.isThrallHit(1, 101));
		assertFalse(thralls.isThrallHit(1, 102));
	}

	/** A scythe punish's big hits land beside the thrall's small one. */
	@Test
	public void onlyASmallHitIsTheThralls()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);

		assertFalse(thralls.isThrallHit(22, 103));
		assertTrue(thralls.isThrallHit(0, 103));
	}

	@Test
	public void aSkeletonHitNeverComesBeforeTwoTicks()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);

		assertFalse(thralls.isThrallHit(1, 101));
		assertTrue(thralls.isThrallHit(1, 102));
	}

	@Test
	public void aWindowShutsWhenItsLastTickHasPassed()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 100);

		assertFalse(thralls.isThrallHit(1, 103));
		assertFalse(thralls.isThrallHit(1, 102));
	}

	/** Attacks four ticks apart: each hit goes to its own attack. */
	@Test
	public void backToBackAttacksEachTakeTheirOwn()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);
		thralls.attacked(ThrallTracker.Style.RANGED, 104);

		assertTrue(thralls.isThrallHit(2, 103));
		assertTrue(thralls.isThrallHit(2, 106));
		assertFalse(thralls.isThrallHit(2, 107));
	}

	@Test
	public void theStyleComesFromTheAttackAnimation()
	{
		assertEquals(ThrallTracker.Style.MAGIC, ThrallTracker.attackStyle(
			NpcID.ARCEUUS_THRALL_GHOST_GREATER, AnimationID.GHOST_UPDATE_TENDRILL_ATTACK_THRALL));
		assertEquals(ThrallTracker.Style.RANGED, ThrallTracker.attackStyle(
			NpcID.ARCEUUS_THRALL_SKELETON_GREATER,
			AnimationID.SKELETON_UPDATE_CHAMPION_ATTACK_THRALL));
		assertEquals(ThrallTracker.Style.MELEE, ThrallTracker.attackStyle(
			NpcID.ARCEUUS_THRALL_ZOMBIE_LESSER, AnimationID.ZOMBIE_UPDATE_ATTACK_NORMAL_THRALL));

		assertNull(ThrallTracker.attackStyle(NpcID.ARCEUUS_THRALL_GHOST_GREATER,
			AnimationID.GHOST_UPDATE_THRALL_SPAWN_RAISED));
		assertNull(ThrallTracker.attackStyle(NpcID.DOM_BOSS,
			AnimationID.GHOST_UPDATE_TENDRILL_ATTACK_THRALL));
	}
}
