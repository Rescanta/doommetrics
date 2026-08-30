package com.rescanta.doommetrics;

import java.util.List;

/**
 * One finished trip into the Doom, as it is written to the history file.
 *
 * <p>This is the on-disk shape, so the field names are the file format: renaming one silently
 * drops that column out of every record already written. {@link #v} exists so a later change can
 * tell old records from new ones without guessing.
 *
 * <p>Only runs that ended in a way we saw are recorded - see {@link EndReason#ABANDONED}. A run
 * that never cleared a delve is not worth a row either, so the deepest delve here is always at
 * least 1.
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

	/**
	 * Time from the start of the run to that last clear, in game ticks - the same measure the
	 * milestone table's personal bests use. Zero when nothing trustworthy could be measured.
	 */
	int ticks;

	EndReason end;

	/** The delve being fought when the player died, or 0 for a run that ended any other way. */
	int diedOn;

	/**
	 * True when the run was already underway when the plugin started watching, so {@link #ticks}
	 * is an over-estimate and the run's real start is unknown.
	 */
	boolean partial;

	/**
	 * True when the plugin stopped watching before the run ended - it was switched off part way
	 * through, and for all we know the player carried on delving afterwards.
	 *
	 * <p>{@link #delve} is then a floor on where the run actually got to rather than the answer,
	 * which is why the depth chart leaves these out. The run is still written down: what happened
	 * is worth keeping even when how far it went is only bounded.
	 */
	boolean incomplete;

	/**
	 * The notable drops from this run, by name, in the order they were seen. Empty for the many
	 * runs that produce none.
	 *
	 * <p>Only the drops that make a trip worth remembering are listed, not every item claimed -
	 * the supplies and currency are noise here. A drop earned twice on one trip is listed twice,
	 * since a deep run really can roll the same unique more than once.
	 */
	List<String> loot;

	/**
	 * What this run's gear and spellbook gave back, by source - see {@link CombatMetric}. Null for
	 * runs written before this was recorded, and for runs where nothing could be attributed.
	 *
	 * <p>This is what lets the history chart plot a metric per run rather than only in aggregate.
	 * The lifetime figure is kept separately in config, so this is not what a total is read from -
	 * which is why an older run missing it costs a point on a chart and nothing else.
	 */
	CombatTotals combat;
}
