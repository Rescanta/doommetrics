package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Pushes snapshots from the client thread to the side panel and the run detail window, only when
 * something they draw has changed.
 */
class PanelFeed
{
	private final DoomMetricsPlugin plugin;
	private final DoomMetricsConfig config;
	private final Totals totals;
	private final MilestoneTracker milestones;

	private DoomMetricsPanel panel;

	/** The plugin's icon, for the detail window's taskbar entry. */
	private volatile BufferedImage icon;

	// Swing thread only.
	private RunDetailWindow detailWindow;
	private RunDetail windowDetail = RunDetail.empty();
	private DoomMetricsPanel.Live windowLive;

	// Client thread only: what was last pushed, so an unchanged tick pushes nothing.
	private String lastLiveKey;
	private String lastDetailKey;

	PanelFeed(DoomMetricsPlugin plugin, DoomMetricsConfig config, Totals totals,
		MilestoneTracker milestones)
	{
		this.plugin = plugin;
		this.config = config;
		this.totals = totals;
		this.milestones = milestones;
	}

	/**
	 * Builds the side panel.
	 *
	 * @param onReset invoked on the Swing thread once a session reset has been confirmed
	 */
	DoomMetricsPanel start(BufferedImage icon, Runnable onReset)
	{
		this.icon = icon;
		panel = new DoomMetricsPanel(this::openDetailWindow, onReset);
		panel.setCombatFolding(GroupHeading.parseFolded(config.foldedCombatGroups()),
			folded -> config.foldedCombatGroups(GroupHeading.formatFolded(folded)));
		return panel;
	}

	void stop()
	{
		panel = null;
		icon = null;
		SwingUtilities.invokeLater(this::closeDetailWindow);
	}

	/** Forgets what was pushed, so the next refresh pushes everything. */
	void forget()
	{
		lastLiveKey = null;
		lastDetailKey = null;
	}

	/** Swing thread. */
	void iconsArrived()
	{
		DoomMetricsPanel shown = panel;

		if (shown != null)
		{
			shown.setIcons(plugin.getIcons());
		}

		if (detailWindow != null)
		{
			detailWindow.setIcons(plugin.getIcons());
		}
	}

	/** The overlay reads the setting every frame; the window only when told. */
	void hideEmptyChanged()
	{
		boolean hideEmpty = config.hideEmptyCounters();
		SwingUtilities.invokeLater(() ->
		{
			if (detailWindow != null)
			{
				detailWindow.setHideEmpty(hideEmpty);
			}
		});
	}

	void refreshLive()
	{
		DoomMetricsPanel target = panel;

		if (target == null)
		{
			return;
		}

		refreshDetail();

		DelveRun display = plugin.getDisplayRun();
		DelveRun detail = plugin.getDetailRun();
		DoomMetricsPanel.Live live = display == null
			? null
			: DoomMetricsPanel.Live.of(display, config.paceMode(), plugin.targetDelve(),
				config.targetPrediction());

		// The window keeps drawing a run the overlay's linger has taken down.
		DoomMetricsPanel.Live detailLive = detail == display
			? live
			: (detail == null ? null : DoomMetricsPanel.Live.of(detail, config.paceMode(),
				plugin.targetDelve(), config.targetPrediction()));

		Instant now = Instant.now();
		boolean showSession = totals.sessionShown(plugin.isRunInProgress(), now);
		DoomMetricsPanel.Stats stats = totals.stats(showSession, now);
		String key = (live == null ? "" : live.key())
			+ "|" + stats.key() + "|" + totals.combatKey(showSession)
			+ (detailLive == live ? "" : "|" + (detailLive == null ? "" : detailLive.key()));

		if (key.equals(lastLiveKey))
		{
			return;
		}

		lastLiveKey = key;

		CombatTotals combat = showSession ? totals.sessionCombat() : null;
		CombatTotals lifetimeShown = totals.lifetimeCombat();

		SwingUtilities.invokeLater(() ->
		{
			target.setLive(live);
			target.setStats(stats);
			target.setCombat(combat, lifetimeShown);

			windowLive = detailLive;

			if (detailWindow != null)
			{
				detailWindow.setLive(detailLive);
			}
		});
	}

	void refreshTable()
	{
		DoomMetricsPanel target = panel;

		if (target == null)
		{
			return;
		}

		List<MilestoneTablePanel.Row> rows = milestones.rows();
		SwingUtilities.invokeLater(() -> target.setRows(rows));
	}

	private void refreshDetail()
	{
		DelveRun target = plugin.getDetailRun();
		String key = RunDetail.keyFor(target);

		if (key.equals(lastDetailKey))
		{
			return;
		}

		lastDetailKey = key;

		// Built on the client thread, which owns the run, and immutable once built.
		RunDetail detail = RunDetail.of(target);

		SwingUtilities.invokeLater(() ->
		{
			windowDetail = detail;

			if (detailWindow != null)
			{
				detailWindow.setDetail(detail);
			}
		});
	}

	/** Swing thread. */
	private void openDetailWindow()
	{
		if (detailWindow == null)
		{
			detailWindow = new RunDetailWindow(icon, () -> detailWindow = null);
			detailWindow.setIcons(plugin.getIcons());
			detailWindow.setHideEmpty(config.hideEmptyCounters());
			detailWindow.setFolding(GroupHeading.parseFolded(config.foldedDetailGroups()),
				folded -> config.foldedDetailGroups(GroupHeading.formatFolded(folded)));
			detailWindow.setDetail(windowDetail);
			detailWindow.setLive(windowLive);
		}

		detailWindow.open(SwingUtilities.getWindowAncestor(panel));
	}

	/** Shutdown only; a window the reader closes keeps its data for reopening. */
	private void closeDetailWindow()
	{
		RunDetailWindow window = detailWindow;

		detailWindow = null;
		windowDetail = RunDetail.empty();
		windowLive = null;

		if (window != null)
		{
			window.dispose();
		}
	}
}
