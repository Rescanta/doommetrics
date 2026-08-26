package com.tnamai.doommetrics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DoomMetricsConfig.GROUP)
public interface DoomMetricsConfig extends Config
{
	String GROUP = "doom-of-mokhaiotl-metrics";

	@ConfigSection(
		name = "Advanced",
		description = "Which delves count as deep, and diagnostics",
		position = 100,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "paceMode",
		name = "Pace",
		description = "Deep pace averages your delve 9+ times and ignores the shallow warm-up."
			+ "<br>Run pace counts deep delves banked per hour of the whole run, warm-up included."
			+ "<br>The choice drives both the overlay and the chat messages.",
		position = 1
	)
	default PaceMode paceMode()
	{
		return PaceMode.DEEP_AVERAGE;
	}

	@ConfigItem(
		keyName = "chatIntervalDelves",
		name = "Chat every N delves",
		description = "Post elapsed time and pace to chat whenever the delve number is a multiple"
			+ " of this. Shallow delves are skipped, so 5 reports at delve 10, 15, 20 and so on."
			+ " Set to 0 to turn the messages off.",
		position = 2
	)
	@Range(min = 0, max = 100)
	default int chatIntervalDelves()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "announceRunEnd",
		name = "Announce run end",
		description = "Post a summary to chat when you claim loot, leave, or die.",
		position = 3
	)
	default boolean announceRunEnd()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDelveNumber",
		name = "Show delve number",
		description = "Show the delve you are currently on in the overlay.",
		position = 10
	)
	default boolean showDelveNumber()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRunTimer",
		name = "Show run timer",
		description = "Show total elapsed time for the current run in the overlay.",
		position = 11
	)
	default boolean showRunTimer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPace",
		name = "Show pace",
		description = "Show the pace figure in the overlay.",
		position = 12
	)
	default boolean showPace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "deepDelveLevel",
		name = "Deep delve from",
		description = "The first delve that counts as deep. Gates the chat messages, and is the"
			+ " numerator for Run pace. Delve 8 counts as deep even though it is excluded from"
			+ " the Deep pace average.",
		position = 101,
		section = advancedSection
	)
	@Range(min = 1, max = 20)
	default int deepDelveLevel()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "paceAverageFromLevel",
		name = "Average pace from",
		description = "The first delve included in the Deep pace average. Defaults to 9 because"
			+ " delve 8 has a different amount of health to 9 and above.",
		position = 102,
		section = advancedSection
	)
	@Range(min = 1, max = 20)
	default int paceAverageFromLevel()
	{
		return 9;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Log every Doom varplayer change and delve transition at debug level."
			+ " Useful for reporting a problem with the timings.",
		position = 103,
		section = advancedSection
	)
	default boolean debugLogging()
	{
		return false;
	}
}
