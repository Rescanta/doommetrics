package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	/**
	 * The first delve that counts as deep - the numerator of every deep delve count and of
	 * {@link PaceMode#RUN_THROUGHPUT}, and the floor for the chat messages.
	 */
	static final int DEEP_DELVE_LEVEL = 8;

	/**
	 * The first delve included in the {@link PaceMode#DEEP_AVERAGE} mean. One past
	 * {@link #DEEP_DELVE_LEVEL} because delve 8 has a different amount of health to 9 and above,
	 * so averaging it in reads as a pace nobody is actually sustaining.
	 */
	static final int PACE_AVERAGE_FROM_LEVEL = 9;

	/**
	 * Handed back for a delve that earned nothing. Shared and never written to - every caller of
	 * {@link #combatOn} only reads.
	 */
	private static final CombatTotals EMPTY_COMBAT = new CombatTotals();

	/**
	 * The item id a drop is placed under when the game said a unique dropped without saying which:
	 * the hole glowing and the unique sound playing. Not a real item, so nothing is ever claimed
	 * under it - see {@link #uniqueSignalled}.
	 */
	static final int UNKNOWN_UNIQUE = -1;

	/** What an unknown unique is called where a name has to be written down. */
	static final String UNKNOWN_UNIQUE_NAME = "Unknown unique";

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

	/**
	 * A notable drop as it landed in the loot pile, placed on the delve it came off.
	 *
	 * <p>Kept apart from {@link #loot}, which is what was claimed: this is where each drop turned
	 * up, whether or not the run went on to walk out with it.
	 */
	static final class Landed
	{
		/** The delve it came off. */
		final int level;

		final int itemId;
		final String name;

		/** How many landed on this delve at once - almost always one. */
		final int quantity;

		/**
		 * How many of this item the pile held once these landed, so the second eye out of a run
		 * reads 2 - which is what a claim has to reach for this one to have been walked out with.
		 */
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

	/** Every notable drop this trip has seen land, in the order they landed. */
	private final List<Landed> landed = new ArrayList<>();

	/**
	 * How many of each notable drop the pile is known to hold, keyed by item id - the figure a new
	 * reading of the pile is measured against, so a pile that has not grown places nothing.
	 *
	 * <p>The game puts up a warning about a unique every time you try to descend with it still in
	 * the pile, and the pile itself is sent over again and again. Only a count going up is a drop.
	 */
	private final Map<Integer, Integer> held = new HashMap<>();

	/**
	 * How many "Your loot contains" warnings each item has had since the last descend was tried,
	 * keyed by item id.
	 *
	 * <p>The game puts up one warning per copy of a unique in the pile, one after another, each time
	 * you try to go deeper. So the number of warnings one try brings up for an item is how many of
	 * it the pile holds. Started over on every try, because backing out and trying again brings the
	 * whole row of warnings up again from the first.
	 */
	private final Map<Integer, Integer> warned = new HashMap<>();

	/**
	 * Whether a warning can be trusted to be about a drop this run saw land. Not until a run joined
	 * part way through has gone down a delve under our eyes: the first warnings it gets are about
	 * whatever was already in the pile, from delves nobody watched.
	 */
	private boolean trustWarnings;

	/**
	 * Bumped whenever a drop lands or a claim is read, so the detail window can tell that something
	 * about the drops has changed without comparing them - see {@link RunDetail#keyFor}.
	 */
	private int lootChanges;

	/**
	 * True from a clear until the game announces the next delve, which is what decides whether a
	 * drop seen now came off the delve just cleared or the one still being fought - see
	 * {@link #dropLevel}.
	 */
	private boolean betweenDelves;

	/**
	 * What this trip's gear and spellbook gave back: healing, prayer and spec damage, by source.
	 * See {@link CombatTracker} for what does and does not get counted.
	 */
	private final CombatTotals combat = new CombatTotals();

	/**
	 * The same tally split by the delve it was earned on, keyed by delve number.
	 *
	 * <p>What decides which delve an amount belongs to is {@link #currentLevel} at the moment the
	 * tracker credits it, which is exact rather than a convention: every source counted here is
	 * cleared when a delve completes, so no effect fired on one delve can pay out on the next.
	 *
	 * <p>A delve that earned nothing has no entry rather than an entry of zeroes, so a run that
	 * never fires a spec costs this nothing at all. The sum of these is {@link #combat} by
	 * construction - both are written by the same call - so the chart and the counters can never
	 * disagree about what a run earned.
	 */
	private final Map<Integer, CombatTotals> combatByDelve = new LinkedHashMap<>();

	/** What each counter gained in the last few seconds, for the figures that show a hit landing. */
	private final RecentGains recent = new RecentGains();

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
		this.trustWarnings = !partial;
	}

	/** The game announced the delve we have just dropped into. */
	void enterLevel(int level)
	{
		currentLevel = level;
		betweenDelves = false;
		warned.clear();
		trustWarnings = true;
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
		betweenDelves = true;
		warned.clear();
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
		Drop drop = loot.get(itemId);
		return drop == null ? 0 : drop.quantity;
	}

	/**
	 * Notes that the loot pile has been seen holding {@code quantity} of a notable drop while the run
	 * is going, and places however many that is more than before on the delve they came off.
	 *
	 * <p>A reading that holds no more than the last one places nothing, which is what makes it safe
	 * to feed every copy of the pile the game sends, and to feed two copies of it: the first to show
	 * a new drop places it, and the other finds nothing left to place. A reading holding fewer is a
	 * pile being emptied, and changes nothing either - a drop that landed stays where it landed.
	 *
	 * @return true if this placed a drop
	 */
	boolean sawInPile(int itemId, String name, int quantity)
	{
		int before = held.getOrDefault(itemId, 0);

		if (name == null || quantity <= before)
		{
			return false;
		}

		held.put(itemId, quantity);
		int level = dropLevel();
		// A unique the game only signalled is this one, now that it has a name.
		landed.removeIf(drop -> drop.itemId == UNKNOWN_UNIQUE && drop.level == level);
		landed.add(new Landed(level, itemId, name, quantity - before, quantity));
		lootChanges++;
		return true;
	}

	/**
	 * A descend was tried: the hole clicked, or the button on the loot screen. Whatever warnings it
	 * brings up are counted from nothing - see {@link #warned}.
	 */
	void descending()
	{
		warned.clear();
	}

	/**
	 * The game warned that the pile holds this item as you tried to go deeper. One warning is one
	 * copy, so this try's count for the item is how many the pile holds, and anything over what the
	 * run already knew about came off the delve just cleared.
	 *
	 * @return true if this placed a drop
	 */
	boolean warnedOf(int itemId, String name)
	{
		int count = warned.merge(itemId, 1, Integer::sum);

		if (!trustWarnings)
		{
			pileAlreadyHeld(itemId, count);
			return false;
		}

		return sawInPile(itemId, name, count);
	}

	/**
	 * The game signalled a unique without naming it - the hole glowing and the unique sound - so
	 * something is in the pile off this delve and nothing yet says what.
	 *
	 * <p>Placed as an unknown unique, which turns into the real drop if a warning, the loot screen or
	 * the claim names it later - see {@link #sawInPile}. One that is never named was lost with the
	 * run, because every way of keeping it would have named it. A second signal for the same delve
	 * places nothing more: the glow says a unique dropped, not how many.
	 *
	 * @return true if this placed a drop
	 */
	boolean uniqueSignalled()
	{
		int level = dropLevel();

		for (Landed drop : landed)
		{
			if (drop.itemId == UNKNOWN_UNIQUE && drop.level == level)
			{
				return false;
			}
		}

		landed.add(new Landed(level, UNKNOWN_UNIQUE, UNKNOWN_UNIQUE_NAME, 1, 1));
		lootChanges++;
		return true;
	}

	/** How many of a notable drop the pile is known to hold, or 0 for none. */
	int held(int itemId)
	{
		return held.getOrDefault(itemId, 0);
	}

	/** Places one more of a notable drop than the run has seen. */
	void landedOne(int itemId, String name)
	{
		sawInPile(itemId, name, held.getOrDefault(itemId, 0) + 1);
	}

	/**
	 * Takes what the pile already holds as having been there before we were watching, so a run
	 * joined part way through does not place every drop already in it on the first delve we see.
	 */
	void pileAlreadyHeld(int itemId, int quantity)
	{
		held.merge(itemId, quantity, Math::max);
	}

	/**
	 * The delve a drop seen now came off.
	 *
	 * <p>Loot only lands in the pile when a delve is cleared, so that is always the delve just
	 * cleared - but the pile and the chat line clearing the delve can arrive either way round. Seen
	 * after the clear, it is the delve before the one we are waiting to drop into; seen before, it
	 * is the delve still being fought, whose clear is on its way.
	 */
	int dropLevel()
	{
		return betweenDelves ? lastLevel() : currentLevel;
	}

	/** Whether a delve has been cleared and the game has not yet announced the next. */
	boolean isBetweenDelves()
	{
		return betweenDelves;
	}

	/** Every notable drop this trip has seen land, in the order they landed. */
	List<Landed> getLanded()
	{
		return Collections.unmodifiableList(landed);
	}

	/** Moves whenever a drop lands or a claim is read - see {@link #lootChanges}. */
	int lootChanges()
	{
		return lootChanges;
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

	/**
	 * Credits an attributed heal, prayer restore or hit to this trip, and to the delve.
	 *
	 * @param at when it was credited, which is what decides how long it shows as a gain
	 */
	void recordCombat(CombatMetric metric, long amount, Instant at)
	{
		if (amount <= 0)
		{
			// Tested here as well as in CombatTotals so nothing that would be discarded can leave
			// an empty tally behind on a delve that earned nothing.
			return;
		}

		combat.add(metric, amount);
		combatByDelve.computeIfAbsent(currentLevel, level -> new CombatTotals()).add(metric, amount);
		recent.add(metric, amount, at);
	}

	/** What {@code metric} has gained in the last few seconds, or 0 - see {@link RecentGains}. */
	long recentGain(CombatMetric metric, Instant now)
	{
		return recent.get(metric, now);
	}

	/**
	 * This trip's combat tally. Live while the run is, so the panel reads what has been counted so
	 * far rather than waiting for the trip to end.
	 */
	CombatTotals getCombat()
	{
		return combat;
	}

	/**
	 * What was earned on one delve, or an empty tally for a delve that earned nothing. Never null,
	 * so a caller walking every delve of a run does not have to tell "no entry" from "no figures".
	 */
	CombatTotals combatOn(int level)
	{
		CombatTotals totals = combatByDelve.get(level);
		return totals == null ? EMPTY_COMBAT : totals;
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
	 * How many delves at or past {@link #DEEP_DELVE_LEVEL} this run completed - what a deep delve rate
	 * counts, whether that rate covers this run alone or a lifetime of them.
	 */
	int deepCleared()
	{
		int deep = 0;

		for (Split split : splits)
		{
			if (split.level >= DEEP_DELVE_LEVEL)
			{
				deep++;
			}
		}

		return deep;
	}

	/**
	 * Deep delves completed per hour of run time, counting the shallow delves against you.
	 * Delve 8 counts towards the numerator even though it is excluded from {@link #deepPace}.
	 */
	Double fullPace()
	{
		int deep = deepCleared();
		double seconds = clearedElapsed().toMillis() / 1000.0;

		if (deep == 0 || seconds <= 0)
		{
			return null;
		}

		return deep * 3600.0 / seconds;
	}

	/**
	 * The mean length of the delves at or past {@link #PACE_AVERAGE_FROM_LEVEL}, or null until one
	 * has been cleared. Delve 8 is excluded because it has a different amount of health to 9 and
	 * above, which would drag the average off the speed you are actually sustaining.
	 *
	 * <p>Both {@link #deepPace} and {@link #untilTarget} are built on this rather than working the
	 * average out for themselves, so the time predicted to a target and the pace shown beside it
	 * can never disagree about how long a delve is taking.
	 */
	Duration meanDeepSegment()
	{
		long count = 0;
		long millis = 0;

		for (Split split : splits)
		{
			if (split.level >= PACE_AVERAGE_FROM_LEVEL)
			{
				count++;
				millis += split.segment.toMillis();
			}
		}

		return count == 0 || millis <= 0 ? null : Duration.ofMillis(millis / count);
	}

	/** Pace implied by {@link #meanDeepSegment}, or null while there is no mean to imply one. */
	Double deepPace()
	{
		Duration mean = meanDeepSegment();
		return mean == null ? null : 3600.0 / (mean.toMillis() / 1000.0);
	}

	/** Whether this run has cleared the delve it was aiming for. */
	boolean hasReached(int target)
	{
		return target > 0 && lastLevel() >= target;
	}

	/**
	 * How much longer this run has to go to reach {@code target}, at the speed its deep delves have
	 * been going. Null when there is no answer to give: no target set, the target already reached,
	 * the run over, or no delve 9+ cleared yet to average over.
	 *
	 * <p>The delve in progress is charged against the estimate as it goes, so the figure counts
	 * down second by second rather than sitting still between clears. A delve that overruns the
	 * average would otherwise drive the estimate below what the delves still to come must take, so
	 * it floors at exactly that: the figure stalls for as long as you are over, then resumes on the
	 * clear. Without the floor it would count down into nothing and jump back up, which reads as
	 * the estimate getting worse the closer you get.
	 *
	 * <p>Two things a flat average cannot know are left in deliberately, because every other figure
	 * here is a flat average and a prediction that quietly corrected for them would be the odd one
	 * out: delves 1-8 are quicker than the mean, so a target set during delves 1-8 reads long, and
	 * delves get slower the deeper they go, so a distant target reads short.
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

	/**
	 * How long this run takes from its start to clearing {@code target}: the real time once it has,
	 * and the time so far plus {@link #untilTarget} until then. Null when there is no answer to
	 * give: no target set, the run over short of it, no delve 9+ cleared yet to average over, or a
	 * run joined already past the target, whose clear of it nobody saw.
	 *
	 * <p>The real time stays put however much deeper the run goes, and survives the run ending.
	 *
	 * <p>Built on {@link #untilTarget} rather than beside it, so the two rows can never disagree.
	 * That also brings its floor along: the figure holds still while a delve runs to the average,
	 * and counts up for as long as one overruns it, which is the run getting slower.
	 */
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

	/**
	 * The pace this run is read by: the one {@code configured} while the run is going, and full pace
	 * once it is over. Deep pace says how fast more deep delves could be added, which a run that has
	 * ended is not going to do - what is left to say is how fast it went, start to finish.
	 */
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
}
