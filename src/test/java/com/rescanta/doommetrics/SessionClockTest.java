package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionClockTest
{
	private static final Instant T0 = Instant.parse("2026-09-16T18:00:00Z");

	private static Instant at(int minutes)
	{
		return T0.plus(Duration.ofMinutes(minutes));
	}

	@Test
	public void nothingIsCountedBeforeTheFirstRun()
	{
		SessionClock clock = new SessionClock();
		clock.pause(at(5));
		clock.resume(at(10));

		assertFalse(clock.isStarted());
		assertNull(clock.elapsed(at(20)));
	}

	@Test
	public void runsOnBetweenRunsWhileLoggedIn()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));

		assertEquals(Duration.ofMinutes(90), clock.elapsed(at(90)));
	}

	@Test
	public void standsStillWhileLoggedOut()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.pause(at(30));

		assertEquals(Duration.ofMinutes(30), clock.elapsed(at(600)));

		clock.resume(at(600));

		assertEquals(Duration.ofMinutes(40), clock.elapsed(at(610)));
	}

	@Test
	public void addsUpSeveralLogouts()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.pause(at(10));
		clock.resume(at(20));
		clock.pause(at(30));
		clock.resume(at(50));

		assertEquals(Duration.ofMinutes(30), clock.elapsed(at(60)));
	}

	/** A second login screen event before the login must not move where the pause began. */
	@Test
	public void aRepeatedPauseKeepsTheFirst()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.pause(at(10));
		clock.pause(at(20));
		clock.resume(at(30));

		assertEquals(Duration.ofMinutes(20), clock.elapsed(at(40)));
	}

	/** A dropped connection that comes back left the character in the world, so its wait counts. */
	@Test
	public void aReconnectKeepsItsWait()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.connectionLost(at(10));
		clock.resume(at(11));

		assertEquals(Duration.ofMinutes(20), clock.elapsed(at(20)));

		// Settled by the reconnect, so a later logout stops the clock where it happens.
		clock.pause(at(30));

		assertEquals(Duration.ofMinutes(30), clock.elapsed(at(40)));
	}

	/** One that ends at the login screen was a logout from the moment the connection first went. */
	@Test
	public void aDropThatEndsAtTheLoginScreenStopsTheClockWhereItWent()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.connectionLost(at(10));
		clock.connectionLost(at(11));
		clock.pause(at(12));

		assertEquals(Duration.ofMinutes(10), clock.elapsed(at(30)));

		clock.resume(at(30));

		assertEquals(Duration.ofMinutes(20), clock.elapsed(at(40)));
	}

	@Test
	public void aSecondStartKeepsTheFirst()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.start(at(30));

		assertEquals(Duration.ofMinutes(40), clock.elapsed(at(40)));
	}

	@Test
	public void resetForgetsEverything()
	{
		SessionClock clock = new SessionClock();
		clock.start(at(0));
		clock.pause(at(10));
		clock.reset();

		assertFalse(clock.isStarted());
		assertNull(clock.elapsed(at(20)));

		clock.start(at(20));

		assertTrue(clock.isStarted());
		assertEquals(Duration.ofMinutes(5), clock.elapsed(at(25)));
	}
}
