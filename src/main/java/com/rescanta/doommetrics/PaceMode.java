package com.rescanta.doommetrics;

public enum PaceMode
{
	/**
	 * Pace implied by how fast the deep delves themselves are going: 3600 divided by the mean
	 * duration of every delve at or past the averaging floor. Ignores delves 1-8.
	 */
	DEEP_AVERAGE("Deep pace"),

	/**
	 * Deep delves actually completed per hour of run time, shallow delves included. Starts low and climbs
	 * as the cost of delves 1-7 amortises over the run.
	 */
	RUN_THROUGHPUT("Full pace");

	private final String label;

	PaceMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
