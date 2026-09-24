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
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A heading over its counters with their group total, catch-alls included, led by the skill icon
 * of its unit. Shared by the side panel and the detail legend. Swing thread only.
 */
class GroupHeading extends JPanel
{
	/** The room the unit's icon takes, held while it loads so nothing moves when it arrives. */
	private static final int ICON_SLOT = IconArt.SMALL + 4;

	private final CombatMetric.Group group;

	private final JLabel value = PanelStyle.label("0", SwingConstants.RIGHT,
		FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);

	/** The unit's skill icon. */
	private final JLabel icon = new JLabel();

	/** Where the fold arrow goes, before the icon, once the heading is made foldable. */
	private final JPanel lead = new JPanel(new BorderLayout());

	/** The icon last shown, so a heading still waiting is the only one redrawn. */
	private BufferedImage shownIcon;

	/** Whether a click folds the rows under this heading away - see {@link #foldable}. */
	private boolean foldable;

	private boolean folded;

	GroupHeading(CombatMetric.Group group)
	{
		super(new BorderLayout());
		this.group = group;

		icon.setPreferredSize(new Dimension(ICON_SLOT, IconArt.SMALL));
		icon.setHorizontalAlignment(SwingConstants.CENTER);

		JLabel text = PanelStyle.label(group.heading(), SwingConstants.LEFT,
			FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
		text.setBorder(new EmptyBorder(1, 1, 1, 5));
		value.setBorder(new EmptyBorder(1, 5, 1, 5));

		lead.setOpaque(false);
		lead.add(icon, BorderLayout.CENTER);

		setBackground(PanelStyle.CARD);
		setBorder(new EmptyBorder(PanelStyle.GRID + 2, 0, 2, 0));
		add(lead, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);
		add(value, BorderLayout.EAST);
	}

	/** @param icons where the unit's skill icon comes from; a no-op until it has arrived */
	void setIcons(Icons icons)
	{
		BufferedImage picture = icons.smallUnit(group.unit());

		if (picture == shownIcon)
		{
			return;
		}

		shownIcon = picture;
		icon.setIcon(picture == null ? null : new ImageIcon(picture));
	}

	/** Leaves the total off, for a card whose tiles already carry it. */
	void hideTotal()
	{
		remove(value);
	}

	/**
	 * Makes a click fold or unfold the heading; the caller owns the rows.
	 *
	 * @param onToggle told when the heading is clicked
	 */
	void foldable(Runnable onToggle)
	{
		foldable = true;
		lead.add(new Arrow(), BorderLayout.WEST);
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

	/** @param totals the sitting, the lifetime, the run or one delve */
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

	/** Folded headings as stored in config: comma-separated names, unknown ones dropped. */
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

	/** The fold arrow, painted because the game font has no arrows. */
	private final class Arrow extends JPanel
	{
		/** How far the arrow's point is from its base. */
		private static final int SIZE = 4;

		private Arrow()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(SIZE * 2 + 3, 0));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D graphics = (Graphics2D) g.create();
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);

			int x = 1;
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
