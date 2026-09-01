package com.rescanta.doommetrics;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/**
 * The colours a figure is drawn in when its unit does not choose one - see
 * {@link CombatMetric.Unit#color()} for the ones that do.
 *
 * <p>Held together rather than at each drawing site because the overlay and the infobox are two
 * views of the same numbers, and a zero that reads as dimmed on one and as live on the other would
 * be telling you two different things about the same counter.
 */
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
