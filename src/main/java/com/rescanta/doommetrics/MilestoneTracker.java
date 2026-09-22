package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarPlayerID;

/** Keeps the character's milestone table: banks milestone clears, loads and seeds it. */
@Slf4j
class MilestoneTracker
{
	private final Client client;
	private final MilestoneStore milestoneStore;

	/** Told whenever the table's rows change. */
	private final Runnable onChanged;

	private final MilestoneTable milestones = new MilestoneTable();

	/** Milestones whose personal best has been beaten since the client started. */
	private final Set<Integer> improvedThisSession = new HashSet<>();

	MilestoneTracker(Client client, MilestoneStore milestoneStore, Runnable onChanged)
	{
		this.client = client;
		this.milestoneStore = milestoneStore;
		this.onChanged = onChanged;
	}

	void reset()
	{
		milestones.replaceAll(Collections.emptyMap());
		improvedThisSession.clear();
	}

	void forgetSession()
	{
		improvedThisSession.clear();
	}

	/** Reads the logged in character's table; while logged out the last one stays on show. */
	void load()
	{
		if (!milestoneStore.hasProfile())
		{
			return;
		}

		Map<Integer, MilestoneTable.Row> loaded = milestoneStore.load();
		milestones.replaceAll(loaded);
		improvedThisSession.clear();
		seedFromDeepestLevel();
		onChanged.run();
	}

	void recordClear(int level, DelveRun run)
	{
		if (!MilestoneTable.isMilestone(level))
		{
			return;
		}

		Duration elapsed = run.pbElapsed();
		int ticks = elapsed == null ? 0 : DoomFormat.toTicks(elapsed);

		if (milestones.record(level, ticks))
		{
			improvedThisSession.add(level);
			log.debug("Delve {} personal best is now {} ticks", level, ticks);
		}

		milestoneStore.save(milestones);
		onChanged.run();
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
		milestones.getRows().forEach((delve, row) -> rows.add(new MilestoneTablePanel.Row(
			delve, row.kc, row.pbTicks, improvedThisSession.contains(delve))));
		return rows;
	}
}
