package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * Which predicted times the target rows carry. Worked out here so the overlay and panel agree.
 * Public because the config interface returns it - see {@link CounterMode}.
 */
public enum TargetPrediction
{
	/** Time from now to the target. */
	REMAINING("Remaining"),

	/** Time from the start of the run to the target: predicted on the way, and real once there. */
	FULL_RUN("Full run"),

	BOTH("Both");

	private final String label;

	TargetPrediction(String label)
	{
		this.label = label;
	}

	/** The target row's label, which also says when it has been reached. */
	static String targetLabel(DelveRun run, int target)
	{
		return run.hasReached(target) ? "Target reached" : "Target";
	}

	/** The label of the time left row, or null when it is not drawn - see {@link #targetLabel}. */
	String remainingLabel(DelveRun run, int target)
	{
		return this == FULL_RUN || run.hasReached(target) ? null : "To go";
	}

	/** What the time left row reads, alongside {@link #remainingLabel}. */
	static String remainingValue(DelveRun run, int target, Instant now)
	{
		return DoomFormat.prediction(run.untilTarget(target, now));
	}

	/** The full run row's label, or null when not drawn. An asterisk marks a joined run. */
	String totalLabel(DelveRun run)
	{
		if (this == REMAINING)
		{
			return null;
		}

		return run.isPartial() ? "Total*" : "Total";
	}

	/**
	 * The predicted or real time to the target, {@code Reached} for a run joined past it, or a
	 * dash.
	 */
	static String totalValue(DelveRun run, int target, Instant now)
	{
		Duration total = run.runToTarget(target, now);

		if (total != null)
		{
			return DoomFormat.duration(total);
		}

		return run.hasReached(target) ? "Reached" : "-";
	}

	@Override
	public String toString()
	{
		return label;
	}
}
