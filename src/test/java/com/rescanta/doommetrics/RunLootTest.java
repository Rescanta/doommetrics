package com.rescanta.doommetrics;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunLootTest
{
	private static final Instant START = Instant.EPOCH;

	private static Instant at(long seconds)
	{
		return START.plusSeconds(seconds);
	}

	@Test
	public void lootIsListedInTheOrderItWasFirstSeen()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.loot().recordLoot(31109, "Mokhaiotl cloth", 1);
		run.loot().recordLoot(31130, "Dom", 1);
		run.loot().recordLoot(31113, "Eye of ayak", 1);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Dom", "Eye of ayak"), run.loot().getClaimed());
	}

	/** A deep run can roll the same unique more than once, and both of them happened. */
	@Test
	public void aDropEarnedTwiceIsListedTwice()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.loot().recordLoot(31109, "Mokhaiotl cloth", 2);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Mokhaiotl cloth"), run.loot().getClaimed());
	}

	/**
	 * The loot pile is read both on the claim click and on the game's claim script, and the pet is
	 * announced in chat as well as being an item, so the sources overlap by design. Seeing the same
	 * pile twice is not the same as earning its contents twice.
	 */
	@Test
	public void seeingTheSamePileTwiceDoesNotInflateTheCount()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.loot().recordLoot(31109, "Mokhaiotl cloth", 2);
		run.loot().recordLoot(31109, "Mokhaiotl cloth", 2);
		run.loot().recordLoot(31130, "Dom", 1);
		run.loot().recordLoot(31130, "Dom", 1);

		assertEquals(Arrays.asList("Mokhaiotl cloth", "Mokhaiotl cloth", "Dom"), run.loot().getClaimed());
	}

	/** A pile that has grown since it was last read is the second drop landing in it. */
	@Test
	public void aPileSeenHoldingMoreRaisesTheCount()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.loot().recordLoot(31088, "Avernic treads", 1);
		run.loot().recordLoot(31088, "Avernic treads", 3);

		assertEquals(3, run.loot().getClaimed().size());
	}

	/** A read that saw fewer than a previous one is a partial view, not a drop being taken back. */
	@Test
	public void aPileSeenHoldingLessDoesNotLowerTheCount()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.loot().recordLoot(31088, "Avernic treads", 2);
		run.loot().recordLoot(31088, "Avernic treads", 1);

		assertEquals(2, run.loot().getClaimed().size());
	}

	/** A drop the item cache could not name is dropped rather than listed as a blank. */
	@Test
	public void anUnnamedDropIsNotListed()
	{
		DelveRun run = new DelveRun(START, 1, false);

		run.loot().recordLoot(31088, null, 1);

		assertTrue(run.loot().getClaimed().isEmpty());
	}

	@Test
	public void aRunWithNoLootListsNone()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		assertTrue(run.loot().getClaimed().isEmpty());
	}

	/** Loot lands in the pile when a delve is cleared, so a drop seen then came off that delve. */
	@Test
	public void aDropLandsOnTheDelveJustCleared()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);

		assertEquals(2, run.loot().sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(2, landed.get(0).level);
		assertEquals(1, landed.get(0).quantity);
	}

	/**
	 * The pile and the chat line clearing the delve can arrive either way round. Seen first, the
	 * pile is the delve still being fought, not the one cleared before it.
	 */
	@Test
	public void aPileSeenAheadOfTheClearLandsOnTheDelveBeingFought()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(70));

		run.loot().sawInPile(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 1);
		run.complete(2, at(120), null);

		assertEquals(2, run.loot().getLanded().get(0).level);
	}

	/**
	 * The game warns about a unique on every descend while it sits in the pile, and sends the pile
	 * over again each time. An eye off delve 10 is on delve 10 alone, and a second one off delve 20
	 * is on delve 20 alone.
	 */
	@Test
	public void aDropStillInThePileIsNotPlacedAgainOnEveryDelveAfter()
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 25; level++)
		{
			run.enterLevel(level, at((level - 1) * 60));
			run.complete(level, at(level * 60), null);

			int held = level >= 20 ? 2 : level >= 10 ? 1 : 0;

			if (held > 0)
			{
				run.loot().sawInPile(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak", held);
			}
		}

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(2, landed.size());
		assertEquals(10, landed.get(0).level);
		assertEquals(20, landed.get(1).level);
		assertEquals(1, landed.get(1).quantity);
		assertEquals(2, landed.get(1).heldAfter);
	}

	/** Two copies of the pile are watched, and a drop showing up in both dropped once. */
	@Test
	public void twoCopiesOfThePilePlaceADropOnce()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		assertEquals(1, run.loot().sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1));
		assertEquals(RunLoot.NOT_RECORDED,
			run.loot().sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1));

		assertEquals(1, run.loot().getLanded().size());
	}

	/** The pile empties when it is claimed. That takes back nothing, and a later drop still counts. */
	@Test
	public void aPileBeingEmptiedTakesNothingBack()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.loot().sawInPile(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 2);

		assertEquals(RunLoot.NOT_RECORDED, run.loot().sawInPile(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 0));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(2, landed.get(0).quantity);
	}

	/** A run joined part way through has drops in its pile from delves nobody saw. */
	@Test
	public void aJoinedRunDoesNotPlaceWhatWasAlreadyInThePile()
	{
		DelveRun run = new DelveRun(START, 14, true);
		run.loot().pileAlreadyHeld(ItemID.EYE_OF_AYAK_UNCHARGED, 1);
		run.complete(14, at(60), null);

		assertEquals(RunLoot.NOT_RECORDED,
			run.loot().sawInPile(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak", 1));

		run.enterLevel(15, at(70));
		run.complete(15, at(120), null);
		assertEquals(15, run.loot().sawInPile(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak", 2));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(15, landed.get(0).level);
		assertEquals(1, landed.get(0).quantity);
	}

	/** The pet is announced rather than seen in the pile, and lands as one more than there was. */
	@Test
	public void thePetLandsFromItsChatLine()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(70));

		run.loot().sawInPile(ItemID.DOMPET, "Dom", run.loot().held(ItemID.DOMPET) + 1);

		assertEquals(1, run.loot().getLanded().size());
		assertEquals(2, run.loot().getLanded().get(0).level);
		assertEquals(1, run.loot().getLanded().get(0).heldAfter);
	}

	/**
	 * Every descend with a unique in the pile brings its warning up again. The treads off delve 6
	 * are on delve 6 alone, however many descends later they are still being warned about.
	 */
	@Test
	public void aWarningRepeatedOnEveryDescendPlacesItsDropOnce()
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= 10; level++)
		{
			run.enterLevel(level, at((level - 1) * 60));
			run.complete(level, at(level * 60), null);
			run.loot().descending();

			if (level >= 6)
			{
				run.loot().warnedOf(ItemID.AVERNIC_TREADS, "Avernic treads");
			}
		}

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(6, landed.get(0).level);
	}

	/** One warning per copy: a second cloth brings a second warning, and lands where it dropped. */
	@Test
	public void aSecondWarningInOneTryIsASecondCopy()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.loot().descending();
		assertEquals(1, run.loot().warnedOf(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth"));

		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);
		run.loot().descending();
		assertEquals("the cloth off delve 1", RunLoot.NOT_RECORDED,
			run.loot().warnedOf(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth"));
		assertEquals("a new one off delve 2", 2,
			run.loot().warnedOf(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth"));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(2, landed.size());
		assertEquals(2, landed.get(1).level);
		assertEquals(2, landed.get(1).heldAfter);
	}

	/** Backing out of a warning and trying again brings the same warnings up from the first. */
	@Test
	public void tryingAgainAfterBackingOutCountsTheWarningsAfresh()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		run.loot().descending();
		run.loot().warnedOf(ItemID.EYE_OF_AYAK, "Eye of ayak");
		run.loot().descending();
		assertEquals(RunLoot.NOT_RECORDED, run.loot().warnedOf(ItemID.EYE_OF_AYAK, "Eye of ayak"));

		assertEquals(1, run.loot().getLanded().size());
		assertEquals(1, run.loot().held(ItemID.EYE_OF_AYAK));
	}

	/**
	 * A run joined part way through gets warned about what was already in the pile before it has
	 * gone down a delve we watched. Those are not drops off the delve just cleared.
	 */
	@Test
	public void aJoinedRunTakesItsFirstWarningsAsAlreadyHeld()
	{
		DelveRun run = new DelveRun(START, 14, true);
		run.complete(14, at(60), null);
		run.loot().descending();
		assertEquals(RunLoot.NOT_RECORDED, run.loot().warnedOf(ItemID.AVERNIC_TREADS, "Avernic treads"));

		run.enterLevel(15, at(70));
		run.complete(15, at(120), null);
		run.loot().descending();
		run.loot().warnedOf(ItemID.AVERNIC_TREADS, "Avernic treads");
		assertEquals(15, run.loot().warnedOf(ItemID.AVERNIC_TREADS, "Avernic treads"));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(15, landed.get(0).level);
	}

	/** The glowing hole says a unique dropped, not which - and saying it twice is still one. */
	@Test
	public void aSignalledUniqueIsPlacedOnceAsUnknown()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		assertTrue(run.loot().uniqueSignalled());
		assertFalse(run.loot().uniqueSignalled());

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(RunLoot.UNKNOWN_UNIQUE, landed.get(0).itemId);
		assertEquals(1, landed.get(0).level);
	}

	/** A warning naming the unique off the same delve turns the unknown into it. */
	@Test
	public void aWarningNamesTheUnknownUniqueOffItsDelve()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);
		run.loot().uniqueSignalled();

		run.loot().descending();
		run.loot().warnedOf(ItemID.AVERNIC_TREADS, "Avernic treads");

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(ItemID.AVERNIC_TREADS, landed.get(0).itemId);
		assertEquals(2, landed.get(0).level);
	}

	/**
	 * A hole glowing over a unique the run already knows about says nothing new - it has been
	 * glowing since that one dropped - so it places nothing.
	 */
	@Test
	public void aGlowOverAUniqueAlreadyKnownAboutPlacesNothing()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.loot().descending();
		run.loot().warnedOf(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth");

		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);

		assertFalse(run.loot().uniqueSignalled());

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(ItemID.MOKHAIOTL_CLOTH, landed.get(0).itemId);
		assertEquals(1, landed.get(0).level);
	}

	/**
	 * A second unique named while the glow's mark is still outstanding takes that mark over. The
	 * glow never said which of them it meant, so the first named is the guess - what this pins is
	 * that the mark is spent once and the second drop is placed on its own delve rather than lost.
	 */
	@Test
	public void aSecondUniqueNamedWhileTheGlowIsOutstandingTakesTheMark()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.loot().uniqueSignalled();

		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);

		// The pile read shows both at once: the glow's unique, and one it never spoke for.
		assertEquals("the first named takes the glow's delve", 1,
			run.loot().sawInPile(ItemID.AVERNIC_TREADS, "Avernic treads", 1));
		assertEquals("the second is placed where we stand", 2,
			run.loot().sawInPile(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 1));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(2, landed.size());
		assertEquals(ItemID.AVERNIC_TREADS, landed.get(0).itemId);
		assertEquals(1, landed.get(0).level);
		assertEquals(ItemID.MOKHAIOTL_CLOTH, landed.get(1).itemId);
		assertEquals(2, landed.get(1).level);
	}

	/**
	 * A duplicate pet drops into the pile and warns on every descend without ever lighting the
	 * hole, so it is not what a later glow is about. The one after it still gets its mark.
	 */
	@Test
	public void aDuplicatePetInThePileDoesNotSwallowALaterGlow()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		// Delve 1 dropped a pet this character already owns: the hole was left plain, and the
		// descend warning is the first thing to say anything about it.
		run.loot().descending();
		assertEquals(1, run.loot().warnedOf(ItemID.DOMPET, "Dom"));

		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);

		assertTrue("delve 2 glowed, which the pet cannot account for", run.loot().uniqueSignalled());

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(2, landed.size());
		assertEquals(ItemID.DOMPET, landed.get(0).itemId);
		assertEquals(1, landed.get(0).level);
		assertEquals(RunLoot.UNKNOWN_UNIQUE, landed.get(1).itemId);
		assertEquals(2, landed.get(1).level);
	}

	/**
	 * The first pet a character is given does light the hole, and goes on lighting it while it
	 * sits in the pile. A glow over that one says nothing the run does not already know.
	 */
	@Test
	public void aPetTheGlowMarkedExplainsTheGlowsAfterIt()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		// Delve 1 glowed and the descend warning named what for: a first pet.
		assertTrue(run.loot().uniqueSignalled());
		run.loot().descending();
		assertEquals("the pet keeps the delve the glow put it on", 1,
			run.loot().warnedOf(ItemID.DOMPET, "Dom"));

		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);

		assertFalse(run.loot().uniqueSignalled());

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(ItemID.DOMPET, landed.get(0).itemId);
		assertEquals(1, landed.get(0).level);
	}

	/** Named delves later, a drop still sits on the delve the glow put it on. */
	@Test
	public void aDropNamedLaterKeepsTheDelveTheGlowPutItOn()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.loot().uniqueSignalled();

		run.enterLevel(2, at(70));
		run.complete(2, at(120), null);
		assertEquals("the delve the glow put it on", 1,
			run.loot().sawInPile(ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth", 1));

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(ItemID.MOKHAIOTL_CLOTH, landed.get(0).itemId);
		assertEquals(1, landed.get(0).level);
	}

	/**
	 * A run joined part way through marks the glow too. The drop may have come off a delve nobody
	 * watched, but the delve it was cleared by is the only one such a run can name.
	 */
	@Test
	public void aJoinedRunMarksAGlowingHoleOnTheDelveItWasClearedBy()
	{
		DelveRun run = new DelveRun(START, 5, true);
		run.complete(5, at(60), null);

		assertTrue(run.loot().uniqueSignalled());

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(RunLoot.UNKNOWN_UNIQUE, landed.get(0).itemId);
		assertEquals(5, landed.get(0).level);
	}

	/** A pile read as the run was picked up is a pile the run knows about, so the glow adds nothing. */
	@Test
	public void aGlowOverAPileReadAsTheRunWasJoinedPlacesNothing()
	{
		DelveRun run = new DelveRun(START, 5, true);
		run.loot().pileAlreadyHeld(ItemID.EYE_OF_AYAK_UNCHARGED, 1);
		run.complete(5, at(60), null);

		assertFalse(run.loot().uniqueSignalled());
		assertTrue(run.loot().getLanded().isEmpty());
	}

	/**
	 * A warning about a pile a joined run inherited places nothing of its own, but it does name
	 * the mark the glow put up - which is worth more than a question mark.
	 */
	@Test
	public void anUntrustedWarningNamesTheUnknownWithoutPlacingADrop()
	{
		DelveRun run = new DelveRun(START, 5, true);
		run.complete(5, at(60), null);
		run.loot().uniqueSignalled();

		run.loot().descending();
		run.loot().warnedOf(ItemID.AVERNIC_TREADS, "Avernic treads");

		List<RunLoot.Landed> landed = run.loot().getLanded();
		assertEquals(1, landed.size());
		assertEquals(ItemID.AVERNIC_TREADS, landed.get(0).itemId);
		assertEquals(5, landed.get(0).level);
	}

	/**
	 * Whichever form of the eye the warning, the pile and the claim name, it is one eye: placed
	 * once, and kept by a claim naming the other form.
	 */
	@Test
	public void bothFormsOfTheEyeAreOneDrop()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);
		run.loot().descending();

		assertEquals(1, run.loot().warnedOf(ItemID.EYE_OF_AYAK, "Eye of ayak"));
		assertEquals(RunLoot.NOT_RECORDED,
			run.loot().sawInPile(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)", 1));

		assertEquals(1, run.loot().getLanded().size());
		assertEquals(1, run.loot().held(ItemID.EYE_OF_AYAK));
		assertEquals(1, run.loot().held(ItemID.EYE_OF_AYAK_UNCHARGED));

		run.loot().recordLoot(ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)", 1);
		run.end(EndReason.FINISHED, at(90), -1);

		assertEquals(1, run.loot().claimed(ItemID.EYE_OF_AYAK));
		assertTrue(RunDetail.of(run).drops().get(0).kept);
	}

	@Test
	public void anUnnamedDropIsNotPlaced()
	{
		DelveRun run = new DelveRun(START, 1, false);
		run.complete(1, at(60), null);

		assertEquals(RunLoot.NOT_RECORDED, run.loot().sawInPile(ItemID.AVERNIC_TREADS, null, 1));
		assertTrue(run.loot().getLanded().isEmpty());
	}
}
