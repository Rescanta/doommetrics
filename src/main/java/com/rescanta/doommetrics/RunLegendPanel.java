package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.EnumSet;
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
 * The chart's legend, which is also its table of figures: the whole run, or the hovered delve.
 * Clicking a row toggles its line; hovering brings it forward. Swing thread only.
 */
class RunLegendPanel extends JPanel
{
	/**
	 * The swatch beside each name: the colour of that counter's line, at the weight it is drawn.
	 */
	private static final int SWATCH = 9;

	/** How much of a switched-off counter's icon is drawn, as its name is drawn in grey. */
	private static final float OFF_ALPHA = 0.35f;

	/** Every row either reading mode can want, built once and only retexted. */
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

	/** The headings whose rows are folded out of the column - see {@link #setFolded}. */
	private final Set<CombatMetric.Group> folded = EnumSet.noneOf(CombatMetric.Group.class);

	private Consumer<Set<CombatMetric.Group>> onFoldChanged = groups ->
	{
	};

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
		super(new DynamicGridLayout(0, 1, 0, 0));
		setBackground(PanelStyle.CARD);
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
		top.setBackground(PanelStyle.CARD);
		top.setBorder(new EmptyBorder(0, 0, 0, 0));
		top.add(heading, BorderLayout.EAST);

		CombatMetric.Group group = null;

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			if (metric.group() != group)
			{
				CombatMetric.Group each = metric.group();
				GroupHeading heading = new GroupHeading(each);
				heading.foldable(() -> toggleFold(each));
				headings.put(each, heading);
				group = each;
			}

			rows.put(metric, new Row(metric, false));
		}

		for (CombatMetric.Group each : CombatMetric.Group.values())
		{
			// Grouped, a heading's row keeps the unit stripe.
			rows.put(each, new Row(each, true));
		}
	}

	/**
	 * Puts up the rows for the way the run is being read, which is all that changes between them.
	 */
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
					GroupHeading heading = headings.get(group);
					heading.setFolded(folded.contains(group));
					add(heading);
				}

				if (!folded.contains(group))
				{
					add(rows.get(metric));
				}
			}
		}

		revalidate();
		repaint();
	}

	void setIcons(Icons icons)
	{
		this.icons = icons;

		for (Row row : rows.values())
		{
			row.showName();
		}
	}

	/**
	 * @param hideEmpty whether a counter the run has not counted anything on starts switched off
	 */
	void setHideEmpty(boolean hideEmpty)
	{
		if (this.hideEmpty == hideEmpty)
		{
			return;
		}

		this.hideEmpty = hideEmpty;
		refresh();
	}

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

	/** Folds a heading's rows out of the column, or brings them back. */
	void toggleFold(CombatMetric.Group group)
	{
		if (!folded.remove(group))
		{
			folded.add(group);
		}

		layOut();
		onFoldChanged.accept(EnumSet.copyOf(folded));
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

	/** Redraws every figure. Meters are scaled per unit, not across the table. */
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

		RunDetail.Delve at = delve > 0 ? detail.at(delve) : null;
		heading.setText(delve <= 0 ? "This run"
			: at != null && !at.watched ? "Delve " + delve + " - not watched" : "Delve " + delve);
	}

	/**
	 * Lines are off when clicked off, or when the run counted nothing on them and they weren't
	 * clicked back on. Tells the chart when that changes.
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

		// The clicked row is the hovered one, so its emphasis follows the toggle.
		onEmphasis.accept(hidden.contains(line) ? null : line);
	}

	/** One counter: swatch, name, figure in text ink, and a slim meter under them. */
	private final class Row extends JPanel
	{
		/** How wide the unit's stripe down a grouped row is - the width a heading gave it. */
		private static final int UNIT_STRIPE = 3;

		private final CombatSeries series;
		private final JLabel name;
		private final JLabel value = PanelStyle.body("0", SwingConstants.RIGHT);
		private final Meter meter;

		/** Whether the unit's colour is drawn down the side, as a heading draws it. */
		private final boolean unitStripe;

		/** Which specs feed a catch-all, as tooltip lines, or empty for a row its name explains. */
		private final String sources;

		private boolean off;

		/** Whether the pointer is over the row, which lifts it as the line comes forward. */
		private boolean pointed;

		/** What the name was last drawn as, so a hover that changes neither redraws nothing. */
		private BufferedImage shownIcon;
		private boolean shownOff;

		private Row(CombatSeries series, boolean unitStripe)
		{
			super(new BorderLayout(4, 0));
			this.series = series;
			this.unitStripe = unitStripe;
			this.name = PanelStyle.body(series.label(), SwingConstants.LEFT);
			this.meter = new Meter(series.seriesColor(), PanelStyle.METER_HEIGHT - 1);

			List<String> from = series.sources();
			this.sources = from.isEmpty() ? "" : "<br><br>Counted from:<br>" + String.join("<br>", from);

			name.setBorder(new EmptyBorder(3, 2, 2, 0));
			value.setBorder(new EmptyBorder(3, 4, 2, 5));
			meter.setTrack(null);

			JPanel bar = new JPanel(new BorderLayout());
			bar.setOpaque(false);
			bar.setBorder(new EmptyBorder(0, SWATCH + 4, 3, 5));
			bar.add(meter, BorderLayout.CENTER);

			setOpaque(false);
			setBorder(new EmptyBorder(0, unitStripe ? 9 : 5, 0, 0));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Click to take this line off the chart");
			add(swatch(), BorderLayout.WEST);
			add(name, BorderLayout.CENTER);
			add(value, BorderLayout.EAST);
			add(bar, BorderLayout.SOUTH);

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
					pointed = true;
					repaint();
					onEmphasis.accept(off ? null : Row.this.series);
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					pointed = false;
					repaint();
					onEmphasis.accept(null);
				}
			});
		}

		/** The line's colour, drawn as a dot. */
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

			meter.setFill(off || amount <= 0 || largest <= 0 ? 0 : (double) amount / largest);
			showName();
			repaint();
		}

		/**
		 * The name and the picture beside it, the picture faded while the line is off as the words
		 * are.
		 */
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
			Graphics2D graphics = (Graphics2D) g.create();
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(pointed ? PanelStyle.TILE : PanelStyle.CARD);
			graphics.fillRoundRect(0, 0, getWidth(), getHeight(), PanelStyle.ARC, PanelStyle.ARC);

			if (unitStripe)
			{
				graphics.setColor(series.unit().color());
				graphics.fillRoundRect(0, 2, UNIT_STRIPE, getHeight() - 4, UNIT_STRIPE, UNIT_STRIPE);
			}

			graphics.dispose();
		}

		/** Filled while the line is on the chart, hollow once it is off. */
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
				Graphics2D graphics = (Graphics2D) g.create();
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				int y = (getHeight() - SWATCH) / 2;
				graphics.setColor(series.seriesColor());

				if (off)
				{
					graphics.drawOval(0, y, SWATCH - 1, SWATCH - 1);
				}
				else
				{
					graphics.fillOval(0, y, SWATCH, SWATCH);
				}

				graphics.dispose();
			}
		}
	}
}
