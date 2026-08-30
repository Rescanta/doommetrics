package com.rescanta.doommetrics;

/**
 * How the counters switched on in the config are drawn on the overlay.
 *
 * <p>Which counters are shown is a separate question, answered one checkbox at a time. This only
 * decides whether the ones you picked arrive as their own lines or folded into their group's, so
 * turning a source off and changing how it reads never means touching the same setting twice.
 *
 * <p>Public because the config interface returns it, and RuneLite implements that interface with a
 * dynamic proxy outside this package: a package-private return type there throws IllegalAccessError
 * at the call site, which on the overlay's path means inside the draw loop.
 */
public enum MetricDisplay
{
	/** One line per group, summing whichever of its sources are switched on. */
	COMBINED("Combined"),

	/** One line per source, each naming what it counts. */
	SEPARATE("Separate");

	private final String label;

	MetricDisplay(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
