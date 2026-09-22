package com.rescanta.doommetrics;

/**
 * What the overlay draws for one counter heading: nothing, one line for the whole heading, or a
 * line for each counter under it.
 *
 * <p>Asked per heading rather than once for all of them, because how much a heading is worth
 * breaking down depends on the heading. Healing from a blood barrage and a blowpipe is one question
 * - is my hitpoints holding up - while three melee weapons punishing is a question about which one
 * is doing the work. So a player can read healing as a total and damage by weapon in one overlay.
 *
 * <p>There is no picking counters one by one. A counter for gear you do not bring never counts
 * anything, and with {@link DoomMetricsConfig#hideEmptyCounters} on - the default - a counter at
 * zero has no line, so {@link #EACH} already draws exactly the gear you are using.
 *
 * <p>Public because the config interface returns it, and RuneLite implements that interface with a
 * dynamic proxy outside this package: a package-private return type there throws IllegalAccessError
 * at the call site, which on the overlay's path means inside the draw loop.
 */
public enum CounterMode
{
	/** No line for this heading. */
	OFF("Off"),

	/**
	 * One line for the heading: everything counted under it, the sources with no counter of their
	 * own included - the figure the side panel's heading carries.
	 */
	TOTAL("Total"),

	/** A line for each counter under the heading, naming or picturing what it counts. */
	EACH("Each");

	private final String label;

	CounterMode(String label)
	{
		this.label = label;
	}

	/**
	 * What the config asks of one heading.
	 *
	 * <p>Here rather than as a default method on the config, which RuneLite's proxy would try to
	 * answer as a setting.
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
