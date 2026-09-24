package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * Records the notable drops the claim took, and notices the claim and leaving that end a run. Only
 * the claim is read: where in the run a drop landed isn't followed. Client thread only.
 */
@Slf4j
class LootWatcher
{
	/** Both forms of the eye, since we can't tell which one is the drop. */
	static final Set<Integer> NOTABLE_DROPS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(
			ItemID.EYE_OF_AYAK,
			ItemID.EYE_OF_AYAK_UNCHARGED,
			ItemID.AVERNIC_TREADS,
			ItemID.MOKHAIOTL_CLOTH,
			ItemID.DOMPET)));

	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You have a funny feeling like you would have been followed",
		"You feel something weird sneaking into your backpack"
	};

	private static final String DESCEND_OPTION = "Descend";

	/** Both copies of the loot pile - the claimed one and the one mid-run. */
	private static final List<Integer> PILES =
		Arrays.asList(InventoryID.DOM_LOOTPILE, InventoryID.DOM_LOOTPILE_DURING);

	private final Client client;
	private final ClientThread clientThread;
	private final GameItems items;
	private final Supplier<DelveRun> run;

	/** Ends the run as finished: the claim went through, or the player left. */
	private final Runnable finishRun;

	/** "Claim and leave" was clicked; only then does the claimed loot filling in end the run. */
	private boolean claimRequested;

	/**
	 * Loot piles not sent since a run ended, so the client may still hold an earlier trip's. Empty
	 * when the plugin starts: what it cannot know about, it trusts.
	 */
	private final Set<Integer> stalePiles = new HashSet<>();

	LootWatcher(Client client, ClientThread clientThread, GameItems items, Supplier<DelveRun> run,
		Runnable finishRun)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.items = items;
		this.run = run;
		this.finishRun = finishRun;
	}

	void reset()
	{
		claimRequested = false;
	}

	void runStarted()
	{
		claimRequested = false;
	}

	/** Until they are sent again, the piles the client holds are this run's. */
	void runEnded()
	{
		stalePiles.addAll(PILES);
	}

	/** A new delve started, so no claim is on its way. */
	void delveStarted()
	{
		claimRequested = false;
	}

	static boolean isPetMessage(String message)
	{
		for (String prefix : PET_MESSAGES)
		{
			if (message.startsWith(prefix))
			{
				return true;
			}
		}

		return false;
	}

	/** The pet line only comes with the claim, ahead of the claimed loot. */
	void petClaimed()
	{
		DelveRun current = run.get();

		if (current == null)
		{
			return;
		}

		current.loot().recordLoot(ItemID.DOMPET, items.name(ItemID.DOMPET), 1);
		log.debug("Pet claimed on delve {}", current.currentLevel());
	}

	/** Notes a pile sent this trip; the claimed one filling in is the claim going through. */
	void itemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();

		if (!PILES.contains(containerId))
		{
			return;
		}

		stalePiles.remove(containerId);

		if (run.get() == null)
		{
			return;
		}

		// The claimed loot filling in after "Claim and leave" is the claim going through.
		if (containerId == InventoryID.DOM_LOOTPILE && claimRequested
			&& !isEmpty(event.getItemContainer()))
		{
			finishClaim();
		}
	}

	void menuOptionClicked(MenuOptionClicked event)
	{
		if (run.get() == null)
		{
			return;
		}

		int widgetId = event.getParam1();

		// Backed out of the claim and carrying on.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_DESCEND
			|| DESCEND_OPTION.equals(Text.removeTags(event.getMenuOption())))
		{
			claimRequested = false;
			return;
		}

		// Not the claim yet: it asks for a Confirm first.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_CLAIM)
		{
			claimRequested = true;
			return;
		}

		// These come after the claim, so a run still open has missed it.
		boolean claimed = widgetId == InterfaceID.DomEndLevelUi.BTN_INV_ALL
			|| widgetId == InterfaceID.DomEndLevelUi.BTN_BANK_ALL;

		// Leave sits beside them on the claimed loot's screen.
		if (claimed || widgetId == InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			finishClaim();
		}
	}

	/** The claim script fires mid-tick, so the pile is read once the tick's events are through. */
	void scriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.DOM_LOOT_CLAIM && run.get() != null)
		{
			clientThread.invokeLater(this::finishClaim);
		}
	}

	private void finishClaim()
	{
		if (run.get() == null)
		{
			return;
		}

		claimLootPile();
		finishRun.run();
	}

	/** Records what the claim took. Only read at the claim: an unclaimed pile is lost on death. */
	private void claimLootPile()
	{
		DelveRun current = run.get();

		// Both copies of the pile, taking the larger count of each drop - but not one last sent on an
		// earlier trip, whose uniques are not this run's.
		Map<Integer, Integer> claimed = new LinkedHashMap<>();

		for (int pile : PILES)
		{
			if (!stalePiles.contains(pile))
			{
				notableDrops(client.getItemContainer(pile))
					.forEach((itemId, quantity) -> claimed.merge(itemId, quantity, Math::max));
			}
		}

		claimed.forEach((itemId, quantity) ->
		{
			current.loot().recordLoot(itemId, items.name(itemId), quantity);
			log.debug("Loot pile holds {} x item {} on delve {}",
				quantity, itemId, current.currentLevel());
		});
	}

	/** Notable drop counts in a pile, totalled across slots. Empty for a missing pile. */
	private static Map<Integer, Integer> notableDrops(ItemContainer pile)
	{
		Map<Integer, Integer> drops = new LinkedHashMap<>();

		if (pile == null)
		{
			return drops;
		}

		for (Item item : pile.getItems())
		{
			if (NOTABLE_DROPS.contains(item.getId()))
			{
				drops.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}

		return drops;
	}

	private static boolean isEmpty(ItemContainer container)
	{
		if (container == null)
		{
			return true;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				return false;
			}
		}

		return true;
	}
}
