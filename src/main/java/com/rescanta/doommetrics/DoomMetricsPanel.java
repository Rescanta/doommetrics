package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: the run in progress on top, the sitting beside the character's lifetime under it,
 * then the sitting's combat figures, the lifetime milestone table, and a button that opens the
 * history window.
 *
 * <p>The run is drawn as two large figures with the rest of it in small type beneath, because
 * there are only two things a player reads while they are being hit - which delve they are on and
 * how long they have been down - and the panel is worth nothing if those have to be picked out of
 * a list of eleven numbers set in the same type.
 *
 * <p>The sitting and the lifetime share one table, one column each, rather than sitting in two
 * sections one above the other. They do answer different questions - how this evening is going,
 * and what the character has done over all of them - but the question a player actually asks is
 * whether tonight is better than usual, and that is a comparison. Under the two column headings
 * each figure still says which it is; side by side it also says how they stand.
 *
 * <p>Everything here runs on the Swing thread. The plugin hands it immutable snapshots rather than
 * live objects, so nothing the client thread is still writing to is ever read from a paint.
 */
class DoomMetricsPanel extends PluginPanel
{
	/** The figures mirrored from the overlay, or null when there is no run to show. */
	static final class Live
	{
		/** How many rows a run can fill: delve, time, pace, and the target pair. */
		static final int ROWS = 5;

		/** Which of those rows are drawn large, at the head of the section. */
		private static final int HERO_ROWS = 2;

		/** Row labels in draw order, a null label meaning that row is switched off. */
		final String[] labels;

		final String[] values;

		/** Whether the run is over, which is what stops the clock rather than merely pausing it. */
		final boolean finished;

		/** Whether it ended in a death, the one outcome worth colouring. */
		final boolean died;

		private Live(String[] labels, String[] values, boolean finished, boolean died)
		{
			this.labels = labels;
			this.values = values;
			this.finished = finished;
			this.died = died;
		}

		/**
		 * The live rows of a run, formatted for drawing. The overlay draws the same figures itself
		 * from the run; this is the panel's copy of them.
		 *
		 * @param target the delve being aimed for, or 0 when the target rows are switched off
		 */
		static Live of(DelveRun display, PaceMode mode, int target)
		{
			boolean died = display.isFinished() && display.getEndReason() == EndReason.DIED;
			String delveLabel;
			String delveValue;

			if (!display.isFinished())
			{
				delveLabel = "Delve";
				delveValue = Integer.toString(display.currentLevel());
			}
			else if (died)
			{
				delveLabel = "Died on";
				// The number alone: the heading over it already says what it is a number of.
				delveValue = Integer.toString(display.getDiedOnLevel());
			}
			else
			{
				delveLabel = "Cleared";
				delveValue = Integer.toString(display.lastLevel());
			}

			Instant now = Instant.now();

			return new Live(
				new String[]{
					delveLabel,
					// The asterisk marks a run joined part way through, whose start is a guess.
					display.isPartial() ? "Time*" : "Time",
					mode.toString(),
					target > 0 ? "Target" : null,
					target > 0 ? "Predicted" : null,
				},
				new String[]{
					delveValue,
					DoomFormat.duration(display.displayElapsed(now)),
					DoomFormat.pace(display.pace(mode)),
					target > 0 ? Integer.toString(target) : null,
					target > 0 ? DoomFormat.prediction(display.untilTarget(target, now),
						display.hasReached(target)) : null,
				},
				display.isFinished(),
				died);
		}

		/**
		 * Enough of the snapshot to tell one repaint from the next. Built from the rows themselves
		 * rather than named off a list of fields, so a row added later cannot be left out of it and
		 * silently stop the panel redrawing.
		 */
		String key()
		{
			StringBuilder key = new StringBuilder();

			for (int i = 0; i < labels.length; i++)
			{
				key.append(labels[i]).append('|').append(values[i]).append('|');
			}

			return key.append(finished).append('|').append(died).toString();
		}
	}

	/**
	 * The sitting's figures and the character's, already formatted. Any of them may be null, which
	 * reads as {@code "-"}: between sittings there is nothing to report on the session, and a brand
	 * new character has no lifetime rate until a run banks a deep delve.
	 */
	static final class Stats
	{
		final String sessionLength;
		final String sessionPace;
		final String sessionTooltip;
		final String sessionDeep;
		final String lifetimePace;
		final String lifetimeTooltip;
		final String lifetimeDeep;

		Stats(String sessionLength, String sessionPace, String sessionTooltip, String sessionDeep,
			String lifetimePace, String lifetimeTooltip, String lifetimeDeep)
		{
			this.sessionLength = sessionLength;
			this.sessionPace = sessionPace;
			this.sessionTooltip = sessionTooltip;
			this.sessionDeep = sessionDeep;
			this.lifetimePace = lifetimePace;
			this.lifetimeTooltip = lifetimeTooltip;
			this.lifetimeDeep = lifetimeDeep;
		}

