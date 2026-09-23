package com.rescanta.doommetrics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
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

	/** The gold of the glowing hole, which is the one thing an unknown unique is known by. */
	private static final Color GLOW = new Color(0xFF, 0xC8, 0x40);

	/** The badge's rim: the glow, dimmed, so the mark stands out from it. */
	private static final Color GLOW_RIM = new Color(0xA8, 0x74, 0x18);

	/** The badge itself: the dark of the hole. */
	private static final Color HOLE = new Color(0x2A, 0x20, 0x16);

	/** How much of the picture the badge fills, leaving the margin an item sprite has around it. */
	private static final float BADGE_FILL = 0.875f;

	private IconArt()
	{
	}

	/**
	 * A gold question mark on a dark badge, for a unique the game signalled without naming. Drawn
	 * at the size it is shown.
	 */
	static BufferedImage unknownUnique(int width, int height)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		int diameter = Math.max(1, Math.round(Math.min(width, height) * BADGE_FILL));
		float rim = Math.max(1f, diameter / 14f);
		float left = (width - diameter) / 2f;
		float top = (height - diameter) / 2f;
		Ellipse2D badge = new Ellipse2D.Float(left + rim / 2, top + rim / 2, diameter - rim, diameter - rim);

		graphics.setColor(HOLE);
		graphics.fill(badge);
		graphics.setStroke(new BasicStroke(rim));
		graphics.setColor(GLOW_RIM);
		graphics.draw(badge);

		// Centred on what the glyph draws rather than on the font's line, which would sit the mark
		// low: a question mark has no descender.
		Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(1, Math.round(diameter * 0.72f)));
		GlyphVector mark = font.createGlyphVector(graphics.getFontRenderContext(), "?");
		Rectangle2D drawn = mark.getVisualBounds();
		graphics.setColor(GLOW);
		graphics.drawGlyphVector(mark,
			(float) (left + (diameter - drawn.getWidth()) / 2 - drawn.getX()),
			(float) (top + (diameter - drawn.getHeight()) / 2 - drawn.getY()));

		graphics.dispose();
		return image;
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

	/** A picture drawn at {@code alpha} of its strength, for a counter or a drop that is off. */
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
