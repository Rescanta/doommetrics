package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;

/**
 * The pictures drawn beside a counter's name. Any may be null - still loading, or none
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
		public BufferedImage sprite(int spriteId)
		{
			return null;
		}

		@Override
		public BufferedImage smallSprite(int spriteId)
		{
			return null;
		}
	};

	/** What a counter counts, pictured at the size the game draws it - see {@link IconArt}. */
	BufferedImage counter(CombatMetric metric);

	/** The same, shrunk to stand in for the counter's name - see {@link IconArt#SMALL}. */
	BufferedImage smallCounter(CombatMetric metric);

	/** An interface sprite as the game draws it - a skill icon, the boss's icon. */
	BufferedImage sprite(int spriteId);

	/** The same, shrunk to a line of text high. */
	BufferedImage smallSprite(int spriteId);

	/** The skill icon a unit is pictured by, a line of text high - see {@link IconArt#spriteFor}. */
	default BufferedImage smallUnit(CombatMetric.Unit unit)
	{
		return smallSprite(IconArt.spriteFor(unit));
	}

	/** The boss's own icon, which stands for the plugin wherever the game's pictures are used. */
	default BufferedImage boss()
	{
		return sprite(IconArt.BOSS);
	}
}
