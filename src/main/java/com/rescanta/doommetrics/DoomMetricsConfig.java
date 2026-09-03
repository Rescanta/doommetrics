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
	 * <p>Well clear of the record rather than level with it. The record only moves one way as
	 * better gear arrives, and a plugin that has to be updated to keep up with it is a plugin
	 * that will one day quietly refuse to show someone their own delve.
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
		description = "Which figure the overlay and chat show"
	)
	default PaceMode paceMode()
	{
		return PaceMode.DEEP_AVERAGE;
	}

	@ConfigItem(
		keyName = "chatIntervalDelves",
		name = "Chat every N delves",
		description = "Post a progress message every N delves once past the warm-up. 0 disables the messages."
	)
	@Range(min = 0, max = 100)
	default int chatIntervalDelves()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "announceRunEnd",
		name = "Announce run end",
		description = "Post a summary when a run ends by claiming, leaving or dying"
	)
	default boolean announceRunEnd()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayStyle",
		name = "Display",
		description = "Panel of rows, one infobox square, or nothing. Off keeps timing, counters, chat, panel and history running."
	)
	default DisplayStyle displayStyle()
	{
		return DisplayStyle.PANEL;
	}

	@ConfigItem(
		keyName = "infoboxFigure",
		name = "Infobox figure",
		description = "Which single figure the infobox square holds"
	)
	default InfoboxFigure infoboxFigure()
	{
		return InfoboxFigure.DELVE;
	}

	@ConfigItem(
		keyName = "showDelveNumber",
		name = "Show delve number",
		description = "Overlay row"
	)
	default boolean showDelveNumber()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRunTimer",
		name = "Show run timer",
		description = "Overlay row"
	)
	default boolean showRunTimer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPace",
		name = "Show pace",
		description = "Overlay row"
	)
	default boolean showPace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resultLingerMinutes",
		name = "Keep result for",
		description = "How long a finished run stays on screen. 0 hides it at once. Right-click Clear dismisses it early."
	)
	@Range(min = 0, max = 180)
	default int resultLingerMinutes()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "showTargetDelve",
		name = "Show target delve",
		description = "Adds the target and predicted rows to the overlay and side panel"
	)
	default boolean showTargetDelve()
	{
		return false;
	}

	@ConfigItem(
		keyName = "targetDelve",
		name = "Target delve",
		description = "The delve being aimed for"
	)
	@Range(min = 10, max = MAX_DELVE)
	default int targetDelve()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Logs every Doom varplayer change and delve transition, at debug level",
		section = advancedSection
	)
	default boolean debugLogging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "groupCounters",
		name = "Group counters",
		description = "Separate gives each ticked counter its own overlay line; Combined folds them into one line per group",
		section = countersSection
	)
	default CounterLayout groupCounters()
	{
		return CounterLayout.SEPARATE;
	}

	@ConfigItem(
		keyName = "countBloodBarrage",
		name = "Blood barrage heal",
		description = "Count hitpoints healed that can be pinned on a blood barrage drain",
		section = countersSection
	)
	default boolean countBloodBarrage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countOtherSpell",
		name = "Other spell heal",
		description = "Count hitpoints healed that can be pinned on a non-barrage blood spell drain",
		section = countersSection
	)
	default boolean countOtherSpell()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countAgs",
		name = "AGS heal",
		description = "Count hitpoints healed that can be pinned on an ancient godsword spec",
		section = countersSection
	)
	default boolean countAgs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countBlowpipe",
		name = "Blowpipe heal",
		description = "Count hitpoints healed that can be pinned on a toxic blowpipe spec",
		section = countersSection
	)
	default boolean countBlowpipe()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countOtherSpecHeal",
		name = "Other spec heal",
		description = "Count hitpoints healed that can be pinned on any other weapon spec",
		section = countersSection
	)
	default boolean countOtherSpecHeal()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countEldritch",
		name = "Eldritch prayer",
		description = "Count prayer points restored that can be pinned on an eldritch nightmare staff spec",
		section = countersSection
	)
	default boolean countEldritch()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countZcb",
		name = "ZCB damage",
		description = "Count damage dealt that can be pinned on a zaryte crossbow spec",
		section = countersSection
	)
	default boolean countZcb()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countOtherSpecDamage",
		name = "Other spec damage",
		description = "Count damage dealt that can be pinned on any other weapon spec",
		section = countersSection
	)
	default boolean countOtherSpecDamage()
	{
		return false;
	}
}
