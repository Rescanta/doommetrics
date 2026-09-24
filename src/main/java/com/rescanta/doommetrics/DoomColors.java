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

	/** The game's interface orange: the names of figures, and the buttons. */
	static final Color ORANGE = new Color(0xFF981F);

	/** The game's yellow, as on a skill level: the delve being fought. */
	static final Color YELLOW = new Color(0xFFFF00);

	/** A run still going, and a target reached. */
	static final Color LIVE = ColorScheme.PROGRESS_COMPLETE_COLOR;

	/** A death, on the overlay. */
	static final Color DEATH = new Color(0xFF4A34);

	/** The glow of the boss's lava, under the overlay's title. */
	static final Color EMBER = new Color(0xE2541C);

	/** The lit edge of the overlay's frame, and the rule between its run and its counters. */
	static final Color STONE_LIGHT = new Color(0x6A5C49);

	/** The outermost line of the overlay's frame. */
	static final Color EDGE = new Color(0x0D0D0B);

	private DoomColors()
	{
	}

	/** {@code color} at {@code alpha} out of 255. */
	static Color alpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}
}
