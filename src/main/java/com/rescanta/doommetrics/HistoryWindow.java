package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A window of its own, outside the side panel and outside the client: the milestone table and the
 * character's lifetime combat figures down one side, and any of those figures charted per run down
 * the other.
 *
 * <p>The chart wants far more width than a side panel has, and the tables are the thing you read it
 * against, so all of it lives here rather than being cramped to fit beside the client.
 *
 * <p>Swing thread only. The plugin owns the single instance and disposes it on shutdown; closing
 * the window disposes it and tells the plugin to forget it, so the next open builds a fresh one
 * rather than resurrecting a disposed frame.
 */
class HistoryWindow extends JFrame
{
	private final MilestoneTablePanel table = new MilestoneTablePanel("Nothing banked yet.");
	private final CombatTablePanel combat = new CombatTablePanel();
	private final DelveChart chart = new DelveChart();
	private final JComboBox<ChartOption> metric = new JComboBox<>(
		new DefaultComboBoxModel<>(ChartOption.all().toArray(new ChartOption[0])));

	/**
	 * The whole history, so switching metric redraws from memory instead of going back to disk.
	 * Replaced outright whenever the plugin hands over a new one.
	 */
	private RunSeries series = RunSeries.empty();

	/**
	 * @param icon    the plugin's own icon, so the window is identifiable in the taskbar
	 * @param onClose run when the user closes the window, to drop the plugin's reference to it
	 */
	HistoryWindow(BufferedImage icon, Runnable onClose)
	{
		super("Doom Metrics - History");

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

		metric.setSelectedItem(ChartOption.deepestDelve());
		metric.setFocusable(false);
		metric.setToolTipText("Which figure to plot for each run");
		metric.addActionListener(event -> redrawChart());

		JPanel content = new JPanel(new BorderLayout(10, 0));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(sidebar(), BorderLayout.WEST);
		content.add(chartPane(), BorderLayout.CENTER);

		setContentPane(content);
		setMinimumSize(new Dimension(700, 420));
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

	void setRows(List<MilestoneTablePanel.Row> rows)
	{
		table.setRows(rows);
	}

	/** @param totals what this character has healed, restored and hit for over their lifetime */
	void setLifetimeCombat(CombatTotals totals)
	{
		combat.setTotals(totals);
	}

	/** @param series every recorded run, reduced to one value per metric per run, oldest first */
	void setSeries(RunSeries series)
	{
		this.series = series;
		redrawChart();
	}

	private void redrawChart()
	{
		ChartOption selected = (ChartOption) metric.getSelectedItem();
		chart.setSeries(series.seriesFor(
			selected == null ? ChartOption.deepestDelve() : selected));
	}

	/**
	 * The chart, with its metric picker above it. The picker sits on the chart rather than beside
	 * the tables because what it changes is the chart and nothing else.
	 */
	private JPanel chartPane()
	{
		JLabel prompt = new JLabel("Show");
		prompt.setFont(FontManager.getRunescapeSmallFont());
		prompt.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel picker = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		picker.setBackground(ColorScheme.DARK_GRAY_COLOR);
		picker.add(prompt);
		picker.add(metric);

		JPanel wrapper = new JPanel(new BorderLayout(0, 6));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(picker, BorderLayout.NORTH);
		wrapper.add(section("Per run", chart), BorderLayout.CENTER);
		return wrapper;
	}

	/**
	 * The two tables, stacked and scrolled together. Their height is the character's history, so
	 * they scroll rather than driving the whole window's, and their width is fixed to what a label
	 * and a number need.
	 */
	private JScrollPane sidebar()
	{
		JPanel stack = new JPanel(new BorderLayout(0, 10));
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stack.add(section("Lifetime totals", combat), BorderLayout.NORTH);
		stack.add(section("Milestones", table), BorderLayout.CENTER);

		// The tables are grids; wrapping them in a BorderLayout stops the viewport stretching
		// their rows to fill the height when there are only a few of them.
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(stack, BorderLayout.NORTH);

		JScrollPane scroller = new JScrollPane(top,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroller.setPreferredSize(new Dimension(210, 0));
		scroller.getVerticalScrollBar().setUnitIncrement(16);
		return scroller;
	}

	private static JPanel section(String title, Component content)
	{
		JLabel heading = new JLabel(title);
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		heading.setBorder(new EmptyBorder(0, 0, 4, 0));

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(heading, BorderLayout.NORTH);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}
}
