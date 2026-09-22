package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/** How long a session has been going, counting only the time spent logged in. */
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
	 * Notes a dropped connection without stopping the clock; one that ends at the login screen was
	 * a logout from the start - see {@link #pause}.
	 */
	void connectionLost(Instant at)
	{
		if (droppedAt == null)
		{
			droppedAt = at;
		}
	}

	/** Stops the clock at a logout, or back at the drop that led to one. */
	void pause(Instant at)
	{
		Instant from = droppedAt != null ? droppedAt : at;
		droppedAt = null;

		if (startedAt != null && pausedAt == null)
		{
			pausedAt = from;
		}
	}

	/** Carries the clock on at a login. A pending drop was a reconnect, and its wait stays. */
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
