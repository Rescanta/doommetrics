package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ChatMessageType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the plugin says in chat, and when - read as text, since the chatbox is not something a test
 * can open. {@link PreviewScene#chat} draws the same lines for looking at.
 */
public class ChatAnnouncementTest
{
	private static final Instant START = Instant.parse("2026-09-10T20:00:00Z");

	@Test
	public void theIntervalAnnouncesItsMultiplesOncePastTheShallowDelves()
	{
		assertTrue(ChatAnnouncement.isDue(10, 5, 0));
		assertTrue(ChatAnnouncement.isDue(15, 5, 0));
		assertFalse(ChatAnnouncement.isDue(11, 5, 0));

		// A multiple of the interval, but shallow.
		assertFalse(ChatAnnouncement.isDue(5, 5, 0));
		assertTrue(ChatAnnouncement.isDue(DelveRun.DEEP_DELVE_LEVEL, 4, 0));
		assertFalse(ChatAnnouncement.isDue(4, 4, 0));

		assertFalse("0 switches the interval off", ChatAnnouncement.isDue(10, 0, 0));
	}

	@Test
	public void theTargetIsAnnouncedWhateverTheIntervalSays()
	{
		assertTrue(ChatAnnouncement.isDue(52, 5, 52));
		assertTrue(ChatAnnouncement.isDue(52, 0, 52));
		assertTrue("even shallow", ChatAnnouncement.isDue(3, 5, 3));

		// Exactly the target: the delve after it is the interval's business again.
		assertFalse(ChatAnnouncement.isDue(53, 5, 52));
	}

	@Test
	public void aDelveLineGivesTheRunTimeAndThePace()
	{
		assertEquals("Doom delve 10 cleared, run time: 10:00, deep pace: 60.0/hr.",
			ChatAnnouncement.delveCleared(run(10), 10, 0, PaceMode.DEEP_AVERAGE).text());
	}

	@Test
	public void theTargetDelveIsSaidToHaveBeenReached()
	{
		assertEquals("Doom target delve 10 reached! Run time: 10:00, deep pace: 60.0/hr.",
			ChatAnnouncement.delveCleared(run(10), 10, 10, PaceMode.DEEP_AVERAGE).text());
	}

	@Test
	public void thePaceIsNamedAfterTheSettingThatProducedIt()
	{
		// Delves 8, 9 and 10 banked in ten minutes.
		assertEquals("Doom delve 10 cleared, run time: 10:00, run pace: 18.0/hr.",
			ChatAnnouncement.delveCleared(run(10), 10, 0, PaceMode.RUN_THROUGHPUT).text());
	}

	@Test
	public void aPaceWithNothingToAverageIsADash()
	{
		assertEquals("Doom target delve 3 reached! Run time: 3:00, deep pace: -.",
			ChatAnnouncement.delveCleared(run(3), 3, 3, PaceMode.DEEP_AVERAGE).text());
	}

	@Test
	public void aDeathIsMarkedBesideHowFarTheRunGot()
	{
		assertEquals("Doom run over (died): cleared delve 10 in 10:00, deep pace: 60.0/hr.",
			ChatAnnouncement.runEnded(run(10), EndReason.DIED, PaceMode.DEEP_AVERAGE).text());
	}

	@Test
	public void anyOtherEndingSaysHowFarTheRunGot()
	{
		assertEquals("Doom run over: cleared delve 10 in 10:00, deep pace: 60.0/hr.",
			ChatAnnouncement.runEnded(run(10), EndReason.FINISHED, PaceMode.DEEP_AVERAGE).text());
	}

	/** Deep pace is how fast more deep delves could be added, which an ended run will not do. */
	@Test
	public void anEndedRunGivesRunPaceWhateverTheSetting()
	{
		DelveRun run = run(10);
		run.end(EndReason.DIED, START.plusSeconds(630), 11);

		assertEquals("Doom run over (died): cleared delve 10 in 10:00, run pace: 18.0/hr.",
			ChatAnnouncement.runEnded(run, EndReason.DIED, PaceMode.DEEP_AVERAGE).text());
	}

	/** The red is for the figures, all of them and nothing else. */
	@Test
	public void onlyTheFiguresAreHighlighted()
	{
		List<String> figures = new ArrayList<>();

		for (ChatAnnouncement.Part part
			: ChatAnnouncement.delveCleared(run(10), 10, 0, PaceMode.DEEP_AVERAGE).parts())
		{
			if (part.figure)
			{
				figures.add(part.text);
			}
			else
			{
				assertFalse("a figure left in the words: " + part.text,
					part.text.matches(".*\\d.*"));
			}
		}

		assertEquals(List.of("10", "10:00", "60.0/hr"), figures);
	}

	/** As RuneLite is handed it, to swap each tag for the player's own chat colour. */
	@Test
	public void theSentLineTagsEachFigureForTheHighlight()
	{
		assertEquals("<colNORMAL>Doom run over: cleared delve <colHIGHLIGHT>10<colNORMAL> in "
				+ "<colHIGHLIGHT>10:00<colNORMAL>, deep pace: <colHIGHLIGHT>60.0/hr<colNORMAL>.",
			ChatAnnouncement.runEnded(run(10), EndReason.FINISHED, PaceMode.DEEP_AVERAGE)
				.formatted());
	}

	/**
	 * RuneLite swaps the tags for the game message colours on a {@code CONSOLE} line only. Sent as
	 * a {@code GAMEMESSAGE} the tags are dropped and the figures are as white as the words.
	 */
	@Test
	public void theLineIsSentWhereRuneLiteColoursIt()
	{
		assertEquals(ChatMessageType.CONSOLE, ChatAnnouncement.TYPE);
	}

	/** A run cleared down to {@code reached}, a minute a delve. */
	private static DelveRun run(int reached)
	{
		DelveRun run = new DelveRun(START, 1, false);

		for (int level = 1; level <= reached; level++)
		{
			run.complete(level, START.plusSeconds(60L * level), Duration.ofSeconds(55));
		}

		return run;
	}
}
