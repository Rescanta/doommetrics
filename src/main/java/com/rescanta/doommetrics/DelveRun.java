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
import net.runelite.api.gameval.ItemID;

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

	/** The item id of a unique the glowing hole signalled but nothing has named yet. */
	static final int UNKNOWN_UNIQUE = -1;

	static final String UNKNOWN_UNIQUE_NAME = "Unknown unique";

	/** Returned in place of a delve when nothing was written down. */
	static final int NOT_RECORDED = -1;

	/** Both forms of the eye count as the same drop. */
	static int dropKey(int itemId)
	{
		return itemId == ItemID.EYE_OF_AYAK ? ItemID.EYE_OF_AYAK_UNCHARGED : itemId;
	}

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

	/** Claimed drops by {@link #dropKey}: the most the pile was seen holding, not a running sum. */
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

	/** A notable drop as it landed in the loot pile, claimed or not. */
	static final class Landed
	{
		/** The delve it came off. */
		final int level;

		final int itemId;
		final String name;

		/** How many landed on this delve at once - almost always one. */
		final int quantity;

		/** How many of this item the pile held once these landed. */
		final int heldAfter;

		Landed(int level, int itemId, String name, int quantity, int heldAfter)
		{
			this.level = level;
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.heldAfter = heldAfter;
		}
	}

	private final List<Landed> landed = new ArrayList<>();

	/** Known pile counts by {@link #dropKey}; only a count above this is a new drop. */
	private final Map<Integer, Integer> held = new HashMap<>();

	/** "Your loot contains" warnings per item since the last descend try. */
	private final Map<Integer, Integer> warned = new HashMap<>();

	/** A joined run's first warnings are about the pile it inherited. */
	private boolean trustWarnings;

	/** Whether the pet in the pile lit the hole (only a character's first one does). */
	private boolean petGlows;

	/** Bumped whenever a drop lands or a claim is read - see {@link RunDetail#keyFor}. */
	private int lootChanges;

	/** From a clear until the game announces the next delve. */
	private boolean betweenDelves;

	/** When each delve after the first was announced, for {@link #fullTime}. */
	private final Map<Integer, Instant> delveStarts = new HashMap<>();

	/** Bumped when something is counted onto a delve already killed - see {@link RunDetail#keyFor}. */
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
		this.trustWarnings = !partial;
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
		warned.clear();
		trustWarnings = true;
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
		warned.clear();
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

	/** Records a claimed quantity. Repeats are harmless; only a larger count moves it. */
	void recordLoot(int itemId, String name, int quantity)
	{
		if (name == null || quantity <= 0)
		{
			return;
		}

		int key = dropKey(itemId);
		Drop drop = loot.get(key);

		if (drop == null)
		{
			loot.put(key, new Drop(name, quantity));
			lootChanges++;
		}
		else if (quantity > drop.quantity)
		{
			drop.quantity = quantity;
			lootChanges++;
		}
	}

	/** How many of a notable drop this trip has claimed, or 0 for none. */
	int claimed(int itemId)
	{
		Drop drop = loot.get(dropKey(itemId));
		return drop == null ? 0 : drop.quantity;
	}

	/**
	 * The pile was seen holding {@code quantity}; whatever is more than before landed now.
	 *
	 * @return the delve the drop was written down on, or {@link #NOT_RECORDED}
	 */
	int sawInPile(int itemId, String name, int quantity)
	{
		int key = dropKey(itemId);
		int before = held.getOrDefault(key, 0);

		if (name == null || quantity <= before)
		{
			return NOT_RECORDED;
		}

		held.put(key, quantity);

		int named = nameUnknown(key, name, quantity - before, quantity);

		if (named != NOT_RECORDED)
		{
			return named;
		}

		landed.add(new Landed(dropLevel(), key, name, quantity - before, quantity));
		lootChanges++;
		return dropLevel();
	}

	/** A descend was tried; its warnings are counted from nothing. */
	void descending()
	{
		warned.clear();
	}

	/**
	 * A "Your loot contains" warning. One per copy, so this try's count is the pile's count.
	 *
	 * @return the delve the drop was written down on, or {@link #NOT_RECORDED}
	 */
	int warnedOf(int itemId, String name)
	{
		int count = warned.merge(dropKey(itemId), 1, Integer::sum);

		if (!trustWarnings)
		{
			pileAlreadyHeld(itemId, count);
			// About an inherited pile: places nothing, but can still name the glow's mark.
			return nameUnknown(dropKey(itemId), name, 1, count);
		}

		return sawInPile(itemId, name, count);
	}

	/**
	 * The glowing hole: marks an unknown unique, unless the run already knows what it glows for.
	 *
	 * @return true if this placed a drop
	 */
	boolean uniqueSignalled()
	{
		if (knowsOfGlowInPile())
		{
			return false;
		}

		landed.add(new Landed(dropLevel(), UNKNOWN_UNIQUE, UNKNOWN_UNIQUE_NAME, 1, 1));
		lootChanges++;
		return true;
	}

	/** A duplicate pet sits in the pile without lighting the hole, so it doesn't count. */
	private boolean knowsOfGlowInPile()
	{
		for (Map.Entry<Integer, Integer> entry : held.entrySet())
		{
			if (entry.getValue() > 0 && (petGlows || entry.getKey() != ItemID.DOMPET))
			{
				return true;
			}
		}

		return outstandingUnknown() >= 0;
	}

	/**
	 * The first drop named after a glow takes over its mark, keeping the mark's delve.
	 *
	 * @return the delve the mark was on, or {@link #NOT_RECORDED} if there was none
	 */
	private int nameUnknown(int key, String name, int quantity, int heldAfter)
	{
		int unknown = outstandingUnknown();

		if (unknown < 0)
		{
			return NOT_RECORDED;
		}

		int level = landed.get(unknown).level;
		landed.set(unknown, new Landed(level, key, name, quantity, heldAfter));
		lootChanges++;

		petGlows |= key == ItemID.DOMPET;
		return level;
	}

	/** The index of the unnamed glow mark in {@link #landed}, or -1. */
	private int outstandingUnknown()
	{
		for (int i = 0; i < landed.size(); i++)
		{
			if (landed.get(i).itemId == UNKNOWN_UNIQUE)
			{
				return i;
			}
		}

		return -1;
	}

	/** How many of a notable drop the pile is known to hold, or 0 for none. */
	int held(int itemId)
	{
		return held.getOrDefault(dropKey(itemId), 0);
	}

	/** What a joined run's pile held before we were watching. */
	void pileAlreadyHeld(int itemId, int quantity)
	{
		held.merge(dropKey(itemId), quantity, Math::max);
	}

	/** The delve a drop seen now came off: the one just cleared, or the one still being fought. */
	int dropLevel()
	{
		return betweenDelves ? lastLevel() : currentLevel;
	}

	boolean isBetweenDelves()
	{
		return betweenDelves;
	}

	List<Landed> getLanded()
	{
		return Collections.unmodifiableList(landed);
	}

	int lootChanges()
	{
		return lootChanges;
	}

	int bankedCombatChanges()
	{
		return bankedCombatChanges;
	}

	/** Claimed drops by name, a drop earned twice listed twice. */
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
