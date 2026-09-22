package com.rescanta.doommetrics;

/**
 * How a counter's line is drawn on the overlay: led by its name, by the picture of what it counts,
 * or by that picture with two counters sharing a line.
 *
 * <p>One setting rather than an icon switch and a columns switch, because two columns only fit
 * with pictures: a name and a figure already fill a whole overlay line, and two of them side by
 * side would wrap. As two switches, one of the four combinations would do nothing at all.
 *
 * <p>A heading drawn as a {@link CounterMode#TOTAL} keeps its name whatever this says - it sums
 * several sources and no one picture stands for all of them - and has a line to itself.
 *
 * <p>Public because the config interface returns it - see {@link CounterMode} for why.
 */
public enum CounterStyle
{
	/** Every counter by name, one to a line. */
	NAMES("Names"),

	/** Every counter by its picture, one to a line. */
	ICONS("Icons"),

	/** Every counter by its picture, two to a line. */
	ICON_GRID("Icon grid");

	private final String label;

	CounterStyle(String label)
	{
		this.label = label;
	}

	/** Whether counters are drawn by their pictures rather than their names. */
	boolean icons()
	{
		return this != NAMES;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
