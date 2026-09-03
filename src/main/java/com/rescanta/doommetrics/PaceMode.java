package com.rescanta.doommetrics;

public enum PaceMode
{
	DEEP_AVERAGE("Deep pace"),
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
