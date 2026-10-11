package com.rescanta.doommetrics;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What was eaten or drunk on a tick, taken out of that tick's heal or restore. The brew's 16 and
 * the dose's 32 are the sizes in the logs at 99 hitpoints and prayer.
 */
public class ConsumablesTest
{
	private static final SpecEffect.Kind HEAL = SpecEffect.Kind.HEAL;
	private static final SpecEffect.Kind PRAYER = SpecEffect.Kind.PRAYER;

	private final Consumables consumables = new Consumables();

	@Test
	public void aRiseWithNothingTakenOnItsTickIsLeftAsItIs()
	{
		consumables.consumed("Saradomin brew", true, 99);

		assertEquals(25, consumables.without(HEAL, 25, 100, false, false));
		assertFalse(consumables.anyAt(100));
		assertTrue(consumables.anyAt(99));
	}

	/**
	 * A brew on its own heals 16, and nothing else explains the rise. Drunk again on the tick a
	 * Blood Sacrifice pays out, the 41 that rises is 25 of the godsword's.
	 */
	@Test
	public void whatAnItemGivesIsLearnedAndTakenOutOfASharedTick()
	{
		consumables.consumed("Saradomin brew", true, 100);
		assertEquals(0, consumables.without(HEAL, 16, 100, true, false));

		consumables.consumed("Saradomin brew", true, 200);
		assertEquals(25, consumables.without(HEAL, 41, 200, false, false));
	}

	/** Not seen on its own yet, it could be all of the rise, so none of the rise is counted. */
	@Test
	public void anItemNotSeenBeforeTakesTheWholeRise()
	{
		consumables.consumed("Saradomin brew", true, 100);

		assertEquals(0, consumables.without(HEAL, 41, 100, false, false));
	}

	/**
	 * A shark eaten four hitpoints short of full heals four. That is the least it gives, and taking
	 * four out of a shared tick would count sixteen of the shark as the spec's.
	 */
	@Test
	public void aRiseCutShortByTheLevelDoesNotSayWhatAnItemGives()
	{
		consumables.consumed("Shark", false, 100);
		assertEquals(0, consumables.without(HEAL, 4, 100, true, true));

		consumables.consumed("Shark", false, 200);
		assertEquals(0, consumables.without(HEAL, 45, 200, false, false));

		consumables.consumed("Shark", false, 300);
		assertEquals(0, consumables.without(HEAL, 20, 300, true, false));

		consumables.consumed("Shark", false, 400);
		assertEquals(25, consumables.without(HEAL, 45, 400, false, false));
	}

	/** Once known, a bigger sighting is still believed: the first may have met a hit taken. */
	@Test
	public void aKnownItemSeenToGiveMoreGivesMore()
	{
		consumables.consumed("Super restore", true, 100);
		assertEquals(0, consumables.without(PRAYER, 31, 100, true, false));

		consumables.consumed("Super restore", true, 200);
		assertEquals(0, consumables.without(PRAYER, 32, 200, true, false));

		consumables.consumed("Super restore", true, 300);
		assertEquals(18, consumables.without(PRAYER, 50, 300, false, false));
	}

	@Test
	public void foodDoesNotRestorePrayer()
	{
		consumables.consumed("Shark", false, 100);

		assertEquals(24, consumables.without(PRAYER, 24, 100, false, false));
	}

	/**
	 * A ranging potion heals nothing and restores nothing: seen once with room for both to rise,
	 * it no longer costs the heal it shares a tick with.
	 */
	@Test
	public void aDrinkSeenToGiveNoneIsNotTakenOut()
	{
		consumables.consumed("Ranging potion", true, 100);
		consumables.didNotRise(HEAL, 100);
		consumables.didNotRise(PRAYER, 100);

		consumables.consumed("Ranging potion", true, 200);
		assertEquals(25, consumables.without(HEAL, 25, 200, false, false));
		assertEquals(24, consumables.without(PRAYER, 24, 200, false, false));
	}

