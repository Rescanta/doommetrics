package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * Which predicted times the target rows carry: how long is left to the target, how long the whole
 * run to it will have taken, or both.
 *
 * <p>The rows are worked out here rather than by the overlay and the side panel each, because both
 * draw them and the one thing they must not do is disagree about which rows a state has.
 *
 * <p>Public because the config interface returns it - see {@link CounterMode} for why that
 * matters.
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

	/**
	 * The label of the row naming the target, which is also the row that says it has been reached -
	 * so the time left row has nothing to add once it has, and goes.
	 */
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

	/**
	 * The label of the full run row, or null when it is not drawn. The asterisk marks a run joined
	 * part way through, whose start is a guess, as it does on the run timer.
	 */
	String totalLabel(DelveRun run)
	{
		if (this == REMAINING)
		{
			return null;
		}

		return run.isPartial() ? "Total*" : "Total";
	}

	/**
	 * What the full run row reads: the predicted or real time to the target, {@code Reached} for a
	 * run joined already past it, or a dash while there is nothing to predict from.
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
