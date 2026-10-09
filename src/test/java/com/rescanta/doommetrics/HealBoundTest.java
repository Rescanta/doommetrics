package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HealBoundTest
{
	@Test
	public void theSaradominGodswordGivesBackHalfAndAQuarterWithAFloor()
	{
		assertEquals(30, HealBound.sgsHeal(60));
		assertEquals(15, HealBound.sgsPrayer(60));
		assertEquals(10, HealBound.sgsHeal(7));
		assertEquals(5, HealBound.sgsPrayer(7));
		assertEquals(10, HealBound.sgsHeal(0));
	}

	@Test
	public void theBlowpipeGivesBackHalfRoundedDown()
	{
		assertEquals(15, HealBound.blowpipeHeal(31));
		assertEquals(0, HealBound.blowpipeHeal(0));
	}

	@Test
	public void aBloodSpellGivesBackAQuarterAndTheBloodFuryThreeTenths()
	{
		assertEquals(9, HealBound.bloodSpellHeal(39));
		assertEquals(11, HealBound.bloodFuryHeal(39));
	}

	@Test
	public void onlySomeSpecsHealByTheirHit()
	{
		assertEquals(20, HealBound.specHeal(SpecWeapon.SARADOMIN_GODSWORD, 40));
		assertEquals(20, HealBound.specHeal(SpecWeapon.BLOWPIPE, 40));
		assertEquals(-1, HealBound.specHeal(SpecWeapon.ANCIENT_GODSWORD, 40));
		assertEquals(-1, HealBound.specHeal(SpecWeapon.ZARYTE_CROSSBOW, 40));
	}
}
