package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;

/**
 * The chart's legend and its table of figures, which are the same thing.
 *
 * <p>Eight lines need eight names, and a legend drawn across the top of a plot has room for about
 * three of them. Down the side there is room for all eight, for the group headings that say what
 * each is counted in, and for a figure beside each name - so the legend that says which line is
 * which is also the table that says what each line came to. That answers the objection to reading
 * a chart by hovering it: every figure on the plot is also written down.
 *
 * <p>The column reads the whole run by default, and the delve under the pointer while there is one.
 * That is the crosshair's other half - see {@link DelveChart#drawCrosshair} for why the per-delve
 * figures are not in a tooltip.
 *
 * <p>Grouped, the same legend is five rows and the chart five lines: a heading's row is its line,
 * and the counters under it are added up rather than listed - see {@link #setGrouped}. Which rows
 * the reader has clicked off is remembered separately for each way of reading, so switching back
 * finds the chart as it was left.
 *
 * <p>Pointing at a row brings its line forward on the plot and pushes the others back;
 * clicking one takes its line off. Neither ever changes another row's colour: a colour belongs to
 * a counter for as long as the window is open.
 *
 * <p>A counter the run has not counted anything on starts switched off, so a run that used three of
 * the eight draws three lines rather than five flat ones along the bottom. Its row stays, and a
 * click puts its line on like any other. Whether a counter is empty goes by the whole run, never
 * by the delve being pointed at, so lines do not come and go as the pointer moves.
 *
 * <p>Swing thread only.
 */
class RunLegendPanel extends JPanel
{
	/** The swatch beside each name: the colour of that counter's line, at the weight it is drawn. */
	private static final int SWATCH = 9;

	/** How much of a switched-off counter's icon is drawn, as its name is drawn in grey. */
	private static final float OFF_ALPHA = 0.35f;

	/**
	 * Every row either way of reading can want, built once at the start and only ever retexted -
	 * the rows of the way not being read are simply not laid out.
	 */
	private final Map<CombatSeries, Row> rows = new HashMap<>();

	/** The headings over the counters, on show only while the counters are listed separately. */
	private final Map<CombatMetric.Group, GroupHeading> headings =
		new EnumMap<>(CombatMetric.Group.class);

	private final JLabel heading = PanelStyle.caption("This run", SwingConstants.RIGHT);
	private final JPanel top = new JPanel(new BorderLayout());

	/** Counters the reader has clicked off. */
	private final Set<CombatSeries> clickedOff = new HashSet<>();

	/** Counters the reader has clicked on while they were still at 0, overriding the default. */
	private final Set<CombatSeries> clickedOn = new HashSet<>();

	/** Counters whose lines are off the chart, clicked off or empty, as the chart was last told. */
	private Set<CombatSeries> hidden = new HashSet<>();

	/** Whether a counter the run has not counted anything on starts switched off. */
	private boolean hideEmpty = true;

	/** Whether the counters are folded into their headings - see {@link #setGrouped}. */
	private boolean grouped;

	private RunDetail detail = RunDetail.empty();

	/** The pictures drawn beside the rows' names, or none while they are still on their way. */
	private Icons icons = Icons.NONE;

	/** The delve being read out, or 0 for the whole run. */
	private int delve;

	private Consumer<Set<CombatSeries>> onHiddenChanged = set ->
	{
	};

	private Consumer<CombatSeries> onEmphasis = metric ->
	{
	};

	RunLegendPanel()
	{
		super(new DynamicGridLayout(0, 1, 0, 1));
		setBackground(PanelStyle.BACKGROUND);
		build();
		layOut();
		setDetail(RunDetail.empty());
	}

	/** @param onHiddenChanged handed the counters switched off, whenever that set changes */
	void setToggleListener(Consumer<Set<CombatSeries>> onHiddenChanged)
	{
		this.onHiddenChanged = onHiddenChanged;
		onHiddenChanged.accept(new HashSet<>(hidden));
	}

	/** @param onEmphasis handed the counter being pointed at, or null when none is */
	void setEmphasisListener(Consumer<CombatSeries> onEmphasis)
	{
		this.onEmphasis = onEmphasis;
	}

	/** Builds every row and heading, both ways of reading the run, once. */
	private void build()
	{
		top.setBackground(PanelStyle.BACKGROUND);
		top.setBorder(new EmptyBorder(0, 5, 3, 5));
		top.add(heading, BorderLayout.EAST);

		CombatMetric.Group group = null;
		int striped = 0;

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			if (metric.group() != group)
			{
				group = metric.group();
				headings.put(group, new GroupHeading(group));
				striped = 0;
			}

			rows.put(metric, new Row(metric,
				striped++ % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE, false));
		}

		striped = 0;

