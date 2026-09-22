package com.rescanta.doommetrics;

import java.util.List;

/**
 * One finished run as written to the history file. The field names are the file format; {@link #v}
 * versions it. Only runs with a seen ending and at least one clear are written.
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

	/** The plugin stopped watching before the run ended, so {@link #delve} is only a floor. */
	boolean incomplete;

	/**
	 * The notable drops from this run by name, in the order seen; a drop earned twice is listed
	 * twice.
	 */
	List<String> loot;

	/** What this run's gear and spellbook gave back, or null when nothing was attributed. */
	CombatTotals combat;
}
