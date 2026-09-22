package com.rescanta.doommetrics;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The milestone table, on the RuneScape profile so each alt has its own. A no-op while logged out -
 * check {@link #hasProfile()}.
 */
@Slf4j
@Singleton
class MilestoneStore
{
	private static final String KEY_ROWS = "milestones";
	private static final String KEY_SEEDED = "milestonesSeeded";

	/** Carries the map's generic type for Gson. */
	private static final class Stored
	{
		Map<Integer, MilestoneTable.Row> rows;
	}

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	MilestoneStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Whether a character is logged in, and so whether reads and writes will go anywhere. */
	boolean hasProfile()
	{
		return configManager.getRSProfileKey() != null;
	}

	/** The stored table for the current character, or null if there is nothing to read. */
	Map<Integer, MilestoneTable.Row> load()
	{
		return decode(configManager.getRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_ROWS));
	}

	void save(MilestoneTable table)
	{
		configManager.setRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_ROWS, encode(table));
	}

	/**
	 * Whether the table was already seeded from the game's deepest delve, stored separately so it
	 * sticks.
	 */
	boolean isSeeded()
	{
		return Boolean.parseBoolean(
			configManager.getRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_SEEDED));
	}

	void setSeeded()
	{
		configManager.setRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_SEEDED, true);
	}

	String encode(MilestoneTable table)
	{
		Stored stored = new Stored();
		stored.rows = table.getRows();
		return gson.toJson(stored);
	}

	Map<Integer, MilestoneTable.Row> decode(String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}

		try
		{
			Stored stored = gson.fromJson(json, Stored.class);
			return stored == null ? null : stored.rows;
		}
		catch (JsonSyntaxException e)
		{
			// Better to start over than to wedge the panel on a value that cannot be parsed.
			log.warn("Discarding unreadable milestone table", e);
			return null;
		}
	}
}
