package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * The special attacks worth telling apart, and what each one is expected to produce.
 *
 * <p>A special attack is recognised by the weapon that was equipped when the energy was spent, not
 * by an animation. Animations are shared - every godsword swings the same - and a weapon is the one
 * thing the game will always tell us plainly. Anything not named here still counts, as
 * {@link #OTHER}: a spec was definitely fired, and grouping the ones nobody asked to break out is
 * better than pretending they did not happen.
 *
 * <p>Not every weapon named here has a row of its own. The dragon knife, the dragon thrownaxe and
 * both blowpipes share one damage figure with {@link #OTHER}, because they hit for about the same as
 * each other. They are named anyway because a thrown spec lands on a different schedule from a
 * swung one, and a window timed for the swing lets the thrower's auto-attacks in - see
 * {@link #FLIGHT}. Being named is also what lets the panel say which specs that figure counts.
 *
 * <p>Each weapon lists its effects with the delay it takes to arrive - see {@link SpecEffect}. That
 * is what lets several specs be in flight at once without stealing each other's hitsplats, which
 * they otherwise do constantly: chaining a spec into another one is ordinary play, and the second
 * one must not evict the first before the first has finished paying out.
 */
enum SpecWeapon
{
	/**
	 * Zaryte crossbow. One bolt, so one hitsplat, and no healing of its own - the spec drains the
	 * target's defence rather than giving anything back.
	 */
	ZARYTE_CROSSBOW("Zaryte crossbow", projectile(CombatMetric.ZCB_DAMAGE, 1)),

	/** Dragon knife. Duality throws two knives at once, each rolling its own hit, and heals nothing. */
	DRAGON_KNIFE("Dragon knife", projectile(CombatMetric.OTHER_SPEC_DAMAGE, 2)),

	/**
	 * Dragon thrownaxe. One axe, thrown on the very next tick whatever the attack timer says, so
	 * the throw ahead of it is often only a tick in front.
	 *
	 * <p>That throw is still in the air when the energy moves, and from close in - where the axe's
	 * reach puts you anyway - it lands before the window opens. From the far edge of longrange it
	 * lands inside the window a tick ahead of the spec and is taken for it, which is the one case
	 * here the timing cannot tell apart.
	 */
	DRAGON_THROWNAXE("Dragon thrownaxe", projectile(CombatMetric.OTHER_SPEC_DAMAGE, 1)),

	/** Rosewood blowpipe. Rapid Burst fires two darts one after the other and heals nothing. */
	ROSEWOOD_BLOWPIPE("Rosewood blowpipe", rapidBurst()),

	/** Toxic blowpipe. One dart, healing half of what it hits for, both landing together. */
	BLOWPIPE("Toxic blowpipe",
		projectile(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		heal(CombatMetric.BLOWPIPE_HEAL, 1)),

	/**
	 * Ancient godsword. Blood Sacrifice hits once immediately, then marks the target for eight
	 * ticks; when the mark expires the target takes 25 typeless damage and only then is the
	 * attacker healed.
	 *
	 * <p>Hence three effects rather than one. The initial swing and the sacrifice are separate
	 * hitsplats separated by eight ticks, and crediting them with one wide window would let every
	 * auto-attack between them read as spec damage.
	 */
	ANCIENT_GODSWORD("Ancient godsword",
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		sacrificeDamage(),
		sacrificeHeal()),

	/** Eldritch nightmare staff. Restores prayer rather than hitpoints. */
	ELDRITCH_STAFF("Eldritch staff",
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		prayer(CombatMetric.ELDRITCH_PRAYER, 2)),

	/**
	 * Everything else with a special attack. Four hits is what the busiest spec in the game throws
	 * - dragon claws - so it is the cap that lets the group cover them all without letting a slow
	 * weapon's window swallow an auto-attack behind it.
	 */
	OTHER(null,
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 4),
		heal(CombatMetric.OTHER_SPEC_HEAL, 4));

	/**
	 * How long a spec's own hit may take to arrive, in game ticks.
	 *
	 * <p>Long enough for a dart, knife or axe thrown from as far off as they reach, and for the
	 * Zaryte crossbow's bolt, which lands on the same delay at any range - all of them by the third
	 * tick. Short enough that the next auto-attack is outside it: the fastest weapon here attacks
	 * again two ticks after its spec, and that throw has to fly as well.
	 */
	private static final int PROMPT = 3;

	/**
	 * The earliest a thrown or fired spec can land, in game ticks after the spec.
	 *
	 * <p>A spec is timed from the tick the energy bar moved, and a melee hit - which the game lands
	 * with no delay at all - arrives one tick after that: every Ancient godsword swing in a night's
	 * worth of logs did. A bolt, dart, knife or axe spends at least a tick more in the air, so
	 * nothing a ranged spec throws can land before the second tick.
	 *
	 * <p>What does land sooner is the auto-attack thrown before the spec, still in the air when the
	 * energy moved. A blowpipe or a knife attacks every other tick, so one of those lands on the
	 * spec's tick or the one after as a matter of course - and a window open from the start took it
	 * for the spec, spent the budget on it, and turned the spec's own hit away.
	 */
	private static final int FLIGHT = 2;

	/**
	 * How long after an Eldritch spec its prayer restore may still arrive, in game ticks.
	 *
	 * <p>Longer than {@link #PROMPT}, because the points do not go up when the spec is fired but
	 * when the spell it threw arrives - and a staff casts from up to ten tiles off. Timed against a
	 * trip's worth of specs the restore landed three ticks behind the spec most of the time and six
	 * behind it at the outside, so a window that shut at three took the ones cast from under the
	 * boss and dropped the rest.
	 *
	 * <p>Which is not a rounding error: a spec is worth twenty-odd prayer points, and a delve where
	 * two of them took the player from the nineties to the cap reported five. Seven ticks covers
	 * the slowest seen with a tick in hand, and still shuts long before a restore potion drunk
	 * after the spec could be mistaken for it.
	 *
	 * <p>What the extra ticks cost is the point of prayer that comes back on its own every twelfth
	 * tick down there, which now lands inside a spec's window more often than not and is credited
	 * to it. That is one point against the twenty the window was widened to save, and the only
	 * over-count anything here allows.
	 */
	private static final int RESTORE = 7;

	/**
	 * When the Ancient godsword's Blood Sacrifice pays out, in ticks after the spec.
	 *
	 * <p>The mark lasts exactly eight ticks and the damage and healing follow it, so the pair is
	 * expected on tick eight or nine. The range is widened by a tick either side for a hitsplat
	 * that lands a frame late, and no further - the point of a range rather than a deadline is that
	 * it excludes the immediate heals a barrage or a blowpipe is producing at the same time.
	 */
	private static final int SACRIFICE_FROM = 7;

	private static final int SACRIFICE_TO = 10;

	private final String label;
	private final List<SpecEffect> effects;

	SpecWeapon(String label, SpecEffect... effects)
	{
		this.label = label;
		this.effects = Collections.unmodifiableList(Arrays.asList(effects));
	}

	private static SpecEffect damage(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, metric, 0, PROMPT, budget);
	}

	/** A spec's hit that has to fly to its target - see {@link #FLIGHT}. */
	private static SpecEffect projectile(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, metric, FLIGHT, PROMPT, budget);
	}

	/**
	 * The rosewood blowpipe's two darts. The second is given a tick longer than a thrown hit
	 * usually has, since nothing yet says whether it lands with the first or a tick behind it. The
	 * budget still stops at two, and the dart thrown after the spec cannot land before both of the
	 * spec's have.
	 */
	private static SpecEffect rapidBurst()
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, CombatMetric.OTHER_SPEC_DAMAGE,
			FLIGHT, PROMPT + 1, 2);
	}

	private static SpecEffect heal(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.HEAL, metric, 0, PROMPT, budget);
	}

	private static SpecEffect prayer(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.PRAYER, metric, 0, RESTORE, budget);
	}

	/** The Ancient godsword's delayed hit, which lands when the mark on the target expires. */
	private static SpecEffect sacrificeDamage()
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, CombatMetric.OTHER_SPEC_DAMAGE,
			SACRIFICE_FROM, SACRIFICE_TO, 1);
	}

	/** The heal that follows that hit, a tick behind it at the outside. */
	private static SpecEffect sacrificeHeal()
	{
		return new SpecEffect(SpecEffect.Kind.HEAL, CombatMetric.AGS_HEAL,
			SACRIFICE_FROM, SACRIFICE_TO + 1, 1);
	}

	List<SpecEffect> effects()
	{
		return effects;
	}

	/** The weapon's name as the panel lists it, or null for {@link #OTHER}, which is no one weapon. */
	String label()
	{
		return label;
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
	 * The weapon held, or null if the slot is empty. An unarmed player has no special attack to
	 * spend, so a null here means whatever moved the energy bar was not something we can attribute.
	 */
	static SpecWeapon forItem(int itemId)
	{
		switch (itemId)
		{
			case ItemID.ZARYTE_XBOW:
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

			// Both the loaded and empty forms, and the Blazing ornament of each: the empty one is
			// listed because the game swaps to it on the shot that runs the scales out, and a spec
			// fired on that shot would otherwise land on an id we did not recognise.
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
	 * The weapon held, falling back to its name when the id is one we do not list.
	 *
	 * <p>A weapon has more ids than anybody can keep up with - ornament kits, charged and uncharged
	 * forms, Leagues and Beta recolours - and a spec fired with a form we missed lands in
	 * {@link #OTHER} rather than under the row the player is watching. The name is stable across
	 * every one of those forms, so it catches what the list does not.
	 *
	 * <p>The toxic blowpipe is matched by its own name and its ornament's rather than by the word
	 * blowpipe, which the Sailing ones carry too. None of them heals, and a match on the word gave
	 * each of them a window for the heal the toxic one's spec gives back.
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

		if (lower.contains("zaryte crossbow"))
		{
			return ZARYTE_CROSSBOW;
		}

		return lower.contains("eldritch") ? ELDRITCH_STAFF : OTHER;
	}
}
