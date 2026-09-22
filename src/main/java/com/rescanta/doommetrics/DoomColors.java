package com.rescanta.doommetrics;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/** Colours for figures whose unit doesn't choose one, shared so the overlay and infobox agree. */
final class DoomColors
{
	/** A figure with nothing behind it yet - a counter still at zero, a pace with no average. */
	static final Color DIMMED = ColorScheme.LIGHT_GRAY_COLOR;

	/** A figure that is not counted in anything: a delve number, a clock, a rate. */
	static final Color PLAIN = Color.WHITE;

	private DoomColors()
	{
	}
}
