package com.rescanta.doommetrics;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.util.ImageUtil;

/** Which picture stands for each counter, and how a picture is cut down to sit beside a name. */
final class IconArt
{
	/** A line of text high. */
	static final int SMALL = 16;

	/** The boss's own icon, from the hiscores and the collection log. */
	static final int BOSS = SpriteID.IconBoss25x25.DOOM_OF_MOKHAIOTL;

	private IconArt()
	{
	}

	/** The item a counter is pictured by, or -1 for one pictured by a sprite or not at all. */
	static int itemFor(CombatMetric metric)
	{
		switch (metric)
		{
			case AGS_HEAL:
				return ItemID.ANCIENT_GODSWORD;

			case BLOWPIPE_HEAL:
				return ItemID.TOXIC_BLOWPIPE;

			case SGS_HEAL:
			case SGS_PRAYER:
				return ItemID.SGS;

			case ELDRITCH_PRAYER:
				return ItemID.NIGHTMARE_STAFF_ELDRITCH;

			case ZCB_DAMAGE:
				return ItemID.ZARYTE_XBOW;

			case SCYTHE_PUNISH:
				return ItemID.SCYTHE_OF_VITUR;

			case NOXIOUS_HALBERD_PUNISH:
				return ItemID.NOXIOUS_HALBERD;

			case CRYSTAL_HALBERD_PUNISH:
				return ItemID.CRYSTAL_HALBERD;

			default:
				return -1;
		}
	}

	/**
	 * The interface sprite a counter is pictured by, or -1 for one pictured by an item or not at
	 * all.
	 */
	static int spriteFor(CombatMetric metric)
	{
		switch (metric)
		{
			case BLOOD_BARRAGE_HEAL:
				return SpriteID.Magicon2.BLOOD_BARRAGE;

			default:
				return -1;
		}
	}

	/** The skill whose icon stands for a unit: what the figures are counted in. */
	static int spriteFor(CombatMetric.Unit unit)
	{
		switch (unit)
		{
			case HITPOINTS:
				return SpriteID.Staticons.HITPOINTS;

			case PRAYER:
				return SpriteID.Staticons.PRAYER;

			default:
				return SpriteID.Staticons.STRENGTH;
		}
	}

	/**
	 * Trims a picture to what is drawn in it, then shrinks it to a {@code box} square. Never
	 * enlarges.
	 *
	 * @return the picture, or null when nothing is drawn in it (a sprite still loading)
	 */
	static BufferedImage shrink(BufferedImage image, int box)
	{
		Rectangle drawn = image == null ? null : drawnBounds(image);

		if (drawn == null)
		{
			return null;
		}

		BufferedImage trimmed = copy(image.getSubimage(drawn.x, drawn.y, drawn.width, drawn.height));
		double scale = Math.min(1, Math.min((double) box / drawn.width, (double) box / drawn.height));

		if (scale >= 1)
		{
			return trimmed;
		}

		int width = Math.max(1, (int) Math.round(drawn.width * scale));
		int height = Math.max(1, (int) Math.round(drawn.height * scale));
		return ImageUtil.resizeImage(trimmed, width, height);
	}

	/** A picture drawn at {@code alpha} of its strength, for a counter that is off. */
	static BufferedImage fade(BufferedImage image, float alpha)
	{
		BufferedImage faded = new BufferedImage(image.getWidth(), image.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = faded.createGraphics();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return faded;
	}

	/** A picture with its colours brought down to {@code brightness}, its shape kept. */
	static BufferedImage darken(BufferedImage image, float brightness)
	{
		BufferedImage dark = copy(image);
		new RescaleOp(new float[]{brightness, brightness, brightness, 1f}, new float[4], null)
			.filter(dark, dark);
		return dark;
	}

	/**
	 * The smallest rectangle holding every pixel that is not fully transparent, or null for none.
	 */
	private static Rectangle drawnBounds(BufferedImage image)
	{
		int left = image.getWidth();
		int top = image.getHeight();
		int right = -1;
		int bottom = -1;

		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					left = Math.min(left, x);
					top = Math.min(top, y);
					right = Math.max(right, x);
					bottom = Math.max(bottom, y);
				}
			}
		}

		return right < 0 ? null : new Rectangle(left, top, right - left + 1, bottom - top + 1);
	}

	/** A picture of its own, rather than a window onto the one it was cut out of. */
	private static BufferedImage copy(BufferedImage image)
	{
		BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = copy.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return copy;
	}
}
