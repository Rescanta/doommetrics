package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
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
 * The look the side panel and the history window are both built out of: three text weights, one
 * card, one section heading.
 *
 * <p>Held in one place because the two windows draw the same figures and a block that reads as a
 * heading in one and as a row in the other is telling the reader something about the numbers that
 * is not true. What the weights are for:
 *
 * <ul>
 * <li>{@link #hero} is the figure you look up mid-fight and nothing else - the delve you are on
 * and how long you have been down there.
 * <li>{@link #caption} names something: a section, a column, a row, a group of rows. Never a
 * number.
 * <li>{@link #body} is every figure that is not a hero one.
 * </ul>
 *
 * <p>Colour is left to the caller, because what a figure is counted in is the one thing colour is
 * spent on here - see {@link CombatMetric.Unit#color()}.
 *
 * <p>Swing thread only.
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

	/**
	 * How much of a row's width a meter can fill, as a fraction. Kept short of the whole so the
	 * largest figure in a unit reads as a bar rather than as a row that has changed colour.
	 */
	static final double METER_WIDTH = 0.94;

	/** How strongly a meter is tinted. Low enough that the figure over it stays legible. */
	private static final int METER_ALPHA = 52;

	/**
	 * The two figures worth reading at a glance, at a size you can read without stopping.
	 *
	 * <p>Sized to what the tile it sits in can hold: the widest clock a run reaches is seven
	 * characters, at half the panel width. Larger than this and an hour long run has its seconds
	 * clipped off, which is worse than a figure a point smaller.
	 */
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

	/**
	 * A named block: the name, a hairline carrying it across the panel, and the card under it.
	 *
	 * <p>The rule is what makes the heading a heading. Without it the four or five names down the
	 * panel are only more text in another colour, which is how a group of rows inside a card ends
	 * up reading as loudly as the section containing it.
	 */
	static JPanel section(String title, Component content)
	{
		JLabel heading = label(title, SwingConstants.LEFT, FontManager.getRunescapeBoldFont(),
			ColorScheme.BRAND_ORANGE);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(BACKGROUND);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		header.add(heading, BorderLayout.WEST);
		header.add(rule(), BorderLayout.CENTER);

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

	/** A unit's colour as a meter fill: the same hue, thin enough to read a figure over. */
	static Color meterFill(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), METER_ALPHA);
	}
}
