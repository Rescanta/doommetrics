package com.rescanta.doommetrics;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PunishWeaponTest
{
	@Test
	public void theNamedWeaponsAreRecognisedByTheirIds()
	{
		assertEquals(PunishWeapon.SCYTHE, PunishWeapon.forItem(ItemID.SCYTHE_OF_VITUR, null));
		assertEquals(PunishWeapon.SCYTHE, PunishWeapon.forItem(ItemID.SCYTHE_OF_VITUR_OR, null));
		assertEquals(PunishWeapon.NOXIOUS_HALBERD,
			PunishWeapon.forItem(ItemID.NOXIOUS_HALBERD, null));
		assertEquals(PunishWeapon.CRYSTAL_HALBERD,
			PunishWeapon.forItem(ItemID.CRYSTAL_HALBERD, null));
	}

	/** The kits and charge states the list will always be missing are caught by name. */
	@Test
	public void anUnlistedFormIsStillRecognisedByName()
	{
		assertEquals(PunishWeapon.SCYTHE,
			PunishWeapon.forItem(999_999, "Sanguine scythe of vitur"));
		assertEquals(PunishWeapon.NOXIOUS_HALBERD,
			PunishWeapon.forItem(999_999, "Noxious halberd"));
		assertEquals(PunishWeapon.CRYSTAL_HALBERD,
			PunishWeapon.forItem(999_999, "Crystal halberd (Cadarn)"));

		assertEquals(PunishWeapon.OTHER, PunishWeapon.forItem(ItemID.DRAGON_CLAWS, "Dragon claws"));
		assertEquals(PunishWeapon.OTHER, PunishWeapon.forItem(999_999, null));
	}

	/** A listed id is never second-guessed by the name. */
	@Test
	public void aListedIdWinsOverTheName()
	{
		assertEquals(PunishWeapon.SCYTHE,
			PunishWeapon.forItem(ItemID.SCYTHE_OF_VITUR, "Crystal halberd"));
	}

	@Test
	public void meleeIsReadFromTheBonuses()
	{
		// Dragon dagger, dragon claws, and a scythe that is all slash.
		assertTrue(PunishWeapon.isMelee(40, 25, -4, 0, 1));
		assertTrue(PunishWeapon.isMelee(41, 57, -4, 0, 0));
		assertTrue(PunishWeapon.isMelee(70, 110, 30, 0, -6));

		// A toxic blowpipe and an eldritch nightmare staff, whose melee bonuses are nothing.
		assertFalse(PunishWeapon.isMelee(0, 0, 0, 30, 0));
		assertFalse(PunishWeapon.isMelee(0, 0, 0, 0, 16));
	}
}
