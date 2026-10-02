package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** How long a session has been going, counting only the time spent logged in and not away. */
final class SessionClock
{
	/** A stretch the session does not count. */
	private static final class Stretch
	{
		final Instant from;
		final Instant to;

		Stretch(Instant from, Instant to)
		{
			this.from = from;
			this.to = to;
		}
	}

	/** When the session's first run started, or null before it has one. */
	private Instant startedAt;

	/** When the client last went to the login screen, or null while logged in. */
	private Instant pausedAt;

	/** When the connection dropped, while the client is still trying to get it back, or null. */
	private Instant droppedAt;

	/**
	 * The logged out stretches already over and the time away, merged where they overlap: a wait
	 * cut from the run can run through a logout, and must not be taken off twice.
	 */
	private final List<Stretch> leftOut = new ArrayList<>();

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

		leaveOut(pausedAt, at);
		pausedAt = null;
	}

	/** Leaves time away during a run out of the session. */
	void exclude(Instant from, Instant to)
	{
		if (startedAt != null)
		{
			leaveOut(from, to);
		}
	}

	private void leaveOut(Instant from, Instant to)
	{
		if (!from.isBefore(to))
		{
			return;
		}

		for (Iterator<Stretch> it = leftOut.iterator(); it.hasNext(); )
		{
			Stretch stretch = it.next();

			if (!stretch.to.isBefore(from) && !to.isBefore(stretch.from))
			{
				from = stretch.from.isBefore(from) ? stretch.from : from;
				to = stretch.to.isAfter(to) ? stretch.to : to;
				it.remove();
			}
		}

		leftOut.add(new Stretch(from, to));
	}

	/** {@link #elapsed(Instant, Instant)} with no time away still going. */
	Duration elapsed(Instant now)
	{
		return elapsed(now, null);
	}

	/**
	 * The logged in time since the start, or null before the session has a run in it.
	 *
	 * @param awayFrom where time away still going started, or null
	 */
	Duration elapsed(Instant now, Instant awayFrom)
	{
		if (startedAt == null)
		{
			return null;
		}

		// A logout and time away still going both run to now, so whichever began first ends it.
		Instant until = now;
		until = pausedAt != null && pausedAt.isBefore(until) ? pausedAt : until;
		until = awayFrom != null && awayFrom.isBefore(until) ? awayFrom : until;

		Duration elapsed = Duration.between(startedAt, until);

		for (Stretch stretch : leftOut)
		{
			Instant from = stretch.from.isAfter(startedAt) ? stretch.from : startedAt;
			Instant to = stretch.to.isBefore(until) ? stretch.to : until;

			if (from.isBefore(to))
			{
				elapsed = elapsed.minus(Duration.between(from, to));
			}
		}

		return elapsed.isNegative() ? Duration.ZERO : elapsed;
	}

	void reset()
	{
		startedAt = null;
		pausedAt = null;
		droppedAt = null;
		leftOut.clear();
	}
}
