package com.rescanta.doommetrics;


/**
 * One run as written to the history file. The field names are the file format; {@link #v}
 * versions it. Only runs with at least one clear are written: those with a seen ending, and those
 * still going when the plugin stopped watching - see {@link #incomplete}.
 */
class RunRecord
{
	/** The schema this record was written under. */
	static final int VERSION = 1;

	int v = VERSION;

	/** When the run ended, as epoch seconds. Seconds rather than millis to keep lines short. */
	long at;

	/** The deepest delve cleared. Dying part way into the next one does not count towards it. */
	int delve;

	/** Start to last clear in game ticks, or 0 when nothing trustworthy could be measured. */
	int ticks;

	EndReason end;

	/** The delve being fought when the player died, or 0 for a run that ended any other way. */
	int diedOn;

	/** Joined part way through, so {@link #ticks} is an over-estimate. */
	boolean partial;

	/**
	 * The plugin stopped watching before the run ended - it was turned off, or the client closed -
	 * so {@link #delve} is only a floor and {@link #end} is {@link EndReason#ABANDONED}.
	 */
	boolean incomplete;

	/** What this run's gear and spellbook gave back, or null when nothing was attributed. */
	CombatTotals combat;
}
