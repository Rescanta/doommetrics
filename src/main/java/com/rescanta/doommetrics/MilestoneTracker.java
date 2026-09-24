package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Keeps the character's milestone table: banks milestone clears and the runs the resets card
 * counts; loads and seeds it.
 */
@Slf4j
class MilestoneTracker
{
	private final Client client;
	private final MilestoneStore milestoneStore;

	/** Told whenever the table's rows change. */
	private final Runnable onChanged;

	private final MilestoneTable milestones = new MilestoneTable();

	/** The milestone runs are being reset at: the target delve, rounded down to a row. */
	private final IntSupplier resetTarget;

	/**
	 * Whether the run in progress belongs to the character logged in. A run held open across a
	 * lost connection can end after the client comes back as an alt, and saving then would write
	 * this character's table over the alt's.
	 */
	private final BooleanSupplier belongsToRun;

	/** Milestones whose personal best has been beaten since the client started. */
	private final Set<Integer> improvedThisSession = new HashSet<>();

	/** Clears of each milestone this session, by delve. */
	private final Map<Integer, Integer> sessionClears = new HashMap<>();

	/**
	 * The trip counted last, while it may still be going: TOTAL_DOM_LEVELS less the delves before
	 * it, or -1. Kept across a toggle or session reset, which drop a run without ending it.
	 */
	private int countedTrip = -1;

	/** Whether the run in progress is a counted trip still on delve 1: not an attempt yet. */
	private boolean takeBackIfAbandoned;

	MilestoneTracker(Client client, MilestoneStore milestoneStore, IntSupplier resetTarget,
		BooleanSupplier belongsToRun, Runnable onChanged)
	{
		this.client = client;
		this.milestoneStore = milestoneStore;
		this.resetTarget = resetTarget;
		this.belongsToRun = belongsToRun;
		this.onChanged = onChanged;
	}

	void reset()
	{
		milestones.replaceAll(null);
		improvedThisSession.clear();
		sessionClears.clear();
	}

	void forgetSession()
	{
		improvedThisSession.clear();
		sessionClears.clear();
	}

	/** Reads the logged in character's table; while logged out the last one stays on show. */
	void load()
	{
		if (!milestoneStore.hasProfile())
		{
			return;
		}

		milestones.replaceAll(milestoneStore.load());
		// Another character's session: its resets are not this one's.
		improvedThisSession.clear();
		sessionClears.clear();
		seedFromDeepestLevel();
		onChanged.run();
	}

	void recordClear(int level, DelveRun run)
	{
		if (!MilestoneTable.isMilestone(level) || !belongsToRun.getAsBoolean())
		{
			return;
		}

		Duration elapsed = run.pbElapsed();
		int ticks = elapsed == null ? 0 : DoomFormat.toTicks(elapsed);
		// A joined run's time is an upper bound: fair as a best it failed to beat, not an average.
		boolean whole = !run.isPartial();

		if (milestones.record(level, ticks, whole))
		{
			improvedThisSession.add(level);
			log.debug("Delve {} personal best is now {} ticks", level, ticks);
		}

		if (whole && ticks > 0 && level == resetTarget.getAsInt())
		{
			milestones.recordRecent(level, ticks);
		}

		sessionClears.merge(level, 1, Integer::sum);
		milestoneStore.save(milestones);
		onChanged.run();
	}

	/**
	 * Counts a run towards the resets card's reach rate. A joined run on the trip counted last is
	 * that trip picked up again - after a session reset, or the plugin turned off before its first
	 * clear - and is not counted twice.
	 *
	 * @param level the delve the run starts on
	 */
	void runStarted(boolean partial, int level)
	{
		takeBackIfAbandoned = false;

		if (!belongsToRun.getAsBoolean())
		{
			return;
		}

		int trip = trip(client.getVarpValue(VarPlayerID.TOTAL_DOM_LEVELS), level);
		takeBackIfAbandoned = level <= 1;

		if (partial && isSameTrip(countedTrip, trip))
		{
			log.debug("Joined the trip counted already, not counting it again");
			return;
		}

		countedTrip = trip;
		milestones.runStarted();
		milestoneStore.save(milestones);
	}

	/**
	 * Names a trip by the game's delve counter less the delves before {@code level}, or -1 while
	 * the counter hasn't arrived.
	 */
	static int trip(int delvesCleared, int level)
	{
		return delvesCleared > 0 ? delvesCleared - (level - 1) : -1;
	}

	/**
	 * One over is the same trip: the delve counter goes up with a clear, seconds before the delve
	 * number moves on.
	 */
	static boolean isSameTrip(int counted, int trip)
	{
		return counted >= 0 && (trip == counted || trip == counted + 1);
	}

	/**
	 * A run walked out of on delve 1 comes back off the reach rate's count. One joined deeper had
	 * clears, unwatched, so it was an attempt.
	 */
	void runAbandoned()
	{
		boolean takeBack = takeBackIfAbandoned;
		takeBackIfAbandoned = false;
		countedTrip = -1;

		if (!takeBack || !belongsToRun.getAsBoolean())
		{
			return;
		}

		milestones.runAbandoned();
		milestoneStore.save(milestones);
		onChanged.run();
	}

	/** The trip counted last is over, so a joined run can't be it. */
	void runEnded()
	{
		takeBackIfAbandoned = false;
		countedTrip = -1;
	}

	/** The resets card's figures, for runs aimed at the current target. */
	ResetSummary summary()
	{
		int target = resetTarget.getAsInt();
		return milestones.summary(target, sessionClears.getOrDefault(target, 0));
	}

	/** Pre-fills the reached milestone rows from the game's deepest delve, once per character. */
	void seedFromDeepestLevel()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !milestoneStore.hasProfile()
			|| milestoneStore.isSeeded())
		{
			return;
		}

		int deepest = client.getVarpValue(VarPlayerID.DOM_DEEPEST_LEVEL);

		if (deepest <= 0)
		{
			// The varp flood has not landed yet; the change event will bring us back here.
			return;
		}

		if (milestones.seedReached(deepest))
		{
			milestoneStore.save(milestones);
			onChanged.run();
			log.debug("Seeded milestones up to delve {} from the game's deepest level", deepest);
		}

		milestoneStore.setSeeded();
	}

	List<MilestoneTablePanel.Row> rows()
	{
		List<MilestoneTablePanel.Row> rows = new ArrayList<>();
		int target = resetTarget.getAsInt();
		milestones.getRows().forEach((delve, row) -> rows.add(new MilestoneTablePanel.Row(
			delve, row.kc, row.pbTicks, improvedThisSession.contains(delve), delve == target)));
		return rows;
	}
}
