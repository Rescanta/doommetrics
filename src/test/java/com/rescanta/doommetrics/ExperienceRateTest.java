package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ExperienceRateTest
{
	private static final double PER_DAMAGE = 4.0 / 3.0;

	/** The bonus measured on each delve, against the hitpoints the boss has on it. */
	@Test
	public void theBonusStepsUpWithTheBossesHitpoints()
	{
		assertEquals(1.625, ExperienceRate.perDamage(525) / PER_DAMAGE, 1e-9);
		assertEquals(1.65, ExperienceRate.perDamage(550) / PER_DAMAGE, 1e-9);
		assertEquals(1.65, ExperienceRate.perDamage(575) / PER_DAMAGE, 1e-9);
		assertEquals(1.675, ExperienceRate.perDamage(600) / PER_DAMAGE, 1e-9);
		assertEquals(1.675, ExperienceRate.perDamage(625) / PER_DAMAGE, 1e-9);
		assertEquals(1.70, ExperienceRate.perDamage(650) / PER_DAMAGE, 1e-9);
		assertEquals(1.70, ExperienceRate.perDamage(675) / PER_DAMAGE, 1e-9);
	}

	@Test
	public void hitpointsTheBossNeverHasSayNothing()
	{
		assertEquals(0, ExperienceRate.perDamage(0), 0);
		assertEquals(0, ExperienceRate.perDamage(500), 0);
		assertEquals(0, ExperienceRate.perDamage(700), 0);
	}

	/** A hit of 31 at 625 hitpoints is worth 69.2: a drop of 69 or 70. */
	@Test
	public void aDropIsOneDamageGiveOrTakeItsFraction()
	{
		double rate = ExperienceRate.perDamage(625);

		for (int drop = 69; drop <= 70; drop++)
		{
			assertEquals(31, ExperienceRate.leastDamage(drop, rate));
			assertEquals(31, ExperienceRate.mostDamage(drop, rate));
		}
	}

	/** Every damage's own drop, rounded either way, has that damage inside its range. */
	@Test
	public void theRangeAlwaysHoldsTheDamageThatEarnedIt()
	{
		for (int hitpoints = 525; hitpoints <= 675; hitpoints += 25)
		{
			double rate = ExperienceRate.perDamage(hitpoints);

			for (int damage = 0; damage <= 120; damage++)
			{
				for (int drop = (int) Math.floor(damage * rate); drop <= Math.ceil(damage * rate); drop++)
				{
					String what = damage + " at " + hitpoints + " dropping " + drop;
					assertEquals(what, true, ExperienceRate.leastDamage(drop, rate) <= damage);
					assertEquals(what, true, ExperienceRate.mostDamage(drop, rate) >= damage);
				}
			}
		}
	}
}
