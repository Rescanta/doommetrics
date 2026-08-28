package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the run in progress on top, the lifetime milestone table under it, and a button
 * that opens the history window.
 *
 * <p>Everything here runs on the Swing thread. The plugin hands it immutable snapshots rather than
 * live objects, so nothing the client thread is still writing to is ever read from a paint.
 */
class DoomMetricsPanel extends PluginPanel
{
	/** The three figures mirrored from the overlay, or null when there is no run to show. */
	static final class Live
	{
		final String delveLabel;
		final String delveValue;
		final String timeLabel;
		final String timeValue;
		final String paceLabel;
		final String paceValue;

		Live(String delveLabel, String delveValue, String timeLabel, String timeValue,
			String paceLabel, String paceValue)
		{
			this.delveLabel = delveLabel;
			this.delveValue = delveValue;
			this.timeLabel = timeLabel;
			this.timeValue = timeValue;
			this.paceLabel = paceLabel;
			this.paceValue = paceValue;
		}
	}

	private final JPanel livePanel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final MilestoneTablePanel tablePanel = new MilestoneTablePanel("Nothing banked yet.");

	private final JLabel idleLabel = plain("No run in progress");
	private final JLabel[] liveLeft = {plain(""), plain(""), plain("")};
	private final JLabel[] liveRight = {right(""), right(""), right("")};

	/** @param onOpenHistory invoked on the Swing thread when the history button is pressed */
	DoomMetricsPanel(Runnable onOpenHistory)
	{
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new DynamicGridLayout(0, 1, 0, 10));

		livePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(section("Current run", livePanel));
		add(section("Milestones", tablePanel));
		add(historyButton(onOpenHistory));

		setLive(null);
		setRows(Collections.emptyList());
	}

	/** Repaints the three live figures. A null snapshot collapses the section to one idle line. */
	void setLive(Live live)
	{
		livePanel.removeAll();

		if (live == null)
		{
			idleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			livePanel.add(idleLabel);
		}
		else
		{
			String[] labels = {live.delveLabel, live.timeLabel, live.paceLabel};
			String[] values = {live.delveValue, live.timeValue, live.paceValue};

			for (int i = 0; i < labels.length; i++)
			{
				if (labels[i] == null)
				{
					continue;
				}

				liveLeft[i].setText(labels[i]);
				liveLeft[i].setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				liveRight[i].setText(values[i]);
				liveRight[i].setForeground(ColorScheme.TEXT_COLOR);
				livePanel.add(pair(liveLeft[i], liveRight[i], ColorScheme.DARK_GRAY_COLOR));
			}
		}

		livePanel.revalidate();
		livePanel.repaint();
	}

	/** Rebuilds the milestone table. Called only when a row actually changed. */
	void setRows(List<MilestoneTablePanel.Row> rows)
	{
		tablePanel.setRows(rows);
	}

	private static JButton historyButton(Runnable onOpenHistory)
	{
		JButton button = new JButton("Open history");
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.TEXT_COLOR);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(new EmptyBorder(6, 8, 6, 8));
		button.setFocusPainted(false);
		button.setToolTipText("Show the milestone table and depth per run in their own window");
		button.addActionListener(event -> onOpenHistory.run());
		return button;
	}

	private static JPanel section(String title, JPanel content)
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

	private static JPanel pair(JLabel left, JLabel right, Color background)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(background);
		panel.add(left, BorderLayout.WEST);
		panel.add(right, BorderLayout.EAST);
		return panel;
	}

	private static JLabel plain(String text)
	{
		return label(text, SwingConstants.LEFT);
	}

	private static JLabel right(String text)
	{
		return label(text, SwingConstants.RIGHT);
	}

	private static JLabel label(String text, int alignment)
	{
		JLabel label = new JLabel(text, alignment);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.TEXT_COLOR);
		return label;
	}
}
