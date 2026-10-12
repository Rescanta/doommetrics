package com.rescanta.doommetrics;

import net.runelite.api.gameval.ItemID;

/**
 * The food and potions whose heal or prayer restore is a set share of a trained level. It is
 * worked out, not learned from a rise, so a dose that shares its tick with something else that
 * heals or restores can't be taught the wrong size.
 */
enum KnownDose
{
	PRAYER_POTION(0, 0, 25, 7, ItemID._4DOSEPRAYERRESTORE, ItemID._3DOSEPRAYERRESTORE,
		ItemID._2DOSEPRAYERRESTORE, ItemID._1DOSEPRAYERRESTORE),
	SUPER_RESTORE(0, 0, 25, 8, ItemID._4DOSE2RESTORE, ItemID._3DOSE2RESTORE,
		ItemID._2DOSE2RESTORE, ItemID._1DOSE2RESTORE),
	SANFEW_SERUM(0, 0, 30, 4, ItemID.SANFEW_SALVE_4_DOSE, ItemID.SANFEW_SALVE_3_DOSE,
		ItemID.SANFEW_SALVE_2_DOSE, ItemID.SANFEW_SALVE_1_DOSE),

	/** Nothing on the sip: its points drip in afterwards, and {@link Regeneration} takes those. */
	PRAYER_REGENERATION(0, 0, 0, 0, ItemID._4DOSE1PRAYER_REGENERATION,
		ItemID._3DOSE1PRAYER_REGENERATION, ItemID._2DOSE1PRAYER_REGENERATION,
		ItemID._1DOSE1PRAYER_REGENERATION),

	SARADOMIN_BREW(15, 2, 0, 0, ItemID._4DOSEPOTIONOFSARADOMIN, ItemID._3DOSEPOTIONOFSARADOMIN,
		ItemID._2DOSEPOTIONOFSARADOMIN, ItemID._1DOSEPOTIONOFSARADOMIN),
	ARMADYL_BREW(10, 2, 0, 0, ItemID._4DOSEARMADYLBREW, ItemID._3DOSEARMADYLBREW,
		ItemID._2DOSEARMADYLBREW, ItemID._1DOSEARMADYLBREW),

	/** A tenth of the level and a step that rises with it - see {@link #heals}. */
	ANGLERFISH(10, 0, 0, 0, ItemID.ANGLERFISH);

	/** What a holy wrench, a prayer or max cape or an imbued ring of the gods adds to the share. */
	private static final int BOOST = 2;

	private final int healPercent;
	private final int healFlat;
	private final int restorePercent;
	private final int restoreFlat;
	private final int[] itemIds;

	KnownDose(int healPercent, int healFlat, int restorePercent, int restoreFlat, int... itemIds)
	{
		this.healPercent = healPercent;
		this.healFlat = healFlat;
		this.restorePercent = restorePercent;
		this.restoreFlat = restoreFlat;
		this.itemIds = itemIds;
	}

	/** What a dose of any size, or a bite, belongs to; null for anything else. */
	static KnownDose of(int itemId)
	{
		for (KnownDose dose : values())
		{
			for (int each : dose.itemIds)
			{
				if (each == itemId)
				{
					return dose;
				}
			}
		}

		return null;
	}

	/**
	 * The hitpoints one dose gives with room for all of it. The brews and the anglerfish heal
	 * past the trained level, by as much as one dose gives, so the room is seldom short.
	 *
	 * @param level the trained hitpoints level
	 */
	int heals(int level)
	{
		if (healPercent == 0)
		{
			return 0;
		}

		return level * healPercent / 100 + (this == ANGLERFISH ? anglerfishStep(level) : healFlat);
	}

	private static int anglerfishStep(int level)
	{
		if (level < 25)
		{
			return 2;
		}

		if (level < 50)
		{
			return 4;
		}

		if (level < 75)
		{
			return 6;
		}

		return level < 93 ? 8 : 13;
	}

	/**
	 * The prayer one dose restores with room for all of it.
	 *
	 * @param level   the trained prayer level
	 * @param boosted whether something carried or worn raises the share - see {@link #boosts}
	 */
	int restores(int level, boolean boosted)
	{
		if (restorePercent == 0)
		{
			return 0;
		}

		return level * (restorePercent + (boosted ? BOOST : 0)) / 100 + restoreFlat;
	}

	/** Whether it is drunk; the anglerfish is eaten. */
	boolean isDrink()
	{
		return this != ANGLERFISH;
	}

	/**
	 * Whether an item raises the prayer share. The wrench works from the inventory, the ring only
	 * worn, a prayer cape either way. A max cape does too, and is the caller's to tell by its name.
	 */
	static boolean boosts(int itemId, boolean worn)
	{
		switch (itemId)
		{
			case ItemID.SKILLCAPE_PRAYER:
			case ItemID.SKILLCAPE_PRAYER_TRIMMED:
				return true;
			case ItemID.DEAL_WRENCH_BLESSED:
				return !worn;
			case ItemID.NZONE_ROTG:
			case ItemID.SW_ROTG:
			case ItemID.PVPA_ROTG:
				return worn;
			default:
				return false;
		}
	}
}
