package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/** What a run's claim took of the notable drops. */
class RunLoot
{
	/** Both forms of the eye count as the same drop. */
	static int dropKey(int itemId)
	{
		return itemId == ItemID.EYE_OF_AYAK ? ItemID.EYE_OF_AYAK_UNCHARGED : itemId;
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

	/** Claimed drops by {@link #dropKey}: the most the claim was seen holding, not a running sum. */
	private final Map<Integer, Drop> loot = new LinkedHashMap<>();

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
		}
		else if (quantity > drop.quantity)
		{
			drop.quantity = quantity;
		}
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
