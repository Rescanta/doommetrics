package com.rescanta.doommetrics;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What a dose gives, against the sizes the game gives at a few trained levels. */
public class KnownDoseTest
{
	@Test
	public void aPrayerDoseRestoresAShareOfTheTrainedLevel()
	{
		assertEquals(31, KnownDose.PRAYER_POTION.restores(99, false));
		assertEquals(32, KnownDose.SUPER_RESTORE.restores(99, false));
		assertEquals(33, KnownDose.SANFEW_SERUM.restores(99, false));

		assertEquals(17, KnownDose.PRAYER_POTION.restores(43, false));
		assertEquals(25, KnownDose.SUPER_RESTORE.restores(70, false));
		assertEquals(25, KnownDose.SANFEW_SERUM.restores(70, false));

		assertEquals(7, KnownDose.PRAYER_POTION.restores(1, false));
		assertEquals(8, KnownDose.SUPER_RESTORE.restores(1, false));
		assertEquals(4, KnownDose.SANFEW_SERUM.restores(1, false));
	}

	/** The top size is reached at 96 with nothing boosting it, and only at 97 with a wrench. */
	@Test
	public void theShareIsRoundedDown()
	{
		assertEquals(31, KnownDose.SUPER_RESTORE.restores(95, false));
		assertEquals(32, KnownDose.SUPER_RESTORE.restores(96, false));
		assertEquals(33, KnownDose.SUPER_RESTORE.restores(96, true));
		assertEquals(34, KnownDose.SUPER_RESTORE.restores(97, true));
	}

	@Test
	public void aWrenchAddsTwoInAHundredOfTheLevel()
	{
		assertEquals(33, KnownDose.PRAYER_POTION.restores(99, true));
		assertEquals(34, KnownDose.SUPER_RESTORE.restores(99, true));
		assertEquals(35, KnownDose.SANFEW_SERUM.restores(99, true));
		assertEquals(18, KnownDose.PRAYER_POTION.restores(43, true));
	}

	@Test
	public void aRegenerationDoseRestoresNothingOnTheSip()
	{
		assertEquals(0, KnownDose.PRAYER_REGENERATION.restores(99, false));
		assertEquals(0, KnownDose.PRAYER_REGENERATION.restores(99, true));
		assertEquals(0, KnownDose.PRAYER_REGENERATION.heals(99));
	}

	/** 16 and 11 at 99, as every sip in the logs. */
	@Test
	public void aBrewHealsAShareOfTheTrainedLevel()
	{
		assertEquals(16, KnownDose.SARADOMIN_BREW.heals(99));
		assertEquals(16, KnownDose.SARADOMIN_BREW.heals(94));
		assertEquals(15, KnownDose.SARADOMIN_BREW.heals(93));
		assertEquals(3, KnownDose.SARADOMIN_BREW.heals(10));

		assertEquals(11, KnownDose.ARMADYL_BREW.heals(99));
		assertEquals(10, KnownDose.ARMADYL_BREW.heals(89));
		assertEquals(2, KnownDose.ARMADYL_BREW.heals(9));
	}

	@Test
	public void anAnglerfishHealsATenthAndAStep()
	{
		assertEquals(3, KnownDose.ANGLERFISH.heals(10));
		assertEquals(4, KnownDose.ANGLERFISH.heals(24));
		assertEquals(6, KnownDose.ANGLERFISH.heals(25));
		assertEquals(8, KnownDose.ANGLERFISH.heals(49));
		assertEquals(11, KnownDose.ANGLERFISH.heals(50));
		assertEquals(13, KnownDose.ANGLERFISH.heals(74));
		assertEquals(15, KnownDose.ANGLERFISH.heals(75));
		assertEquals(17, KnownDose.ANGLERFISH.heals(92));
		assertEquals(22, KnownDose.ANGLERFISH.heals(93));
		assertEquals(22, KnownDose.ANGLERFISH.heals(99));
	}

	@Test
	public void whatHealsRestoresNoPrayerAndWhatRestoresDoesNotHeal()
	{
		assertEquals(0, KnownDose.SARADOMIN_BREW.restores(99, true));
		assertEquals(0, KnownDose.ANGLERFISH.restores(99, false));
		assertEquals(0, KnownDose.SUPER_RESTORE.heals(99));
		assertTrue(KnownDose.ARMADYL_BREW.isDrink());
		assertFalse(KnownDose.ANGLERFISH.isDrink());
	}

	@Test
	public void everyDoseIsThePotion()
	{
		assertEquals(KnownDose.PRAYER_POTION, KnownDose.of(ItemID._1DOSEPRAYERRESTORE));
		assertEquals(KnownDose.SUPER_RESTORE, KnownDose.of(ItemID._4DOSE2RESTORE));
		assertEquals(KnownDose.SUPER_RESTORE, KnownDose.of(ItemID._1DOSE2RESTORE));
		assertEquals(KnownDose.SANFEW_SERUM, KnownDose.of(ItemID.SANFEW_SALVE_2_DOSE));
		assertEquals(KnownDose.PRAYER_REGENERATION,
			KnownDose.of(ItemID._3DOSE1PRAYER_REGENERATION));
		assertEquals(KnownDose.SARADOMIN_BREW, KnownDose.of(ItemID._2DOSEPOTIONOFSARADOMIN));
		assertEquals(KnownDose.ARMADYL_BREW, KnownDose.of(ItemID._1DOSEARMADYLBREW));
		assertEquals(KnownDose.ANGLERFISH, KnownDose.of(ItemID.ANGLERFISH));
		assertNull(KnownDose.of(ItemID.SGS));
	}

	@Test
	public void theWrenchWorksCarriedTheRingWornAndTheCapeEitherWay()
	{
		assertTrue(KnownDose.boosts(ItemID.DEAL_WRENCH_BLESSED, false));
		assertFalse(KnownDose.boosts(ItemID.DEAL_WRENCH_BLESSED, true));

		assertTrue(KnownDose.boosts(ItemID.NZONE_ROTG, true));
		assertFalse(KnownDose.boosts(ItemID.NZONE_ROTG, false));
		assertFalse(KnownDose.boosts(ItemID.ROTG, true));

		assertTrue(KnownDose.boosts(ItemID.SKILLCAPE_PRAYER, true));
		assertTrue(KnownDose.boosts(ItemID.SKILLCAPE_PRAYER_TRIMMED, false));
		assertFalse(KnownDose.boosts(ItemID.SGS, true));
	}
}
