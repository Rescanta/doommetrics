package com.rescanta.doommetrics;

import java.util.Arrays;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RunLootTest
{
	/** The claim is read more than once - the script, the claimed pile, the take-all buttons. */
	@Test
	public void aClaimReadAgainIsNotCountedTwice()
	{
		RunLoot loot = new RunLoot();
		loot.recordLoot(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		loot.recordLoot(ItemID.AVERNIC_TREADS, "Avernic treads", 1);

		assertEquals(Arrays.asList("Avernic treads"), loot.getClaimed());
	}

	/** A deep run can roll the same unique twice; the larger count wins and is listed twice. */
	@Test
	public void aUniqueClaimedTwiceIsListedTwice()
	{
		RunLoot loot = new RunLoot();
		loot.recordLoot(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 1);
		loot.recordLoot(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 2);
		loot.recordLoot(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 1);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Mokhaiotl cloth"), loot.getClaimed());
	}

	/** Two copies of the pile can name the eye either way; it is one drop. */
	@Test
	public void bothFormsOfTheEyeAreOneDrop()
	{
		RunLoot loot = new RunLoot();
		loot.recordLoot(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)", 1);
		loot.recordLoot(ItemID.EYE_OF_AYAK, "Eye of ayak", 1);

		assertEquals(Arrays.asList("Eye of ayak (uncharged)"), loot.getClaimed());
	}

	@Test
	public void nothingUnnamedOrEmptyIsRecorded()
	{
		RunLoot loot = new RunLoot();
		loot.recordLoot(ItemID.DOMPET, null, 1);
		loot.recordLoot(ItemID.DOMPET, "Dom", 0);

		assertEquals(Arrays.asList(), loot.getClaimed());
	}
}
