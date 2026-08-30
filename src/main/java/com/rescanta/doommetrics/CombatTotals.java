package com.rescanta.doommetrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tally of every {@link CombatMetric}, summed over any number of delves.
 *
 * <p>Used three times over, the same way {@link DelveTotals} is used twice: once against the run in
 * progress, once in memory for the sitting, and once on the RuneScape profile for the character's
 * lifetime. A run is banked into the other two as it ends, so all three are the same sum of the
 * same numbers and a sitting holding one run reads exactly what that run did.
 *
 * <p>Both the config value and the history file are written from this, so the shape here is the
 * stored format. Metrics are held in a map keyed by {@link CombatMetric#key()} rather than in
 * fields, which buys two things: a metric that has never fired takes no space at all - most runs
 * touch three or four of the eight - and a value written by a version that knows a metric this one
 * does not is carried through a read and a write untouched instead of being silently dropped.
 *
 * <p>{@link #v} exists so a later change can tell old values from new ones without guessing.
 */
class CombatTotals
{
	/** The schema this value was written under. */
	static final int VERSION = 1;

	int v = VERSION;

	/**
	 * Amount per metric key, in first-seen order, with zeroes left out.
	 *
	 * <p>Package-private and named for brevity because Gson writes the field name into every line
	 * of the history file, where a run is otherwise about sixty bytes.
	 */
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

	/**
	 * Adds to a metric. A non-positive amount is ignored rather than stored, so nothing can enter
	 * the map that would make a total read lower than the sum of the runs behind it.
	 */
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

	/**
	 * Adds every metric of another tally into this one, including any key this version does not
	 * know about - see the note on the map above.
	 */
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

	/**
	 * A snapshot of this tally. Handed to the Swing thread so it never reads a map the client
	 * thread may be writing to a tick later.
	 */
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
	 * Drops anything unusable from a tally read back off disk or out of config, and returns whether
	 * what is left can be trusted.
	 *
	 * <p>A negative amount is not a tally anybody earned, so it goes rather than being carried for
	 * the rest of the character's lifetime. An unrecognised key stays: it is either a metric added
	 * by a newer version or one this version has retired, and in both cases the honest thing is to
	 * keep the number and not show it.
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
