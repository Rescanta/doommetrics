package com.rescanta.doommetrics;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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
	 * with a weapon the plugin does not name has a damage line grouped and none separately.
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
		assertEquals(allGroupsBut(CombatMetric.Group.DAMAGE), offChart.get());
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

		legend.toggle(CombatMetric.Group.DAMAGE);
		legend.setGrouped(false);

		assertEquals("the counter clicked off is still the only line off",
			Collections.singleton(CombatMetric.ZCB_DAMAGE), offChart.get());
	}

	/**
	 * Folding a heading takes its rows out of the column and leaves the heading, and the chart is
	 * told nothing: which lines are drawn is not what folding is for.
	 */
	@Test
	public void foldingAHeadingTakesDownItsRowsAndLeavesTheChart()
	{
		AtomicReference<Set<CombatMetric.Group>> folded = new AtomicReference<>();
		legend.setToggleListener(offChart::set);
		legend.setFoldListener(folded::set);
		legend.setHideEmpty(false);
		legend.setDetail(RunDetail.of(crossbowOnly()));
		int unfolded = legend.getComponentCount();

		offChart.set(null);
		legend.toggleFold(CombatMetric.Group.DAMAGE);

		assertEquals("the four damage rows are folded away", unfolded - 4,
			legend.getComponentCount());
		assertEquals(EnumSet.of(CombatMetric.Group.DAMAGE), folded.get());
		assertNull("the chart keeps its lines", offChart.get());

		legend.toggleFold(CombatMetric.Group.DAMAGE);
		assertEquals(unfolded, legend.getComponentCount());
		assertEquals(Collections.emptySet(), folded.get());
	}

	/**
	 * Grouped, the headings are the rows and there is nothing under them to fold - and a fold made
	 * while reading separately is still there on coming back.
	 */
	@Test
	public void aFoldOnlyAppliesReadingSeparately()
	{
		legend.setHideEmpty(false);
		legend.setFolded(EnumSet.of(CombatMetric.Group.HEALING));
		int folded = legend.getComponentCount();

		legend.setGrouped(true);
		assertEquals("the top row and a row per heading",
			1 + CombatMetric.Group.values().length, legend.getComponentCount());

		legend.setGrouped(false);
		assertEquals(folded, legend.getComponentCount());
	}

	/** A counter the run counted nothing on has no row, until the link under the column is asked. */
	@Test
	public void countersTheRunCountedNothingOnAreLeftOut()
	{
		legend.setDetail(RunDetail.of(crossbowOnly()));

		assertEquals("the top row, three headings, the crossbow's row and the link",
			1 + CombatMetric.Group.values().length + 1 + 1, legend.getComponentCount());

		legend.setShowingAll(true);
		assertEquals("every row, and the link to hide them again", everyRow() + 1,
			legend.getComponentCount());

		legend.setShowingAll(false);
		legend.setHideEmpty(false);
		assertEquals("nothing is left out, so there is no link", everyRow(),
			legend.getComponentCount());
	}

	/** Clicked on, a counter at 0 is a line on the chart, so its row stays up to click off again. */
	@Test
	public void anEmptyCounterClickedOnKeepsItsRow()
	{
		legend.setDetail(RunDetail.of(crossbowOnly()));
		int compact = legend.getComponentCount();

		legend.setShowingAll(true);
		legend.toggle(CombatMetric.AGS_HEAL);
		legend.setShowingAll(false);
		assertEquals(compact + 1, legend.getComponentCount());

		legend.toggle(CombatMetric.AGS_HEAL);
		assertEquals(compact, legend.getComponentCount());
	}

	/** Grouped, a heading is left out only when nothing under it counted. */
	@Test
	public void groupedHeadingsThatCountedNothingAreLeftOut()
	{
		legend.setDetail(RunDetail.of(crossbowOnly()));
		legend.setGrouped(true);

		assertEquals("the top row, the damage row and the link", 3, legend.getComponentCount());
	}

	/**
	 * Show all moves a row to where the link was, and the second press of a double-click on the
	 * link lands on that row without the reader having aimed at it.
	 */
	@Test
	public void aDoubleClickOnTheLinkTogglesNoRow()
	{
		legend.setToggleListener(offChart::set);
		legend.setDetail(RunDetail.of(crossbowOnly()));
		int slot = legend.getComponentCount() - 1;

		press(legend.getComponent(slot), 1);
		assertEquals("the link showed every row", everyRow() + 1, legend.getComponentCount());

		Component under = legend.getComponent(slot);
		press(under, 2);
		assertEquals(allDrawnBut(CombatMetric.ZCB_DAMAGE), offChart.get());

		press(under, 1);
		assertEquals("a press of its own switches the row's line on",
			CombatMetric.DISPLAYED.size() - 2, offChart.get().size());

		press(under, 2);
		assertEquals("and a second, the row not having moved, switches it off again",
			allDrawnBut(CombatMetric.ZCB_DAMAGE), offChart.get());
	}

	/** A row taken down while pointed at is sent no mouseExited to put its line back. */
	@Test
	public void aRowTakenDownIsNoLongerBroughtForward()
	{
		AtomicReference<CombatSeries> forward = new AtomicReference<>();
		legend.setEmphasisListener(forward::set);
		legend.setDetail(RunDetail.of(crossbowOnly()));

		// The only row up, so the last thing above the link.
		Component crossbow = legend.getComponent(legend.getComponentCount() - 2);
		crossbow.dispatchEvent(new MouseEvent(crossbow, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0,
			false));
		assertEquals(CombatMetric.ZCB_DAMAGE, forward.get());

		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, START.plusSeconds(60), null);
		legend.setDetail(RunDetail.of(run));

		assertNull(forward.get());
	}

	private static void press(Component on, int clicks)
	{
		on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, clicks, false,
			MouseEvent.BUTTON1));
	}

	/** The top row, a heading per group and a row per counter. */
	private static int everyRow()
	{
		return 1 + CombatMetric.Group.values().length + CombatMetric.DISPLAYED.size();
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
