package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One entry in the history chart's metric dropdown.
 *
 * <p>The depth of a run and the healing in it share nothing but an x-axis, so the chart shows one
 * at a time rather than laying them over each other: "delve 40" and "18,000 damage" on one scale
 * is a picture with no readable answer in it, and a second axis to fix that is two charts drawn on
 * top of each other and called one.
 *
 * <p>{@link #toString} is what the dropdown draws, so this needs no renderer of its own.
 */
final class ChartOption
{
	/** The depth chart the window opened with, and what it still shows by default. */
	private static final ChartOption DEEPEST_DELVE = new ChartOption("Deepest delve", null);

	private static final List<ChartOption> ALL = build();

	private final String label;

	/** The metric to plot, or null for {@link #DEEPEST_DELVE}. */
	private final CombatMetric metric;

	private ChartOption(String label, CombatMetric metric)
	{
		this.label = label;
		this.metric = metric;
	}

	/** Every choice, depth first and then the combat metrics in the order they are grouped. */
	static List<ChartOption> all()
	{
		return ALL;
	}

	static ChartOption deepestDelve()
	{
		return DEEPEST_DELVE;
	}

	private static List<ChartOption> build()
	{
		List<ChartOption> options = new ArrayList<>();
		options.add(DEEPEST_DELVE);

		for (CombatMetric metric : CombatMetric.values())
		{
			// Qualified, because "Other specs" appears under two headings and would otherwise be
			// two identical lines in the dropdown.
			options.add(new ChartOption(metric.qualifiedLabel(), metric));
		}

		return Collections.unmodifiableList(options);
	}

	CombatMetric metric()
	{
		return metric;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
