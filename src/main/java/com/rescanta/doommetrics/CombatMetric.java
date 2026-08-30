package com.rescanta.doommetrics;

import java.awt.Color;

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
	BLOOD_BARRAGE_HEAL(Group.SPELL_HEAL, "bloodBarrage", "Blood barrage", "Barrage", Unit.HITPOINTS),
	OTHER_SPELL_HEAL(Group.SPELL_HEAL, "otherSpell", "Other spells", "Other spells", Unit.HITPOINTS),

	AGS_HEAL(Group.SPEC_HEAL, "agsHeal", "Ancient godsword", "AGS", Unit.HITPOINTS),
	BLOWPIPE_HEAL(Group.SPEC_HEAL, "bpHeal", "Blowpipe", "BP", Unit.HITPOINTS),
	OTHER_SPEC_HEAL(Group.SPEC_HEAL, "otherSpecHeal", "Other specs", "Other specs", Unit.HITPOINTS),

	ELDRITCH_PRAYER(Group.PRAYER, "eldritchPrayer", "Eldritch staff", "Eldritch", Unit.PRAYER),

	ZCB_DAMAGE(Group.DAMAGE, "zcbDamage", "Zaryte crossbow", "ZCB", Unit.DAMAGE),
	OTHER_SPEC_DAMAGE(Group.DAMAGE, "otherSpecDamage", "Other specs", "Other dmg", Unit.DAMAGE);

	/** Which heading a metric sits under, so the panel groups like with like. */
	enum Group
	{
		SPELL_HEAL("Spell healing", "Spell heals", Unit.HITPOINTS),
		SPEC_HEAL("Spec healing", "Spec heals", Unit.HITPOINTS),
		PRAYER("Prayer restored", "Prayer", Unit.PRAYER),
		DAMAGE("Spec damage", "Spec dmg", Unit.DAMAGE);

		private final String heading;
		private final String overlayHeading;
		private final Unit unit;

		Group(String heading, String overlayHeading, Unit unit)
		{
			this.heading = heading;
			this.overlayHeading = overlayHeading;
			this.unit = unit;
		}

		String heading()
		{
			return heading;
		}

		/**
		 * How the group reads on the overlay when its metrics are drawn as one line - shorter than
		 * the panel's heading for the same reason {@link #overlayLabel()} is, and kept honest by
		 * the same measure.
		 */
		String overlayHeading()
		{
			return overlayHeading;
		}

		/**
		 * What the group's figures are counted in. Every metric under a heading shares it, which
		 * is what makes a combined line addable at all - and what lets that line be drawn in the
		 * same colour as the separate lines it stands in for.
		 */
		Unit unit()
		{
			return unit;
		}
	}

	/** What a metric is counted in. Two metrics only share a chart axis if they share a unit. */
	enum Unit
	{
		HITPOINTS("hitpoints healed", new Color(0xFF6B6B)),
		PRAYER("prayer points restored", new Color(0x6FB7FF)),
		DAMAGE("damage dealt", new Color(0xFFC145));

		private final String description;
		private final Color color;

		Unit(String description, Color color)
		{
			this.description = description;
			this.color = color;
		}

		/** What a number in this unit is, spelled out - "1,204 hitpoints healed". */
		String description()
		{
			return description;
		}

		/**
		 * The colour every figure counted in this unit is drawn in, on the overlay and in the
		 * panel's table alike.
		 *
		 * <p>Hung on the unit rather than on the metric because what is worth telling apart at a
		 * glance is what a number measures, not what produced it: eight counters in eight colours
		 * is a legend to memorise, whereas three say outright that this line is hitpoints, that
		 * one is prayer and that one is damage. Sources within a unit are told apart by their
		 * labels, which is what the labels are there for.
		 *
		 * <p>Kept light enough to carry on RuneLite's dark panel and on the overlay's translucent
		 * background, and clear of the muted grey a zero is drawn in.
		 */
		Color color()
		{
			return color;
		}
	}

	private final Group group;

	/** The stored name. Never rename one without migrating every saved value. */
	private final String key;

	private final String label;
	private final String overlayLabel;
	private final Unit unit;

	CombatMetric(Group group, String key, String label, String overlayLabel, Unit unit)
	{
		this.group = group;
		this.key = key;
		this.label = label;
		this.overlayLabel = overlayLabel;
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

	/**
	 * How the metric reads on the overlay, where the lines stand on their own with no heading over
	 * them and no room for one.
	 *
	 * <p>Short, because the whole row - the label, the figure and the space between them - has to
	 * fit the width every overlay in the client starts at, with the widest figure a run can put on
	 * a counter beside it: five figures, which nothing a delve can do exceeds.
	 *
	 * <p>A label that does not fit is not merely cramped. The panel wraps it onto a second line,
	 * squeezes the figure into the last third of the width and then draws it back over the label,
	 * so "Blood barrage heal" and 25,400 come out as "Blood barrag25,400".
	 * {@code DoomMetricsOverlayTest} measures every one of them against that width.
	 *
	 * <p>What the shortening gives up is saying outright what is being counted, and what pays for
	 * it is the colour: a figure is drawn in the colour of its unit, so a red one is hitpoints and
	 * a yellow one is damage without a word being spent on it - see {@link Unit#color()}. Where
	 * two rows would otherwise read identically the word stays, which is why the two catch-alls
	 * are "Other specs" and "Other dmg" rather than "Other specs" twice.
	 */
	String overlayLabel()
	{
		return overlayLabel;
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
