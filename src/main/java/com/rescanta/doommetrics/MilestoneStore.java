package com.rescanta.doommetrics;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Reads and writes the milestone table against the logged in character.
 *
 * <p>It is kept on the RuneScape profile rather than in the plugin's own config so an alt keeps its
 * own table, and so it survives client restarts and updates the way any other setting does. There
 * is no profile to key off while logged out, so every call here tolerates being a no-op - callers
 * check {@link #hasProfile()} before letting an empty read mean anything.
 */
@Slf4j
@Singleton
class MilestoneStore
{
	private static final String KEY_ROWS = "milestones";
	private static final String KEY_SEEDED = "milestonesSeeded";

	/**
	 * Wrapper so the map's generic type is carried by a field signature Gson can read off a plain
	 * class literal, rather than needing a type token at the call site.
	 */
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
	 * Whether this character's table has already been pre-filled from the game's own deepest delve.
	 * Recorded separately from the table so it stays true once set, and a character whose deepest
	 * is still under ten is not asked again on every login.
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
