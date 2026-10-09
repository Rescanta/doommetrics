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

		assertFalse(thralls.isThrallHit(2, 100, true));
		assertTrue(thralls.isThrallHit(2, 101, true));
	}

	@Test
	public void eachAttackTakesOneHit()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 100);

		assertTrue(thralls.isThrallHit(3, 101, true));
		assertFalse(thralls.isThrallHit(1, 101, true));
		assertFalse(thralls.isThrallHit(1, 102, true));
	}

	/** A scythe punish's big hits land beside the thrall's small one. */
	@Test
	public void onlyASmallHitIsTheThralls()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);

		assertFalse(thralls.isThrallHit(22, 103, true));
		assertTrue(thralls.isThrallHit(0, 103, true));
	}

	@Test
	public void aSkeletonHitNeverComesBeforeTwoTicks()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);

		assertFalse(thralls.isThrallHit(1, 101, true));
		assertTrue(thralls.isThrallHit(1, 102, true));
	}

	@Test
	public void aWindowShutsWhenItsLastTickHasPassed()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 100);

		assertFalse(thralls.isThrallHit(1, 103, true));
		assertFalse(thralls.isThrallHit(1, 102, true));
	}

	/** Attacks four ticks apart: each hit goes to its own attack. */
	@Test
	public void backToBackAttacksEachTakeTheirOwn()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);
		thralls.attacked(ThrallTracker.Style.RANGED, 104);

		assertTrue(thralls.isThrallHit(2, 103, true));
		assertTrue(thralls.isThrallHit(2, 106, true));
		assertFalse(thralls.isThrallHit(2, 107, true));
	}

	/**
	 * 2026-09-30 21:37 tick 6481: a ghost attacked at 6480, a 2 of ours landed off the boss and a 1
	 * on it. Which was the thrall's can't be read, so the first doesn't let the second through.
	 */
	@Test
	public void aSmallHitOffTheBossLeavesTheAttackOpenForOneOnIt()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 6480);

		assertTrue(thralls.isThrallHit(2, 6481, false));
		assertTrue(thralls.isThrallHit(1, 6481, true));
		assertFalse(thralls.isThrallHit(1, 6482, true));
	}

	/** Larvae hit in a pile: only the first is left to the thrall. */
	@Test
	public void onlyOneHitOffTheBossIsLeftToAnAttack()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 100);

		assertTrue(thralls.isThrallHit(1, 101, false));
		assertFalse(thralls.isThrallHit(1, 101, false));
		assertFalse(thralls.isThrallHit(1, 102, false));
	}

	/** The thrall's own hit on the boss: nothing off it is taken afterwards. */
	@Test
	public void anAttackSpentOnTheBossTakesNothingElse()
	{
		thralls.attacked(ThrallTracker.Style.MAGIC, 100);

		assertTrue(thralls.isThrallHit(2, 101, true));
		assertFalse(thralls.isThrallHit(1, 101, false));
	}

	/** 2026-09-30 tick 4351: spawned, 2 at 4356 taken as a 4th scythe punish splat. */
	@Test
	public void aGhostsFirstAttackIsExpectedFromItsSpawn()
	{
		thralls.spawned(NpcID.ARCEUUS_THRALL_GHOST_GREATER, 4351);

		assertFalse(thralls.isThrallHit(0, 4354, true));
		assertTrue(thralls.isThrallHit(2, 4356, true));
		assertFalse(thralls.isThrallHit(1, 4356, true));
	}

	/** 2026-09-25 tick 1284: animated a tick late, its hit at +7 and a 2 of ours at +6. */
	@Test
	public void aGhostsAnimatedFirstAttackReplacesTheExpectedOne()
	{
		thralls.spawned(NpcID.ARCEUUS_THRALL_GHOST_GREATER, 1284);
		thralls.attacked(ThrallTracker.Style.MAGIC, 1289);

		assertFalse(thralls.isThrallHit(2, 1289, true));
		assertTrue(thralls.isThrallHit(2, 1290, true));
		assertFalse(thralls.isThrallHit(0, 1291, true));
	}

	/** Skeletons animate their first attack. */
	@Test
	public void noOtherThrallIsExpectedFromItsSpawn()
	{
		thralls.spawned(NpcID.ARCEUUS_THRALL_SKELETON_GREATER, 100);
		thralls.spawned(NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER, 100);

		for (int tick = 101; tick <= 110; tick++)
		{
			assertFalse(thralls.isThrallHit(0, tick, true));
		}
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

	/** 2026-09-25: all 17 spawns played 13683-13685 on the tick they spawned. */
	@Test
	public void aSummonIsReadOffTheSpawnAnimation()
	{
		assertTrue(ThrallTracker.isSummoning(AnimationID.GHOST_UPDATE_THRALL_SPAWN_RAISED));
		assertTrue(ThrallTracker.isSummoning(AnimationID.SKELETON_UPDATE_THRALL_SPAWN_RAISED));
		assertTrue(ThrallTracker.isSummoning(AnimationID.ZOMBIE_UPDATE_THRAWL_SPAWN_RAISED));

		assertFalse(ThrallTracker.isSummoning(AnimationID.GHOST_UPDATE_TENDRILL_ATTACK_THRALL));
		assertFalse(ThrallTracker.isSummoning(-1));
	}

	/** At a punish a splat the window took can turn out to be the swing's - see PunishTracker. */
	@Test
	public void aSplatHandedBackLeavesTheAttackOpenForTheThrallsOwn()
	{
		thralls.attacked(ThrallTracker.Style.RANGED, 100);
		assertTrue(thralls.isThrallHit(1, 102, true));
		assertEquals(1, thralls.tookOnBoss(102));
		assertEquals(-1, thralls.tookOnBoss(103));

		thralls.handBack();
		assertTrue(thralls.isThrallHit(3, 103, true));
		assertFalse("the attack is spent again", thralls.isThrallHit(2, 103, true));
	}
}
