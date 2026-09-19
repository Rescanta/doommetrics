package com.rescanta.doommetrics;

/**
 * One thing a cause is expected to produce, and when.
 *
 * <p>The window is a range rather than a deadline because the effects being told apart do not all
 * arrive at once. A blowpipe spec heals on the tick it hits; the Ancient godsword's Blood Sacrifice
 * marks the target for eight ticks, deals its damage, and only then heals. Given a bare "within N
 * ticks" both would claim both, and whichever was fired last would take the pair. Given a range,
 * the Ancient godsword does not claim heals that land immediately and the blowpipe does not claim
 * heals that land nine ticks later, so two specs can be in flight at once without either stealing
 * from the other.
 *
 * <p>{@link #budget} is what stops a cause claiming more than it has to give. A spec that fires one
 * bolt is credited with one hitsplat: the auto-attack behind it lands inside the same window and is
 * not the spec.
 *
 * <p>{@link #exactly} is the other half of that, for the one effect whose size is known in advance.
 * A window open across ticks seven to ten is open long enough for an ordinary attack to land in it,
 * and the budget alone cannot tell which of the two arrived: it credits the first and turns the
 * second away, so a bow shot was taken for a Blood Sacrifice and the sacrifice itself, landing on
 * the same tick, was dropped for want of a window. Where the effect always arrives as one number,
 * saying so settles it.
 */
final class SpecEffect
{
	/** What {@link #exactly} holds for an effect whose size cannot be known ahead of it. */
	static final int ANY_AMOUNT = -1;

	enum Kind
	{
		/** A hitsplat of ours on something else. */
		DAMAGE,

		/** A heal hitsplat on our own head. */
		HEAL,

		/** Prayer points going up. */
		PRAYER
	}

	private final Kind kind;
	private final CombatMetric metric;

	/** Earliest tick after the cause this effect may arrive, inclusive. */
	private final int from;

	/** Latest tick after the cause this effect may arrive, inclusive. */
	private final int to;

	/** How many times this effect may be credited before the cause is spent. */
	private final int budget;

	/** The amount this effect always arrives as, or {@link #ANY_AMOUNT} when it varies. */
	private final int exactly;

	SpecEffect(Kind kind, CombatMetric metric, int from, int to, int budget)
	{
		this(kind, metric, from, to, budget, ANY_AMOUNT);
	}

	SpecEffect(Kind kind, CombatMetric metric, int from, int to, int budget, int exactly)
	{
		this.kind = kind;
		this.metric = metric;
		this.from = from;
		this.to = to;
		this.budget = budget;
		this.exactly = exactly;
	}

	Kind kind()
	{
		return kind;
	}

	CombatMetric metric()
	{
		return metric;
	}

	int budget()
	{
		return budget;
	}

	int to()
	{
		return to;
	}

	boolean covers(int ticksSince)
	{
		return ticksSince >= from && ticksSince <= to;
	}

	/**
	 * Whether an effect of this size could be the one expected.
	 *
	 * <p>Always true where the size varies, which is nearly everything: a hit rolls a number and a
	 * heal is capped by how hurt you were.
	 */
	boolean isSized(long amount)
	{
		return exactly == ANY_AMOUNT || amount == exactly;
	}
}
