package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The side panel as a stack of cards: the live run, session beside lifetime, the combat table,
 * milestones, and the detail window and reset buttons. Swing thread only; fed immutable snapshots.
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

		/** The row holding the target, which is drawn as a meter rather than a tile. */
		static final int TARGET_ROW = 3;

		/** Row labels in draw order, a null label meaning that row is switched off. */
		final String[] labels;

		final String[] values;

		/** Whether the run is over, which is what stops the clock rather than merely pausing it. */
		final boolean finished;

		/** Whether it ended in a death, the one outcome worth colouring. */
		final boolean died;

		/** The deepest delve cleared, for the target meter. */
		final int cleared;

		/** The delve being aimed for, or 0 when the target rows are switched off. */
		final int target;

		private Live(String[] labels, String[] values, boolean finished, boolean died, int cleared,
			int target)
		{
			this.labels = labels;
			this.values = values;
			this.finished = finished;
			this.died = died;
			this.cleared = cleared;
			this.target = target;
		}

		/**
		 * The panel's formatted copy of the live rows.
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
				died,
				display.lastLevel(),
				Math.max(0, target));
		}

		/** How far the run is towards its target, from 0 to 1, or 0 with no target. */
		double progress()
		{
			return target <= 0 ? 0 : Math.min(1, (double) cleared / target);
		}

		/** Built from the rows themselves so no row can be left out of it. */
		String key()
		{
			StringBuilder key = new StringBuilder();

			for (int i = 0; i < labels.length; i++)
			{
				key.append(labels[i]).append('|').append(values[i]).append('|');
			}

			return key.append(finished).append('|').append(died)
				.append('|').append(cleared).append('|').append(target).toString();
		}
	}

	/** Session and lifetime figures, formatted. Null reads as {@code "-"}. */
	static final class Stats
	{
		final String sessionLength;
		final String sessionPace;
		final String sessionTooltip;
		final String sessionDeep;
		final String lifetimePace;
		final String lifetimeTooltip;
		final String lifetimeDeep;

		/** The two rates unformatted, for the meters that compare them; null when there is none. */
		final Double sessionKph;
		final Double lifetimeKph;

		Stats(String sessionLength, String sessionPace, String sessionTooltip, String sessionDeep,
			String lifetimePace, String lifetimeTooltip, String lifetimeDeep, Double sessionKph,
			Double lifetimeKph)
		{
			this.sessionLength = sessionLength;
			this.sessionPace = sessionPace;
			this.sessionTooltip = sessionTooltip;
			this.sessionDeep = sessionDeep;
			this.lifetimePace = lifetimePace;
			this.lifetimeTooltip = lifetimeTooltip;
			this.lifetimeDeep = lifetimeDeep;
			this.sessionKph = sessionKph;
			this.lifetimeKph = lifetimeKph;
		}

		/** Enough of the snapshot to tell one repaint from the next. */
		String key()
		{
			return sessionLength + "|" + sessionPace + "|" + sessionDeep
				+ "|" + lifetimePace + "|" + lifetimeDeep;
		}
	}

	/** A run in progress, in the status pill. */
	private static final Color LIVE_COLOR = DoomColors.LIVE;

	/** The lifetime meter: the yardstick, so it stays out of the accent the session wears. */
	private static final Color LIFETIME_METER = new Color(120, 120, 120);

	private final JPanel runLower = PanelStyle.column(PanelStyle.GRID * 2);
	private final CombatTablePanel combatPanel = new CombatTablePanel();

	/** The two tallies the combat table can draw, the tab in front deciding which it does. */
	private CombatTotals sessionCombat;
	private CombatTotals lifetimeCombat;

	/** Whether the tab in front is the lifetime one. */
	private boolean showingLifetime;

	private final MaterialTabGroup combatTabs = new MaterialTabGroup();

	/** The two tabs, kept so {@link #showLifetime} can put either in front. */
	private final MaterialTab sessionTab = PanelStyle.toggle(combatTabs, "Session",
		"What this sitting has counted, the run in progress included",
		() -> showCombat(false));
	private final MaterialTab lifetimeTab = PanelStyle.toggle(combatTabs, "Lifetime",
		"<html>What this character has counted, every sitting added up."
			+ "<br>Added to as each delve is cleared, so it holds what the run in progress has"
			+ "<br>banked rather than what it is part way through earning.</html>",
		() -> showCombat(true));

	private final MilestoneTablePanel tablePanel = new MilestoneTablePanel("No delves completed yet.");

	private final StatusPill status = new StatusPill();

	private final JLabel idleLabel = PanelStyle.caption("No run in progress", SwingConstants.LEFT);

	private final JLabel[] heroCaptions = {
		PanelStyle.caption("Delve", SwingConstants.LEFT),
		PanelStyle.caption("Time", SwingConstants.RIGHT),
	};

	private final JLabel[] heroValues = {
		PanelStyle.hero("-", SwingConstants.LEFT),
		PanelStyle.hero("-", SwingConstants.RIGHT),
	};

	/** The target row, drawn as a meter rather than a tile. */
	private final TargetProgress target = new TargetProgress(5);

	/** A tile per row under the heroes, built once and retexted; the target row has none. */
	private final JLabel[] tileCaptions = new JLabel[Live.ROWS];
	private final PanelStyle.Figure[] tileValues = new PanelStyle.Figure[Live.ROWS];
	private final JPanel[] tiles = new JPanel[Live.ROWS];

	private final JLabel sessionLength = PanelStyle.body("-", SwingConstants.RIGHT);
	private final PanelStyle.Figure sessionPace = PanelStyle.heroFigure(SwingConstants.LEFT);
	private final PanelStyle.Figure lifetimePace = PanelStyle.heroFigure(SwingConstants.LEFT);
	private final JLabel sessionDeep = PanelStyle.caption("-", SwingConstants.LEFT);
	private final JLabel lifetimeDeep = PanelStyle.caption("-", SwingConstants.LEFT);
	private final Meter sessionMeter = new Meter(PanelStyle.ACCENT, PanelStyle.METER_HEIGHT);
	private final Meter lifetimeMeter = new Meter(LIFETIME_METER, PanelStyle.METER_HEIGHT);

	/** The resets card: its title names the milestone, which follows the target delve. */
	private final JLabel resetsTitle = PanelStyle.title("Resets");
	private final PanelStyle.Figure reachRate = PanelStyle.statFigure(SwingConstants.LEFT);
	private final JLabel reachCount = PanelStyle.caption("-", SwingConstants.LEFT);
	private final JLabel averageTime = PanelStyle.stat("-", SwingConstants.LEFT);
	private final JLabel bestTime = PanelStyle.caption("-", SwingConstants.LEFT);
	private final JLabel sessionResets = PanelStyle.body("-", SwingConstants.RIGHT);
	private final JLabel recentTime = PanelStyle.body("-", SwingConstants.RIGHT);
	private final JLabel recentCaption = PanelStyle.caption("Last 10", SwingConstants.LEFT);
	private final JLabel resetsEmpty = PanelStyle.caption("<html><body style='width:150px'>"
		+ "Nothing counted yet. Runs and times fill in from your next run.</body></html>",
		SwingConstants.LEFT);
	private final JPanel resetsBody = PanelStyle.column(PanelStyle.ROW_GAP);
	private final JPanel resetsRows = PanelStyle.column(PanelStyle.ROW_GAP);
	private final JPanel resetsTiles = new JPanel(new GridLayout(1, 2, PanelStyle.GRID, 0));

	/** Each heading's total, large, at the head of the combat card. */
	private final JLabel[] unitTotals = new JLabel[CombatMetric.Group.values().length];

	DoomMetricsPanel(Runnable onOpenDetail, Runnable onReset)
	{
		setBackground(PanelStyle.BACKGROUND);
		setLayout(new DynamicGridLayout(0, 1, 0, PanelStyle.SECTION_GAP));

		add(PanelStyle.section("Current run", status, runCard()));
		// The sitting and the character's lifetime above the tables: what is being earned right
		// now is what a player glances at mid-run, and the rest is what they scroll to.
		add(PanelStyle.section("Session & lifetime", compare()));
		add(PanelStyle.section("Combat", combatTabs, combatCard()));
		add(PanelStyle.section(resetsTitle, null, resetsCard()));
		add(PanelStyle.section("Milestones", tablePanel));
		add(buttons(onOpenDetail, onReset));

		setLive(null);
		setStats(null);
		setCombat(null, null);
		setRows(Collections.emptyList());
		setResets(null, null);

		combatTabs.setOpaque(false);
		combatTabs.select(sessionTab);
	}

	/** Repaints the sitting's and the character's figures. A null snapshot blanks all of them. */
	void setStats(Stats stats)
	{
		apply(sessionLength, stats == null ? null : stats.sessionLength);
		apply(sessionPace, stats == null ? null : stats.sessionPace,
			stats == null ? null : stats.sessionTooltip);
		apply(lifetimePace, stats == null ? null : stats.lifetimePace,
			stats == null ? null : stats.lifetimeTooltip);
		sessionDeep.setText(deep(stats == null ? null : stats.sessionDeep));
		lifetimeDeep.setText(deep(stats == null ? null : stats.lifetimeDeep));

		// Both against the faster, so the longer bar answers "is tonight better than usual".
		double session = stats == null || stats.sessionKph == null ? 0 : stats.sessionKph;
		double lifetime = stats == null || stats.lifetimeKph == null ? 0 : stats.lifetimeKph;
		double scale = Math.max(session, lifetime);
		sessionMeter.setFill(scale > 0 ? session / scale : 0);
		lifetimeMeter.setFill(scale > 0 ? lifetime / scale : 0);
	}

	/** Short, so a lifetime's five figures still fit half a card. */
	private static String deep(String count)
	{
		return (count == null ? "0" : count) + " deep";
	}

	private static void apply(JLabel label, String value)
	{
		label.setText(value == null ? "-" : value);
		label.setForeground(value == null ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.TEXT_COLOR);
	}

	/** Retexts one rate. A null tooltip takes the last one down, so a blank rate explains nothing. */
	private static void apply(PanelStyle.Figure figure, String value, String tooltip)
	{
		figure.setText(value == null ? "-" : value);
		figure.setForeground(value == null || "-".equals(value)
			? DoomColors.DIMMED
			: DoomColors.PLAIN);
		figure.setToolTipText(tooltip);
	}

	/** A null snapshot blanks the heroes and collapses the rest of the card to one idle line. */
	void setLive(Live live)
	{
		runLower.removeAll();
		status.show(live);

		if (live == null)
		{
			for (int i = 0; i < Live.HERO_ROWS; i++)
			{
				heroValues[i].setText("-");
				heroValues[i].setForeground(DoomColors.DIMMED);
			}

			heroCaptions[0].setText("Delve");
			heroCaptions[1].setText("Time");
			runLower.add(idleLabel);
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

			if (live.labels[Live.TARGET_ROW] != null)
			{
				target.show(live);
				runLower.add(target);
			}

			JPanel row = tileRow(live);

			if (row != null)
			{
				runLower.add(row);
			}
		}

		runLower.revalidate();
		runLower.repaint();
	}

	/** The rows under the heroes as tiles side by side, or null when every one is switched off. */
	private JPanel tileRow(Live live)
	{
		int count = 0;

		for (int i = Live.HERO_ROWS; i < Live.ROWS; i++)
		{
			if (i != Live.TARGET_ROW && live.labels[i] != null)
			{
				count++;
			}
		}

		if (count == 0)
		{
			return null;
		}

		JPanel row = new JPanel(new GridLayout(1, count, PanelStyle.GRID, 0));
		row.setOpaque(false);

		for (int i = Live.HERO_ROWS; i < Live.ROWS; i++)
		{
			if (i == Live.TARGET_ROW || live.labels[i] == null)
			{
				continue;
			}

			tileCaptions[i].setText(live.labels[i]);
			tileValues[i].setText(live.values[i]);
			tileValues[i].setForeground("-".equals(live.values[i])
				? DoomColors.DIMMED
				: DoomColors.PLAIN);
			row.add(tiles[i]);
		}

		return row;
	}

	/** A null session tally reads as zeroes. */
	void setCombat(CombatTotals session, CombatTotals lifetime)
	{
		sessionCombat = session;
		lifetimeCombat = lifetime;
		drawCombat();
	}

	private void showCombat(boolean lifetime)
	{
		showingLifetime = lifetime;
		drawCombat();
	}

	/** Draws whichever of the two tallies the tab in front is for. */
	private void drawCombat()
	{
		CombatTotals shown = showingLifetime ? lifetimeCombat : sessionCombat;
		combatPanel.setTotals(shown);

		CombatTotals counted = shown == null ? new CombatTotals() : shown;

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			long amount = group.amount(counted);
			JLabel total = unitTotals[group.ordinal()];
			total.setText(PanelStyle.tileFigure(amount));
			total.setForeground(amount > 0 ? group.unit().color() : ColorScheme.LIGHT_GRAY_COLOR);
			total.setToolTipText(group.tooltip(counted));
		}
	}

	/** For the preview harness. Idempotent. */
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
	 * @param onFoldChanged handed the folded headings whenever a click changes them, on the Swing
	 * thread
	 */
	void setCombatFolding(Set<CombatMetric.Group> folded,
		Consumer<Set<CombatMetric.Group>> onFoldChanged)
	{
		combatPanel.setFolded(folded);
		combatPanel.setFoldListener(onFoldChanged);
	}

	/**
	 * Repaints the resets card.
	 *
	 * @param summary        how runs aimed at the target have gone, or null for none yet
	 * @param sessionPerHour targets cleared per hour this sitting, or null while there is no rate
	 */
	void setResets(ResetSummary summary, Double sessionPerHour)
	{
		resetsTitle.setText(summary == null ? "Resets" : "Delve " + summary.target + " resets");
		resetsBody.removeAll();

		if (summary == null || summary.runs == 0)
		{
			resetsBody.add(resetsEmpty);
			resetsBody.revalidate();
			resetsBody.repaint();
			return;
		}

		double rate = summary.reachRate();
		reachRate.setText(Math.round(rate * 100) + "%");
		reachRate.setForeground(DoomColors.PLAIN);
		reachCount.setText(DoomFormat.count(summary.reached) + " / "
			+ DoomFormat.count(summary.runs) + " runs");

		averageTime.setText(DoomFormat.tickDuration(summary.averageTicks));
		averageTime.setForeground(summary.averageTicks > 0 ? DoomColors.PLAIN : DoomColors.DIMMED);
		bestTime.setText(summary.bestTicks > 0
			? "best " + DoomFormat.tickDuration(summary.bestTicks)
			: "no time yet");

		apply(sessionResets, DoomFormat.count(summary.sessionResets)
			+ (sessionPerHour == null ? "" : " (" + DoomFormat.pace(sessionPerHour) + ")"));

		recentCaption.setText("Last " + Math.max(1, summary.recentCount));
		recentTime.setText(recent(summary));
		recentTime.setForeground(recentColor(summary));

		resetsBody.add(resetsTiles);
		resetsBody.add(resetsRows);
		resetsBody.revalidate();
		resetsBody.repaint();
	}

	/** The latest times' average, and how far it is off the whole average, or - for none. */
	private static String recent(ResetSummary summary)
	{
		if (summary.recentCount == 0)
		{
			return "-";
		}

		String average = DoomFormat.tickDuration(summary.recentTicks);

		if (summary.averageTicks <= 0 || summary.recentCount < 2)
		{
			return average;
		}

		int difference = summary.recentTicks - summary.averageTicks;
		return difference == 0
			? average
			: average + " (" + (difference < 0 ? "-" : "+")
				+ DoomFormat.tickDuration(Math.abs(difference)) + ")";
	}

	/** Green when the latest runs are quicker than usual, red when slower. */
	private static Color recentColor(ResetSummary summary)
	{
		if (summary.recentCount < 2 || summary.averageTicks <= 0
			|| summary.recentTicks == summary.averageTicks)
		{
			return summary.recentCount == 0 ? DoomColors.DIMMED : ColorScheme.TEXT_COLOR;
		}

		return summary.recentTicks < summary.averageTicks
			? DoomColors.LIVE
			: ColorScheme.PROGRESS_ERROR_COLOR;
	}

	/** The reach rate and the average time as tiles, then a row each for the rest. */
	private JPanel resetsCard()
	{
		JLabel reachCaption = PanelStyle.caption("Reached", SwingConstants.LEFT);
		JLabel averageCaption = PanelStyle.caption("Average time", SwingConstants.LEFT);

		JPanel reach = new JPanel(new BorderLayout());
		reach.setOpaque(false);
		reach.add(reachRate, BorderLayout.NORTH);
		reach.add(reachCount, BorderLayout.CENTER);

		JPanel average = new JPanel(new BorderLayout());
		average.setOpaque(false);
		average.add(averageTime, BorderLayout.NORTH);
		average.add(bestTime, BorderLayout.CENTER);

		JPanel reachTile = PanelStyle.tile(reachCaption, reach);
		reachTile.setToolTipText("Runs that cleared the target, out of every run started");
		JPanel averageTile = PanelStyle.tile(averageCaption, average);
		averageTile.setToolTipText("From the run's start to clearing the target, restocking included");

		resetsTiles.setOpaque(false);
		resetsTiles.add(reachTile);
		resetsTiles.add(averageTile);

		JLabel session = PanelStyle.caption("This session", SwingConstants.LEFT);
		session.setToolTipText("Targets cleared this sitting, and how many that is an hour");
		recentCaption.setToolTipText("The latest runs' average time to the target, and how far it"
			+ " is off the whole average");

		resetsRows.setOpaque(false);
		resetsRows.setBorder(new EmptyBorder(PanelStyle.GRID, 2, 0, 2));
		resetsRows.add(line(session, sessionResets));
		resetsRows.add(line(recentCaption, recentTime));

		resetsBody.setOpaque(false);
		resetsBody.setToolTipText("Counted since this version of the plugin; the target delve is"
			+ " rounded down to a milestone");
		return resetsBody;
	}

	/** A name on the left and its figure on the right. */
	private static JPanel line(JLabel left, JLabel right)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(left, BorderLayout.WEST);
		panel.add(right, BorderLayout.EAST);
		return panel;
	}

	/** @param hideEmpty whether counters at 0 are left out of the combat table until shown */
	void setHideEmpty(boolean hideEmpty)
	{
		combatPanel.setHideEmpty(hideEmpty);
	}

	/** Rebuilds the milestone table. Called only when a row actually changed. */
	void setRows(List<MilestoneTablePanel.Row> rows)
	{
		tablePanel.setRows(rows);
	}

	/** The delve and the clock large, the target's meter, then the rest as tiles. */
	private JPanel runCard()
	{
		JPanel hero = new JPanel(new GridLayout(1, 2, PanelStyle.GRID * 2, 0));
		hero.setOpaque(false);

		// The clock is right-aligned so its digits stay put as it grows.
		for (int i = 0; i < Live.HERO_ROWS; i++)
		{
			JPanel cell = new JPanel(new BorderLayout());
			cell.setOpaque(false);
			cell.add(heroCaptions[i], BorderLayout.NORTH);
			cell.add(heroValues[i], BorderLayout.CENTER);
			hero.add(cell);
		}

		for (int i = Live.HERO_ROWS; i < Live.ROWS; i++)
		{
			tileCaptions[i] = PanelStyle.caption("", SwingConstants.LEFT);
			tileValues[i] = PanelStyle.statFigure(SwingConstants.LEFT);
			tiles[i] = PanelStyle.tile(tileCaptions[i], tileValues[i]);
		}

		runLower.setOpaque(false);

		JPanel card = new JPanel(new BorderLayout(0, PanelStyle.GRID * 2));
		card.setOpaque(false);
		card.add(hero, BorderLayout.NORTH);
		card.add(runLower, BorderLayout.CENTER);
		return card;
	}

	/** Session beside lifetime, a tile each, with the sitting's length under them. */
	private JPanel compare()
	{
		JLabel length = PanelStyle.caption("Sitting length", SwingConstants.LEFT);
		length.setToolTipText("How long this sitting has been going, from its first run to now");
		sessionLength.setToolTipText(length.getToolTipText());
		sessionDeep.setToolTipText("Deep delves completed this sitting");
		lifetimeDeep.setToolTipText("Deep delves completed by this character");

		JPanel tiles = new JPanel(new GridLayout(1, 2, PanelStyle.GRID, 0));
		tiles.setOpaque(false);
		tiles.add(paceTile("Session pace", sessionPace, sessionMeter, sessionDeep));
		tiles.add(paceTile("Lifetime pace", lifetimePace, lifetimeMeter, lifetimeDeep));

		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(new EmptyBorder(0, 2, 0, 2));
		footer.add(length, BorderLayout.WEST);
		footer.add(sessionLength, BorderLayout.EAST);

		JPanel panel = new JPanel(new BorderLayout(0, PanelStyle.GRID * 2));
		panel.setOpaque(false);
		panel.add(tiles, BorderLayout.CENTER);
		panel.add(footer, BorderLayout.SOUTH);
		return panel;
	}

	/** One of the two rates, large, with its meter and the count it is built on under it. */
	private static JPanel paceTile(String caption, PanelStyle.Figure pace, Meter meter,
		JLabel deep)
	{
		JPanel under = new JPanel(new BorderLayout(0, PanelStyle.GRID));
		under.setOpaque(false);
		under.setBorder(new EmptyBorder(2, 0, 0, 0));
		under.add(meter, BorderLayout.NORTH);
		under.add(deep, BorderLayout.CENTER);

		JPanel value = new JPanel(new BorderLayout());
		value.setOpaque(false);
		value.add(pace, BorderLayout.NORTH);
		value.add(under, BorderLayout.CENTER);

		return PanelStyle.tile(PanelStyle.caption(caption, SwingConstants.LEFT), value);
	}

	/** Each heading's total as a tile, then the table the Session and Lifetime segments switch. */
	private JComponent combatCard()
	{
		JPanel totals = new JPanel(new GridLayout(1, unitTotals.length, PanelStyle.GRID, 0));
		totals.setOpaque(false);

		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			JLabel value = PanelStyle.stat("0", SwingConstants.LEFT);
			unitTotals[group.ordinal()] = value;
			totals.add(PanelStyle.tile(
				PanelStyle.caption(group.overlayHeading(), SwingConstants.LEFT), value));
		}

		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setOpaque(false);
		panel.add(totals, BorderLayout.NORTH);
		panel.add(combatPanel, BorderLayout.CENTER);
		return panel;
	}

	/** The way into the detail window, and the reset under it. */
	private JPanel buttons(Runnable onOpenDetail, Runnable onReset)
	{
		JPanel panel = new JPanel(new DynamicGridLayout(0, 1, 0, PanelStyle.GRID * 2));
		panel.setBackground(PanelStyle.BACKGROUND);
		panel.add(new CardButton("Open run detail",
			"Break this run down delve by delve, in a window of its own", onOpenDetail));
		panel.add(new CardButton("Reset session",
			"Start the session over and drop the run in progress. Lifetime figures are kept.",
			() -> confirmReset(onReset)));
		return panel;
	}

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

	/** Where the run stands, in a word, on the end of the card's title line. */
	static final class StatusPill extends RoundedPanel
	{
		private final JLabel text = PanelStyle.caption("", SwingConstants.CENTER);

		StatusPill()
		{
			super(PanelStyle.TILE, PanelStyle.ARC);
			setLayout(new BorderLayout());
			setBorder(new EmptyBorder(0, 6, 0, 6));
			add(text, BorderLayout.CENTER);
		}

		/** @param live the run on show, or null for none */
		void show(Live live)
		{
			Color color;
			String word;

			if (live == null)
			{
				word = "Idle";
				color = ColorScheme.LIGHT_GRAY_COLOR;
			}
			else if (live.died)
			{
				word = "Died";
				color = ColorScheme.PROGRESS_ERROR_COLOR;
			}
			else if (live.finished)
			{
				word = "Ended";
				color = ColorScheme.LIGHT_GRAY_COLOR;
			}
			else
			{
				word = "Live";
				color = LIVE_COLOR;
			}

			text.setText(word);
			text.setForeground(color);
			setFill(live == null || (live.finished && !live.died)
				? PanelStyle.TILE
				: PanelStyle.alpha(color, 40));
		}
	}
}
