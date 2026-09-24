package com.rescanta.doommetrics;

import java.util.function.Supplier;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/** Notices the claim and leaving that end a run. What was claimed isn't read. Client thread only. */
class ClaimWatcher
{
	private static final String DESCEND_OPTION = "Descend";

	private final ClientThread clientThread;
	private final Supplier<DelveRun> run;

	/** Ends the run as finished: the claim went through, or the player left. */
	private final Runnable finishRun;

	/** "Claim and leave" was clicked; only then does the claimed loot filling in end the run. */
	private boolean claimRequested;

	ClaimWatcher(ClientThread clientThread, Supplier<DelveRun> run, Runnable finishRun)
	{
		this.clientThread = clientThread;
		this.run = run;
		this.finishRun = finishRun;
	}

	/** A run started or stopped, or a new delve did: no claim is on its way. */
	void reset()
	{
		claimRequested = false;
	}

	/** The claimed loot filling in after "Claim and leave" is the claim going through. */
	void itemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.DOM_LOOTPILE && claimRequested
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

		// These come after the claim, so a run still open has missed it. Leave sits beside them on
		// the claimed loot's screen.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_INV_ALL
			|| widgetId == InterfaceID.DomEndLevelUi.BTN_BANK_ALL
			|| widgetId == InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			finishClaim();
		}
	}

	/** The claim script fires mid-tick, so the run ends once the tick's events are through. */
	void scriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.DOM_LOOT_CLAIM && run.get() != null)
		{
			clientThread.invokeLater(this::finishClaim);
		}
	}

	private void finishClaim()
	{
		claimRequested = false;

		if (run.get() != null)
		{
			finishRun.run();
		}
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
