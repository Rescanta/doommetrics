package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Works out which hitsplats on the boss were a melee punish, and which weapon swung it. A swing is
 * a punish if the boss's prayer was up, or if its hits cut the beam off. Hitsplats after a swing
 * are held to the end of the tick, when both signs and the weapon are known. No RuneLite types.
 */
class PunishTracker
{
	/** Where a hit of ours goes once it is settled - see {@link #tickEnded}. */
	interface Handback
	{
		void damaged(int amount, int tick);
	}

	/**
	 * How long after the swing a punish's hitsplats may land, in ticks. Nothing the player does
	 * after the punish can land inside it.
	 */
	static final int HIT_WINDOW = 3;

	private static final int MAX_HELD = 16;

	/** A strength-bonus splat lands two ticks after the swing at the soonest, a tick after its hit. */
	static final int BONUS_FROM = 2;

	private static final int NONE = Integer.MIN_VALUE;

	/** A hitsplat on the boss waiting on the end of its tick. */
	private static final class Held
	{
		private final int amount;
		private final boolean mine;
		private final int tick;

		private Held(int amount, boolean mine, int tick)
		{
			this.amount = amount;
			this.mine = mine;
			this.tick = tick;
		}
	}

	private final CombatTracker.Sink sink;
	private final Handback handback;

	/** Whether the boss was praying as of the last tick's end. */
	private boolean praying;

	/** The tick the prayer went up, or {@link #NONE} before it ever has. */
	private int raisedAt = NONE;

	/** The tick the prayer came down, or {@link #NONE} while it is up. */
	private int droppedAt = NONE;

	/** The tick the standing boss last cut its beam off, or {@link #NONE}. */
	private int cancelledAt = NONE;

	/** The tick of the swing the window is open for, or {@link #NONE} when there is none. */
	private int swungAt = NONE;

	/** What made that swing, or null until the tick it was made on has ended. */
	private PunishWeapon weapon;

	/**
	 * Our hits on the boss since the swing, its own tick included, and the bonus splats credited.
	 * The game draws a bonus splat per hit, so one past them is something else - a larva
	 * exploding by the boss.
	 */
	private int ownHits;
	private int bonusesCounted;

	private final List<Held> held = new ArrayList<>();

	/**
	 * @param sink     where a punish's damage is credited
	 * @param handback where our other hits go: whole if no punish, as zero if it was one, so a spec
	 *                 swung at a punish still spends its budget
	 */
	PunishTracker(CombatTracker.Sink sink, Handback handback)
	{
		this.sink = sink;
		this.handback = handback;
	}

	/** Forgets everything, for the same reasons {@link CombatTracker#reset()} does. */
	void reset()
	{
		praying = false;
		raisedAt = NONE;
		droppedAt = NONE;
		cancelledAt = NONE;
		swungAt = NONE;
		weapon = null;
		ownHits = 0;
		bonusesCounted = 0;
		held.clear();
	}

	boolean isPraying()
	{
		return praying;
	}

	/**
	 * The player started an animation; it's a swing only if a melee weapon is in hand at tick end.
	 */
	void swung(int tick)
	{
		// Only the first animation of a tick counts, and nothing inside a punish's window moves it
		// on.
		if (swungAt != NONE && (weapon == null || (tick - swungAt <= HIT_WINDOW && isPunish())))
		{
			return;
		}

		swungAt = tick;
		weapon = null;
		ownHits = 0;
		bonusesCounted = 0;
	}

	/** The boss, standing, cut its beam off. */
	void beamCancelled(int tick)
	{
		cancelledAt = tick;
	}

	/**
	 * Whether a hitsplat on the boss now must be held by {@link #hit}. Spends nothing. A melee hit
	 * lands a tick after its swing, so one on the swing's own tick was fired before it.
	 */
	boolean mayBePunish(int tick)
	{
		return swungAt != NONE && tick > swungAt && tick - swungAt <= HIT_WINDOW;
	}

	/**
	 * One of our hits on the boss that isn't held, e.g. a bow's landing on the swing's own tick: it
	 * still brings a bonus splat of its own if the swing was a punish.
	 */
	void ownHitNotHeld(int tick)
	{
		if (tick == swungAt)
		{
			ownHits++;
		}
	}

	/**
	 * A hitsplat on the boss held until the tick ends.
	 *
	 * @param mine whether it is plainly ours (a strength-bonus splat is not); only those are handed
	 * back
	 */
	void hit(int amount, boolean mine, int tick)
	{
		if (amount < 0)
		{
			return;
		}

		// Settled early rather than dropped, so an upstream bug costs the oldest hit its punish
		// rather than costing the spec tracker the hit altogether.
		if (held.size() >= MAX_HELD)
		{
			settle(held.remove(0), false);
		}

		if (mine)
		{
			ownHits++;
		}

		held.add(new Held(amount, mine, tick));
	}

	/**
	 * Settles everything held for the tick.
	 *
	 * @param equipped the melee weapon held, or null; only asked for when a swing is waiting
	 */
	void tickEnded(int tick, boolean prayerUp, Supplier<PunishWeapon> equipped)
	{
		if (prayerUp && !praying)
		{
			raisedAt = tick;
			droppedAt = NONE;
		}
		else if (!prayerUp && praying)
		{
			droppedAt = tick;
		}

		praying = prayerUp;

		if (swungAt != NONE && weapon == null)
		{
			weapon = equipped.get();

			if (weapon == null)
			{
				// Not a melee swing - a spell, a throw, a potion. Nothing it hit for was a punish.
				swungAt = NONE;
			}
		}

		boolean punish = swungAt != NONE && isPunish();

		for (Held hit : held)
		{
			settle(hit, punish);
		}

		held.clear();
	}

	private void settle(Held hit, boolean punish)
	{
		if (punish && !hit.mine && !isBonus(hit))
		{
			return;
		}

		if (punish && hit.amount > 0)
		{
			sink.record(weapon.metric(), hit.amount);
		}

		if (hit.mine)
		{
			handback.damaged(punish ? 0 : hit.amount, hit.tick);
		}
	}

	/** Whether a splat that isn't plainly ours is one of the punish's bonus splats; counts it. */
	private boolean isBonus(Held hit)
	{
		if (hit.tick - swungAt < BONUS_FROM || bonusesCounted >= ownHits)
		{
			return false;
		}

		bonusesCounted++;
		return true;
	}

	/**
	 * Whether the swing the window is open for was a punish - see the class notes for the two
	 * signs.
	 */
	private boolean isPunish()
	{
		boolean underPrayer = raisedAt != NONE && swungAt >= raisedAt
			&& (praying || swungAt <= droppedAt);

		boolean cutTheBeam = cancelledAt != NONE && cancelledAt >= swungAt
			&& cancelledAt - swungAt <= HIT_WINDOW;

		return underPrayer || cutTheBeam;
	}
}
