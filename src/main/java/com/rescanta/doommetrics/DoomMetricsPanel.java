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
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The side panel: the run in progress on top, the sitting beside the character's lifetime under it,
 * then the combat figures, the lifetime milestone table, a button that opens the run detail window,
 * and one that starts the session over.
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
		/** How many rows a run can fill: delve, time, pace, the target, and its two predictions. */
		static final int ROWS = 6;

		/** Which of those rows are drawn large, at the head of the section. */
		static final int HERO_ROWS = 2;

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
		 * @param target     the delve being aimed for, or 0 when the target rows are switched off
		 * @param prediction which predicted times the target rows carry
		 */
		static Live of(DelveRun display, PaceMode configured, int target,
			TargetPrediction prediction)
		{
			PaceMode mode = display.paceMode(configured);
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
			String remainingLabel = target > 0 ? prediction.remainingLabel(display, target) : null;
			String totalLabel = target > 0 ? prediction.totalLabel(display) : null;

			return new Live(
				new String[]{
					delveLabel,
					// The asterisk marks a run joined part way through, whose start is a guess.
					display.isPartial() ? "Time*" : "Time",
					mode.toString(),
					target > 0 ? TargetPrediction.targetLabel(display, target) : null,
					remainingLabel,
					totalLabel,
				},
				new String[]{
					delveValue,
					DoomFormat.duration(display.displayElapsed(now)),
					DoomFormat.pace(display.pace(mode)),
					target > 0 ? Integer.toString(target) : null,
					remainingLabel != null
						? TargetPrediction.remainingValue(display, target, now) : null,
					totalLabel != null ? TargetPrediction.totalValue(display, target, now) : null,
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

	/** The two tallies the combat table can draw, the tab in front deciding which it does. */
	private CombatTotals sessionCombat;
	private CombatTotals lifetimeCombat;

	/** Whether the tab in front is the lifetime one. */
	private boolean showingLifetime;

	private final MaterialTabGroup combatTabs = new MaterialTabGroup();

	/** The two tabs, kept so {@link #showLifetime} can put either in front. */
	private MaterialTab sessionTab;
	private MaterialTab lifetimeTab;
	private final MilestoneTablePanel tablePanel = new MilestoneTablePanel("No delves completed yet.");

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

	/**
	 * @param onOpenDetail invoked on the Swing thread when the run detail button is pressed
	 * @param onReset      invoked on the Swing thread once a session reset has been confirmed
	 */
	DoomMetricsPanel(Runnable onOpenDetail, Runnable onReset)
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
		add(PanelStyle.section("Combat", combatSection()));
		add(PanelStyle.section("Milestones", tablePanel));
		add(button("Open run detail", "Break this run down delve by delve, in a window of its own",
			onOpenDetail));
		add(button("Reset session",
			"Start the session over and drop the run in progress. Lifetime figures are kept.",
			() -> confirmReset(onReset)));

		setLive(null);
		setStats(null);
		setCombat(null, null);
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
	 * Repaints the combat table from both tallies: the sitting's, the run in progress included,
	 * and the character's lifetime.
	 *
	 * <p>A null tally reads as all zeroes. The sitting's is null between sittings - there is
	 * nothing being earned, and that is not the same as leaving this morning's numbers up as
	 * though there were. The character's is handed over whatever the sitting is doing: it is not
	 * the evening's to go quiet with, and it is the figure the panel is opened for when there is
	 * no run to watch.
	 */
	void setCombat(CombatTotals session, CombatTotals lifetime)
	{
		sessionCombat = session;
		lifetimeCombat = lifetime;
		drawCombat();
	}

	/** Draws whichever of the two tallies the tab in front is for. */
	private void drawCombat()
	{
		combatPanel.setTotals(showingLifetime ? lifetimeCombat : sessionCombat);
	}

	/**
	 * The combat table, under a tab each for the sitting and the character's lifetime.
	 *
	 * <p>One table behind two tabs rather than two tables down the panel: the rows are the same
	 * counters either way, and stacked they would read as sixteen figures where there are eight
	 * counted twice. Each tab's meters are filled against the largest figure in the tally on show,
	 * so both answer which source is carrying it - against a lifetime the sitting is a rounding
	 * error of, one shared scale would leave every row of the evening empty.
	 */
	private JComponent combatSection()
	{
		combatTabs.setBorder(new EmptyBorder(0, 0, 4, 0));

		sessionTab = combatTab("Session", false,
			"What this sitting has counted, the run in progress included");
		lifetimeTab = combatTab("Lifetime", true,
			"<html>What this character has counted, every sitting added up."
				+ "<br>Added to as each delve is cleared, so it holds what the run in progress has"
				+ "<br>banked rather than what it is part way through earning.</html>");
		combatTabs.select(sessionTab);

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(PanelStyle.BACKGROUND);
		panel.add(combatTabs, BorderLayout.NORTH);
		panel.add(combatPanel, BorderLayout.CENTER);
		return panel;
	}

	/** One of the combat section's tabs, which puts its own tally in the table as it is picked. */
	private MaterialTab combatTab(String name, boolean lifetime, String tooltip)
	{
		MaterialTab tab = new MaterialTab(name, combatTabs, null);
		tab.setToolTipText(tooltip);
		tab.setOnSelectEvent(() ->
		{
			showingLifetime = lifetime;
			drawCombat();
			return true;
		});

		combatTabs.addTab(tab);
		return tab;
	}

	/**
	 * Puts either combat tab in front, for the preview harness - in the plugin nothing but a click
	 * moves them, and the panel opens on the sitting's.
	 *
	 * <p>Idempotent: picking the tab already in front does nothing at all.
	 */
	void showLifetime(boolean lifetime)
	{
		combatTabs.select(lifetime ? lifetimeTab : sessionTab);
	}

	/** @param icons the pictures to draw beside the counters' names - see {@link Icons} */
	void setIcons(Icons icons)
	{
		combatPanel.setIcons(icons);
	}

	/**
	 * @param folded        the combat headings to fold down to their totals, as last left
	 * @param onFoldChanged handed the headings folded down whenever a click changes them, on the
	 *                      Swing thread, so the choice can be kept for next time
	 */
	void setCombatFolding(Set<CombatMetric.Group> folded,
		Consumer<Set<CombatMetric.Group>> onFoldChanged)
	{
		combatPanel.setFolded(folded);
		combatPanel.setFoldListener(onFoldChanged);
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

	/**
	 * Asks before resetting, because the session it throws away cannot be got back - a whole
	 * evening's figures are one stray click from gone otherwise.
	 */
	private void confirmReset(Runnable onReset)
	{
		int answer = JOptionPane.showConfirmDialog(this,
			"Reset the session and drop the run in progress?\n"
				+ "Lifetime figures and milestones are kept.",
			"Reset session", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

		if (answer == JOptionPane.YES_OPTION)
		{
			onReset.run();
		}
	}

	private static JComponent button(String text, String tooltip, Runnable onPress)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.TEXT_COLOR);
		button.setBackground(PanelStyle.CARD);
		button.setBorder(new EmptyBorder(7, 8, 7, 8));
		button.setFocusPainted(false);
		button.setToolTipText(tooltip);
		button.addActionListener(event -> onPress.run());

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
