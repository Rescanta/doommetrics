package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One line on {@link DelveChart} and one row beside it, whether that line is a single counter or a
 * whole heading's worth of them added up.
 *
 * <p>The chart and its legend used to speak in {@link CombatMetric}s, which settled the question of
 * what a line could be before it was asked. A reader on their eighth run of the evening wants to
 * know whether the healing held up, not which of three sources did the healing, and a plot of
 * eight lines answers the second question at the cost of the first. So both are drawn from the
 * same code, and what differs is only which list of these it is handed - see
 * {@link RunLegendPanel#setGrouped}.
 *
 * <p>Implemented by {@link CombatMetric} and by {@link CombatMetric.Group}, both of which are enums
 * and so are singletons: a set of these can be a plain {@link java.util.HashSet} and still compare
 * by identity, which is what the chart's hidden set and its emphasis rely on.
 */
interface CombatSeries
{
	/**
	 * The lines drawn under either way of reading a run: one per heading when grouped, one per
	 * counter otherwise.
	 *
	 * <p>Worked out per call rather than held in a field of this interface. A field here would be
	 * filled the moment {@link CombatMetric} is initialised - a class is what initialises the
	 * interfaces it implements when they carry default methods - and it would read
	 * {@link CombatMetric#DISPLAYED} back before that enum had finished filling it in. Nothing
	 * asks for this on a paint, so the list costs nothing worth keeping.
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

	/** What this line comes to in a tally - one counter's figure, or a heading's total. */
	long amount(CombatTotals totals);

	/**
	 * What feeds this line, one per line, or nothing when the label already says - see
	 * {@link CombatMetric#sources()} and {@link CombatMetric.Group#sources()}.
	 */
	List<String> sources();

	/**
	 * The picture drawn beside the name, or null when there is none to draw.
	 *
	 * <p>A heading has none by default: it sums several sources and no one picture stands for all
	 * of them, which is the same reason the overlay's total lines are drawn by name.
	 */
	default BufferedImage icon(Icons icons)
	{
		return null;
	}
}
