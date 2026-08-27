package com.rescanta.doommetrics;

import java.time.Duration;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every string here was taken verbatim from a client log of a real trip, colour templates and all.
 */
public class DelveMessageTest
{
	@Test
	public void readsAShallowDelveStarting()
	{
		DelveMessage message = DelveMessage.parse("@mes_hl_red@Delve level: 3</col>");

		assertNotNull(message);
		assertEquals(3, message.getLevel());
		assertFalse(message.isCleared());
		assertNull(message.getFight());
	}

	@Test
	public void readsADeepDelveStartingFromTheBracketedNumber()
	{
		DelveMessage message = DelveMessage.parse("@mes_hl_red@Delve level: 8+ (15)</col>");

		assertNotNull(message);
		assertEquals(15, message.getLevel());
		assertFalse(message.isCleared());
	}

	@Test
	public void readsAShallowDelveClearing()
	{
		DelveMessage message = DelveMessage.parse(
			"Delve level: 3 duration: @mes_hl_red@1:05.40</col>. Personal best: @mes_hl_red@0:37.80</col>");

		assertNotNull(message);
		assertEquals(3, message.getLevel());
		assertTrue(message.isCleared());
		assertEquals(Duration.ofMillis(65_400), message.getFight());
	}

	@Test
	public void readsADeepDelveClearing()
	{
		DelveMessage message = DelveMessage.parse(
			"Delve level: 8+ (15) duration: @mes_hl_red@1:30.60</col>. Personal best: @mes_hl_red@0:48.60</col>");

		assertNotNull(message);
		assertEquals(15, message.getLevel());
		assertEquals(Duration.ofMillis(90_600), message.getFight());
	}

	@Test
	public void readsAClearingThatSetANewPersonalBest()
	{
		DelveMessage message = DelveMessage.parse(
			"Delve level: 2 duration: @mes_hl_red@0:23.40</col> (new personal best)");

		assertNotNull(message);
		assertEquals(2, message.getLevel());
		assertEquals(Duration.ofMillis(23_400), message.getFight());
	}

	/** The delve 1-8 milestone has no colon after "level" and must not be read as a clear. */
	@Test
	public void ignoresTheOneToEightMilestone()
	{
		assertNull(DelveMessage.parse(
			"Delve level 1 - 8 duration: @mes_hl_red@9:35.40</col>. Personal best: @mes_hl_red@6:45.00</col>"));
	}

	@Test
	public void ignoresOtherDoomChatter()
	{
		assertNull(DelveMessage.parse("Deep delves completed: @mes_hl_red@6,694"));
		assertNull(DelveMessage.parse("Oh dear, you are dead!"));
		assertNull(DelveMessage.parse("<colHIGHLIGHT>[Doom] <colNORMAL>Cleared delve 15 | 22:47 | 3.9/hr"));
		assertNull(DelveMessage.parse(null));
	}
}
