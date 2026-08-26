package com.tnamai.doommetrics;

enum EndReason
{
	CLAIMED,
	LEFT,
	DIED,
	/** The run went away without us seeing how it finished - logout, hop, plugin restart. */
	ABANDONED
}
