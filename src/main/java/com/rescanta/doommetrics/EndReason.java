package com.rescanta.doommetrics;

enum EndReason
{
	/**
	 * The player ended up outside the cave with the run over, whether or not they claimed loot.
	 *
	 * <p>This covers walking out as well as hopping, logging out and dropping the connection. The
	 * game puts you back outside the entrance either way and will not let you resume the delve you
	 * were on, so all four stop the run at the same exact depth, and nothing downstream has any
	 * use for telling them apart.
	 */
	FINISHED,
	DIED,

	/** The run went away without us seeing how it finished - plugin restart, or a lost boss. */
	ABANDONED
}
