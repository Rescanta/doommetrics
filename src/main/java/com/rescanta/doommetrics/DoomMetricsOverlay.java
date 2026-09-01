package com.rescanta.doommetrics;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class DoomMetricsOverlay extends OverlayPanel
{
	// Held rather than fetched, because values() hands out a fresh copy of the array every call and
	// these are walked several times a frame.
	private static final CombatMetric.Group[] GROUPS = CombatMetric.Group.values();

	private static final CombatMetric[] METRICS = CombatMetric.values();

	private final DoomMetricsPlugin plugin;
	private final DoomMetricsConfig config;

	/** Package-private rather than private so the preview harness can build one without Guice. */
	@Inject
	DoomMetricsOverlay(DoomMetricsPlugin plugin, DoomMetricsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG,
			OverlayManager.OPTION_CONFIGURE, "Doom Metrics overlay"));
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,
			"Clear", "Doom Metrics overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// The other two styles are drawn by the infobox, or not at all. Answered before the run is
		// asked for, so a player who has switched the panel off pays nothing for it every frame.
		if (config.displayStyle() != DisplayStyle.PANEL)
		{
			return null;
		}

		DelveRun run = plugin.getDisplayRun();
		if (run == null)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Doom Metrics")
			.build());

		if (run.isFinished())
		{
			if (run.getEndReason() == EndReason.DIED)
			{
				addLine("Died on", "Delve " + run.getDiedOnLevel());
			}

			if (config.showDelveNumber())
			{
				addLine("Cleared", Integer.toString(run.lastLevel()));
			}
		}
		else if (config.showDelveNumber())
		{
			addLine("Delve", Integer.toString(run.currentLevel()));
		}

		Instant now = Instant.now();

		if (config.showRunTimer())
		{
			// The asterisk marks a run we joined part way through, whose start time is a guess.
			addLine(run.isPartial() ? "Time*" : "Time",
				DoomFormat.duration(run.displayElapsed(now)));
		}

		if (config.showPace())
		{
			PaceMode mode = config.paceMode();
			addLine(mode.toString(), DoomFormat.pace(run.pace(mode)));
		}

		if (config.showTargetDelve())
		{
			int target = config.targetDelve();
			addLine("Target", Integer.toString(target));
			addLine("Predicted",
				DoomFormat.prediction(run.untilTarget(target, now), run.hasReached(target)));
		}

		addCombatLines(run.getCombat());

		return super.render(graphics);
	}

	/**
	 * Draws the counters that have been ticked on, in the order they are declared, so the overlay
	 * reads down in the same order as the side panel's table.
	 *
	 * <p>Every line drawn is one you asked for, so there are no headings over them: a heading you
	 * did not choose, sitting above a single line you did, is a row of overlay spent saying nothing
	 * you had not already been told. What the labels lose by having no heading to qualify them they
	 * make up in saying outright what they count - see {@link CombatMetric#overlayLabel()}.
	 *
	 * <p>A source that has counted nothing still gets its line, dimmed, for the same reason the
	 * table keeps its zero rows: the overlay does not resize under you mid-delve, and a spec you
	 * expected to be firing is visibly not.
	 */
	private void addCombatLines(CombatTotals combat)
	{
		if (config.metricGrouping() == MetricDisplay.SEPARATE)
		{
			for (CombatMetric metric : METRICS)
			{
				if (isShown(metric))
				{
					addAmount(metric.overlayLabel(), combat.get(metric), metric.unit());
				}
			}

			return;
		}

		for (CombatMetric.Group group : GROUPS)
		{
			long total = 0;
			boolean shown = false;

			for (CombatMetric metric : METRICS)
			{
				if (metric.group() == group && isShown(metric))
				{
					total += combat.get(metric);
					shown = true;
				}
			}

			// Only the sources you ticked are in the figure, so a group with none of them ticked
			// has no line rather than a zero: nothing was asked for, so nothing is being answered.
			if (shown)
			{
				addAmount(group.overlayHeading(), total, group.unit());
			}
		}
	}

	private boolean isShown(CombatMetric metric)
	{
		switch (metric)
		{
			case BLOOD_BARRAGE_HEAL:
				return config.showBloodBarrage();

			case OTHER_SPELL_HEAL:
				return config.showOtherSpell();

			case AGS_HEAL:
				return config.showAgsHeal();

			case BLOWPIPE_HEAL:
				return config.showBpHeal();

			case OTHER_SPEC_HEAL:
				return config.showOtherSpecHeal();

			case ELDRITCH_PRAYER:
				return config.showEldritchPrayer();

			case ZCB_DAMAGE:
				return config.showZcbDamage();

			case OTHER_SPEC_DAMAGE:
				return config.showOtherSpecDamage();

			default:
				return false;
		}
	}

	/**
	 * Draws one counter, its figure in the colour of whatever it is counted in, so which lines are
	 * hitpoints, which are prayer and which are damage is legible without reading the labels.
	 *
	 * <p>A zero stays grey rather than taking a faint tint of its unit: a counter that has not
	 * fired is being drawn back deliberately, and the whole point of the colour is that it marks
	 * out a figure worth reading.
	 */
	private void addAmount(String left, long amount, CombatMetric.Unit unit)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(DoomFormat.count(amount))
			.rightColor(amount > 0 ? unit.color() : DoomColors.DIMMED)
			.build());
	}

	private void addLine(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.build());
	}
}
