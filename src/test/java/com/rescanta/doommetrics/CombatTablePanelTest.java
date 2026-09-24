package com.rescanta.doommetrics;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CombatTablePanelTest
{
	private final CombatTablePanel table = new CombatTablePanel();
	private final AtomicReference<Set<CombatMetric.Group>> told = new AtomicReference<>();

	/** Every heading, and a row for every counter drawn: nothing folded to begin with. */
	@Test
	public void startsWithEveryRowShowing()
	{
		assertEquals(CombatMetric.Group.values().length + CombatMetric.DISPLAYED.size(),
			table.getComponentCount());
	}

	/** A folded heading keeps its place and its total, and only the rows under it go. */
	@Test
	public void foldingAHeadingTakesDownOnlyItsRows()
	{
		table.setFoldListener(told::set);
		table.toggle(CombatMetric.Group.DAMAGE);

		assertTrue(table.isFolded(CombatMetric.Group.DAMAGE));
		assertEquals("the four damage counters are folded away",
			CombatMetric.Group.values().length + CombatMetric.DISPLAYED.size() - 4,
			table.getComponentCount());
		assertEquals(EnumSet.of(CombatMetric.Group.DAMAGE), told.get());

		table.toggle(CombatMetric.Group.DAMAGE);
		assertFalse(table.isFolded(CombatMetric.Group.DAMAGE));
		assertEquals(Collections.emptySet(), told.get());
	}

	/** Restoring what the reader last left is not a click, so nothing is saved back. */
	@Test
	public void beingToldWhatIsFoldedTellsNobody()
	{
		table.setFoldListener(told::set);
		table.setFolded(EnumSet.of(CombatMetric.Group.HEALING, CombatMetric.Group.PRAYER));

		assertNull(told.get());
		assertEquals(CombatMetric.Group.values().length + 4, table.getComponentCount());
	}

	/** What is kept in the config reads back as it was, and a name no heading has any more is dropped. */
	@Test
	public void theFoldedHeadingsSurviveTheConfig()
	{
		Set<CombatMetric.Group> folded = EnumSet.of(CombatMetric.Group.HEALING,
			CombatMetric.Group.DAMAGE);

		assertEquals("HEALING,DAMAGE", GroupHeading.formatFolded(folded));
		assertEquals(folded, GroupHeading.parseFolded("HEALING,DAMAGE"));
		assertEquals(EnumSet.of(CombatMetric.Group.PRAYER),
			GroupHeading.parseFolded("PUNISH, PRAYER"));
		assertEquals(Collections.emptySet(), GroupHeading.parseFolded(""));
		assertEquals(Collections.emptySet(), GroupHeading.parseFolded(null));
	}

	/** With counters at 0 hidden, only the headings are up, and the link under them says how many are not. */
	@Test
	public void hidingCountersAtZeroLeavesTheHeadingsAndALink()
	{
		table.setHideEmpty(true);

		assertEquals(CombatMetric.Group.values().length + 1, table.getComponentCount());
		assertEquals("Show all (" + CombatMetric.DISPLAYED.size() + " at 0)", linkText());
	}

	@Test
	public void aCounterComesUpTheMomentItCounts()
	{
		table.setHideEmpty(true);

		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.SCYTHE_PUNISH, 120);
		table.setTotals(totals);

		assertEquals(CombatMetric.Group.values().length + 1 + 1, table.getComponentCount());
		assertEquals("Show all (" + (CombatMetric.DISPLAYED.size() - 1) + " at 0)", linkText());
	}

	@Test
	public void theLinkBringsEveryRowBackAndTakesThemAwayAgain()
	{
		table.setHideEmpty(true);
		table.setShowingAll(true);

		assertEquals(CombatMetric.Group.values().length + CombatMetric.DISPLAYED.size() + 1,
			table.getComponentCount());
		assertEquals("Hide counters at 0", linkText());

		table.setShowingAll(false);
		assertEquals(CombatMetric.Group.values().length + 1, table.getComponentCount());
	}

	/** Nothing at 0, or the setting off, and there is nothing for a link to show. */
	@Test
	public void noLinkWhenNothingIsHidden()
	{
		CombatTotals totals = new CombatTotals();

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			totals.add(metric, 1);
		}

		table.setHideEmpty(true);
		table.setTotals(totals);

		assertEquals(CombatMetric.Group.values().length + CombatMetric.DISPLAYED.size(),
			table.getComponentCount());
	}

	/** The last component is the link, whenever there is one. */
	private String linkText()
	{
		return ((javax.swing.JLabel) table.getComponent(table.getComponentCount() - 1)).getText();
	}
}
