package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A heading over the counters it covers, with what all of them came to.
 *
 * <p>The total is the group's whole figure, the catch-alls included - see
 * {@link CombatMetric.Group#amount}. The rows under it name what can be named, so the two need not
 * agree, and where they do not the tooltip says by how much and why.
 *
 * <p>The group's colour is spent on a stripe down the side rather than on the words. A heading set
 * in its own colour reads as loudly as the name of the section it sits inside, and a panel where a
 * group of three rows shouts as loudly as the block containing it has no hierarchy at all. The
 * stripe says which unit the block is counted in - the same thing the colour was saying - without
 * competing with anything.
 *
 * <p>Shared by the side panel's table and the run detail window's legend, which draw the same
 * headings over the same counters: two copies of this drifted apart would have the same figure
 * reading two ways in two windows.
 *
 * <p>Swing thread only.
 */
class GroupHeading extends JPanel
{
	/** How wide the unit's stripe down the side is. */
	private static final int STRIPE = 3;

	private final CombatMetric.Group group;

	private final JLabel value = PanelStyle.label("0", SwingConstants.RIGHT,
		FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);

	/** Where the fold arrow goes, beside the stripe, once the heading is made foldable. */
	private final JPanel lead = new JPanel(new BorderLayout());

	/** Whether a click folds the rows under this heading away - see {@link #foldable}. */
	private boolean foldable;

	private boolean folded;

	GroupHeading(CombatMetric.Group group)
	{
		super(new BorderLayout());
		this.group = group;

		JPanel tab = new JPanel();
		tab.setBackground(group.unit().color());
		tab.setPreferredSize(new Dimension(STRIPE, 0));

		JLabel text = PanelStyle.label(group.heading(), SwingConstants.LEFT,
			FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
		text.setBorder(new EmptyBorder(2, 5, 2, 5));
		value.setBorder(new EmptyBorder(2, 5, 2, 5));

		lead.setOpaque(false);
		lead.add(tab, BorderLayout.WEST);

		setBackground(PanelStyle.BACKGROUND);
		setBorder(new EmptyBorder(4, 0, 1, 0));
		add(lead, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);
		add(value, BorderLayout.EAST);
	}

	/**
	 * Makes a click on the heading fold the rows under it away, or bring them back, leaving the
	 * heading and its total - which is the figure most readers are after - where they were.
	 *
	 * <p>The heading only says whether it is folded; the rows are whoever built it to put up or
	 * take down, which is why this takes a callback rather than the rows.
	 *
	 * @param onToggle told when the heading is clicked
	 */
	void foldable(Runnable onToggle)
	{
		foldable = true;
		lead.add(new Arrow(), BorderLayout.EAST);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				onToggle.run();
			}
		});
	}

	/** @param folded whether the rows under the heading are folded away, which the arrow shows */
	void setFolded(boolean folded)
	{
		this.folded = folded;
		repaint();
	}

	/**
	 * @param totals what the heading is reading - the sitting, the lifetime, the run or one delve
	 *
	 * <p>The figure is drawn in the unit's colour, as the figures under it are, so a heading and
	 * its rows read as the same kind of number. A zero stays muted.
	 */
	void set(CombatTotals totals)
	{
		long amount = group.amount(totals);

		value.setText(DoomFormat.count(amount));
		value.setForeground(amount > 0 ? group.unit().color() : ColorScheme.LIGHT_GRAY_COLOR);

		String tooltip = group.tooltip(totals);

		setToolTipText(foldable
			? tooltip.replace("</html>", "<br><br>Click to show or hide the rows under it</html>")
			: tooltip);
	}

	/**
	 * Folded headings as they are kept in the config: their names, comma-separated. A name
	 * that is no heading any more - one folded before the headings changed - is dropped.
	 */
	static Set<CombatMetric.Group> parseFolded(String stored)
	{
		Set<CombatMetric.Group> groups = EnumSet.noneOf(CombatMetric.Group.class);

		if (stored == null)
		{
			return groups;
		}

		for (String name : stored.split(","))
		{
			for (CombatMetric.Group group : CombatMetric.Group.values())
			{
				if (group.name().equals(name.trim()))
				{
					groups.add(group);
				}
			}
		}

		return groups;
	}

	/** The other half of {@link #parseFolded}. */
	static String formatFolded(Set<CombatMetric.Group> groups)
	{
		StringJoiner names = new StringJoiner(",");

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			if (groups.contains(group))
			{
				names.add(group.name());
			}
		}

		return names.toString();
	}

	/**
	 * The fold arrow: pointing down at the rows while they are there, and at the total while they
	 * are folded away. Painted rather than set as text, because the game font the heading is set
	 * in has no arrows.
	 */
	private final class Arrow extends JPanel
	{
		/** How far the arrow's point is from its base. */
		private static final int SIZE = 4;

		private Arrow()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(SIZE * 2 + 5, 0));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D graphics = (Graphics2D) g.create();
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);

			int x = 5;
			int y = getHeight() / 2;
			Polygon arrow = new Polygon();

			if (folded)
			{
				arrow.addPoint(x, y - SIZE);
				arrow.addPoint(x + SIZE, y);
				arrow.addPoint(x, y + SIZE);
			}
			else
			{
				arrow.addPoint(x, y - SIZE / 2);
				arrow.addPoint(x + SIZE * 2, y - SIZE / 2);
				arrow.addPoint(x + SIZE, y + SIZE / 2);
			}

			graphics.fillPolygon(arrow);
			graphics.dispose();
		}
	}
}
