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
 */
final class SpecEffect
{
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

	SpecEffect(Kind kind, CombatMetric metric, int from, int to, int budget)
	{
		this.kind = kind;
		this.metric = metric;
		this.from = from;
		this.to = to;
		this.budget = budget;
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
}
