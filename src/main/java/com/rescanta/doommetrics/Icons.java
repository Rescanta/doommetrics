package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;

/**
 * The pictures drawn beside a counter's or a drop's name. Any may be null - still loading, or none
 * - and callers then draw the name alone.
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
