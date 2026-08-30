package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A character's whole history, turned into one list of numbers per thing the chart can draw.
 *
 * <p>Built once when the window opens rather than each time the dropdown moves, because switching
 * metric should not cost a pass over ten thousand runs. Every list is the same length and in the
 * same order, so run 400 is the four hundredth dot whichever metric is on show.
 *
 * <p>A run recorded before combat tracking existed contributes a zero to every metric, not a gap.
 * That is the honest reading: what the run healed for was never measured, and the alternative -
 * dropping those runs from the metric charts - would silently renumber every run after them and
 * leave the depth chart and the healing chart disagreeing about which run was which.
 *
 * <p>Immutable, and built on the executor thread before being handed to Swing.
 */
final class RunSeries
{
	private static final RunSeries EMPTY = new RunSeries(Collections.emptyList(),
		new EnumMap<>(CombatMetric.class));

	private final List<Integer> delves;
	private final Map<CombatMetric, List<Integer>> combat;

	private RunSeries(List<Integer> delves, Map<CombatMetric, List<Integer>> combat)
	{
		this.delves = delves;
		this.combat = combat;
	}

	static RunSeries empty()
	{
		return EMPTY;
	}

	static RunSeries of(List<RunRecord> records)
	{
		List<Integer> delves = new ArrayList<>(records.size());
		Map<CombatMetric, List<Integer>> combat = new EnumMap<>(CombatMetric.class);

		for (CombatMetric metric : CombatMetric.values())
		{
			combat.put(metric, new ArrayList<>(records.size()));
		}

		for (RunRecord record : records)
		{
			append(delves, combat, record);
		}

		return new RunSeries(delves, combat);
	}

	/** This history with one more run on the end, leaving this one untouched. */
	RunSeries plus(RunRecord record)
	{
		List<Integer> grown = new ArrayList<>(delves);
		Map<CombatMetric, List<Integer>> grownCombat = new EnumMap<>(CombatMetric.class);

		for (CombatMetric metric : CombatMetric.values())
		{
			grownCombat.put(metric, new ArrayList<>(valuesFor(metric)));
		}

		append(grown, grownCombat, record);
		return new RunSeries(grown, grownCombat);
	}

	private static void append(List<Integer> delves, Map<CombatMetric, List<Integer>> combat,
		RunRecord record)
	{
		delves.add(record.delve);

		for (CombatMetric metric : CombatMetric.values())
		{
			long amount = record.combat == null ? 0 : record.combat.get(metric);
			// Clamped rather than widened: a single run cannot honestly exceed an int, so a value
			// that does is a corrupt line, and a clamp keeps it from wrapping into a negative dot.
			combat.get(metric).add((int) Math.min(Integer.MAX_VALUE, Math.max(0, amount)));
		}
	}

	private List<Integer> valuesFor(CombatMetric metric)
	{
		List<Integer> values = combat.get(metric);
		return values == null ? Collections.emptyList() : values;
	}

	int size()
	{
		return delves.size();
	}

	/** What the chart should draw for {@code option}, ready to hand straight to it. */
	ChartSeries seriesFor(ChartOption option)
	{
		CombatMetric metric = option.metric();

		if (metric == null)
		{
			return new ChartSeries("Deepest delve", "", delves, ChartSeries.Axis.DELVE);
		}

		return new ChartSeries(option.toString(), metric.unit().description(),
			valuesFor(metric), ChartSeries.Axis.COUNT);
	}
}
