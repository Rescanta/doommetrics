package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AttackLedgerTest
{
	private static final int BOSS = 7;
	private static final int LARVA = 9;
	private static final int ARROW = 1120;
	private static final int GHOST_BOLT = 1907;

	/** 625 hitpoints: 4/3 of 1.675 a point of damage. */
	private static final double RATE = ExperienceRate.perDamage(625);

	private final List<AttackLedger.Verdict> verdicts = new ArrayList<>();
	private final List<AttackLedger.Attack> lapsed = new ArrayList<>();

	private final AttackLedger ledger = new AttackLedger(new AttackLedger.Listener()
	{
		@Override
		public void settled(AttackLedger.Verdict verdict)
		{
			verdicts.add(verdict);
		}

		@Override
		public void lapsed(AttackLedger.Attack attack)
		{
			lapsed.add(attack);
		}
	});

	/** Runs the ledger up to the end of {@code tick}, which settles the splats of the tick before. */
	private void endTicks(int from, int to)
	{
		for (int tick = from; tick <= to; tick++)
		{
			ledger.tickEnded(tick);
		}
	}

	private AttackLedger.Kind kindOf(int verdict)
	{
		AttackLedger.Attack attack = verdicts.get(verdict).attack;
		return attack == null ? null : attack.kind;
	}

	@Test
	public void aShotTakesTheSplatOnItsLandingTick()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.splat(102, BOSS, 31, "boss");
		endTicks(100, 103);

		assertEquals(1, verdicts.size());
		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(0, verdicts.get(0).offset());
		assertTrue(lapsed.isEmpty());
	}

	/** Nothing is settled until the tick after: a projectile is first seen once its tick is over. */
	@Test
	public void aSplatWaitsATickForItsShotToBeSeen()
	{
		ledger.splat(100, BOSS, 2, "boss");
		ledger.tickEnded(100);
		assertTrue(verdicts.isEmpty());

		ledger.thrallShot(GHOST_BOLT, 100, 100, BOSS);
		ledger.tickEnded(101);

		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(0));
	}

	@Test
	public void aShotReplacesTheSwingItsAnimationWasTakenFor()
	{
		ledger.swung(100, BOSS);
		ledger.tickEnded(100);
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.splat(102, BOSS, 31, "boss");
		endTicks(101, 106);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertTrue("the swing is gone, so nothing lapses", lapsed.isEmpty());
	}

	@Test
	public void aSwingLandsTheTickAfterAndTakesEveryHit()
	{
		ledger.swung(100, BOSS);
		ledger.splat(101, BOSS, 40, "boss");
		ledger.splat(101, BOSS, 20, "boss");
		ledger.splat(101, BOSS, 10, "boss");
		endTicks(100, 102);

		assertEquals(3, verdicts.size());

		for (int i = 0; i < 3; i++)
		{
			assertEquals(AttackLedger.Kind.SWING, kindOf(i));
		}
	}

	@Test
	public void aHitOnTheSwingsOwnTickIsNotItsOwn()
	{
		ledger.swung(100, BOSS);
		ledger.splat(100, BOSS, 40, "boss");
		endTicks(100, 101);

		assertNull(verdicts.get(0).attack);
	}

	/** The arrow fired before a punish lands with it, and is the first of the tick's hits. */
	@Test
	public void theEarlierShotIsAheadOfTheSwing()
	{
		ledger.shot(ARROW, 98, 101, BOSS);
		ledger.swung(100, BOSS);
		ledger.splat(101, BOSS, 12, "boss");
		ledger.splat(101, BOSS, 44, "boss");
		ledger.splat(101, BOSS, 22, "boss");
		endTicks(100, 102);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(AttackLedger.Kind.SWING, kindOf(1));
		assertEquals(AttackLedger.Kind.SWING, kindOf(2));
	}

	/** An animation that was no attack stays open three ticks, and must not take a shot's hit. */
	@Test
	public void theAttackDueOnTheTickBeatsAnEarlierOneStillOpen()
	{
		ledger.swung(100, BOSS);
		ledger.shot(ARROW, 101, 103, BOSS);
		ledger.splat(103, BOSS, 31, "boss");
		endTicks(100, 104);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(1, lapsed.size());
		assertEquals(AttackLedger.Kind.SWING, lapsed.get(0).kind);
	}

	@Test
	public void aThrallOnlyTakesASmallHit()
	{
		ledger.thrallShot(GHOST_BOLT, 99, 101, BOSS);
		ledger.shot(ARROW, 100, 101, BOSS);
		ledger.splat(101, BOSS, 31, "boss");
		ledger.splat(101, BOSS, 3, "boss");
		endTicks(100, 102);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(1));
	}

	@Test
	public void twoSmallHitsGoInTheOrderTheirAttacksWereMade()
	{
		ledger.shot(ARROW, 99, 101, BOSS);
		ledger.thrallShot(GHOST_BOLT, 100, 101, BOSS);
		ledger.splat(101, BOSS, 2, "boss");
		ledger.splat(101, BOSS, 1, "boss");
		endTicks(100, 102);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(1));
	}

	@Test
	public void ofTwoMadeOnOneTickTheThrallIsFirst()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.thrallShot(GHOST_BOLT, 100, 102, BOSS);
		ledger.splat(102, BOSS, 0, "boss");
		ledger.splat(102, BOSS, 0, "boss");
		endTicks(100, 103);

		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(0));
		assertEquals(AttackLedger.Kind.SHOT, kindOf(1));
	}

	@Test
	public void aShotOnlyTakesAHitOnWhatItWasFiredAt()
	{
		ledger.shot(ARROW, 100, 102, LARVA);
		ledger.splat(102, BOSS, 31, "boss");
		ledger.splat(102, LARVA, 2, "larva");
		endTicks(100, 103);

		assertNull(verdicts.get(0).attack);
		assertEquals(AttackLedger.Kind.SHOT, kindOf(1));
	}

	@Test
	public void aZombieHitsAnythingTheTickAfter()
	{
		ledger.thrallSwung(100);
		ledger.splat(101, LARVA, 2, "larva");
		ledger.splat(101, BOSS, 3, "boss");
		endTicks(100, 102);

		assertEquals(AttackLedger.Kind.THRALL_SWING, kindOf(0));
		assertNull("one attack, one hit", verdicts.get(1).attack);
	}

	@Test
	public void aShotATickEitherSideOfItsLandingIsStillTaken()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.splat(103, BOSS, 31, "boss");
		endTicks(100, 104);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(1, verdicts.get(0).offset());

		ledger.shot(ARROW, 110, 113, BOSS);
		ledger.splat(112, BOSS, 31, "boss");
		endTicks(110, 113);

		assertEquals(-1, verdicts.get(1).offset());
	}

	@Test
	public void aShotThatNeverLandsLapsesOnce()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		endTicks(100, 110);

		assertTrue(verdicts.isEmpty());
		assertEquals(1, lapsed.size());
		assertEquals(ARROW, lapsed.get(0).projectile);
	}

	@Test
	public void theAttackMadeOnTheSpecsTickIsTheSpec()
	{
		ledger.shot(ARROW, 98, 100, BOSS);
		ledger.shot(1995, 100, 102, BOSS);
		ledger.specFired(100);
		ledger.splat(100, BOSS, 20, "boss");
		ledger.splat(102, BOSS, 110, "boss");
		endTicks(100, 103);

		assertFalse(verdicts.get(0).spec);
		assertTrue(verdicts.get(1).spec);
		assertEquals(1995, verdicts.get(1).attack.projectile);
	}

	@Test
	public void theExperienceOfTheTickSaysWhatTheShotHitFor()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.experience(100, 69, RATE);
		ledger.splat(102, BOSS, 31, "boss");
		endTicks(100, 103);

		AttackLedger.Verdict verdict = verdicts.get(0);
		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(69, verdict.experience);
		assertEquals(31, verdict.least);
		assertEquals(31, verdict.most);
	}

	/** The thrall's hit lands a tick ahead of where it was due, on the tick our arrow is due. */
	@Test
	public void aShotLeavesAHitItsExperienceDoesNotAllowToWhoeverElseIsDue()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.experience(100, 69, RATE);
		ledger.thrallShot(GHOST_BOLT, 101, 103, BOSS);
		ledger.splat(102, BOSS, 3, "boss");
		ledger.splat(102, BOSS, 31, "boss");
		endTicks(100, 103);

		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(0));
		assertEquals(AttackLedger.Kind.SHOT, kindOf(1));
	}

	@Test
	public void aHitNoAttackAllowsIsLeftAndSaysWhichAttackRefusedIt()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.experience(100, 69, RATE);
		ledger.splat(102, BOSS, 12, "boss");
		ledger.splat(102, BOSS, 31, "boss");
		endTicks(100, 103);

		assertNull(verdicts.get(0).attack);
		assertEquals(ARROW, verdicts.get(0).refused.projectile);
		assertEquals("12 on boss at tick 102 = nothing due, too much or too little for shot 1120"
			+ " made 100 due 102 at #7", verdicts.get(0).toString());
		assertEquals("its own hit is still taken", AttackLedger.Kind.SHOT, kindOf(1));
	}

	@Test
	public void noExperienceAtAKnownRateIsAMiss()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.experience(100, 0, RATE);
		ledger.splat(102, BOSS, 3, "boss");
		ledger.splat(102, BOSS, 0, "boss");
		endTicks(100, 103);

		assertNull(verdicts.get(0).attack);
		assertEquals(AttackLedger.Kind.SHOT, kindOf(1));
	}

	@Test
	public void aSwingsHitsMayNotComeToMoreThanItsExperience()
	{
		ledger.swung(110, BOSS);
		ledger.experience(110, 89, RATE);
		ledger.splat(111, BOSS, 20, "boss");
		ledger.splat(111, BOSS, 20, "boss");
		ledger.splat(111, BOSS, 20, "boss");
		endTicks(110, 112);

		assertEquals(AttackLedger.Kind.SWING, kindOf(0));
		assertEquals(AttackLedger.Kind.SWING, kindOf(1));
		assertNull("89 is 40 damage at most", verdicts.get(2).attack);
	}

	@Test
	public void experienceAtAnUnknownRateIsOnlyReported()
	{
		ledger.shot(ARROW, 100, 102, LARVA);
		ledger.experience(100, 8, 0);
		ledger.splat(102, LARVA, 2, "larva");
		endTicks(100, 103);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(8, verdicts.get(0).experience);
		assertFalse(verdicts.get(0).sized);
	}

	@Test
	public void experienceSaysNothingOfAThrallsHit()
	{
		ledger.thrallShot(GHOST_BOLT, 100, 101, BOSS);
		ledger.experience(100, 69, RATE);
		ledger.splat(101, BOSS, 2, "boss");
		endTicks(100, 102);

		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(0));
		assertEquals(0, verdicts.get(0).experience);
	}

	@Test
	public void theVerdictReadsAsOneLine()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.specFired(100);
		ledger.experience(100, 69, RATE);
		ledger.splat(103, BOSS, 31, "Doom (14707) #7");
		ledger.splat(103, LARVA, 2, "Larva (14711) #9");
		endTicks(100, 104);

		assertEquals("31 on Doom (14707) #7 at tick 103 = spec shot 1120 made 100 due 102 (+1),"
			+ " experience 69 says 31 to 31", verdicts.get(0).toString());
		assertEquals("2 on Larva (14711) #9 at tick 103 = nothing due", verdicts.get(1).toString());
	}

	/**
	 * What a tick says of its attacks comes in no fixed order: the swing, its projectile, the
	 * energy and the experience are separate events. The verdicts must not depend on it.
	 */
	@Test
	public void theOrderATicksAttacksAreHeardInChangesNothing()
	{
		List<Runnable> heard = new ArrayList<>();
		heard.add(() -> ledger.shot(ARROW, 98, 101, BOSS));
		heard.add(() -> ledger.thrallShot(GHOST_BOLT, 100, 101, BOSS));
		heard.add(() -> ledger.swung(100, BOSS));
		heard.add(() -> ledger.shot(1995, 100, 102, BOSS));
		heard.add(() -> ledger.specFired(100));
		heard.add(() -> ledger.experience(100, 69, RATE));

		List<String> first = null;

		for (List<Runnable> order : orders(heard))
		{
			ledger.reset();
			verdicts.clear();
			lapsed.clear();
			order.forEach(Runnable::run);
			ledger.splat(101, BOSS, 2, "boss");
			ledger.splat(101, BOSS, 1, "boss");
			ledger.splat(102, BOSS, 31, "boss");
			endTicks(100, 106);

			List<String> read = new ArrayList<>();
			verdicts.forEach(verdict -> read.add(verdict.toString()));
			lapsed.forEach(attack -> read.add(attack + " took nothing"));

			if (first == null)
			{
				first = read;
				assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
				assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(1));
				assertTrue(verdicts.get(2).spec);
				assertTrue("the spec's animation was no swing", lapsed.isEmpty());
			}

			assertEquals(first, read);
		}
	}

	private static <T> List<List<T>> orders(List<T> items)
	{
		List<List<T>> orders = new ArrayList<>();

		if (items.isEmpty())
		{
			orders.add(new ArrayList<>());
			return orders;
		}

		for (int i = 0; i < items.size(); i++)
		{
			List<T> rest = new ArrayList<>(items);
			T head = rest.remove(i);

			for (List<T> order : orders(rest))
			{
				order.add(0, head);
				orders.add(order);
			}
		}

		return orders;
	}

	@Test
	public void aShotIsTheSwingMadeTwoTicksBeforeItStartedToMove()
	{
		ledger.swung(100, BOSS);
		ledger.experience(100, 69, RATE);
		endTicks(100, 101);
		ledger.shotStarted(ARROW, 102, 103, BOSS);
		ledger.splat(103, BOSS, 31, "boss");
		endTicks(102, 106);

		assertEquals(AttackLedger.Kind.SHOT, kindOf(0));
		assertEquals(100, verdicts.get(0).attack.made);
		assertTrue(verdicts.get(0).sized);
		assertTrue("the swing is the shot, so nothing lapses", lapsed.isEmpty());
	}

	@Test
	public void aShotWithNoSwingTwoTicksBeforeIsTheOneMadeTheTickBefore()
	{
		ledger.swung(101, BOSS);
		endTicks(101, 101);
		ledger.shotStarted(ARROW, 102, 102, BOSS);
		ledger.splat(102, BOSS, 31, "boss");
		endTicks(102, 106);

		assertEquals(101, verdicts.get(0).attack.made);
		assertTrue(lapsed.isEmpty());
	}

	@Test
	public void aShotIsNotASwingMadeAtSomethingElse()
	{
		ledger.swung(100, LARVA);
		ledger.swung(101, BOSS);
		endTicks(100, 101);
		ledger.shotStarted(ARROW, 102, 102, BOSS);
		ledger.splat(102, BOSS, 31, "boss");
		ledger.splat(102, LARVA, 2, "larva");
		endTicks(102, 106);

		assertEquals(101, verdicts.get(0).attack.made);
		assertEquals(AttackLedger.Kind.SWING, kindOf(1));
	}

	@Test
	public void aShotNoAnimationSpokeForWasMadeTwoTicksBefore()
	{
		ledger.shotStarted(ARROW, 102, 103, BOSS);
		ledger.splat(103, BOSS, 31, "boss");
		endTicks(102, 104);

		assertEquals(100, verdicts.get(0).attack.made);
	}

	/** A block is an animation too, and so is a cast. */
	@Test
	public void anAnimationThatEarnedNothingOnlyTakesAZero()
	{
		ledger.swung(100, AttackLedger.UNKNOWN);
		ledger.experience(100, 0, 0);
		ledger.splat(101, BOSS, 26, "boss");
		ledger.splat(101, BOSS, 0, "boss");
		endTicks(100, 105);

		assertNull(verdicts.get(0).attack);
		assertEquals(AttackLedger.Kind.SWING, kindOf(1));
	}

	@Test
	public void anAnimationThatEarnedNothingAndTookNothingIsNotReported()
	{
		ledger.swung(100, AttackLedger.UNKNOWN);
		ledger.experience(100, 0, 0);
		endTicks(100, 105);

		assertTrue(lapsed.isEmpty());
	}

	@Test
	public void aSpecThatEarnedNothingAndTookNothingIsReported()
	{
		ledger.swung(100, BOSS);
		ledger.specFired(100);
		ledger.experience(100, 0, RATE);
		endTicks(100, 105);

		assertEquals(1, lapsed.size());
	}

	/** Seen in game: a spec that hit 0 two ticks late, with a cast animated in between. */
	@Test
	public void aKnownAttackTakesAZeroAheadOfAnAnimationDueNearer()
	{
		ledger.swung(100, BOSS);
		ledger.specFired(100);
		ledger.experience(100, 0, RATE);
		ledger.swung(102, BOSS);
		ledger.experience(102, 0, RATE);
		ledger.splat(103, BOSS, 0, "boss");
		endTicks(100, 107);

		assertEquals(100, verdicts.get(0).attack.made);
		assertTrue(verdicts.get(0).spec);
		assertTrue(lapsed.isEmpty());
	}

	/** Seen in game: a thrall that attacked first, with a scythe's three hits due on its tick. */
	@Test
	public void aThrallThatAnimatedBeforeOurSwingLandsFirst()
	{
		ledger.thrallShooting(100);
		ledger.swung(101, BOSS);
		ledger.experience(101, 84, RATE);
		endTicks(100, 101);
		ledger.thrallShotStarted(GHOST_BOLT, 102, 102, BOSS);
		ledger.splat(102, BOSS, 0, "boss");
		ledger.splat(102, BOSS, 22, "boss");
		endTicks(102, 106);

		assertEquals(AttackLedger.Kind.THRALL_SHOT, kindOf(0));
		assertEquals(100, verdicts.get(0).attack.made);
		assertEquals(AttackLedger.Kind.SWING, kindOf(1));
	}

	@Test
	public void aThrallsShotWithNoAnimationWasMadeTheTickBefore()
	{
		ledger.thrallShotStarted(GHOST_BOLT, 102, 102, BOSS);
		ledger.splat(102, BOSS, 2, "boss");
		endTicks(102, 104);

		assertEquals(101, verdicts.get(0).attack.made);
	}

	@Test
	public void resetForgetsWhatWasInFlight()
	{
		ledger.shot(ARROW, 100, 102, BOSS);
		ledger.splat(101, BOSS, 5, "boss");
		ledger.reset();
		endTicks(101, 110);

		assertTrue(verdicts.isEmpty());
		assertTrue(lapsed.isEmpty());
	}
}
