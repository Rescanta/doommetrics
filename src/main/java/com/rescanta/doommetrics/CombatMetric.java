package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The healing, prayer and damage figures, one per source. {@link #key} is stored in the history
 * file and config, so it must never change; the constant may be renamed.
 */
enum CombatMetric implements CombatSeries
{
	// Each counter drawn has a palette slot to itself - see seriesColor - except the two SGS ones,
	// which share one each and are dashed. The catch-alls are drawn nowhere, and their colours are only there
	// because every constant needs one.
	BLOOD_BARRAGE_HEAL(Group.HEALING, "bloodBarrage", "Blood barrage", "Barrage", Unit.HITPOINTS,
		new Color(0x3987E5)),
	OTHER_SPELL_HEAL(Group.HEALING, "otherSpell", "Other spells", "Other spells", Unit.HITPOINTS,
		new Color(0xD95926)),

	AGS_HEAL(Group.HEALING, "agsHeal", "Ancient godsword", "AGS", Unit.HITPOINTS,
		new Color(0xD95926)),
	BLOWPIPE_HEAL(Group.HEALING, "bpHeal", "Blowpipe", "BP", Unit.HITPOINTS,
		new Color(0x199E70)),
	SGS_HEAL(Group.HEALING, "sgsHeal", "Saradomin godsword", "SGS", Unit.HITPOINTS,
		new Color(0x9085E9)),
	OTHER_SPEC_HEAL(Group.HEALING, "otherSpecHeal", "Other specs", "Other specs", Unit.HITPOINTS,
		new Color(0xD55181)),

	ELDRITCH_PRAYER(Group.PRAYER, "eldritchPrayer", "Eldritch staff", "Eldritch", Unit.PRAYER,
		new Color(0xC98500)),
	SGS_PRAYER(Group.PRAYER, "sgsPrayer", "Saradomin godsword", "SGS prayer", Unit.PRAYER,
		new Color(0x008300)),

	ZCB_DAMAGE(Group.DAMAGE, "zcbDamage", "Zaryte crossbow", "ZCB", Unit.DAMAGE,
		new Color(0xD55181)),
	OTHER_SPEC_DAMAGE(Group.DAMAGE, "otherSpecDamage", "Other specs", "Other dmg", Unit.DAMAGE,
		new Color(0xE66767)),

	SCYTHE_PUNISH(Group.DAMAGE, "scythePunish", "Scythe of vitur", "Scythe", Unit.DAMAGE,
		new Color(0x008300)),
	NOXIOUS_HALBERD_PUNISH(Group.DAMAGE, "noxiousHalberdPunish", "Noxious halberd", "Nox halb",
		Unit.DAMAGE, new Color(0x9085E9)),
	CRYSTAL_HALBERD_PUNISH(Group.DAMAGE, "crystalHalberdPunish", "Crystal halberd", "Crystal halb",
		Unit.DAMAGE, new Color(0xE66767)),
	OTHER_MELEE_PUNISH(Group.DAMAGE, "otherMeleePunish", "Other melee", "Other melee", Unit.DAMAGE,
		new Color(0xC98500));

	/**
	 * The counters drawn anywhere, in declaration order: everything but the catch-alls, which are
	 * still counted into their heading.
	 */
	static final List<CombatMetric> DISPLAYED = Collections.unmodifiableList(Arrays.stream(values())
		.filter(CombatMetric::displayed)
		.collect(Collectors.toList()));

	/** The heading a metric sits under: one per unit. */
	enum Group implements CombatSeries
	{
		/** Every heal, spell or spec: what kept your hitpoints up. */
		HEALING("Healing", "Healing", Unit.HITPOINTS, new Color(0x3987E5)),
		PRAYER("Prayer restored", "Prayer", Unit.PRAYER, new Color(0xD95926)),

		/** Spec hits, and melee punishes including the boss's strength-bonus splats. */
		DAMAGE("Spec & punish damage", "Damage", Unit.DAMAGE, new Color(0x199E70));

		private final String heading;
		private final String overlayHeading;
		private final Unit unit;
		private final Color series;

		Group(String heading, String overlayHeading, Unit unit, Color series)
		{
			this.heading = heading;
			this.overlayHeading = overlayHeading;
			this.unit = unit;
			this.series = series;
		}

		String heading()
		{
			return heading;
		}

		/** How a heading's line reads in the legend, which is what it reads as a heading. */
		@Override
		public String label()
		{
			return heading;
		}

		/** The first palette slots, in order - see {@link CombatMetric#seriesColor}. */
		@Override
		public Color seriesColor()
		{
			return series;
		}

		/** The unit's skill icon: a sum has no one weapon, but it has one unit. */
		@Override
		public BufferedImage icon(Icons icons)
		{
			return icons.smallUnit(unit);
		}

		/** Every counter under this heading, the catch-alls included. */
		@Override
		public long amount(CombatTotals totals)
		{
			long total = 0;

			for (CombatMetric metric : metrics())
			{
				total += totals.get(metric);
			}

			return total;
		}

		/** What the catch-alls under this heading counted. */
		long unnamed(CombatTotals totals)
		{
			long total = 0;

			for (CombatMetric metric : metrics())
			{
				if (!metric.displayed())
				{
					total += totals.get(metric);
				}
			}

			return total;
		}

		/** Every counter under this heading, in declaration order, the catch-alls last. */
		List<CombatMetric> metrics()
		{
			return Members.BY_GROUP.get(this);
		}

		/** The heading's figure, and what of it no row names. */
		String tooltip(CombatTotals totals)
		{
			long amount = amount(totals);
			long unnamed = unnamed(totals);

			StringBuilder text = new StringBuilder("<html>").append(heading).append("<br>");

			text.append(amount > 0
				? DoomFormat.count(amount) + " " + unit.description()
				: "Nothing counted yet");

			if (unnamed > 0)
			{
				text.append("<br><br>Includes ").append(DoomFormat.count(unnamed))
					.append(" from ").append(String.join(" and ", unnamedLabels(totals)))
					.append(",<br>which have no row of their own.");
			}

			return text.append("</html>").toString();
		}

		/** The catch-alls under this heading that counted something. */
		private List<String> unnamedLabels(CombatTotals totals)
		{
			List<String> labels = new ArrayList<>();

			for (CombatMetric metric : metrics())
			{
				if (!metric.displayed() && totals.get(metric) > 0)
				{
					labels.add(metric.label());
				}
			}

			return labels;
		}

		/** What feeds a heading's line: every counter under it, by name. */
		@Override
		public List<String> sources()
		{
			List<String> named = new ArrayList<>();

			for (CombatMetric metric : metrics())
			{
				named.add(metric.label());
			}

			return named;
		}

		String overlayHeading()
		{
			return overlayHeading;
		}

		@Override
		public Unit unit()
		{
			return unit;
		}

		/**
		 * The counters under each heading. A holder class, since a static field in the enum would
		 * be filled before {@code CombatMetric}'s constants exist.
		 */
		private static final class Members
		{
			private static final Map<Group, List<CombatMetric>> BY_GROUP = byGroup();

			private Members()
			{
			}

			private static Map<Group, List<CombatMetric>> byGroup()
			{
				Map<Group, List<CombatMetric>> map = new EnumMap<>(Group.class);

				for (Group group : values())
				{
					map.put(group, Collections.unmodifiableList(
						Arrays.stream(CombatMetric.values())
							.filter(metric -> metric.group == group)
							.collect(Collectors.toList())));
				}

				return Collections.unmodifiableMap(map);
			}
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

		/** The colour figures in this unit are drawn in on the overlay and in the panel. */
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
	@Override
	public String label()
	{
		return label;
	}

	/**
	 * A short label that fits the default overlay width beside a five-figure value.
	 * {@code DoomMetricsOverlayTest} checks every one.
	 */
	String overlayLabel()
	{
		return overlayLabel;
	}

	@Override
	public Unit unit()
	{
		return unit;
	}

	/** What this counter came to in a tally. */
	@Override
	public long amount(CombatTotals totals)
	{
		return totals.get(this);
	}

	/** The picture of what this counter counts, shrunk to sit beside its name. */
	@Override
	public BufferedImage icon(Icons icons)
	{
		return icons.smallCounter(this);
	}

	/**
	 * The weapons whose specs feed this figure, or nothing when its label already names the one.
	 */
	@Override
	public List<String> sources()
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

		// Credited straight from the hitsplat - see CombatWatcher.
		if (this == OTHER_SPEC_DAMAGE)
		{
			named.add("Burns (scorching bow, burning claws)");
		}

		return named;
	}

	/**
	 * The chart line and legend swatch colour: a validated categorical palette, one slot per drawn
	 * counter in listing order, with no ninth slot. {@code CombatMetricTest} pins the order.
	 */
	@Override
	public Color seriesColor()
	{
		return series;
	}

	/** The counters past the eighth slot reuse hues checked against their neighbours. */
	@Override
	public boolean dashed()
	{
		return this == SGS_HEAL || this == SGS_PRAYER;
	}
}
