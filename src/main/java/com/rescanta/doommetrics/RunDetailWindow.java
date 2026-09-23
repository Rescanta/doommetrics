package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * One run, delve by delve, as a dashboard: a strip of tiles for the run's figures, the chart as the
 * main card, and its legend and the drops beside it. Shows the live run or the last one, ignoring
 * the overlay's linger and Clear. Swing thread only; the plugin owns the one instance.
 */
class RunDetailWindow extends JFrame
{
	/** Wide enough for the sidebar's longest name and its figure, and no wider. */
	private static final int SIDEBAR_WIDTH = 224;

	/** How far a notch of the wheel scrolls the sidebar. */
	private static final int SCROLL_UNIT = 16;

	/** Between the window's cards. */
	private static final int GAP = PanelStyle.GRID * 3;

	private final DelveChart chart = new DelveChart();
	private final RunLegendPanel legend = new RunLegendPanel();
	private final RunDropsPanel drops = new RunDropsPanel();

	/** Which way the counters are read, one segment each - see {@link #groupingControl()}. */
	private final SegmentedControl grouping = groupingControl();

	/** The drops, on a card of their own - only on show for a run that has any. */
	private final JPanel dropsSection = PanelStyle.section("Drops", drops);

	/** The tiles across the top, rebuilt only when which of them are up changes. */
	private final JPanel strip = new JPanel(new GridLayout(1, 0, PanelStyle.GRID * 2, 0));

	/** The live rows, a tile each - see {@link DoomMetricsPanel.Live}. */
	private final JLabel[] rowCaptions = new JLabel[DoomMetricsPanel.Live.ROWS];
	private final JComponent[] rowValues = new JComponent[DoomMetricsPanel.Live.ROWS];
	private final JPanel[] rowTiles = new JPanel[DoomMetricsPanel.Live.ROWS];
	private final Meter targetMeter = new Meter(PanelStyle.ACCENT, PanelStyle.METER_HEIGHT);

	/** Each heading's total for the run, after the run's own figures. */
	private final JLabel[] unitTotals = new JLabel[CombatMetric.Group.values().length];
	private final JPanel[] unitTiles = new JPanel[CombatMetric.Group.values().length];

	/** Which of {@link #rowTiles} are up, a bit per index. Starts as none of the possible sets. */
	private int shownRows = Integer.MIN_VALUE;

