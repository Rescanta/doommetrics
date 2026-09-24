package com.rescanta.doommetrics;

/**
 * Decides whether a run held across a lost connection carries on, by polling the delve varp once
 * logged back in - a varp set to its old value raises no event. A zero is only believed after
 * {@link #GRACE_TICKS}, since the login varp flood arrives late.
 */
final class ResumeCheck
{
	/** How long a zero delve must hold before it is believed. */
	static final int GRACE_TICKS = 5;

	enum Verdict
	{
		/** No answer yet - the varplayers may still be on their way. */
		WAIT,

		/** The player is back in the delve. The run carries on. */
		INSIDE,

		/** The player came back outside the cave, the pile lost. The run died where we last saw it. */
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

		// The varp is 0 on delve 1 too, so a run with no clears is resumed; the abandon timer
		// settles it.
		if (deepestCleared == 0)
		{
			return Verdict.INSIDE;
		}

		return ++ticksWaited >= GRACE_TICKS ? Verdict.OUTSIDE : Verdict.WAIT;
	}
}
