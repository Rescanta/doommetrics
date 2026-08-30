package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The attribution rules, tested away from the client. Everything the tracker knows arrives through
 * its own methods, so none of this needs a game running.
 */
public class CombatTrackerTest
{
	/** What the tracker decided, in the order it decided it. */
	private final List<String> recorded = new ArrayList<>();

	private final CombatTracker tracker =
		new CombatTracker((metric, amount) -> recorded.add(metric.key() + "=" + amount));

	@Test
	public void aHealWithNothingOpenIsNotCounted()
	{
		// A brew, a shark, or plain regeneration. None of them is what this measures, and there is
		// no way to tell them from a spell heal except by nothing having caused one.
		tracker.healed(16, 100);
		assertTrue(recorded.isEmpty());
	}

	@Test
	public void aBloodBarrageHealIsCreditedToTheBarrage()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.healed(12, 100);

		assertEquals(list("bloodBarrage=12"), recorded);
	}

	@Test
	public void aBarrageOnlyHealsOnce()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.healed(12, 100);

		// A second heal on the same cast is something else - a brew swallowed on the same tick.
		tracker.healed(16, 100);

		assertEquals(list("bloodBarrage=12"), recorded);
	}

	/**
	 * The heal reaches us before the graphic that names the spell does - the client reads the
	 * players in a tick before the NPCs, and the heal is on the player while the impact is on the
	 * target. Every barrage arrives this way round, so a tracker that dropped it counted none.
	 */
	@Test
	public void aHealArrivingAheadOfItsSpellIsStillTheSpells()
	{
		tracker.healed(12, 100);
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);

		assertEquals(list("bloodBarrage=12"), recorded);
	}

	/** A melee spec hits on the tick it was fired, before the weapon that fired it can be read. */
	@Test
	public void aHitArrivingAheadOfItsSpecIsStillTheSpecs()
	{
		tracker.damaged(52, 100);
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);

		tracker.damaged(25, 108);
		tracker.healed(25, 108);

		assertEquals(list("otherSpecDamage=52", "otherSpecDamage=25", "agsHeal=25"), recorded);
	}

	/** Held only for the tick it arrived on: a brew is not the next tick's barrage. */
	@Test
	public void aHealIsNotHeldPastItsOwnTick()
	{
		tracker.healed(16, 100);
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 101);

		assertTrue(recorded.isEmpty());
	}

	/**
	 * A window that does not open on the tick of its cause never claims what arrived on it. The
	 * godsword's mark pays out eight ticks later, so a heal from the same tick as the swing is
	 * somebody else's.
	 */
	@Test
	public void aDelayedWindowDoesNotClaimWhatArrivedBeforeIt()
	{
		tracker.healed(6, 100);
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);

		assertTrue(recorded.isEmpty());
	}

	/**
	 * One cast, one heal. The same barrage is reported once per target it lit up and once more if
	 * the impact is drawn on the ground as well - and a second window for it would be left open
	 * for a brew swallowed on the same tick to walk into.
	 */
	@Test
	public void oneCastSeenTwiceOpensOneWindow()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);

		tracker.healed(12, 100);
		tracker.healed(16, 100);

		assertEquals(list("bloodBarrage=12"), recorded);
	}

	/**
	 * The chat line and the impact graphic are two views of one cast, and they do not always agree
	 * on which spell it was - the line names no spell at all. The first signal of the tick is the
	 * cast; the rest are echoes.
	 */
	@Test
	public void twoSignalsForOneCastDoNotBothOpen()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.spellHit(CombatMetric.OTHER_SPELL_HEAL, 100);

		tracker.healed(12, 100);
		tracker.healed(16, 100);

		assertEquals(list("bloodBarrage=12"), recorded);
	}

	@Test
	public void aSecondCastOnAnotherTickHealsAgain()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.healed(12, 100);

		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 105);
		tracker.healed(9, 105);

		assertEquals(list("bloodBarrage=12", "bloodBarrage=9"), recorded);
	}

	@Test
	public void aHealLongAfterTheSpellIsNotTheSpells()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.healed(12, 108);

		assertTrue(recorded.isEmpty());
	}

	@Test
	public void aBlowpipeSpecHealsAndTheDamageGoesToTheGroupedTotal()
	{
		tracker.specFired(SpecWeapon.BLOWPIPE, 100);
		tracker.damaged(31, 101);
		tracker.healed(15, 101);

		assertEquals(list("otherSpecDamage=31", "bpHeal=15"), recorded);
	}

	@Test
	public void aZaryteSpecIsCreditedWithOneHitAndNoMore()
	{
		tracker.specFired(SpecWeapon.ZARYTE_CROSSBOW, 100);
		tracker.damaged(44, 101);

		// The auto-attack behind it lands inside the window and is not part of the spec.
		tracker.damaged(30, 102);

		assertEquals(list("zcbDamage=44"), recorded);
	}

	@Test
	public void aZaryteSpecDoesNotHeal()
	{
		tracker.specFired(SpecWeapon.ZARYTE_CROSSBOW, 100);
		tracker.healed(20, 100);

		assertTrue(recorded.isEmpty());
	}

	@Test
	public void damageWellAfterASpecIsAnAutoAttack()
	{
		tracker.specFired(SpecWeapon.ZARYTE_CROSSBOW, 100);
		tracker.damaged(44, 110);

		assertTrue(recorded.isEmpty());
	}

	/**
	 * A block is a zero, and it is still the hit the spec spent itself on. Counting it against the
	 * budget is what stops the auto-attack behind a missed spec being read as the spec.
	 */
	@Test
	public void aMissedSpecStillSpendsItsHit()
	{
		tracker.specFired(SpecWeapon.ZARYTE_CROSSBOW, 100);
		tracker.damaged(0, 101);
		tracker.damaged(30, 102);

		assertEquals(list("zcbDamage=0"), recorded);
	}

	/**
	 * Blood Sacrifice hits, marks the target for eight ticks, then deals 25 typeless damage and
	 * only then heals. All three are the one special attack.
	 */
	@Test
	public void ancientGodswordPaysOutEightTicksAfterTheSwing()
	{
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);
		tracker.damaged(52, 100);
		tracker.damaged(25, 108);
		tracker.healed(25, 108);

		assertEquals(list("otherSpecDamage=52", "otherSpecDamage=25", "agsHeal=25"), recorded);
	}

	/**
	 * The heal lands nine ticks out, so the window has to start late as well as end late. Were it
	 * an ordinary "within N ticks" the godsword would claim the barrage heals landing in between.
	 */
	@Test
	public void ancientGodswordDoesNotClaimHealsThatLandImmediately()
	{
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);
		tracker.healed(6, 101);

		assertTrue(recorded.isEmpty());
	}

	@Test
	public void ancientGodswordHealingStopsAfterTheMarkHasPaidOut()
	{
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);
		tracker.healed(25, 118);

		assertTrue(recorded.isEmpty());
	}

	/**
	 * The bug the first cut had. Specs get chained as a matter of course, and Blood Sacrifice does
	 * not pay out for eight ticks - so remembering only the most recent spec lost every one of
	 * them to whatever was fired next.
	 */
	@Test
	public void aSpecFiredWhileTheGodswordMarkIsUpDoesNotEvictIt()
	{
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);
		tracker.damaged(52, 100);

		tracker.specFired(SpecWeapon.ZARYTE_CROSSBOW, 103);
		tracker.damaged(44, 104);

		tracker.damaged(25, 108);
		tracker.healed(25, 109);

		assertEquals(list("otherSpecDamage=52", "zcbDamage=44", "otherSpecDamage=25", "agsHeal=25"),
			recorded);
	}

	/** The same eviction, the other way round: a later spell must not cost the barrage its heal. */
	@Test
	public void aSecondSpellDoesNotTakeTheFirstsHeal()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.spellHit(CombatMetric.OTHER_SPELL_HEAL, 101);

		tracker.healed(12, 101);
		tracker.healed(9, 101);

		assertEquals(list("otherSpell=12", "bloodBarrage=9"), recorded);
	}

	@Test
	public void eldritchPrayerIsCountedAndOtherSpecsPrayerIsNot()
	{
		tracker.specFired(SpecWeapon.ELDRITCH_STAFF, 100);
		tracker.prayerGained(24, 100);

		tracker.specFired(SpecWeapon.BLOWPIPE, 200);
		tracker.prayerGained(24, 200);

		assertEquals(list("eldritchPrayer=24"), recorded);
	}

	@Test
	public void prayerLongAfterAnEldritchSpecIsAPotion()
	{
		tracker.specFired(SpecWeapon.ELDRITCH_STAFF, 100);
		tracker.prayerGained(24, 110);

		assertTrue(recorded.isEmpty());
	}

	/**
	 * You can barrage on the tick you spec, and then both could explain the heal. The nearer cause
	 * wins, because the effects being told apart arrive within a tick or two of their cause.
	 */
	@Test
	public void theMoreRecentCauseTakesTheHeal()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.specFired(SpecWeapon.BLOWPIPE, 101);
		tracker.healed(15, 101);

		assertEquals(list("bpHeal=15"), recorded);
	}

	@Test
	public void aSpecWithNoHealingLeftFallsBackToTheSpell()
	{
		tracker.specFired(SpecWeapon.BLOWPIPE, 100);
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.healed(15, 100);
		tracker.healed(12, 100);

		// Both opened on the same tick, so the later-registered barrage takes the first; its budget
		// of one is then spent and the blowpipe takes the second rather than it being dropped.
		assertEquals(list("bloodBarrage=15", "bpHeal=12"), recorded);
	}

	/**
	 * A blowpipe spec that hits for nothing heals for nothing - half of zero. The hit is still the
	 * spec's, so it is counted and the budget spent; there is simply no heal to follow it.
	 */
	@Test
	public void aBlowpipeSpecThatDealsNoDamageHealsNothing()
	{
		tracker.specFired(SpecWeapon.BLOWPIPE, 100);
		tracker.damaged(0, 101);

		assertEquals(list("otherSpecDamage=0"), recorded);
	}

	/** The name catches the charge states and ornaments the id list will always be missing. */
	@Test
	public void anUnlistedIdIsStillRecognisedByName()
	{
		assertEquals(SpecWeapon.BLOWPIPE, SpecWeapon.forItem(999_999, "Blazing blowpipe"));
		assertEquals(SpecWeapon.ANCIENT_GODSWORD,
			SpecWeapon.forItem(999_999, "Ancient godsword"));
		assertEquals(SpecWeapon.ZARYTE_CROSSBOW, SpecWeapon.forItem(999_999, "Zaryte crossbow"));
		assertEquals(SpecWeapon.ELDRITCH_STAFF,
			SpecWeapon.forItem(999_999, "Eldritch nightmare staff"));

		assertEquals(SpecWeapon.OTHER, SpecWeapon.forItem(999_999, "Dragon claws"));
		assertEquals(SpecWeapon.OTHER, SpecWeapon.forItem(999_999, null));
		assertEquals(null, SpecWeapon.forItem(0, "Nothing at all"));
	}

	/** A listed id is never second-guessed by the name, so the fallback cannot misread one. */
	@Test
	public void aListedIdWinsOverTheName()
	{
		assertEquals(SpecWeapon.ZARYTE_CROSSBOW,
			SpecWeapon.forItem(ItemID.ZARYTE_XBOW, "Ancient godsword"));
	}

	@Test
	public void aResetForgetsEverythingInFlight()
	{
		tracker.specFired(SpecWeapon.ANCIENT_GODSWORD, 100);
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.reset();

		tracker.healed(6, 101);
		tracker.damaged(50, 101);

		assertTrue(recorded.isEmpty());
	}

	@Test
	public void nothingIsCountedForAnUnarmedSpec()
	{
		tracker.specFired(null, 100);
		tracker.damaged(20, 100);

		assertTrue(recorded.isEmpty());
	}

	@Test
	public void zeroAndNegativeAmountsAreIgnored()
	{
		tracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, 100);
		tracker.healed(0, 100);
		tracker.healed(-4, 100);

		// Neither consumed the cast's one heal, so a real one still lands on it.
		tracker.healed(12, 100);

		assertEquals(list("bloodBarrage=12"), recorded);
	}

	@Test
	public void everyKnownSpecWeaponIsRecognisedByItsItemId()
	{
		assertEquals(SpecWeapon.ZARYTE_CROSSBOW,
			SpecWeapon.forItem(ItemID.ZARYTE_XBOW));
		assertEquals(SpecWeapon.ANCIENT_GODSWORD,
			SpecWeapon.forItem(ItemID.ANCIENT_GODSWORD));
		assertEquals(SpecWeapon.BLOWPIPE,
			SpecWeapon.forItem(ItemID.TOXIC_BLOWPIPE_LOADED));
		assertEquals(SpecWeapon.BLOWPIPE,
			SpecWeapon.forItem(ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT));
		assertEquals(SpecWeapon.ELDRITCH_STAFF,
			SpecWeapon.forItem(ItemID.NIGHTMARE_STAFF_ELDRITCH));
	}

	/** Anything else with a spec still fired one, and is grouped rather than thrown away. */
	@Test
	public void anUnlistedWeaponIsGroupedAndAnEmptyHandIsNothing()
	{
		assertEquals(SpecWeapon.OTHER,
			SpecWeapon.forItem(ItemID.AGS));
		assertEquals(null, SpecWeapon.forItem(0));
		assertEquals(null, SpecWeapon.forItem(-1));
	}

	private static List<String> list(String... values)
	{
		List<String> expected = new ArrayList<>();

		for (String value : values)
		{
			expected.add(value);
		}

		return expected;
	}
}
