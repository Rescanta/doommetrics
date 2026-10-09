package com.rescanta.doommetrics;

/**
 * What a hit gives back to the player who made it, where that follows from its size. A rise on the
 * hit's tick can be no more than this, and what is over it came from something else.
 */
final class HealBound
{
	/** Healing Blade never gives less than these, however small the hit. */
	private static final int LEAST_SGS_HEAL = 10;
	private static final int LEAST_SGS_PRAYER = 5;

	private HealBound()
	{
	}

	/** Saradomin godsword: half of the hit. */
	static int sgsHeal(int damage)
	{
		return Math.max(LEAST_SGS_HEAL, damage / 2);
	}

	/** Saradomin godsword: a quarter of the hit. */
	static int sgsPrayer(int damage)
	{
		return Math.max(LEAST_SGS_PRAYER, damage / 4);
	}

	/** Toxic blowpipe spec: half of the hit. */
	static int blowpipeHeal(int damage)
	{
		return damage / 2;
	}

	/** A blood spell: a quarter of what it hit for over every target. */
	static int bloodSpellHeal(int damage)
	{
		return damage / 4;
	}

	/** Amulet of blood fury, when it heals at all: three tenths of a melee hit. */
	static int bloodFuryHeal(int damage)
	{
		return damage * 3 / 10;
	}

	/**
	 * What a spec's hit gives back as hitpoints, or -1 for a weapon whose heal does not follow from
	 * the hit.
	 */
	static int specHeal(SpecWeapon weapon, int damage)
	{
		switch (weapon)
		{
			case SARADOMIN_GODSWORD:
				return sgsHeal(damage);

			case BLOWPIPE:
				return blowpipeHeal(damage);

			default:
				return -1;
		}
	}
}
