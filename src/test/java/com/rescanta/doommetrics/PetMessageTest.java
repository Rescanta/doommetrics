package com.rescanta.doommetrics;

import net.runelite.client.util.Text;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PetMessageTest
{
	/** The line exactly as the game sent it on a claim, colour and all. */
	@Test
	public void theColouredLineFromAClaimIsThePet()
	{
		String sent = "<col=ef1020>You have a funny feeling like you would have been followed...</col>";

		assertFalse(LootWatcher.isPetMessage(sent));
		assertTrue(LootWatcher.isPetMessage(Text.removeTags(sent)));
	}

	@Test
	public void everyWayThePetArrivesIsRecognised()
	{
		assertTrue(LootWatcher.isPetMessage("You have a funny feeling like you're being followed."));
		assertTrue(LootWatcher.isPetMessage(
			"You feel something weird sneaking into your backpack."));
	}

	@Test
	public void otherLinesAreNot()
	{
		assertFalse(LootWatcher.isPetMessage("Deep delves completed: 6,900"));
	}
}
