package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * The special attacks worth telling apart, recognised by the weapon held when the energy was spent.
 * Each lists the effects it produces and when they may arrive - see {@link SpecEffect}.
 */
enum SpecWeapon
{
	ZARYTE_CROSSBOW("Zaryte crossbow", projectile(CombatMetric.ZCB_DAMAGE, 1)),

	/**
	 * Dragon knife. Duality throws two knives at once, each rolling its own hit, and heals nothing.
	 */
	DRAGON_KNIFE("Dragon knife", projectile(CombatMetric.OTHER_SPEC_DAMAGE, 2)),

	DRAGON_THROWNAXE("Dragon thrownaxe", projectile(CombatMetric.OTHER_SPEC_DAMAGE, 1)),

	/**
	 * Rosewood blowpipe. Rapid Burst fires two darts that land on the same tick, and heals nothing.
	 */
	ROSEWOOD_BLOWPIPE("Rosewood blowpipe", projectile(CombatMetric.OTHER_SPEC_DAMAGE, 2)),

	/** Toxic blowpipe. One dart, healing half of what it hits for, both landing together. */
	BLOWPIPE("Toxic blowpipe",
		projectile(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		projectileHeal(CombatMetric.BLOWPIPE_HEAL, 1)),

	/** Ancient godsword: the swing, then Blood Sacrifice's damage and heal eight ticks later. */
	ANCIENT_GODSWORD("Ancient godsword",
		swing(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		sacrificeDamage(),
		sacrificeHeal()),

	/** Saradomin godsword. Healing Blade heals half of what the swing hits for, as it lands. */
	SARADOMIN_GODSWORD("Saradomin godsword",
		swing(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		swingHeal(CombatMetric.SGS_HEAL)),

	/** Eldritch nightmare staff. Restores prayer rather than hitpoints, both when the spell lands. */
	ELDRITCH_STAFF("Eldritch staff",
		projectile(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		prayer(CombatMetric.ELDRITCH_PRAYER, 2)),

	/** Every other melee spec. Four hits covers dragon claws. None of them heals. */
	OTHER(null, swing(CombatMetric.OTHER_SPEC_DAMAGE, 4)),

	/** Every other ranged or magic spec, whose hits have to fly - see {@link #FLIGHT}. */
	OTHER_FIRED(null, projectile(CombatMetric.OTHER_SPEC_DAMAGE, 4));

	/** How long a spec's own hit may take to arrive, in ticks. */
	private static final int PROMPT = 3;

	/**
	 * The earliest a melee spec lands: a tick after the energy moves. A hit on the spec's own tick
	 * is the attack before it.
	 */
	private static final int SWING = 1;

	/** The earliest a thrown or fired spec can land; keeps out the auto-attack thrown before it. */
	private static final int FLIGHT = 2;

	/** How long after an Eldritch spec its restore may arrive: it lands when the spell does. */
	private static final int RESTORE = 7;

	/** When Blood Sacrifice pays out: eight or nine ticks after the spec, give or take one. */
	private static final int SACRIFICE_FROM = 7;

	private static final int SACRIFICE_TO = 10;

	private static final int SACRIFICE_DAMAGE = 25;

	private final String label;
	private final List<SpecEffect> effects;

	SpecWeapon(String label, SpecEffect... effects)
	{
		this.label = label;
		this.effects = Collections.unmodifiableList(Arrays.asList(effects));
	}

	private static SpecEffect swing(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, metric, SWING, PROMPT, budget);
	}

	/** A spec's hit that has to fly to its target - see {@link #FLIGHT}. */
	private static SpecEffect projectile(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, metric, FLIGHT, PROMPT, budget);
	}

	/** A heal that lands with a melee spec's hit. */
	private static SpecEffect swingHeal(CombatMetric metric)
	{
		return new SpecEffect(SpecEffect.Kind.HEAL, metric, SWING, PROMPT, 1);
	}

	/** A heal that lands with a projectile's hit. */
	private static SpecEffect projectileHeal(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.HEAL, metric, FLIGHT, PROMPT, budget);
	}

	private static SpecEffect prayer(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.PRAYER, metric, FLIGHT, RESTORE, budget);
	}

