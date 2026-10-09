package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * The watcher against a client that is not there: a spec's events go in as the client sends them,
 * and what is credited comes out.
 */
public class CombatWatcherTest
{
	private static final int FULL_ENERGY = 1000;
	private static final int HITPOINTS_XP = 13_034_431;

	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final DoomMetricsConfig config = mock(DoomMetricsConfig.class);
	private final GameItems items = mock(GameItems.class);
	private final Player player = mock(Player.class);
	private final NPC boss = mock(NPC.class);

	private final DelveRun run = new DelveRun(Instant.now(), 1, false);
	private final Map<CombatMetric, Long> credited = new EnumMap<>(CombatMetric.class);

	private final CombatWatcher watcher = new CombatWatcher(client, clientThread, config, items,
		() -> run, (metric, amount) -> credited.merge(metric, amount, Long::sum));

	@Before
	public void setUp()
	{
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(FULL_ENERGY);
		when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(70);
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(50);
		when(client.getSkillExperience(Skill.HITPOINTS)).thenReturn(HITPOINTS_XP);
		when(boss.getId()).thenReturn(NpcID.DOM_BOSS);

		when(items.equipped(EquipmentInventorySlot.WEAPON)).thenReturn(ItemID.SGS);
		when(items.name(ItemID.SGS)).thenReturn("Saradomin godsword");
		when(items.isMeleeWeapon(ItemID.SGS)).thenReturn(true);

		when(client.getTickCount()).thenReturn(100);
		watcher.runStarted();
		watcher.bossSpawned(boss);
	}

	/** The energy falls first; the weapon is read in a task the watcher queues for later. */
	private Runnable spendEnergy()
	{
		watcher.specEnergyChanged(FULL_ENERGY / 2);

		ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(queued.capture());
		return queued.getValue();
	}

	/** Healing Blade's heal and restore come on the spec's own tick, with its experience. */
	private void healingBlade()
	{
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP + 89, 99, 90));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 60));
	}

	private void hitBoss(int amount)
	{
		HitsplatApplied event = new HitsplatApplied();
		event.setActor(boss);
		event.setHitsplat(new Hitsplat()
		{
			@Override
			public int getHitsplatType()
			{
				return HitsplatID.DAMAGE_ME;
			}

			@Override
			public int getAmount()
			{
				return amount;
			}

			@Override
			public int getDisappearsOnGameCycle()
			{
				return 0;
			}
		});
		watcher.hitsplatApplied(event);
	}

	private long credited(CombatMetric metric)
	{
		return credited.getOrDefault(metric, 0L);
	}

	private void assertHealingBladeCredited()
	{
		when(client.getTickCount()).thenReturn(101);
		hitBoss(40);
		watcher.tickEnded();

		assertEquals(20, credited(CombatMetric.SGS_HEAL));
		assertEquals(10, credited(CombatMetric.SGS_PRAYER));
		assertEquals(40, credited(CombatMetric.OTHER_SPEC_DAMAGE));
	}

	@Test
	public void aSaradominGodswordSpecIsCreditedItsHealRestoreAndHit()
	{
		Runnable readWeapon = spendEnergy();
		healingBlade();
		readWeapon.run();
		watcher.tickEnded();

		assertHealingBladeCredited();
	}

	/** The weapon may be read only after the tick has been settled; the credit is the same. */
	@Test
	public void theSpecIsCreditedTheSameWhenItsWeaponIsReadAfterTheTickEnds()
	{
		Runnable readWeapon = spendEnergy();
		healingBlade();
		watcher.tickEnded();
		readWeapon.run();

		assertHealingBladeCredited();
	}

	/** And when the levels move before the energy does. */
	@Test
	public void theSpecIsCreditedTheSameWhenTheHealIsHeardBeforeTheEnergy()
	{
		healingBlade();
		Runnable readWeapon = spendEnergy();
		readWeapon.run();
		watcher.tickEnded();

		assertHealingBladeCredited();
	}

	@Test
	public void aHealWithNoSpecBehindItIsCreditedToNothing()
	{
		healingBlade();
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(101);
		hitBoss(40);
		watcher.tickEnded();

		assertEquals(credited.toString(), 0, credited.size());
	}
}
