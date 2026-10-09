package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * A second reading of whose hit each hitsplat of ours is, kept beside the trackers and only ever
 * written to the debug log. Every attack is a record of when it was made, what at and the tick it
 * should land on, and a splat goes to the record due nearest its tick on what it hit, of those
 * whose experience allows a hit of its size. Splats are settled a tick late, when every attack
 * that could have landed one has been heard of: a projectile is first reported as it starts to
 * move, a tick or two after its attack. No RuneLite types.
 */
final class AttackLedger
{
	/** A target nothing could name: a zombie thrall's, or a swing made at nothing. */
	static final int UNKNOWN = -1;

	enum Kind
	{
		/** Our own attack with nothing seen to fly: lands a tick after its animation. */
		SWING("swing", 0, 2, false, 0, Integer.MAX_VALUE),

		/** Our own projectile, due on the tick its flight ends. */
		SHOT("shot", 1, 1, false, 0, Integer.MAX_VALUE),

		/**
		 * A spell with nothing seen to fly, which hits everything in its reach: two ticks on from
		 * beside its target and one more for every three tiles.
		 */
		CAST("cast", 0, 3, false, 0, Integer.MAX_VALUE),

		/**
		 * Blood Sacrifice, the ancient godsword's second hit: always the same and earning
		 * nothing. It follows a spec that landed, also one the shield took for a 0.
		 */
		SACRIFICE("sacrifice", 1, 1, false, SpecWeapon.SACRIFICE_DAMAGE, SpecWeapon.SACRIFICE_DAMAGE),

		THRALL_SHOT("thrall shot", 1, 1, true, 0, ThrallTracker.MAX_HIT),

		/** A zombie thrall's attack, which has no projectile and no readable target. */
		THRALL_SWING("thrall swing", 0, 0, true, 0, ThrallTracker.MAX_HIT);

		private final String label;

		/** How many ticks ahead of its due tick, and after it, a hit may still be this attack's. */
		private final int early;
		private final int late;

		private final boolean thrall;

		/** The least and the most one hit of it can be. */
		private final int least;
		private final int most;

		Kind(String label, int early, int late, boolean thrall, int least, int most)
		{
			this.label = label;
			this.early = early;
			this.late = late;
			this.thrall = thrall;
			this.least = least;
			this.most = most;
		}

		boolean isThrall()
		{
			return thrall;
		}

		/** Whether the hitpoints experience of the tick it was made on is its own. */
		private boolean earns()
		{
			return !thrall && this != SACRIFICE;
		}
	}

	/** Where each verdict goes. */
	interface Listener
	{
		void settled(Verdict verdict);

		/** An attack's last tick went by with no hitsplat taken for it. */
		void lapsed(Attack attack);

		/** A spell's last tick went by: what it hit for over every target is known. */
		default void castLanded(Attack attack)
		{
		}
	}

	static final class Attack
	{
		final Kind kind;
		final int made;
		final int due;

		/** The NPC it was made at, or {@link #UNKNOWN}. */
		final int target;

		/** The projectile's id, or 0 for an attack without one. */
		final int projectile;

		/** For a spell, which is at whatever it reaches: the NPC it was cast at. */
		private int aim = UNKNOWN;

		private int left;
		private int hits;
		private long damage;

		private Attack(Kind kind, int made, int due, int target, int projectile, int left)
		{
			this.kind = kind;
			this.made = made;
			this.due = due;
			this.target = target;
			this.projectile = projectile;
			this.left = left;
		}

		long damage()
		{
			return damage;
		}

		private boolean takes(Splat splat)
		{
			return left > 0 && (target == UNKNOWN || target == splat.target)
				&& splat.tick >= due - kind.early && splat.tick <= due + kind.late
				&& splat.amount >= kind.least && splat.amount <= kind.most;
		}

		private void take(Splat splat)
		{
			left--;
			hits++;
			damage += splat.amount;
		}

		@Override
		public String toString()
		{
			return kind.label + (projectile > 0 ? " " + projectile : "") + " made " + made + " due "
				+ due + " at " + (target == UNKNOWN ? "anything" : "#" + target);
		}
	}

	/** What the hitpoints experience of a tick says its attack hit for. */
	private static final class Earned
	{
		private int experience;
		private double perDamage;
	}

	private static final class Splat
	{
		private final int tick;
		private final int target;
		private final int amount;
		private final String on;

		private Splat(int tick, int target, int amount, String on)
		{
			this.tick = tick;
			this.target = target;
			this.amount = amount;
			this.on = on;
		}
	}

