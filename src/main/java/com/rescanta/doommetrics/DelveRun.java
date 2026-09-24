package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One trip into the Doom of Mokhaiotl, from entering the cave until the player leaves or dies.
 * A delve's segment runs from the previous clear, so segments sum to the run time. Wall clock.
 */
class DelveRun
{
	static final int DEEP_DELVE_LEVEL = 8;

	/** Delve 8 has different boss health, so the deep average starts at 9. */
	static final int PACE_AVERAGE_FROM_LEVEL = 9;

	/** Shared and read only. */
	private static final CombatTotals EMPTY_COMBAT = new CombatTotals();

	static final class Split
	{
		final int level;
		final Instant completedAt;

		/** Wall clock from the previous clear, or the run start, up to this clear. */
		final Duration segment;

		/** The fight length the game reported, or null if we never saw it. */
		final Duration fight;

		/** Whether {@link #segment} was measured, rather than starting wherever we picked it up. */
		final boolean timed;

		Split(int level, Instant completedAt, Duration segment, Duration fight, boolean timed)
		{
			this.level = level;
			this.completedAt = completedAt;
			this.segment = segment;
			this.fight = fight;
			this.timed = timed;
		}
	}

	private final List<Split> splits = new ArrayList<>();


	/** From a clear until the game announces the next delve. */
	private boolean betweenDelves;

	/** When each delve after the first was announced, for {@link #fullTime}. */
	private final Map<Integer, Instant> delveStarts = new HashMap<>();

	/**
	 * Bumped when something is counted onto a delve already killed - see {@link RunDetail#keyFor}.
	 */
	private int bankedCombatChanges;

	private final CombatTotals combat = new CombatTotals();

	/** The same tally by the delve it was earned on: its kill and the wait after it. */
	private final Map<Integer, CombatTotals> combatByDelve = new LinkedHashMap<>();

	private final RecentGains recent = new RecentGains();

	private Instant startedAt;

	/** Already underway when we started watching, so times are incomplete. */
	private final boolean partial;

	/** No later than the real start of a joined run, for personal bests. Null when exact. */
	private final Instant pbAnchor;

	/** Whether the next clear's delve was watched from its start, so its segment is measured. */
	private boolean nextClearTimed;

	private Instant lastClearedAt;

	/** Where the next clear's segment starts: the last clear, or where the run was picked up. */
	private Instant segmentStart;
	private int currentLevel;
	private EndReason endReason;
	private Instant endedAt;
	private int diedOnLevel = -1;

	DelveRun(Instant startedAt, int currentLevel, boolean partial)
	{
		this(startedAt, currentLevel, partial, null);
	}

	DelveRun(Instant startedAt, int currentLevel, boolean partial, Instant pbAnchor)
	{
		this.startedAt = startedAt;
		this.lastClearedAt = startedAt;
		this.segmentStart = startedAt;
		this.currentLevel = currentLevel;
		this.partial = partial;
		this.pbAnchor = pbAnchor;
		this.nextClearTimed = !partial;
	}

	/** The game announced the delve we have just dropped into. */
	void enterLevel(int level, Instant at)
	{
		// A run picked up between delves has now seen one start.
		if (!nextClearTimed && level > currentLevel)
		{
			watchedFromDelveStart(at);
		}

		currentLevel = level;
		betweenDelves = false;
		delveStarts.putIfAbsent(level, at);
	}

	/**
	 * A picked up run saw its delve start, so its next clear is measured. One already measuring
	 * from its last clear keeps that start.
	 */
	void watchedFromDelveStart(Instant at)
	{
		if (nextClearTimed)
		{
			return;
		}

		if (splits.isEmpty())
		{
			startedAt = at;
			lastClearedAt = at;
		}

		segmentStart = at;
		nextClearTimed = true;
	}

