package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
 * because you opened it, so it has no reason to time itself out. Clearing the overlay clears this
 * too, since that is what asking to be rid of a finished run means.
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

	private final DelveChart chart = new DelveChart();
	private final RunLegendPanel legend = new RunLegendPanel();
	private final JPanel summary = PanelStyle.column(3);

	/** The two figures at the head of the sidebar - see {@link DoomMetricsPanel.Live}. */
	private final JLabel[] heroCaptions = new JLabel[DoomMetricsPanel.Live.HERO_ROWS];
	private final JLabel[] heroValues = new JLabel[DoomMetricsPanel.Live.HERO_ROWS];

	private final JPanel summaryRows = PanelStyle.column(PanelStyle.ROW_GAP);
	private final JLabel idle = PanelStyle.caption("No run yet", SwingConstants.LEFT);

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
		// legend's column onto that delve, and pointing at a name brings that line forward.
		chart.setHoverListener(legend::setDelve);
		legend.setToggleListener(chart::setHidden);
		legend.setEmphasisListener(chart::setEmphasis);

		buildSummary();

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
	 * <p>Pushed only when a delve is banked, which is the only moment anything on the chart can
	 * change - see {@link RunDetail#keyFor}.
	 */
	void setDetail(RunDetail detail)
	{
		chart.setDetail(detail);
		legend.setDetail(detail);
	}

	/**
	 * @param live the same rows the overlay and the side panel draw, or null when there is no run
	 *
	 * <p>Pushed on the ordinary refresh, so the clock at the head of the sidebar runs while the
	 * chart under it sits still between clears.
	 */
	void setLive(DoomMetricsPanel.Live live)
	{
		summaryRows.removeAll();

		if (live == null)
		{
			for (int i = 0; i < DoomMetricsPanel.Live.HERO_ROWS; i++)
			{
				heroValues[i].setText("-");
				heroValues[i].setForeground(DoomColors.DIMMED);
			}

			heroCaptions[0].setText("Delve");
			heroCaptions[1].setText("Time");
			summaryRows.add(idle);
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

			for (int i = DoomMetricsPanel.Live.HERO_ROWS; i < DoomMetricsPanel.Live.ROWS; i++)
			{
				if (live.labels[i] == null)
				{
					continue;
				}

				summaryRows.add(pair(live.labels[i], live.values[i]));
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

		summary.add(hero);
		summary.add(PanelStyle.rule());
		summary.add(summaryRows);
		setLive(null);
	}

	private static JPanel pair(String label, String value)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(PanelStyle.CARD);
		panel.add(PanelStyle.caption(label, SwingConstants.LEFT), BorderLayout.WEST);
		panel.add(PanelStyle.body(value, SwingConstants.RIGHT), BorderLayout.EAST);
		return panel;
	}

	/**
	 * The run's own figures over the legend, stacked and scrolled together. Their height is twelve
	 * counters under five headings, which is fixed, so they scroll only when the window is made
	 * short enough to need it.
	 */
	private JScrollPane sidebar()
	{
		JPanel stack = new JPanel(new BorderLayout(0, PanelStyle.SECTION_GAP));
		stack.setBackground(PanelStyle.BACKGROUND);
		stack.add(PanelStyle.section("This run", PanelStyle.card(summary)), BorderLayout.NORTH);
		stack.add(PanelStyle.section("Counters", legend), BorderLayout.CENTER);

		// The legend is a grid; wrapping it in a BorderLayout stops the viewport stretching its
		// rows to fill the height.
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(PanelStyle.BACKGROUND);
		top.add(stack, BorderLayout.NORTH);

		JScrollPane scroller = new JScrollPane(top,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(PanelStyle.BACKGROUND);
		scroller.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
		scroller.getVerticalScrollBar().setUnitIncrement(16);
		return scroller;
	}
}
