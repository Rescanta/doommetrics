package com.rescanta.doommetrics;

import java.util.EnumMap;
import java.util.Map;

/**
 * A config the preview harness can turn knobs on, standing in for the one RuneLite proxies out of
 * the settings panel.
 *
 * <p>Only the options that change what a widget looks like are held here. Everything else falls
 * through to the interface's own defaults, so an option added to the config and not added here
 * still previews at whatever a fresh install would show rather than failing to compile.
 */
class PreviewConfig implements DoomMetricsConfig
{
	private final Map<CombatMetric.Group, CounterMode> modes =
		new EnumMap<>(CombatMetric.Group.class);

	DisplayStyle displayStyle = DisplayStyle.PANEL;
	InfoBoxFigure infoboxFigure = InfoBoxFigure.DELVE;
	PaceMode paceMode = PaceMode.DEEP_AVERAGE;
	boolean hidePluginName = false;
	boolean showDelveNumber = true;
	boolean showRunTimer = true;
	boolean showPace = true;
	boolean showTargetDelve = false;
	CounterStyle counterStyle = CounterStyle.NAMES;
	boolean hideEmptyCounters = true;
	int targetDelve = 50;
	TargetPrediction targetPrediction = TargetPrediction.FULL_RUN;

	/** What the side panel was last told to keep folded, for a test to read back. */
	String foldedCombatGroups = "";

	/** The same for the run detail window's legend. */
	String foldedDetailGroups = "";

	PreviewConfig()
	{
		allModes(CounterMode.EACH);
	}

	/** How one heading is drawn. Keyed by group so a control can be built per heading. */
	CounterMode mode(CombatMetric.Group group)
	{
		return modes.get(group);
	}

	void mode(CombatMetric.Group group, CounterMode mode)
	{
		modes.put(group, mode);
	}

	/** Sets every heading at once, for the states worth looking at as a whole. */
	void allModes(CounterMode mode)
	{
		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			modes.put(group, mode);
		}
	}

	/** Takes on another config's settings, so a scene can be loaded into the live one. */
	void adopt(PreviewConfig other)
	{
		displayStyle = other.displayStyle;
		infoboxFigure = other.infoboxFigure;
		paceMode = other.paceMode;
		hidePluginName = other.hidePluginName;
		showDelveNumber = other.showDelveNumber;
		showRunTimer = other.showRunTimer;
		showPace = other.showPace;
		showTargetDelve = other.showTargetDelve;
		targetDelve = other.targetDelve;
		targetPrediction = other.targetPrediction;
		counterStyle = other.counterStyle;
		hideEmptyCounters = other.hideEmptyCounters;
		modes.putAll(other.modes);
	}

	@Override
	public DisplayStyle displayStyle()
	{
		return displayStyle;
	}

	@Override
	public InfoBoxFigure infoboxFigure()
	{
		return infoboxFigure;
	}

	@Override
	public PaceMode paceMode()
	{
		return paceMode;
	}

	@Override
	public boolean hidePluginName()
	{
		return hidePluginName;
	}

	@Override
	public boolean showDelveNumber()
	{
		return showDelveNumber;
	}

	@Override
	public boolean showRunTimer()
	{
		return showRunTimer;
	}

	@Override
	public boolean showPace()
	{
		return showPace;
	}

	@Override
	public boolean showTargetDelve()
	{
		return showTargetDelve;
	}

	@Override
	public int targetDelve()
	{
		return targetDelve;
	}

	@Override
	public TargetPrediction targetPrediction()
	{
		return targetPrediction;
	}

	@Override
	public CounterMode healingCounters()
	{
		return mode(CombatMetric.Group.HEALING);
	}

	@Override
	public CounterMode prayerCounters()
	{
		return mode(CombatMetric.Group.PRAYER);
	}

	@Override
	public CounterMode damageCounters()
	{
		return mode(CombatMetric.Group.DAMAGE);
	}

	@Override
	public CounterStyle counterStyle()
	{
		return counterStyle;
	}

	@Override
	public boolean hideEmptyCounters()
	{
		return hideEmptyCounters;
	}

	@Override
	public String foldedCombatGroups()
	{
		return foldedCombatGroups;
	}

	@Override
	public void foldedCombatGroups(String groups)
	{
		foldedCombatGroups = groups;
	}

	@Override
	public String foldedDetailGroups()
	{
		return foldedDetailGroups;
	}

	@Override
	public void foldedDetailGroups(String groups)
	{
		foldedDetailGroups = groups;
	}
}
