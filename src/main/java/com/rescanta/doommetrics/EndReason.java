package com.rescanta.doommetrics;

enum EndReason
{
	/**
	 * The run ended with the player outside the cave: walked out, hopped or logged out, claimed or
	 * not.
	 */
	FINISHED,
	DIED,

	/** The run went away without us seeing how it finished - plugin restart, or a lost boss. */
	ABANDONED
}
