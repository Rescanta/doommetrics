package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
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
 * A window of its own, outside the side panel and outside the client: one run, taken apart delve
 * by delve.
 *
 * <p>It shows the run in progress, and goes on showing it once it ends - the last run you made is
 * the one you want to read afterwards, and unlike the overlay this window is only on screen
 * because you opened it, so it has no reason to time itself out. Clearing the overlay leaves it be
 * for the same reason: Clear takes a finished run off the game screen, and this is not on it.
 *
 * <p>Nothing here is read back from disk, so a window opened before this session's first run is
 * empty and says so. What survives a client restart is the milestone table and the lifetime totals,
 * and both of those are in the side panel where they always were.
 *
 * <p>The chart wants far more width than a side panel has, and its legend wants a column beside it,
 * so all of it lives here rather than being cramped to fit next to the client.
 *
 * <p>Swing thread only. The plugin owns the single instance and disposes it on shutdown; closing
 * the window disposes it and tells the plugin to forget it, so the next open builds a fresh one
 * rather than resurrecting a disposed frame.
 */
class RunDetailWindow extends JFrame
{
	/** Wide enough for the sidebar's longest name and its figure, and no wider. */
	private static final int SIDEBAR_WIDTH = 224;

	/** How far a notch of the wheel scrolls the sidebar. */
	private static final int SCROLL_UNIT = 16;

	private final DelveChart chart = new DelveChart();
	private final RunLegendPanel legend = new RunLegendPanel();
	private final RunDropsPanel drops = new RunDropsPanel();

	/** The drops, under their heading - only on show for a run that has any. */
	private final JPanel dropsSection = PanelStyle.section("Drops", drops);
	private final JPanel summary = PanelStyle.column(3);

	/** The two figures at the head of the sidebar - see {@link DoomMetricsPanel.Live}. */
	private final JLabel[] heroCaptions = new JLabel[DoomMetricsPanel.Live.HERO_ROWS];
	private final JLabel[] heroValues = new JLabel[DoomMetricsPanel.Live.HERO_ROWS];

	private final JPanel summaryRows = PanelStyle.column(PanelStyle.ROW_GAP);
	private final JLabel idle = PanelStyle.caption("No run yet", SwingConstants.LEFT);

	/** The rows under the two figures, built once and retexted - see {@link #setLive}. */
	private final JLabel[] rowCaptions = new JLabel[DoomMetricsPanel.Live.ROWS];
	private final JLabel[] rowValues = new JLabel[DoomMetricsPanel.Live.ROWS];
	private final JPanel[] rows = new JPanel[DoomMetricsPanel.Live.ROWS];

	/**
	 * Which of {@link #rows} are up, a bit for each by its index, or -1 while the idle line is.
	 * Starts as neither, so the first snapshot always lays them out.
	 */
	private int shownRows = Integer.MIN_VALUE;

	/**
	 * @param icon    the plugin's own icon, so the window is identifiable in the taskbar
	 * @param onClose run when the user closes the window, to drop the plugin's reference to it
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

		// The chart and its legend are two halves of one thing: pointing at a delve moves the
		// legend's column onto that delve, and pointing at a name brings that line forward. The
		// drops list is read the same way, lighting up whatever came off the delve pointed at.
		chart.setHoverListener(level ->
		{
			legend.setDelve(level);
			drops.setDelve(level);
		});
		legend.setToggleListener(chart::setHidden);
		legend.setEmphasisListener(chart::setEmphasis);

		buildSummary();
		dropsSection.setVisible(false);

		JPanel content = new JPanel(new BorderLayout(10, 0));
		content.setBackground(PanelStyle.BACKGROUND);
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(sidebar(), BorderLayout.WEST);
		content.add(PanelStyle.section("Per delve", chart), BorderLayout.CENTER);

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

	/**
	 * @param detail the run to draw, delve by delve
	 *
	 * <p>Pushed only when something on the chart has moved: a delve killed, something counted in
	 * the wait after it, or that wait ending - see {@link RunDetail#keyFor}.
	 */
	void setDetail(RunDetail detail)
	{
		chart.setDetail(detail);
		legend.setDetail(detail);
		drops.setDetail(detail);
		dropsSection.setVisible(!detail.drops().isEmpty());
	}

