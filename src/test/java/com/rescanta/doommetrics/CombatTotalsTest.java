package com.rescanta.doommetrics;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CombatTotalsTest
{
	@Test
	public void anEmptyTallyReadsZeroForEveryMetric()
	{
		CombatTotals totals = new CombatTotals();

		assertTrue(totals.isEmpty());

		for (CombatMetric metric : CombatMetric.values())
		{
			assertEquals(0, totals.get(metric));
		}
	}

	@Test
	public void addingAccumulates()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.ZCB_DAMAGE, 40);
		totals.add(CombatMetric.ZCB_DAMAGE, 62);

		assertEquals(102, totals.get(CombatMetric.ZCB_DAMAGE));
		assertFalse(totals.isEmpty());
	}

	/** Nothing that would make a total read lower than the runs behind it may enter the map. */
	@Test
	public void nonPositiveAmountsAreNotStored()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.AGS_HEAL, 0);
		totals.add(CombatMetric.AGS_HEAL, -5);

		assertTrue(totals.isEmpty());
	}

	@Test
	public void plusLeavesBothOperandsAlone()
	{
		CombatTotals session = new CombatTotals();
		session.add(CombatMetric.BLOOD_BARRAGE_HEAL, 300);

		CombatTotals run = new CombatTotals();
		run.add(CombatMetric.BLOOD_BARRAGE_HEAL, 40);
		run.add(CombatMetric.ELDRITCH_PRAYER, 24);

		CombatTotals sum = session.plus(run);

		assertEquals(340, sum.get(CombatMetric.BLOOD_BARRAGE_HEAL));
		assertEquals(24, sum.get(CombatMetric.ELDRITCH_PRAYER));
		assertEquals(300, session.get(CombatMetric.BLOOD_BARRAGE_HEAL));
		assertEquals(0, session.get(CombatMetric.ELDRITCH_PRAYER));
		assertEquals(40, run.get(CombatMetric.BLOOD_BARRAGE_HEAL));
	}

	@Test
	public void addingNothingIsSafe()
	{
		CombatTotals totals = new CombatTotals();
		totals.addAll(null);
		totals.addAll(new CombatTotals());

		assertTrue(totals.isEmpty());
	}

	@Test
	public void aCopyDoesNotShareTheMapItWasMadeFrom()
	{
		CombatTotals original = new CombatTotals();
		original.add(CombatMetric.OTHER_SPEC_DAMAGE, 90);

		CombatTotals copy = original.copy();
		original.add(CombatMetric.OTHER_SPEC_DAMAGE, 10);

		assertEquals(90, copy.get(CombatMetric.OTHER_SPEC_DAMAGE));
		assertEquals(100, original.get(CombatMetric.OTHER_SPEC_DAMAGE));
	}

	/**
	 * A key this version does not know is either a metric a newer one added or one this version
	 * retired. Keeping the number and not showing it is honest; dropping it on the next write is
	 * not.
	 */
	@Test
	public void anUnknownKeySurvivesBeingSummed()
	{
		CombatTotals stored = new CombatTotals();
		stored.m = new LinkedHashMap<>();
		stored.m.put("somethingLaterAdded", 77L);

		CombatTotals lifetime = new CombatTotals();
		lifetime.addAll(stored);

		assertEquals(Long.valueOf(77), lifetime.m.get("somethingLaterAdded"));
		assertFalse(lifetime.isEmpty());
	}

	@Test
	public void sanitisingDropsNegativesAndKeepsTheRest()
	{
		CombatTotals stored = new CombatTotals();
		stored.m = new LinkedHashMap<>();
		stored.m.put(CombatMetric.ZCB_DAMAGE.key(), -1L);
		stored.m.put(CombatMetric.AGS_HEAL.key(), 40L);

		stored.sanitise();

		assertEquals(0, stored.get(CombatMetric.ZCB_DAMAGE));
		assertEquals(40, stored.get(CombatMetric.AGS_HEAL));
	}

	@Test
	public void sanitisingAValueWithNoMapAtAllLeavesSomethingUsable()
	{
		CombatTotals stored = new CombatTotals();
		stored.m = null;

		stored.sanitise();

		assertTrue(stored.isEmpty());
		assertEquals(0, stored.get(CombatMetric.ZCB_DAMAGE));
	}

	/** The stored names are the file format, so a clash or a rename has to fail loudly here. */
	@Test
	public void everyMetricKeyIsDistinctAndRoundTrips()
	{
		Set<String> keys = new HashSet<>();

		for (CombatMetric metric : CombatMetric.values())
		{
			assertTrue("duplicate key " + metric.key(), keys.add(metric.key()));
			assertEquals(metric, CombatMetric.byKey(metric.key()));
		}

		assertNull(CombatMetric.byKey("nothingStoredUnderThis"));
	}

	/** Two metrics under one heading may share a label, so the dropdown uses the qualified one. */
	@Test
	public void qualifiedLabelsAreDistinct()
	{
		Set<String> labels = new HashSet<>();

		for (CombatMetric metric : CombatMetric.values())
		{
			assertTrue("duplicate label " + metric.qualifiedLabel(),
				labels.add(metric.qualifiedLabel()));
		}
	}

	/** The overlay draws these with no heading over them, so each has to stand on its own. */
	@Test
	public void overlayLabelsAreDistinct()
	{
		Set<String> labels = new HashSet<>();

		for (CombatMetric metric : CombatMetric.values())
		{
			assertTrue("duplicate overlay label " + metric.overlayLabel(),
				labels.add(metric.overlayLabel()));
		}
	}

	/** The panel builds its headings by watching the group change, so groups must not interleave. */
	@Test
	public void metricsAreDeclaredGroupedTogether()
	{
		Set<CombatMetric.Group> seen = new HashSet<>();
		CombatMetric.Group current = null;

		for (CombatMetric metric : CombatMetric.values())
		{
			if (metric.group() != current)
			{
				current = metric.group();
				assertTrue("group " + current + " is split up", seen.add(current));
			}
		}
	}

	/**
	 * The config value is the stored format for a whole character's lifetime, so what survives a
	 * write and a read back is pinned here.
	 */
	@Test
	public void theLifetimeValueSurvivesTheRoundTrip()
	{
		TotalsStore store = new TotalsStore(null, new Gson());

		CombatTotals original = new CombatTotals();
		original.add(CombatMetric.BLOWPIPE_HEAL, 8_400);
		original.add(CombatMetric.OTHER_SPEC_DAMAGE, 1_204_318);

		CombatTotals restored = store.decodeCombat(new Gson().toJson(original));

		assertEquals(CombatTotals.VERSION, restored.v);
		assertEquals(8_400, restored.get(CombatMetric.BLOWPIPE_HEAL));
		assertEquals(1_204_318, restored.get(CombatMetric.OTHER_SPEC_DAMAGE));
	}

	@Test
	public void nothingStoredAndNonsenseStoredBothReadAsNothing()
	{
		TotalsStore store = new TotalsStore(null, new Gson());

		assertNull(store.decodeCombat(null));
		assertNull(store.decodeCombat(""));
		assertNull(store.decodeCombat("{"));
	}

	/** A negative lifetime is not one anybody earned, so it goes rather than being carried on. */
	@Test
	public void aNegativeStoredAmountIsDiscardedButTheRestIsKept()
	{
		TotalsStore store = new TotalsStore(null, new Gson());

		CombatTotals restored = store.decodeCombat(
			"{\"v\":1,\"m\":{\"zcbDamage\":-5,\"agsHeal\":40}}");

		assertEquals(0, restored.get(CombatMetric.ZCB_DAMAGE));
		assertEquals(40, restored.get(CombatMetric.AGS_HEAL));
	}

	@Test
	public void theChartOffersDepthAndEveryMetric()
	{
		List<ChartOption> options = ChartOption.all();

		assertEquals(CombatMetric.values().length + 1, options.size());
		assertNull("depth comes first", options.get(0).metric());
	}

	@Test
	public void aRunWithNoRecordedCombatPlotsAsZeroRatherThanAGap()
	{
		RunRecord older = new RunRecord();
		older.delve = 32;

		RunRecord newer = new RunRecord();
		newer.delve = 41;
		newer.combat = new CombatTotals();
		newer.combat.add(CombatMetric.ZCB_DAMAGE, 1200);

		RunSeries series = RunSeries.of(Arrays.asList(older, newer));

		assertEquals(2, series.size());
		assertEquals(Arrays.asList(32, 41),
			series.seriesFor(ChartOption.deepestDelve()).values());
		assertEquals(Arrays.asList(0, 1200), zcb(series));
	}

	@Test
	public void plusAppendsWithoutTouchingTheOriginal()
	{
		RunRecord first = new RunRecord();
		first.delve = 20;

		RunSeries series = RunSeries.of(Collections.singletonList(first));

		RunRecord second = new RunRecord();
		second.delve = 25;
		second.combat = new CombatTotals();
		second.combat.add(CombatMetric.ZCB_DAMAGE, 900);

		RunSeries grown = series.plus(second);

		assertEquals(1, series.size());
		assertEquals(2, grown.size());
		assertEquals(Arrays.asList(0, 900), zcb(grown));
	}

	private static List<Integer> zcb(RunSeries series)
	{
		for (ChartOption option : ChartOption.all())
		{
			if (option.metric() == CombatMetric.ZCB_DAMAGE)
			{
				return series.seriesFor(option).values();
			}
		}

		throw new AssertionError("no chart option for the Zaryte crossbow");
	}
}
