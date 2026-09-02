package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;

/**
 * The lifetime milestone table - delve, kill count, personal best - as a component in its own
 * right, so the side panel and the history window draw the same table rather than two that drift.
 *
 * <p>Runs on the Swing thread. Callers hand it immutable snapshots, never live model objects.
 */
class MilestoneTablePanel extends JPanel
{
	/** One row of the table, copied out of {@link MilestoneTable} for display. */
	static final class Row
	{
		final int delve;
		final int kc;
		final int pbTicks;

		/** Beaten since the client started, so the table can point at what you just improved. */
		final boolean improved;

		Row(int delve, int kc, int pbTicks, boolean improved)
		{
			this.delve = delve;
			this.kc = kc;
			this.pbTicks = pbTicks;
			this.improved = improved;
		}
	}

	/**
	 * How the three columns share the available width.
	 *
	 * <p>Weights only divide the space left over once every cell has its preferred width, so these
	 * hold their promise only because the whole table is a single grid. Laying each row out on its
	 * own would let a long personal best in one row push that row's columns off the others.
	 */
	private static final double[] COLUMN_WEIGHTS = {0.30, 0.26, 0.44};

	private static final Border HEADER_PADDING = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, PanelStyle.RULE),
		PanelStyle.CELL_PADDING);

	private final String emptyText;

	MilestoneTablePanel(String emptyText)
	{
		super(new GridBagLayout());
		this.emptyText = emptyText;
		setBackground(PanelStyle.BACKGROUND);
	}

	/** Rebuilds the table. Called only when a row actually changed. */
	void setRows(List<Row> rows)
	{
		removeAll();

		if (rows.isEmpty())
		{
			JLabel empty = PanelStyle.caption(emptyText, SwingConstants.LEFT);
			empty.setBorder(PanelStyle.CELL_PADDING);

			GridBagConstraints constraints = new GridBagConstraints();
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.gridwidth = COLUMN_WEIGHTS.length;
			constraints.weightx = 1;
			add(empty, constraints);
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

		revalidate();
		repaint();
	}

	private void addHeaderRow()
	{
		addRow(0, PanelStyle.BACKGROUND, HEADER_PADDING,
			PanelStyle.caption("Delve", SwingConstants.RIGHT),
			PanelStyle.caption("KC", SwingConstants.RIGHT),
			PanelStyle.caption("PB", SwingConstants.RIGHT));
	}

	private void addDataRow(Row data, int index)
	{
		JLabel delve = PanelStyle.body(Integer.toString(data.delve), SwingConstants.RIGHT);
		JLabel kc = PanelStyle.body(Integer.toString(data.kc), SwingConstants.RIGHT);
		JLabel pb = PanelStyle.body(DoomFormat.ticks(data.pbTicks), SwingConstants.RIGHT);

		// A seeded row - reached before the plugin was watching - has nothing measured behind it.
		kc.setForeground(data.kc == 0 ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.TEXT_COLOR);
		pb.setForeground(data.improved
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: data.pbTicks > 0 ? ColorScheme.TEXT_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		addRow(index + 1, index % 2 == 0 ? PanelStyle.CARD : PanelStyle.STRIPE,
			PanelStyle.CELL_PADDING, delve, kc, pb);
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
			add(cells[i], constraints);
		}
	}
}
