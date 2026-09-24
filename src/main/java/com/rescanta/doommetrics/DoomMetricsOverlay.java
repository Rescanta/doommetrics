package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;

/**
 * The run drawn over the game as one of its own panels: a stone frame, an orange title over an
 * ember line, names in orange and figures in white, counters in their unit's colour.
 */
class DoomMetricsOverlay extends OverlayPanel
{
	// Held rather than fetched, because values() hands out a fresh copy of the array every call and
	// these are walked several times a frame.
	private static final CombatMetric.Group[] GROUPS = CombatMetric.Group.values();

	/** Between a counter's icon and the rest of its row, when it is drawn with one. */
	private static final Point ICON_GAP = new Point(4, 0);

	/** How many counters share a line under {@link CounterStyle#ICON_GRID}. */
	static final int GRID_COLUMNS = 2;

	/** The space between two counters sharing a line. */
	static final int GRID_GAP = 6;

	/** The most characters a figure keeps whole in a grid column - see {@link #figure}. */
	private static final int NARROW_FIGURE = 5;

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

		List<LayoutableRenderableEntity> children = panelComponent.getChildren();

		if (!config.hidePluginName())
		{
			children.add(new OverlayChrome.Title("Doom Metrics"));
		}

		int heading = children.size();

		if (run.isFinished())
		{
			if (run.getEndReason() == EndReason.DIED)
			{
				addLine("Died on", "Delve " + run.getDiedOnLevel(), DoomColors.DEATH);
			}

			if (config.showDelveNumber())
			{
				addLine("Cleared", Integer.toString(run.lastLevel()));
			}
		}
		else if (config.showDelveNumber())
		{
			// In the game's yellow, as it writes a level: the one figure read mid-fight.
			addLine("Delve", Integer.toString(run.currentLevel()), DoomColors.YELLOW);
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

			// Under the target's lines rather than between them, so it closes the run's block.
			children.add(new OverlayChrome.Progress((double) run.lastLevel() / target));
		}

		int counters = children.size();
		addCombatLines(run, now);

		// A rule between the run and its counters, when there are both.
		if (counters > heading && children.size() > counters)
		{
			children.add(counters, new OverlayChrome.Rule());
		}

		Dimension size = super.render(graphics);
		OverlayChrome.frame(graphics, size);
		return size;
	}

	/**
	 * Draws each heading as configured - nothing, one total line, or a line per counter - in the
	 * side panel's order. Empty lines are dimmed or hidden, and a fresh gain shows as {@code +97}
	 * for a few seconds.
	 */
	private void addCombatLines(DelveRun run, Instant now)
	{
		CombatTotals combat = run.getCombat();
		boolean hideEmpty = config.hideEmptyCounters();
		CounterStyle style = config.counterStyle();
		Icons icons = style.icons() ? plugin.getIcons() : Icons.NONE;
		List<LayoutableRenderableEntity> row = new ArrayList<>(GRID_COLUMNS);

		for (CombatMetric.Group group : GROUPS)
		{
			CounterMode mode = CounterMode.of(config, group);

			if (mode == CounterMode.TOTAL)
			{
				long total = group.amount(combat);

				if (!(hideEmpty && total == 0))
				{
					long recent = 0;

					for (CombatMetric metric : group.metrics())
					{
						recent += run.recentGain(metric, now);
					}

					// A total has a line to itself even in a grid: its name will not fit in half
					// of one. Its unit's skill icon goes before the name, not in place of it.
					flush(row);
					panelComponent.getChildren().add(amount(group.overlayHeading(),
						icons.smallUnit(group.unit()), true, total, recent, group.unit(), false));
				}
			}
			else if (mode == CounterMode.EACH)
			{
				for (CombatMetric metric : group.metrics())
				{
					long amount = combat.get(metric);

					if (!metric.displayed() || (hideEmpty && amount == 0))
					{
						continue;
					}

					BufferedImage icon = icons.smallCounter(metric);

					// A counter whose picture has not arrived yet is drawn by name, and a name
					// needs the whole width, as a total does.
					boolean gridded = style == CounterStyle.ICON_GRID && icon != null;
					LayoutableRenderableEntity line = amount(metric.overlayLabel(), icon, false,
						amount, run.recentGain(metric, now), metric.unit(), gridded);

					if (!gridded)
					{
						flush(row);
						panelComponent.getChildren().add(line);
						continue;
					}

					row.add(line);

					if (row.size() == GRID_COLUMNS)
					{
						flush(row);
					}
				}
			}
		}

		flush(row);
	}

	/** Puts up the counters waiting to share a line, if any are, and starts the next line empty. */
	private void flush(List<LayoutableRenderableEntity> row)
	{
		if (row.isEmpty())
		{
			return;
		}

		panelComponent.getChildren().add(
			new OverlayColumns(new ArrayList<>(row), GRID_COLUMNS, GRID_GAP));
		row.clear();
	}

	/**
	 * One counter's line, its figure in its unit's colour; a zero stays grey.
	 *
	 * @param icon     drawn before {@code left} when there is one, or null for the words alone
	 * @param keepName whether the words stay beside the icon rather than giving way to it
	 * @param recent   what the counter has just gained, shown instead of the total while above 0
	 * @param narrow   whether the line is one column of a grid - see {@link #figure}
	 */
	private static LayoutableRenderableEntity amount(String left, BufferedImage icon,
		boolean keepName, long amount, long recent, CombatMetric.Unit unit, boolean narrow)
	{
		LineComponent line = LineComponent.builder()
			.left(icon == null || keepName ? left : "")
			.leftColor(DoomColors.ORANGE)
			.right(figure(amount, recent, narrow))
			.rightColor(amount > 0 ? unit.color() : DoomColors.DIMMED)
			.build();

		if (icon == null)
		{
			return line;
		}

		return SplitComponent.builder()
			.first(new ImageComponent(icon))
			.second(line)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(ICON_GAP)
			.build();
	}

	/**
	 * The gain while there is one, the total otherwise. Shortened in a grid column so it never
	 * wraps over the icon.
	 *
	 * @param narrow whether the figure has a grid column rather than a whole line
	 */
	static String figure(long amount, long recent, boolean narrow)
	{
		String text = recent > 0 ? "+" + DoomFormat.count(recent) : DoomFormat.count(amount);

		if (!narrow || text.length() <= NARROW_FIGURE)
		{
			return text;
		}

		return recent > 0 ? "+" + DoomFormat.compact(recent) : DoomFormat.compact(amount);
	}

	private void addLine(String left, String right)
	{
		addLine(left, right, DoomColors.PLAIN);
	}

	/** A name in the game's orange and its figure in {@code color}. */
	private void addLine(String left, String right, Color color)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftColor(DoomColors.ORANGE)
			.right(right)
			.rightColor(color)
			.build());
	}
}
