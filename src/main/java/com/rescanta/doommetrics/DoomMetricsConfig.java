package com.rescanta.doommetrics;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DoomMetricsConfig.GROUP)
public interface DoomMetricsConfig extends Config
{
	String GROUP = "doom-of-mokhaiotl-metrics";

	/**
	 * The deepest delve anything here has to account for: the ceiling on a target, and the widest
	 * delve number the overlay is measured against.
	 *
	 * <p>Well clear of the record rather than level with it. The record was 260 when this was
	 * written and only moves one way as better gear arrives, and a plugin that has to be updated to
	 * keep up with it is a plugin that will one day quietly refuse to show someone their own delve.
	 */
	int MAX_DELVE = 1000;

	@ConfigSection(
		name = "Counters",
		description = "Extra overlay lines for what your gear gives back",
		position = 50,
		closedByDefault = true
	)
	String countersSection = "counters";

	@ConfigSection(
		name = "Advanced",
		description = "Diagnostics",
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
		keyName = "showTargetDelve",
		name = "Show target delve",
		description = "Show the delve you are aiming for and how long it is predicted to take, in"
			+ " the overlay and the side panel."
			+ "<br>Reaching it is always announced in chat, whatever the chat interval is set to.",
		position = 14
	)
	default boolean showTargetDelve()
	{
		return false;
	}

	@ConfigItem(
		keyName = "targetDelve",
		name = "Target delve",
		description = "The delve to aim for. The predicted time is what your delve 9+ average says"
			+ " the delves between here and there will take, so it appears once this run has"
			+ " cleared a delve 9."
			+ "<br>Delves get slower the deeper they go, so a distant target reads short.",
		position = 15
	)
	@Range(min = 10, max = MAX_DELVE)
	default int targetDelve()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "resultLingerMinutes",
		name = "Keep result for",
		description = "Minutes the overlay keeps showing a finished run after you die or leave, so"
			+ " the numbers are still there when you get back. Set to 0 to hide it straight away."
			+ "<br>Right-click the overlay and pick Clear to dismiss it early.",
		position = 13
	)
	@Range(min = 0, max = 180)
	default int resultLingerMinutes()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "metricGrouping",
		name = "Group counters",
		description = "How the counters ticked below are drawn."
			+ "<br>Combined sums them into one line per heading, so ticking the ancient godsword"
			+ " and the blowpipe gives a single Spec healing figure."
			+ "<br>Separate gives each its own line.",
		position = 51,
		section = countersSection
	)
	default MetricDisplay metricGrouping()
	{
		return MetricDisplay.SEPARATE;
	}

	@ConfigItem(
		keyName = "showBloodBarrage",
		name = "Blood barrage heal",
		description = "Count the hitpoints blood spells have healed you for.",
		position = 52,
		section = countersSection
	)
	default boolean showBloodBarrage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOtherSpell",
		name = "Other spell heal",
		description = "Count the hitpoints your other spells have healed you for.",
		position = 53,
		section = countersSection
	)
	default boolean showOtherSpell()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showAgsHeal",
		name = "AGS heal",
		description = "Count the hitpoints the ancient godsword spec has healed you for.",
		position = 54,
		section = countersSection
	)
	default boolean showAgsHeal()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBpHeal",
		name = "Blowpipe heal",
		description = "Count the hitpoints the blowpipe spec has healed you for.",
		position = 55,
		section = countersSection
	)
	default boolean showBpHeal()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOtherSpecHeal",
		name = "Other spec heal",
		description = "Count the hitpoints your other special attacks have healed you for.",
		position = 56,
		section = countersSection
	)
	default boolean showOtherSpecHeal()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showEldritchPrayer",
		name = "Eldritch prayer",
		description = "Count the prayer points the eldritch staff spec has restored.",
		position = 57,
		section = countersSection
	)
	default boolean showEldritchPrayer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showZcbDamage",
		name = "ZCB damage",
		description = "Count the damage the zaryte crossbow spec has dealt.",
		position = 58,
		section = countersSection
	)
	default boolean showZcbDamage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOtherSpecDamage",
		name = "Other spec damage",
		description = "Count the damage your other special attacks have dealt.",
		position = 59,
		section = countersSection
	)
	default boolean showOtherSpecDamage()
	{
		return false;
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
