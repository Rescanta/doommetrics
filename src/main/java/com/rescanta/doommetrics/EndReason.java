package com.rescanta.doommetrics;

enum EndReason
{
	/**
	 * The player ended up outside the cave with the run over, whether or not they claimed loot.
	 *
	 * <p>This covers walking out as well as hopping and logging out. The game puts you back outside
	 * the entrance either way and will not let you resume the delve you were on, so all three stop
	 * the run at the same exact depth, and nothing downstream has any use for telling them apart.
	 *
	 * <p>A dropped connection only lands here once the player is seen to be back outside. One that
	 * gets back into the delve never ends the run at all - see {@link ResumeCheck}.
	 */
	FINISHED,
	DIED,

	/** The run went away without us seeing how it finished - plugin restart, or a lost boss. */
	ABANDONED
}
