package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * What makes the overlay read as one of the game's own panels: the stone frame round it, a title
 * over an ember line, the target's bar, and the rule between the run and its counters.
 */
final class OverlayChrome
{
	private OverlayChrome()
	{
	}

	/**
	 * The game's interface edge over the panel's own: a black line round the outside and a bevel
	 * inside it. Drawn in the panel's border, so it never covers a line.
	 */
	static void frame(Graphics2D graphics, Dimension size)
	{
		if (size == null || size.width < 4 || size.height < 4)
		{
			return;
		}

		int right = size.width - 1;
		int bottom = size.height - 1;

		graphics.setColor(DoomColors.EDGE);
		graphics.drawRect(0, 0, right, bottom);

		graphics.setColor(DoomColors.alpha(DoomColors.STONE_LIGHT, 200));
		graphics.drawLine(1, 1, right - 1, 1);
		graphics.drawLine(1, 1, 1, bottom - 1);

		graphics.setColor(DoomColors.alpha(DoomColors.EDGE, 170));
		graphics.drawLine(2, bottom - 1, right - 1, bottom - 1);
		graphics.drawLine(right - 1, 2, right - 1, bottom - 1);
	}

	/** A line that glows at its middle and fades out at both ends. */
	private static void glow(Graphics2D graphics, int x, int y, int width, Color color)
	{
		int middle = x + width / 2;
		Color faded = DoomColors.alpha(color, 0);

		graphics.setPaint(new GradientPaint(x, y, faded, middle, y, color));
		graphics.fillRect(x, y, width / 2, 1);
		graphics.setPaint(new GradientPaint(middle, y, color, x + width, y, faded));
		graphics.fillRect(middle, y, width - width / 2, 1);
	}

	/** The panel's title, centred as the game centres an interface's, with an ember line under it. */
	static final class Title extends Entity
	{
		/** Between the title and its ember line, and under the line. */
		private static final int UNDER = 3;

		private static final Font FONT = FontManager.getRunescapeBoldFont();

		private final String text;

		Title(String text)
		{
			this.text = text;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			Font previous = graphics.getFont();
			graphics.setFont(FONT);
			FontMetrics metrics = graphics.getFontMetrics();

			int lineHeight = metrics.getHeight();
			int x = location.x + Math.max(0, (size.width - metrics.stringWidth(text)) / 2);
			int y = location.y;

			TextComponent words = new TextComponent();
			words.setText(text);
			words.setColor(DoomColors.ORANGE);
			words.setFont(FONT);
			words.setPosition(new Point(x, y + metrics.getAscent()));
			words.render(graphics);

			glow(graphics, location.x, y + lineHeight + UNDER - 1, size.width, DoomColors.EMBER);
			graphics.setFont(previous);

			return done(lineHeight + UNDER + 1);
		}
	}

	/** The rule between the run's lines and its counters. */
	static final class Rule extends Entity
	{
		/** The line and the space either side of it. */
		static final int HEIGHT = 5;

		@Override
		public Dimension render(Graphics2D graphics)
		{
			glow(graphics, location.x, location.y + HEIGHT / 2, size.width,
				DoomColors.alpha(DoomColors.STONE_LIGHT, 230));
			return done(HEIGHT);
		}
	}

	/** How far the run is towards its target, as a thin bar under the target's line. */
	static final class Progress extends Entity
	{
		/** The bar and the space above and below it. */
		private static final int HEIGHT = 3;
		private static final int PAD = 2;

		private static final Color TRACK = new Color(0, 0, 0, 110);

		private final double fill;

		/** @param fill from 0 to 1; full turns the bar green */
		Progress(double fill)
		{
			this.fill = Math.max(0, Math.min(1, fill));
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			int y = location.y + PAD;
			graphics.setColor(TRACK);
			graphics.fillRect(location.x, y, size.width, HEIGHT);
			graphics.setColor(fill >= 1 ? DoomColors.LIVE : PanelStyle.ACCENT);
			graphics.fillRect(location.x, y, (int) Math.round(size.width * fill), HEIGHT);
			return done(HEIGHT + PAD * 2);
		}
	}

	/** The bookkeeping every panel child needs, and nothing else. */
	private abstract static class Entity implements LayoutableRenderableEntity
	{
		final Rectangle bounds = new Rectangle();
		Point location = new Point();
		Dimension size = new Dimension();

		Dimension done(int height)
		{
			bounds.setLocation(location);
			bounds.setSize(size.width, height);
			return new Dimension(size.width, height);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}

		@Override
		public void setPreferredLocation(Point position)
		{
			location = position;
		}

		@Override
		public void setPreferredSize(Dimension dimension)
		{
			size = dimension;
		}
	}
}
