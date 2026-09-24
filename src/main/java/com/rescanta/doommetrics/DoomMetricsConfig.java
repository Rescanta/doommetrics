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
	 * The ceiling on a target and the widest delve the overlay is sized for, well clear of the
	 * record.
	 */
	int MAX_DELVE = 1000;

	@ConfigSection(
		name = "Overlay",
		description = "What is drawn over the game while a run is on",
		position = 10
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Pace & target",
		description = "How pace is measured, and the delve you are aiming for",
		position = 20
	)
	String paceSection = "pace";

	@ConfigSection(
		name = "Counters",
		description = "Overlay lines for what your gear gives back",
		position = 30
	)
	String countersSection = "counters";

	@ConfigSection(
		name = "Chat",
		description = "Messages posted to chat during and after a run",
		position = 40,
		closedByDefault = true
	)
	String chatSection = "chat";

	@ConfigSection(
		name = "Advanced",
		description = "Diagnostics",
		position = 100,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "displayStyle",
		name = "Display",
		description = "What the plugin draws over the game while a run is on."
			+ "<br>Panel is the overlay of lines, built from the switches in this section."
			+ "<br>Infobox is a single square holding the one figure picked underneath,"
			+ "<br>with the rest of it in the tooltip."
			+ "<br>Off draws nothing. Delves are still timed and everything is still counted,"
			+ "<br>and the side panel and the chat messages carry on as they were.",
		position = 11,
		section = overlaySection
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
			+ "<br>The counters here are independent of the Counters settings,"
			+ "<br>which choose what the panel draws."
			+ "<br>Time to target counts down to the delve set under Target delve,"
			+ "<br>whether or not Show target delve is switched on."
			+ "<br>Predicted run time is the time from delve 1 to that delve,"
			+ "<br>and the real time once you reach it.",
		position = 12,
		section = overlaySection
	)
	default InfoBoxFigure infoboxFigure()
	{
		return InfoBoxFigure.DELVE;
	}

	@ConfigItem(
		keyName = "hidePluginName",
		name = "Hide plugin name",
		description = "Leave the Doom Metrics title off the top of the overlay.",
		position = 13,
		section = overlaySection
	)
	default boolean hidePluginName()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDelveNumber",
		name = "Show delve number",
		description = "Show the delve you are currently on in the overlay.",
		position = 14,
		section = overlaySection
	)
	default boolean showDelveNumber()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRunTimer",
		name = "Show run timer",
		description = "Show total elapsed time for the current run in the overlay.",
		position = 15,
		section = overlaySection
	)
	default boolean showRunTimer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPace",
		name = "Show pace",
		description = "Show the pace figure in the overlay.",
		position = 16,
		section = overlaySection
	)
	default boolean showPace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resultLingerMinutes",
		name = "Keep result for",
		description = "Minutes the overlay keeps showing a finished run after you die or leave,"
			+ "<br>so the numbers are still there when you get back."
			+ "<br>Set to 0 to hide it straight away."
			+ "<br>Right-click the overlay and pick Clear to dismiss it early.",
		position = 17,
		section = overlaySection
	)
	@Range(min = 0, max = 180)
	default int resultLingerMinutes()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "paceMode",
		name = "Pace",
		description = "Deep pace averages your delve 9+ times."
			+ "<br>Full pace counts deep delves completed per hour of the whole run, shallow delves"
			+ " included.",
		position = 21,
		section = paceSection
	)
	default PaceMode paceMode()
	{
		return PaceMode.DEEP_AVERAGE;
	}

	@ConfigItem(
		keyName = "showTargetDelve",
		name = "Show target delve",
		description = "Show the delve you are aiming for and how long it is predicted to take,"
			+ "<br>in the overlay and the side panel. Prediction picks which times are shown."
			+ "<br>Reaching it is always announced in chat, whatever the chat interval is set to.",
		position = 22,
		section = paceSection
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
		position = 23,
		section = paceSection
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
		position = 24,
		section = paceSection
	)
	default TargetPrediction targetPrediction()
	{
		return TargetPrediction.FULL_RUN;
	}

	@ConfigItem(
		keyName = "healingCounters",
		name = "Healing",
		description = "Overlay lines for the hitpoints healed: blood spells and the ancient godsword"
			+ "<br>and blowpipe specs."
			+ "<br>Total is one line with everything healed, other spells and specs included."
			+ "<br>Each is a line per source. Off draws none.",
		position = 31,
		section = countersSection
	)
	default CounterMode healingCounters()
	{
		return CounterMode.OFF;
	}

	@ConfigItem(
		keyName = "prayerCounters",
		name = "Prayer",
		description = "Overlay lines for the prayer points the eldritch staff spec has restored."
			+ "<br>Total and Each are the same figure; Each draws it as the staff's icon"
			+ "<br>when Counter style is set to icons. Off draws none.",
		position = 32,
		section = countersSection
	)
	default CounterMode prayerCounters()
	{
		return CounterMode.OFF;
	}

	@ConfigItem(
		keyName = "damageCounters",
		name = "Damage",
		description = "Overlay lines for the damage the zaryte crossbow spec dealt, and what your"
			+ "<br>scythe and halberds dealt punishing the boss's prayer, strength-bonus hitsplats"
			+ "<br>and halberd specs included."
			+ "<br>Total is one line with all of it, other specs and melee weapons included."
			+ "<br>Each is a line per weapon. Off draws none.",
		position = 33,
		section = countersSection
	)
	default CounterMode damageCounters()
	{
		return CounterMode.OFF;
	}

	@ConfigItem(
		keyName = "counterStyle",
		name = "Counter style",
		description = "How each counter's overlay line is led."
			+ "<br>Names spells out what it counts. Icons draws the weapon or spell in its place,"
			+ "<br>and Icon grid fits two of those to a line."
			+ "<br>A Total line keeps its name and a line of its own, since it has no one icon."
			+ "<br>The infobox takes the icon when it holds a single counter and this is not Names."
			+ "<br>The side panel and the run detail window always list counters by icon.",
		position = 34,
		section = countersSection
	)
	default CounterStyle counterStyle()
	{
		return CounterStyle.NAMES;
	}

	@ConfigItem(
		keyName = "hideEmptyCounters",
		name = "Hide counters at 0",
		description = "Leave a counter off the overlay and the side panel until it has counted"
			+ "<br>something, so Each only draws the gear you are actually using."
			+ "<br>A line appears when its counter first counts."
			+ "<br>The side panel has a Show all link under its counters to see the rest."
			+ "<br>The run detail window starts those counters switched off,"
			+ "<br>so their flat lines are not on the chart. Click one there to put it back."
			+ "<br>Untick to draw every counter from the start, greyed at 0.",
		position = 35,
		section = countersSection
	)
	default boolean hideEmptyCounters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "foldedCombatGroups",
		name = "",
		description = "The side panel's combat headings folded down to their totals.",
		hidden = true
	)
	default String foldedCombatGroups()
	{
		return "";
	}

	@ConfigItem(
		keyName = "foldedCombatGroups",
		name = "",
		description = ""
	)
	void foldedCombatGroups(String groups);

	@ConfigItem(
		keyName = "foldedDetailGroups",
		name = "",
		description = "The run detail window's counter headings folded down to their totals.",
		hidden = true
	)
	default String foldedDetailGroups()
	{
		return "";
	}

	@ConfigItem(
		keyName = "foldedDetailGroups",
		name = "",
		description = ""
	)
	void foldedDetailGroups(String groups);

	@ConfigItem(
		keyName = "chatIntervalDelves",
		name = "Chat every N delves",
		description = "Post elapsed time and pace to chat whenever the delve number is a multiple"
			+ " of this."
			+ "<br>Shallow delves are skipped, so 5 reports at delve 10, 15, 20 and so on."
			+ "<br>Set to 0 to turn the messages off.",
		position = 41,
		section = chatSection
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
		position = 42,
		section = chatSection
	)
	default boolean announceRunEnd()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug logging",
		description = "Log delve transitions, Doom varplayer changes and what each counter was"
			+ "<br>credited, at debug level."
			+ "<br>Useful for reporting a problem; leave it off otherwise.",
		position = 101,
		section = advancedSection
	)
	default boolean debugLogging()
	{
		return false;
	}
}