	/**
	 * Whether the player on {@code level} is still in this run, given the delves cleared since it
	 * was last watched. Between delves the level reads one short, as the varp does - but only then:
	 * one short at any other time is a later trip that happens to line up.
	 *
	 * @param betweenDelves whether the game shows a clear not yet followed by the next delve
	 */
	boolean continuesAt(int level, int clearsSince, boolean betweenDelves)
	{
		int expected = currentLevel + clearsSince;
		return clearsSince >= 0 && (level == expected || (betweenDelves && level == expected - 1));
	}

	/**
	 * Carries the run on at {@code level}. After delves nobody watched, the next clear's segment
	 * starts somewhere in them, so it is left out of the rates; with none missed, the run carries
	 * on exactly as it was.
	 */
	void resumeOn(int level, Instant at, int clearsSince)
	{
		if (clearsSince == 0)
		{
			// The delve the level reads was started while nobody watched.
			if (level == currentLevel)
			{
				betweenDelves = false;
			}

			return;
		}

		currentLevel = level;
		betweenDelves = false;
		nextClearTimed = false;
		segmentStart = at;
		delveStarts.putIfAbsent(level, at);
	}

	/**
	 * Moves the run onto the delve the game says is under way, when it was picked up while the
	 * varp still read the delve before and so missed that delve's start. Timing is left alone.
	 *
	 * @return true if the run was moved
	 */
	boolean caughtUpTo(int level)
	{
		if (level < currentLevel || (level == currentLevel && !betweenDelves))
		{
			return false;
		}

		currentLevel = level;
		betweenDelves = false;
		return true;
	}

	/**
	 * Moves the start of a run with no clears onto the game's own delve start, a couple of seconds
	 * after the chat line.
	 *
	 * @return true if the run was moved
	 */
	boolean reanchorStart(Instant at)
	{
		if (!splits.isEmpty() || at.isBefore(startedAt))
		{
			return false;
		}

		startedAt = at;
		lastClearedAt = at;
		segmentStart = at;
		return true;
	}

	Split complete(int level, Instant at, Duration fight)
	{
		Split split = new Split(level, at, Duration.between(segmentStart, at), fight, nextClearTimed);
		splits.add(split);
		lastClearedAt = at;
		segmentStart = at;
		nextClearTimed = true;
		currentLevel = level + 1;
		betweenDelves = true;
		return split;
	}

	/** Whether the last clear's segment was measured, and so can be charged to a rate. */
	boolean lastClearTimed()
	{
		return !splits.isEmpty() && splits.get(splits.size() - 1).timed;
	}

	/** The last clear's segment in ticks, rounded so a run's clears sum to its total. */
	long lastClearTicks()
	{
		if (splits.isEmpty())
		{
			return 0;
		}

		Duration through = clearedElapsed();
		Duration before = through.minus(splits.get(splits.size() - 1).segment);
		return DoomFormat.toTicks(through) - DoomFormat.toTicks(before);
	}

	/** The delve something seen now belongs to: the one just cleared, or the one being fought. */
	int creditLevel()
	{
		return betweenDelves ? lastLevel() : currentLevel;
	}

	boolean isBetweenDelves()
	{
		return betweenDelves;
	}

	int bankedCombatChanges()
	{
		return bankedCombatChanges;
	}

	/** Credits a heal, prayer restore or hit to this trip and to the delve. */
	void recordCombat(CombatMetric metric, long amount, Instant at)
	{
		// Checked here so nothing leaves an empty tally on a delve.
		if (amount <= 0)
		{
			return;
		}

		combat.add(metric, amount);
		combatByDelve.computeIfAbsent(creditLevel(), level -> new CombatTotals()).add(metric, amount);
		recent.add(metric, amount, at);

		if (betweenDelves)
		{
			bankedCombatChanges++;
		}
	}

	long recentGain(CombatMetric metric, Instant now)
	{
		return recent.get(metric, now);
	}

	CombatTotals getCombat()
	{
		return combat;
	}

