package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.time.Instant;
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
 * The side panel: the run in progress on top, the sitting under it, the character's lifetime under
 * that, then the lifetime milestone table and a button that opens the history window.
 *
 * <p>The sitting and the lifetime are kept apart rather than interleaved, because they answer
 * different questions - how this evening is going, and what the character has done over all of
 * them - and a reader comparing the two wants each under a heading that says which it is.
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

		/** Row labels in draw order, a null label meaning that row is switched off. */
		final String[] labels;

		final String[] values;

		private Live(String[] labels, String[] values)
		{
			this.labels = labels;
			this.values = values;
		}

		/**
		 * The live rows of a run, formatted for drawing. The overlay draws the same figures itself
		 * from the run; this is the panel's copy of them.
		 *
		 * @param target the delve being aimed for, or 0 when the target rows are switched off
		 */
		static Live of(DelveRun display, PaceMode mode, int target)
		{
			String delveLabel;
			String delveValue;

			if (!display.isFinished())
			{
				delveLabel = "Delve";
				delveValue = Integer.toString(display.currentLevel());
			}
			else if (display.getEndReason() == EndReason.DIED)
			{
				delveLabel = "Died on";
				delveValue = "Delve " + display.getDiedOnLevel();
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
				});
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

			return key.toString();
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

	private final JPanel livePanel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final JPanel sessionPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JPanel lifetimePanel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final CombatTablePanel combatPanel = new CombatTablePanel();
	private final MilestoneTablePanel tablePanel = new MilestoneTablePanel("Nothing banked yet.");

	private final JLabel idleLabel = plain("No run in progress");
	private final JLabel[] liveLeft = labels(SwingConstants.LEFT);
	private final JLabel[] liveRight = labels(SwingConstants.RIGHT);

	private final JLabel sessionLength = right("-");
	private final JLabel sessionPace = right("-");
	private final JLabel sessionDeep = right("-");
	private final JLabel lifetimePace = right("-");
	private final JLabel lifetimeDeep = right("-");

	/** @param onOpenHistory invoked on the Swing thread when the history button is pressed */
	DoomMetricsPanel(Runnable onOpenHistory)
	{
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new DynamicGridLayout(0, 1, 0, 10));

		livePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Built once and only ever retexted, unlike the live section - these are always the same
		// rows, so there is nothing for a rebuild to change.
		sessionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sessionPanel.add(rows(
			row("Length", sessionLength,
				"How long this sitting has been going, from its first run to now"),
			row("Deep pace", sessionPace, null),
			row("Deep delves", sessionDeep, "Delves cleared this sitting at or past the deep "
				+ "level, the run in progress included")));
		sessionPanel.add(combatPanel);

		lifetimePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		lifetimePanel.add(row("Deep pace", lifetimePace, null));
		lifetimePanel.add(row("Deep delves", lifetimeDeep,
			"Every delve this character has cleared at or past the deep level"));

		add(section("Current run", livePanel));
		// The sitting above the character's lifetime, and the milestone table below both: what is
		// being earned right now is what a player glances at mid-run, and what they have earned
		// over a lifetime is what they scroll to.
		add(section("This session", sessionPanel));
		add(section("Lifetime", lifetimePanel));
		add(section("Milestones", tablePanel));
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

		if (tooltip != null)
		{
			label.setToolTipText(tooltip);
		}
	}

	/** One reusable widget per live row, retexted rather than rebuilt as the figures move. */
	private static JLabel[] labels(int alignment)
	{
		JLabel[] labels = new JLabel[Live.ROWS];

		for (int i = 0; i < labels.length; i++)
		{
			labels[i] = label("", alignment);
		}

		return labels;
	}

	/** Repaints the live figures. A null snapshot collapses the section to one idle line. */
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
			for (int i = 0; i < live.labels.length; i++)
			{
				if (live.labels[i] == null)
				{
					continue;
				}

				liveLeft[i].setText(live.labels[i]);
				liveLeft[i].setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				liveRight[i].setText(live.values[i]);
				liveRight[i].setForeground(ColorScheme.TEXT_COLOR);
				livePanel.add(pair(liveLeft[i], liveRight[i], ColorScheme.DARK_GRAY_COLOR));
			}
		}

		livePanel.revalidate();
		livePanel.repaint();
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

	/** Several rows stacked tight, for a block that shares a section with a table below it. */
	private static JPanel rows(JPanel... content)
	{
		JPanel panel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (JPanel row : content)
		{
			panel.add(row);
		}

		return panel;
	}

	private static JPanel row(String text, JLabel value, String tooltip)
	{
		JLabel label = plain(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setToolTipText(tooltip);
		return pair(label, value, ColorScheme.DARK_GRAY_COLOR);
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