	static final class Verdict
	{
		final int tick;
		final int amount;

		/** How the caller named what was hit, carried through for the log. */
		final String on;

		/** The attack the splat was given to, or null when none was due. */
		final Attack attack;

		final boolean spec;

		/** The hitpoints experience earned on the tick the attack was made, or 0. */
		final int experience;

		/** Whether the experience says what the attack's hits come to - see {@link #least}. */
		final boolean sized;

		/** The least and most all of the attack's hits may come to by that experience. */
		final int least;
		final int most;

		/**
		 * An attack that was due and left the splat alone, as not what its experience says it hit
		 * for; null when there was none, or another took the splat.
		 */
		final Attack refused;

		private Verdict(Splat splat, Attack attack, boolean spec, Earned earned, Attack refused)
		{
			boolean sized = isSized(attack, earned);

			this.tick = splat.tick;
			this.amount = splat.amount;
			this.on = splat.on;
			this.attack = attack;
			this.spec = spec;
			this.experience = attack != null && attack.kind.earns() && earned != null
				? earned.experience
				: 0;
			this.sized = sized;
			this.least = sized ? ExperienceRate.leastDamage(earned.experience, earned.perDamage) : 0;
			this.most = sized ? ExperienceRate.mostDamage(earned.experience, earned.perDamage) : 0;
			this.refused = attack == null ? refused : null;
		}

		/** How many ticks after its due tick the splat came; negative when it came ahead of it. */
		int offset()
		{
			return attack == null ? 0 : tick - attack.due;
		}

		@Override
		public String toString()
		{
			StringBuilder text = new StringBuilder().append(amount).append(" on ").append(on)
				.append(" at tick ").append(tick).append(" = ");

			if (attack == null)
			{
				text.append("nothing due");
				return refused == null ? text.toString()
					: text.append(", too much or too little for ").append(refused).toString();
			}

			text.append(spec ? "spec " : "").append(attack.kind.label);

			if (attack.projectile > 0)
			{
				text.append(' ').append(attack.projectile);
			}

			text.append(" made ").append(attack.made).append(" due ").append(attack.due)
				.append(" (").append(offset() < 0 ? "" : "+").append(offset()).append(')');

			if (sized || experience > 0)
			{
				text.append(", experience ").append(experience);
			}

			if (sized)
			{
				text.append(" says ").append(least).append(" to ").append(most);
			}

			return text.toString();
		}
	}

	/** Whether what an attack earned says what its hits come to: ours, at a rate that is known. */
	private static boolean isSized(Attack attack, Earned earned)
	{
		return attack != null && attack.kind.earns() && earned != null && earned.perDamage > 0;
	}

	/** More than can be in flight at once; a guard against leaks, not a limit in play. */
	private static final int MAX_ATTACKS = 32;

	/** How long what a tick said is kept for attacks made on it. */
	private static final int KEPT_TICKS = 16;

	/**
	 * How many ticks before its projectile starts to move an attack was made, likeliest first:
	 * most start 32 to 51 cycles on, which is in the second tick after.
	 */
	private static final int[] TICKS_TO_START = {2, 1};

	/** A spell lands no sooner than this after its cast. */
	private static final int CAST_TICKS = 2;

	/** How long after the spec its sacrifice hits: seen at 9 ticks, and once at 8. */
	private static final int SACRIFICE_TICKS = 9;

	private static final int NEVER = Integer.MIN_VALUE;

	private final Listener listener;
	private final List<Attack> attacks = new ArrayList<>();
	private final List<Splat> splats = new ArrayList<>();
	private final Set<Integer> specs = new HashSet<>();
	private final Set<Integer> casts = new HashSet<>();
	private final TreeMap<Integer, Earned> earned = new TreeMap<>();

	/** The NPCs that died on each of the last few ticks. */
	private final TreeMap<Integer, Set<Integer>> deaths = new TreeMap<>();

	/** The tick a thrall last animated a shot. */
	private int thrallShotAt = NEVER;

	AttackLedger(Listener listener)
	{
		this.listener = listener;
	}

	void reset()
	{
		attacks.clear();
		splats.clear();
		specs.clear();
		casts.clear();
		earned.clear();
		deaths.clear();
		thrallShotAt = NEVER;
	}

	/**
	 * An animation of ours that may be an attack: a block and a cast are animations too. Taken for
	 * a swing unless a projectile of ours made on the same tick shows it was a shot.
	 */
	void swung(int tick, int target)
	{
		for (Attack attack : attacks)
		{
			if (attack.kind == Kind.SHOT && attack.made == tick)
			{
				return;
			}
		}

		add(casts.contains(tick)
			? castAt(tick, target)
			: new Attack(Kind.SWING, tick, tick + 1, target, 0, Integer.MAX_VALUE));
	}

