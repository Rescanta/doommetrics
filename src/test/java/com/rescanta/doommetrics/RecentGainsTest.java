package com.rescanta.doommetrics;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RecentGainsTest
{
	private static final Instant AT = Instant.parse("2026-09-10T20:39:00Z");

	private final RecentGains gains = new RecentGains();

	@Test
	public void nothingIsOnShowBeforeAnythingIsGained()
	{
		assertEquals(0, gains.get(CombatMetric.SCYTHE_PUNISH, AT));
	}

	/** A punish's swing and its bonus splats a tick behind read as one gain, not two. */
	@Test
	public void gainsLandingTogetherAreShownAsOne()
	{
		gains.add(CombatMetric.SCYTHE_PUNISH, 30, AT);
		gains.add(CombatMetric.SCYTHE_PUNISH, 67, AT.plusMillis(DoomFormat.TICK_MILLIS));

		assertEquals(97, gains.get(CombatMetric.SCYTHE_PUNISH, AT.plusSeconds(1)));
	}

	/** Shown for five ticks after the last amount added, not after the first. */
	@Test
	public void eachGainStartsTheClockOver()
	{
		Instant last = AT.plusMillis(DoomFormat.TICK_MILLIS);

		gains.add(CombatMetric.SCYTHE_PUNISH, 30, AT);
		gains.add(CombatMetric.SCYTHE_PUNISH, 67, last);

		assertEquals(97, gains.get(CombatMetric.SCYTHE_PUNISH,
			last.plus(RecentGains.SHOWN_FOR).minusMillis(1)));
		assertEquals(0, gains.get(CombatMetric.SCYTHE_PUNISH, last.plus(RecentGains.SHOWN_FOR)));
	}

	/** Once one gain has run its time, the next starts from nothing rather than adding to it. */
	@Test
	public void aGainAfterTheLastHasRunItsTimeStandsAlone()
	{
		gains.add(CombatMetric.SCYTHE_PUNISH, 97, AT);

		Instant later = AT.plusSeconds(20);
		gains.add(CombatMetric.SCYTHE_PUNISH, 41, later);

		assertEquals(41, gains.get(CombatMetric.SCYTHE_PUNISH, later));
	}

	@Test
	public void eachCounterKeepsItsOwnGain()
	{
		gains.add(CombatMetric.SCYTHE_PUNISH, 97, AT);
		gains.add(CombatMetric.ZCB_DAMAGE, 44, AT);

		assertEquals(97, gains.get(CombatMetric.SCYTHE_PUNISH, AT));
		assertEquals(44, gains.get(CombatMetric.ZCB_DAMAGE, AT));
		assertEquals(0, gains.get(CombatMetric.AGS_HEAL, AT));
	}

	@Test
	public void nothingAndLessAreIgnored()
	{
		gains.add(CombatMetric.SCYTHE_PUNISH, 0, AT);
		gains.add(CombatMetric.SCYTHE_PUNISH, -5, AT);

		assertEquals(0, gains.get(CombatMetric.SCYTHE_PUNISH, AT));
	}
}
