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
 * A {@link CombatTotals} as a table: headings led by their skill icon, and a row per counter with a
 * meter behind it scaled per unit. Rows are built once; with counters at 0 hidden, the table is
 * laid out again only when a counter crosses from 0, and a link under it shows every row. Swing
 * thread only.
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

	/** Whether counters still at 0 are left out - the overlay's Hide counters at 0. */
	private boolean hideEmpty;

	/** Whether the reader has asked for every row despite {@link #hideEmpty}. */
	private boolean showingAll;

	/** The counters at 0 in the tally on show, which are the rows {@link #hideEmpty} leaves out. */
	private final Set<CombatMetric> empty = EnumSet.noneOf(CombatMetric.class);

	private final JLabel showAll = PanelStyle.link(() -> setShowingAll(!showingAll));

	CombatTablePanel()
	{
		super(new DynamicGridLayout(0, 1, 0, 1));
		setBackground(PanelStyle.CARD);
		build();
		layOut();
		setTotals(null);
	}

	private void build()
	{
		CombatMetric.Group heading = null;

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			if (metric.group() != heading)
			{
				CombatMetric.Group group = metric.group();
				GroupHeading row = new GroupHeading(group);
				row.foldable(() -> toggle(group));
				// The card's tiles carry the totals, so the heading is a name and a fold.
				row.hideTotal();
				headings[group.ordinal()] = row;
				heading = group;
			}

			rows[metric.ordinal()] = new MeterRow(metric);
		}
	}

	/**
	 * Puts up every heading and the rows under unfolded ones. Removed rather than hidden, since the
	 * grid leaves a gap for a hidden component.
	 */
	private void layOut()
	{
		removeAll();
		int hidden = 0;

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			GroupHeading heading = headings[group.ordinal()];
			heading.setFolded(folded.contains(group));
			add(heading);

			if (folded.contains(group))
			{
				continue;
			}

			// Restarted under each heading so the stripes read as a block per group rather than
			// as one run of alternating rows the headings happen to interrupt.
			int striped = 0;

			for (CombatMetric metric : group.metrics())
			{
				if (!metric.displayed())
				{
					continue;
				}

				if (hideEmpty && empty.contains(metric))
				{
					hidden++;

					if (!showingAll)
					{
						continue;
					}
				}

				MeterRow row = rows[metric.ordinal()];
				row.setStripe(striped++ % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE);
				add(row);
			}
		}

		if (hidden > 0)
		{
			showAll.setText(showingAll
				? "Hide counters at 0"
				: "Show all (" + hidden + " at 0)");
			add(showAll);
		}

		revalidate();
		repaint();
	}

	/**
	 * @param hideEmpty whether counters still at 0 are left out, as the overlay leaves them; the
	 *                  link under the table brings them back
	 */
	void setHideEmpty(boolean hideEmpty)
	{
		if (this.hideEmpty != hideEmpty)
		{
			this.hideEmpty = hideEmpty;
			layOut();
		}
	}

	/** Every row despite {@link #hideEmpty}, or back to leaving the ones at 0 out. */
	void setShowingAll(boolean showingAll)
	{
		this.showingAll = showingAll;
		layOut();
	}

	/** The headings to fold, as last left. Tells no listener. */
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

	void setIcons(Icons icons)
	{
		this.icons = icons;

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			rows[metric.ordinal()].showName(icons);
		}

		for (GroupHeading heading : headings)
		{
			heading.setIcons(icons);
		}
	}

	/** Repaints every figure; a null tally reads as zeroes. Meters are scaled per unit. */
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

		Set<CombatMetric> nowEmpty = EnumSet.noneOf(CombatMetric.class);

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			long amount = counted.get(metric);
			rows[metric.ordinal()].set(amount, largest[metric.unit().ordinal()]);

			if (amount <= 0)
			{
				nowEmpty.add(metric);
			}
		}

		// Only a counter crossing from 0 - or the tab switching tallies - changes which rows are up.
		if (!nowEmpty.equals(empty))
		{
			empty.clear();
			empty.addAll(nowEmpty);

			if (hideEmpty)
			{
				layOut();
			}
		}

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			headings[group.ordinal()].set(counted);
		}
	}

	/** One metric: name, figure, and a painted meter behind both. */
	private static final class MeterRow extends JPanel
	{
		private final CombatMetric metric;
		private Color stripe = PanelStyle.CARD;
		private final JLabel value = PanelStyle.body("0", SwingConstants.RIGHT);
		private final JLabel label;

		/** How much of the row the meter fills, from nothing to {@link PanelStyle#METER_WIDTH}. */
		private double fill;

		/** The icon the name was last drawn with, so only rows still waiting redraw. */
		private BufferedImage shownIcon;

		private MeterRow(CombatMetric metric)
		{
			super(new BorderLayout());
			this.metric = metric;

			label = PanelStyle.body(metric.label(), SwingConstants.LEFT);
			label.setBorder(PanelStyle.CELL_PADDING);
			value.setBorder(PanelStyle.CELL_PADDING);

			// A narrow row squeezes the name rather than clipping the figure.
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

		/** Its place in the alternation, which moves as the rows around it are hidden. */
		private void setStripe(Color stripe)
		{
			this.stripe = stripe;
			setBackground(stripe);
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
		 * @param largest the most counted in the same unit, which fills the meter
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
