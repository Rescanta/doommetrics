package com.tnamai.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One trip into the Doom of Mokhaiotl, from entering the cave until loot is claimed, the player
 * leaves, or the player dies.
 *
 * <p>Delve segments are contiguous with no gaps: a delve's duration runs from the moment the
 * previous delve was cleared, so restocking and walking to the hole are charged to the delve they
 * precede. The first segment starts when the run does. That makes the sum of every segment equal
 * the total run time by construction.
 *
 * <p>All timing is wall clock and never pauses.
 */
class DelveRun
{
	static final class Split
	{
		final int level;
		final Instant completedAt;
		final Duration duration;

		Split(int level, Instant completedAt, Duration duration)
		{
			this.level = level;
			this.completedAt = completedAt;
			this.duration = duration;
		}
	}

	private final Instant startedAt;
	private final List<Split> splits = new ArrayList<>();

	/** True when the run was already underway when we started watching, so times are incomplete. */
	private final boolean partial;

	private Instant lastClearedAt;
	private EndReason endReason;
	private int diedOnLevel = -1;

	DelveRun(Instant startedAt, boolean partial)
	{
		this.startedAt = startedAt;
		this.lastClearedAt = startedAt;
		this.partial = partial;
	}

	Split complete(int level, Instant at)
	{
		Split split = new Split(level, at, Duration.between(lastClearedAt, at));
		splits.add(split);
		lastClearedAt = at;
		return split;
	}

	void end(EndReason reason, int diedOnLevel)
	{
		this.endReason = reason;
		this.diedOnLevel = diedOnLevel;
	}

	boolean isPartial()
	{
		return partial;
	}

	EndReason getEndReason()
	{
		return endReason;
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

	/** The delve currently being fought - one past the last one cleared. */
	int currentLevel()
	{
		return lastLevel() + 1;
	}

	/**
	 * Time from the start of the run to the moment the last delve was cleared. This is what both
	 * pace figures and every reported total are built on, so a death part way into a delve simply
	 * never contributes: the answer is already the time through the previous delve.
	 */
	Duration clearedElapsed()
	{
		return Duration.between(startedAt, lastClearedAt);
	}

	/** Live wall clock time since the run started, including the delve in progress. */
	Duration liveElapsed(Instant now)
	{
		return Duration.between(startedAt, now);
	}

	/**
	 * Deep delves banked per hour of run time, counting the shallow delves against you.
	 * Delve 8 counts towards the numerator even though it is excluded from {@link #deepPace}.
	 */
	Double runPace(int deepDelveLevel)
	{
		long deep = splits.stream().filter(s -> s.level >= deepDelveLevel).count();
		double seconds = clearedElapsed().toMillis() / 1000.0;

		if (deep == 0 || seconds <= 0)
		{
			return null;
		}

		return deep * 3600.0 / seconds;
	}

	/**
	 * Pace implied by the mean length of the delves at or past {@code fromLevel}. Delve 8 is
	 * normally excluded here because it has a different amount of health to 9 and above, which
	 * would drag the average off the speed you are actually sustaining.
	 */
	Double deepPace(int fromLevel)
	{
		long count = 0;
		long millis = 0;

		for (Split split : splits)
		{
			if (split.level >= fromLevel)
			{
				count++;
				millis += split.duration.toMillis();
			}
		}

		if (count == 0 || millis <= 0)
		{
			return null;
		}

		double meanSeconds = millis / 1000.0 / count;
		return 3600.0 / meanSeconds;
	}

	Double pace(PaceMode mode, int deepDelveLevel, int paceAverageFromLevel)
	{
		return mode == PaceMode.RUN_THROUGHPUT
			? runPace(deepDelveLevel)
			: deepPace(paceAverageFromLevel);
	}

	List<Split> getSplits()
	{
		return splits;
	}
}
