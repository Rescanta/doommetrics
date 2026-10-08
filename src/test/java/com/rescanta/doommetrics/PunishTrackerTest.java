package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The punish rules, tested away from the client, the same way {@link CombatTrackerTest} tests the
 * spec rules. Each tick is played out in the order the client delivers it: the swing's animation,
 * the hitsplats, the boss's own animation, and then the end of the tick, where the prayer and the
 * weapon are read. The ticks are the ones a real trip logged.
 */
public class PunishTrackerTest
{
	/** What was credited to a punish. */
	private final List<String> recorded = new ArrayList<>();

	/** What was handed back for the spec tracker; a punish's own hit goes back as nothing. */
	private final List<String> handedBack = new ArrayList<>();

	/** Which swing each hit at a punish was handed back against, and as what. */
	private final List<String> against = new ArrayList<>();

	private final PunishTracker tracker = new PunishTracker(
		(metric, amount) -> recorded.add(metric.key() + "=" + amount),
		new PunishTracker.Handback()
		{
			@Override
			public void damaged(int amount, int tick)
			{
				handedBack.add(amount + "@" + tick);
			}

			@Override
			public void strayed(int amount, int tick, int swing)
			{
				handedBack.add(amount + "@" + tick);
				against.add("before " + swing);
			}

			@Override
			public void punished(int tick, int swing)
			{
				handedBack.add("0@" + tick);
				against.add("from " + swing);
			}
		});

	/**
	 * The one the plugin exists for: the boss prays, the scythe goes in, and the swing's three hits
	 * and the strength-bonus splat behind each of them are all the scythe's.
	 */
	@Test
	public void aScytheSwungUnderThePrayerCountsTheSwingAndTheBonusSplats()
	{
		tickEnded(1042, true, null);

		tracker.swung(1047);
		tickEnded(1047, true, PunishWeapon.SCYTHE);

		mine(11, 1048);
		mine(4, 1048);
		mine(1, 1048);
		tracker.beamCancelled(1048);
		tickEnded(1048, false, PunishWeapon.SCYTHE);

		bonus(16, 1049);
		bonus(16, 1049);
		bonus(16, 1049);
		tickEnded(1049, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=11", "scythePunish=4", "scythePunish=1", "scythePunish=16",
			"scythePunish=16", "scythePunish=16"), recorded);

