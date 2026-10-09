package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;

/**
 * Tells a thrall's hits from ours. Its splats are drawn as the player's own, so each attack opens
 * a window for the one hit it lands, and the first small splat of ours inside it is taken as that
 * hit. When one of our own 0-3s beats the thrall's to it, the swap costs at most three damage.
 * What a thrall aims at can't be read, so a small splat off the boss doesn't shut the window to
 * one on it. No RuneLite types.
 */
class ThrallTracker
{
	/** A greater thrall's max hit; lesser and superior thralls hit less. */
	static final int MAX_HIT = 3;

	/** When each kind's hit lands after its attack animation, in ticks. */
	enum Style
	{
		/** Zombie: next to its target. */
		MELEE(1, 1),

		/** Ghost: a tick later from further off. */
		MAGIC(1, 2),

		/** Skeleton: its arrow is slowest. */
		RANGED(2, 3);

		private final int from;
		private final int to;

		Style(int from, int to)
		{
			this.from = from;
			this.to = to;
		}
	}

	/** More than any thrall can have in flight; a guard against leaks, not a limit in play. */
	private static final int MAX_PENDING = 4;

	/** A ghost's first attack comes as its spawn animation ends, with no attack animation. */
	private static final int GHOST_FIRST_ATTACK = 4;

	private static final class Attack
	{
		private final int tick;
		private final Style style;

		/** Never animated, only expected; an attack that is seen replaces it. */
		private final boolean unseen;

		/** A small splat off the boss was already left to it. */
		private boolean tookOther;

		private Attack(int tick, Style style, boolean unseen)
		{
			this.tick = tick;
			this.style = style;
			this.unseen = unseen;
		}

		private boolean covers(int at)
		{
			return at - tick >= style.from && at - tick <= style.to;
		}
	}

	private final List<Attack> pending = new ArrayList<>();

	/** The attack whose window last took a splat on the boss, and that splat's size and tick. */
	private Attack taker;
	private int tookAmount;
	private int tookAt;

	void reset()
	{
		pending.clear();
		taker = null;
	}

	/**
	 * The attacking style of a thrall's attack animation, or null for anything else it does
	 * (spawning, walking, standing).
	 */
	static Style attackStyle(int npcId, int animation)
	{
		if (npcId < NpcID.ARCEUUS_THRALL_GHOST_LESSER || npcId > NpcID.ARCEUUS_THRALL_ZOMBIE_GREATER)
		{
			return null;
		}

		switch (animation)
		{
			case AnimationID.GHOST_UPDATE_TENDRILL_ATTACK_THRALL:
				return Style.MAGIC;

			case AnimationID.SKELETON_UPDATE_CHAMPION_ATTACK_THRALL:
				return Style.RANGED;

			case AnimationID.ZOMBIE_UPDATE_ATTACK_NORMAL_THRALL:
				return Style.MELEE;

			default:
				return null;
		}
	}

	/**
	 * Whether a thrall has just been summoned. Read off its spawn animation, not its spawning: a
	 * thrall coming back into view spawns again and attacks on its own time.
	 */
	static boolean isSummoning(int animation)
	{
		return animation == AnimationID.GHOST_UPDATE_THRALL_SPAWN_RAISED
			|| animation == AnimationID.SKELETON_UPDATE_THRALL_SPAWN_RAISED
			|| animation == AnimationID.ZOMBIE_UPDATE_THRAWL_SPAWN_RAISED;
	}

	/**
	 * A ghost attacks as its spawn animation ends, four ticks in, and that attack is never
	 * animated. When it waits a tick longer it is animated, and {@link #attacked} replaces this one.
	 */
	void spawned(int npcId, int tick)
	{
		if (npcId >= NpcID.ARCEUUS_THRALL_GHOST_LESSER && npcId <= NpcID.ARCEUUS_THRALL_GHOST_GREATER)
		{
			add(new Attack(tick + GHOST_FIRST_ATTACK, Style.MAGIC, true));
		}
	}

	void attacked(Style style, int tick)
	{
		pending.removeIf(attack -> attack.unseen);
		add(new Attack(tick, style, false));
	}

	private void add(Attack attack)
	{
		if (pending.size() >= MAX_PENDING)
		{
			pending.remove(0);
		}

		pending.add(attack);
	}

	/**
	 * Whether a splat of ours is the thrall's. One on the boss spends the attack it came from. One
	 * anywhere else is left to the thrall once and keeps the attack open: it may be our own hit on
	 * a larva, with the thrall's still to land on the boss.
	 *
	 * @param amount the splat's amount; a 0 is a thrall's miss as much as ours
	 * @param onBoss whether it landed where damage counts
	 */
	boolean isThrallHit(int amount, int tick, boolean onBoss)
	{
		pending.removeIf(attack -> tick - attack.tick > attack.style.to);

		if (amount > MAX_HIT)
		{
			return false;
		}

		// The oldest first: the next attack's window can open before this one's closes.
		for (int i = 0; i < pending.size(); i++)
		{
			Attack attack = pending.get(i);

			if (!attack.covers(tick))
			{
				continue;
			}

			if (onBoss)
			{
				pending.remove(i);
				taker = attack;
				tookAmount = amount;
				tookAt = tick;
				return true;
			}

			if (!attack.tookOther)
			{
				attack.tookOther = true;
				return true;
			}
		}

		return false;
	}

	/** The splat a window took on the boss on this tick, or -1 when none did. */
	int tookOnBoss(int tick)
	{
		return taker != null && tookAt == tick ? tookAmount : -1;
	}

	/**
	 * That splat was not the thrall's after all - see {@link PunishTracker}. Its attack is open
	 * again for the splat that is.
	 */
	void handBack()
	{
		if (taker != null)
		{
			pending.add(0, taker);
			taker = null;
		}
	}
}
