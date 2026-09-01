package com.rescanta.doommetrics;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.InfoBoxComponent;
import net.runelite.client.util.ImageUtil;

/**
 * Draws the plugin's widgets outside the client - into an image, or into whatever the preview
 * window puts on screen.
 *
 * <p>The overlay needs nothing but a {@link Graphics2D} to draw into, so it is drawn directly. The
 * Swing widgets need a laid-out container behind them, so they are packed into a window that is
 * never shown and printed out of it: {@code printAll} rather than {@code paint} because a window
 * that was never made visible has nothing in its double buffer to copy from.
 */
final class PreviewRender
{
	/** Backdrops to judge the overlay's own translucent panel against. */
	enum Backdrop
	{
		CAVE("Cave floor", new Color(0x2B2622)),
		STONE("Lit stone", new Color(0x6B6357)),
		GLARE("Glare", new Color(0xC9BFA6));

		final String label;
		final Color color;

		Backdrop(String label, Color color)
		{
			this.label = label;
			this.color = color;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** Room for the tallest overlay any scene can produce. The drawing is cropped back to fit. */
	private static final int CANVAS = 900;

	/** The size a fresh install draws an infobox at - {@code RuneLiteConfig.infoBoxSize}. */
	static final int INFOBOX_SIZE = 35;

	/** Space around and between the squares in a grid of them. */
	private static final int GRID_GAP = 6;

	private PreviewRender()
	{
	}

	/**
	 * One overlay drawing, cropped to what it actually covered, or null when the overlay declined
	 * to draw - which is itself a state worth seeing, and the one the plugin is in most of the time.
	 *
	 * <p>Drawn twice, and only the second one kept. A panel's size is measured from the children of
	 * the draw before it, so the first draw of all is the only one that comes out the wrong size -
	 * in the client that is one frame at the start of a run, and here it would be every picture.
	 */
	static BufferedImage overlay(DoomMetricsOverlay overlay)
	{
		BufferedImage canvas = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();

		// What the client's overlay renderer sets up before handing its graphics to an overlay.
		// Without the game font the panel is measured in a font it will never be drawn in, and
		// every column in it lands somewhere the real thing does not.
		graphics.setFont(FontManager.getRunescapeFont());
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		overlay.render(graphics);
		graphics.setComposite(AlphaComposite.Clear);
		graphics.fillRect(0, 0, CANVAS, CANVAS);
		graphics.setComposite(AlphaComposite.SrcOver);

		Dimension size = overlay.render(graphics);
		graphics.dispose();

		if (size == null || size.width <= 0 || size.height <= 0)
		{
			return null;
		}

		return canvas.getSubimage(0, 0,
			Math.min(size.width, CANVAS), Math.min(size.height, CANVAS));
	}

	/** The picture an infobox carries here: the plugin's icon, loaded the way the plugin loads it. */
	static BufferedImage icon()
	{
		return ImageUtil.loadImageResource(DoomMetricsPlugin.class, "panel_icon.png");
	}

	/**
	 * One infobox square as the client draws it: the picture, the figure over it, and the
	 * background the client puts behind every infobox, at the size a fresh install uses.
	 *
	 * <p>Null when the box declines to draw, which is the state it is in whenever there is no run
	 * - the same answer {@link #overlay} gives for the same reason.
	 */
	static BufferedImage infoBox(DoomMetricsInfoBox box)
	{
		if (!box.render())
		{
			return null;
		}

		InfoBoxComponent component = new InfoBoxComponent();
		component.setText(box.getText());
		component.setColor(box.getTextColor());
		component.setImage(box.getImage());
		component.setFont(FontManager.getRunescapeFont());
		component.setPreferredSize(new Dimension(INFOBOX_SIZE, INFOBOX_SIZE));

		BufferedImage canvas =
			new BufferedImage(INFOBOX_SIZE, INFOBOX_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		component.render(graphics);
		graphics.dispose();
		return canvas;
	}

	/**
	 * Squares laid out in a grid with what each one is written under it, wrapped at
	 * {@code columns}, so every figure the square can hold is one picture rather than sixteen.
	 *
	 * <p>A cell with nothing in it is left as a gap rather than skipped: which figures are drawing
	 * and which are not is the thing worth seeing when a scene has none of them.
	 */
	static BufferedImage grid(List<BufferedImage> cells, List<String> labels, int columns,
		Backdrop backdrop)
	{
		Graphics2D measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		measuring.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = measuring.getFontMetrics();

		int cellWidth = INFOBOX_SIZE;

		for (String label : labels)
		{
			cellWidth = Math.max(cellWidth, metrics.stringWidth(label));
		}

		measuring.dispose();

		cellWidth += GRID_GAP;
		int cellHeight = INFOBOX_SIZE + metrics.getHeight() + GRID_GAP;
		int rows = (cells.size() + columns - 1) / columns;

		BufferedImage image = new BufferedImage(cellWidth * columns + GRID_GAP,
			cellHeight * rows + GRID_GAP, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(backdrop.color);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setFont(FontManager.getRunescapeSmallFont());
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		for (int i = 0; i < cells.size(); i++)
		{
			int x = GRID_GAP + (i % columns) * cellWidth;
			int y = GRID_GAP + (i / columns) * cellHeight;
			BufferedImage cell = cells.get(i);

			if (cell != null)
			{
				graphics.drawImage(cell, x, y, null);
			}

			graphics.setColor(Color.WHITE);
			graphics.drawString(labels.get(i), x, y + INFOBOX_SIZE + metrics.getAscent());
		}

		graphics.dispose();
		return image;
	}

	/** An overlay drawing sat on a backdrop, the way it sits on the game rather than on nothing. */
	static BufferedImage against(BufferedImage drawing, Backdrop backdrop, int margin)
	{
		return against(drawing, backdrop, margin, "(overlay draws nothing)");
	}

	/** As above, saying {@code nothing} in place of the drawing when there is not one. */
	static BufferedImage against(BufferedImage drawing, Backdrop backdrop, int margin,
		String nothing)
	{
		int width = (drawing == null ? 160 : drawing.getWidth()) + margin * 2;
		int height = (drawing == null ? 40 : drawing.getHeight()) + margin * 2;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(backdrop.color);
		graphics.fillRect(0, 0, width, height);

		if (drawing == null)
		{
			graphics.setFont(FontManager.getRunescapeFont());
			graphics.setColor(Color.WHITE);
			graphics.drawString(nothing, margin, margin + 12);
		}
		else
		{
			graphics.drawImage(drawing, margin, margin, null);
		}

		graphics.dispose();
		return image;
	}

	/** A Swing component at its preferred size, drawn without ever putting a window on screen. */
	static BufferedImage component(JComponent content)
	{
		JFrame frame = new JFrame();

		try
		{
			frame.setUndecorated(true);
			frame.setContentPane(content);
			frame.pack();
			return print(content, content.getWidth(), content.getHeight());
		}
		finally
		{
			// Disposing releases the peer that pack() created; the component itself is untouched
			// and can be handed to another frame afterwards.
			frame.dispose();
		}
	}

	/** A whole window - the history window is one - drawn at the size given. */
	static BufferedImage window(JFrame frame, int width, int height)
	{
		try
		{
			frame.pack();
			frame.setSize(width, height);
			frame.validate();

			Container content = frame.getContentPane();
			return print(content, content.getWidth(), content.getHeight());
		}
		finally
		{
			frame.dispose();
		}
	}

	private static BufferedImage print(Container content, int width, int height)
	{
		BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
			BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		content.printAll(graphics);
		graphics.dispose();
		return image;
	}

	/**
	 * Blown up by a whole number of pixels, nearest neighbour, so the game font stays the shape it
	 * is on screen instead of being smeared into something no player ever sees.
	 */
	static BufferedImage scale(BufferedImage image, int factor)
	{
		int width = image.getWidth() * factor;
		int height = image.getHeight() * factor;

		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = scaled.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();
		return scaled;
	}

	static void write(BufferedImage image, Path file) throws IOException
	{
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}

	/**
	 * Stops with something worth reading rather than a headless exception out of the middle of a
	 * layout, since every widget here needs a screen to be laid out against.
	 */
	static void requireDisplay()
	{
		if (GraphicsEnvironment.isHeadless())
		{
			throw new IllegalStateException(
				"The preview harness needs a display: Swing cannot lay a panel out without one");
		}
	}
}
