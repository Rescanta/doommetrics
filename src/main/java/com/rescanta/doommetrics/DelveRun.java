package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One trip into the Doom of Mokhaiotl, from entering the cave until the player leaves or dies.
 *
 * <p>Delve segments are contiguous with no gaps: a delve's segment runs from the moment the
 * previous delve was cleared, so restocking and dropping down the hole are charged to the delve
 * they precede. The first segment starts when the run does. That makes the sum of every segment
 * equal the total run time by construction, which is what the pace figures are built on.
 *
 * <p>The game also reports the length of each fight on its own, to a tenth of a second. That is
 * kept alongside the segment as {@link Split#fight} for display, but it deliberately does not feed
 * the pace maths - the downtime between delves is real time spent, and a delves-per-hour figure
 * that ignored it would flatter you.
 *
 * <p>All timing is wall clock and never pauses.
 */
class DelveRun
{
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

	/**
	 * How many of each notable drop this trip earned, keyed by item id and in the order each was
	 * first seen. A deep run really can roll the same unique twice, so these are counts rather
	 * than a set.
	 *
	 * <p>Each count is the most the loot pile has been seen holding, not a running total of what
	 * has been added to it. That is what makes the sources safe to overlap: the pile is read both
	 * when the claim is clicked and when the game's claim script fires, and the pet arrives as a
	 * chat line as well as possibly an item. Summing those reads would count one drop several
	 * times over. Taking the largest cannot, because the pile never holds more than the run
	 * earned.
	 */
	private final Map<Integer, Drop> loot = new LinkedHashMap<>();

	private static final class Drop
	{
		private final String name;
		private int quantity;

		private Drop(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	private Instant startedAt;

	/** True when the run was already underway when we started watching, so times are incomplete. */
	private final boolean partial;

	/**
	 * A moment known to be no later than the real start of the run, used only for milestone
	 * personal bests. Null when {@link #startedAt} is already exact.
	 */
	private final Instant pbAnchor;

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
	}

	/** The game announced the delve we have just dropped into. */
	void enterLevel(int level)
	{
		currentLevel = level;
	}

	/**
	 * Moves the start of a run that has not banked a delve yet onto the moment the game says the
	 * delve actually began. The chat line announcing a delve lands a couple of seconds before the
	 * fight the game is timing starts, and without this the first segment carries that walk-in and
	 * reads longer than the duration the game reports for the same delve.
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
		return split;
	}

	/**
	 * Notes that this trip has been seen holding {@code quantity} of a notable drop.
	 *
	 * <p>Reporting the same quantity again leaves the run as it was, so a caller never has to know
	 * whether another source got there first. Reporting a larger one raises the count: that is how
	 * a second cloth out of a deeper delve gets counted, and it is the only way a count ever moves.
	 */
	void recordLoot(int itemId, String name, int quantity)
	{
		if (name == null || quantity <= 0)
		{
			return;
		}

		Drop drop = loot.get(itemId);

		if (drop == null)
		{
			loot.put(itemId, new Drop(name, quantity));
		}
		else if (quantity > drop.quantity)
		{
			drop.quantity = quantity;
		}
	}

	/**
	 * The notable drops from this trip, by name, in the order each was first seen. A drop earned
	 * twice is listed twice - the names are the record, so the count has to live in them.
	 */
	List<String> getLoot()
	{
		List<String> names = new ArrayList<>();

		for (Drop drop : loot.values())
		{
			for (int i = 0; i < drop.quantity; i++)
			{
				names.add(drop.name);
			}
		}

		return names;
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

	/** The delve currently being fought. */
	int currentLevel()
	{
		return currentLevel;
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

	/**
	 * What a milestone personal best is measured over: the same span as {@link #clearedElapsed},
	 * except that a run we joined part way through is measured from the anchor instead.
	 *
	 * <p>A partial run's {@link #startedAt} is the moment we first saw it, which is later than the
	 * truth and would hand out a personal best nobody earned. The anchor is a moment the run
	 * provably had not started by - you cannot drop back into the Doom past delve 1, so the run
	 * began after you logged in, which in turn was after the client started. Measuring from it can
	 * only ever make the time too long, and a time that is too long simply never wins.
	 */
	Duration pbElapsed()
	{
		Instant from = pbAnchor == null || startedAt.isBefore(pbAnchor) ? startedAt : pbAnchor;
		return Duration.between(from, lastClearedAt);
	}

	/** Live wall clock time since the run started, including the delve in progress. */
	Duration liveElapsed(Instant now)
	{
		return Duration.between(startedAt, now);
	}

	/**
	 * What the timer should read: live while the run is going, and frozen on the time through the
	 * last cleared delve once it is over, so the number always matches the pace denominator.
	 */
	Duration displayElapsed(Instant now)
	{
		return isFinished() ? clearedElapsed() : liveElapsed(now);
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
				millis += split.segment.toMillis();
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
		return Collections.unmodifiableList(splits);
	}
}
