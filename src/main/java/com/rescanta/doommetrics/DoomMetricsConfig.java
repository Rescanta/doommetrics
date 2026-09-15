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
		description = "Deep pace averages your delve 9+ times."
			+ "<br>Full pace counts deep delves completed per hour of the whole run, shallow delves"
			+ " included.",
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
			+ " of this."
			+ "<br>Shallow delves are skipped, so 5 reports at delve 10, 15, 20 and so on."
			+ "<br>Set to 0 to turn the messages off.",
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
		keyName = "hidePluginName",
		name = "Hide plugin name",
		description = "Leave the Doom Metrics title off the top of the overlay.",
		position = 7
	)
	default boolean hidePluginName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "displayStyle",
		name = "Display",
		description = "What the plugin draws over the game while a run is on."
			+ "<br>Panel is the overlay of lines, built from the switches below."
			+ "<br>Infobox is a single square holding the one figure picked underneath,"
			+ "<br>with the rest of it in the tooltip."
			+ "<br>Off draws nothing. Delves are still timed and everything is still counted,"
			+ "<br>and the side panel and the chat messages carry on as they were.",
		position = 8
	)
	default DisplayStyle displayStyle()
	{
		return DisplayStyle.PANEL;
	}

	@ConfigItem(
		keyName = "infoboxFigure",
		name = "Infobox figure",
		description = "Which single figure the infobox square holds."
			+ "<br>Only used when Display is set to Infobox."
			+ "<br>The counters here are independent of the counter checkboxes below,"
			+ "<br>which choose what the panel draws."
			+ "<br>Time to target counts down to the delve set under Target delve,"
			+ "<br>whether or not Show target delve is switched on."
			+ "<br>Predicted run time is the time from delve 1 to that delve,"
			+ "<br>and the real time once you reach it.",
		position = 9
	)
	default InfoBoxFigure infoboxFigure()
	{
		return InfoBoxFigure.DELVE;
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
		description = "Show the delve you are aiming for and how long it is predicted to take,"
			+ "<br>in the overlay and the side panel. Prediction picks which times are shown."
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
		description = "The delve to aim for."
			+ "<br>The predicted time is what your delve 9+ average says the delves between here"
			+ "<br>and there will take, so it appears once this run has cleared a delve 9."
			+ "<br>Delves get slower the deeper they go, so a distant target reads short.",
		position = 15
	)
	@Range(min = 10, max = MAX_DELVE)
	default int targetDelve()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "targetPrediction",
		name = "Prediction",
		description = "Which predicted times the target rows show."
			+ "<br>Remaining is To go, the time from now to the target delve."
			+ "<br>Full run is Total, the time from delve 1 to the target delve."
			+ "<br>Both shows the two."
			+ "<br>Once the target is reached, To go is dropped and the Target row says so.",
		position = 16
	)
	default TargetPrediction targetPrediction()
	{
		return TargetPrediction.FULL_RUN;
	}

	@ConfigItem(
		keyName = "resultLingerMinutes",
		name = "Keep result for",
		description = "Minutes the overlay keeps showing a finished run after you die or leave,"
			+ "<br>so the numbers are still there when you get back."
			+ "<br>Set to 0 to hide it straight away."
			+ "<br>Right-click the overlay and pick Clear to dismiss it early.",
		position = 13
	)
	@Range(min = 0, max = 180)
	default int resultLingerMinutes()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "counterIcons",
		name = "Icons for counters",
		description = "Draw each counter as the icon of what it counts - the Zaryte crossbow,"
			+ "<br>the blood barrage spell - in place of its name on the overlay."
			+ "<br>The side panel and the run detail window always list counters by icon;"
			+ "<br>hover a row there for its name."
			+ "<br>The infobox takes the same icon when it holds a single counter."
			+ "<br>Counters grouped into one line keep their heading, since a group has no one icon.",
		position = 50,
		section = countersSection
	)
	default boolean counterIcons()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideEmptyCounters",
		name = "Hide counters at 0",
		description = "Leave a ticked counter off the overlay until it has counted something,"
			+ "<br>so you can tick everything your gear might use and only see what is firing."
			+ "<br>The overlay grows a line when a counter first counts,"
			+ "<br>and a combined line appears once anything under it has."
			+ "<br>The run detail window starts those counters switched off,"
			+ "<br>so their flat lines are not on the chart. Click one there to put it back."
			+ "<br>Untick to draw every counter from the start, greyed at 0.",
		position = 51,
		section = countersSection
	)
	default boolean hideEmptyCounters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "metricGrouping",
		name = "Group counters",
		description = "How the counters ticked below are drawn."
			+ "<br>Combined sums them into one line per heading,"
			+ "<br>so ticking the ancient godsword and the blowpipe gives a single Spec healing figure."
			+ "<br>Separate gives each its own line.",
		position = 52,
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
		position = 53,
		section = countersSection
	)
	default boolean showBloodBarrage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showAgsHeal",
		name = "AGS heal",
		description = "Count the hitpoints the ancient godsword spec has healed you for.",
		position = 55,
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
		position = 56,
		section = countersSection
	)
	default boolean showBpHeal()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showEldritchPrayer",
		name = "Eldritch prayer",
		description = "Count the prayer points the eldritch staff spec has restored.",
		position = 58,
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
		position = 59,
		section = countersSection
	)
	default boolean showZcbDamage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showScythePunish",
		name = "Scythe punish",
		description = "Count the damage your scythe has dealt punishing the boss's prayer, its"
			+ " strength-bonus hitsplats included.",
		position = 61,
		section = countersSection
	)
	default boolean showScythePunish()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showNoxiousHalberdPunish",
		name = "Noxious halberd punish",
		description = "Count the damage your noxious halberd has dealt punishing the boss's prayer,"
			+ " its spec and strength-bonus hitsplats included.",
		position = 62,
		section = countersSection
	)
	default boolean showNoxiousHalberdPunish()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCrystalHalberdPunish",
		name = "Crystal halberd punish",
		description = "Count the damage your crystal halberd has dealt punishing the boss's prayer,"
			+ " its spec and strength-bonus hitsplats included.",
		position = 63,
		section = countersSection
	)
	default boolean showCrystalHalberdPunish()
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
