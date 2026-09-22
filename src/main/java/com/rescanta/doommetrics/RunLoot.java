package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import net.runelite.api.gameval.ItemID;

/**
 * A run's notable drops: where each landed, read from the pile, the loot warnings and the glowing
 * hole, and what the claim took.
 */
class RunLoot
{
	/** The item id of a unique the glowing hole signalled but nothing has named yet. */
	static final int UNKNOWN_UNIQUE = -1;

	static final String UNKNOWN_UNIQUE_NAME = "Unknown unique";

	/** Returned in place of a delve when nothing was written down. */
	static final int NOT_RECORDED = -1;

	/** Both forms of the eye count as the same drop. */
	static int dropKey(int itemId)
	{
		return itemId == ItemID.EYE_OF_AYAK ? ItemID.EYE_OF_AYAK_UNCHARGED : itemId;
	}

	/** A notable drop as it landed in the loot pile, claimed or not. */
	static final class Landed
	{
		/** The delve it came off. */
		final int level;

		final int itemId;
		final String name;

		/** How many landed on this delve at once - almost always one. */
		final int quantity;

		/** How many of this item the pile held once these landed. */
		final int heldAfter;

		Landed(int level, int itemId, String name, int quantity, int heldAfter)
		{
			this.level = level;
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.heldAfter = heldAfter;
		}
	}

	private static final class Drop
	{
		private final String name;
		private int quantity;

		private Drop(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	/** The delve a drop seen now came off - see {@link DelveRun#dropLevel}. */
	private final IntSupplier dropLevel;

	/** Claimed drops by {@link #dropKey}: the most the pile was seen holding, not a running sum. */
	private final Map<Integer, Drop> loot = new LinkedHashMap<>();

	private final List<Landed> landed = new ArrayList<>();

	/** Known pile counts by {@link #dropKey}; only a count above this is a new drop. */
	private final Map<Integer, Integer> held = new HashMap<>();

	/** "Your loot contains" warnings per item since the last descend try. */
	private final Map<Integer, Integer> warned = new HashMap<>();

	/** A joined run's first warnings are about the pile it inherited. */
	private boolean trustWarnings;

	/** Whether the pet in the pile lit the hole (only a character's first one does). */
	private boolean petGlows;

	/** Bumped whenever a drop lands or a claim is read - see {@link RunDetail#keyFor}. */
	private int changes;

	RunLoot(boolean partial, IntSupplier dropLevel)
	{
		this.trustWarnings = !partial;
		this.dropLevel = dropLevel;
	}

	/** A new delve started: warnings are counted afresh and can now be trusted. */
	void delveEntered()
	{
		warned.clear();
		trustWarnings = true;
	}

	/** The run was picked up again after delves nobody watched: the pile may hold their drops. */
	void resumed()
	{
		warned.clear();
		trustWarnings = false;
	}

	/** A delve was cleared: warnings are counted afresh. */
	void delveCleared()
	{
		warned.clear();
	}

	/** Records a claimed quantity. Repeats are harmless; only a larger count moves it. */
	void recordLoot(int itemId, String name, int quantity)
	{
		if (name == null || quantity <= 0)
		{
			return;
		}

		int key = dropKey(itemId);
		Drop drop = loot.get(key);

		if (drop == null)
		{
			loot.put(key, new Drop(name, quantity));
			changes++;
		}
		else if (quantity > drop.quantity)
		{
			drop.quantity = quantity;
			changes++;
		}
	}

	/** How many of a notable drop this trip has claimed, or 0 for none. */
	int claimed(int itemId)
	{
		Drop drop = loot.get(dropKey(itemId));
		return drop == null ? 0 : drop.quantity;
	}

	/**
	 * The pile was seen holding {@code quantity}; whatever is more than before landed now.
	 *
	 * @return the delve the drop was written down on, or {@link #NOT_RECORDED}
	 */
	int sawInPile(int itemId, String name, int quantity)
	{
		int key = dropKey(itemId);
		int before = held.getOrDefault(key, 0);

		if (name == null || quantity <= before)
		{
			return NOT_RECORDED;
		}

		held.put(key, quantity);

		int named = nameUnknown(key, name, quantity - before, quantity);

		if (named != NOT_RECORDED)
		{
			return named;
		}

		int level = dropLevel.getAsInt();
		landed.add(new Landed(level, key, name, quantity - before, quantity));
		changes++;
		return level;
	}

	/** A descend was tried; its warnings are counted from nothing. */
	void descending()
	{
		warned.clear();
	}

	/**
	 * A "Your loot contains" warning. One per copy, so this try's count is the pile's count.
	 *
	 * @return the delve the drop was written down on, or {@link #NOT_RECORDED}
	 */
	int warnedOf(int itemId, String name)
	{
		int count = warned.merge(dropKey(itemId), 1, Integer::sum);

		if (!trustWarnings)
		{
			pileAlreadyHeld(itemId, count);
			// About an inherited pile: places nothing, but can still name the glow's mark.
			return nameUnknown(dropKey(itemId), name, 1, count);
		}

		return sawInPile(itemId, name, count);
	}

	/**
	 * The glowing hole: marks an unknown unique, unless the run already knows what it glows for.
	 *
	 * @return true if this placed a drop
	 */
	boolean uniqueSignalled()
	{
		if (knowsOfGlowInPile())
		{
			return false;
		}

		landed.add(new Landed(dropLevel.getAsInt(), UNKNOWN_UNIQUE, UNKNOWN_UNIQUE_NAME, 1, 1));
		changes++;
		return true;
	}

	/** A duplicate pet sits in the pile without lighting the hole, so it doesn't count. */
	private boolean knowsOfGlowInPile()
	{
		for (Map.Entry<Integer, Integer> entry : held.entrySet())
		{
			if (entry.getValue() > 0 && (petGlows || entry.getKey() != ItemID.DOMPET))
			{
				return true;
			}
		}

		return outstandingUnknown() >= 0;
	}

	/**
	 * The first drop named after a glow takes over its mark, keeping the mark's delve.
	 *
	 * @return the delve the mark was on, or {@link #NOT_RECORDED} if there was none
	 */
	private int nameUnknown(int key, String name, int quantity, int heldAfter)
	{
		int unknown = outstandingUnknown();

		if (unknown < 0)
		{
			return NOT_RECORDED;
		}

		int level = landed.get(unknown).level;
		landed.set(unknown, new Landed(level, key, name, quantity, heldAfter));
		changes++;

		petGlows |= key == ItemID.DOMPET;
		return level;
	}

	/** The index of the unnamed glow mark in {@link #landed}, or -1. */
	private int outstandingUnknown()
	{
		for (int i = 0; i < landed.size(); i++)
		{
			if (landed.get(i).itemId == UNKNOWN_UNIQUE)
			{
				return i;
			}
		}

		return -1;
	}

	/** How many of a notable drop the pile is known to hold, or 0 for none. */
	int held(int itemId)
	{
		return held.getOrDefault(dropKey(itemId), 0);
	}

	/** What a joined run's pile held before we were watching. */
	void pileAlreadyHeld(int itemId, int quantity)
	{
		held.merge(dropKey(itemId), quantity, Math::max);
	}

	List<Landed> getLanded()
	{
		return Collections.unmodifiableList(landed);
	}

	int changes()
	{
		return changes;
	}

	/** Claimed drops by name, a drop earned twice listed twice. */
	List<String> getClaimed()
	{
		List<String> names = new ArrayList<>();

		for (Drop drop : loot.values())
		{
			for (int i = 0; i < drop.quantity; i++)
			{
				names.add(drop.name);
			}
		}

		return names;
	}
}