	/** What was earned on one delve; never null. */
	CombatTotals combatOn(int level)
	{
		CombatTotals totals = combatByDelve.get(level);
		return totals == null ? EMPTY_COMBAT : totals;
	}

	Set<Integer> combatLevels()
	{
		return Collections.unmodifiableSet(combatByDelve.keySet());
	}

	void end(EndReason reason, Instant at, int diedOnLevel)
	{
		this.endReason = reason;
		this.endedAt = at;
		this.diedOnLevel = diedOnLevel;
	}

	boolean isPartial()
	{
		return partial;
	}

	boolean isFinished()
	{
		return endReason != null;
	}

	EndReason getEndReason()
	{
		return endReason;
	}

	Instant getEndedAt()
	{
		return endedAt;
	}

	int getDiedOnLevel()
	{
		return diedOnLevel;
	}

	/** The deepest delve cleared, or 0 if none has been. */
	int lastLevel()
	{
		return splits.isEmpty() ? 0 : splits.get(splits.size() - 1).level;
	}

	int currentLevel()
	{
		return currentLevel;
	}

	/** From the run start to the last clear - what every total and pace is built on. */
	Duration clearedElapsed()
	{
		return Duration.between(startedAt, lastClearedAt);
	}

	/**
	 * The span a milestone personal best is measured over. A joined run is measured from its
	 * login anchor, which can only make it too long.
	 *
	 * @return the span, or null for a joined run with no anchor
	 */
	Duration pbElapsed()
	{
		if (partial && pbAnchor == null)
		{
			return null;
		}

		Instant from = pbAnchor == null || startedAt.isBefore(pbAnchor) ? startedAt : pbAnchor;
		return Duration.between(from, lastClearedAt);
	}

	Duration liveElapsed(Instant now)
	{
		return Duration.between(startedAt, now);
	}

	/** Live while the run is going, frozen on {@link #clearedElapsed} once it is over. */
	Duration displayElapsed(Instant now)
	{
		return isFinished() ? clearedElapsed() : liveElapsed(now);
	}

	private static int deepIn(List<Split> cleared)
	{
		int deep = 0;

		for (Split split : cleared)
		{
			if (split.level >= DEEP_DELVE_LEVEL)
			{
				deep++;
			}
		}

		return deep;
	}

	/** Every clear whose segment was measured. */
	private List<Split> timedSplits()
	{
		List<Split> timed = new ArrayList<>();

		for (Split split : splits)
		{
			if (split.timed)
			{
				timed.add(split);
			}
		}

		return timed;
	}

	/** Deep delves per hour of measured run time, shallow delves counting against you. */
	Double fullPace()
	{
		List<Split> timed = timedSplits();
		int deep = deepIn(timed);
		long millis = 0;

		for (Split split : timed)
		{
			millis += split.segment.toMillis();
		}

		double seconds = millis / 1000.0;

		if (deep == 0 || seconds <= 0)
		{
			return null;
		}

		return deep * 3600.0 / seconds;
	}

	/** The mean segment of delves 9 and deeper, or null until one has been cleared. */
	Duration meanDeepSegment()
	{
		long count = 0;
		long millis = 0;

		for (Split split : timedSplits())
		{
			if (split.level >= PACE_AVERAGE_FROM_LEVEL)
			{
				count++;
				millis += split.segment.toMillis();
			}
		}

		return count == 0 || millis <= 0 ? null : Duration.ofMillis(millis / count);
	}

	Double deepPace()
	{
		Duration mean = meanDeepSegment();
		return mean == null ? null : 3600.0 / (mean.toMillis() / 1000.0);
	}

	boolean hasReached(int target)
	{
		return target > 0 && lastLevel() >= target;
	}

