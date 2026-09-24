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

	/** What was handed back to be counted as any other hit is. */
	private final List<String> handedBack = new ArrayList<>();

	private final PunishTracker tracker = new PunishTracker(
		(metric, amount) -> recorded.add(metric.key() + "=" + amount),
		(amount, tick) -> handedBack.add(amount + "@" + tick));

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
		tickEnded(99, true, null);
		tracker.swung(100);
		tickEnded(100, false, PunishWeapon.SCYTHE);

		// The window's last tick is still inside it.
		mine(30, 100 + PunishTracker.HIT_WINDOW);
		tickEnded(100 + PunishTracker.HIT_WINDOW, false, null);

		// The tick after it is not, so a dart landing then is left to the spec tracker - through
		// the same check the plugin makes before it holds a hit for the punish.
		assertFalse(tracker.mayBePunish(100 + PunishTracker.HIT_WINDOW + 1));
		mine(25, 100 + PunishTracker.HIT_WINDOW + 1);
		tickEnded(100 + PunishTracker.HIT_WINDOW + 1, false, PunishWeapon.SCYTHE);

		assertEquals(list("scythePunish=30"), recorded);
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
		}
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
