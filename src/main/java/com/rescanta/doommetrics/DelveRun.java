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

		Split(int level, Instant completedAt, Duration segment, Duration fight)
		{
			this.level = level;
			this.completedAt = completedAt;
			this.segment = segment;
			this.fight = fight;
		}
	}

	private final List<Split> splits = new ArrayList<>();

	private final RunLoot loot;

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

	/** Whether the first clear was watched from its delve's start. */
	private boolean firstClearTimed;

	private Instant lastClearedAt;
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
		this.currentLevel = currentLevel;
		this.partial = partial;
		this.pbAnchor = pbAnchor;
		this.loot = new RunLoot(partial, this::dropLevel);
		this.firstClearTimed = !partial;
	}

	/** The game announced the delve we have just dropped into. */
	void enterLevel(int level, Instant at)
	{
		// A joined run picked up between delves has now seen one start.
		if (partial && splits.isEmpty() && level > currentLevel)
		{
			watchedFromDelveStart(at);
		}

		currentLevel = level;
		betweenDelves = false;
		loot.delveEntered();
		delveStarts.putIfAbsent(level, at);
	}

	/** A joined run with no clears saw its delve start, so its first clear is measured. */
	void watchedFromDelveStart(Instant at)
	{
		if (!splits.isEmpty())
		{
			return;
		}

		startedAt = at;
		lastClearedAt = at;
		firstClearTimed = true;
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
		return true;
	}

	Split complete(int level, Instant at, Duration fight)
	{
		Split split = new Split(level, at, Duration.between(lastClearedAt, at), fight);
		splits.add(split);
		lastClearedAt = at;
		currentLevel = level + 1;
		betweenDelves = true;
		loot.delveCleared();
		return split;
	}

	/** Whether the last clear's segment was measured, and so can be charged to a rate. */
	boolean lastClearTimed()
	{
		return !splits.isEmpty() && (firstClearTimed || splits.size() > 1);
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

	/** The delve a drop seen now came off: the one just cleared, or the one still being fought. */
	int dropLevel()
	{
		return betweenDelves ? lastLevel() : currentLevel;
	}

	RunLoot loot()
	{
		return loot;
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
		combatByDelve.computeIfAbsent(dropLevel(), level -> new CombatTotals()).add(metric, amount);
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

	/** Every clear less an unmeasured first one. */
	private List<Split> timedSplits()
	{
		return firstClearTimed || splits.isEmpty() ? splits : splits.subList(1, splits.size());
	}

	/** Deep delves per hour of run time, shallow delves counting against you. */
	Double fullPace()
	{
		List<Split> timed = timedSplits();
		int deep = deepIn(timed);
		Instant from = timed.size() == splits.size() ? startedAt : splits.get(0).completedAt;
		double seconds = Duration.between(from, lastClearedAt).toMillis() / 1000.0;

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
		long millis = remaining * mean.toMillis() - Duration.between(lastClearedAt, now).toMillis();
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

	/**
	 * A cleared delve's time as the detail window draws it: from its start to the next delve's
	 * start, so its kill and the wait after it.
	 */
	Duration fullTime(int index)
	{
		Split split = splits.get(index);
		Instant from = index == 0
			? startedAt
			: delveStarts.getOrDefault(split.level, splits.get(index - 1).completedAt);
		Instant to = delveStarts.get(split.level + 1);

		if (to == null)
		{
			to = isFinished() && index == splits.size() - 1 ? endedAt : split.completedAt;
		}

		return Duration.between(from, to);
	}
}
