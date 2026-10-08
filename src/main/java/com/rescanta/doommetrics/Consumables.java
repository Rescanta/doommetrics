package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes what was eaten or drunk out of a heal or a restore on the same tick. The game reports one
 * rise whatever raised it, and a brew can go down on the tick a spec pays out. What an item gives
 * is learned from a rise nothing else explains; until then a rise it shares a tick with is left
 * uncounted. Errs short. No RuneLite types.
 */
final class Consumables
{
	private static final int NONE = Integer.MIN_VALUE;

	/** Something that left the inventory by being eaten or drunk. */
	private static final class Taken
	{
		private final String item;
		private final boolean drink;

		private Taken(String item, boolean drink)
		{
			this.item = item;
			this.drink = drink;
		}
	}

	/** The most each item has been seen to heal and to restore; 0 once seen to give none. */
	private final Map<String, Integer> heals = new HashMap<>();
	private final Map<String, Integer> restores = new HashMap<>();

	private final List<Taken> taken = new ArrayList<>();
	private int takenAt = NONE;

	/** Forgets what was taken this tick. What each item gives is kept: it doesn't change. */
	void reset()
	{
		taken.clear();
		takenAt = NONE;
	}

	/**
	 * @param item  the item's name, the same for every dose of a potion
	 * @param drink whether it was drunk rather than eaten: food never restores prayer
	 */
	void consumed(String item, boolean drink, int tick)
	{
		if (tick != takenAt)
		{
			taken.clear();
			takenAt = tick;
		}

		taken.add(new Taken(item, drink));
	}

	boolean anyAt(int tick)
	{
		return tick == takenAt && !taken.isEmpty();
	}

	/**
	 * What is left of a rise once what was eaten or drunk on its tick is taken out.
	 *
	 * @param spare  whether nothing else can explain the rise, which makes it what the item gives
	 * @param capped whether the rise stopped at the trained level or over it, where food stops
	 *               short: it says the least an item gives, not what it gives
	 * @return the points something else gave
	 */
	long without(SpecEffect.Kind kind, long rise, int tick, boolean spare, boolean capped)
	{
		List<Taken> relevant = relevant(kind, tick);

		if (relevant.isEmpty())
		{
			return rise;
		}

		Map<String, Integer> gives = kind == SpecEffect.Kind.HEAL ? heals : restores;
		long explained = 0;
		String unknown = null;
		int unknowns = 0;

		for (Taken each : relevant)
		{
			Integer known = gives.get(each.item);

			if (known == null)
			{
				unknowns++;
				unknown = each.item;
			}
			else
			{
				explained += known;
			}
		}

		if (spare)
		{
			if (unknowns == 1 && !capped)
			{
				gives.put(unknown, (int) Math.max(0, rise - explained));
			}
			else if (unknowns == 0 && relevant.size() == 1)
			{
				gives.merge(relevant.get(0).item, (int) rise, Math::max);
			}

			return 0;
		}

		// Something not seen on its own yet could be all of it.
		return unknowns > 0 ? 0 : Math.max(0, rise - explained);
	}

	/**
	 * The level had room to rise this tick and didn't, so what was taken on it gives none of this:
	 * a ranging potion heals nothing, and must not cost a heal it shares a tick with later.
	 */
	void didNotRise(SpecEffect.Kind kind, int tick)
	{
		Map<String, Integer> gives = kind == SpecEffect.Kind.HEAL ? heals : restores;

		for (Taken each : relevant(kind, tick))
		{
			gives.putIfAbsent(each.item, 0);
		}
	}

	private List<Taken> relevant(SpecEffect.Kind kind, int tick)
	{
		List<Taken> relevant = new ArrayList<>();

		if (tick != takenAt || kind == SpecEffect.Kind.DAMAGE)
		{
			return relevant;
		}

		for (Taken each : taken)
		{
			if (each.drink || kind == SpecEffect.Kind.HEAL)
			{
				relevant.add(each);
			}
		}

		return relevant;
	}
}
