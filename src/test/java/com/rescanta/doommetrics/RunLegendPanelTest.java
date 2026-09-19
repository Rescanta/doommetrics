package com.rescanta.doommetrics;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RunLegendPanelTest
{
	private static final Instant START = Instant.now().minusSeconds(600);

	private final RunLegendPanel legend = new RunLegendPanel();
	private final AtomicReference<Set<CombatSeries>> offChart = new AtomicReference<>();

	@Test
	public void countersTheRunCountedNothingOnStartOff()
	{
		legend.setToggleListener(offChart::set);
		legend.setDetail(RunDetail.of(crossbowOnly()));

		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE), offChart.get());

		legend.setHideEmpty(false);
		assertEquals("every line is on", Collections.emptySet(), offChart.get());
	}

	@Test
	public void theCatchAllsAreNeverOnTheChart()
	{
		legend.setToggleListener(offChart::set);
		legend.setHideEmpty(false);

		DelveRun run = crossbowOnly();
		run.recordCombat(CombatMetric.OTHER_SPEC_DAMAGE, 500, START.plusSeconds(130));
		run.complete(3, START.plusSeconds(180), null);
		legend.setDetail(RunDetail.of(run));

		assertEquals("the legend only tells the chart about lines it draws",
			Collections.emptySet(), offChart.get());
	}

	@Test
	public void anEmptyCounterClickedOnStaysOn()
	{
		legend.setToggleListener(offChart::set);
		legend.setDetail(RunDetail.of(crossbowOnly()));

		legend.toggle(CombatMetric.AGS_HEAL);
		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE, CombatMetric.AGS_HEAL), offChart.get());

		// The next clear pushes a fresh snapshot, and the reader's click still stands.
		legend.setDetail(RunDetail.of(crossbowOnly()));
		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE, CombatMetric.AGS_HEAL), offChart.get());

		legend.toggle(CombatMetric.AGS_HEAL);
		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE), offChart.get());
	}

	@Test
	public void anEmptyCounterComesOnOnceItCounts()
	{
		legend.setToggleListener(offChart::set);
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, START.plusSeconds(60), null);

		legend.setDetail(RunDetail.of(run));
		assertEquals(allDrawnBut(), offChart.get());

		run.enterLevel(2, START.plusSeconds(60));
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 300, START.plusSeconds(90));
		run.complete(2, START.plusSeconds(120), null);
		legend.setDetail(RunDetail.of(run));

		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE), offChart.get());

		// Pointing at delve 1, where the crossbow did nothing, leaves its line on.
		legend.setDelve(1);
		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE), offChart.get());
	}

	@Test
	public void aCounterClickedOffStaysOffWhateverItCounts()
	{
		legend.setToggleListener(offChart::set);
		legend.setHideEmpty(false);
		legend.setDetail(RunDetail.of(crossbowOnly()));

		legend.toggle(CombatMetric.ZCB_DAMAGE);
		legend.setHideEmpty(true);

		assertEquals(allDrawnBut(), offChart.get());
	}

	/** Clicking off the line that is brought forward must not leave the chart pushed back behind it. */
	@Test
	public void aLineClickedOffIsNoLongerBroughtForward()
	{
		AtomicReference<CombatSeries> forward = new AtomicReference<>();
		legend.setToggleListener(offChart::set);
		legend.setEmphasisListener(forward::set);
		legend.setDetail(RunDetail.of(crossbowOnly()));

		legend.toggle(CombatMetric.ZCB_DAMAGE);
		assertNull(forward.get());

		legend.toggle(CombatMetric.ZCB_DAMAGE);
		assertEquals(CombatMetric.ZCB_DAMAGE, forward.get());
	}

	/**
	 * Grouped, the chart is told about headings rather than counters: the lines are the headings,
	 * and a heading is empty only when everything under it is.
	 */
	@Test
	public void groupingMakesTheHeadingsTheLines()
	{
		legend.setToggleListener(offChart::set);
		legend.setDetail(RunDetail.of(crossbowOnly()));
		legend.setGrouped(true);

		assertEquals(allGroupsBut(CombatMetric.Group.DAMAGE), offChart.get());
	}

	/**
	 * A heading's line counts what no counter under it names, so a run that only ever punished
	 * with a weapon the plugin does not name has a punish line grouped and none separately.
	 */
	@Test
	public void aHeadingIsOnTheChartForCountersWithNoLineOfTheirOwn()
	{
		legend.setToggleListener(offChart::set);

		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.OTHER_MELEE_PUNISH, 141, START.plusSeconds(30));
		run.complete(1, START.plusSeconds(60), null);
		legend.setDetail(RunDetail.of(run));

		assertEquals("no counter counted anything", allDrawnBut(), offChart.get());

		legend.setGrouped(true);
		assertEquals(allGroupsBut(CombatMetric.Group.PUNISH), offChart.get());
	}

	/** Which lines are off is asked of the way being read, so switching back finds it as it was. */
	@Test
	public void aLineClickedOffStaysOffItsOwnWayOfReading()
	{
		legend.setToggleListener(offChart::set);
		legend.setHideEmpty(false);
		legend.setDetail(RunDetail.of(crossbowOnly()));

		legend.toggle(CombatMetric.ZCB_DAMAGE);

		legend.setGrouped(true);
		assertEquals("a counter clicked off is not a heading clicked off",
			Collections.emptySet(), offChart.get());

		legend.toggle(CombatMetric.Group.PUNISH);
		legend.setGrouped(false);

		assertEquals("the counter clicked off is still the only line off",
			Collections.singleton(CombatMetric.ZCB_DAMAGE), offChart.get());
	}

	/** Two delves, with the crossbow's spec on the first and nothing else counted. */
	private static DelveRun crossbowOnly()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 300, START.plusSeconds(30));
		run.complete(1, START.plusSeconds(60), null);
		run.complete(2, START.plusSeconds(120), null);
		return run;
	}

	private static Set<CombatSeries> allDrawnBut(CombatMetric... on)
	{
		Set<CombatSeries> off = new HashSet<>(CombatMetric.DISPLAYED);

		for (CombatMetric metric : on)
		{
			off.remove(metric);
		}

		return off;
	}

	private static Set<CombatSeries> allGroupsBut(CombatMetric.Group... on)
	{
		Set<CombatSeries> off = new HashSet<>(Arrays.asList(CombatMetric.Group.values()));

		for (CombatMetric.Group group : on)
		{
			off.remove(group);
		}

		return off;
	}
}
