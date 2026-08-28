package com.rescanta.doommetrics;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * The connection-drop path, which is the one ending no in-game session can be made to produce on
 * purpose without pulling the plug. Everything the plugin decides on the way back into the world
 * is decided here, so it can be pinned down without a client.
 */
public class ResumeCheckTest
{
	/** A delve the game still has us in is the whole point: the run carries on. */
	@Test
	public void aDelveStillInProgressResumesTheRun()
	{
		assertEquals(ResumeCheck.Verdict.INSIDE, new ResumeCheck().onTick(12, 11));
	}

	/** Answered on the first look, so a reconnect costs the run no time at all. */
	@Test
	public void beingBackInsideIsNotWaitedOn()
	{
		ResumeCheck check = new ResumeCheck();

		assertEquals(ResumeCheck.Verdict.INSIDE, check.onTick(3, 2));
		assertEquals(ResumeCheck.Verdict.INSIDE, check.onTick(3, 2));
	}

	/**
	 * A zero read before the varplayers land looks exactly like being outside, so it is not taken
	 * at face value until it has held.
	 */
	@Test
	public void aZeroDelveIsNotBelievedStraightAway()
	{
		ResumeCheck check = new ResumeCheck();

		for (int tick = 1; tick < ResumeCheck.GRACE_TICKS; tick++)
		{
			assertEquals(ResumeCheck.Verdict.WAIT, check.onTick(0, 9));
		}

		assertEquals(ResumeCheck.Verdict.OUTSIDE, check.onTick(0, 9));
	}

	/** The varplayer arriving inside the grace window settles it, and the wait stops there. */
	@Test
	public void theVarplayerArrivingLateStillResumesTheRun()
	{
		ResumeCheck check = new ResumeCheck();

		assertEquals(ResumeCheck.Verdict.WAIT, check.onTick(0, 9));
		assertEquals(ResumeCheck.Verdict.WAIT, check.onTick(0, 9));
		assertEquals(ResumeCheck.Verdict.INSIDE, check.onTick(14, 9));
	}

	/**
	 * Delve 1 never sets the varplayer, so a run that has banked nothing reads the same inside the
	 * cave as out of it. It is resumed rather than guessed at - the abandon timer settles it a
	 * minute later on evidence, by which point either the boss is there or it is not.
	 */
	@Test
	public void aRunThatHasBankedNothingIsAlwaysResumed()
	{
		ResumeCheck check = new ResumeCheck();

		for (int tick = 0; tick <= ResumeCheck.GRACE_TICKS * 2; tick++)
		{
			assertEquals(ResumeCheck.Verdict.INSIDE, check.onTick(0, 0));
		}
	}
}