	/**
	 * Time left to clear {@code target} at the deep average, counting down through the current
	 * delve but floored so an overrunning delve stalls it. Null when there is nothing to predict.
	 */
	Duration untilTarget(int target, Instant now)
	{
		if (target <= 0 || isFinished() || hasReached(target))
		{
			return null;
		}

		Duration mean = meanDeepSegment();

		if (mean == null)
		{
			return null;
		}

		long remaining = target - lastLevel();
		long millis = remaining * mean.toMillis() - Duration.between(segmentStart, now).toMillis();
		return Duration.ofMillis(Math.max(millis, (remaining - 1) * mean.toMillis()));
	}

	/** Start to clearing {@code target}: the real time once reached, else elapsed + prediction. */
	Duration runToTarget(int target, Instant now)
	{
		if (hasReached(target))
		{
			for (Split split : splits)
			{
				if (split.level == target)
				{
					return Duration.between(startedAt, split.completedAt);
				}
			}

			return null;
		}

		Duration remaining = untilTarget(target, now);
		return remaining == null ? null : liveElapsed(now).plus(remaining);
	}

	/** A finished run is read by full pace. */
	PaceMode paceMode(PaceMode configured)
	{
		return isFinished() ? PaceMode.RUN_THROUGHPUT : configured;
	}

	Double pace(PaceMode mode)
	{
		return mode == PaceMode.RUN_THROUGHPUT ? fullPace() : deepPace();
	}

	List<Split> getSplits()
	{
		return Collections.unmodifiableList(splits);
	}

	/** A delve on the detail window's timeline, cleared while watched or not. */
	static final class DelveTime
	{
		final int level;

		/** From this delve starting to the next one starting. */
		final Duration fullTime;

		/** The clear, or null for a delve cleared while the plugin was not watching. */
		final Split split;

		/** Whether {@link #fullTime} is an even share of a stretch nobody watched. */
		final boolean estimated;

		private DelveTime(int level, Duration fullTime, Split split, boolean estimated)
		{
			this.level = level;
			this.fullTime = fullTime;
			this.split = split;
			this.estimated = estimated;
		}
	}

	/**
	 * Every delve from the first clear to the last, with the time the detail window draws it
	 * taking: from its start to the next one's, so its kill and the wait after it. Delves skipped
	 * between two clears went by unwatched, and share the stretch from the clear before them evenly
	 * - with the clear after them too, when its own start was not seen.
	 */
	List<DelveTime> timeline()
	{
		List<DelveTime> timeline = new ArrayList<>();

		for (int i = 0; i < splits.size(); i++)
		{
			Split split = splits.get(i);
			int firstUnwatched = i == 0 ? split.level : splits.get(i - 1).level + 1;

			if (firstUnwatched >= split.level)
			{
				timeline.add(new DelveTime(split.level, Duration.between(startOf(i), endOf(i)),
					split, false));
				continue;
			}

			// A timed clear saw its delve start, which is where the unwatched stretch ends.
			Instant from = splits.get(i - 1).completedAt;
			Instant to = split.timed ? startOf(i) : endOf(i);
			int shared = split.level - firstUnwatched + (split.timed ? 0 : 1);
			Duration share = Duration.between(from, to).dividedBy(shared);

			for (int level = firstUnwatched; level < split.level; level++)
			{
				timeline.add(new DelveTime(level, share, null, true));
			}

			timeline.add(split.timed
				? new DelveTime(split.level, Duration.between(startOf(i), endOf(i)), split, false)
				: new DelveTime(split.level, share, split, true));
		}

		return timeline;
	}

	private Instant startOf(int index)
	{
		Split split = splits.get(index);
		return index == 0
			? startedAt
			: delveStarts.getOrDefault(split.level, splits.get(index - 1).completedAt);
	}

	/**
	 * The next delve's start; a delve still in its wait runs to its kill, or to the run's end if
	 * it ended there.
	 */
	private Instant endOf(int index)
	{
		Split split = splits.get(index);
		Instant to = delveStarts.get(split.level + 1);

		if (to == null)
		{
			to = isFinished() && index == splits.size() - 1 ? endedAt : split.completedAt;
		}

		return to;
	}
}
