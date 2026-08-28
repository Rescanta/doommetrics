package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The chart's arithmetic, tested away from any painting. Everything here is static, so none of it
 * needs a display or the Swing thread.
 */
public class DelveChartTest
{
	private static final double EPSILON = 1e-9;

	@Test
	public void theAverageIsCentredOnThePointItDescribes()
	{
		List<Integer> runs = Arrays.asList(10, 20, 30, 40, 50);

		// A window of three: each point is the mean of itself and one either side, and the ends
		// average over what they have rather than being dropped.
		assertArrayEquals(new double[]{15, 20, 30, 40, 45},
			DelveChart.rollingAverage(runs, 3), EPSILON);
	}

	@Test
	public void aWindowOfOneIsTheRunsThemselves()
	{
		List<Integer> runs = Arrays.asList(7, 3, 9);
		assertArrayEquals(new double[]{7, 3, 9}, DelveChart.rollingAverage(runs, 1), EPSILON);
	}

	@Test
	public void anEmptyHistoryAveragesToNothing()
	{
		assertEquals(0, DelveChart.rollingAverage(Collections.emptyList(), 5).length);
	}

	/**
	 * The window has to widen with the history or the line just traces the dots, and it has to
	 * stop widening or a long history flattens into a bar.
	 */
	@Test
	public void theAveragingWindowWidensThenSettles()
	{
		assertEquals(5, DelveChart.windowFor(30));
		assertEquals(10, DelveChart.windowFor(200));
		assertEquals(50, DelveChart.windowFor(1000));
		assertEquals(50, DelveChart.windowFor(9000));
	}

	/**
	 * A window narrow enough to fit a short history would pass through every dot and hide it, so
	 * there is no line at all until there are enough runs for one to mean something.
	 */
	@Test
	public void aShortHistoryGetsNoTrendLine()
	{
		assertEquals(0, DelveChart.windowFor(0));
		assertEquals(0, DelveChart.windowFor(1));
		assertEquals(0, DelveChart.windowFor(29));
	}

	@Test
	public void tickStepsAreRoundNumbersThatDoNotCrowdTheAxis()
	{
		assertEquals(1, DelveChart.niceStep(8, 10));
		assertEquals(2, DelveChart.niceStep(20, 10));
		assertEquals(5, DelveChart.niceStep(50, 10));
		assertEquals(10, DelveChart.niceStep(100, 10));
		assertEquals(500, DelveChart.niceStep(4200, 10));
	}

	@Test
	public void everyTickStepIsAOneTwoOrFiveTimesAPowerOfTen()
	{
		for (int span = 0; span <= 3000; span += 7)
		{
			int step = DelveChart.niceStep(span, 8);

			assertTrue("crowded at span " + span, span / step <= 8);

			int mantissa = step;

			while (mantissa % 10 == 0)
			{
				mantissa /= 10;
			}

			assertTrue("step " + step + " is not round", mantissa == 1 || mantissa == 2 || mantissa == 5);
		}
	}
}
