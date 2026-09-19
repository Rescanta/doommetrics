package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Dimension;
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

		setBackground(PanelStyle.BACKGROUND);
		setBorder(new EmptyBorder(4, 0, 1, 0));
		add(tab, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);
		add(value, BorderLayout.EAST);
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
		setToolTipText(group.tooltip(totals));
	}
}