	/**
	 * Blood Sacrifice's hit, pinned to the 25 it always deals so a hit landing first isn't taken.
	 */
	private static SpecEffect sacrificeDamage()
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, CombatMetric.OTHER_SPEC_DAMAGE,
			SACRIFICE_FROM, SACRIFICE_TO, 1, SACRIFICE_DAMAGE);
	}

	/** The heal after it, not pinned: it is capped by missing hitpoints. */
	private static SpecEffect sacrificeHeal()
	{
		return new SpecEffect(SpecEffect.Kind.HEAL, CombatMetric.AGS_HEAL,
			SACRIFICE_FROM, SACRIFICE_TO + 1, 1);
	}

	List<SpecEffect> effects()
	{
		return effects;
	}

	/**
	 * The weapon's name as the panel lists it, or null for the catch-alls, which are no one weapon.
	 */
	String label()
	{
		return label;
	}

	/** {@link #OTHER_FIRED} for an unnamed weapon that isn't melee; any other weapon unchanged. */
	SpecWeapon fired(boolean melee)
	{
		return this == OTHER && !melee ? OTHER_FIRED : this;
	}

	/** Whether anything this weapon's spec produces is credited to {@code metric}. */
	boolean credits(CombatMetric metric)
	{
		for (SpecEffect effect : effects)
		{
			if (effect.metric() == metric)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * The weapon held, or null if the slot is empty. Unnamed weapons are {@link #OTHER}; the caller
	 * tells a fired one apart with {@link #fired}.
	 */
	static SpecWeapon forItem(int itemId)
	{
		switch (itemId)
		{
			case ItemID.ZARYTE_XBOW:
			case ItemID.BR_ZARYTE_XBOW:
				return ZARYTE_CROSSBOW;

			case ItemID.DRAGON_KNIFE:
			case ItemID.DRAGON_KNIFE_P:
			case ItemID.DRAGON_KNIFE_P_:
			case ItemID.DRAGON_KNIFE_P__:
			case ItemID.BR_DRAGON_KNIFE:
				return DRAGON_KNIFE;

			case ItemID.DRAGON_THROWNAXE:
			case ItemID.BR_DRAGON_THROWNAXE:
				return DRAGON_THROWNAXE;

			// The empty one for the same reason as the toxic blowpipe's below.
			case ItemID.ROSEWOOD_BLOWPIPE:
			case ItemID.ROSEWOOD_BLOWPIPE_EMPTY:
				return ROSEWOOD_BLOWPIPE;

			case ItemID.ANCIENT_GODSWORD:
			case ItemID.BR_ANCIENT_GODSWORD:
				return ANCIENT_GODSWORD;

			case ItemID.SGS:
			case ItemID.SGSG:
				return SARADOMIN_GODSWORD;

			// Loaded and empty forms, and their ornaments: the last shot swaps to empty.
			case ItemID.TOXIC_BLOWPIPE:
			case ItemID.TOXIC_BLOWPIPE_LOADED:
			case ItemID.TOXIC_BLOWPIPE_ORNAMENT:
			case ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT:
				return BLOWPIPE;

			case ItemID.NIGHTMARE_STAFF_ELDRITCH:
				return ELDRITCH_STAFF;

			default:
				return itemId <= 0 ? null : OTHER;
		}
	}

	/**
	 * The weapon held, falling back to its name for ids we don't list. The toxic blowpipe is
	 * matched by its own name, not the word blowpipe, which non-healing Sailing blowpipes carry.
	 *
	 * @param name the item's name from the cache, or null if it could not be read
	 */
	static SpecWeapon forItem(int itemId, String name)
	{
		SpecWeapon known = forItem(itemId);

		if (known != OTHER || name == null)
		{
			return known;
		}

		String lower = name.toLowerCase();

		if (lower.contains("toxic blowpipe") || lower.contains("blazing blowpipe"))
		{
			return BLOWPIPE;
		}

		if (lower.contains("rosewood blowpipe"))
		{
			return ROSEWOOD_BLOWPIPE;
		}

		if (lower.contains("dragon knife"))
		{
			return DRAGON_KNIFE;
		}

		if (lower.contains("dragon thrownaxe"))
		{
			return DRAGON_THROWNAXE;
		}

		if (lower.contains("ancient godsword"))
		{
			return ANCIENT_GODSWORD;
		}

		if (lower.contains("saradomin godsword"))
		{
			return SARADOMIN_GODSWORD;
		}

		if (lower.contains("zaryte crossbow"))
		{
			return ZARYTE_CROSSBOW;
		}

		return lower.contains("eldritch") ? ELDRITCH_STAFF : OTHER;
	}
}
