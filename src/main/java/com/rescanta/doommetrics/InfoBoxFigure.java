package com.rescanta.doommetrics;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * The single figure an infobox square can hold, and everything needed to draw it: the text, the
 * colour, and the tooltip that says what the text left out.
 *
 * <p>A square is not a panel with fewer rows. A panel row has a label beside it and room for a
 * separated figure, so it can afford to say {@code Deep pace 40.1/hr}; a square is thirty-five
 * pixels wide with a picture behind it, and everything that will not fit in it has to go somewhere
 * or be dropped. So the square carries the figure at a glance - {@code 40.1}, {@code 1.2k},
 * {@code 1h23} - and the tooltip carries what qualifies it: the unit, the full precision, and
 * whether the run it was measured over is one we saw the start of.
 *
 * <p>The constant names are what the config stores, so renaming one silently resets the choice of
 * whoever had it picked, the same way {@link CombatMetric#key()} works. The labels may be reworded
 * freely. The catch-all counters had squares once; a square still saved as one of them fails to
 * read back and the client falls back to the default, {@link #DELVE}.
 *
 * <p>Public because the config interface returns it - see {@link DisplayStyle} for why that
 * matters.
 */
public enum InfoBoxFigure
{
	DELVE("Delve"),
	RUN_TIMER("Run timer"),
	PACE("Pace"),
	TIME_TO_TARGET("Time to target"),
	TARGET_RUN_TIME("Predicted run time"),

	BLOOD_BARRAGE_HEAL("Blood barrage heal", CombatMetric.BLOOD_BARRAGE_HEAL),
	AGS_HEAL("AGS heal", CombatMetric.AGS_HEAL),
	BLOWPIPE_HEAL("Blowpipe heal", CombatMetric.BLOWPIPE_HEAL),
	ELDRITCH_PRAYER("Eldritch prayer", CombatMetric.ELDRITCH_PRAYER),
	ZCB_DAMAGE("ZCB damage", CombatMetric.ZCB_DAMAGE),
	SCYTHE_PUNISH("Scythe punish", CombatMetric.SCYTHE_PUNISH),
	NOXIOUS_HALBERD_PUNISH("Noxious halberd punish", CombatMetric.NOXIOUS_HALBERD_PUNISH),
	CRYSTAL_HALBERD_PUNISH("Crystal halberd punish", CombatMetric.CRYSTAL_HALBERD_PUNISH),

	ALL_SPELL_HEALING("All spell healing", CombatMetric.Group.SPELL_HEAL),
	ALL_SPEC_HEALING("All spec healing", CombatMetric.Group.SPEC_HEAL),
	ALL_PRAYER_RESTORED("All prayer restored", CombatMetric.Group.PRAYER),
	ALL_SPEC_DAMAGE("All spec damage", CombatMetric.Group.DAMAGE),
	ALL_PUNISH_DAMAGE("All punish damage", CombatMetric.Group.PUNISH);

	private final String label;

	/** The one source this figure counts, or null when it is a group or not a counter at all. */
	private final CombatMetric metric;

	/** The heading this figure sums, or null when it is a single source or not a counter. */
	private final CombatMetric.Group group;

	InfoBoxFigure(String label)
	{
		this(label, null, null);
	}

	InfoBoxFigure(String label, CombatMetric metric)
	{
		this(label, metric, null);
	}

	InfoBoxFigure(String label, CombatMetric.Group group)
	{
		this(label, null, group);
	}

	InfoBoxFigure(String label, CombatMetric metric, CombatMetric.Group group)
	{
		this.label = label;
		this.metric = metric;
		this.group = group;
	}

	/**
	 * Whether the square has anything to hold for this run. Only the time to a target ever has
	 * nothing: once the target is behind you there is no time left to count down, and a square
	 * that only says so is one more thing on screen saying nothing.
	 */
	boolean shown(DelveRun run, DoomMetricsConfig config)
	{
		return this != TIME_TO_TARGET || !run.hasReached(config.targetDelve());
	}

	/**
	 * What the square reads, shortened to fit it. Never null and never empty, so a square that is
	 * on screen always has a figure in it.
	 */
	String text(DelveRun run, DoomMetricsConfig config, Instant now)
	{
		switch (this)
		{
			case DELVE:
				return Integer.toString(delve(run));

			case RUN_TIMER:
				return DoomFormat.compactDuration(run.displayElapsed(now));

			case PACE:
				return DoomFormat.compactPace(run.pace(run.paceMode(config.paceMode())));

			case TIME_TO_TARGET:
			{
				Duration remaining = run.untilTarget(config.targetDelve(), now);
				return remaining == null ? "-" : DoomFormat.compactDuration(remaining);
			}

			case TARGET_RUN_TIME:
			{
				int target = config.targetDelve();
				Duration total = run.runToTarget(target, now);

				if (total != null)
				{
					return DoomFormat.compactDuration(total);
				}

				return run.hasReached(target) ? "Done" : "-";
			}

			default:
			{
				// A gain just made reads as the gain for a few seconds, as on the panel.
				long recent = recent(run, now);
				return recent > 0 ? "+" + DoomFormat.compact(recent) : DoomFormat.compact(amount(run));
			}
		}
	}

	/**
	 * The colour the figure is drawn in: its unit's, so that red is hitpoints, blue is prayer and
	 * yellow is damage without the square having room to say so, and dimmed when there is nothing
	 * behind the figure yet.
	 *
	 * <p>The three run figures are drawn plain. They are not counted in any unit, and lending one
	 * of them a unit's colour would spend the only thing the square has to tell the units apart.
	 */
	Color color(DelveRun run, DoomMetricsConfig config, Instant now)
	{
		switch (this)
		{
			case DELVE:
			case RUN_TIMER:
				return DoomColors.PLAIN;

			case PACE:
				return run.pace(run.paceMode(config.paceMode())) == null
					? DoomColors.DIMMED
					: DoomColors.PLAIN;

			case TIME_TO_TARGET:
				return run.untilTarget(config.targetDelve(), now) != null
					? DoomColors.PLAIN
					: DoomColors.DIMMED;

			case TARGET_RUN_TIME:
				return run.hasReached(config.targetDelve())
					|| run.runToTarget(config.targetDelve(), now) != null
					? DoomColors.PLAIN
					: DoomColors.DIMMED;

			default:
				return amount(run) > 0 ? unit().color() : DoomColors.DIMMED;
		}
	}

	/**
	 * What the square could not fit: what the figure is, what it is counted in, and the full
	 * precision of it. Split into lines the way RuneLite's tooltips are.
	 */
	String tooltip(DelveRun run, DoomMetricsConfig config, Instant now)
	{
		switch (this)
		{
			case DELVE:
				if (!run.isFinished())
				{
					return "Delve " + run.currentLevel();
				}

				return run.getEndReason() == EndReason.DIED
					? "Died on delve " + run.getDiedOnLevel()
					: "Cleared delve " + run.lastLevel();

			case RUN_TIMER:
				return partialNote(run,
					"Run time</br>" + DoomFormat.duration(run.displayElapsed(now)));

			case PACE:
			{
				PaceMode mode = run.paceMode(config.paceMode());
				Double pace = run.pace(mode);

				return pace == null
					? mode + (mode == PaceMode.RUN_THROUGHPUT
						? "</br>No deep delve completed"
						: "</br>Nothing deep enough to average yet")
					: mode + "</br>" + DoomFormat.pace(pace);
			}

			case TIME_TO_TARGET:
			{
				int target = config.targetDelve();
				Duration remaining = run.untilTarget(target, now);

				return "Predicted to delve " + target + "</br>" + (remaining != null
					? DoomFormat.duration(remaining)
					: nothingToPredict(run));
			}

			case TARGET_RUN_TIME:
			{
				int target = config.targetDelve();
				Duration total = run.runToTarget(target, now);

				if (run.hasReached(target))
				{
					return total == null
						? "Delve " + target + "</br>Reached before the run was joined"
						: partialNote(run, "Delve " + target + " reached in</br>"
							+ DoomFormat.duration(total));
				}

				return total == null
					? "Predicted run to delve " + target + "</br>" + nothingToPredict(run)
					: partialNote(run, "Predicted run to delve " + target + "</br>"
						+ DoomFormat.duration(total));
			}

			default:
			{
				String tooltip = heading() + "</br>" + DoomFormat.count(amount(run)) + " "
					+ unit().description();

				// A catch-all's label does not say what it catches, and the square has no label.
				List<String> sources = metric == null ? Collections.emptyList() : metric.sources();

				return sources.isEmpty()
					? tooltip
					: tooltip + "</br>Counted from:</br>" + String.join("</br>", sources);
			}
		}
	}

	/**
	 * A time measured from the start of the run, with a line saying the start is a guess when the
	 * run was joined part way through. The panel says this with an asterisk it has the width for;
	 * here it is said out.
	 */
	private static String partialNote(DelveRun run, String tooltip)
	{
		return run.isPartial()
			? tooltip + "</br>Joined part way through, so the run is at least this long"
			: tooltip;
	}

	/** Why a target has no predicted time, for a target not yet reached. */
	private static String nothingToPredict(DelveRun run)
	{
		return run.isFinished()
			? "The run is over"
			: "No delve " + DelveRun.PACE_AVERAGE_FROM_LEVEL + " cleared to predict from";
	}

	/** The one source this figure counts, or null for a group or a figure that is not a counter. */
	CombatMetric metric()
	{
		return metric;
	}

	/** The figure itself, for a counter: one source, or every source under one heading. */
	private long amount(DelveRun run)
	{
		return sum(run.getCombat()::get);
	}

	/** What the counter has just gained - see {@link RecentGains}. */
	private long recent(DelveRun run, Instant now)
	{
		return sum(each -> run.recentGain(each, now));
	}

	private long sum(ToLongFunction<CombatMetric> figure)
	{
		if (metric != null)
		{
			return figure.applyAsLong(metric);
		}

		long total = 0;

		// Every counter under the heading, the ones with no row of their own included - the same
		// figure the panel's heading and the overlay's combined line carry, so a square set to
		// All punish damage does not read lower than the table it was set from.
		for (CombatMetric each : group.metrics())
		{
			total += figure.applyAsLong(each);
		}

		return total;
	}

	/**
	 * The delve the square reports: the one being fought, or the one the run ended on. A death is
	 * reported on the delve it happened on rather than the last one banked, which is the delve the
	 * panel names in the same state.
	 */
	private static int delve(DelveRun run)
	{
		if (!run.isFinished())
		{
			return run.currentLevel();
		}

		return run.getEndReason() == EndReason.DIED ? run.getDiedOnLevel() : run.lastLevel();
	}

	private CombatMetric.Unit unit()
	{
		return metric != null ? metric.unit() : group.unit();
	}

	private String heading()
	{
		return metric != null ? metric.qualifiedLabel() : group.heading();
	}

	@Override
	public String toString()
	{
		return label;
	}
}
