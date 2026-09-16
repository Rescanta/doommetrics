package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * How long a session has been going, counting only the time spent logged in.
 *
 * <p>A session now lasts until the client is closed or the player resets it, so it can easily span
 * a logout for dinner or a night with the client left open at the login screen. Wall clock from the
 * first run would charge all of that to the session; this leaves out every stretch spent logged
 * out, and keeps everything else - banking, restocking and walking back are the session too.
 */
final class SessionClock
{
	/** When the session's first run started, or null before it has one. */
	private Instant startedAt;

	/** When the client last went to the login screen, or null while logged in. */
	private Instant pausedAt;

	/** When the connection dropped, while the client is still trying to get it back, or null. */
	private Instant droppedAt;

	/** The logged out stretches already over, summed. */
	private Duration paused = Duration.ZERO;

	boolean isStarted()
	{
		return startedAt != null;
	}

	/** Starts the clock from {@code at}. Does nothing if it is already going. */
	void start(Instant at)
	{
		if (startedAt == null)
		{
			startedAt = at;
		}
	}

	/**
	 * Notes a dropped connection, without stopping the clock. Most drops reconnect with the
	 * character still in the world, and the wait is the session's as much as the run's. A drop that
	 * ends at the login screen instead was a logout from the moment the connection went - see
	 * {@link #pause}.
	 */
	void connectionLost(Instant at)
	{
		if (droppedAt == null)
		{
			droppedAt = at;
		}
	}

	/**
	 * Stops the clock at a logout, or back at the dropped connection that ended in one. Does
	 * nothing before the session has a run in it.
	 */
	void pause(Instant at)
	{
		Instant from = droppedAt != null ? droppedAt : at;
		droppedAt = null;

		if (startedAt != null && pausedAt == null)
		{
			pausedAt = from;
		}
	}

	/**
	 * Carries the clock on at a login, leaving out the time spent logged out. A drop still pending
	 * was a reconnect, and its wait stays on the clock.
	 */
	void resume(Instant at)
	{
		droppedAt = null;

		if (pausedAt == null)
		{
			return;
		}

		if (at.isAfter(pausedAt))
		{
			paused = paused.plus(Duration.between(pausedAt, at));
		}

		pausedAt = null;
	}

	/** The logged in time since the start, or null before the session has a run in it. */
	Duration elapsed(Instant now)
	{
		if (startedAt == null)
		{
			return null;
		}

		Instant until = pausedAt != null ? pausedAt : now;
		Duration elapsed = Duration.between(startedAt, until).minus(paused);
		return elapsed.isNegative() ? Duration.ZERO : elapsed;
	}

	void reset()
	{
		startedAt = null;
		pausedAt = null;
		droppedAt = null;
		paused = Duration.ZERO;
	}
}
