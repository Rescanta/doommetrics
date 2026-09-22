package com.rescanta.doommetrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tally of every {@link CombatMetric}, used for a run, a session and a lifetime. This is the
 * stored format: a map by {@link CombatMetric#key()}, which carries unknown keys through untouched.
 * {@link #v} versions it.
 */
class CombatTotals
{
	/** The schema this value was written under. */
	static final int VERSION = 1;

	int v = VERSION;

	/** Amount per metric key, zeroes left out. Short name: Gson writes it on every history line. */
	Map<String, Long> m = new LinkedHashMap<>();

	long get(CombatMetric metric)
	{
		return get(metric.key());
	}

	private long get(String key)
	{
		if (m == null)
		{
			return 0;
		}

		Long value = m.get(key);
		return value == null ? 0 : value;
	}

	/** Adds to a metric; non-positive amounts are ignored. */
	void add(CombatMetric metric, long amount)
	{
		if (amount <= 0)
		{
			return;
		}

		if (m == null)
		{
			m = new LinkedHashMap<>();
		}

		m.merge(metric.key(), amount, Long::sum);
	}

	/** Adds another tally in, unknown keys included. */
	void addAll(CombatTotals other)
	{
		if (other == null || other.m == null)
		{
			return;
		}

		if (m == null)
		{
			m = new LinkedHashMap<>();
		}

		other.m.forEach((key, amount) ->
		{
			if (amount != null && amount > 0)
			{
				m.merge(key, amount, Long::sum);
			}
		});
	}

	/** A snapshot, for handing to the Swing thread. */
	CombatTotals copy()
	{
		CombatTotals copy = new CombatTotals();
		copy.addAll(this);
		return copy;
	}

	/** This tally with another added, leaving both untouched. */
	CombatTotals plus(CombatTotals other)
	{
		CombatTotals sum = new CombatTotals();
		sum.addAll(this);
		sum.addAll(other);
		return sum;
	}

	/** Whether anything at all has been counted. */
	boolean isEmpty()
	{
		if (m == null || m.isEmpty())
		{
			return true;
		}

		for (Long amount : m.values())
		{
			if (amount != null && amount > 0)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Drops negative amounts from a stored tally, keeping unknown keys. Returns whether it can be
	 * trusted.
	 */
	boolean sanitise()
	{
		if (m == null)
		{
			m = new LinkedHashMap<>();
			return true;
		}

		m.values().removeIf(amount -> amount == null || amount < 0);
		return true;
	}
}
