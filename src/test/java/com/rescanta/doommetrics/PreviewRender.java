package com.rescanta.doommetrics;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
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

	/**
	 * The two chatboxes a player can have, and what each draws the plugin's words in: the colour
	 * RuneLite gives a {@link ChatAnnouncement#TYPE} line on that box, the game's own unless the
	 * player has set one.
	 */
	enum Chatbox
	{
		TRANSPARENT("Transparent", JagexColors.CHAT_GAME_EXAMINE_TEXT_TRANSPARENT_BACKGROUND, true),
		OPAQUE("Opaque", JagexColors.CHAT_GAME_EXAMINE_TEXT_OPAQUE_BACKGROUND, false);

		final String label;
		final Color words;

		/** The transparent box shadows its text, so it reads over whatever the game draws behind. */
		final boolean shadowed;

		Chatbox(String label, Color words, boolean shadowed)
		{
			this.label = label;
			this.words = words;
			this.shadowed = shadowed;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** A stretch of a chat row in one colour. */
	private static final class Piece
	{
		final String text;
		final Color color;

		Piece(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}

	/**
	 * What a figure in a chat line is drawn in: RuneLite's default game message highlight, which
	 * {@code ChatColorConfig} makes the same red for both chatboxes. It only reaches a line sent as
	 * {@link ChatAnnouncement#TYPE}; any other type and the figures are drawn like the words.
	 */
	static final Color CHAT_HIGHLIGHT = new Color(0xEF1020);

	/**
	 * A flat stand-in for the opaque chatbox's parchment, which is a game sprite the harness has no
	 * copy of. Near enough to judge black and red text against, and no nearer.
	 */
	private static final Color PARCHMENT = new Color(0xCFBF98);

	/**
	 * How far a line of chat runs before the chatbox wraps it, unstretched. Measured off a stretched
	 * client, so it shows which lines wrap rather than exactly where they break.
	 */
	static final int CHAT_WIDTH = 486;

	private static final int CHAT_MARGIN = 6;

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

	/**
	 * Chat lines as the chatbox draws them: wrapped at {@link #CHAT_WIDTH}, the words in the box's
	 * own colour and each figure in the highlight. On the transparent box they are shadowed and
	 * drawn over {@code backdrop}, standing for the game behind it; on the opaque one, over the
	 * parchment.
	 *
	 * <p>Null when there are no lines, the same answer {@link #overlay} gives when it draws nothing.
	 */
	static BufferedImage chat(List<ChatAnnouncement> lines, Chatbox box, Backdrop backdrop)
	{
		if (lines.isEmpty())
		{
			return null;
		}

		Font font = FontManager.getRunescapeFont();
		Graphics2D measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		measuring.setFont(font);
		FontMetrics metrics = measuring.getFontMetrics();
		measuring.dispose();

		List<List<Piece>> rows = new ArrayList<>();

		for (ChatAnnouncement line : lines)
		{
			rows.addAll(wrap(line, box, metrics));
		}

		int pitch = metrics.getHeight();
		BufferedImage image = new BufferedImage(CHAT_WIDTH + CHAT_MARGIN * 2,
			rows.size() * pitch + CHAT_MARGIN * 2, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(box == Chatbox.OPAQUE ? PARCHMENT : backdrop.color);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setFont(font);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		for (int i = 0; i < rows.size(); i++)
		{
			int x = CHAT_MARGIN;
			int y = CHAT_MARGIN + i * pitch + metrics.getAscent();

			for (Piece piece : rows.get(i))
			{
				if (box.shadowed)
				{
					graphics.setColor(Color.BLACK);
					graphics.drawString(piece.text, x + 1, y + 1);
				}

				graphics.setColor(piece.color);
				graphics.drawString(piece.text, x, y);
				x += metrics.stringWidth(piece.text);
			}
		}

		graphics.dispose();
		return image;
	}

	/**
	 * One message broken into the rows the chatbox would draw it on, at the last space that keeps a
	 * row inside {@link #CHAT_WIDTH}. A word can change colour part way - {@code 60.0/hr.} is a red
	 * figure and a full stop in the words' colour - so it is kept as the stretches it is made of.
	 */
	private static List<List<Piece>> wrap(ChatAnnouncement line, Chatbox box, FontMetrics metrics)
	{
		List<List<Piece>> words = new ArrayList<>();
		words.add(new ArrayList<>());

		for (ChatAnnouncement.Part part : line.parts())
		{
			Color color = part.figure ? CHAT_HIGHLIGHT : box.words;
			String[] chunks = part.text.split(" ", -1);

			for (int i = 0; i < chunks.length; i++)
			{
				if (i > 0)
				{
					words.add(new ArrayList<>());
				}

				if (!chunks[i].isEmpty())
				{
					words.get(words.size() - 1).add(new Piece(chunks[i], color));
				}
			}
		}

		List<List<Piece>> rows = new ArrayList<>();
		List<Piece> row = new ArrayList<>();
		int space = metrics.stringWidth(" ");
		int width = 0;

		for (List<Piece> word : words)
		{
			if (word.isEmpty())
			{
				continue;
			}

			int wide = 0;

			for (Piece piece : word)
			{
				wide += metrics.stringWidth(piece.text);
			}

			if (!row.isEmpty() && width + space + wide > CHAT_WIDTH)
			{
				rows.add(row);
				row = new ArrayList<>();
				width = 0;
			}

			if (!row.isEmpty())
			{
				row.add(new Piece(" ", box.words));
				width += space;
			}

			row.addAll(word);
			width += wide;
		}

		rows.add(row);
		return rows;
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

	/** A whole window - the run detail window is one - drawn at the size given. */
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
