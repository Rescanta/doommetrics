package com.rescanta.doommetrics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CombatMetricTest
{
	/**
	 * The chart palette's eight slots, in the order they were validated as neighbours - see
	 * {@link CombatMetric#seriesColor}.
	 */
	private static final int[] PALETTE = {
		0x3987E5, 0xD95926, 0x199E70, 0xC98500, 0xD55181, 0x008300, 0x9085E9, 0xE66767,
	};

	/**
	 * The colour-blind check only covers slots that sit side by side, so the lines listed side by
	 * side have to be those slots: the first counter the first slot, and none skipped. A counter
	 * added, retired or moved fails this until the slots are handed out again in order.
	 */
	@Test
	public void theCountersDrawnTakeThePaletteInTheOrderTheyAreListed()
	{
		assertEquals(PALETTE.length, CombatMetric.DISPLAYED.size());

		for (int i = 0; i < PALETTE.length; i++)
		{
			CombatMetric metric = CombatMetric.DISPLAYED.get(i);
			assertEquals(metric + " should have palette slot " + (i + 1),
				PALETTE[i], metric.seriesColor().getRGB() & 0xFFFFFF);
		}
	}

	/** Eight lines, eight checked hues: no two lines on the chart can be told apart by name alone. */
	@Test
	public void everyCounterDrawnHasAColourToItself()
	{
		Set<Color> taken = new HashSet<>();

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			assertTrue(metric + " shares its colour", taken.add(metric.seriesColor()));
		}
	}

	/**
	 * A heading's figure is only a figure because everything under it is counted in the same
	 * thing. Two units in one group would make it hitpoints plus damage.
	 */
	@Test
	public void everyCounterUnderAHeadingIsCountedInTheHeadingsUnit()
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			assertEquals(metric + " is counted in something its heading is not",
				metric.group().unit(), metric.unit());
		}
	}

	/**
	 * A heading totals what its group counted, not what its rows say. The catch-alls have no row -
	 * an ancient godsword punish is counted under "Other melee" and named nowhere - so a heading
	 * that added up the rows would leave that damage out of every figure in the plugin.
	 */
	@Test
	public void aHeadingTotalsTheCountersWithNoRowOfTheirOwnAsWell()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.SCYTHE_PUNISH, 600);
		totals.add(CombatMetric.OTHER_MELEE_PUNISH, 141);

		assertEquals(741, CombatMetric.Group.PUNISH.amount(totals));
		assertEquals("what no row names", 141, CombatMetric.Group.PUNISH.unnamed(totals));

		assertEquals("a group nothing was counted under", 0,
			CombatMetric.Group.SPELL_HEAL.amount(totals));
	}

	/** The breakdown is only worth a line when there is something under it to break down. */
	@Test
	public void aHeadingOwnsUpToWhatItCountedAndNamedNowhere()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.SCYTHE_PUNISH, 600);

		assertFalse("nothing unnamed to mention",
			CombatMetric.Group.PUNISH.tooltip(totals).contains("Includes"));

		totals.add(CombatMetric.OTHER_MELEE_PUNISH, 141);
		String tooltip = CombatMetric.Group.PUNISH.tooltip(totals);

		assertTrue(tooltip, tooltip.contains("741 damage dealt"));
		assertTrue(tooltip, tooltip.contains("Includes 141 from Other melee"));
	}

	/** Every counter is under exactly one heading, so grouping neither drops nor double-counts. */
	@Test
	public void theHeadingsBetweenThemHoldEveryCounterOnce()
	{
		List<CombatMetric> seen = new ArrayList<>();

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			seen.addAll(group.metrics());
		}

		assertEquals(Arrays.asList(CombatMetric.values()), seen);
	}
}
