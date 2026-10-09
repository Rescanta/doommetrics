package com.rescanta.doommetrics;

/**
 * What a hit on the boss earns in hitpoints experience, read off the boss's hitpoints: 4/3 a point
 * of damage, times a bonus that steps up with how much health the boss has. Measured as 1.625 at
 * 525 hitpoints, 1.65 at 550 and 575, 1.675 at 600 and 625, and 1.70 at 650 and 675. It is exact
 * enough to say what a swing's hits come to, which the punish tracker tells a thrall's hit by.
 */
final class ExperienceRate
{
	private static final int LEAST_HITPOINTS = 525;
	private static final int MOST_HITPOINTS = 675;

	private static final double BASE_BONUS = 1.625;
	private static final double BONUS_STEP = 0.025;

	/** How many hitpoints the boss gains before the bonus goes up a step. */
	private static final int HITPOINTS_PER_STEP = 50;

	private static final double PER_DAMAGE = 4.0 / 3.0;

	private ExperienceRate()
	{
	}

	/**
	 * Hitpoints experience per point of damage on a boss with this many hitpoints, or 0 when that
	 * is not an amount the boss has.
	 */
	static double perDamage(int maxHitpoints)
	{
		if (maxHitpoints < LEAST_HITPOINTS || maxHitpoints > MOST_HITPOINTS)
		{
			return 0;
		}

		int steps = (maxHitpoints - LEAST_HITPOINTS + HITPOINTS_PER_STEP - 1) / HITPOINTS_PER_STEP;
		return PER_DAMAGE * (BASE_BONUS + BONUS_STEP * steps);
	}

	/**
	 * The least damage that earns this much. The game keeps a fraction of a point the drop does not
	 * show, so a drop can be one either side of what its damage works out to.
	 */
	static int leastDamage(int experience, double perDamage)
	{
		return Math.max(0, (int) Math.ceil((experience - 1) / perDamage));
	}

	/** The most damage that earns this much - see {@link #leastDamage}. */
	static int mostDamage(int experience, double perDamage)
	{
		return (int) Math.floor((experience + 1) / perDamage);
	}
}
