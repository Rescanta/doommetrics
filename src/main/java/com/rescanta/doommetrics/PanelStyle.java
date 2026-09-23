package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * The shared look of the side panel and the detail window: rounded cards on the panel, each led by
 * a {@link #hero} figure or two, {@link #stat} for tile figures, {@link #caption} for names,
 * {@link #body} for every other figure. Colour is left to callers. Swing thread only.
 */
final class PanelStyle
{
	/** What the cards sit on. */
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;

	/** A block of related figures, sunk a shade out of the background. */
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;

	/** A tile inside a card, lifted back towards the background so it reads as a cell. */
	static final Color TILE = new Color(37, 37, 37);

	/** Every other row of a table, a shade off the card so a long one can be read across. */
	static final Color STRIPE = new Color(35, 35, 35);

	/** The hairline between a card's blocks. */
	static final Color RULE = new Color(50, 50, 50);

	/** The empty part of a meter. */
	static final Color TRACK = new Color(52, 52, 52);

	/** The one colour that is not a unit's: progress, the selected control, the main button. */
	static final Color ACCENT = ColorScheme.BRAND_ORANGE;

	/** The spacing grid: every gap and padding is a multiple of it. */
	static final int GRID = 4;

	/** Between one card and the next. */
	static final int SECTION_GAP = 2 * GRID;

	/** Corner diameter of a card; tiles and pills take half of it. */
	static final int ARC = 10;

	static final Border CARD_PADDING = new EmptyBorder(2 * GRID, 2 * GRID, 2 * GRID, 2 * GRID);
	static final Border TILE_PADDING = new EmptyBorder(GRID, 2 * GRID - 2, GRID + 1, 2 * GRID - 2);
	static final Border CELL_PADDING = new EmptyBorder(3, 5, 3, 5);

	/** How tall a slim meter under a row is. */
	static final int METER_HEIGHT = 3;

	/** Between a picture and the name beside it. */
	private static final int ICON_TEXT_GAP = 4;

	/** Sized so a seven-character clock fits half the panel width. */
	private static final Font HERO_FONT = FontManager.getRunescapeBoldFont().deriveFont(24f);

	private PanelStyle()
	{
	}

	/** The one or two figures a card is led by. */
	static JLabel hero(String text, int alignment)
	{
		return label(text, alignment, HERO_FONT, DoomColors.PLAIN);
	}

	/** A hero that may be a rate, its {@code /hr} in small type. */
	static Figure heroFigure(int alignment)
	{
		return new Figure(HERO_FONT, alignment);
	}

	/** A tile's figure that may be a rate. */
	static Figure statFigure(int alignment)
	{
		return new Figure(FontManager.getRunescapeBoldFont(), alignment);
	}

	/** A tile's figure: bold, a step under a hero. */
	static JLabel stat(String text, int alignment)
	{
		return label(text, alignment, FontManager.getRunescapeBoldFont(), DoomColors.PLAIN);
	}

	static JLabel caption(String text, int alignment)
	{
		return label(text, alignment, FontManager.getRunescapeSmallFont(),
			ColorScheme.LIGHT_GRAY_COLOR);
	}

	static JLabel body(String text, int alignment)
	{
		return label(text, alignment, FontManager.getRunescapeSmallFont(), ColorScheme.TEXT_COLOR);
	}

	static JLabel label(String text, int alignment, Font font, Color color)
	{
		JLabel label = new JLabel(text, alignment);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	/** A stack of rows on the card colour, for anything that is not a grid. */
	static JPanel column(int gap)
	{
		JPanel panel = new JPanel(new DynamicGridLayout(0, 1, 0, gap));
		panel.setBackground(CARD);
		return panel;
	}

	/** A named card: its title on the first line, and the content under it. */
	static JPanel section(String title, Component content)
	{
		return section(title, null, content);
	}

	/**
	 * The same, with a control on the end of the title line.
	 *
	 * @param control what the reader can change about this card, or null for a plain title
	 */
	static JPanel section(String title, Component control, Component content)
	{
		JPanel header = new JPanel(new BorderLayout(2 * GRID, 0));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 2 * GRID, 0));
		header.add(title(title), BorderLayout.WEST);

		if (control != null)
		{
			header.add(control, BorderLayout.EAST);
		}

		RoundedPanel card = new RoundedPanel(CARD, ARC);
		card.setLayout(new BorderLayout());
		card.setBorder(CARD_PADDING);
		card.add(header, BorderLayout.NORTH);
		card.add(content, BorderLayout.CENTER);
		return card;
	}

	/** A card's title: the section's name, quiet beside the figures it heads. */
	static JLabel title(String text)
	{
		return label(text, SwingConstants.LEFT, FontManager.getRunescapeBoldFont(), ACCENT);
	}

	/**
	 * A cell for one figure: its name above, the figure under it.
	 *
	 * @param value the figure, which sets the tile's weight - {@link #hero} or {@link #stat}
	 */
	static JPanel tile(JLabel caption, JComponent value)
	{
		RoundedPanel tile = new RoundedPanel(TILE, ARC);
		tile.setLayout(new BorderLayout(0, 1));
		tile.setBorder(TILE_PADDING);
		tile.add(caption, BorderLayout.NORTH);
		tile.add(value, BorderLayout.SOUTH);
		return tile;
	}

	/**
	 * Puts a picture beside the label's words, or just the words while there is none. The name
	 * stays off the label's tooltip, which would give it mouse listeners that swallow the row's.
	 */
	static void nameAndIcon(JLabel label, String name, BufferedImage icon)
	{
		label.setText(name);
		label.setIcon(icon == null ? null : new ImageIcon(icon));
		label.setIconTextGap(icon == null ? 0 : ICON_TEXT_GAP);
	}

	/** A figure shortened to fit a tile: whole to six characters, then {@code 25k}, {@code 1.2m}. */
	static String tileFigure(long amount)
	{
		String whole = DoomFormat.count(amount);
		return whole.length() <= 6 ? whole : DoomFormat.compact(amount);
	}

	/** A colour at a lower opacity. */
	static Color alpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	/**
	 * A large figure with its unit in small type after it - {@code 26.7} then {@code /hr} - so the
	 * digits take the weight and a rate still fits half a card.
	 */
	static final class Figure extends JPanel
	{
		private static final String RATE = "/hr";

		private final JLabel number;
		private final JLabel unit = caption("", SwingConstants.LEFT);

		Figure(Font font, int alignment)
		{
			FlowLayout layout = new FlowLayout(alignment == SwingConstants.RIGHT
				? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0);
			layout.setAlignOnBaseline(true);
			setLayout(layout);
			setOpaque(false);

			number = label("-", SwingConstants.LEFT, font, DoomColors.PLAIN);
			unit.setBorder(new EmptyBorder(0, 2, 0, 0));
			add(number);
			add(unit);
		}

		/** @param text a formatted figure; a rate's {@code /hr} is split off into small type */
		void setText(String text)
		{
			boolean rate = text.endsWith(RATE);
			number.setText(rate ? text.substring(0, text.length() - RATE.length()) : text);
			unit.setText(rate ? RATE : "");
		}

		@Override
		public void setForeground(Color color)
		{
			super.setForeground(color);

			if (number != null)
			{
				number.setForeground(color);
			}
		}

		@Override
		public void setToolTipText(String text)
		{
			super.setToolTipText(text);
			if (number != null)
			{
				number.setToolTipText(text);
			}
		}
	}
}
