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
		description = "What shows over the game during a run",
		position = 10
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Pace & target",
		description = "How pace is worked out, and the delve you're aiming for",
		position = 20
	)
	String paceSection = "pace";

	@ConfigSection(
		name = "Counters",
		description = "Overlay lines for healing, prayer and damage",
		position = 30
	)
	String countersSection = "counters";

	@ConfigSection(
		name = "Chat",
		description = "Chat messages during and after a run",
		position = 40,
		closedByDefault = true
	)
	String chatSection = "chat";

	@ConfigSection(
		name = "Advanced",
		description = "Troubleshooting",
		position = 100,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "displayStyle",
		name = "Display",
		description = "How your run is shown over the game."
			+ "<br>Overlay: a box of lines, picked with the settings in this section."
			+ "<br>Infobox: one square showing the Infobox figure. Hover it for details."
			+ "<br>Off: nothing over the game. Delves are still timed and counted,"
			+ "<br>and the side panel and chat messages still work.",
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
		description = "The figure the infobox shows. Only used when Display is Infobox."
			+ "<br>Any counter can be picked here; the Counters section only affects the overlay."
			+ "<br>Time to target counts down to your Target delve,"
			+ "<br>even if Show target delve is off."
			+ "<br>Predicted run time is the time from delve 1 to your Target delve,"
			+ "<br>and the actual time once you get there.",
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
		description = "Leave the Doom Metrics title off the overlay.",
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
		description = "Show which delve you're on.",
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
		description = "Show how long the current run has taken.",
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
		description = "Show the run's pace. The Pace setting picks which one.",
		position = 16,
		section = overlaySection
	)
	default boolean showPace()
	{
		return true;
	}

	@ConfigItem(
		keyName = "resultLingerMinutes",
		name = "Show ended run for",
		description = "Minutes a run stays on screen after you die or leave, so you can still"
			+ "<br>read it afterwards. Covers the overlay, the infobox and the side panel's"
			+ "<br>Current run card. Session and lifetime figures don't depend on it."
			+ "<br>0 hides the run as soon as it ends."
			+ "<br>To hide it sooner, right-click the overlay and pick Clear.",
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
		description = "Which pace the run shows."
			+ "<br>Deep pace: delves per hour at your average delve 9+ time."
			+ "<br>Full pace: deep delves (8+) cleared per hour of the whole run,"
			+ "<br>counting the time spent on delves 1-7 and restocking."
			+ "<br>An ended run always shows Full pace, and so do the side panel's"
			+ "<br>session and lifetime pace.",
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
		description = "Show your target delve and how long it should take to get there,"
			+ "<br>on the overlay and in the side panel. Prediction picks which times."
			+ "<br>Reaching the target is always announced in chat,"
			+ "<br>even when Chat every N delves is 0.",
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
		description = "The delve you're aiming for."
			+ "<br>Predictions use this run's average delve 9+ time, so they appear"
			+ "<br>once you clear delve 9. Deeper delves take longer, so the further away"
			+ "<br>the target, the more the prediction undershoots."
			+ "<br>The side panel's Resets card uses this, rounded down to a multiple of 10.",
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
		description = "Which predicted times to show for the target."
			+ "<br>Remaining: To go, the time from now until you reach it."
			+ "<br>Full run: Total, the time from delve 1 to the target."
			+ "<br>Both: the two of them."
			+ "<br>Once you reach the target, To go disappears and Target reads Target reached.",
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
		description = "Overlay lines for hitpoints healed by blood spells and by ancient godsword,"
			+ "<br>blowpipe and Saradomin godsword specs."
			+ "<br>Total: one line for all healing, other spells and specs included."
			+ "<br>Each: a line per source."
			+ "<br>Off: no lines.",
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
		description = "Overlay lines for prayer points restored by eldritch staff and"
			+ "<br>Saradomin godsword specs."
			+ "<br>Total: one line for both."
			+ "<br>Each: a line per weapon."
			+ "<br>Off: no lines.",
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
		description = "Overlay lines for zaryte crossbow spec damage, and for scythe and halberd"
			+ "<br>damage that punishes the boss's prayer, including strength-bonus hitsplats"
			+ "<br>and halberd specs."
			+ "<br>Total: one line for all of it, other specs and melee weapons included."
			+ "<br>Each: a line per weapon."
			+ "<br>Off: no lines.",
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
		description = "How counters are labelled on the overlay."
			+ "<br>Names: written out."
			+ "<br>Icons: the weapon or spell icon instead of the name."
			+ "<br>Icon grid: icons, two counters to a line."
			+ "<br>Total lines always keep their name and a line of their own."
			+ "<br>The infobox shows a counter's icon unless this is Names."
			+ "<br>The side panel and run detail window always show icons.",
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
		description = "Hide a counter until it counts something, so Each only shows"
			+ "<br>the gear you're actually using."
			+ "<br>The side panel has a Show all link under its counters for the rest."
			+ "<br>The run detail window starts them hidden from the chart;"
			+ "<br>click one in its list to show it."
			+ "<br>Turn off to show every counter from the start, greyed out at 0.",
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
		description = "Post the run time and pace to chat every N delves, from delve 8 on."
			+ "<br>For example, 5 posts at delves 10, 15, 20 and so on."
			+ "<br>0 turns these messages off.",
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
		description = "Post a summary to chat when you claim the loot, leave or die.",
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
		description = "Write delve changes, Doom varplayer changes and what each counter"
			+ "<br>was credited to the client log, at debug level."
			+ "<br>Useful when reporting a problem. Otherwise leave it off.",
		position = 101,
		section = advancedSection
	)
	default boolean debugLogging()
	{
		return false;
	}
}
