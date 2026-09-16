package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Works out which hitsplats on the boss were a melee punish, and which weapon swung it.
 *
 * <p>A punish is the boss charging its beam behind a prayer against magic and ranged, and the
 * player answering with a melee weapon. That one swing lands with full accuracy, cuts the beam off,
 * and brings strength-bonus hitsplats in a tick behind it, one for each hit of the swing. The swing
 * and the bonus are both what the punish figures count.
 *
 * <p>A swing is a punish if the prayer was up when it was made, or if its hits cut the beam off.
 * The prayer is the usual sign but not the only one: a swing that lands on the very tick the boss
 * starts to charge interrupts the beam before the prayer is ever drawn. That one brings no bonus
 * hitsplats, but its hits are the punish all the same, and the beam being cut off on the tick they
 * land is what gives it away. The boss cuts its beam off over and over on its own while it is
 * shielded, but nothing that lands on it then counts, so none of those is ever read against a hit.
 *
 * <p>So every hitsplat on the boss in the ticks after a melee swing is held until the end of its
 * tick, when both signs are in and the weapon behind the swing can be read. A punish's are credited
 * to that weapon; anything else is handed back, to be counted the way any other hit is. The weapon
 * waits for the tick as well: the swing is noticed by its animation, and a weapon switched to and
 * swung on one tick is only reliably in hand by its end.
 *
 * <p>No RuneLite types, for the reason the other tracker has none: the plugin does the watching and
 * this does the deciding, which is what makes the deciding testable.
 */
class PunishTracker
{
	/** Where a hit of ours goes once it is settled - see {@link #tickEnded}. */
	interface Handback
	{
		void damaged(int amount, int tick);
	}

	/**
	 * How long after the swing a punish's hitsplats may still land, in game ticks.
	 *
	 * <p>A melee hit lands the tick after the swing, and the strength-bonus splats a tick behind
	 * that. The limit that matters is the other side: the fastest weapon swung at a punish - claws
	 * or a dagger - cannot attack again for four ticks, whatever it is switched to, so nothing the
	 * player does after the punish can land inside this.
	 */
	static final int HIT_WINDOW = 3;

	/**
	 * How many hitsplats one tick can hold before it is settled. A punish is three scythe hits and
	 * three bonus splats at the most, and whatever else lands with them is a handful more.
	 */
	private static final int MAX_HELD = 16;

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

	private final List<Held> held = new ArrayList<>();

	/**
	 * @param sink     where a punish's damage is credited
	 * @param handback where a hit of ours goes otherwise: whole if it was no punish, as nothing if
	 *                 it was - a halberd or claw spec swung at a punish still spent a hit on it,
	 *                 and the spec tracker has to hear of the hit to know its budget is spent
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
		held.clear();
	}

	boolean isPraying()
	{
		return praying;
	}

	/**
	 * The player started an animation. Every one is offered, because an attack is not told from
	 * any other animation until the weapon behind it is read - and one that turns out not to be a
	 * melee swing is dropped then.
	 */
	void swung(int tick)
	{
		// The first animation of a tick is the one read at its end. And a punish is one swing, so
		// the window it opened stays with it: nothing can attack again inside it, which makes
		// anything else started there - a switch, a potion - not a swing, and it must not move
		// the window on or take the splats still to land. A swing that was no punish holds
		// nothing, so the punish right after it is not lost to it.
		if (swungAt != NONE && (weapon == null || (tick - swungAt <= HIT_WINDOW && isPunish())))
		{
			return;
		}

		swungAt = tick;
		weapon = null;
	}

	/** The boss, standing, cut its beam off. */
	void beamCancelled(int tick)
	{
		cancelledAt = tick;
	}

	/**
	 * Whether a hitsplat on the boss landing now could be a punish's, and so has to be held by
	 * {@link #hit} rather than counted as it arrives. Nothing is spent by asking.
	 */
	boolean mayBePunish(int tick)
	{
		return swungAt != NONE && tick >= swungAt && tick - swungAt <= HIT_WINDOW;
	}

	/**
	 * A hitsplat on the boss that {@link #mayBePunish} said could be a punish's, held until the
	 * tick ends.
	 *
	 * @param mine whether it is one of the types that is plainly ours, as a strength-bonus splat is
	 *             not - only those are handed back when it turns out to be no punish
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

		held.add(new Held(amount, mine, tick));
	}

	/**
	 * The tick has ended: whether the boss is praying now, and - if a swing is waiting on it - the
	 * weapon in hand. Everything held for the tick is settled here.
	 *
	 * @param equipped the melee weapon held, or null for anything else. Only asked for when a swing
	 *                 is waiting, so a tick without one costs no equipment lookup.
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
		if (punish && hit.amount > 0)
		{
			sink.record(weapon.metric(), hit.amount);
		}

		if (hit.mine)
		{
			handback.damaged(punish ? 0 : hit.amount, hit.tick);
		}
	}

	/** Whether the swing the window is open for was a punish - see the class notes for the two signs. */
	private boolean isPunish()
	{
		boolean underPrayer = raisedAt != NONE && swungAt >= raisedAt
			&& (praying || swungAt <= droppedAt);

		boolean cutTheBeam = cancelledAt != NONE && cancelledAt >= swungAt
			&& cancelledAt - swungAt <= HIT_WINDOW;

		return underPrayer || cutTheBeam;
	}
}
