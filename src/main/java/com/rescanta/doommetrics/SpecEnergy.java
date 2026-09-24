package com.rescanta.doommetrics;

/**
 * Tells a special attack from the rest of what moves spec energy: a spec is the one thing that
 * lowers it. The game only sends the energy when it changes, so the level it fell from has to be
 * known beforehand - see {@link #seed}.
 */
final class SpecEnergy
{
	/** No level seen since the plugin was turned on. */
	private static final int UNKNOWN = -1;

	private int last = UNKNOWN;

	/**
	 * Sets the level a change is read against, from the client rather than a change. Needed
	 * whenever the last one seen may be stale: at full energy nothing is sent until the first spec,
	 * and that spec read against no level would go uncounted.
	 */
	void seed(int energy)
	{
		last = energy;
	}

	void forget()
	{
		last = UNKNOWN;
	}

	/**
	 * Takes in a new level.
	 *
	 * @return whether it fell, which is a spec; a first level with nothing to read it against
	 * never is
	 */
	boolean spent(int energy)
	{
		int was = last;
		last = energy;
		return was != UNKNOWN && energy < was;
	}
}
