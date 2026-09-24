package com.rescanta.doommetrics;

/**
 * What the plugin draws over the game during a run. Off only stops the drawing. Public because the
 * config interface returns it - see {@link CounterMode}.
 */
public enum DisplayStyle
{
	/** Every line you have switched on, drawn as an overlay panel. */
	PANEL("Overlay"),

	/** One infobox square, showing the figure picked in the config. */
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
