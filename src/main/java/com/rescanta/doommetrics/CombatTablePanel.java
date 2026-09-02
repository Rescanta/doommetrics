package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * A {@link CombatTotals} laid out as a table: what healed you, what restored your prayer and what
 * your specs hit for, under the heading each belongs to.
 *
 * <p>A component in its own right for the same reason {@link MilestoneTablePanel} is one - the side
 * panel draws the sitting's figures with it and the history window draws the character's lifetime
 * figures with it, and two tables built separately would drift apart.
 *
 * <p>Every row is built once and only ever retexted. The rows never change: eight metrics under
 * four headings, whether or not any of them has fired. A metric that has counted nothing reads zero
 * in the muted colour rather than vanishing, so the table does not reflow as a run goes on and so
 * the reader can see that a source they expected to fire has not.
 *
 * <p>Each row carries a meter behind its figure, filled against the largest figure counted in the
 * same unit - see {@link #setTotals}. Eight numbers in a column say what each source gave you but
 * not which of them was carrying the run, and the answer is the shape of the column rather than
 * any one number in it.
 *
 * <p>Swing thread only.
 */
class CombatTablePanel extends JPanel
{
	/** The rows, in declaration order, so an update is eight setTexts and eight fills. */
	private final MeterRow[] rows = new MeterRow[CombatMetric.values().length];

	CombatTablePanel()
	{
		super(new DynamicGridLayout(0, 1, 0, 1));
		setBackground(PanelStyle.BACKGROUND);
		build();
		setTotals(null);
	}

	private void build()
	{
		CombatMetric.Group heading = null;
		int striped = 0;

		for (CombatMetric metric : CombatMetric.values())
		{
			if (metric.group() != heading)
			{
				heading = metric.group();
				add(heading(heading));

				// Restarted under each heading so the stripes read as a block per group rather
				// than as one run of alternating rows the headings happen to interrupt.
				striped = 0;
			}

			MeterRow row = new MeterRow(metric,
				striped++ % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE);
			rows[metric.ordinal()] = row;
			add(row);
		}
	}

	/**
	 * Repaints every figure. A null tally reads as all zeroes, which is what it means.
	 *
	 * <p>The meters are scaled per unit rather than across the whole table: hitpoints, prayer
	 * points and damage are three different things counted in three different sizes, and a damage
	 * figure is routinely twenty times a healing one. Put on one scale it would leave every
	 * healing row an indistinguishable stub. Scaled per unit, a bar answers the question the reader
	 * actually has - which of these sources is doing the work - and never invites the comparison
	 * across colours that the numbers do not support.
	 */
	void setTotals(CombatTotals totals)
	{
		long[] largest = new long[CombatMetric.Unit.values().length];

		for (CombatMetric metric : CombatMetric.values())
		{
			long amount = totals == null ? 0 : totals.get(metric);
			largest[metric.unit().ordinal()] = Math.max(largest[metric.unit().ordinal()], amount);
		}

		for (CombatMetric metric : CombatMetric.values())
		{
			long amount = totals == null ? 0 : totals.get(metric);
			rows[metric.ordinal()].set(amount, largest[metric.unit().ordinal()]);
		}
	}

	/**
	 * A group's name, over the rows it covers.
	 *
	 * <p>The group's colour is spent on a stripe down the side rather than on the words. A heading
	 * set in its own colour reads as loudly as the name of the section it sits inside, and a panel
	 * where a group of three rows shouts as loudly as the block containing it has no hierarchy at
	 * all. The stripe says which unit the block is counted in - the same thing the colour was
	 * saying - without competing with anything.
	 */
	private static JPanel heading(CombatMetric.Group group)
	{
		JPanel tab = new JPanel();
		tab.setBackground(group.unit().color());
		tab.setPreferredSize(new Dimension(3, 0));

		JLabel text = PanelStyle.label(group.heading(), SwingConstants.LEFT,
			FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
		text.setBorder(new EmptyBorder(2, 5, 2, 5));

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(PanelStyle.BACKGROUND);
		panel.setBorder(new EmptyBorder(4, 0, 1, 0));
		panel.add(tab, BorderLayout.WEST);
		panel.add(text, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * One metric: its name, its figure, and a meter behind both.
	 *
	 * <p>The meter is painted rather than laid out, so it costs the row no height and cannot push
	 * the figure out of line with the figures above it.
	 */
	private static final class MeterRow extends JPanel
	{
		private final CombatMetric metric;
		private final Color stripe;
		private final JLabel value = PanelStyle.body("0", SwingConstants.RIGHT);

		/** How much of the row the meter fills, from nothing to {@link PanelStyle#METER_WIDTH}. */
		private double fill;

		private MeterRow(CombatMetric metric, Color stripe)
		{
			super(new BorderLayout());
			this.metric = metric;
			this.stripe = stripe;

			JLabel label = PanelStyle.body(metric.label(), SwingConstants.LEFT);
			label.setBorder(PanelStyle.CELL_PADDING);
			value.setBorder(PanelStyle.CELL_PADDING);

			setBackground(stripe);
			add(label, BorderLayout.WEST);
			add(value, BorderLayout.EAST);
		}

		/**
		 * @param amount  what this metric has counted
		 * @param largest the most anything counted in the same unit has counted, which is what
		 *                fills the meter
		 */
		private void set(long amount, long largest)
		{
			value.setText(DoomFormat.count(amount));
			// Coloured by what it counts, matching the overlay's lines, so the two read as the
			// same figures. A zero stays muted - see CombatMetric.Unit#color.
			value.setForeground(amount > 0 ? metric.unit().color() : ColorScheme.LIGHT_GRAY_COLOR);
			value.setToolTipText(amount > 0
				? DoomFormat.count(amount) + " " + metric.unit().description()
				: "Nothing counted yet");

			fill = amount > 0 && largest > 0 ? (double) amount / largest : 0;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(stripe);
			g.fillRect(0, 0, getWidth(), getHeight());

			if (fill <= 0)
			{
				return;
			}

			g.setColor(PanelStyle.meterFill(metric.unit().color()));
			g.fillRect(0, 0, (int) (getWidth() * PanelStyle.METER_WIDTH * fill), getHeight());
		}
	}
}
