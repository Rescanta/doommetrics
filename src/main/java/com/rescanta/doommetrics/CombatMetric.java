package com.rescanta.doommetrics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
 *
 * <p>The four catch-alls - other spells, other spec heals, other spec damage and other melee - are
 * still counted and still saved, but drawn nowhere: see {@link #DISPLAYED}.
 */
enum CombatMetric
{
	// Each counter drawn has a palette slot to itself - see seriesColor. The catch-alls are drawn
	// nowhere, and their colours are only there because every constant needs one.
	BLOOD_BARRAGE_HEAL(Group.SPELL_HEAL, "bloodBarrage", "Blood barrage", "Barrage", Unit.HITPOINTS,
		new Color(0x3987E5)),
	OTHER_SPELL_HEAL(Group.SPELL_HEAL, "otherSpell", "Other spells", "Other spells", Unit.HITPOINTS,
		new Color(0xD95926)),

	AGS_HEAL(Group.SPEC_HEAL, "agsHeal", "Ancient godsword", "AGS", Unit.HITPOINTS,
		new Color(0xD95926)),
	BLOWPIPE_HEAL(Group.SPEC_HEAL, "bpHeal", "Blowpipe", "BP", Unit.HITPOINTS,
		new Color(0x199E70)),
	OTHER_SPEC_HEAL(Group.SPEC_HEAL, "otherSpecHeal", "Other specs", "Other specs", Unit.HITPOINTS,
		new Color(0xD55181)),

	ELDRITCH_PRAYER(Group.PRAYER, "eldritchPrayer", "Eldritch staff", "Eldritch", Unit.PRAYER,
		new Color(0xC98500)),

	ZCB_DAMAGE(Group.DAMAGE, "zcbDamage", "Zaryte crossbow", "ZCB", Unit.DAMAGE,
		new Color(0xD55181)),
	OTHER_SPEC_DAMAGE(Group.DAMAGE, "otherSpecDamage", "Other specs", "Other dmg", Unit.DAMAGE,
		new Color(0xE66767)),

	SCYTHE_PUNISH(Group.PUNISH, "scythePunish", "Scythe of vitur", "Scythe", Unit.DAMAGE,
		new Color(0x008300)),
	NOXIOUS_HALBERD_PUNISH(Group.PUNISH, "noxiousHalberdPunish", "Noxious halberd", "Nox halb",
		Unit.DAMAGE, new Color(0x9085E9)),
	CRYSTAL_HALBERD_PUNISH(Group.PUNISH, "crystalHalberdPunish", "Crystal halberd", "Crystal halb",
		Unit.DAMAGE, new Color(0xE66767)),
	OTHER_MELEE_PUNISH(Group.PUNISH, "otherMeleePunish", "Other melee", "Other melee", Unit.DAMAGE,
		new Color(0xC98500));

	/**
	 * The counters drawn anywhere - the overlay, the infobox, the side panel and the run detail
	 * window - in declaration order.
	 *
	 * <p>Everything but the catch-alls. With a counter for every weapon worth naming, a line for
	 * "everything else" was one more row to read for a figure nobody played around, so those are
	 * tallied and saved as before but never shown, and never summed into a heading's figure either:
	 * a heading reads as the rows under it add up to.
	 */
	static final List<CombatMetric> DISPLAYED = Collections.unmodifiableList(Arrays.stream(values())
		.filter(CombatMetric::displayed)
		.collect(Collectors.toList()));

	/** Which heading a metric sits under, so the panel groups like with like. */
	enum Group
	{
		SPELL_HEAL("Spell healing", "Spell heals", Unit.HITPOINTS),
		SPEC_HEAL("Spec healing", "Spec heals", Unit.HITPOINTS),
		PRAYER("Prayer restored", "Prayer", Unit.PRAYER),
		DAMAGE("Spec damage", "Spec dmg", Unit.DAMAGE),

		/**
		 * What a melee punish hit for: the swing itself and the strength-bonus hitsplats the boss
		 * takes on top of it, credited to the weapon that swung - see {@link PunishTracker}.
		 */
		PUNISH("Punish damage", "Punish dmg", Unit.DAMAGE);

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
	private final Color series;

	CombatMetric(Group group, String key, String label, String overlayLabel, Unit unit,
		Color series)
	{
		this.group = group;
		this.key = key;
		this.label = label;
		this.overlayLabel = overlayLabel;
		this.unit = unit;
		this.series = series;
	}

	Group group()
	{
		return group;
	}

	/** Whether this counter is drawn anywhere - see {@link #DISPLAYED}. */
	boolean displayed()
	{
		switch (this)
		{
			case OTHER_SPELL_HEAL:
			case OTHER_SPEC_HEAL:
			case OTHER_SPEC_DAMAGE:
			case OTHER_MELEE_PUNISH:
				return false;

			default:
				return true;
		}
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

	/**
	 * The weapons whose specs feed this figure, one per line, or nothing when a single weapon does
	 * - its label already names that one.
	 *
	 * <p>For the catch-alls, whose label cannot say what they catch: "Other specs" under spec
	 * damage takes the knives, thrownaxes and blowpipes that hit for about the same as each other,
	 * and the damage of every spec counted for something else. Worked out from {@link SpecWeapon}
	 * rather than written down, so a weapon added there is listed here without anybody having to
	 * remember to. The catch-all itself comes last, because a figure it feeds counts every spec
	 * not named, not only the ones that are.
	 */
	List<String> sources()
	{
		List<String> named = new ArrayList<>();
		boolean anyOther = false;

		for (SpecWeapon weapon : SpecWeapon.values())
		{
			if (!weapon.credits(this))
			{
				continue;
			}

			if (weapon.label() == null)
			{
				anyOther = true;
			}
			else
			{
				named.add(weapon.label());
			}
		}

		if (named.size() + (anyOther ? 1 : 0) < 2)
		{
			return Collections.emptyList();
		}

		if (anyOther)
		{
			named.add("Any other spec");
		}

		return named;
	}

	/**
	 * The colour this metric's line is drawn in on {@link DelveChart}, and the colour of the
	 * swatch beside its name in {@link RunLegendPanel}.
	 *
	 * <p>Distinct from {@link Unit#color()} because the two answer different questions. Everywhere
	 * a figure stands on its own - the overlay, the side panel's table - what the reader needs to
	 * know is what it is counted in, and three colours say that outright. On a chart with all
	 * eight drawn at once, three colours would put three identical red lines on the plot and the
	 * labels would be the only way to tell a barrage heal from a blowpipe one. Identity is the job
	 * there, so hues do it, and the unit is carried instead by the group heading each line
	 * is listed under - which is where the legend keeps its unit-coloured tab.
	 *
	 * <p>These are the eight slots of a categorical palette validated as a set against this
	 * plugin's dark surface, taken by the counters drawn in the order they are listed, skipping
	 * none. The validation measures each slot against the one after it, so it only holds while the
	 * lines listed side by side are slots that sit side by side: handing a new counter a slot
	 * another left free puts two hues next to each other that were never checked as neighbours,
	 * which is how three warm punish rows once came to sit together. A counter added or retired
	 * means the slots are handed out again from the first - {@code CombatMetricTest} pins the
	 * order so that cannot happen quietly. Measured worst adjacent pair is a colour-blind Delta E
	 * of 8.4 against a target of 8, and a normal-vision Delta E of 19.3 against a floor of 15, all
	 * eight clearing 3:1 contrast on the panel background. Identity never rests on the colour alone either way: every line is named
	 * in the legend beside it, and hovering a delve puts that delve's figures in the same table.
	 *
	 * <p>The palette has eight validated slots and no ninth, and there are eight counters drawn, so
	 * each takes a slot of its own and no two lines share a hue. A counter added later has no slot
	 * left: a hue generated to make one would be exactly the colour nobody checked.
	 */
	Color seriesColor()
	{
		return series;
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