	/** @param hideEmpty whether a counter the run has not counted anything on is left out */
	void setHideEmpty(boolean hideEmpty)
	{
		legend.setHideEmpty(hideEmpty);
	}

	/**
	 * @param icons the pictures of the drops on the chart, and of the counters and drops named
	 *              beside it. The plugin hands over the game's own, again as each arrives; the
	 *              preview harness, which has no game, hands over copies it keeps with its tests.
	 */
	void setIcons(Icons icons)
	{
		chart.setItemIcons(icons::item);
		legend.setIcons(icons);
		drops.setIcons(icons);
	}

	/**
	 * @param live the same rows the overlay and the side panel draw, or null when there is no run
	 *
	 * <p>Pushed on the ordinary refresh, so the clock at the head of the sidebar runs while the
	 * chart under it sits still between clears.
	 */
	void setLive(DoomMetricsPanel.Live live)
	{
		int shown = -1;

		if (live == null)
		{
			for (int i = 0; i < DoomMetricsPanel.Live.HERO_ROWS; i++)
			{
				heroValues[i].setText("-");
				heroValues[i].setForeground(DoomColors.DIMMED);
			}

			heroCaptions[0].setText("Delve");
			heroCaptions[1].setText("Time");
		}
		else
		{
			for (int i = 0; i < DoomMetricsPanel.Live.HERO_ROWS; i++)
			{
				heroCaptions[i].setText(live.labels[i]);
				heroValues[i].setText(live.values[i]);
			}

			heroValues[0].setForeground(live.died
				? ColorScheme.PROGRESS_ERROR_COLOR
				: DoomColors.PLAIN);
			heroValues[1].setForeground(live.finished ? DoomColors.DIMMED : DoomColors.PLAIN);

			shown = 0;

			for (int i = DoomMetricsPanel.Live.HERO_ROWS; i < DoomMetricsPanel.Live.ROWS; i++)
			{
				if (live.labels[i] == null)
				{
					continue;
				}

				rowCaptions[i].setText(live.labels[i]);
				rowValues[i].setText(live.values[i]);
				shown |= 1 << i;
			}
		}

		showRows(shown);
	}

	/**
	 * Puts up the rows a snapshot has, and does nothing when they are the ones already up.
	 *
	 * <p>A snapshot is pushed every time the clock at the head moves, which is every second, and
	 * taking the rows down and building them again for that would lay the sidebar out anew each
	 * time. What changes the rows is rarer - a run starting or ending, a target reached - and is
	 * the only thing that needs them put up again.
	 */
	private void showRows(int shown)
	{
		if (shown == shownRows)
		{
			return;
		}

		shownRows = shown;
		summaryRows.removeAll();

		if (shown < 0)
		{
			summaryRows.add(idle);
		}
		else
		{
			for (int i = DoomMetricsPanel.Live.HERO_ROWS; i < DoomMetricsPanel.Live.ROWS; i++)
			{
				if ((shown & (1 << i)) != 0)
				{
					summaryRows.add(rows[i]);
				}
			}
		}

		summaryRows.revalidate();
		summaryRows.repaint();
	}

