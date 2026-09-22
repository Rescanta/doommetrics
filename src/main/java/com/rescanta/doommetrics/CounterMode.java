package com.rescanta.doommetrics;

/**
 * What the overlay draws for one counter heading: nothing, one total line, or a line per counter.
 * Public because the config interface returns it: RuneLite proxies that interface from another
 * package, and a package-private return type throws IllegalAccessError at the call site.
 */
public enum CounterMode
{
	/** No line for this heading. */
	OFF("Off"),

	/** One line for everything counted under the heading, catch-alls included. */
	TOTAL("Total"),

	/** A line for each counter under the heading, naming or picturing what it counts. */
	EACH("Each");

	private final String label;

	CounterMode(String label)
	{
		this.label = label;
	}

	/**
	 * What the config asks of one heading. Not a default method, which the config proxy would treat
	 * as a setting.
	 */
	static CounterMode of(DoomMetricsConfig config, CombatMetric.Group group)
	{
		switch (group)
		{
			case HEALING:
				return config.healingCounters();

			case PRAYER:
				return config.prayerCounters();

			case DAMAGE:
				return config.damageCounters();

			default:
				return OFF;
		}
	}

	@Override
	public String toString()
	{
		return label;
	}
}
