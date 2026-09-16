package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * The earliest a run we joined part way through can have started: the login it happened in.
 *
 * <p>The client does not say when it logged in, and a login the plugin saw for itself is only
 * there if the plugin was on at the time. The game tick counter fills the gap. It moves once per
 * server tick while logged in, so ticks times the tick length reads back to the login from any
 * moment - checked against a debug log, where it landed two seconds before the login screen let
 * go. It does not start over when you log out and back in, only stops while you are out, so after
 * a relog it reads back to a moment before the first login, less the time spent logged out. That
 * is still no later than the run's real start, which is all a bound has to be.
 *
 * <p>The earlier of it and a login actually seen is taken, which covers a counter that did start
 * over somehow.
 */
final class LoginBound
{
	private LoginBound()
	{
	}

	/**
	 * @param now       the moment the counter was read
	 * @param tickCount the game tick counter, or a negative number when not logged in
	 * @param seenLogin a login the plugin saw happen, or null for none
	 * @return the earlier of the two bounds, or null when there is neither
	 */
	static Instant of(Instant now, int tickCount, Instant seenLogin)
	{
		Instant fromTicks = null;

		if (tickCount >= 0)
		{
			fromTicks = now.minus(Duration.ofMillis((long) tickCount * DoomFormat.TICK_MILLIS));
		}

		if (fromTicks == null)
		{
			return seenLogin;
		}

		return seenLogin != null && seenLogin.isBefore(fromTicks) ? seenLogin : fromTicks;
	}
}
