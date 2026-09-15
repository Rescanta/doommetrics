package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IconArtTest
{
	/** Drawn at the size it is asked for, with a transparent margin and the gold mark in the middle. */
	@Test
	public void theUnknownUniqueIsAGoldMarkOnABadge()
	{
		for (int size : new int[]{IconArt.SMALL, 32})
		{
			BufferedImage image = IconArt.unknownUnique(size, size);
			assertEquals(size, image.getWidth());
			assertEquals(size, image.getHeight());
			assertEquals("the corner is outside the badge", 0, image.getRGB(0, 0) >>> 24);

			boolean gold = false;

			for (int y = 0; y < size; y++)
			{
				for (int x = 0; x < size; x++)
				{
					Color pixel = new Color(image.getRGB(x, y), true);
					gold |= pixel.getAlpha() == 255 && pixel.getRed() > 200 && pixel.getGreen() > 150
						&& pixel.getBlue() < 120;
				}
			}

			assertTrue("a " + size + "px badge should carry the gold mark", gold);
		}
	}

	@Test
	public void everyCounterDrawnIsPicturedByAnItemOrASpriteButNeverBoth()
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			boolean item = IconArt.itemFor(metric) > 0;
			boolean sprite = IconArt.spriteFor(metric) >= 0;

			if (metric.displayed())
			{
				assertTrue(metric + " should be pictured by exactly one thing", item ^ sprite);
			}
			else
			{
				assertFalse(metric + " is drawn nowhere, so needs no picture", item || sprite);
			}
		}
	}

	/** So a counter added later is not drawn by name in the harness while it has an icon in game. */
	@Test
	public void theHarnessHasAPictureForEveryCounter()
	{
		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			assertNotNull(metric + " has no harness picture", PreviewIcons.INSTANCE.counter(metric));
			assertNotNull(metric + " has no small harness picture",
				PreviewIcons.INSTANCE.smallCounter(metric));
		}
	}

	@Test
	public void shrinkTrimsTheEmptySlotAroundTheItem()
	{
		// An inventory slot with a 6x10 item drawn in the middle of it.
		BufferedImage slot = picture(36, 32);
		paint(slot, 15, 11, 6, 10, Color.RED);

		BufferedImage shrunk = IconArt.shrink(slot, IconArt.SMALL);

		assertEquals("already small enough, so only trimmed", 6, shrunk.getWidth());
		assertEquals(10, shrunk.getHeight());
		assertEquals(Color.RED.getRGB(), shrunk.getRGB(0, 0));
	}

	@Test
	public void shrinkFitsTheBoxKeepingTheShape()
	{
		BufferedImage wide = picture(36, 32);
		paint(wide, 0, 8, 32, 16, Color.BLUE);

		BufferedImage shrunk = IconArt.shrink(wide, IconArt.SMALL);

		assertEquals(IconArt.SMALL, shrunk.getWidth());
		assertEquals("half as tall as it is wide, as before", IconArt.SMALL / 2, shrunk.getHeight());
	}

	@Test
	public void shrinkNeverEnlarges()
	{
		BufferedImage tiny = picture(4, 4);
		paint(tiny, 0, 0, 4, 4, Color.GREEN);

		BufferedImage shrunk = IconArt.shrink(tiny, IconArt.SMALL);

		assertEquals(4, shrunk.getWidth());
		assertEquals(4, shrunk.getHeight());
	}

	@Test
	public void shrinkHasNothingToGiveForAnEmptyPicture()
	{
		assertNull("a sprite still loading is drawn blank", IconArt.shrink(picture(36, 32), 16));
		assertNull(IconArt.shrink(null, 16));
	}

	@Test
	public void shrinkHandsBackAPictureOfItsOwn()
	{
		BufferedImage slot = picture(8, 8);
		paint(slot, 2, 2, 4, 4, Color.RED);

		BufferedImage shrunk = IconArt.shrink(slot, IconArt.SMALL);
		paint(slot, 2, 2, 4, 4, Color.BLUE);

		assertEquals("painting the original afterwards leaves the copy alone",
			Color.RED.getRGB(), shrunk.getRGB(0, 0));
	}

	@Test
	public void fadeKeepsTheSizeAndThinsThePicture()
	{
		BufferedImage solid = picture(3, 3);
		paint(solid, 0, 0, 3, 3, Color.WHITE);

		BufferedImage faded = IconArt.fade(solid, 0.5f);

		assertEquals(3, faded.getWidth());
		assertEquals(3, faded.getHeight());
		int alpha = faded.getRGB(1, 1) >>> 24;
		assertTrue("half strength, give or take rounding: " + alpha, Math.abs(alpha - 128) <= 1);
	}

	@Test
	public void noIconsDrawsEverythingByName()
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			assertNull(Icons.NONE.counter(metric));
			assertNull(Icons.NONE.smallCounter(metric));
		}

		assertSame(null, Icons.NONE.item(1));
		assertSame(null, Icons.NONE.smallItem(1));
	}

	private static BufferedImage picture(int width, int height)
	{
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	private static void paint(BufferedImage image, int x, int y, int width, int height, Color color)
	{
		for (int row = y; row < y + height; row++)
		{
			for (int column = x; column < x + width; column++)
			{
				image.setRGB(column, row, color.getRGB());
			}
		}
	}
}
