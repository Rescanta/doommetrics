package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;

/**
 * A {@link CombatTotals} laid out as a table: what healed you, what restored your prayer and what
 * your specs hit for, under the heading each belongs to.
 *
 * <p>A component in its own right so the figures are laid out in one place: the side panel draws
 * the sitting's tally with it, and anything else that has a {@link CombatTotals} to show can draw
 * that one the same way rather than growing a second table that drifts from this.
 *
 * <p>Every heading carries its group's total: what all of its counters came to, including the
 * catch-alls that have no row - see {@link CombatMetric.Group#amount}. A column of sources answers
 * which of them did the work and never answers how much work there was, which is the figure anyone
 * comparing one sitting with another is after.
 *
 * <p>Every row is built once and only ever retexted. The rows never change: eight metrics under
 * three headings, whether or not any of them has fired. A metric that has counted nothing reads
 * zero in the muted colour rather than vanishing, so the table does not reflow as a run goes on and
 * so the reader can see that a source they expected to fire has not.
 *
 * <p>A click on a heading folds its rows away, leaving the heading's total - see
 * {@link #setFolded}. Which headings are folded is the reader's to choose and nothing else moves
 * them, so the table still never reflows by itself.
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
	/** The rows, in declaration order, so an update is a setText and a fill per metric. */
	private final MeterRow[] rows = new MeterRow[CombatMetric.values().length];

	/** The headings, by group, so each can be handed its total the same way. */
	private final GroupHeading[] headings = new GroupHeading[CombatMetric.Group.values().length];

	/** The pictures drawn beside the rows' names, or none while they are still on their way. */
	private Icons icons = Icons.NONE;

	/** The headings whose rows are folded away. */
	private final Set<CombatMetric.Group> folded = EnumSet.noneOf(CombatMetric.Group.class);

	private Consumer<Set<CombatMetric.Group>> onFoldChanged = groups ->
	{
	};

	CombatTablePanel()
	{
		super(new DynamicGridLayout(0, 1, 0, 1));
		setBackground(PanelStyle.BACKGROUND);
		build();
		layOut();
		setTotals(null);
	}

	private void build()
	{
		CombatMetric.Group heading = null;
		int striped = 0;

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			if (metric.group() != heading)
			{
				CombatMetric.Group group = metric.group();
				GroupHeading row = new GroupHeading(group);
				row.foldable(() -> toggle(group));
				headings[group.ordinal()] = row;
				heading = group;

				// Restarted under each heading so the stripes read as a block per group rather
				// than as one run of alternating rows the headings happen to interrupt.
				striped = 0;
			}

			rows[metric.ordinal()] = new MeterRow(metric,
				striped++ % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE);
		}
	}

	/**
	 * Puts up every heading, and the rows under the ones not folded.
	 *
	 * <p>Taken down and put back rather than hidden, because the grid lays out a hidden component
	 * as a gap the height of the tallest row.
	 */
	private void layOut()
	{
		removeAll();

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			GroupHeading heading = headings[group.ordinal()];
			heading.setFolded(folded.contains(group));
			add(heading);

			if (folded.contains(group))
			{
				continue;
			}

			for (CombatMetric metric : group.metrics())
			{
				if (metric.displayed())
				{
					add(rows[metric.ordinal()]);
				}
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * @param groups the headings to fold down to their totals, as the reader last left them
	 *
	 * <p>Tells no listener: this is the table being told what it already was, not a click.
	 */
	void setFolded(Set<CombatMetric.Group> groups)
	{
		folded.clear();
		folded.addAll(groups);
		layOut();
	}

	/** @param onFoldChanged handed the headings folded down, whenever a click changes them */
	void setFoldListener(Consumer<Set<CombatMetric.Group>> onFoldChanged)
	{
		this.onFoldChanged = onFoldChanged;
	}

	/** Folds a heading's rows away, or brings them back. */
	void toggle(CombatMetric.Group group)
	{
		if (!folded.remove(group))
		{
			folded.add(group);
		}

		layOut();
		onFoldChanged.accept(EnumSet.copyOf(folded));
	}

	/** Whether the rows under a heading are folded away, for the tests. */
	boolean isFolded(CombatMetric.Group group)
	{
		return folded.contains(group);
	}

	/**
	 * @param icons the pictures to draw beside the counters' names. Handed over again as they
	 *              arrive from the game, and a row stands on its name alone until its picture has.
	 */
	void setIcons(Icons icons)
	{
		this.icons = icons;

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			rows[metric.ordinal()].showName(icons);
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
		// An empty tally rather than a null one from here down: every figure below reads the same
		// zero out of it, and the headings' tooltips have something to ask for their breakdown.
		CombatTotals counted = totals == null ? new CombatTotals() : totals;
		long[] largest = new long[CombatMetric.Unit.values().length];

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			long amount = counted.get(metric);
			largest[metric.unit().ordinal()] = Math.max(largest[metric.unit().ordinal()], amount);
		}

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			rows[metric.ordinal()].set(counted.get(metric), largest[metric.unit().ordinal()]);
		}

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			headings[group.ordinal()].set(counted);
		}
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
		private final JLabel label;

		/** How much of the row the meter fills, from nothing to {@link PanelStyle#METER_WIDTH}. */
		private double fill;

		/**
		 * What the name was last drawn with, so the pictures arriving one at a time redraw the
		 * rows waiting on theirs and leave the rows already holding one alone.
		 */
		private BufferedImage shownIcon;

		private MeterRow(CombatMetric metric, Color stripe)
		{
			super(new BorderLayout());
			this.metric = metric;
			this.stripe = stripe;

			label = PanelStyle.body(metric.label(), SwingConstants.LEFT);
			label.setBorder(PanelStyle.CELL_PADDING);
			value.setBorder(PanelStyle.CELL_PADDING);

			setBackground(stripe);
			// The name in the middle and the figure on the edge, so a row too narrow for both
			// takes it out of the name - which the row's tooltip still spells out - rather than
			// clipping digits off a lifetime figure, which nothing else says.
			add(label, BorderLayout.CENTER);
			add(value, BorderLayout.EAST);

			// On the row rather than the label, so the gap beside a short name answers too. The
			// figure keeps its own tooltip, which Swing shows in preference while over it.
			List<String> sources = metric.sources();

			setToolTipText(sources.isEmpty()
				? metric.label()
				: "<html>" + metric.label() + "<br><br>Counted from:<br>"
					+ String.join("<br>", sources) + "</html>");
		}

		private void showName(Icons icons)
		{
			BufferedImage icon = icons.smallCounter(metric);

			if (icon == shownIcon)
			{
				return;
			}

			shownIcon = icon;
			PanelStyle.nameAndIcon(label, metric.label(), icon);
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
