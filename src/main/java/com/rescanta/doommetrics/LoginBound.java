package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;

/**
 * The earliest a joined run can have started: its login. Read back from the game tick counter,
 * which only moves while logged in, or a login the plugin saw - whichever is earlier.
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
