package com.rescanta.doommetrics;

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
	@Test
	public void gridStepsAreRoundNumbersThatDoNotCrowdTheAxis()
	{
		assertEquals(1, DelveChart.niceStep(8, 10));
		assertEquals(2, DelveChart.niceStep(20, 10));
		assertEquals(5, DelveChart.niceStep(50, 10));
		assertEquals(10, DelveChart.niceStep(100, 10));
		assertEquals(500, DelveChart.niceStep(4200, 10));
	}

	@Test
	public void everyGridStepIsAOneTwoOrFiveTimesAPowerOfTen()
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

			assertTrue("step " + step + " is not round",
				mantissa == 1 || mantissa == 2 || mantissa == 5);
		}
	}

	/**
	 * The world record run is past delve 260 and the axis has to hold more than that, so the delve
	 * ticks are checked at the depths a chart actually has to draw rather than only at short ones.
	 */
	@Test
	public void theDelveAxisStaysReadableOnTheDeepestRuns()
	{
		assertEquals(20, DelveChart.niceStep(200, 10));
		assertEquals(50, DelveChart.niceStep(260, 10));
		assertEquals(50, DelveChart.niceStep(350, 10));
		assertEquals(100, DelveChart.niceStep(900, 10));
	}

	/**
	 * A clock wants the spans a reader would pick. {@link DelveChart#niceStep} would put a
	 * gridline every fifty seconds on a run of ordinary delves and label it {@code 0:50}.
	 */
	@Test
	public void theTimeStripsGridlinesLandOnRoundSpans()
	{
		assertEquals(15, DelveChart.timeStep(30, 2));
		assertEquals(60, DelveChart.timeStep(100, 2));
		assertEquals(60, DelveChart.timeStep(120, 2));
		assertEquals(120, DelveChart.timeStep(300, 2));
		assertEquals(600, DelveChart.timeStep(1200, 2));
	}

	/** A delve slow enough to run off the longest step is off any scale worth drawing. */
	@Test
	public void aTimeStepIsNeverInventedPastTheLongestOne()
	{
		assertEquals(3600, DelveChart.timeStep(Integer.MAX_VALUE, 2));
	}

	@Test
	public void everyTimeStepIsARoundSpanThatDoesNotCrowdTheStrip()
	{
		for (int span = 0; span <= 4000; span += 3)
		{
			int step = DelveChart.timeStep(span, 2);

			assertTrue("crowded at span " + span, span / step <= 2 || step == 3600);
			assertTrue("step " + step + " is not a round span",
				step == 5 || step == 10 || step == 15 || step == 30 || step % 60 == 0);
		}
	}

	/**
	 * A short run reads delve by delve; a long one needs a trend through it, and the window has to
	 * widen with the run and then stop widening.
	 */
	@Test
	public void theAveragingWindowWidensThenSettles()
	{
		assertEquals(9, DelveChart.windowFor(80));
		assertEquals(15, DelveChart.windowFor(150));
		assertEquals(25, DelveChart.windowFor(260));
		assertEquals(25, DelveChart.windowFor(350));
		assertEquals(25, DelveChart.windowFor(900));
	}

	/**
	 * A window narrow enough to fit a short run would pass through every delve and hide it, so
	 * there is no average at all until there are enough delves for one to mean something.
	 */
	@Test
	public void aShortRunGetsNoAverage()
	{
		assertEquals(0, DelveChart.windowFor(0));
		assertEquals(0, DelveChart.windowFor(1));
		assertEquals(0, DelveChart.windowFor(79));
	}

	@Test
	public void theAverageIsCentredOnTheDelveItDescribes()
	{
		// A window of three: each delve is the mean of itself and one either side, and the ends
		// average over what they have rather than being dropped.
		assertArrayEquals(new double[]{15, 20, 30, 40, 45},
			DelveChart.rollingAverage(new long[]{10, 20, 30, 40, 50}, 3), 1e-9);
	}

	@Test
	public void aWindowOfOneIsTheDelvesThemselves()
	{
		assertArrayEquals(new double[]{7, 3, 9},
			DelveChart.rollingAverage(new long[]{7, 3, 9}, 1), 1e-9);
	}

	@Test
	public void aRunWithNoDelvesAveragesToNothing()
	{
		assertEquals(0, DelveChart.rollingAverage(new long[0], 5).length);
	}

	/** Both ends of an axis land on a gridline, so nothing is drawn off the top of the plot. */
	@Test
	public void anAxisReachesPastTheLargestFigureOnIt()
	{
		assertEquals(120, DelveChart.ceilTo(101, 20));
		assertEquals(100, DelveChart.ceilTo(100, 20));
		assertEquals(20, DelveChart.ceilTo(1, 20));
	}

	/**
	 * A figure landing exactly on a gridline gets another one over it, so the line is never drawn
	 * along the top edge of the plot - which happens all the time, since a run of delves that each
	 * took two minutes tops out at exactly two minutes.
	 */
	@Test
	public void anAxisStopsClearOfTheLargestFigureRatherThanOnIt()
	{
		assertEquals(180, DelveChart.topFor(120, 60));
		assertEquals(120, DelveChart.topFor(101, 60));
		assertEquals(60, DelveChart.topFor(1, 60));
		assertEquals(60, DelveChart.topFor(0, 60));
	}

	/** A run that counted nothing has no axis to reach, and must not ask for a negative one. */
	@Test
	public void anAxisOverNothingIsNothing()
	{
		assertEquals(0, DelveChart.ceilTo(0, 20));
		assertEquals(0, DelveChart.ceilTo(-5, 20));
		assertEquals(0, DelveChart.ceilTo(10, 0));
	}

	/** Icons with room between them all sit in the row nearest the plot. */
	@Test
	public void iconsFarApartShareOneRow()
	{
		assertArrayEquals(new int[]{0, 0, 0},
			DelveChart.stackRows(new int[]{10, 60, 200}, 30, 3));
	}

	/** Too close to sit side by side, each goes up a row, and the last row takes the overflow. */
	@Test
	public void iconsTooCloseTogetherStack()
	{
		assertArrayEquals(new int[]{0, 1, 2, 2, 0},
			DelveChart.stackRows(new int[]{100, 110, 120, 125, 130}, 30, 3));
	}

	/** Two drops off the same delve are two icons, one over the other. */
	@Test
	public void twoDropsOffOneDelveStack()
	{
		assertArrayEquals(new int[]{0, 1}, DelveChart.stackRows(new int[]{50, 50}, 30, 3));
	}

	/** The rows are worked out left to right whatever order the drops are listed in. */
	@Test
	public void rowsDoNotDependOnTheOrderTheDropsLanded()
	{
		assertArrayEquals(new int[]{1, 0, 0},
			DelveChart.stackRows(new int[]{110, 100, 300}, 30, 3));
	}

	@Test
	public void noDropsNeedNoRows()
	{
		assertEquals(0, DelveChart.stackRows(new int[0], 30, 3).length);
	}
}
