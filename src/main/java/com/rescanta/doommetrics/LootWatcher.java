package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * Places the run's notable drops on the delves they came off, records what the claim took, and
 * notices the claim and leaving that end a run. Client thread only.
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

	/** One per unique copy, on every descend try. */
	private static final Pattern LOOT_WARNING = Pattern.compile("^Your loot contains (.+?)! Are you sure");

	private static final String DESCEND_OPTION = "Descend";

	private final Client client;
	private final ClientThread clientThread;
	private final GameItems items;
	private final Supplier<DelveRun> run;

	/** Ends the run as finished: the claim went through, or the player left. */
	private final Runnable finishRun;

	/** "Claim and leave" was clicked; only then does the claimed loot filling in end the run. */
	private boolean claimRequested;

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

	void runStarted(DelveRun started)
	{
		claimRequested = false;

		if (started.isPartial())
		{
			// Already in the pile, from delves we did not see.
			notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE))
				.forEach(started.loot()::pileAlreadyHeld);
			notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE_DURING))
				.forEach(started.loot()::pileAlreadyHeld);
		}
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

		int pets = Math.max(1, current.loot().held(ItemID.DOMPET));
		int delve = current.loot().sawInPile(ItemID.DOMPET, items.name(ItemID.DOMPET), pets);
		current.loot().recordLoot(ItemID.DOMPET, items.name(ItemID.DOMPET), pets);
		log.debug("Pet claimed, from delve {}",
			delve == RunLoot.NOT_RECORDED ? current.dropLevel() : delve);
	}

	/** A "Your loot contains" warning. The dialog's item is read once it has been filled in. */
	void lootWarning(String message)
	{
		if (run.get() == null)
		{
			return;
		}

		Matcher matcher = LOOT_WARNING.matcher(Text.removeTags(message));

		if (!matcher.find())
		{
			return;
		}

		String named = matcher.group(1);

		clientThread.invokeLater(() ->
		{
			DelveRun current = run.get();

			if (current == null)
			{
				return;
			}

			Widget shown = client.getWidget(InterfaceID.Objectbox.ITEM);
			int itemId = shown != null && NOTABLE_DROPS.contains(shown.getItemId())
				? shown.getItemId()
				: notableNamed(named);

			if (itemId < 0)
			{
				log.debug("Loot warning for \"{}\", which is not a drop we track", named);
				return;
			}

			int delve = current.loot().warnedOf(itemId, items.name(itemId));
			log.debug("Loot warning for item {} while on delve {}, recorded on delve {}",
				itemId, current.dropLevel(), delve == RunLoot.NOT_RECORDED ? "none" : delve);
		});
	}

	/** The notable drop the game calls {@code name}, or -1 for none. */
	private int notableNamed(String name)
	{
		for (int itemId : NOTABLE_DROPS)
		{
			if (name.equalsIgnoreCase(items.name(itemId)))
			{
				return itemId;
			}
		}

		return -1;
	}

	/** Places drops on delves as the pile grows. Claiming is decided separately. */
	void itemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		DelveRun current = run.get();

		if (current == null
			|| (containerId != InventoryID.DOM_LOOTPILE && containerId != InventoryID.DOM_LOOTPILE_DURING))
		{
			return;
		}

		Map<Integer, Integer> drops = notableDrops(event.getItemContainer());
		log.debug("Loot pile {} sent on delve {} (between delves: {}), notable drops {}",
			containerId, current.currentLevel(), current.dropLevel() != current.currentLevel(), drops);

		drops.forEach((itemId, quantity) ->
		{
			int delve = current.loot().sawInPile(itemId, items.name(itemId), quantity);

			if (delve != RunLoot.NOT_RECORDED)
			{
				log.debug("Item {} recorded on delve {}, pile now holds {}", itemId, delve, quantity);
			}
		});

		// The claimed loot filling in after "Claim and leave" is the claim going through.
		if (containerId == InventoryID.DOM_LOOTPILE && claimRequested
			&& !isEmpty(event.getItemContainer()))
		{
			finishClaim();
		}
	}

	void menuOptionClicked(MenuOptionClicked event)
	{
		DelveRun current = run.get();

		if (current == null)
		{
			return;
		}

		int widgetId = event.getParam1();

		// A new descend try - but the Descend on a warning dialog continues the same try.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_DESCEND
			|| (DESCEND_OPTION.equals(Text.removeTags(event.getMenuOption()))
				&& WidgetUtil.componentToInterface(widgetId) != InterfaceID.OBJECTBOX))
		{
			current.loot().descending();
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

		if (claimed)
		{
			finishClaim();
		}
		else if (widgetId == InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			finishRun.run();
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

	/** The glowing hole: the pile holds a unique. Scene rebuilds re-send it, which is harmless. */
	void gameObjectSpawned(GameObjectSpawned event)
	{
		DelveRun current = run.get();

		if (event.getGameObject().getId() != ObjectID.DOM_DESCEND_HOLE_UNIQUE || current == null
			|| !current.isBetweenDelves())
		{
			return;
		}

		if (current.loot().uniqueSignalled())
		{
			log.debug("Delve {} was left by the glowing hole: a unique is in the pile",
				current.dropLevel());
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

		// Both copies of the pile, taking the larger count of each drop.
		Map<Integer, Integer> claimed = notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE));
		notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE_DURING))
			.forEach((itemId, quantity) -> claimed.merge(itemId, quantity, Math::max));

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