	/**
	 * The two figures a run is read by, and the rest of its rows under them - the same head the
	 * side panel gives the run, so the two never word the same state differently.
	 */
	private void buildSummary()
	{
		JPanel hero = new JPanel(new GridLayout(1, 2, 6, 0));
		hero.setBackground(PanelStyle.CARD);

		for (int i = 0; i < DoomMetricsPanel.Live.HERO_ROWS; i++)
		{
			heroCaptions[i] = PanelStyle.caption("", SwingConstants.LEFT);
			heroValues[i] = PanelStyle.hero("-", SwingConstants.LEFT);

			JPanel tile = new JPanel(new BorderLayout());
			tile.setBackground(PanelStyle.CARD);
			tile.add(heroCaptions[i], BorderLayout.NORTH);
			tile.add(heroValues[i], BorderLayout.CENTER);
			hero.add(tile);
		}

		for (int i = DoomMetricsPanel.Live.HERO_ROWS; i < DoomMetricsPanel.Live.ROWS; i++)
		{
			rowCaptions[i] = PanelStyle.caption("", SwingConstants.LEFT);
			rowValues[i] = PanelStyle.body("", SwingConstants.RIGHT);
			rows[i] = pair(rowCaptions[i], rowValues[i]);
		}

		summary.add(hero);
		summary.add(PanelStyle.rule());
		summary.add(summaryRows);
		setLive(null);
	}

	private static JPanel pair(JLabel label, JLabel value)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(PanelStyle.CARD);
		panel.add(label, BorderLayout.WEST);
		panel.add(value, BorderLayout.EAST);
		return panel;
	}

	/**
	 * The run's own figures over its drops and the legend, stacked and scrolled together. Their
	 * height is eight counters under five headings and a drop or two, so they scroll only when
	 * the window is made short enough to need it.
	 */
	private JScrollPane sidebar()
	{
		// A hidden section takes its gap with it, so a run without drops lays out as it always did.
		JPanel lower = new JPanel(new BorderLayout(0, PanelStyle.SECTION_GAP));
		lower.setBackground(PanelStyle.BACKGROUND);
		lower.add(dropsSection, BorderLayout.NORTH);
		lower.add(PanelStyle.section("Counters", legend), BorderLayout.CENTER);

		JPanel stack = new JPanel(new BorderLayout(0, PanelStyle.SECTION_GAP));
		stack.setBackground(PanelStyle.BACKGROUND);
		stack.add(PanelStyle.section("This run", PanelStyle.card(summary)), BorderLayout.NORTH);
		stack.add(lower, BorderLayout.CENTER);

		// The legend is a grid; wrapping it in a BorderLayout stops the viewport stretching its
		// rows to fill the height. Pinning its width stops the opposite: a row wider than the
		// sidebar - a long heading, a lifetime figure - would otherwise be laid out at the width
		// it asked for and have its last digits clipped off by the viewport, with no horizontal
		// scrollbar to reach them by. Held to the width it has, a row squeezes its name instead,
		// which is what its tooltip is for.
		JPanel top = new ColumnWidth();
		top.setBackground(PanelStyle.BACKGROUND);
		top.add(stack, BorderLayout.NORTH);

		JScrollPane scroller = new JScrollPane(top,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(PanelStyle.BACKGROUND);
		scroller.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT);

		// The sidebar plus a lane for the scrollbar, which is kept clear whether or not the bar is
		// in it. A scrollbar that takes its width out of the rows relays every one of them as it
		// appears, and it appears on a window short enough that the counters listed one per source
		// do not fit while the same counters grouped into five do - so switching between the two
		// moved every figure in the column sideways, twice, for a bar neither of them asked about.
		// Given a lane of its own it comes and goes in the gap between the sidebar and the chart,
		// where there was nothing to disturb, and the rows do not move at all.
		int lane = scroller.getVerticalScrollBar().getPreferredSize().width;
		scroller.setPreferredSize(new Dimension(SIDEBAR_WIDTH + lane, 0));
		return scroller;
	}

	/**
	 * The sidebar's contents, laid out at the sidebar's width whatever they would rather have and
	 * whatever the viewport around them is doing - see {@link #sidebar()}.
	 */
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

		/**
		 * Never. The width is this component's own, so the lane the scrollbar sits in is left
		 * empty when there is no bar in it rather than being handed to the rows.
		 */
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
