package com.rescanta.doommetrics;

/**
 * Takes natural regeneration (the prayer regeneration potion, natural hitpoints) out of a rise, so
 * what is left is what the gear gave back. Drips never pass the trained level, and below it they
 * land on an exact cadence, learned only from points nothing else explains. Errs a point short.
 */
final class Regeneration
{
	/** The tick a drip was last seen on, which lays the grid, or -1 while nothing has laid it. */
	private int lastDrip = -1;

	/**
	 * How much of a rise the player's gear gave back.
	 *
	 * @param from    the boosted level before the rise
	 * @param to      the boosted level after it
	 * @param natural the trained level, as far as a drip can reach
	 * @param tick    the tick the level went up on
	 * @param period  how often this drips, in ticks, or 0 when nothing is dripping
	 * @param spare   whether nothing else can explain the rise, which makes it safe to learn from
	 * @return the points to credit
	 */
	int without(int from, int to, int natural, int tick, int period, boolean spare)
	{
		int rise = to - from;

		// Nothing to take out: either nothing is dripping, or the level was already as high as a
		// drip could have carried it.
		if (period <= 0 || from >= natural)
		{
			return rise;
		}

		if (rise == 1 && spare)
		{
			lastDrip = tick;
			return 0;
		}

		if (lastDrip < 0)
		{
			// Nothing has laid the grid yet, so nothing is due. The first drip to fall in the open
			// lays it, which on either cadence is a matter of a minute at the outside.
			return rise;
		}

		if (tick < lastDrip)
		{
			// The client's tick counter has been round a login, so the grid was laid against one
			// that no longer runs and is dropped rather than measured from.
			lastDrip = -1;
			return rise;
		}

		if ((tick - lastDrip) % period != 0)
		{
			return rise;
		}

		lastDrip = tick;
		return rise - 1;
	}

	/** Forgets the grid. The tick counter it was laid against does not survive a login. */
	void reset()
	{
		lastDrip = -1;
	}
}