		/** Enough of the snapshot to tell one repaint from the next. */
		String key()
		{
			return sessionLength + "|" + sessionPace + "|" + sessionDeep
				+ "|" + lifetimePace + "|" + lifetimeDeep;
		}
	}

	/** How the three columns of the session and lifetime table share the width. */
	private static final double[] COMPARE_WEIGHTS = {0.36, 0.32, 0.32};

	private final JPanel runCard = PanelStyle.column(4);
	private final JPanel runRows = PanelStyle.column(PanelStyle.ROW_GAP);
	private final CombatTablePanel combatPanel = new CombatTablePanel();
	private final MilestoneTablePanel tablePanel = new MilestoneTablePanel("Nothing banked yet.");

	private final JLabel idleLabel = PanelStyle.caption("No run in progress",
		SwingConstants.LEFT);

	private final JLabel[] heroCaptions = {
		PanelStyle.caption("Delve", SwingConstants.LEFT),
		PanelStyle.caption("Time", SwingConstants.RIGHT),
	};

	private final JLabel[] heroValues = {
		PanelStyle.hero("-", SwingConstants.LEFT),
		PanelStyle.hero("-", SwingConstants.RIGHT),
	};

	private final JLabel[] runLabels = new JLabel[Live.ROWS];
	private final JLabel[] runValues = new JLabel[Live.ROWS];

	private final JLabel sessionLength = PanelStyle.body("-", SwingConstants.RIGHT);
	private final JLabel sessionPace = PanelStyle.body("-", SwingConstants.RIGHT);
	private final JLabel sessionDeep = PanelStyle.body("-", SwingConstants.RIGHT);
	private final JLabel lifetimePace = PanelStyle.body("-", SwingConstants.RIGHT);
	private final JLabel lifetimeDeep = PanelStyle.body("-", SwingConstants.RIGHT);

	/** @param onOpenHistory invoked on the Swing thread when the history button is pressed */
	DoomMetricsPanel(Runnable onOpenHistory)
	{
		setBackground(PanelStyle.BACKGROUND);
		setLayout(new DynamicGridLayout(0, 1, 0, PanelStyle.SECTION_GAP));

		for (int i = Live.HERO_ROWS; i < Live.ROWS; i++)
		{
			runLabels[i] = PanelStyle.caption("", SwingConstants.LEFT);
			runValues[i] = PanelStyle.body("", SwingConstants.RIGHT);
		}

		runCard.add(hero());
		runCard.add(PanelStyle.rule());
		runCard.add(runRows);

		add(PanelStyle.section("Current run", PanelStyle.card(runCard)));
		// The sitting and the character's lifetime above the tables: what is being earned right
		// now is what a player glances at mid-run, and the rest is what they scroll to.
		add(PanelStyle.section("Session & lifetime", PanelStyle.card(compare())));
		add(PanelStyle.section("Session combat", combatPanel));
		add(PanelStyle.section("Milestones", tablePanel));
		add(historyButton(onOpenHistory));

		setLive(null);
		setStats(null);
		setCombat(null);
		setRows(Collections.emptyList());
	}

	/** Repaints the sitting's and the character's figures. A null snapshot blanks all of them. */
	void setStats(Stats stats)
	{
		apply(sessionLength, stats == null ? null : stats.sessionLength, null);
		apply(sessionPace, stats == null ? null : stats.sessionPace,
			stats == null ? null : stats.sessionTooltip);
		apply(sessionDeep, stats == null ? null : stats.sessionDeep, null);
		apply(lifetimePace, stats == null ? null : stats.lifetimePace,
			stats == null ? null : stats.lifetimeTooltip);
		apply(lifetimeDeep, stats == null ? null : stats.lifetimeDeep, null);
	}

	/**
	 * Retexts one value cell. A null tooltip leaves whatever the cell already had, so the fixed
	 * explanations set once at build time are not wiped by a snapshot that has nothing to add.
	 */
	private static void apply(JLabel label, String value, String tooltip)
	{
		label.setText(value == null ? "-" : value);
		label.setForeground(value == null ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.TEXT_COLOR);

		if (tooltip != null)
		{
			label.setToolTipText(tooltip);
		}
	}

