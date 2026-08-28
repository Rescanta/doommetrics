package com.rescanta.doommetrics;

/**
 * Decides what became of a run that was still going when the client left the world.
 *
 * <p>Leaving the world is not the same as leaving the cave. A hop or a logout does put you back
 * outside the entrance, but a connection that drops and comes back can land you in the delve you
 * were already in, and a run that carries on is a run worth carrying on measuring. Nothing about
 * the disconnection itself says which happened, so the answer is only available once we are logged
 * in again and can look at where the game has put us.
 *
 * <p>That look is a poll rather than an event. Across a reconnect the delve varplayer is very
 * often set to the value it already held, and a varplayer set to what it already was raises no
 * change event - so the case we most want to hear about, "still in delve 12", is exactly the case
 * that would never fire one. Reading the value each tick sees it either way.
 *
 * <p>The poll has to tolerate arriving before the answer does. The varplayers land in a flood
 * shortly after login, and a delve read before that flood is indistinguishable from being outside,
 * so a zero is only believed once it has held for {@link #GRACE_TICKS}.
 */
final class ResumeCheck
{
	/**
	 * How long a zero delve has to hold before it is taken at face value. The varplayer flood
	 * lands within a tick or two of logging in, so three seconds is several times longer than it
	 * takes; the cost of waiting too long is a run that ends a moment late, and the cost of not
	 * waiting long enough is a run cut short while the player is still in it.
	 */
	static final int GRACE_TICKS = 5;

	enum Verdict
	{
		/** No answer yet - the varplayers may still be on their way. */
		WAIT,

		/** The player is back in the delve. The run carries on. */
		INSIDE,

		/** The player came back outside the cave. The run ended where we last saw it. */
		OUTSIDE
	}

	private int ticksWaited;

	/**
	 * @param currentDelve   DOM_CURRENT_LEVEL_TEMP as it reads now
	 * @param deepestCleared the deepest delve the run had banked before the connection went
	 */
	Verdict onTick(int currentDelve, int deepestCleared)
	{
		if (currentDelve > 0)
		{
			return Verdict.INSIDE;
		}

		// The varplayer counts the delves you descended to, so it sits at zero for the whole of
		// delve 1 and cannot tell "back in delve 1" from "back outside". A run that never banked
		// anything is resumed on the benefit of the doubt: if the player really is outside, the
		// boss will not be there either, and the abandon timer gives the same answer a minute
		// later without this having to guess.
		if (deepestCleared == 0)
		{
			return Verdict.INSIDE;
		}

		return ++ticksWaited >= GRACE_TICKS ? Verdict.OUTSIDE : Verdict.WAIT;
	}
}
