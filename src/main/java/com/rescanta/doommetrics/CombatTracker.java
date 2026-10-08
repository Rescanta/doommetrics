package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Works out what caused each heal, prayer restore and spec hitsplat. Each cause the game reports
 * opens a window, and an effect is credited to the most recent window that accepts it. Effects
 * nothing explains are dropped, so every figure is a floor. No RuneLite types, so it can be tested.
 */
class CombatTracker
{
	/** Where an attributed amount goes. The plugin points this at the run in progress. */
	interface Sink
	{
		void record(CombatMetric metric, long amount);
	}

	/** How many causes may be in flight at once; the oldest is dropped first. */
	private static final int MAX_PENDING = 16;

	/** How long after a blood spell lands its heal may still arrive, in ticks. */
	private static final int SPELL_WINDOW = 2;

	/** How many effects arriving ahead of their cause may be held; the oldest is dropped first. */
	private static final int MAX_HELD = 8;

	/**
	 * The least and most hitpoints experience a point of damage on the boss earns: 4/3 times the
	 * boss's modifier, 2.11 to 2.40 over 2811 attacks on delves 1 to 43, with room either side.
	 */
	private static final double LEAST_EXPERIENCE = 2.0;
	private static final double MOST_EXPERIENCE = 2.5;

	/** Damage either way for the rounding, and for a larva's point of experience on the same tick. */
	private static final int EXPERIENCE_SLACK = 1;

	/** A cause with a window still open, and how much of its effect is still unaccounted for. */
	private static final class Pending
	{
		private final int openedAt;
		private final SpecEffect effect;

		/** The least a hit of this cause's can be, short of a 0; 0 when anything can be. */
		private final int leastHit;

		/**
		 * The least and the most the cause's own hits come to, by the experience it earned; 0 and
		 * no limit when that says nothing.
		 */
		private final long least;
		private final long most;

		private int left;

		/** What has been credited so far. */
		private long taken;

		/** Its hit was counted somewhere else, so there is none still to come. */
		private boolean settled;

		private Pending(int openedAt, SpecEffect effect, int leastHit, int experience)
		{
			boolean known = experience > 0 && effect.isOfTheAttack();

			this.openedAt = openedAt;
			this.effect = effect;
			this.leastHit = effect.kind() == SpecEffect.Kind.DAMAGE ? leastHit : 0;
			this.least = known
				? Math.max(0, (long) Math.floor(experience / MOST_EXPERIENCE) - EXPERIENCE_SLACK)
				: 0;
			this.most = known
				? (long) Math.ceil(experience / LEAST_EXPERIENCE) + EXPERIENCE_SLACK
				: Long.MAX_VALUE;
			this.left = effect.budget();
		}

		private boolean accepts(SpecEffect.Kind kind, long amount, int tick)
		{
			if (effect.kind() != kind || !effect.covers(tick - openedAt) || !effect.isSized(amount))
			{
				return false;
			}

			if (left > 0)
			{
				return amount == 0 || (amount >= leastHit && amount <= most - taken);
			}

			// The hit it is still short of: one that is what the experience says, by itself.
			return isShort() && amount >= least && amount <= most;
		}

		/**
		 * A one-hit cause that took something too small to be what its experience says it did: a
		 * miss or a small hit of another attack's landed first, and its own is still to come.
		 */
		private boolean isShort()
		{
			return left <= 0 && effect.budget() == 1 && !settled && taken < least;
		}

		/** Takes an effect; returns how much of it is new, over what a short cause took before. */
		private long take(long amount)
		{
			long share = left > 0 ? amount : amount - taken;
			left--;
			taken += share;
			return share;
		}

		private boolean isExpired(int tick)
		{
			return tick - openedAt > effect.to() || (left <= 0 && !isShort());
		}
	}

	/**
	 * An effect that arrived before its cause, kept for its own tick: the client reads players
	 * before NPCs, so a heal can come before the graphic that explains it.
	 */
	private static final class Held
	{
		private final SpecEffect.Kind kind;
		private final long amount;
		private final int tick;

