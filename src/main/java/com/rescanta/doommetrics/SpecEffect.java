package com.rescanta.doommetrics;

/**
 * One thing a cause is expected to produce, and when: a range of ticks, so two specs in flight
 * don't steal each other's effects. {@link #budget} caps how many hitsplats it claims, and
 * {@link #exactly} pins the size where it is known.
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

	/** Whether an effect of this size could be the one expected; always true when the size varies. */
	boolean isSized(long amount)
	{
		return exactly == ANY_AMOUNT || amount == exactly;
	}
}
