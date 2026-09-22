package com.rescanta.doommetrics;

/**
 * How a counter's line is led on the overlay: by its name, its picture, or its picture with two
 * counters to a line. Public because the config interface returns it - see {@link CounterMode}.
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