		// The swing's own hits still reach the spec tracker, as nothing, so a spec swung at the
		// punish knows its hits are spent. The bonus splats were never the spec tracker's.
		assertEquals(list("0@1048", "0@1048", "0@1048"), handedBack);
	}

	/**
	 * Swung on the tick the boss started to charge: the beam is cut off before the prayer is ever
	 * drawn, and no bonus follows - but the hits are the punish, and the cut beam says so.
	 */
	@Test
	public void aPunishLandedBeforeThePrayerShowsIsReadOffTheCutBeam()
	{
		tickEnded(908, false, null);

		tracker.swung(909);
		tickEnded(909, false, PunishWeapon.SCYTHE);

		mine(33, 910);
		mine(10, 910);
		mine(0, 910);
		tracker.beamCancelled(910);
		tickEnded(910, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=33", "scythePunish=10"), recorded);
	}

	/**
	 * From a trip's log: a bow shot fired at 557 was still in the air when the scythe was switched
	 * to and swung at 559, and landed on the swing's own tick. The scythe's hits came a tick later.
	 */
	@Test
	public void aHitLandingOnTheSwingsOwnTickIsNotThePunishs()
	{
		tickEnded(558, true, null);

		tracker.swung(559);
		mine(49, 559);
		tickEnded(559, true, PunishWeapon.SCYTHE);

		mine(4, 560);
		mine(5, 560);
		mine(2, 560);
		tickEnded(560, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=4", "scythePunish=5", "scythePunish=2"), recorded);
		assertFalse("left to the spec tracker, never held", tracker.mayBePunish(559));
	}

	/**
	 * Log 2026-09-19 17:34:02-03: one halberd hit, then its 29 and a 5 on the same tick. The game
	 * draws a bonus splat per hit, so the 5 is a larva exploding by the boss. So is a splat a tick
	 * after the swing, before any bonus can land.
	 */
	@Test
	public void aSplatPastOnePerHitOrBeforeTheBonusesIsALarvaExploding()
	{
		tickEnded(292, true, null);

		tracker.swung(294);
		tickEnded(294, true, PunishWeapon.NOXIOUS_HALBERD);

		mine(12, 295);
		bonus(22, 295);
		tickEnded(295, false, PunishWeapon.NOXIOUS_HALBERD);

		bonus(29, 296);
		bonus(5, 296);
		tickEnded(296, false, PunishWeapon.NOXIOUS_HALBERD);

		assertEquals(list("noxiousHalberdPunish=12", "noxiousHalberdPunish=29"), recorded);
		assertEquals("an explosion is nobody's hit to hand back", list("0@295"), handedBack);
	}

	/**
	 * Log 2026-09-20 20:18:04-05, seen on video: the bow's 43 landed on the halberd swing's tick,
	 * and the game drew a second bonus splat a tick after the halberd's own. The arrow is no punish,
	 * but the user wants the extra splat - it looks like, and is, a punish's - counted as one.
	 */
	@Test
	public void theExtraBonusSplatABowHitBringsCountsAsThePunishs()
	{
		tickEnded(9949, true, null);

		tracker.swung(9950);
		mine(43, 9950);
		tickEnded(9950, true, PunishWeapon.NOXIOUS_HALBERD);

		mine(39, 9951);
		tickEnded(9951, false, PunishWeapon.NOXIOUS_HALBERD);

		bonus(29, 9952);
		tickEnded(9952, false, PunishWeapon.NOXIOUS_HALBERD);

		bonus(27, 9953);
		tickEnded(9953, false, PunishWeapon.NOXIOUS_HALBERD);

		assertEquals(list("noxiousHalberdPunish=39", "noxiousHalberdPunish=29",
			"noxiousHalberdPunish=27"), recorded);
	}

	/**
	 * 2026-09-30 tick 640, on video: a twisted bow shot at 638 landed with the scythe's three hits.
	 * The XP drops read 44 ranged and 250 strength (5 and 28 damage), a third of that as hitpoints.
	 */
	@Test
	public void theExperienceEachAttackEarnedSaysWhichHitWasTheArrow()
	{
		tickEnded(637, true, null);
		tracker.experienceGained(15, 638);
		tickEnded(638, true, null);

		tracker.swung(639);
		tracker.experienceGained(83, 639);
		tickEnded(639, true, PunishWeapon.SCYTHE);

		mine(5, 640);
		mine(23, 640);
		mine(1, 640);
		mine(4, 640);
		tickEnded(640, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=23", "scythePunish=1", "scythePunish=4"), recorded);
		assertEquals(list("5@640", "0@640", "0@640", "0@640"), handedBack);

		// The arrow can only be a spec's from before the swing; the rest only one swung with it.
		assertEquals(list("before 639", "from 639", "from 639", "from 639"), against);
	}

	/** Tick 659, the same trip: 250 ranged and 143 strength - the 28 was the bow's. */
	@Test
	public void theArrowCanBeTheBiggestHit()
	{
		tickEnded(656, true, null);
		tracker.experienceGained(83, 657);
		tickEnded(657, true, null);

		tracker.swung(658);
		tracker.experienceGained(48, 658);
		tickEnded(658, true, PunishWeapon.SCYTHE);

		mine(28, 659);
		mine(9, 659);
		mine(4, 659);
		mine(3, 659);
		tickEnded(659, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=9", "scythePunish=4", "scythePunish=3"), recorded);
		assertEquals(list("28@659", "0@659", "0@659", "0@659"), handedBack);
	}

	/**
	 * 2026-09-30 22:43 run, tick 442: bow 36, scythe 38, hits 16 12 4 1. A point or two of
	 * experience can follow an attack a tick later, from a larva hit on the way.
	 */
	@Test
	public void experienceTrailingAnAttackIsCountedWithIt()
	{
		tickEnded(438, true, null);
		tracker.experienceGained(34, 440);
		tickEnded(440, true, null);
		tracker.experienceGained(2, 441);
		tickEnded(441, true, null);

		tracker.swung(442);
		tracker.experienceGained(38, 442);
		tickEnded(442, true, PunishWeapon.SCYTHE);

		mine(16, 443);
		mine(12, 443);
		mine(4, 443);
		mine(1, 443);
		tickEnded(443, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=12", "scythePunish=4", "scythePunish=1"), recorded);
		assertEquals("16@443", handedBack.get(0));
	}

	/**
	 * 2026-10-06 tick 5995, the hit that cleared the delve: bow 51, scythe 60, hits 23 8 2 0. The
	 * scythe was worth 27 and the boss had 10 left after the arrow, so its hits were cut to 10 and
	 * the arrow's share of what landed came to 15, nearer the 8 than the 23. The arrow lands first
	 * and is never the one cut.
	 */
	@Test
	public void aKillCutsTheScythesHitsShortAndNotTheArrow()
	{
		tickEnded(5991, false, null);
		tracker.experienceGained(51, 5992);
		tickEnded(5992, false, null);

		tracker.swung(5994);
		tracker.experienceGained(60, 5994);
		tickEnded(5994, true, PunishWeapon.SCYTHE);

		mine(23, 5995);
		mine(8, 5995);
		mine(2, 5995);
		mine(0, 5995);
		tickEnded(5995, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=8", "scythePunish=2"), recorded);
		assertEquals(list("23@5995", "0@5995", "0@5995", "0@5995"), handedBack);
	}

	/**
	 * 2026-10-08 tick 18326: a twisted bow shot that missed landed as a 0 with the halberd's 19,
	 * and a larva hit two ticks before it had earned 3 experience. The 0 is under that share of
	 * 1.3, and still the nearest to it.
	 */
	@Test
	public void aFirstHitTooSmallForItsShareIsStillTheArrowWhenNothingIsNearer()
	{
		tracker.experienceGained(3, 18322);
		tickEnded(18322, false, null);
		tickEnded(18323, true, null);

		tracker.swung(18325);
		tracker.experienceGained(42, 18325);
		tickEnded(18325, true, PunishWeapon.NOXIOUS_HALBERD);

		mine(0, 18326);
		mine(19, 18326);
		tickEnded(18326, false, PunishWeapon.NOXIOUS_HALBERD);

		assertEquals(list("noxiousHalberdPunish=19"), recorded);
		assertEquals(list("0@18326", "0@18326"), handedBack);
		assertEquals(list("before 18325", "from 18325"), against);
	}

	/**
	 * A swing that earns no experience hit for nothing, so the one hit landing with it is the
	 * arrow's. All 31 scythe and halberd swings that hit in the two runs with experience logged
	 * earned it on the swing's tick, and the 7 that missed earned none.
	 */
	@Test
	public void aHitLandingWithASwingThatEarnedNothingIsTheArrows()
	{
		tickEnded(98, true, null);
		tracker.experienceGained(66, 99);
		tickEnded(99, true, null);

		tracker.swung(100);
		tickEnded(100, true, PunishWeapon.NOXIOUS_HALBERD);

		mine(30, 101);
		mine(0, 101);
		tickEnded(101, false, PunishWeapon.NOXIOUS_HALBERD);

		assertTrue(recorded.isEmpty());
		assertEquals(list("30@101", "0@101"), handedBack);
		assertEquals(list("before 100", "from 100"), against);
	}

	/** With no experience seen for the attacks, nothing tells the arrow apart. */
	@Test
	public void withoutExperienceAnExtraHitStaysThePunishs()
	{
		tickEnded(638, true, null);
		tracker.swung(639);
		tickEnded(639, true, PunishWeapon.SCYTHE);

		mine(5, 640);
		mine(23, 640);
		mine(1, 640);
		mine(4, 640);
		tickEnded(640, false, PunishWeapon.SCYTHE);

		assertEquals(4, recorded.size());
	}

	/**
	 * Any other melee weapon's hits can't be counted, so the arrow is only taken out when a hit
	 * comes to its share of the experience: 61 and 20 of 81, as the halberd's did at tick 2732.
	 */
	@Test
	public void anArrowWithAnotherWeaponsHitIsFoundByItsShareAlone()
	{
		tickEnded(2729, true, null);
		tracker.experienceGained(136, 2730);
		tickEnded(2730, true, null);

		tracker.swung(2731);
		tracker.experienceGained(45, 2731);
		tickEnded(2731, true, PunishWeapon.OTHER);

		mine(61, 2732);
		mine(20, 2732);
		tickEnded(2732, false, PunishWeapon.OTHER);

		assertEquals(list("otherMeleePunish=20"), recorded);
		assertEquals(list("61@2732", "0@2732"), handedBack);
	}

	/**
	 * Two hits that don't divide the way the experience did are both the swing's - claws, with an
	 * arrow that had already landed.
	 */
	@Test
	public void hitsThatDoNotMatchTheShareStayThePunishs()
	{
		tickEnded(2729, true, null);
		tracker.experienceGained(136, 2730);
		tickEnded(2730, true, null);

		tracker.swung(2731);
		tracker.experienceGained(90, 2731);
		tickEnded(2731, true, PunishWeapon.OTHER);

		mine(20, 2732);
		mine(10, 2732);
		tickEnded(2732, false, PunishWeapon.OTHER);

		assertEquals(list("otherMeleePunish=20", "otherMeleePunish=10"), recorded);
		assertEquals(list("0@2732", "0@2732"), handedBack);
	}

	/**
	 * The arrow had landed on the swing's own tick, so the two hits a tick later are both the
	 * swing's, though one of them comes to the arrow's share of the experience.
	 */
	@Test
	public void anArrowThatHasLandedIsNotLookedForAmongAnotherWeaponsHits()
	{
		tickEnded(98, true, null);
		tracker.experienceGained(66, 99);
		tickEnded(99, true, null);

		tracker.swung(100);
		tracker.experienceGained(66, 100);
		mine(30, 100);
		tickEnded(100, true, PunishWeapon.OTHER);

		mine(15, 101);
		mine(15, 101);
		tickEnded(101, false, PunishWeapon.OTHER);

		assertEquals(list("otherMeleePunish=15", "otherMeleePunish=15"), recorded);
		assertEquals(list("0@101", "0@101"), handedBack);
	}

	/**
	 * 2026-09-25 tick 589: the scythe's three hits at 590 and a 3 at 592, which was the skeleton
	 * thrall's (it attacked at 590) before thralls were told apart. Once a scythe's three hits have
	 * landed, a later hit of ours is something else.
	 */
	@Test
	public void aHitOfOursAfterTheScythesThreeIsNotAScythes()
	{
		tickEnded(588, true, null);
		tracker.swung(589);
		tickEnded(589, true, PunishWeapon.SCYTHE);

		mine(1, 590);
		mine(1, 590);
		mine(6, 590);
		tickEnded(590, false, PunishWeapon.SCYTHE);

		mine(3, 592);
		tickEnded(592, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=1", "scythePunish=1", "scythePunish=6"), recorded);
		assertEquals(list("0@590", "0@590", "0@590", "3@592"), handedBack);
	}

	/**
	 * The first animation with the scythe in hand need not be the swing - a block, or the bow shot
	 * before the switch - and the swing's hits then land a tick later than expected. None seen
	 * among 260 scythe and halberd swings in the logs; a godsword's are often read this way.
	 */
	@Test
	public void aScythesHitsLandingATickLateAreStillItsOwn()
	{
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, true, PunishWeapon.SCYTHE);

		tracker.swung(101);
		tickEnded(101, true, PunishWeapon.SCYTHE);

		mine(30, 102);
		mine(15, 102);
		mine(7, 102);
		tickEnded(102, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=30", "scythePunish=15", "scythePunish=7"), recorded);
		assertEquals(list("0@102", "0@102", "0@102"), handedBack);
	}

	/**
	 * A thrall's window took the scythe's own 2, so the thrall's 3 a tick later stands in for it:
	 * the swap {@link ThrallTracker} allows for, which keeps the scythe at three hits.
	 */
	@Test
	public void aThrallsHitStandsInForTheScytheHitItsWindowTook()
	{
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, true, PunishWeapon.SCYTHE);

		mine(13, 101);
		mine(6, 101);
		tickEnded(101, false, PunishWeapon.SCYTHE);

		mine(3, 102);
		tickEnded(102, false, PunishWeapon.SCYTHE);

		mine(40, 103);
		tickEnded(103, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=13", "scythePunish=6", "scythePunish=3"), recorded);
		assertEquals(list("0@101", "0@101", "0@102", "40@103"), handedBack);
	}

	@Test
	public void eachWeaponIsCreditedToItsOwnFigure()
	{
		tickEnded(974, true, null);
		tracker.swung(978);
		tickEnded(978, true, PunishWeapon.CRYSTAL_HALBERD);
		mine(10, 979);
		tickEnded(979, false, null);
		bonus(24, 980);
		tickEnded(980, false, null);

		tickEnded(1110, true, null);
		tracker.swung(1111);
		tickEnded(1111, true, PunishWeapon.OTHER);
		mine(25, 1112);
		tickEnded(1112, false, null);

		assertEquals(list("crystalHalberdPunish=10", "crystalHalberdPunish=24",
			"otherMeleePunish=25"), recorded);
	}

	/**
	 * Without the prayer and with the beam left alone, a melee hit is only a melee hit: none of it
	 * is the punish's, and the hit goes back to be counted as any other.
	 */
	@Test
	public void aSwingWithoutThePrayerOrTheBeamIsNotAPunish()
	{
		tickEnded(902, false, null);
		tracker.swung(903);
		tickEnded(903, false, PunishWeapon.SCYTHE);
		mine(30, 904);
		bonus(5, 904);
		tickEnded(904, false, PunishWeapon.SCYTHE);

		assertTrue(recorded.isEmpty());
		assertEquals("only what is plainly ours goes back", list("30@904"), handedBack);
	}

	/** Too late: the prayer came down on its own before the swing, and the beam went off. */
	@Test
	public void aSwingAfterThePrayerDroppedIsNotAPunish()
	{
		tickEnded(98, true, null);
		tickEnded(99, false, null);

		tracker.swung(100);
		tickEnded(100, false, PunishWeapon.SCYTHE);
		mine(30, 101);
		tickEnded(101, false, PunishWeapon.SCYTHE);

		assertTrue(recorded.isEmpty());
	}

	/**
	 * The boss cuts its beam off over and over on its own. One that falls long after a swing is not
	 * that swing's doing.
	 */
	@Test
	public void aBeamCutOffPastTheWindowIsNotTheSwings()
	{
		tickEnded(99, false, null);
		tracker.swung(100);
		tickEnded(100, false, PunishWeapon.SCYTHE);
		mine(30, 101);
		tickEnded(101, false, PunishWeapon.SCYTHE);

		tracker.beamCancelled(100 + PunishTracker.HIT_WINDOW + 1);

		assertTrue(recorded.isEmpty());
		assertFalse(tracker.mayBePunish(100 + PunishTracker.HIT_WINDOW + 1));
	}

	/**
	 * The window shuts before the weapon could swing again, so nothing after it is the punish's -
	 * a blowpipe switched back to after the swing lands its darts outside it.
	 */
	@Test
	public void aHitPastTheWindowIsNotThePunishs()
	{
		// A weapon whose hits may land late; a scythe's all land the tick after the swing.
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, false, PunishWeapon.OTHER);

		// The window's last tick is still inside it.
		mine(30, 100 + PunishTracker.HIT_WINDOW);
		tickEnded(100 + PunishTracker.HIT_WINDOW, false, null);

		// The tick after it is not, so a dart landing then is left to the spec tracker - through
		// the same check the plugin makes before it holds a hit for the punish.
		assertFalse(tracker.mayBePunish(100 + PunishTracker.HIT_WINDOW + 1));
		mine(25, 100 + PunishTracker.HIT_WINDOW + 1);
		tickEnded(100 + PunishTracker.HIT_WINDOW + 1, false, PunishWeapon.OTHER);

		assertEquals(list("otherMeleePunish=30"), recorded);
	}

	/**
	 * A blowpipe or a spell into the prayer is an animation too, and turns out not to be a swing
	 * once the weapon is read, so nothing after it is held.
	 */
	@Test
	public void anAnimationWithoutAMeleeWeaponIsNotASwing()
	{
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, true, null);

		assertTrue(recorded.isEmpty());
		assertFalse(tracker.mayBePunish(101));
	}

	/**
	 * A punish is one swing. What the player does a tick later - switching back, drinking - starts
	 * an animation too, and must neither move the window on nor take the splats still to land.
	 */
	@Test
	public void anAnimationInsideTheWindowDoesNotMoveIt()
	{
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, true, PunishWeapon.SCYTHE);

		// Switched back to the blowpipe and did something with it.
		tracker.swung(101);
		mine(20, 101);
		tickEnded(101, false, null);
		bonus(67, 102);
		tickEnded(102, false, null);

		assertEquals(list("scythePunish=20", "scythePunish=67"), recorded);
	}

	/**
	 * A melee swing made just before the prayer went up holds no window, so the punish that
	 * follows it straight away is still counted.
	 */
	@Test
	public void aSwingBeforeThePrayerDoesNotHoldUpThePunishAfterIt()
	{
		tickEnded(99, false, null);
		tracker.swung(100);
		tickEnded(100, false, PunishWeapon.SCYTHE);

		tickEnded(101, true, null);
		tracker.swung(102);
		tickEnded(102, true, PunishWeapon.SCYTHE);
		mine(40, 103);
		tickEnded(103, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=40"), recorded);
	}

	/** A hit for nothing is the swing's all the same, and counts for nothing. */
	@Test
	public void aBlockedSwingCountsNothing()
	{
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, true, PunishWeapon.NOXIOUS_HALBERD);
		mine(0, 101);
		tickEnded(101, false, PunishWeapon.NOXIOUS_HALBERD);

		assertTrue(recorded.isEmpty());
		assertEquals(list("0@101"), handedBack);
	}

	@Test
	public void aResetForgetsEverythingInFlight()
	{
		tickEnded(99, true, null);
		tracker.swung(100);
		tracker.reset();

		assertFalse(tracker.mayBePunish(101));
		tickEnded(101, false, PunishWeapon.SCYTHE);

		assertTrue(recorded.isEmpty());
		assertFalse(tracker.isPraying());
	}

	@Test
	public void theWeaponIsOnlyReadWhenASwingIsWaiting()
	{
		int[] reads = new int[1];

		tracker.tickEnded(99, true, () ->
		{
			reads[0]++;
			return null;
		});

		assertEquals("a tick without a swing looks at no equipment", 0, reads[0]);
	}

	/** A hit of ours on the boss, offered the way the plugin offers it. */
	private void mine(int amount, int tick)
	{
		if (tracker.mayBePunish(tick))
		{
			tracker.hit(amount, true, tick);
			return;
		}

		tracker.ownHitNotHeld(tick, true);
	}

	/** A strength-bonus splat, which is not one of the types plainly ours. */
	private void bonus(int amount, int tick)
	{
		if (tracker.mayBePunish(tick))
		{
			tracker.hit(amount, false, tick);
		}
	}

	private void tickEnded(int tick, boolean praying, PunishWeapon inHand)
	{
		tracker.tickEnded(tick, praying, () -> inHand);
	}

	private static List<String> list(String... values)
	{
		return new ArrayList<>(Arrays.asList(values));
	}
}
