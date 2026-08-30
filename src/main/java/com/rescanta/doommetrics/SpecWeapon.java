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
	ZARYTE_CROSSBOW(damage(CombatMetric.ZCB_DAMAGE, 1)),

	/**
	 * Ancient godsword. Blood Sacrifice hits once immediately, then marks the target for eight
	 * ticks; when the mark expires the target takes 25 typeless damage and only then is the
	 * attacker healed.
	 *
	 * <p>Hence three effects rather than one. The initial swing and the sacrifice are separate
	 * hitsplats separated by eight ticks, and crediting them with one wide window would let every
	 * auto-attack between them read as spec damage.
	 */
	ANCIENT_GODSWORD(
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		sacrificeDamage(),
		sacrificeHeal()),

	/** Toxic blowpipe. One dart, healing half of what it hits for, both landing together. */
	BLOWPIPE(
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		heal(CombatMetric.BLOWPIPE_HEAL, 1)),

	/** Eldritch nightmare staff. Restores prayer rather than hitpoints. */
	ELDRITCH_STAFF(
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 1),
		prayer(CombatMetric.ELDRITCH_PRAYER, 2)),

	/**
	 * Everything else with a special attack. Four hits is what the busiest spec in the game throws
	 * - dragon claws - so it is the cap that lets the group cover them all without letting a slow
	 * weapon's window swallow an auto-attack behind it.
	 */
	OTHER(
		damage(CombatMetric.OTHER_SPEC_DAMAGE, 4),
		heal(CombatMetric.OTHER_SPEC_HEAL, 4));

	/**
	 * How long a spec's own hit may take to arrive, in game ticks.
	 *
	 * <p>Long enough for a bolt to cross the room and for the hit to land on the following tick,
	 * short enough that the next auto-attack in the sequence is outside it. A weapon attacks every
	 * four ticks at the fastest, so three keeps the two apart.
	 */
	private static final int PROMPT = 3;

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

	private final List<SpecEffect> effects;

	SpecWeapon(SpecEffect... effects)
	{
		this.effects = Collections.unmodifiableList(Arrays.asList(effects));
	}

	private static SpecEffect damage(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.DAMAGE, metric, 0, PROMPT, budget);
	}

	private static SpecEffect heal(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.HEAL, metric, 0, PROMPT, budget);
	}

	private static SpecEffect prayer(CombatMetric metric, int budget)
	{
		return new SpecEffect(SpecEffect.Kind.PRAYER, metric, 0, PROMPT, budget);
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

		if (lower.contains("blowpipe"))
		{
			return BLOWPIPE;
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
