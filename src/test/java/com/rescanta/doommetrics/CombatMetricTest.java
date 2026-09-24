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
		List<CombatMetric> solid = new ArrayList<>();

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			if (!metric.dashed())
			{
				solid.add(metric);
			}
		}

		assertEquals(PALETTE.length, solid.size());

		for (int i = 0; i < PALETTE.length; i++)
		{
			CombatMetric metric = solid.get(i);
			assertEquals(metric + " should have palette slot " + (i + 1),
				PALETTE[i], metric.seriesColor().getRGB() & 0xFFFFFF);
		}
	}

	/**
	 * Eight solid lines, eight checked hues. A counter past them is dashed and reuses a slot - one
	 * validated against the lines listed either side of it, so it never shares with them.
	 */
	@Test
	public void everyCounterDrawnHasAColourToItselfOrADash()
	{
		Set<Color> taken = new HashSet<>();
		List<CombatMetric> drawn = CombatMetric.DISPLAYED;

		for (int i = 0; i < drawn.size(); i++)
		{
			CombatMetric metric = drawn.get(i);

			if (!metric.dashed())
			{
				assertTrue(metric + " shares its colour", taken.add(metric.seriesColor()));
				continue;
			}

			assertTrue(metric + " reuses a slot", contains(PALETTE,
				metric.seriesColor().getRGB() & 0xFFFFFF));
			assertFalse(metric + " looks like the line above it",
				metric.seriesColor().equals(drawn.get(i - 1).seriesColor()));
			assertFalse(metric + " looks like the line below it",
				metric.seriesColor().equals(drawn.get(i + 1).seriesColor()));
		}
	}

	private static boolean contains(int[] palette, int rgb)
	{
		for (int slot : palette)
		{
			if (slot == rgb)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Grouped, the headings are the lines, and they take the same palette from the same end - see
	 * {@link CombatMetric.Group#seriesColor}. A group added or moved fails this until they do.
	 */
	@Test
	public void theHeadingsTakeThePaletteFromTheFirstSlotToo()
	{
		CombatMetric.Group[] groups = CombatMetric.Group.values();
		assertTrue("more headings than the palette has slots", groups.length <= PALETTE.length);

		for (int i = 0; i < groups.length; i++)
		{
			assertEquals(groups[i] + " should have palette slot " + (i + 1),
				PALETTE[i], groups[i].seriesColor().getRGB() & 0xFFFFFF);
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

		assertEquals(741, CombatMetric.Group.DAMAGE.amount(totals));
		assertEquals("what no row names", 141, CombatMetric.Group.DAMAGE.unnamed(totals));

		assertEquals("a group nothing was counted under", 0,
			CombatMetric.Group.HEALING.amount(totals));
	}

	/** The breakdown is only worth a line when there is something under it to break down. */
	@Test
	public void aHeadingOwnsUpToWhatItCountedAndNamedNowhere()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.SCYTHE_PUNISH, 600);

		assertFalse("nothing unnamed to mention",
			CombatMetric.Group.DAMAGE.tooltip(totals).contains("Includes"));

		totals.add(CombatMetric.OTHER_MELEE_PUNISH, 141);
		String tooltip = CombatMetric.Group.DAMAGE.tooltip(totals);

		assertTrue(tooltip, tooltip.contains("741 damage dealt"));
		assertTrue("names only the catch-all that counted: " + tooltip,
			tooltip.contains("Includes 141 from Other melee,"));

		totals.add(CombatMetric.OTHER_SPEC_DAMAGE, 59);
		tooltip = CombatMetric.Group.DAMAGE.tooltip(totals);

		assertTrue(tooltip, tooltip.contains("Includes 200 from Other specs and Other melee"));
	}

	/**
	 * Folding the counters into their headings is only worth doing if it folds: eight counters
	 * into as many headings as there are units, so a player reading every heading as a total gets
	 * one line for each thing a figure can be counted in.
	 */
	@Test
	public void thereIsOneHeadingPerUnit()
	{
		assertEquals(CombatMetric.Unit.values().length, CombatMetric.Group.values().length);

		Set<CombatMetric.Unit> units = new HashSet<>();

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			assertTrue(group + " shares its unit with another heading", units.add(group.unit()));
		}
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
