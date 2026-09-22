package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
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
 * The shared look of the side panel and the detail window: {@link #hero} for the two mid-fight
 * figures, {@link #caption} for names, {@link #body} for every other figure. Colour is left to
 * callers. Swing thread only.
 */
final class PanelStyle
{
	/** What the cards sit on. */
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;

	/** A block of related figures, sunk a shade out of the background so its edges are visible. */
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;

	/** Every other row of a table, a shade off the card so a long one can be read across. */
	static final Color STRIPE = new Color(36, 36, 36);

	/** The hairline that separates a card's blocks and carries a section heading across. */
	static final Color RULE = new Color(54, 54, 54);

	/** Between one section and the next. */
	static final int SECTION_GAP = 10;

	/** Between two rows of the same card. */
	static final int ROW_GAP = 3;

	static final Border CARD_PADDING = new EmptyBorder(6, 8, 7, 8);
	static final Border CELL_PADDING = new EmptyBorder(3, 5, 3, 5);

	/** Short of the whole, so the largest figure reads as a bar rather than a recoloured row. */
	static final double METER_WIDTH = 0.94;

	/** How strongly a meter is tinted. Low enough that the figure over it stays legible. */
	private static final int METER_ALPHA = 52;

	/** Between a picture and the name beside it. */
	private static final int ICON_TEXT_GAP = 4;

	/** Sized so a seven-character clock fits half the panel width. */
	private static final Font HERO_FONT = FontManager.getRunescapeBoldFont().deriveFont(24f);

	private PanelStyle()
	{
	}

	static JLabel hero(String text, int alignment)
	{
		return label(text, alignment, HERO_FONT, DoomColors.PLAIN);
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

	/** Content on the card colour with room around it, so a block reads as one thing. */
	static JPanel card(JComponent content)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(CARD);
		panel.setBorder(CARD_PADDING);
		panel.add(content, BorderLayout.CENTER);
		return panel;
	}

	/** A named block: the name, a hairline rule across the panel, and the card under it. */
	static JPanel section(String title, Component content)
	{
		return section(title, null, content);
	}

	/**
	 * The same, with a control on the end of the heading's rule.
	 *
	 * @param control what the reader can change about this section, or null for a plain heading
	 */
	static JPanel section(String title, Component control, Component content)
	{
		JLabel heading = label(title, SwingConstants.LEFT, FontManager.getRunescapeBoldFont(),
			ColorScheme.BRAND_ORANGE);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(BACKGROUND);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		header.add(heading, BorderLayout.WEST);
		header.add(rule(), BorderLayout.CENTER);

		if (control != null)
		{
			header.add(control, BorderLayout.EAST);
		}

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(BACKGROUND);
		wrapper.add(header, BorderLayout.NORTH);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}

	/** A one pixel line down the middle of whatever width it is given. */
	static JComponent rule()
	{
		return new JComponent()
		{
			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(0, 1);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(RULE);
				g.fillRect(0, getHeight() / 2, getWidth(), 1);
			}
		};
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

	/** A unit's colour as a meter fill: the same hue, thin enough to read a figure over. */
	static Color meterFill(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), METER_ALPHA);
	}
}
