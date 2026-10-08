package com.rescanta.doommetrics;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Item lookups shared by the watchers. Client thread only. */
class GameItems
{
	private final Client client;
	private final ItemManager itemManager;

	GameItems(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/** An item's name from the cache, or null when there is nothing to name. */
	String name(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}

		ItemComposition item = itemManager.getItemComposition(itemId);
		return item == null ? null : item.getName();
	}

	/**
	 * Whether an item is drunk from the inventory (true) or eaten (false), or null when it is
	 * neither.
	 */
	Boolean isDrunk(int itemId)
	{
		ItemComposition item = itemId <= 0 ? null : itemManager.getItemComposition(itemId);
		String[] actions = item == null ? null : item.getInventoryActions();

		if (actions == null)
		{
			return null;
		}

		for (String action : actions)
		{
			if ("Eat".equals(action))
			{
				return false;
			}

			if ("Drink".equals(action))
			{
				return true;
			}
		}

		return null;
	}

	/** How many of each item the inventory holds, by id; null when it can't be read. */
	Map<Integer, Integer> carried()
	{
		return count(client.getItemContainer(InventoryID.INV));
	}

	static Map<Integer, Integer> count(ItemContainer container)
	{
		if (container == null)
		{
			return null;
		}

		Map<Integer, Integer> counts = new HashMap<>();

		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				counts.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		return counts;
	}

	/** The item id worn in {@code slot}, or 0 when the slot is empty or unreadable. */
	int equipped(EquipmentInventorySlot slot)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);

		if (worn == null)
		{
			return 0;
		}

		Item item = worn.getItem(slot.getSlotIdx());
		return item == null ? 0 : item.getId();
	}

	/** Whether a weapon's bonuses say melee - see {@link PunishWeapon#isMelee}. */
	boolean isMeleeWeapon(int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		ItemEquipmentStats bonuses = stats == null ? null : stats.getEquipment();

		return bonuses != null && PunishWeapon.isMelee(bonuses.getAstab(), bonuses.getAslash(),
			bonuses.getAcrush(), bonuses.getArange(), bonuses.getAmagic());
	}
}
