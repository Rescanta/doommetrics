package com.rescanta.doommetrics;

import java.util.Arrays;

/** The arithmetic behind {@link DelveChart}'s axes, averaging and drop lane. */
final class ChartMath
{
	/** Past this many delves the counters are drawn as a rolling average over the raw lines. */
	static final int TREND_FROM_DELVES = 80;

	/** Clock-friendly gridline steps, in seconds. */
	private static final int[] TIME_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};

	private ChartMath()
	{
	}

	/**
	 * Where an axis stops: the next gridline strictly above {@code highest}, so a line on a round
	 * number isn't drawn along the top edge.
	 */
	static int topFor(int highest, int step)
	{
		int top = ceilTo(highest, step);
		return top <= highest ? top + step : Math.max(step, top);
	}

	/** The next multiple of {@code step} at or above {@code value}. */
	static int ceilTo(int value, int step)
	{
		if (step <= 0 || value <= 0)
		{
			return 0;
		}

		int over = value % step;
		return over == 0 ? value : value + step - over;
	}

	/**
	 * The smallest 1-2-5 step that divides {@code span} into at most {@code maxTicks} intervals.
	 */
	static int niceStep(int span, int maxTicks)
	{
		for (int decade = 1; ; decade *= 10)
		{
			for (int mantissa : new int[]{1, 2, 5})
			{
				int step = mantissa * decade;

				if (span / step <= maxTicks)
				{
					return step;
				}
			}
		}
	}

	/** {@link #niceStep} for a clock, off {@link #TIME_STEPS}. */
	static int timeStep(int spanSeconds, int maxTicks)
	{
		for (int step : TIME_STEPS)
		{
			if (spanSeconds / step <= maxTicks)
			{
				return step;
			}
		}

		return TIME_STEPS[TIME_STEPS.length - 1];
	}

	/**
	 * The rolling average window: 0 below {@link #TREND_FROM_DELVES}, else delves/10 within 9..25.
	 * Always odd, since the average is centred.
	 */
	static int windowFor(int delves)
	{
		return delves < TREND_FROM_DELVES ? 0 : (Math.max(9, Math.min(25, delves / 10)) | 1);
	}

	/** A centred mean over {@code window} delves; the ends average over what is there. */
	static double[] rollingAverage(long[] values, int window)
	{
		double[] out = new double[values.length];
		int half = window / 2;

		for (int i = 0; i < values.length; i++)
		{
			int from = Math.max(0, i - half);
			int to = Math.min(values.length - 1, i + half);
			long sum = 0;

			for (int j = from; j <= to; j++)
			{
				sum += values[j];
			}

			out[i] = (double) sum / (to - from + 1);
		}

		return out;
	}

	/**
	 * The lowest lane row each icon fits in without overlap, or the last row when none has room.
	 */
	static int[] stackRows(int[] lefts, int spacing, int maxRows)
	{
		Integer[] order = new Integer[lefts.length];

		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}

		Arrays.sort(order, (a, b) -> Integer.compare(lefts[a], lefts[b]));

		int[] rows = new int[lefts.length];
		int[] free = new int[maxRows];
		Arrays.fill(free, Integer.MIN_VALUE);

		for (int i : order)
		{
			int row = maxRows - 1;

			for (int r = 0; r < maxRows; r++)
			{
				if (lefts[i] >= free[r])
				{
					row = r;
					break;
				}
			}

			rows[i] = row;
			free[row] = lefts[i] + spacing;
		}

		return rows;
	}
}