		private Held(SpecEffect.Kind kind, long amount, int tick)
		{
			this.kind = kind;
			this.amount = amount;
			this.tick = tick;
		}
	}

	private final Sink sink;
	private final List<Pending> pending = new ArrayList<>();
	private final List<Held> held = new ArrayList<>();

	/** The last spell signal taken, so two views of one cast do not open two windows. */
	private CombatMetric lastSpell;
	private int lastSpellTick;

	CombatTracker(Sink sink)
	{
		this.sink = sink;
	}

	/** Forgets everything in flight; called when a run starts and ends. */
	void reset()
	{
		pending.clear();
		held.clear();
		lastSpell = null;
	}

	/** The special attack energy was spent while {@code weapon} was held. */
	void specFired(SpecWeapon weapon, int tick)
	{
		specFired(weapon, tick, 0);
	}

	/**
	 * @param leastHit the least the spec can hit for unless it misses, where that is known - see
	 *                 {@link SpecWeapon#leastRubyBoltHit}; a smaller hit is left for something else
	 */
	void specFired(SpecWeapon weapon, int tick, int leastHit)
	{
		specFired(weapon, tick, leastHit, 0);
	}

	/**
	 * @param experience the hitpoints experience gained on the spec's tick, 0 if none. An attack
	 *                   earns it as it is made, in proportion to what it will hit for, so the
	 *                   spec's own hits come to no more than it says and a hit too big for it is
	 *                   another attack's. A one-hit spec that took something too small first - a
	 *                   miss of another attack's, usually - still takes the hit that fits. None
	 *                   earned says nothing: a miss earns none, but neither does anything at the
	 *                   experience cap.
	 */
	void specFired(SpecWeapon weapon, int tick, int leastHit, int experience)
	{
		if (weapon == null)
		{
			return;
		}

		open(weapon.effects(), tick, leastHit, experience);
	}

	/** @param metric which spell's heal to credit - blood barrage, or the grouped rest */
	void spellHit(CombatMetric metric, int tick)
	{
		// One cast heals once: later signals on the same tick are echoes of it.
		if (lastSpell != null && tick == lastSpellTick)
		{
			return;
		}

		lastSpell = metric;
		lastSpellTick = tick;

		open(Collections.singletonList(new SpecEffect(
			SpecEffect.Kind.HEAL, metric, 0, SPELL_WINDOW, 1)), tick, 0, 0);
	}

	/** A heal hitsplat landed on the player. */
	void healed(int amount, int tick)
	{
		if (amount > 0)
		{
			credit(SpecEffect.Kind.HEAL, amount, tick);
		}
	}

	/** A hitsplat of ours landed on something else. */
	void damaged(int amount, int tick)
	{
		// A block is a zero and is still a hit the spec spent itself on, so it consumes a hit from
		// the budget the same as any other - it just adds nothing to the total.
		if (amount >= 0)
		{
			credit(SpecEffect.Kind.DAMAGE, amount, tick);
		}
	}

	/**
	 * A hitsplat of ours from an attack made before the swing at {@code swing}, so no spec fired
	 * from then on is its cause.
	 *
	 * @return what it was credited to, or null
	 */
	CombatMetric damagedBefore(int amount, int tick, int swing)
	{
		return amount < 0 ? null
			: credit(SpecEffect.Kind.DAMAGE, amount, tick, Integer.MIN_VALUE, swing - 1);
	}

	/**
	 * A hit of the swing at {@code swing} counted elsewhere: a spec fired with that swing or after
	 * has spent a hit on it, and one fired before has not.
	 *
	 * @return the figure whose spec spent the hit, or null
	 */
	CombatMetric spent(int tick, int swing)
	{
		prune(tick);

		Pending source = best(SpecEffect.Kind.DAMAGE, 0, tick, swing, Integer.MAX_VALUE);

		if (source == null)
		{
			return null;
		}

		source.settled = true;
		sink.record(source.effect.metric(), source.take(0));
		return source.effect.metric();
	}

