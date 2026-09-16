package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;

/**
 * The run's notable drops, written down beside the chart: what each was, the delve it came off,
 * and whether the run walked out with it.
 *
 * <p>The chart already draws each drop as an icon over its delve. This is the same thing in
 * words, for the same reason the counters have a table as well as lines: an icon says what
 * dropped to a reader who knows the icon, and the row says it to everyone. Pointing at a delve on
 * the chart lights up the drops that came off it.
 *
 * <p>Swing thread only.
 */
class RunDropsPanel extends JPanel
{
	/** The row of a drop off the delve under the pointer. */
	private static final Color HOVERED = new Color(62, 62, 58);

	/** How much of a lost drop's icon is drawn, as its name is drawn in grey. */
	private static final float LOST_ALPHA = 0.35f;

	/** What an unknown unique is drawn as in place of a name. */
	private static final BufferedImage UNKNOWN_ICON = IconArt.unknownUnique(IconArt.SMALL, IconArt.SMALL);

	private RunDetail detail = RunDetail.empty();

	/** What each drop is drawn as - its icon where there is one, its name otherwise. */
	private Icons icons = Icons.NONE;

	/** The delve under the pointer on the chart, or 0 for none. */
	private int delve;

	RunDropsPanel()
	{
		super(new DynamicGridLayout(0, 1, 0, 1));
		setBackground(PanelStyle.BACKGROUND);
	}

	/**
	 * @param icons the pictures to draw in place of the drops' names. Handed over again as they
	 *              arrive from the game, and a name stays in words until its picture has.
	 */
	void setIcons(Icons icons)
	{
		this.icons = icons;
		rebuild();
	}

	void setDetail(RunDetail detail)
	{
		this.detail = detail;
		this.delve = 0;
		rebuild();
	}

	/** @param delve the delve under the pointer on the chart, or 0 for none */
	void setDelve(int delve)
	{
		if (this.delve == delve)
		{
			return;
		}

		this.delve = delve;

		// Only the highlight moves, so only the backgrounds change. Building the rows again would
		// fade every lost drop's icon afresh and lay the sidebar out anew for each delve the
		// pointer crosses. The rows are the drops, one each and in order - see rebuild.
		List<RunDetail.Drop> drops = detail.drops();

		for (int i = 0; i < drops.size() && i < getComponentCount(); i++)
		{
			getComponent(i).setBackground(background(drops.get(i), i));
		}
	}

	/**
	 * Rebuilt whole rather than updated when the drops or their icons change, because it is a
	 * handful of rows at the very most. Pointing at a delve only recolours them - see
	 * {@link #setDelve}.
	 */
	private void rebuild()
	{
		removeAll();
		List<RunDetail.Drop> drops = detail.drops();

		for (int i = 0; i < drops.size(); i++)
		{
			add(row(drops.get(i), i));
		}

		revalidate();
		repaint();
	}

	/** A row's colour: lit while the delve it came off is pointed at, and striped otherwise. */
	private Color background(RunDetail.Drop drop, int index)
	{
		if (drop.level == delve)
		{
			return HOVERED;
		}

		return index % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE;
	}

	private JPanel row(RunDetail.Drop drop, int index)
	{
		String text = drop.quantity > 1 ? drop.quantity + " x " + drop.name : drop.name;
		Color ink = drop.kept ? ColorScheme.TEXT_COLOR : ColorScheme.MEDIUM_GRAY_COLOR;
		BufferedImage icon = drop.isUnknown() ? UNKNOWN_ICON : icons.smallItem(drop.itemId);

		JLabel name = PanelStyle.body(text, SwingConstants.LEFT);
		name.setForeground(ink);
		name.setBorder(new EmptyBorder(3, 3, 3, 0));

		if (drop.isUnknown())
		{
			// The mark and the words both: a question mark alone does not say what it is asking.
			name.setIcon(new ImageIcon(drop.kept ? icon : IconArt.fade(icon, LOST_ALPHA)));
			name.setIconTextGap(4);
		}
		else if (icon != null)
		{
			// The icon in place of the name, and a count beside it for the rare delve that dropped
			// two - where the game would print the stack's size.
			name.setIcon(new ImageIcon(drop.kept ? icon : IconArt.fade(icon, LOST_ALPHA)));
			name.setText(drop.quantity > 1 ? "x" + drop.quantity : "");
		}

		JLabel where = PanelStyle.body("Delve " + drop.level, SwingConstants.RIGHT);
		where.setForeground(drop.kept ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		where.setBorder(PanelStyle.CELL_PADDING);

		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(background(drop, index));
		panel.setBorder(new EmptyBorder(0, 5, 0, 0));
		panel.add(name, BorderLayout.CENTER);
		panel.add(where, BorderLayout.EAST);

		// The name is always here, since the row may be showing the icon in its place.
		if (drop.isUnknown())
		{
			panel.setToolTipText("<html>" + text + "<br>" + RunDetail.UNKNOWN_UNIQUE_CANDIDATES
				+ (drop.kept ? "" : "<br>" + DelveChart.lostHow(detail)) + "</html>");
		}
		else
		{
			panel.setToolTipText(drop.kept
				? text
				: "<html>" + text + "<br>" + DelveChart.lostHow(detail) + "</html>");
		}

		return panel;
	}
}
