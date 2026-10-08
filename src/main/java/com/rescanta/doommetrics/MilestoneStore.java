package com.rescanta.doommetrics;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
	private static final String KEY_TRIP = "milestonesCountedTrip";

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
	MilestoneTable.Saved load()
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

	/**
	 * The trip counted last while it may still be going, or -1 - see
	 * {@link MilestoneTracker#runStarted}. Stored so a client restarted mid-trip finds it again.
	 */
	int loadCountedTrip()
	{
		return decodeTrip(configManager.getRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_TRIP));
	}

	void saveCountedTrip(int trip)
	{
		if (trip < 0)
		{
			configManager.unsetRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_TRIP);
			return;
		}

		configManager.setRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_TRIP, trip);
	}

	static int decodeTrip(String saved)
	{
		if (saved == null || saved.isEmpty())
		{
			return -1;
		}

		try
		{
			return Math.max(-1, Integer.parseInt(saved));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	String encode(MilestoneTable table)
	{
		return gson.toJson(table.save());
	}

	MilestoneTable.Saved decode(String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}

		try
		{
			return gson.fromJson(json, MilestoneTable.Saved.class);
		}
		catch (JsonSyntaxException e)
		{
			// Better to start over than to wedge the panel on a value that cannot be parsed.
			log.warn("Discarding unreadable milestone table", e);
			return null;
		}
	}
}
