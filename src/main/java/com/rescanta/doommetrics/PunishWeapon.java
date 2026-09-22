package com.rescanta.doommetrics;

import net.runelite.api.gameval.ItemID;

/**
 * The melee weapons a punish is told apart by. Other melee lands in {@link #OTHER}. A halberd's
 * spec is credited here, not as spec damage, since it is only fired at a punish.
 */
enum PunishWeapon
{
	SCYTHE(CombatMetric.SCYTHE_PUNISH),
	NOXIOUS_HALBERD(CombatMetric.NOXIOUS_HALBERD_PUNISH),
	CRYSTAL_HALBERD(CombatMetric.CRYSTAL_HALBERD_PUNISH),

	/**
	 * Any other melee weapon. Which ones are melee is the caller's to say - see {@link #isMelee}.
	 */
	OTHER(CombatMetric.OTHER_MELEE_PUNISH);

	private final CombatMetric metric;

	PunishWeapon(CombatMetric metric)
	{
		this.metric = metric;
	}

	CombatMetric metric()
	{
		return metric;
	}

	/**
	 * The weapon held, falling back to its name for forms the id list misses (holy and sanguine
	 * scythes).
	 *
	 * @param name the item's name from the cache, or null if it could not be read
	 * @return {@link #OTHER} for anything not named, melee or not; an empty hand is the caller's
	 */
	static PunishWeapon forItem(int itemId, String name)
	{
		switch (itemId)
		{
			case ItemID.SCYTHE_OF_VITUR:
			case ItemID.SCYTHE_OF_VITUR_OR:
			case ItemID.SCYTHE_OF_VITUR_BL:
			case ItemID.DEADMAN_BLIGHTED_SCYTHE_OF_VITUR:
				return SCYTHE;

			case ItemID.NOXIOUS_HALBERD:
			case ItemID.BR_NOXIOUS_HALBERD:
				return NOXIOUS_HALBERD;

			case ItemID.CRYSTAL_HALBERD:
			case ItemID.CRYSTAL_HALBERD_2500:
				return CRYSTAL_HALBERD;

			default:
				break;
		}

		if (name == null)
		{
			return OTHER;
		}

		String lower = name.toLowerCase();

		if (lower.contains("scythe of vitur"))
		{
			return SCYTHE;
		}

		if (lower.contains("noxious halberd"))
		{
			return NOXIOUS_HALBERD;
		}

		return lower.contains("crystal halberd") ? CRYSTAL_HALBERD : OTHER;
	}

	/** Whether a weapon's best melee attack bonus beats both ranged and magic. */
	static boolean isMelee(int stab, int slash, int crush, int ranged, int magic)
	{
		return Math.max(stab, Math.max(slash, crush)) > Math.max(ranged, magic);
	}
}
