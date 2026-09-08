package com.rescanta.doommetrics;

/**
 * Takes the points that come back on their own out of a rise in prayer or hitpoints, so what is
 * left is what the player's gear gave back.
 *
 * <p>Two things drip. The prayer regeneration potion returns a point every twelfth tick for eight
 * minutes a dose, and hitpoints come back a point a minute on their own - twice that under Rapid
 * Heal or a Hitpoints cape, twice that again wearing a regen bracelet. Both arrive through the same
 * signal a restore or a heal does, the level simply going up, and both are indistinguishable from
 * one by anything about the number itself. A drip can also land on the very tick a heal does, and
 * then the game reports the two as one number.
 *
 * <p>What they cannot do is go past the level the player trained. Only the Eldritch staff pushes a
 * prayer book past it, and only a brew pushes hitpoints past it, so a rise that started at or above
 * the natural level has no drip in it whatever: a hundred and nineteen prayer to a hundred and
 * twenty is a spec, and nothing else it could be.
 *
 * <p>Under that level, when they arrive settles it. Both drips run on a fixed cadence, and the
 * cadence is exact: across one trip's sixty potion drips every one landed on the same tick of
 * twelve, and every hitpoint that came back on its own landed on the same tick of a hundred across
 * nine hundred ticks of a fight that was taking damage and healing throughout. So a rise landing on
 * the grid is credited a point short - ninety-eight to a hundred is the potion's point and the
 * staff's, and the staff is credited with the one - and a lone point on the grid is credited as
 * nothing at all.
 *
 * <p>The grid is only ever laid on a point that nothing else can explain. That is the part worth
 * being careful about: a single hitpoint could be a blood barrage on a small hit, and a grid laid
 * on one of those would dock points off real heals for as long as it stood. A point no window can
 * explain is not one of those, and nothing is lost by taking it, because an effect nothing explains
 * is dropped anyway - see {@link CombatTracker}. So the cadence is learned from the drips that fell
 * in the open, and then applied to the ones that fell inside a window.
 *
 * <p>Two things are given up, both a single point against the twenty a spec gives back, and both
 * short rather than over. A real one-point heal that lands exactly on the grid is counted as
 * nothing. And where a rise crosses the natural level, the order the game applied the two in is not
 * knowable from here - if the drip went first its point really is in the rise, and if the gear went
 * first the drip had nowhere left to land - so this assumes the former and counts a point short of
 * the latter.
 *
 * <p>No RuneLite types here, for the same reason there are none in {@link CombatTracker}: what is
 * equipped and which potion is up are things to look up, and what they mean is a thing to decide.
 */
final class Regeneration
{
	/** The tick a drip was last seen on, which lays the grid, or -1 while nothing has laid it. */
	private int lastDrip = -1;

	/**
	 * How much of a rise the player's gear gave back.
	 *
	 * @param from    the boosted level before the rise
	 * @param to      the boosted level after it, always the greater of the two
	 * @param natural the level the player trained, which is as far as a drip can reach
	 * @param tick    the tick the level went up on
	 * @param period  how often this drips, in ticks, or 0 when nothing is dripping at all
	 * @param spare   whether nothing else can explain the rise, which is what makes a lone point
	 *                safe to read as a drip and to lay the grid on
	 * @return the points to credit, which is none when the rise was the drip itself
	 */
	int without(int from, int to, int natural, int tick, int period, boolean spare)
	{
		int rise = to - from;

		// Nothing to take out: either nothing is dripping, or the level was already as high as a
		// drip could have carried it.
		if (period <= 0 || from >= natural)
		{
			return rise;
		}

		if (rise == 1 && spare)
		{
			lastDrip = tick;
			return 0;
		}

		if (lastDrip < 0)
		{
			// Nothing has laid the grid yet, so nothing is due. The first drip to fall in the open
			// lays it, which on either cadence is a matter of a minute at the outside.
			return rise;
		}

		if (tick < lastDrip)
		{
			// The client's tick counter has been round a login, so the grid was laid against one
			// that no longer runs and is dropped rather than measured from.
			lastDrip = -1;
			return rise;
		}

		if ((tick - lastDrip) % period != 0)
		{
			return rise;
		}

		lastDrip = tick;
		return rise - 1;
	}

	/** Forgets the grid. The tick counter it was laid against does not survive a login. */
	void reset()
	{
		lastDrip = -1;
	}
}
