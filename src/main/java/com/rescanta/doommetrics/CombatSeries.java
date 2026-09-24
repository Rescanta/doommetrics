package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One chart line and legend row: a single counter, or a heading's counters added up. Implemented by
 * two enums, so sets of these compare by identity.
 */
interface CombatSeries
{
	/**
	 * The lines drawn when grouped or not. Worked out per call: a static field here would be filled
	 * before {@link CombatMetric#DISPLAYED} is.
	 *
	 * @param grouped whether the counters are folded into their headings
	 */
	static List<CombatSeries> drawn(boolean grouped)
	{
		return Collections.unmodifiableList(grouped
			? new ArrayList<CombatSeries>(Arrays.asList(CombatMetric.Group.values()))
			: new ArrayList<CombatSeries>(CombatMetric.DISPLAYED));
	}

	/** How the line reads in the legend beside the chart. */
	String label();

	/** What the line is counted in, which is what its figures may be compared against. */
	CombatMetric.Unit unit();

	/** The colour the line is drawn in, and of the swatch beside its name. */
	Color seriesColor();

	/**
	 * Whether the line is dashed: a counter past the palette's eight slots shares a hue with
	 * another line, and the dash tells them apart.
	 */
	default boolean dashed()
	{
		return false;
	}

	/** What this line comes to in a tally - one counter's figure, or a heading's total. */
	long amount(CombatTotals totals);

	/**
	 * What feeds this line, one per line, or nothing when the label already says - see
	 * {@link CombatMetric#sources()} and {@link CombatMetric.Group#sources()}.
	 */
	List<String> sources();

	/** The picture drawn beside the name, or null: a weapon or spell, or a heading's skill. */
	default BufferedImage icon(Icons icons)
	{
		return null;
	}
}
