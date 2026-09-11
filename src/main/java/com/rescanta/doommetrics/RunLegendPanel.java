package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import net.runelite.client.ui.FontManager;

/**
 * The chart's legend and its table of figures, which are the same thing.
 *
 * <p>Twelve lines need twelve names, and a legend drawn across the top of a plot has room for about
 * three of them. Down the side there is room for all twelve, for the group headings that say what
 * each is counted in, and for a figure beside each name - so the legend that says which line is
 * which is also the table that says what each line came to. That answers the objection to reading
 * a chart by hovering it: every figure on the plot is also written down.
 *
 * <p>The column reads the whole run by default, and the delve under the pointer while there is one.
 * That is the crosshair's other half - see {@link DelveChart#drawCrosshair} for why the per-delve
 * figures are not in a tooltip.
 *
 * <p>Pointing at a row brings its line forward on the plot and pushes the others back;
 * clicking one takes its line off. Neither ever changes another row's colour: a colour belongs to
 * a counter for as long as the window is open.
 *
 * <p>Swing thread only.
 */
class RunLegendPanel extends JPanel
{
	/** The swatch beside each name: the colour of that counter's line, at the weight it is drawn. */
	private static final int SWATCH = 9;

	private final Row[] rows = new Row[CombatMetric.values().length];
	private final JLabel heading = PanelStyle.caption("This run", SwingConstants.RIGHT);

	/** Counters the reader has clicked off. */
	private final Set<CombatMetric> hidden = EnumSet.noneOf(CombatMetric.class);

	private RunDetail detail = RunDetail.empty();

	/** The delve being read out, or 0 for the whole run. */
	private int delve;

	private Consumer<Set<CombatMetric>> onHiddenChanged = set ->
	{
	};

	private Consumer<CombatMetric> onEmphasis = metric ->
	{
	};

	RunLegendPanel()
	{
		super(new DynamicGridLayout(0, 1, 0, 1));
		setBackground(PanelStyle.BACKGROUND);
		build();
		setDetail(RunDetail.empty());
	}

	/** @param onHiddenChanged handed the counters switched off, whenever that set changes */
	void setToggleListener(Consumer<Set<CombatMetric>> onHiddenChanged)
	{
		this.onHiddenChanged = onHiddenChanged;
	}

	/** @param onEmphasis handed the counter being pointed at, or null when none is */
	void setEmphasisListener(Consumer<CombatMetric> onEmphasis)
	{
		this.onEmphasis = onEmphasis;
	}

	private void build()
	{
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(PanelStyle.BACKGROUND);
		top.setBorder(new EmptyBorder(0, 5, 3, 5));
		top.add(heading, BorderLayout.EAST);
		add(top);

		CombatMetric.Group group = null;
		int striped = 0;

		for (CombatMetric metric : CombatMetric.values())
		{
			if (metric.group() != group)
			{
				group = metric.group();
				add(groupHeading(group));
				striped = 0;
			}

			Row row = new Row(metric, striped++ % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE);
			rows[metric.ordinal()] = row;
			add(row);
		}
	}

	/**
	 * A group's name over the rows it covers, with the unit's colour as a stripe down the side -
	 * the same shape {@link CombatTablePanel} uses, and the thing that keeps the unit legible now
	 * that the row colours are spent on telling the lines apart.
	 */
	private static JPanel groupHeading(CombatMetric.Group group)
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
		CombatTotals totals = totals();
		long[] largest = new long[CombatMetric.Unit.values().length];

		for (CombatMetric metric : CombatMetric.values())
		{
			if (!hidden.contains(metric))
			{
				long amount = totals.get(metric);
				largest[metric.unit().ordinal()] =
					Math.max(largest[metric.unit().ordinal()], amount);
			}
		}

		for (CombatMetric metric : CombatMetric.values())
		{
			rows[metric.ordinal()].set(totals.get(metric), largest[metric.unit().ordinal()],
				hidden.contains(metric));
		}

		heading.setText(delve > 0 ? "Delve " + delve : "This run");
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

	private void toggle(CombatMetric metric)
	{
		if (!hidden.remove(metric))
		{
			hidden.add(metric);
		}

		refresh();
		onHiddenChanged.accept(EnumSet.copyOf(hidden));
	}

	/**
	 * One counter: its swatch, its name, its figure, and a meter behind them.
	 *
	 * <p>The figure is set in text ink rather than in the line's colour. The swatch carries the
	 * identity, and a column of twelve numbers each in a different colour is a column nothing can
	 * be read off.
	 */
	private final class Row extends JPanel
	{
		private final CombatMetric metric;
		private final Color stripe;
		private final JLabel name;
		private final JLabel value = PanelStyle.body("0", SwingConstants.RIGHT);

		/** Which specs feed a catch-all, as tooltip lines, or empty for a row its name explains. */
		private final String sources;

		private double fill;
		private boolean off;

		private Row(CombatMetric metric, Color stripe)
		{
			super(new BorderLayout(4, 0));
			this.metric = metric;
			this.stripe = stripe;
			this.name = PanelStyle.body(metric.label(), SwingConstants.LEFT);

			List<String> from = metric.sources();
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
					toggle(Row.this.metric);
				}

				@Override
				public void mouseEntered(MouseEvent event)
				{
					onEmphasis.accept(off ? null : Row.this.metric);
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

			String tooltip = off
				? "Click to put this line back on the chart"
				: DoomFormat.count(amount) + " " + metric.unit().description()
					+ " - click to take this line off the chart";

			setToolTipText(sources.isEmpty() ? tooltip : "<html>" + tooltip + sources + "</html>");

			fill = off || amount <= 0 || largest <= 0 ? 0 : (double) amount / largest;
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

			Color color = metric.seriesColor();
			g.setColor(PanelStyle.meterFill(color));
			g.fillRect(0, 0, (int) (getWidth() * PanelStyle.METER_WIDTH * fill), getHeight());
		}

		/**
		 * A filled square while the line is on the chart, a hollow one once it is off - and split
		 * down the middle for a dashed line, which is what tells it from the solid line sharing
		 * its colour.
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
				g.setColor(metric.seriesColor());

				if (off)
				{
					g.drawRect(0, y, SWATCH - 1, SWATCH - 1);
					return;
				}

				if (metric.seriesDashed())
				{
					int half = (SWATCH - 1) / 2;
					g.fillRect(0, y, half, SWATCH);
					g.fillRect(SWATCH - half, y, half, SWATCH);
					return;
				}

				g.fillRect(0, y, SWATCH, SWATCH);
			}
		}
	}
}