	/** The player's prayer points went up by {@code amount}. */
	void prayerGained(int amount, int tick)
	{
		if (amount > 0)
		{
			credit(SpecEffect.Kind.PRAYER, amount, tick);
		}
	}

	/**
	 * The metric an effect of {@code kind} and this size would be credited to now, or null. Spends
	 * nothing.
	 */
	CombatMetric wouldCredit(SpecEffect.Kind kind, long amount, int tick)
	{
		Pending source = best(kind, amount, tick, Integer.MIN_VALUE, Integer.MAX_VALUE);
		return source == null ? null : source.effect.metric();
	}

	private void open(List<SpecEffect> effects, int tick, int leastHit, int experience)
	{
		prune(tick);

		for (SpecEffect effect : effects)
		{
			if (pending.size() >= MAX_PENDING)
			{
				pending.remove(0);
			}

			Pending opened = new Pending(tick, effect, leastHit, experience);
			pending.add(opened);
			claimHeld(opened);
		}
	}

	private void credit(SpecEffect.Kind kind, long amount, int tick)
	{
		prune(tick);

		long rest = amount;

		// Two causes on one tick arrive as one amount: a cause that can only be so much of it
		// takes up to that much first, and the rest is the other's.
		for (Pending part = limited(kind, rest, tick); part != null && rest > 0;
			part = limited(kind, rest, tick))
		{
			long share = Math.min(rest, part.effect.most());
			part.take(share);
			sink.record(part.effect.metric(), share);
			rest -= share;
		}

		if (rest == 0 && amount > 0)
		{
			return;
		}

		if (credit(kind, rest, tick, Integer.MIN_VALUE, Integer.MAX_VALUE) == null)
		{
			hold(kind, rest, tick);
		}
	}

	/** The most recently opened window that accepts the effect and has a limit to what it takes. */
	private Pending limited(SpecEffect.Kind kind, long amount, int tick)
	{
		Pending limited = null;

		for (Pending candidate : pending)
		{
			if (candidate.effect.most() != SpecEffect.NO_LIMIT && candidate.accepts(kind, amount, tick)
				&& (limited == null || candidate.openedAt >= limited.openedAt))
			{
				limited = candidate;
			}
		}

		return limited;
	}

	/** Credits an effect to a window opened between two ticks, inclusive; null if none takes it. */
	private CombatMetric credit(SpecEffect.Kind kind, long amount, int tick, int from, int to)
	{
		prune(tick);

		Pending source = best(kind, amount, tick, from, to);

		if (source == null)
		{
			return null;
		}

		sink.record(source.effect.metric(), source.take(amount));
		return source.effect.metric();
	}

	/** Keeps an effect that nothing explained yet, in case its cause is still to be noticed. */
	private void hold(SpecEffect.Kind kind, long amount, int tick)
	{
		if (held.size() >= MAX_HELD)
		{
			held.remove(0);
		}

		held.add(new Held(kind, amount, tick));
	}

	/** Gives a window that has just opened whatever arrived unexplained on its tick. */
	private void claimHeld(Pending opened)
	{
		Iterator<Held> effects = held.iterator();

		while (effects.hasNext())
		{
			Held effect = effects.next();

			if (opened.accepts(effect.kind, effect.amount, effect.tick))
			{
				effects.remove();
				sink.record(opened.effect.metric(), opened.take(effect.amount));
			}
		}
	}

	/** The most recently opened window that accepts the effect, of those opened in a range. */
	private Pending best(SpecEffect.Kind kind, long amount, int tick, int from, int to)
	{
		Pending best = null;

		for (Pending candidate : pending)
		{
			if (candidate.openedAt >= from && candidate.openedAt <= to
				&& candidate.accepts(kind, amount, tick)
				&& (best == null || candidate.openedAt >= best.openedAt))
			{
				best = candidate;
			}
		}

		return best;
	}

	private void prune(int tick)
	{
		pending.removeIf(p -> p.isExpired(tick));

		// Only what arrived before the tick being processed. A cause noticed a step late still
		// carries the tick it was fired on, so its own holds are not the ones being dropped.
		held.removeIf(h -> h.tick < tick);
	}
}
