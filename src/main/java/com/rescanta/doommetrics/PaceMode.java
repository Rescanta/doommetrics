package com.rescanta.doommetrics;

public enum PaceMode
{
	/** 3600 over the mean segment of delves 9 and deeper. */
	DEEP_AVERAGE("Deep pace"),

	/** Deep delves completed per hour of run time, shallow delves included. */
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
