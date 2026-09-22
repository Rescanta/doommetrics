package com.rescanta.doommetrics;

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
