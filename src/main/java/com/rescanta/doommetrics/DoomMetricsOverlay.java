package com.rescanta.doommetrics;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class DoomMetricsOverlay extends OverlayPanel
{
	// Held rather than fetched, because values() hands out a fresh copy of the array every call and
	// these are walked several times a frame.
	private static final CombatMetric.Group[] GROUPS = CombatMetric.Group.values();

	private static final CombatMetric[] METRICS =
		CombatMetric.DISPLAYED.toArray(new CombatMetric[0]);

	/** Between a counter's icon and the rest of its row, when it is drawn with one. */
	private static final Point ICON_GAP = new Point(4, 0);

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

		if (!config.hidePluginName())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Doom Metrics")
				.build());
		}

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
			PaceMode mode = run.paceMode(config.paceMode());
			addLine(mode.toString(), DoomFormat.pace(run.pace(mode)));
		}

		if (config.showTargetDelve())
		{
			int target = config.targetDelve();
			TargetPrediction prediction = config.targetPrediction();
			addLine(TargetPrediction.targetLabel(run, target), Integer.toString(target));

			String remaining = prediction.remainingLabel(run, target);

			if (remaining != null)
			{
				addLine(remaining, TargetPrediction.remainingValue(run, target, now));
			}

			String total = prediction.totalLabel(run);

			if (total != null)
			{
				addLine(total, TargetPrediction.totalValue(run, target, now));
			}
		}

		addCombatLines(run, now);

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
	 * <p>A source that has counted nothing is left off by default, so everything your gear might
	 * use can be ticked without the overlay filling with zeros. With that switched off it gets its
	 * line, dimmed, for the same reason the table keeps its zero rows: the overlay does not resize
	 * under you mid-delve, and a spec you expected to be firing is visibly not.
	 *
	 * <p>A line that has just gained reads as the gain for a few seconds - {@code +97} - before it
	 * goes back to the run's total. See {@link RecentGains}.
	 *
	 * <p>With icons switched on, each line is led by the icon of what it counts in place of its
	 * name. A group's line keeps its heading: it sums several sources, and no one icon stands for
	 * all of them.
	 *
	 * <p>A group's line also carries what the counters listed here cannot: the sources with no tick
	 * of their own, such as a melee weapon punishing without a counter to name it. Separate lines
	 * leave those out, having nowhere to put them.
	 */
	private void addCombatLines(DelveRun run, Instant now)
	{
		CombatTotals combat = run.getCombat();
		boolean hideEmpty = config.hideEmptyCounters();

		if (config.metricGrouping() == MetricDisplay.SEPARATE)
		{
			Icons icons = config.counterIcons() ? plugin.getIcons() : Icons.NONE;

			for (CombatMetric metric : METRICS)
			{
				if (isShown(metric) && !(hideEmpty && combat.get(metric) == 0))
				{
					addAmount(metric.overlayLabel(), icons.smallCounter(metric), combat.get(metric),
						run.recentGain(metric, now), metric.unit());
				}
			}

			return;
		}

		for (CombatMetric.Group group : GROUPS)
		{
			boolean shown = false;

			for (CombatMetric metric : METRICS)
			{
				shown |= metric.group() == group && isShown(metric);
			}

			// A group with none of its sources ticked has no line rather than a zero: nothing was
			// asked for, so nothing is being answered.
			if (!shown)
			{
				continue;
			}

			long total = 0;
			long recent = 0;

			for (CombatMetric metric : group.metrics())
			{
				// The sources you ticked, and the catch-alls, which have no tick to be given: a
				// punish line that left them out would be missing every weapon without a counter
				// of its own - an ancient godsword above all - and would read lower than the
				// damage the run actually punished for with no way to tell why.
				if (metric.displayed() && !isShown(metric))
				{
					continue;
				}

				total += combat.get(metric);
				recent += run.recentGain(metric, now);
			}

			if (!(hideEmpty && total == 0))
			{
				addAmount(group.overlayHeading(), null, total, recent, group.unit());
			}
		}
	}

	private boolean isShown(CombatMetric metric)
	{
		switch (metric)
		{
			case BLOOD_BARRAGE_HEAL:
				return config.showBloodBarrage();

			case AGS_HEAL:
				return config.showAgsHeal();

			case BLOWPIPE_HEAL:
				return config.showBpHeal();

			case ELDRITCH_PRAYER:
				return config.showEldritchPrayer();

			case ZCB_DAMAGE:
				return config.showZcbDamage();

			case SCYTHE_PUNISH:
				return config.showScythePunish();

			case NOXIOUS_HALBERD_PUNISH:
				return config.showNoxiousHalberdPunish();

			case CRYSTAL_HALBERD_PUNISH:
				return config.showCrystalHalberdPunish();

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
	 *
	 * @param icon   drawn in place of {@code left} when there is one, or null for the words
	 * @param recent what the counter has just gained, drawn in place of the total while it is
	 *               more than nothing
	 */
	private void addAmount(String left, BufferedImage icon, long amount, long recent,
		CombatMetric.Unit unit)
	{
		LineComponent line = LineComponent.builder()
			.left(icon == null ? left : "")
			.right(recent > 0 ? "+" + DoomFormat.count(recent) : DoomFormat.count(amount))
			.rightColor(amount > 0 ? unit.color() : DoomColors.DIMMED)
			.build();

		if (icon == null)
		{
			panelComponent.getChildren().add(line);
			return;
		}

		panelComponent.getChildren().add(SplitComponent.builder()
			.first(new ImageComponent(icon))
			.second(line)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(ICON_GAP)
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
