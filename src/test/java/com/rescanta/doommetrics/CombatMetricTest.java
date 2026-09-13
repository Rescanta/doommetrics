package com.rescanta.doommetrics;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CombatMetricTest
{
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