	/** A spell with no projectile was cast on this tick: its animation is the cast, not a swing. */
	void cast(int tick)
	{
		casts.add(tick);

		for (Attack attack : attacks)
		{
			if (attack.kind == Kind.SWING && attack.made == tick && attack.hits == 0)
			{
				attacks.remove(attack);
				add(castAt(tick, attack.target));
				return;
			}
		}
	}

	private static Attack castAt(int tick, int aim)
	{
		Attack cast = new Attack(Kind.CAST, tick, tick + CAST_TICKS, UNKNOWN, 0, Integer.MAX_VALUE);
		cast.aim = aim;
		return cast;
	}

	/** A projectile of ours, one hit each. Replaces the swing its animation was taken for. */
	void shot(int projectile, int made, int due, int target)
	{
		attacks.removeIf(attack -> attack.kind == Kind.SWING && attack.made == made
			&& attack.hits == 0);
		add(new Attack(Kind.SHOT, made, due, target, projectile, 1));
	}

	/**
	 * A projectile of ours, heard of as it starts to move. Its attack is the swing made a tick or
	 * two before at what it flies at, or was made two before when no animation said so.
	 */
	void shotStarted(int projectile, int started, int due, int target)
	{
		shot(projectile, madeBefore(started, target), due, target);
	}

	private int madeBefore(int started, int target)
	{
		for (int ticks : TICKS_TO_START)
		{
			for (Attack attack : attacks)
			{
				if (attack.kind == Kind.SWING && attack.made == started - ticks && attack.hits == 0
					&& (attack.target == UNKNOWN || attack.target == target))
				{
					return attack.made;
				}
			}
		}

		return started - TICKS_TO_START[0];
	}

	void thrallShot(int projectile, int made, int due, int target)
	{
		add(new Attack(Kind.THRALL_SHOT, made, due, target, projectile, 1));
	}

	/** A ghost or a skeleton animated its attack; the projectile follows. */
	void thrallShooting(int tick)
	{
		thrallShotAt = tick;
	}

	/**
	 * A thrall's projectile, heard of as it starts to move: a tick after a ghost's animation and
	 * two after a skeleton's. A ghost's first attack has no animation.
	 */
	void thrallShotStarted(int projectile, int started, int due, int target)
	{
		boolean animated = thrallShotAt != NEVER && thrallShotAt < started
			&& started - thrallShotAt <= TICKS_TO_START[0];

		thrallShot(projectile, animated ? thrallShotAt : started - 1, due, target);
	}

	void thrallSwung(int tick)
	{
		add(new Attack(Kind.THRALL_SWING, tick, tick + 1, UNKNOWN, 0, 1));
	}

	/** Special attack energy was spent on this tick: the attack made on it is the spec. */
	void specFired(int tick)
	{
		specs.add(tick);
	}

	/** An NPC died on this tick: the hit that killed it is cut to what it had left. */
	void died(int tick, int target)
	{
		deaths.computeIfAbsent(tick, at -> new HashSet<>()).add(target);
	}

	/** The spec made on this tick has a second hit to come, on whatever it hit. */
	void sacrifice(int tick)
	{
		add(new Attack(Kind.SACRIFICE, tick, tick + SACRIFICE_TICKS, UNKNOWN, 0, 1));
	}

	/**
	 * Hitpoints experience earned on a tick, which is the attack made on it. None says the attack
	 * missed, or that its animation was no attack.
	 *
	 * @param perDamage what a point of damage on its target earns, or 0 when that is not known
	 */
	void experience(int tick, int amount, double perDamage)
	{
		Earned total = earned.computeIfAbsent(tick, at -> new Earned());
		total.experience += amount;
		total.perDamage = perDamage;
	}

	/**
	 * A hitsplat of ours on an NPC.
	 *
	 * @param on how to name the NPC in the verdict
	 */
	void splat(int tick, int target, int amount, String on)
	{
		splats.add(new Splat(tick, target, amount, on));
	}

