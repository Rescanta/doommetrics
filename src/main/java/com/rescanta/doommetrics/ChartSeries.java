package com.rescanta.doommetrics;

import java.util.Collections;
import java.util.List;

/**
 * One value per run, and everything the chart needs to draw it without knowing what it is.
 *
 * <p>The chart plots whichever of these the reader picks, and the metrics do not share a scale -
 * a run is delve forty, or nine hundred hitpoints healed, or twelve thousand damage. Rather than
 * teach the chart about each one, a series carries its own {@link Axis}: how far the axis has to
 * reach, how its gridlines are labelled, and how a point reads when you hover it.
 */
final class ChartSeries
{
	/**
	 * How a series' numbers are drawn up the side of the chart.
	 *
	 * <p>The two differ over zero, and that is the whole reason there are two. A count is a
	 * magnitude, and a run that healed nine hundred against one that healed eighteen hundred is
	 * twice the run - which the eye only reads correctly if the axis starts at nothing. A delve
	 * number is a position on a ladder, not a magnitude, and an axis reaching zero for a character
	 * who never ends a run under delve forty would spend most of its height on empty space and
	 * flatten the part worth reading.
	 */
	enum Axis
	{
		/** Delve numbers: gridlines on tens, labelled {@code d40}, and no need to reach zero. */
		DELVE(10, false),

		/** Anything counted up from nothing: hitpoints, prayer points, damage. */
		COUNT(1, true);

		private final int stepFloor;
		private final boolean fromZero;

		Axis(int stepFloor, boolean fromZero)
		{
			this.stepFloor = stepFloor;
			this.fromZero = fromZero;
		}

		int stepFloor()
		{
			return stepFloor;
		}

		boolean isFromZero()
		{
			return fromZero;
		}

		String gridLabel(int value)
		{
			return this == DELVE ? "d" + value : DoomFormat.compact(value);
		}

		String pointLabel(int value)
		{
			return this == DELVE ? "delve " + value : DoomFormat.count(value);
		}
	}

	/** What the dots are, for the legend. */
	private final String label;

	/** What a point is counted in, for the tooltip. Empty for delves, which read on their own. */
	private final String noun;

	/** One value per recorded run, oldest first. */
	private final List<Integer> values;

	private final Axis axis;

	ChartSeries(String label, String noun, List<Integer> values, Axis axis)
	{
		this.label = label;
		this.noun = noun;
		this.values = Collections.unmodifiableList(values);
		this.axis = axis;
	}

	/** The chart's own starting state, and what it falls back to when there is nothing to show. */
	static ChartSeries empty()
	{
		return new ChartSeries("", "", Collections.emptyList(), Axis.COUNT);
	}

	String label()
	{
		return label;
	}

	List<Integer> values()
	{
		return values;
	}

	Axis axis()
	{
		return axis;
	}

	/** How the run at {@code index} reads when the pointer finds it. */
	String describe(int index)
	{
		String value = axis.pointLabel(values.get(index));
		return "Run " + (index + 1) + ": " + value + (noun.isEmpty() ? "" : " " + noun);
	}
}
