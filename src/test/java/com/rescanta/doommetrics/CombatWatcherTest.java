package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
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
		() -> run, (metric, amount) -> credited.merge(metric, amount, Long::sum),
		new CombatDiagnostics(client, config, items, () -> run));

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
		splat(boss, HitsplatID.DAMAGE_ME, amount);
	}

	private void splat(Actor target, int type, int amount)
	{
		HitsplatApplied event = new HitsplatApplied();
		event.setActor(target);
		event.setHitsplat(new Hitsplat()
		{
			@Override
			public int getHitsplatType()
			{
				return type;
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

	/** The inventory as the client sends it after a change: one of each of these. */
	private void carry(int... itemIds)
	{
		Item[] held = new Item[itemIds.length];

		for (int i = 0; i < itemIds.length; i++)
		{
			held[i] = new Item(itemIds[i], 1);
		}

		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(held);
		watcher.itemContainerChanged(new ItemContainerChanged(InventoryID.INV, inventory));
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

	@Test
	public void aPotionLeavingTheInventoryIsReadAsDrunk()
	{
		carry(ItemID._4DOSE2RESTORE);
		carry();

		verify(items).isDrunk(ItemID._4DOSE2RESTORE);
	}

	/** Seen in game: every potion carried was read as drunk on the tick of the respawn. */
	@Test
	public void whatLeavesTheInventoryOnADeathWasNotDrunk()
	{
		carry(ItemID._4DOSE2RESTORE);
		watcher.playerDied();
		carry();

		verify(items, never()).isDrunk(anyInt());
	}

	/** The holy water of a punish kill heals with a splat of its own, and no spec does. */
	@Test
	public void aHealWithASplatOfItsOwnIsNotTheSpecs()
	{
		Runnable readWeapon = spendEnergy();
		splat(player, HitsplatID.HEAL, 4);
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP + 89, 99, 94));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 60));
		readWeapon.run();
		watcher.tickEnded();

		assertHealingBladeCredited();
	}

	/**
	 * Seen in game: a Super restore sipped on a tick of the holy water was taken to heal 4, and
	 * every heal it later shared a tick with was counted 4 short.
	 */
	@Test
	public void theHolyWaterDoesNotTeachAPotionToHeal()
	{
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 10));
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(150);
		carry(ItemID._2DOSE2RESTORE);
		carry(ItemID._1DOSE2RESTORE);
		splat(player, HitsplatID.HEAL, 4);
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP, 99, 74));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 45));
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(200);
		Runnable readWeapon = spendEnergy();
		carry();
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP + 89, 99, 94));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 87));
		readWeapon.run();
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(201);
		hitBoss(40);
		watcher.tickEnded();

		assertEquals(20, credited(CombatMetric.SGS_HEAL));
		assertEquals(10, credited(CombatMetric.SGS_PRAYER));
	}

	/** With a holy wrench carried the dose is 34 at 99, and that is what comes out of the rise. */
	@Test
	public void aDoseWithAWrenchCarriedTakesItsLargerShareOutOfARestore()
	{
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 10));
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(200);
		carry(ItemID._1DOSE2RESTORE, ItemID.DEAL_WRENCH_BLESSED);
		Runnable readWeapon = spendEnergy();
		carry(ItemID.DEAL_WRENCH_BLESSED);
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP + 89, 99, 90));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 54));
		readWeapon.run();
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(201);
		hitBoss(40);
		watcher.tickEnded();

		assertEquals(20, credited(CombatMetric.SGS_HEAL));
		assertEquals(10, credited(CombatMetric.SGS_PRAYER));
	}

	/**
	 * Seen in game: at 4 hitpoints Redemption healed 24, a quarter of 99 prayer. On the tick a
	 * godsword heals it is no part of the godsword's.
	 */
	@Test
	public void redemptionsHealIsNotTheSpecs()
	{
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
		when(player.hasSpotAnim(SpotanimID.REDEMPTION_SPOTANIM)).thenReturn(true);
		GraphicChanged graphic = new GraphicChanged();
		graphic.setActor(player);

		Runnable readWeapon = spendEnergy();
		watcher.graphicChanged(graphic);
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP + 89, 99, 99));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 60));
		readWeapon.run();
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(101);
		watcher.graphicChanged(graphic);
		hitBoss(40);
		watcher.tickEnded();

		assertEquals(5, credited(CombatMetric.SGS_HEAL));
		assertEquals(40, credited(CombatMetric.OTHER_SPEC_DAMAGE));
	}

	/** A brew's 16 at 99 hitpoints is known from the first sip and comes out of the spec's heal. */
	@Test
	public void aBrewSippedOnASpecsTickIsTakenOutOfItsHeal()
	{
		when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);

		carry(ItemID._1DOSEPOTIONOFSARADOMIN);
		Runnable readWeapon = spendEnergy();
		carry();
		watcher.statChanged(new StatChanged(Skill.HITPOINTS, HITPOINTS_XP + 89, 99, 106));
		watcher.statChanged(new StatChanged(Skill.PRAYER, 0, 99, 60));
		readWeapon.run();
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(101);
		hitBoss(40);
		watcher.tickEnded();

		assertEquals(20, credited(CombatMetric.SGS_HEAL));
		assertEquals(10, credited(CombatMetric.SGS_PRAYER));
	}

	/** The noxious halberd poisons the boss. Those splats are nobody's attack and never counted. */
	@Test
	public void poisonOnTheBossIsNotASpecsHit()
	{
		Runnable readWeapon = spendEnergy();
		healingBlade();
		readWeapon.run();
		watcher.tickEnded();

		when(client.getTickCount()).thenReturn(101);
		splat(boss, HitsplatID.POISON, 6);
		splat(boss, HitsplatID.VENOM, 8);
		watcher.tickEnded();

		assertEquals(0, credited(CombatMetric.OTHER_SPEC_DAMAGE));
	}
}
