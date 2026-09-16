package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoginBoundTest
{
	private static final Instant NOW = Instant.parse("2026-09-16T20:01:54Z");

	/** The reading from the log: tick 1552, logged in 15:31.2 earlier. */
	@Test
	public void readsTheLoginBackFromTheTickCounter()
	{
		assertEquals(NOW.minusMillis(1552L * 600), LoginBound.of(NOW, 1552, null));
	}

	@Test
	public void anEarlierSeenLoginWins()
	{
		Instant seen = NOW.minus(Duration.ofHours(2));

		assertEquals(seen, LoginBound.of(NOW, 1552, seen));
	}

	/** A later seen login, as after a reconnect that may not have reset the counter, loses. */
	@Test
	public void aLaterSeenLoginLoses()
	{
		Instant seen = NOW.minus(Duration.ofMinutes(1));

		assertEquals(LoginBound.of(NOW, 1552, null), LoginBound.of(NOW, 1552, seen));
	}

	@Test
	public void withNoCounterOnlyTheSeenLoginIsLeft()
	{
		Instant seen = NOW.minus(Duration.ofMinutes(10));

		assertEquals(seen, LoginBound.of(NOW, -1, seen));
		assertNull(LoginBound.of(NOW, -1, null));
	}
}
