package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;

/**
 * Tells a thrall's hits from ours. Its splats are drawn as the player's own, so each attack opens
 * a window for the one hit it lands, and the first small splat of ours inside it is taken as that
 * hit. When one of our own 0-3s beats the thrall's to it, the swap costs at most three damage and
 * still leaves the right number of hits. No RuneLite types.
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

	private static final class Attack
	{
		private final int tick;
		private final Style style;

		private Attack(int tick, Style style)
		{
			this.tick = tick;
			this.style = style;
		}

		private boolean covers(int at)
		{
			return at - tick >= style.from && at - tick <= style.to;
		}
	}

	private final List<Attack> pending = new ArrayList<>();

	void reset()
	{
		pending.clear();
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

	void attacked(Style style, int tick)
	{
		if (pending.size() >= MAX_PENDING)
		{
			pending.remove(0);
		}

		pending.add(new Attack(tick, style));
	}

	/**
	 * Whether a splat of ours is the thrall's, which spends the attack it came from.
	 *
	 * @param amount the splat's amount; a 0 is a thrall's miss as much as ours
	 */
	boolean isThrallHit(int amount, int tick)
	{
		pending.removeIf(attack -> tick - attack.tick > attack.style.to);

		if (amount > MAX_HIT)
		{
			return false;
		}

		// The oldest first: the next attack's window can open before this one's closes.
		for (int i = 0; i < pending.size(); i++)
		{
			if (pending.get(i).covers(tick))
			{
				pending.remove(i);
				return true;
			}
		}

		return false;
	}
}