	/**
	 * Repaints the live figures. A null snapshot leaves the two large figures blank and collapses
	 * the rest of the card to one idle line, so the section holds its shape between runs instead
	 * of the whole panel jumping every time one starts.
	 */
	void setLive(Live live)
	{
		runRows.removeAll();

		if (live == null)
		{
			for (int i = 0; i < Live.HERO_ROWS; i++)
			{
				heroValues[i].setText("-");
				heroValues[i].setForeground(DoomColors.DIMMED);
			}

			heroCaptions[0].setText("Delve");
			heroCaptions[1].setText("Time");
			runRows.add(idleLabel);
		}
		else
		{
			for (int i = 0; i < Live.HERO_ROWS; i++)
			{
				heroCaptions[i].setText(live.labels[i]);
				heroValues[i].setText(live.values[i]);
			}

			// A death is the one outcome the panel colours, and a stopped clock is dimmed so a
			// run walked out of ten minutes ago is not read as one still going.
			heroValues[0].setForeground(live.died
				? ColorScheme.PROGRESS_ERROR_COLOR
				: DoomColors.PLAIN);
			heroValues[1].setForeground(live.finished ? DoomColors.DIMMED : DoomColors.PLAIN);

			for (int i = Live.HERO_ROWS; i < Live.ROWS; i++)
			{
				if (live.labels[i] == null)
				{
					continue;
				}

				runLabels[i].setText(live.labels[i]);
				runValues[i].setText(live.values[i]);
				runRows.add(pair(runLabels[i], runValues[i]));
			}
		}

		runRows.revalidate();
		runRows.repaint();
	}

	/**
	 * Repaints the sitting's combat figures, the run in progress included. A null tally reads as
	 * all zeroes - between sittings there is nothing being earned, and that is not the same as
	 * leaving this morning's numbers up as though there were.
	 */
	void setCombat(CombatTotals totals)
	{
		combatPanel.setTotals(totals);
	}

	/** Rebuilds the milestone table. Called only when a row actually changed. */
	void setRows(List<MilestoneTablePanel.Row> rows)
	{
		tablePanel.setRows(rows);
	}

	/**
	 * The delve and the clock, side by side and large, each under the word for what it is.
	 *
	 * <p>The clock is set against the right edge so its digits stay put as it goes from four
	 * characters to five to seven, rather than the whole figure sliding left as the run wears on.
	 */
	private JPanel hero()
	{
		JPanel panel = new JPanel(new GridLayout(1, 2, 6, 0));
		panel.setBackground(PanelStyle.CARD);

		for (int i = 0; i < Live.HERO_ROWS; i++)
		{
			JPanel tile = new JPanel(new BorderLayout());
			tile.setBackground(PanelStyle.CARD);
			tile.add(heroCaptions[i], BorderLayout.NORTH);
			tile.add(heroValues[i], BorderLayout.CENTER);
			panel.add(tile);
		}

		return panel;
	}

	/**
	 * The sitting's figures and the character's in one grid, a column each under its own heading.
	 *
	 * <p>The sitting's length has no lifetime counterpart, so it sits above the comparison rather
	 * than in them as a row with half of it permanently blank.
	 */
	private JPanel compare()
	{
		JLabel length = PanelStyle.caption("Sitting length", SwingConstants.LEFT);
		length.setToolTipText("How long this sitting has been going, from its first run to now");
		sessionLength.setToolTipText(length.getToolTipText());

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBackground(PanelStyle.CARD);
		addCompareRow(grid, 0, PanelStyle.caption("", SwingConstants.LEFT),
			PanelStyle.caption("Session", SwingConstants.RIGHT),
			PanelStyle.caption("Lifetime", SwingConstants.RIGHT));
		addCompareRow(grid, 1, PanelStyle.caption("Deep pace", SwingConstants.LEFT),
			sessionPace, lifetimePace);
		addCompareRow(grid, 2, PanelStyle.caption("Deep delves", SwingConstants.LEFT),
			sessionDeep, lifetimeDeep);

		JPanel panel = PanelStyle.column(4);
		panel.add(pair(length, sessionLength));
		panel.add(PanelStyle.rule());
		panel.add(grid);
		return panel;
	}

	private static void addCompareRow(JPanel grid, int gridy, JLabel... cells)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridy = gridy;
		constraints.ipady = 2;

		for (int i = 0; i < cells.length; i++)
		{
			constraints.gridx = i;
			constraints.weightx = COMPARE_WEIGHTS[i];
			grid.add(cells[i], constraints);
		}
	}

	/** A name on the left and its figure on the right, the shape most of the panel is made of. */
	private static JPanel pair(JLabel left, JLabel right)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(PanelStyle.CARD);
		panel.add(left, BorderLayout.WEST);
		panel.add(right, BorderLayout.EAST);
		return panel;
	}

	private static JComponent historyButton(Runnable onOpenHistory)
	{
		JButton button = new JButton("Open history");
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.TEXT_COLOR);
		button.setBackground(PanelStyle.CARD);
		button.setBorder(new EmptyBorder(7, 8, 7, 8));
		button.setFocusPainted(false);
		button.setToolTipText("Show the milestone table and depth per run in their own window");
		button.addActionListener(event -> onOpenHistory.run());

		// The panel is otherwise all text, so nothing about the button says it can be pressed
		// until the pointer is over it.
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				button.setBackground(PanelStyle.CARD);
			}
		});

		return button;
	}
}
