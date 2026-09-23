package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * The overlay's head: one figure large with its name over it, an optional second figure on the
 * right in bold, and an optional thin meter under both. Built fresh each frame.
 */
final class OverlayHero implements LayoutableRenderableEntity
{
	/** Twice the bold game font, so the pixel font scales without smearing. */
	static final Font HEADLINE_FONT = FontManager.getRunescapeBoldFont().deriveFont(32f);

	/** Names over the figures: the game's small font, quieter than the figures. */
	static final Color CAPTION_COLOR = new Color(0xB4B4B4);

	private static final Color TRACK_COLOR = new Color(0, 0, 0, 120);

	/** Between the figures and the meter, and the meter's own height. */
	private static final int METER_GAP = 3;
	private static final int METER_HEIGHT = 3;

	/** Above the block, so its names do not run into the title. */
	private static final int TOP_GAP = 2;

	/** Below the whole block, so the first line under it does not crowd the large digits. */
	private static final int BOTTOM_GAP = 3;

	private final String caption;
	private final String value;
	private final Color valueColor;

	private String sideCaption;
	private String sideValue;
	private Color sideColor;

	/** From 0 to 1, or negative for no meter. */
	private double progress = -1;
	private Color progressColor;

	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension();

	OverlayHero(String caption, String value, Color valueColor)
	{
		this.caption = caption;
		this.value = value;
		this.valueColor = valueColor;
	}

	/** A second figure, right-aligned in bold, with its name over it. */
	OverlayHero side(String caption, String value, Color color)
	{
		this.sideCaption = caption;
		this.sideValue = value;
		this.sideColor = color;
		return this;
	}

	/** A thin meter under the figures, from 0 to 1. */
	OverlayHero meter(double progress, Color color)
	{
		this.progress = Math.max(0, Math.min(1, progress));
		this.progressColor = color;
		return this;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Font small = FontManager.getRunescapeSmallFont();
		Font line = graphics.getFont();
		Font sideFont = FontManager.getRunescapeBoldFont();
		FontMetrics smallMetrics = graphics.getFontMetrics(small);
		FontMetrics bigMetrics = graphics.getFontMetrics(HEADLINE_FONT);
		FontMetrics sideMetrics = graphics.getFontMetrics(sideFont);

		int x = preferredLocation.x;
		int y = preferredLocation.y;
		int width = preferredSize.width;

		int captionBaseline = y + TOP_GAP + smallMetrics.getAscent();
		// The large digits have little descent to speak of, so they sit on their ascent alone.
		int valueBaseline = captionBaseline + 2 + bigMetrics.getAscent() - bigMetrics.getDescent();

		text(graphics, small, caption, CAPTION_COLOR, x, captionBaseline);
		text(graphics, HEADLINE_FONT, value, valueColor, x, valueBaseline);

		if (sideValue != null)
		{
			text(graphics, small, sideCaption, CAPTION_COLOR,
				x + width - smallMetrics.stringWidth(sideCaption), captionBaseline);
			text(graphics, sideFont, sideValue, sideColor,
				x + width - sideMetrics.stringWidth(sideValue), valueBaseline);
		}

		int bottom = valueBaseline + 1;

		if (progress >= 0)
		{
			int top = bottom + METER_GAP;
			graphics.setColor(TRACK_COLOR);
			graphics.fillRect(x, top, width, METER_HEIGHT);
			graphics.setColor(progressColor);
			graphics.fillRect(x, top, (int) Math.round(width * progress), METER_HEIGHT);
			bottom = top + METER_HEIGHT;
		}

		// Restored, since the lines after this one measure themselves in it.
		graphics.setFont(line);

		Dimension size = new Dimension(width, bottom + BOTTOM_GAP - y);
		bounds.setLocation(preferredLocation);
		bounds.setSize(size);
		return size;
	}

	private static void text(Graphics2D graphics, Font font, String text, Color color, int x,
		int baseline)
	{
		graphics.setFont(font);
		TextComponent component = new TextComponent();
		component.setText(text);
		component.setColor(color);
		component.setPosition(new Point(x, baseline));
		component.render(graphics);
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		preferredLocation = position;
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		preferredSize = dimension;
	}
}
