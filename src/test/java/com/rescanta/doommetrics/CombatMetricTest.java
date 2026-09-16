package com.rescanta.doommetrics;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
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
}
