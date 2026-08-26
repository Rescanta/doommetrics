package com.tnamai.doommetrics;

enum EndReason
{
	/** The player left the cave under their own power, whether or not they claimed loot. */
	FINISHED,
	DIED,
	/** The run went away without us seeing how it finished - logout, hop, plugin restart. */
	ABANDONED
}
