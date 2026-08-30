package com.rescanta.doommetrics;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Reads and writes the lifetime deep delve rate against the logged in character.
 *
 * <p>Kept on the RuneScape profile alongside the milestone table, for the same reasons: an alt
 * keeps its own rate, and it survives client restarts the way any other setting does. It belongs
 * in config rather than in the history file because it is an aggregate that stops growing - two
 * numbers, whatever the character has done - so writing it costs the same on the ten thousandth
 * run as on the first.
 *
 * <p>There is no profile to key off while logged out, so every call here tolerates being a no-op -
 * callers check {@link #hasProfile()} before letting an empty read mean anything.
 */
@Slf4j
@Singleton
class TotalsStore
{
	private static final String KEY_TOTALS = "delveTotals";
	private static final String KEY_COMBAT = "combatTotals";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	TotalsStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Whether a character is logged in, and so whether reads and writes will go anywhere. */
	boolean hasProfile()
	{
		return configManager.getRSProfileKey() != null;
	}

	/** The stored total for the current character, or null if there is nothing to read. */
	DelveTotals load()
	{
		return decode(configManager.getRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_TOTALS));
	}

	void save(DelveTotals totals)
	{
		configManager.setRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_TOTALS, encode(totals));
	}

	/**
	 * The stored combat tally for the current character, or null if there is nothing to read.
	 *
	 * <p>Kept beside the delve rate and for the same reasons: it is an aggregate that stops
	 * growing - eight numbers, whatever the character has done - so writing it costs the same on
	 * the ten thousandth run as on the first, and an alt keeps its own.
	 */
	CombatTotals loadCombat()
	{
		return decodeCombat(
			configManager.getRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_COMBAT));
	}

	void saveCombat(CombatTotals totals)
	{
		configManager.setRSProfileConfiguration(DoomMetricsConfig.GROUP, KEY_COMBAT,
			gson.toJson(totals));
	}

	CombatTotals decodeCombat(String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}

		try
		{
			CombatTotals totals = gson.fromJson(json, CombatTotals.class);

			if (totals == null)
			{
				return null;
			}

			totals.sanitise();
			return totals;
		}
		catch (JsonSyntaxException e)
		{
			// Better to start over than to wedge the panel on a value that cannot be parsed.
			log.warn("Discarding unreadable lifetime combat totals", e);
			return null;
		}
	}

	String encode(DelveTotals totals)
	{
		return gson.toJson(totals);
	}

	DelveTotals decode(String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}

		try
		{
			DelveTotals totals = gson.fromJson(json, DelveTotals.class);

			if (totals == null || totals.deep < 0 || totals.ticks < 0)
			{
				// A negative count is not a rate anybody earned; start the character over rather
				// than carry a value that would misreport for the rest of their lifetime.
				return null;
			}

			return totals;
		}
		catch (JsonSyntaxException e)
		{
			// Better to start over than to wedge the panel on a value that cannot be parsed.
			log.warn("Discarding unreadable lifetime totals", e);
			return null;
		}
	}
}