	/** Settles the splats of the tick before, and lets go of attacks whose time has gone by. */
	void tickEnded(int tick)
	{
		Iterator<Splat> waiting = splats.iterator();

		while (waiting.hasNext())
		{
			Splat splat = waiting.next();

			if (splat.tick < tick)
			{
				waiting.remove();
				settle(splat);
			}
		}

		Iterator<Attack> open = attacks.iterator();

		while (open.hasNext())
		{
			Attack attack = open.next();

			if (attack.due + attack.kind.late < tick)
			{
				open.remove();

				// A sacrifice that never came followed a spec that missed, which is no news.
				if (attack.hits == 0 && attack.kind != Kind.SACRIFICE && isKnownAttack(attack))
				{
					listener.lapsed(attack);
				}
				else if (attack.hits > 0 && attack.kind == Kind.CAST)
				{
					listener.castLanded(attack);
				}
			}
		}

		specs.removeIf(at -> at < tick - KEPT_TICKS);
		casts.removeIf(at -> at < tick - KEPT_TICKS);
		deaths.headMap(tick - KEPT_TICKS).clear();
		earned.headMap(tick - KEPT_TICKS).clear();
	}

	private void settle(Splat splat)
	{
		Attack taker = null;
		Attack refused = null;

		for (Attack attack : attacks)
		{
			if (!attack.takes(splat))
			{
				continue;
			}

			if (!fits(attack, splat))
			{
				refused = refused == null || isAhead(attack, refused, splat) ? attack : refused;
			}
			else if (taker == null || isAhead(attack, taker, splat))
			{
				taker = attack;
			}
		}

		listener.settled(new Verdict(splat, taker, taker != null && !taker.kind.thrall
			&& specs.contains(taker.made), taker == null ? null : earned.get(taker.made), refused));

		if (taker == null)
		{
			return;
		}

		taker.take(splat);

		if (taker.left == 0)
		{
			attacks.remove(taker);
		}
	}

	/**
	 * Whether more than an animation says this was an attack: a swing that earned nothing is a
	 * miss, a block or a cast, and only a miss leaves a splat to take.
	 */
	private boolean isKnownAttack(Attack attack)
	{
		Earned made = earned.get(attack.made);

		return attack.kind != Kind.SWING || made == null || made.experience > 0
			|| specs.contains(attack.made);
	}

	/**
	 * Whether a splat can be the attack's by the experience its tick earned. Nothing earned leaves
	 * it only a 0, whatever it was made at. A shot's one hit is what the experience says, or less
	 * when it killed; a swing's hits may not come to more than it. True when the experience says
	 * nothing.
	 */
	private boolean fits(Attack attack, Splat splat)
	{
		Earned made = earned.get(attack.made);

		if (!attack.kind.earns() || made == null)
		{
			return true;
		}

		if (made.experience == 0)
		{
			return splat.amount == 0;
		}

		if (made.perDamage <= 0)
		{
			return true;
		}

		long soFar = attack.damage + splat.amount;

		return soFar <= ExperienceRate.mostDamage(made.experience, made.perDamage)
			&& (attack.kind != Kind.SHOT || killed(splat)
			|| soFar >= ExperienceRate.leastDamage(made.experience, made.perDamage));
	}

	private boolean killed(Splat splat)
	{
		Set<Integer> dead = deaths.get(splat.tick);
		return dead != null && dead.contains(splat.target);
	}

	/** Whether the splat is on something a spell was not cast at: it may reach it, no more. */
	private static boolean onlyReaches(Attack attack, Splat splat)
	{
		return attack.kind == Kind.CAST && attack.aim != splat.target;
	}

	/**
	 * A known attack comes before a swing only its animation speaks for. Then the attack due
	 * nearest the tick takes the hit. Of two as near, one made at what was hit comes before a
	 * spell that only reaches it, and then hits land in the order their attacks were made; of two
	 * made on one tick the thrall's comes first, and something seen to fly comes ahead of a swing.
	 */
	private boolean isAhead(Attack attack, Attack other, Splat splat)
	{
		boolean known = isKnownAttack(attack);

		if (known != isKnownAttack(other))
		{
			return known;
		}

		int near = Math.abs(splat.tick - attack.due);
		int otherNear = Math.abs(splat.tick - other.due);

		if (near != otherNear)
		{
			return near < otherNear;
		}

		boolean reaches = onlyReaches(attack, splat);

		if (reaches != onlyReaches(other, splat))
		{
			return !reaches;
		}

		if (attack.made != other.made)
		{
			return attack.made < other.made;
		}

		if (attack.kind.thrall != other.kind.thrall)
		{
			return attack.kind.thrall;
		}

		return attack.kind != Kind.SWING && other.kind == Kind.SWING;
	}

	private void add(Attack attack)
	{
		if (attacks.size() >= MAX_ATTACKS)
		{
			attacks.remove(0);
		}

		attacks.add(attack);
	}
}
