package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * What each counter gained in the last few seconds, shown as {@code +97}. A new gain adds to one
 * still showing, so a punish's swing and bonus splats read as one. Wall clock; client thread only.
 */
class RecentGains
{
	/** How long a gain stays on show after the last amount added to it: five game ticks. */
	static final Duration SHOWN_FOR = Duration.ofMillis(5 * DoomFormat.TICK_MILLIS);

	private final long[] amounts = new long[CombatMetric.values().length];
	private final Instant[] lastAt = new Instant[amounts.length];

	void add(CombatMetric metric, long amount, Instant at)
	{
		if (amount <= 0)
		{
			return;
		}

		int slot = metric.ordinal();

		if (!isShowing(slot, at))
		{
			amounts[slot] = 0;
		}

		amounts[slot] += amount;
		lastAt[slot] = at;
	}

	/**
	 * The gain on show for {@code metric}, or 0 once it has run its time or before there was one.
	 */
	long get(CombatMetric metric, Instant now)
	{
		int slot = metric.ordinal();
		return isShowing(slot, now) ? amounts[slot] : 0;
	}

	private boolean isShowing(int slot, Instant now)
	{
		return lastAt[slot] != null && Duration.between(lastAt[slot], now).compareTo(SHOWN_FOR) < 0;
	}
}
