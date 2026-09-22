package com.rescanta.doommetrics;

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
import net.runelite.client.ui.overlay.components.TitleComponent;

class DoomMetricsOverlay extends OverlayPanel
{
	// Held rather than fetched, because values() hands out a fresh copy of the array every call and
	// these are walked several times a frame.
	private static final CombatMetric.Group[] GROUPS = CombatMetric.Group.values();

	/** Between a counter's icon and the rest of its row, when it is drawn with one. */
	private static final Point ICON_GAP = new Point(4, 0);

	/** How many counters share a line under {@link CounterStyle#ICON_GRID}. */
	static final int GRID_COLUMNS = 2;

	/**
	 * The space between two counters sharing a line. Enough that the first one's figure is not
	 * read as running into the second one's icon.
	 */
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
	 * Draws each counter heading the way the config asks - nothing, one total line, or a line per
	 * counter - in the order they are declared, so the overlay reads down in the same order as the
	 * side panel's table.
	 *
	 * <p>There are no headings over the lines: a heading sitting above lines you asked for is a row
	 * of overlay spent saying nothing you had not already been told. What the labels lose by having
	 * no heading to qualify them they make up in saying outright what they count - see
	 * {@link CombatMetric#overlayLabel()}.
	 *
	 * <p>A line that has counted nothing is left off by default, so a heading read counter by
	 * counter only draws the gear you are using. With that switched off it gets its line, dimmed,
	 * for the same reason the table keeps its zero rows: the overlay does not resize under you
	 * mid-delve, and a spec you expected to be firing is visibly not.
	 *
	 * <p>A line that has just gained reads as the gain for a few seconds - {@code +97} - before it
	 * goes back to the run's total. See {@link RecentGains}.
	 *
	 * <p>A total line carries what the counters cannot: the sources with no counter of their own,
	 * such as a melee weapon punishing without a counter to name it. It is the figure the side
	 * panel's heading carries, so the two never disagree.
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
					// of one, and no one picture stands for everything it sums.
					flush(row);
					panelComponent.getChildren().add(amount(group.overlayHeading(), null, total,
						recent, group.unit(), false));
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
					LayoutableRenderableEntity line = amount(metric.overlayLabel(), icon, amount,
						run.recentGain(metric, now), metric.unit(), gridded);

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
	 * One counter's line, its figure in the colour of whatever it is counted in, so which lines are
	 * hitpoints, which are prayer and which are damage is legible without reading the labels.
	 *
	 * <p>A zero stays grey rather than taking a faint tint of its unit: a counter that has not
	 * fired is being drawn back deliberately, and the whole point of the colour is that it marks
	 * out a figure worth reading.
	 *
	 * @param icon   drawn in place of {@code left} when there is one, or null for the words
	 * @param recent what the counter has just gained, drawn in place of the total while it is
	 *               more than nothing
	 * @param narrow whether the line is one column of a grid rather than the whole width - see
	 *               {@link #figure}
	 */
	private static LayoutableRenderableEntity amount(String left, BufferedImage icon, long amount,
		long recent, CombatMetric.Unit unit, boolean narrow)
	{
		LineComponent line = LineComponent.builder()
			.left(icon == null ? left : "")
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
	 * The figure a counter's line reads: the gain while there is one, the total otherwise.
	 *
	 * <p>In a grid column, a figure too long for it is shortened the way the infobox shortens one -
	 * {@code 25k}, {@code +1.2k}. Half a line holds an icon and five characters, which keeps
	 * everything up to 9,999 whole; past that the column would wrap the figure onto a second line
	 * and draw it back over the icon, which is worse than losing the last digits.
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
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.build());
	}
}