	/** A brew and a restore on one tick: each explains its own stat. */
	@Test
	public void twoThingsOnOneTickAreBothTakenOut()
	{
		consumables.consumed("Saradomin brew", true, 100);
		assertEquals(0, consumables.without(HEAL, 16, 100, true, false));
		consumables.didNotRise(PRAYER, 100);

		consumables.consumed("Super restore", true, 110);
		assertEquals(0, consumables.without(PRAYER, 32, 110, true, false));
		consumables.didNotRise(HEAL, 110);

		consumables.consumed("Saradomin brew", true, 200);
		consumables.consumed("Super restore", true, 200);
		assertEquals(9, consumables.without(HEAL, 25, 200, false, false));
		assertEquals(24, consumables.without(PRAYER, 56, 200, false, false));
	}

	/**
	 * Seen in game: a Super restore sipped as the holy water of a punish kill gave 4 hitpoints and
	 * 3 prayer. Read as the potion's, the 4 came out of two Blood Sacrifices later in the run.
	 */
	@Test
	public void aDrinkOfKnownSizeIsNotTaughtByARiseItSharesATickWith()
	{
		consumables.consumed("Super restore", true, 100, 0, 32);
		assertEquals(0, consumables.without(HEAL, 4, 100, true, false));
		assertEquals(0, consumables.without(PRAYER, 35, 100, true, false));

		consumables.consumed("Super restore", true, 200, 0, 32);
		assertEquals(25, consumables.without(HEAL, 25, 200, false, false));
		assertEquals(24, consumables.without(PRAYER, 56, 200, false, false));
	}

	/** Its size is taken out from the first sip: there is nothing to wait to learn. */
	@Test
	public void aDrinkOfKnownSizeIsTakenOutUnseen()
	{
		consumables.consumed("Prayer potion", true, 100, 0, 31);

		assertEquals(24, consumables.without(PRAYER, 55, 100, false, false));
		assertEquals(25, consumables.without(HEAL, 25, 100, false, false));
	}

	/** A sip with little missing restores less than its size, and what is left is nothing. */
	@Test
	public void aDrinkOfKnownSizeCutShortByTheLevelLeavesNothingOver()
	{
		consumables.consumed("Super restore", true, 100, 0, 32);

		assertEquals(0, consumables.without(PRAYER, 20, 100, false, true));
	}

	/** Beside one of known size, the other thing taken is still learned from what is left. */
	@Test
	public void anItemBesideADrinkOfKnownSizeIsLearnedFromTheRest()
	{
		consumables.consumed("Super restore", true, 100, 0, 32);
		consumables.consumed("Saradomin brew", true, 100);
		assertEquals(0, consumables.without(HEAL, 16, 100, true, false));

		consumables.consumed("Saradomin brew", true, 200);
		assertEquals(25, consumables.without(HEAL, 41, 200, false, false));
	}

	/**
	 * A brew heals past the trained level, so a sip near full hitpoints still gives its 16. Known
	 * ahead, that is what comes out of a Blood Sacrifice on its tick, first sip or not.
	 */
	@Test
	public void aBrewOfKnownSizeIsTakenOutOfAHealOnItsTick()
	{
		consumables.consumed("Saradomin brew", true, 100, 16, 0);

		assertEquals(25, consumables.without(HEAL, 41, 100, false, true));
		assertEquals(24, consumables.without(PRAYER, 24, 100, false, false));
	}

	/** An anglerfish is food: its 22 comes out of a heal, and it has nothing to do with prayer. */
	@Test
	public void foodOfKnownSizeDoesNotRestorePrayer()
	{
		consumables.consumed("Anglerfish", false, 100, 22, 0);

		assertEquals(3, consumables.without(HEAL, 25, 100, false, false));
		assertEquals(24, consumables.without(PRAYER, 24, 100, false, false));
	}

	/** What an item gives outlives a run; what was taken on a tick does not. */
	@Test
	public void aResetKeepsWhatWasLearned()
	{
		consumables.consumed("Saradomin brew", true, 100);
		assertEquals(0, consumables.without(HEAL, 16, 100, true, false));

		consumables.consumed("Saradomin brew", true, 200);
		consumables.reset();
		assertEquals(30, consumables.without(HEAL, 30, 200, false, false));

		consumables.consumed("Saradomin brew", true, 300);
		assertEquals(14, consumables.without(HEAL, 30, 300, false, false));
	}
}
