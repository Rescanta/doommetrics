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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * One run, delve by delve: the chart and its legend. Shows the live run or the last one,
 * ignoring the overlay's linger and Clear. Swing thread only; the plugin owns the one instance.
 */
class RunDetailWindow extends JFrame
{
	/** Wide enough for the sidebar's longest name and its figure, and no wider. */
	private static final int SIDEBAR_WIDTH = 224;

	/** How far a notch of the wheel scrolls the sidebar. */
	private static final int SCROLL_UNIT = 16;

	private final DelveChart chart = new DelveChart();
	private final RunLegendPanel legend = new RunLegendPanel();

	/** Which way the counters are read, one tab each - see {@link #groupingTabs()}. */
	private final MaterialTabGroup grouping = new MaterialTabGroup();

	private MaterialTab separateTab;
	private MaterialTab groupedTab;

	/** Live, died, ended or idle, on the end of the run's heading. */
	private final DoomMetricsPanel.StatusPill status = new DoomMetricsPanel.StatusPill();

	/** The target row, drawn as a meter - see {@link TargetProgress}. */
	private final TargetProgress target = new TargetProgress(4);

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
	 * Which of {@link #rows} are up, a bit per index, or -1 for the idle line. Starts as neither.
	 */
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

		// Hovering a delve moves the legend onto it; hovering a name brings a line forward.
		chart.setHoverListener(legend::setDelve);
		legend.setToggleListener(chart::setHidden);
		legend.setEmphasisListener(chart::setEmphasis);

		buildSummary();

		JPanel content = new JPanel(new BorderLayout(10, 0));
		content.setBackground(PanelStyle.BACKGROUND);
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(sidebar(), BorderLayout.WEST);
		content.add(PanelStyle.flatSection("Per delve", chart), BorderLayout.CENTER);

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
		legend.setIcons(icons);
	}

	/** @param live the side panel's live rows, or null when there is no run */
	void setLive(DoomMetricsPanel.Live live)
	{
		int shown = -1;
		status.show(live);

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

				if (i == DoomMetricsPanel.Live.TARGET_ROW)
				{
					target.show(live);
				}
				else
				{
					rowCaptions[i].setText(live.labels[i]);
					rowValues[i].setText(live.values[i]);
				}

				shown |= 1 << i;
			}
		}

		showRows(shown);
	}

	/**
	 * Puts up the rows a snapshot has; a no-op when they're already up, since the clock ticks every
	 * second.
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

	/** The same head the side panel gives the run. */
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
			rows[i] = i == DoomMetricsPanel.Live.TARGET_ROW
				? target
				: pair(rowCaptions[i], rowValues[i]);
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

	/** Separate / Grouped tabs on the Counters heading. */
	private MaterialTabGroup groupingTabs()
	{
		separateTab = PanelStyle.toggle(grouping, "Sources",
			"One line per counter", () -> showGrouping(false));
		groupedTab = PanelStyle.toggle(grouping, "Grouped",
			"<html>One line per heading, adding up everything under it."
				+ "<br>Includes sources without a line of their own, such as punishes"
				+ "<br>with a weapon the plugin doesn't list.</html>",
			() -> showGrouping(true));
		grouping.setOpaque(false);
		grouping.select(separateTab);
		return grouping;
	}

	private void showGrouping(boolean grouped)
	{
		// The chart first: the legend answers by handing over the lines that are off, and which
		// lines those are depends on which of them the chart is drawing.
		chart.setGrouped(grouped);
		legend.setGrouped(grouped);
	}

	/** For the preview harness. Idempotent. */
	void showGrouped(boolean grouped)
	{
		grouping.select(grouped ? groupedTab : separateTab);
	}

	/** The run's figures and legend, scrolled together. */
	private JScrollPane sidebar()
	{

		JPanel stack = new JPanel(new BorderLayout(0, PanelStyle.SECTION_GAP));
		stack.setBackground(PanelStyle.BACKGROUND);
		stack.add(PanelStyle.flatSection("This run", status, PanelStyle.card(summary)), BorderLayout.NORTH);
		stack.add(PanelStyle.flatSection("Counters", groupingTabs(), legend), BorderLayout.CENTER);

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