		for (CombatMetric.Group each : CombatMetric.Group.values())
		{
			// A heading's own row carries the unit stripe the heading was carrying, in the place
			// the heading had it: grouped, the row is the heading, and what it is counted in is
			// the one thing its colour no longer says.
			rows.put(each, new Row(each,
				striped++ % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE, true));
		}
	}

	/** Puts up the rows for the way the run is being read, which is all that changes between them. */
	private void layOut()
	{
		removeAll();
		add(top);

		if (grouped)
		{
			for (CombatMetric.Group group : CombatMetric.Group.values())
			{
				add(rows.get(group));
			}
		}
		else
		{
			CombatMetric.Group group = null;

			for (CombatMetric metric : CombatMetric.DISPLAYED)
			{
				if (metric.group() != group)
				{
					group = metric.group();
					add(headings.get(group));
				}

				add(rows.get(metric));
			}
		}

		revalidate();
		repaint();
	}

	/**
	 * @param icons the pictures to draw beside the counters' names. Handed over again as they
	 *              arrive from the game, and a row stands on its name alone until its picture has.
	 */
	void setIcons(Icons icons)
	{
		this.icons = icons;

		for (Row row : rows.values())
		{
			row.showName();
		}
	}

	/** @param hideEmpty whether a counter the run has not counted anything on starts switched off */
	void setHideEmpty(boolean hideEmpty)
	{
		if (this.hideEmpty == hideEmpty)
		{
			return;
		}

		this.hideEmpty = hideEmpty;
		refresh();
	}

	/**
	 * @param grouped whether the counters are folded into their headings: five rows and five
	 *                lines rather than eight of each
	 *
	 * <p>What a grouped row adds up is everything under its heading, the counters with no row of
	 * their own included - so a punish thrown with a weapon the plugin does not name is in the
	 * punish figure here and nowhere else.
	 */
	void setGrouped(boolean grouped)
	{
		if (this.grouped == grouped)
		{
			return;
		}

		this.grouped = grouped;
		layOut();
		refresh();
	}

	void setDetail(RunDetail detail)
	{
		this.detail = detail;
		this.delve = 0;
		refresh();
	}

	/** @param delve the delve to read out, or 0 for the run as a whole */
	void setDelve(int delve)
	{
		if (this.delve == delve)
		{
			return;
		}

		this.delve = delve;
		refresh();
	}

	/** The lines on show: one per counter, or one per heading once they are grouped. */
	private List<CombatSeries> series()
	{
		return CombatSeries.drawn(grouped);
	}

	/**
	 * Redraws every figure.
	 *
	 * <p>The meters are scaled per unit, not across the table: damage on a delve runs several times
	 * the prayer restored on it, and on one scale the prayer row would be a stub whatever it did.
	 * Per unit, the bar answers the question the reader has - which source is carrying this - and
	 * never invites a comparison across colours the numbers do not support.
	 */
	private void refresh()
	{
		updateHidden();
		CombatTotals totals = totals();
		long[] largest = new long[CombatMetric.Unit.values().length];

		for (CombatSeries line : series())
		{
			if (!hidden.contains(line))
			{
				long amount = line.amount(totals);
				largest[line.unit().ordinal()] = Math.max(largest[line.unit().ordinal()], amount);
			}
		}

		for (CombatSeries line : series())
		{
			rows.get(line).set(line.amount(totals), largest[line.unit().ordinal()],
				hidden.contains(line));
		}

		if (!grouped)
		{
			// The headings total the whole group, the counters with no row included, so a heading
			// and the rows under it need not add up - see GroupHeading.
			for (GroupHeading each : headings.values())
			{
				each.set(totals);
			}
		}

		heading.setText(delve > 0 ? "Delve " + delve : "This run");
	}

	/**
	 * Works out which lines are off - the ones clicked off, and the ones the run has counted
	 * nothing on unless they were clicked back on - and tells the chart when that has changed.
	 *
	 * <p>A counter that was only off for being empty comes on by itself once it counts something,
	 * so a live run's lines appear as they start to matter.
	 */
	private void updateHidden()
	{
		CombatTotals run = detail.totals();
		Set<CombatSeries> off = new HashSet<>();

		for (CombatSeries line : series())
		{
			if (clickedOff.contains(line)
				|| (hideEmpty && line.amount(run) <= 0 && !clickedOn.contains(line)))
			{
				off.add(line);
			}
		}

		if (!off.equals(hidden))
		{
			hidden = off;
			onHiddenChanged.accept(new HashSet<>(off));
		}
	}

	/** What the column is reading: one delve's figures, or the run's. */
	private CombatTotals totals()
	{
		if (delve <= 0)
		{
			return detail.totals();
		}

		RunDetail.Delve at = detail.at(delve);
		return at == null ? new CombatTotals() : at.combat;
	}

	void toggle(CombatSeries line)
	{
		if (hidden.contains(line))
		{
			clickedOff.remove(line);
			clickedOn.add(line);
		}
		else
		{
			clickedOn.remove(line);
			clickedOff.add(line);
		}

		refresh();

		// A click lands on the row under the pointer, whose line is the one brought forward. Taken
		// off, it would leave every other line pushed back behind a line no longer drawn; put back
		// on, it comes forward as it would have had the pointer just arrived.
		onEmphasis.accept(hidden.contains(line) ? null : line);
	}

	/**
	 * One counter: its swatch, its name, its figure, and a meter behind them.
	 *
	 * <p>The figure is set in text ink rather than in the line's colour. The swatch carries the
	 * identity, and a column of eight numbers each in a different colour is a column nothing can
	 * be read off.
	 */
	private final class Row extends JPanel
	{
		/** How wide the unit's stripe down a grouped row is - the width a heading gave it. */
		private static final int UNIT_STRIPE = 3;

		private final CombatSeries series;
		private final Color stripe;
		private final JLabel name;
		private final JLabel value = PanelStyle.body("0", SwingConstants.RIGHT);

		/** Whether the unit's colour is drawn down the side, as a heading draws it. */
		private final boolean unitStripe;

		/** Which specs feed a catch-all, as tooltip lines, or empty for a row its name explains. */
		private final String sources;

		private double fill;
		private boolean off;

		/** What the name was last drawn as, so a hover that changes neither redraws nothing. */
		private BufferedImage shownIcon;
		private boolean shownOff;

		private Row(CombatSeries series, Color stripe, boolean unitStripe)
		{
			super(new BorderLayout(4, 0));
			this.series = series;
			this.stripe = stripe;
			this.unitStripe = unitStripe;
			this.name = PanelStyle.body(series.label(), SwingConstants.LEFT);

			List<String> from = series.sources();
			this.sources = from.isEmpty() ? "" : "<br><br>Counted from:<br>" + String.join("<br>", from);

			name.setBorder(new EmptyBorder(3, 3, 3, 0));
			value.setBorder(PanelStyle.CELL_PADDING);

			setBackground(stripe);
			setBorder(new EmptyBorder(0, 5, 0, 0));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Click to take this line off the chart");
			add(swatch(), BorderLayout.WEST);
			add(name, BorderLayout.CENTER);
			add(value, BorderLayout.EAST);

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					toggle(Row.this.series);
				}

				@Override
				public void mouseEntered(MouseEvent event)
				{
					onEmphasis.accept(off ? null : Row.this.series);
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					onEmphasis.accept(null);
				}
			});
		}

		/** The line's colour, drawn as the segment of line it stands for. */
		private Swatch swatch()
		{
			return new Swatch();
		}

		private void set(long amount, long largest, boolean off)
		{
			this.off = off;

			value.setText(DoomFormat.count(amount));
			value.setForeground(amount > 0 && !off
				? ColorScheme.TEXT_COLOR
				: ColorScheme.LIGHT_GRAY_COLOR);
			name.setForeground(off ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.TEXT_COLOR);

			// Led by the name, which is the only place it is written when an icon stands in for it.
			String tooltip = off
				? "Click to put this line back on the chart"
				: DoomFormat.count(amount) + " " + series.unit().description()
					+ " - click to take this line off the chart";

			setToolTipText("<html>" + series.label() + "<br>" + tooltip + sources + "</html>");

			fill = off || amount <= 0 || largest <= 0 ? 0 : (double) amount / largest;
			showName();
			repaint();
		}

		/** The name and the picture beside it, the picture faded while the line is off as the words are. */
		private void showName()
		{
			BufferedImage icon = series.icon(icons);

			if (icon == shownIcon && off == shownOff)
			{
				return;
			}

			shownIcon = icon;
			shownOff = off;
			PanelStyle.nameAndIcon(name, series.label(),
				icon == null || !off ? icon : IconArt.fade(icon, OFF_ALPHA));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(stripe);
			g.fillRect(0, 0, getWidth(), getHeight());

			if (fill > 0)
			{
				Color color = series.seriesColor();
				g.setColor(PanelStyle.meterFill(color));
				g.fillRect(0, 0, (int) (getWidth() * PanelStyle.METER_WIDTH * fill), getHeight());
			}

			if (unitStripe)
			{
				// Over the meter rather than under it: the meter runs the width of the row, and a
				// stripe the fill washes over says nothing.
				g.setColor(series.unit().color());
				g.fillRect(0, 0, UNIT_STRIPE, getHeight());
			}
		}

		/**
		 * A filled square while the line is on the chart, and a hollow one once it is off.
		 */
		private final class Swatch extends JPanel
		{
			private Swatch()
			{
				setOpaque(false);
				setPreferredSize(new Dimension(SWATCH, SWATCH));
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				int y = (getHeight() - SWATCH) / 2;
				g.setColor(series.seriesColor());

				if (off)
				{
					g.drawRect(0, y, SWATCH - 1, SWATCH - 1);
					return;
				}

				g.fillRect(0, y, SWATCH, SWATCH);
			}
		}
	}
}
