package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the run in progress on top, and under it the lifetime milestone table.
 *
 * <p>Everything here runs on the Swing thread. The plugin hands it immutable snapshots rather than
 * live objects, so nothing the client thread is still writing to is ever read from a paint.
 */
class DoomMetricsPanel extends PluginPanel
{
	/** One row of the milestone table, copied out of {@link MilestoneTable} for display. */
	static final class Row
	{
		final int delve;
		final int kc;
		final int pbTicks;

		/** Beaten since the client started, so the panel can point at what you just improved. */
		final boolean improved;

		Row(int delve, int kc, int pbTicks, boolean improved)
		{
			this.delve = delve;
			this.kc = kc;
			this.pbTicks = pbTicks;
			this.improved = improved;
		}
	}

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

	private static final Color STRIPE = new Color(36, 36, 36);

	/**
	 * How the three columns share the panel width.
	 *
	 * <p>Weights only divide the space left over once every cell has its preferred width, so these
	 * hold their promise only because the whole table is a single grid. Laying each row out on its
	 * own would let a long personal best in one row push that row's columns off the others.
	 */
	private static final double[] COLUMN_WEIGHTS = {0.30, 0.26, 0.44};

	private static final Border CELL_PADDING = new EmptyBorder(2, 4, 2, 4);
	private static final Border HEADER_PADDING = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
		CELL_PADDING);

	private final JPanel livePanel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final JPanel tablePanel = new JPanel(new GridBagLayout());

	private final JLabel idleLabel = plain("No run in progress", SwingConstants.LEFT);
	private final JLabel[] liveLeft = {plain("", SwingConstants.LEFT), plain("", SwingConstants.LEFT),
		plain("", SwingConstants.LEFT)};
	private final JLabel[] liveRight = {plain("", SwingConstants.RIGHT),
		plain("", SwingConstants.RIGHT), plain("", SwingConstants.RIGHT)};

	DoomMetricsPanel()
	{
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new DynamicGridLayout(0, 1, 0, 10));

		livePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tablePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(section("Current run", livePanel));
		add(section("Milestones", tablePanel));

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
	void setRows(List<Row> rows)
	{
		tablePanel.removeAll();

		if (rows.isEmpty())
		{
			JLabel empty = plain("Nothing banked yet.", SwingConstants.LEFT);
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(CELL_PADDING);

			GridBagConstraints constraints = new GridBagConstraints();
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.gridwidth = COLUMN_WEIGHTS.length;
			constraints.weightx = 1;
			tablePanel.add(empty, constraints);
		}
		else
		{
			addHeaderRow();

			int index = 0;

			for (Row row : rows)
			{
				addDataRow(row, index++);
			}
		}

		tablePanel.revalidate();
		tablePanel.repaint();
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

	private void addHeaderRow()
	{
		addRow(0, ColorScheme.DARK_GRAY_COLOR, HEADER_PADDING,
			bold("Delve", SwingConstants.RIGHT),
			bold("KC", SwingConstants.RIGHT),
			bold("PB", SwingConstants.RIGHT));
	}

	private void addDataRow(Row data, int index)
	{
		JLabel delve = plain(Integer.toString(data.delve), SwingConstants.RIGHT);
		JLabel kc = plain(Integer.toString(data.kc), SwingConstants.RIGHT);
		JLabel pb = plain(DoomFormat.ticks(data.pbTicks), SwingConstants.RIGHT);

		delve.setForeground(ColorScheme.TEXT_COLOR);

		// A seeded row - reached before the plugin was watching - has nothing measured behind it.
		kc.setForeground(data.kc == 0 ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.TEXT_COLOR);
		pb.setForeground(data.improved
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: data.pbTicks > 0 ? ColorScheme.TEXT_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		addRow(index + 1, index % 2 == 0 ? ColorScheme.DARKER_GRAY_COLOR : STRIPE,
			CELL_PADDING, delve, kc, pb);
	}

	/**
	 * Adds one row of cells straight into the shared grid.
	 *
	 * <p>The cells carry the row's stripe themselves rather than sitting on a panel that paints it,
	 * which is what lets every row live in one grid and so line its columns up with every other.
	 */
	private void addRow(int gridy, Color background, Border border, JLabel... cells)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.BOTH;
		constraints.gridy = gridy;

		// The gap the stripes used to be separated by, now that they tile the row themselves.
		constraints.insets = new Insets(gridy == 0 ? 0 : 1, 0, 0, 0);

		for (int i = 0; i < cells.length; i++)
		{
			cells[i].setOpaque(true);
			cells[i].setBackground(background);
			cells[i].setBorder(border);

			constraints.gridx = i;
			constraints.weightx = COLUMN_WEIGHTS[i];
			tablePanel.add(cells[i], constraints);
		}
	}

	private static JPanel pair(JLabel left, JLabel right, Color background)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(background);
		panel.add(left, BorderLayout.WEST);
		panel.add(right, BorderLayout.EAST);
		return panel;
	}

	private static JLabel plain(String text, int alignment)
	{
		return label(text, alignment, FontManager.getRunescapeSmallFont());
	}

	private static JLabel bold(String text, int alignment)
	{
		JLabel label = label(text, alignment, FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static JLabel label(String text, int alignment, Font font)
	{
		JLabel label = new JLabel(text, alignment);
		label.setFont(font);
		label.setForeground(ColorScheme.TEXT_COLOR);
		return label;
	}
}
