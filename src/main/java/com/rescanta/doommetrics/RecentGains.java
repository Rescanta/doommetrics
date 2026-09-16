package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * What each counter has gained in the last few seconds, so the overlay and the square can show a
 * hit's worth as {@code +97} before the figure goes back to the run's total.
 *
 * <p>A gain landing while the last one is still on show adds to it and starts the clock over. One
 * punish arrives as the swing's hitsplats and the strength-bonus ones behind them, a tick or two
 * apart, and reading it as {@code +30} then {@code +67} would show neither the punish nor the total.
 *
 * <p>Wall clock rather than game ticks, so the figure can be read in a frame without asking the
 * client what tick it is. Client thread only: written as amounts are credited, read as frames are
 * drawn, and both happen there.
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

	/** The gain on show for {@code metric}, or 0 once it has run its time or before there was one. */
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
