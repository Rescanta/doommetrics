package com.tnamai.doommetrics;

public enum PaceMode
{
	/**
	 * Pace implied by how fast the deep delves themselves are going: 3600 divided by the mean
	 * duration of every delve at or past the averaging floor. Ignores the shallow warm-up.
	 */
	DEEP_AVERAGE("Deep pace"),

	/**
	 * Deep delves actually banked per hour of run time, warm-up included. Starts low and climbs
	 * as the cost of delves 1-7 amortises over the run.
	 */
	RUN_THROUGHPUT("Run pace");

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
