package com.rescanta.doommetrics;

/**
 * What the plugin puts over the game while a run is on: the panel of lines, a single square, or
 * nothing at all.
 *
 * <p>Off leaves the tracking exactly where it was. The delves are still timed, the counters still
 * count, the chat messages still arrive and the side panel and history still fill up - the only
 * thing switched off is the drawing, which is the one part of the plugin that occupies the screen
 * you are playing on.
 *
 * <p>Public because the config interface returns it, for the reason spelled out on
 * {@link MetricDisplay}: RuneLite implements that interface with a dynamic proxy from another
 * package, and a package-private return type there throws IllegalAccessError at the call site.
 */
public enum DisplayStyle
{
	/** Every line you have switched on, drawn as an overlay panel. */
	PANEL("Panel"),

	/**
	 * One infobox square, showing the single figure picked in the config. Everything else the
	 * panel would have said is either in the tooltip or in the side panel.
	 */
	INFOBOX("Infobox"),

	/** Nothing drawn over the game. */
	OFF("Off");

	private final String label;

	DisplayStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
