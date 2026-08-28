package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A window of its own, outside the side panel and outside the client: the same milestone table the
 * side panel draws, next to the depth of every run this character has recorded.
 *
 * <p>The chart wants far more width than a side panel has, and the table is the thing you read it
 * against, so both live here rather than one being cramped to fit beside the other.
 *
 * <p>Swing thread only. The plugin owns the single instance and disposes it on shutdown; closing
 * the window disposes it and tells the plugin to forget it, so the next open builds a fresh one
 * rather than resurrecting a disposed frame.
 */
class HistoryWindow extends JFrame
{
	private final MilestoneTablePanel table = new MilestoneTablePanel("Nothing banked yet.");
	private final DelveChart chart = new DelveChart();

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

		JPanel content = new JPanel(new BorderLayout(10, 0));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		content.add(section("Milestones", tableScroller()), BorderLayout.WEST);
		content.add(section("Depth per run", chart), BorderLayout.CENTER);

		setContentPane(content);
		setMinimumSize(new Dimension(560, 320));
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

	/** @param delves the deepest delve cleared in each recorded run, oldest first */
	void setDelves(List<Integer> delves)
	{
		chart.setDelves(delves);
	}

	/**
	 * The table is as tall as the character's history is deep, so it scrolls rather than driving
	 * the whole window's height. Its width is fixed to what three columns of numbers need.
	 */
	private JScrollPane tableScroller()
	{
		// The table is a grid; wrapping it in a BorderLayout stops the viewport stretching its
		// rows to fill the height when there are only a few of them.
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(table, BorderLayout.NORTH);

		JScrollPane scroller = new JScrollPane(top,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroller.setPreferredSize(new Dimension(180, 0));
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
