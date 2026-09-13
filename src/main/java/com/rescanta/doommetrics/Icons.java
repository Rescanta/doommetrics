package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;

/**
 * The pictures drawn in place of a counter's or a drop's name: the weapon, the spell or the item,
 * as the game draws it.
 *
 * <p>Any of them can be null - still loading, or nothing to draw - and whatever asks for one draws
 * the name instead, so a picture that has not arrived yet never leaves a gap where a name should
 * be.
 *
 * <p>The plugin's come out of the game - see {@link GameIcons}. The preview harness, which has no
 * game, keeps copies with its tests.
 */
interface Icons
{
	/** No pictures at all, so everything is drawn by name. */
	Icons NONE = new Icons()
	{
		@Override
		public BufferedImage counter(CombatMetric metric)
		{
			return null;
		}

		@Override
		public BufferedImage smallCounter(CombatMetric metric)
		{
			return null;
		}

		@Override
		public BufferedImage item(int itemId)
		{
			return null;
		}

		@Override
		public BufferedImage smallItem(int itemId)
		{
			return null;
		}
	};

	/** What a counter counts, pictured at the size the game draws it - see {@link IconArt}. */
	BufferedImage counter(CombatMetric metric);

	/** The same, shrunk to stand in for the counter's name - see {@link IconArt#SMALL}. */
	BufferedImage smallCounter(CombatMetric metric);

	/** An item as the game draws it in the inventory. */
	BufferedImage item(int itemId);

	/** The same, shrunk to stand in for the item's name. */
	BufferedImage smallItem(int itemId);
}
