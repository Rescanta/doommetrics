package com.rescanta.doommetrics;

/**
 * How ticked counters are drawn on the overlay.
 *
 * <p>{@code Separate} gives each ticked counter its own line; {@code Combined} folds them into
 * one line per group, summing only that group's ticked members and skipping a group with none
 * ticked.
 */
public enum CounterLayout
{
	SEPARATE("Separate"),
	COMBINED("Combined");

	private final String label;

	CounterLayout(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
