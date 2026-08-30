package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Works out what caused each heal, each restored prayer point and each spec hitsplat, and hands the
 * answer to whoever is keeping score.
 *
 * <p>The game does not say. A blood barrage heal, a blowpipe spec heal and a bite of a saradomin
 * brew are the same hitpoints going up, and a spec hitsplat looks exactly like the auto-attack
 * after it. What the game does say, plainly, is when the special attack energy moved, what was
 * equipped when it did, and when a blood spell landed on something. So this keeps a window open
 * after each of those, and credits an effect to a window it lands in.
 *
 * <p>An effect can reach us before its own cause does, so one that explains nothing waits out the
 * tick it arrived on before it is given up on - see {@link Held}. Anything still unexplained at the
 * end of that tick is dropped rather than guessed at, and that is the point rather than a
 * shortcoming: brews, food, regeneration and prayer potions are all heals, and a tracker that
 * counted them would report a sustain figure the player's gear never earned. Every number here is
 * therefore a floor - what we could prove - and never an over-count.
 *
 * <p>Any number of causes can be in flight at once, which is the whole reason this is a list rather
 * than a slot per kind. Specs get chained into one another as a matter of course, and the Ancient
 * godsword does not pay out until nine ticks after it was fired - so a design that remembered only
 * the most recent spec lost every Blood Sacrifice to whatever was fired next, and lost barrage heals
 * to any later spell in the same way.
 *
 * <p>When two windows could both explain one effect, the more recent takes it. That is a guess, but
 * a bounded one: both causes really did produce a heal on that tick, so both are credited as their
 * heals arrive and only which amount went to which is uncertain.
 *
 * <p>No RuneLite types here on purpose - the plugin does the watching and this does the deciding,
 * which is what makes the deciding testable.
 */
class CombatTracker
{
	/** Where an attributed amount goes. The plugin points this at the run in progress. */
	interface Sink
	{
		void record(CombatMetric metric, long amount);
	}

	/**
	 * How many causes may be in flight at once.
	 *
	 * <p>Comfortably more than play produces - the longest window is ten ticks, and nobody fires
	 * more than a handful of specs and spells in that - and small enough that a bug upstream cannot
	 * grow this into a leak. Oldest is dropped first, which is also the one closest to expiring.
	 */
	private static final int MAX_PENDING = 16;

	/**
	 * How long after a blood spell lands its heal may still arrive, in game ticks. The heal is part
	 * of the same hit, so this only has to cover the hitsplats of one tick arriving in either
	 * order.
	 */
	private static final int SPELL_WINDOW = 2;

	/**
	 * How many effects arriving ahead of their cause may be held at once.
	 *
	 * <p>Only ever one tick's worth, and one tick brings a handful of hitsplats at the outside.
	 * Oldest is dropped first, so an upstream bug costs the oldest hold rather than growing.
	 */
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

		private boolean accepts(SpecEffect.Kind kind, int tick)
		{
			return left > 0 && effect.kind() == kind && effect.covers(tick - openedAt);
		}

		private boolean isExpired(int tick)
		{
			return left <= 0 || tick - openedAt > effect.to();
		}
	}

	/**
	 * An effect that arrived before anything could explain it, kept for the tick it arrived on.
	 *
	 * <p>Effects do not reach us in the order the game produced them. A heal lands on the player,
	 * and the graphic that says which spell caused it lands on the target - and the client reads
	 * the players in a tick before the NPCs, so a blood barrage's heal is offered a tick's worth of
	 * events before there is a barrage to credit it to. The same goes for a melee spec, whose hit
	 * arrives on the tick it was fired while the weapon that fired it can only be read a step
	 * later.
	 *
	 * <p>So an unexplained effect waits out its tick rather than being thrown away at once. Nothing
	 * is loosened by this: a cause opening on a later tick prunes the hold before it could claim
	 * it, and a window that does not start on the tick of its cause never accepts one at all.
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

	/**
	 * Forgets everything in flight. Called when a run starts and when one ends, so a spec fired on
	 * the way out of the cave cannot be credited with a heal on the way into the next trip.
	 */
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

	/**
	 * A healing spell landed on something.
	 *
	 * @param metric which spell's heal to credit - blood barrage, or the grouped rest
	 */
	void spellHit(CombatMetric metric, int tick)
	{
		// One cast heals once, however many targets it lit up and however many of the client's
		// signals reported it - the chat line and the impact graphic are two views of the same
		// cast. Two windows for one cast would leave a spare one open, and a brew swallowed on the
		// same tick would find it. Nobody casts two healing spells on one tick, so the first
		// signal of a tick is the cast and the rest are echoes of it.
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
	 * The metric an effect of {@code kind} arriving now would be credited to, or null if none would
	 * be. Nothing is spent by asking, so this is for logging what the tracker is about to do.
	 */
	CombatMetric wouldCredit(SpecEffect.Kind kind, int tick)
	{
		Pending source = best(kind, tick);
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

		Pending source = best(kind, tick);

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

	/**
	 * Gives a window that has just opened whatever already arrived on its tick and matched nothing.
	 *
	 * <p>Held effects are only ever from the tick being processed, so this credits nothing a
	 * simultaneous cause could not have produced.
	 */
	private void claimHeld(Pending opened)
	{
		Iterator<Held> effects = held.iterator();

		while (effects.hasNext())
		{
			Held effect = effects.next();

			if (opened.accepts(effect.kind, effect.tick))
			{
				effects.remove();
				opened.left--;
				sink.record(opened.effect.metric(), effect.amount);
			}
		}
	}

	/**
	 * Whichever open window explains an effect best: of those that could, the one opened most
	 * recently. Windows that start at different delays rarely overlap at all - which is the point
	 * of them being ranges - so this only decides genuinely simultaneous cases.
	 */
	private Pending best(SpecEffect.Kind kind, int tick)
	{
		Pending best = null;

		for (Pending candidate : pending)
		{
			if (candidate.accepts(kind, tick)
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
