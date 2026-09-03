package com.rescanta.doommetrics;

/**
 * Which single figure the infobox square holds.
 *
 * <p>A square holds one number, so this covers everything the square can show: the delve, the
 * run timer, the pace, the time left to the target delve, any one of the eight counters, or any
 * one of the four counter group totals with its ticked sources summed. Counter entries use the
 * qualified names (effect as well as source) because "Other specs" appears under both Spec
 * healing and Spec damage.
 */
public enum InfoboxFigure
{
	DELVE("Delve"),
	TIME("Time"),
	PACE("Pace"),
	TIME_TO_TARGET("Time to target"),
	BLOOD_BARRAGE_HEAL("Blood barrage heal"),
	OTHER_SPELL_HEAL("Other spell heal"),
	AGS_HEAL("AGS heal"),
	BLOWPIPE_HEAL("Blowpipe heal"),
	OTHER_SPEC_HEAL("Other spec heal"),
	ELDRITCH_PRAYER("Eldritch prayer"),
	ZCB_DAMAGE("ZCB damage"),
	OTHER_SPEC_DAMAGE("Other spec damage"),
	SPELL_HEALING_TOTAL("Spell healing total"),
	SPEC_HEALING_TOTAL("Spec healing total"),
	PRAYER_TOTAL("Prayer total"),
	SPEC_DAMAGE_TOTAL("Spec damage total");

	private final String label;

	InfoboxFigure(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
