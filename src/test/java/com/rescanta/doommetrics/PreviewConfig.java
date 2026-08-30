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
	private final Map<CombatMetric, Boolean> counters = new EnumMap<>(CombatMetric.class);

	PaceMode paceMode = PaceMode.DEEP_AVERAGE;
	MetricDisplay grouping = MetricDisplay.SEPARATE;
	boolean showDelveNumber = true;
	boolean showRunTimer = true;
	boolean showPace = true;

	PreviewConfig()
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			counters.put(metric, true);
		}
	}

	/** Whether one counter is ticked on. Keyed by metric so a control can be built per metric. */
	boolean counter(CombatMetric metric)
	{
		return Boolean.TRUE.equals(counters.get(metric));
	}

	void counter(CombatMetric metric, boolean shown)
	{
		counters.put(metric, shown);
	}

	/** Ticks every counter on or off at once, for the two states worth looking at as a whole. */
	void allCounters(boolean shown)
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			counters.put(metric, shown);
		}
	}

	/** Takes on another config's settings, so a scene can be loaded into the live one. */
	void adopt(PreviewConfig other)
	{
		paceMode = other.paceMode;
		grouping = other.grouping;
		showDelveNumber = other.showDelveNumber;
		showRunTimer = other.showRunTimer;
		showPace = other.showPace;

		for (CombatMetric metric : CombatMetric.values())
		{
			counter(metric, other.counter(metric));
		}
	}

	@Override
	public PaceMode paceMode()
	{
		return paceMode;
	}

	@Override
	public MetricDisplay metricGrouping()
	{
		return grouping;
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
	public boolean showBloodBarrage()
	{
		return counter(CombatMetric.BLOOD_BARRAGE_HEAL);
	}

	@Override
	public boolean showOtherSpell()
	{
		return counter(CombatMetric.OTHER_SPELL_HEAL);
	}

	@Override
	public boolean showAgsHeal()
	{
		return counter(CombatMetric.AGS_HEAL);
	}

	@Override
	public boolean showBpHeal()
	{
		return counter(CombatMetric.BLOWPIPE_HEAL);
	}

	@Override
	public boolean showOtherSpecHeal()
	{
		return counter(CombatMetric.OTHER_SPEC_HEAL);
	}

	@Override
	public boolean showEldritchPrayer()
	{
		return counter(CombatMetric.ELDRITCH_PRAYER);
	}

	@Override
	public boolean showZcbDamage()
	{
		return counter(CombatMetric.ZCB_DAMAGE);
	}

	@Override
	public boolean showOtherSpecDamage()
	{
		return counter(CombatMetric.OTHER_SPEC_DAMAGE);
	}
}
