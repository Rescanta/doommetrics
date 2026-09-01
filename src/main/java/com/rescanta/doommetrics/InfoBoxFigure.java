package com.rescanta.doommetrics;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;

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
 * freely.
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

	BLOOD_BARRAGE_HEAL("Blood barrage heal", CombatMetric.BLOOD_BARRAGE_HEAL),
	OTHER_SPELL_HEAL("Other spell heal", CombatMetric.OTHER_SPELL_HEAL),
	AGS_HEAL("AGS heal", CombatMetric.AGS_HEAL),
	BLOWPIPE_HEAL("Blowpipe heal", CombatMetric.BLOWPIPE_HEAL),
	OTHER_SPEC_HEAL("Other spec heal", CombatMetric.OTHER_SPEC_HEAL),
	ELDRITCH_PRAYER("Eldritch prayer", CombatMetric.ELDRITCH_PRAYER),
	ZCB_DAMAGE("ZCB damage", CombatMetric.ZCB_DAMAGE),
	OTHER_SPEC_DAMAGE("Other spec damage", CombatMetric.OTHER_SPEC_DAMAGE),

	ALL_SPELL_HEALING("All spell healing", CombatMetric.Group.SPELL_HEAL),
	ALL_SPEC_HEALING("All spec healing", CombatMetric.Group.SPEC_HEAL),
	ALL_PRAYER_RESTORED("All prayer restored", CombatMetric.Group.PRAYER),
	ALL_SPEC_DAMAGE("All spec damage", CombatMetric.Group.DAMAGE);

	// Held rather than fetched for the reason the overlay holds its own copy: values() hands out a
	// fresh array every call, and a group figure walks it up to three times a frame.
	private static final CombatMetric[] METRICS = CombatMetric.values();

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
				return DoomFormat.compactPace(run.pace(config.paceMode()));

			case TIME_TO_TARGET:
			{
				int target = config.targetDelve();

				if (run.hasReached(target))
				{
					return "Done";
				}

				Duration remaining = run.untilTarget(target, now);
				return remaining == null ? "-" : DoomFormat.compactDuration(remaining);
			}

			default:
				return DoomFormat.compact(amount(run));
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
				return run.pace(config.paceMode()) == null ? DoomColors.DIMMED : DoomColors.PLAIN;

			case TIME_TO_TARGET:
				return run.hasReached(config.targetDelve())
					|| run.untilTarget(config.targetDelve(), now) != null
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
			{
				String elapsed = "Run time</br>" + DoomFormat.duration(run.displayElapsed(now));

				// The panel says this with an asterisk it has the width for; here it is said out.
				return run.isPartial()
					? elapsed + "</br>Joined part way through, so the run is at least this long"
					: elapsed;
			}

			case PACE:
			{
				PaceMode mode = config.paceMode();
				Double pace = run.pace(mode);

				return pace == null
					? mode + "</br>Nothing deep enough to average yet"
					: mode + "</br>" + DoomFormat.pace(pace);
			}

			case TIME_TO_TARGET:
			{
				int target = config.targetDelve();

				if (run.hasReached(target))
				{
					return "Delve " + target + "</br>Reached";
				}

				Duration remaining = run.untilTarget(target, now);

				if (remaining != null)
				{
					return "Predicted to delve " + target + "</br>"
						+ DoomFormat.duration(remaining);
				}

				return "Predicted to delve " + target + "</br>" + (run.isFinished()
					? "The run is over"
					: "No delve " + DelveRun.PACE_AVERAGE_FROM_LEVEL + " cleared to predict from");
			}

			default:
				return heading() + "</br>" + DoomFormat.count(amount(run)) + " "
					+ unit().description();
		}
	}

	/** The figure itself, for a counter: one source, or every source under one heading. */
	private long amount(DelveRun run)
	{
		CombatTotals combat = run.getCombat();

		if (metric != null)
		{
			return combat.get(metric);
		}

		long total = 0;

		for (CombatMetric each : METRICS)
		{
			if (each.group() == group)
			{
				total += combat.get(each);
			}
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
