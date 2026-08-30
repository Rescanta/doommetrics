package com.rescanta.doommetrics;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The file format is what has to survive a plugin update, so it is pinned here. Only
 * {@link RunHistoryStore#encode} and {@link RunHistoryStore#decode} are exercised - neither touches
 * the executor or the config manager, so the store can be built without either.
 */
public class RunHistoryStoreTest
{
	private final RunHistoryStore store = new RunHistoryStore(new Gson(), null, null);

	private static RunRecord record(int delve, EndReason end)
	{
		RunRecord record = new RunRecord();
		record.at = 1_756_339_200L;
		record.delve = delve;
		record.ticks = 4210;
		record.end = end;
		record.loot = Collections.emptyList();
		return record;
	}

	@Test
	public void aRunSurvivesTheRoundTrip()
	{
		RunRecord original = record(83, EndReason.DIED);
		original.diedOn = 84;
		original.partial = true;
		original.loot = Arrays.asList("Eye of ayak", "Mokhaiotl cloth");

		RunRecord restored = store.decode(store.encode(original));

		assertNotNull(restored);
		assertEquals(RunRecord.VERSION, restored.v);
		assertEquals(1_756_339_200L, restored.at);
		assertEquals(83, restored.delve);
		assertEquals(4210, restored.ticks);
		assertEquals(EndReason.DIED, restored.end);
		assertEquals(84, restored.diedOn);
		assertTrue(restored.partial);
		assertEquals(Arrays.asList("Eye of ayak", "Mokhaiotl cloth"), restored.loot);
	}

	@Test
	public void aRunsCombatFiguresSurviveTheRoundTrip()
	{
		RunRecord original = record(60, EndReason.FINISHED);
		original.combat = new CombatTotals();
		original.combat.add(CombatMetric.BLOOD_BARRAGE_HEAL, 412);
		original.combat.add(CombatMetric.ZCB_DAMAGE, 1804);

		RunRecord restored = store.decode(store.encode(original));

		assertNotNull(restored);
		assertNotNull(restored.combat);
		assertEquals(CombatTotals.VERSION, restored.combat.v);
		assertEquals(412, restored.combat.get(CombatMetric.BLOOD_BARRAGE_HEAL));
		assertEquals(1804, restored.combat.get(CombatMetric.ZCB_DAMAGE));

		// A metric that never fired takes no space in the line at all.
		assertEquals(0, restored.combat.get(CombatMetric.AGS_HEAL));
		assertFalse(store.encode(original).contains(CombatMetric.AGS_HEAL.key()));
	}

	/**
	 * Runs written before combat tracking existed have no such block, and reading one back must
	 * leave the rest of the record intact rather than failing the line.
	 */
	@Test
	public void aRunFromBeforeCombatWasTrackedStillReads()
	{
		RunRecord restored = store.decode(
			"{\"v\":1,\"at\":1756339200,\"delve\":42,\"ticks\":4210,\"end\":\"FINISHED\"}");

		assertNotNull(restored);
		assertEquals(42, restored.delve);
		assertNull(restored.combat);
	}

	/** One record per line is the whole contract, so an encoded record must not contain one. */
	@Test
	public void anEncodedRunIsASingleLine()
	{
		assertFalse(store.encode(record(40, EndReason.FINISHED)).contains("\n"));
	}

	/** A run that cleared nothing still happened, and the chart is entitled to show it. */
	@Test
	public void aRunThatClearedNothingIsKept()
	{
		RunRecord restored = store.decode(store.encode(record(0, EndReason.FINISHED)));

		assertNotNull(restored);
		assertEquals(0, restored.delve);
	}

	@Test
	public void unreadableLinesAreDropped()
	{
		assertNull(store.decode(null));
		assertNull(store.decode(""));
		assertNull(store.decode("   "));
		assertNull(store.decode("{\"delve\":"));
		assertNull(store.decode("not json at all"));
		assertNull(store.decode("{\"delve\":-4}"));
	}

	/**
	 * A record written before a field existed must still read, so that adding one does not throw
	 * away everything already banked.
	 */
	@Test
	public void aRecordMissingFieldsStillReads()
	{
		RunRecord restored = store.decode("{\"v\":1,\"at\":1756339200,\"delve\":30}");

		assertNotNull(restored);
		assertEquals(30, restored.delve);
		assertNull(restored.end);
		assertNull(restored.loot);
	}

	@Test
	public void fileNamesAreSafeForBothKindsOfProfileKey()
	{
		assertEquals("a1b2c3d4.jsonl", RunHistoryStore.fileName("a1b2c3d4"));
		assertEquals("zezima.jsonl", RunHistoryStore.fileName("Zezima"));
		assertEquals("some_name.jsonl", RunHistoryStore.fileName("Some Name"));
		assertEquals("a_b.jsonl", RunHistoryStore.fileName("a/../b"));
		assertEquals("unknown.jsonl", RunHistoryStore.fileName("///"));
	}
}
