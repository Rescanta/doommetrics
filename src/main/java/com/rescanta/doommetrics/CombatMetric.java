package com.rescanta.doommetrics;

/**
 * The sustain and burst figures tracked across a delve, a sitting and a lifetime.
 *
 * <p>{@link #key} is the stored name in both the history file and the config value, so renaming one
 * silently drops that figure out of every character's saved totals and out of every run already
 * written. The constant may be renamed freely; the key may not.
 *
 * <p>Each metric names a source rather than an effect, because the whole point is telling apart two
 * numbers that arrive looking identical: a blood barrage heal and a blowpipe spec heal are both a
 * green hitsplat on your own head, and only the thing that caused them makes them worth separating.
 * Anything that cannot be pinned on a source is not counted at all - see {@link CombatTracker}.
 */
enum CombatMetric
{
	BLOOD_BARRAGE_HEAL(Group.SPELL_HEAL, "bloodBarrage", "Blood barrage", Unit.HITPOINTS),
	OTHER_SPELL_HEAL(Group.SPELL_HEAL, "otherSpell", "Other spells", Unit.HITPOINTS),

	AGS_HEAL(Group.SPEC_HEAL, "agsHeal", "Ancient godsword", Unit.HITPOINTS),
	BLOWPIPE_HEAL(Group.SPEC_HEAL, "bpHeal", "Blowpipe", Unit.HITPOINTS),
	OTHER_SPEC_HEAL(Group.SPEC_HEAL, "otherSpecHeal", "Other specs", Unit.HITPOINTS),

	ELDRITCH_PRAYER(Group.PRAYER, "eldritchPrayer", "Eldritch staff", Unit.PRAYER),

	ZCB_DAMAGE(Group.DAMAGE, "zcbDamage", "Zaryte crossbow", Unit.DAMAGE),
	OTHER_SPEC_DAMAGE(Group.DAMAGE, "otherSpecDamage", "Other specs", Unit.DAMAGE);

	/** Which heading a metric sits under, so the panel groups like with like. */
	enum Group
	{
		SPELL_HEAL("Spell healing"),
		SPEC_HEAL("Spec healing"),
		PRAYER("Prayer restored"),
		DAMAGE("Spec damage");

		private final String heading;

		Group(String heading)
		{
			this.heading = heading;
		}

		String heading()
		{
			return heading;
		}
	}

	/** What a metric is counted in. Two metrics only share a chart axis if they share a unit. */
	enum Unit
	{
		HITPOINTS("hitpoints healed"),
		PRAYER("prayer points restored"),
		DAMAGE("damage dealt");

		private final String description;

		Unit(String description)
		{
			this.description = description;
		}

		/** What a number in this unit is, spelled out - "1,204 hitpoints healed". */
		String description()
		{
			return description;
		}
	}

	private final Group group;

	/** The stored name. Never rename one without migrating every saved value. */
	private final String key;

	private final String label;
	private final Unit unit;

	CombatMetric(Group group, String key, String label, Unit unit)
	{
		this.group = group;
		this.key = key;
		this.label = label;
		this.unit = unit;
	}

	Group group()
	{
		return group;
	}

	String key()
	{
		return key;
	}

	/** How the metric reads in the panel, under its group's heading. */
	String label()
	{
		return label;
	}

	/** How the metric reads on its own, where there is no heading to qualify it. */
	String qualifiedLabel()
	{
		return group.heading() + ": " + label;
	}

	Unit unit()
	{
		return unit;
	}

	/** The metric stored under {@code key}, or null if nothing is - an older or newer schema. */
	static CombatMetric byKey(String key)
	{
		for (CombatMetric metric : values())
		{
			if (metric.key.equals(key))
			{
				return metric;
			}
		}

		return null;
	}
}