	/**
	 * @param icon    the plugin's icon, for the taskbar
	 * @param onClose run when the user closes the window
	 */
	RunDetailWindow(BufferedImage icon, Runnable onClose)
	{
		super("Doom Metrics - Run detail");

		if (icon != null)
		{
			setIconImage(icon);
		}

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent event)
			{
				onClose.run();
			}
		});

		// Hovering a delve moves the legend and drops list onto it; hovering a name brings a line
		// forward.
		chart.setHoverListener(level ->
		{
			legend.setDelve(level);
			drops.setDelve(level);
		});
		legend.setToggleListener(chart::setHidden);
		legend.setEmphasisListener(chart::setEmphasis);

		buildStrip();
		dropsSection.setVisible(false);

		JPanel main = new JPanel(new BorderLayout(GAP, 0));
		main.setOpaque(false);
		main.add(PanelStyle.section("Per delve", chart), BorderLayout.CENTER);
		main.add(sidebar(), BorderLayout.EAST);

		JPanel content = new JPanel(new BorderLayout(0, GAP));
		content.setBackground(PanelStyle.BACKGROUND);
		content.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
		content.add(strip, BorderLayout.NORTH);
		content.add(main, BorderLayout.CENTER);

		setContentPane(content);
		setMinimumSize(new Dimension(760, 460));
		pack();
	}

	/** Shows the window, or brings it forward if it is already up behind something. */
	void open(Component anchor)
	{
		if (!isVisible())
		{
			setLocationRelativeTo(anchor);
			setVisible(true);
		}

		// An already-visible window can still be buried, and clicking the button again should
		// mean "show me this" either way.
		setState(NORMAL);
		toFront();
		requestFocus();
	}

	void setDetail(RunDetail detail)
	{
		chart.setDetail(detail);
		legend.setDetail(detail);
		drops.setDetail(detail);
		dropsSection.setVisible(!detail.drops().isEmpty());

		CombatTotals totals = detail.totals();

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			long amount = group.amount(totals);
			JLabel total = unitTotals[group.ordinal()];
			total.setText(PanelStyle.tileFigure(amount));
			total.setForeground(amount > 0 ? group.unit().color() : ColorScheme.LIGHT_GRAY_COLOR);
			unitTiles[group.ordinal()].setToolTipText(group.tooltip(totals));
		}
	}

	/**
	 * @param onFoldChanged handed the folded headings whenever a click changes them, on the Swing
	 * thread
	 */
	void setFolding(Set<CombatMetric.Group> folded, Consumer<Set<CombatMetric.Group>> onFoldChanged)
	{
		legend.setFolded(folded);
		legend.setFoldListener(onFoldChanged);
	}

	/** @param hideEmpty whether a counter the run has not counted anything on is left out */
	void setHideEmpty(boolean hideEmpty)
	{
		legend.setHideEmpty(hideEmpty);
	}

	void setIcons(Icons icons)
	{
		chart.setItemIcons(icons::item);
		legend.setIcons(icons);
		drops.setIcons(icons);
	}

	/** @param live the side panel's live rows, or null when there is no run */
	void setLive(DoomMetricsPanel.Live live)
	{
		// The two heroes are always up, so the strip holds its shape between runs.
		int shown = (1 << DoomMetricsPanel.Live.HERO_ROWS) - 1;

		if (live == null)
		{
			for (int i = 0; i < DoomMetricsPanel.Live.HERO_ROWS; i++)
			{
				setValue(i, "-");
				rowValues[i].setForeground(DoomColors.DIMMED);
			}

			rowCaptions[0].setText("Delve");
			rowCaptions[1].setText("Time");
		}
		else
		{
			for (int i = 0; i < DoomMetricsPanel.Live.ROWS; i++)
			{
				if (live.labels[i] == null)
				{
					continue;
				}

				rowCaptions[i].setText(live.labels[i]);
				setValue(i, i == DoomMetricsPanel.Live.TARGET_ROW && live.progress() < 1
					? live.cleared + " / " + live.values[i]
					: live.values[i]);
				rowValues[i].setForeground("-".equals(live.values[i])
					? DoomColors.DIMMED
					: DoomColors.PLAIN);
				shown |= 1 << i;
			}

			rowValues[0].setForeground(live.died
				? ColorScheme.PROGRESS_ERROR_COLOR
				: DoomColors.PLAIN);
			rowValues[1].setForeground(live.finished ? DoomColors.DIMMED : DoomColors.PLAIN);

			targetMeter.setFill(live.progress());
			targetMeter.setColor(live.progress() >= 1
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: PanelStyle.ACCENT);
		}

		showTiles(shown);
	}

	private void setValue(int row, String text)
	{
		JComponent value = rowValues[row];

		if (value instanceof PanelStyle.Figure)
		{
			((PanelStyle.Figure) value).setText(text);
		}
		else
		{
			((JLabel) value).setText(text);
		}
	}

	/**
	 * Puts up the tiles a snapshot has; a no-op when they're already up, since the clock ticks
	 * every second.
	 */
	private void showTiles(int shown)
	{
		if (shown == shownRows)
		{
			return;
		}

		shownRows = shown;
		strip.removeAll();

		for (int i = 0; i < DoomMetricsPanel.Live.ROWS; i++)
		{
			if ((shown & (1 << i)) != 0)
			{
				strip.add(rowTiles[i]);
			}
		}

		for (JPanel tile : unitTiles)
		{
			strip.add(tile);
		}

		strip.revalidate();
		strip.repaint();
	}

	/** A tile per live row and per heading; the delve and the clock drawn as heroes. */
	private void buildStrip()
	{
		strip.setOpaque(false);

		for (int i = 0; i < DoomMetricsPanel.Live.ROWS; i++)
		{
			rowCaptions[i] = PanelStyle.caption("", SwingConstants.LEFT);

			if (i < DoomMetricsPanel.Live.HERO_ROWS)
			{
				rowValues[i] = PanelStyle.hero("-", SwingConstants.LEFT);
			}
			else
			{
				rowValues[i] = i == DoomMetricsPanel.Live.TARGET_ROW
					? PanelStyle.hero("", SwingConstants.LEFT)
					: PanelStyle.heroFigure(SwingConstants.LEFT);
			}

			JComponent value = rowValues[i];

			if (i == DoomMetricsPanel.Live.TARGET_ROW)
			{
				JPanel withMeter = new JPanel(new BorderLayout(0, PanelStyle.GRID));
				withMeter.setOpaque(false);
				withMeter.add(value, BorderLayout.CENTER);
				withMeter.add(targetMeter, BorderLayout.SOUTH);
				value = withMeter;
			}

			rowTiles[i] = heroTile(rowCaptions[i], value);
		}

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			JLabel value = PanelStyle.hero("0", SwingConstants.LEFT);
			unitTotals[group.ordinal()] = value;
			unitTiles[group.ordinal()] = heroTile(
				PanelStyle.caption(group.overlayHeading(), SwingConstants.LEFT), value);
		}

		setLive(null);
	}

	/** A strip tile: a card-coloured cell with more room than the side panel's. */
	private static JPanel heroTile(JLabel caption, JComponent value)
	{
		RoundedPanel tile = new RoundedPanel(PanelStyle.CARD, PanelStyle.ARC);
		tile.setLayout(new BorderLayout(0, 2));
		tile.setBorder(new EmptyBorder(PanelStyle.GRID * 2, PanelStyle.GRID * 3,
			PanelStyle.GRID * 2, PanelStyle.GRID * 3));
		tile.add(caption, BorderLayout.NORTH);
		tile.add(value, BorderLayout.SOUTH);
		return tile;
	}

	/** Separate / Grouped segments on the Counters card. */
	private SegmentedControl groupingControl()
	{
		return new SegmentedControl(new String[]{"Sources", "Grouped"},
			new String[]{
				"A line for each counter, named by what it counts",
				"<html>A line for each heading, summing the counters under it."
					+ "<br>Counts the sources with no line of their own too - a punish thrown"
					+ "<br>with a weapon this plugin does not name is in the figure here.</html>",
			},
			index ->
			{
				boolean grouped = index == 1;
				// The chart first: the legend answers by handing over the lines that are off, and
				// which lines those are depends on which of them the chart is drawing.
				chart.setGrouped(grouped);
				legend.setGrouped(grouped);
			});
	}

	/** For the preview harness. Idempotent. */
	void showGrouped(boolean grouped)
	{
		grouping.select(grouped ? 1 : 0);
	}

	/** The legend and the drops, scrolled together. */
	private JScrollPane sidebar()
	{
		// A hidden card takes its gap with it, so a run without drops lays out as it always did.
		JPanel stack = new JPanel(new BorderLayout(0, GAP));
		stack.setBackground(PanelStyle.BACKGROUND);
		stack.add(PanelStyle.section("Counters", grouping, legend), BorderLayout.NORTH);
		stack.add(dropsSection, BorderLayout.CENTER);

		// Wrapped so rows aren't stretched to the viewport height, and held to the sidebar width so
		// a wide row squeezes its name rather than having its digits clipped.
		JPanel top = new ColumnWidth();
		top.setBackground(PanelStyle.BACKGROUND);
		top.add(stack, BorderLayout.NORTH);

		JScrollPane scroller = new JScrollPane(top,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(PanelStyle.BACKGROUND);
		scroller.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT);

		// A permanent lane for the scrollbar, so it appearing never relays the rows sideways.
		int lane = scroller.getVerticalScrollBar().getPreferredSize().width;
		scroller.setPreferredSize(new Dimension(SIDEBAR_WIDTH + lane, 0));
		return scroller;
	}

	/** Lays the sidebar out at its own width whatever the viewport does. */
	private static final class ColumnWidth extends JPanel implements Scrollable
	{
		private ColumnWidth()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(SIDEBAR_WIDTH, super.getPreferredSize().height);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return SCROLL_UNIT;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return visible.height;
		}

		/** Never, so the scrollbar lane is left empty rather than handed to the rows. */
		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return false;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
