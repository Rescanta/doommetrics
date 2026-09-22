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

	/** A cause with a window still open, and how much of its effect is still unaccounted for. */
	private static final class Pending
	{
		private final int openedAt;
		private final SpecEffect effect;
		private int left;

		private Pending(int openedAt, SpecEffect effect)
		{
			this.openedAt = openedAt;
			this.effect = effect;
			this.left = effect.budget();
		}

		private boolean accepts(SpecEffect.Kind kind, long amount, int tick)
		{
			return left > 0 && effect.kind() == kind && effect.covers(tick - openedAt)
				&& effect.isSized(amount);
		}

		private boolean isExpired(int tick)
		{
			return left <= 0 || tick - openedAt > effect.to();
		}
	}

	/**
	 * An effect that arrived before its cause, kept for its own tick: the client reads players before
	 * NPCs, so a heal can come before the graphic that explains it.
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
		if (weapon == null)
		{
			return;
		}

		open(weapon.effects(), tick);
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
			SpecEffect.Kind.HEAL, metric, 0, SPELL_WINDOW, 1)), tick);
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
		Pending source = best(kind, amount, tick);
		return source == null ? null : source.effect.metric();
	}

	private void open(List<SpecEffect> effects, int tick)
	{
		prune(tick);

		for (SpecEffect effect : effects)
		{
			if (pending.size() >= MAX_PENDING)
			{
				pending.remove(0);
			}

			Pending opened = new Pending(tick, effect);
			pending.add(opened);
			claimHeld(opened);
		}
	}

	private void credit(SpecEffect.Kind kind, long amount, int tick)
	{
		prune(tick);

		Pending source = best(kind, amount, tick);

		if (source == null)
		{
			hold(kind, amount, tick);
			return;
		}

		source.left--;
		sink.record(source.effect.metric(), amount);
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
				opened.left--;
				sink.record(opened.effect.metric(), effect.amount);
			}
		}
	}

	/** The most recently opened window that accepts the effect. */
	private Pending best(SpecEffect.Kind kind, long amount, int tick)
	{
		Pending best = null;

		for (Pending candidate : pending)
		{
			if (candidate.accepts(kind, amount, tick)
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
