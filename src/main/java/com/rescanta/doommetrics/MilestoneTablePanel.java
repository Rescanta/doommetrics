package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The lifetime milestone table - delve, kill count, personal best. Deep tables are shortened to
 * every ten delves up to 50 and every 50 after, keeping the target's row and the deepest, with a
 * click to show the rest. Swing thread only.
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

		/** The target delve's row, which is always shown and marked. */
		final boolean target;

		Row(int delve, int kc, int pbTicks, boolean improved, boolean target)
		{
			this.delve = delve;
			this.kc = kc;
			this.pbTicks = pbTicks;
			this.improved = improved;
			this.target = target;
		}
	}

	/** Every row down to here is always shown. */
	private static final int SHOWN_TO = 50;

	/** Past {@link #SHOWN_TO}, only every this many delves is shown while the table is short. */
	private static final int SHORT_STEP = 50;

	/** How the columns share the width; holds only because the whole table is one grid. */
	private static final double[] COLUMN_WEIGHTS = {0.30, 0.26, 0.44};

	private static final Border HEADER_PADDING = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, PanelStyle.RULE),
		PanelStyle.CELL_PADDING);

	private final String emptyText;

	/** The rows as last handed over, kept so a click on the footer can redraw them. */
	private List<Row> rows = Collections.emptyList();

	/** Whether every row is on show rather than the short table. */
	private boolean expanded;

	MilestoneTablePanel(String emptyText)
	{
		super(new GridBagLayout());
		this.emptyText = emptyText;
		setBackground(PanelStyle.CARD);
	}

	/** Rebuilds the table. Called only when a row actually changed. */
	void setRows(List<Row> rows)
	{
		this.rows = rows;
		draw();
	}

	/** For the preview harness. */
	void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		draw();
	}

	/**
	 * The rows the short table keeps: every ten delves to 50, every 50 after, the target and the
	 * deepest.
	 */
	static List<Row> shortened(List<Row> rows)
	{
		List<Row> kept = new ArrayList<>();

		for (int i = 0; i < rows.size(); i++)
		{
			Row row = rows.get(i);

			if (row.delve <= SHOWN_TO || row.delve % SHORT_STEP == 0 || row.target
				|| i == rows.size() - 1)
			{
				kept.add(row);
			}
		}

		return kept;
	}

	private void draw()
	{
		removeAll();

		if (rows.isEmpty())
		{
			JLabel empty = PanelStyle.caption(emptyText, SwingConstants.LEFT);

			GridBagConstraints constraints = new GridBagConstraints();
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.gridwidth = COLUMN_WEIGHTS.length;
			constraints.weightx = 1;
			add(empty, constraints);
		}
		else
		{
			addHeaderRow();

			List<Row> shortRows = shortened(rows);
			List<Row> shown = expanded ? rows : shortRows;
			int index = 0;

			for (Row row : shown)
			{
				addDataRow(row, index++);
			}

			int hidden = rows.size() - shortRows.size();

			if (hidden > 0)
			{
				addFooter(index + 1, hidden);
			}
		}

		revalidate();
		repaint();
	}

	private void addHeaderRow()
	{
		addRow(0, PanelStyle.CARD, HEADER_PADDING,
			PanelStyle.caption("Delve", SwingConstants.LEFT),
			PanelStyle.caption("KC", SwingConstants.RIGHT),
			PanelStyle.caption("PB", SwingConstants.RIGHT));
	}

	private void addDataRow(Row data, int index)
	{
		// The delve in bold: it is what the row is looked up by. The target's in orange.
		JLabel delve = PanelStyle.label(Integer.toString(data.delve), SwingConstants.LEFT,
			FontManager.getRunescapeBoldFont(),
			data.target ? DoomColors.ORANGE : ColorScheme.TEXT_COLOR);

		if (data.target)
		{
			delve.setToolTipText("Your target delve");
		}
		JLabel kc = PanelStyle.body(Integer.toString(data.kc), SwingConstants.RIGHT);
		JLabel pb = PanelStyle.body(DoomFormat.ticks(data.pbTicks), SwingConstants.RIGHT);

		// A seeded row - reached before the plugin was watching - has nothing measured behind it.
		kc.setForeground(data.kc == 0 ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.TEXT_COLOR);
		pb.setForeground(data.improved
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: data.pbTicks > 0 ? ColorScheme.TEXT_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		if (data.improved)
		{
			pb.setToolTipText("New PB since the client started");
		}

		addRow(index + 1, index % 2 == 0 ? PanelStyle.STRIPE : PanelStyle.CARD,
			PanelStyle.CELL_PADDING, delve, kc, pb);
	}

	/** The click that opens the whole table, or closes it back to the short one. */
	private void addFooter(int gridy, int hidden)
	{
		JLabel toggle = PanelStyle.link(() -> setExpanded(!expanded));
		toggle.setText(expanded ? "Show fewer" : "Show all (" + hidden + " more)");

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridy = gridy;
		constraints.gridwidth = COLUMN_WEIGHTS.length;
		constraints.weightx = 1;
		constraints.insets = new Insets(2, 0, 0, 0);
		add(toggle, constraints);
	}

	/** Adds a row's cells to the shared grid; the cells paint the row stripe themselves. */
	private void addRow(int gridy, Color background, Border border, JLabel... cells)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.BOTH;
		constraints.gridy = gridy;
		constraints.insets = new Insets(gridy == 1 ? 2 : 0, 0, 0, 0);

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
