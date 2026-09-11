package com.rescanta.doommetrics;

import net.runelite.api.gameval.ItemID;

/**
 * The melee weapons a punish is told apart by, and the figure each one's punish is credited to.
 *
 * <p>Only three are named, because only three get swung at a punish often enough to be worth a row:
 * the scythe and both halberds. Every other melee weapon - a dragon dagger, dragon or burning claws
 * - still punished, and lands in {@link #OTHER} rather than being thrown away.
 *
 * <p>A halberd's spec is credited here and not under spec damage. It is never fired at anything but
 * a punish, so the punish is what it is for, and the spec's hits reaching the spec figure as well
 * would count the same damage twice.
 */
enum PunishWeapon
{
	SCYTHE(CombatMetric.SCYTHE_PUNISH),
	NOXIOUS_HALBERD(CombatMetric.NOXIOUS_HALBERD_PUNISH),
	CRYSTAL_HALBERD(CombatMetric.CRYSTAL_HALBERD_PUNISH),

	/** Any other melee weapon. Which ones are melee is the caller's to say - see {@link #isMelee}. */
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
	 * The weapon held, falling back to its name for the forms the id list misses - the holy and
	 * sanguine scythes above all, which are a kit on the scythe and have ids of their own, and
	 * which a name match catches without anybody having to keep up with them.
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

	/**
	 * Whether a weapon with these attack bonuses is a melee weapon: its best melee style beats
	 * both ranged and magic.
	 *
	 * <p>Read from the bonuses rather than listed, because {@link #OTHER} is any melee weapon at all
	 * and no list keeps up with that. What it has to keep out is the attack a punish is not: a
	 * blowpipe or a staff swung at the boss while it prays hits for nothing, and a weapon read as
	 * melee there would take the strength-bonus splats of a real punish landing a tick later.
	 */
	static boolean isMelee(int stab, int slash, int crush, int ranged, int magic)
	{
		return Math.max(stab, Math.max(slash, crush)) > Math.max(ranged, magic);
	}
}
