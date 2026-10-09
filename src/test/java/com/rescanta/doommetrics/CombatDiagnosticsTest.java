package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * The diagnostics against a client that is not there: game events go in as the client sends them,
 * and what would be written to the debug log comes out.
 */
public class CombatDiagnosticsTest
{
	private static final int CYCLES_PER_TICK = 30;
	private static final int ARROW = 1120;

	private final Client client = mock(Client.class);
	private final DoomMetricsConfig config = mock(DoomMetricsConfig.class);
	private final GameItems items = mock(GameItems.class);
	private final Player player = mock(Player.class);
	private final NPC boss = npc(NpcID.DOM_BOSS, 7, "Doom of Mokhaiotl");
	private final NPC shield = npc(NpcID.DOM_BOSS_SHIELDED, 7, "Doom of Mokhaiotl (Shielded)");
	private final NPC larva = npc(NpcID.DOM_DEMONIC_ENERGY_RANGE, 9, "Demonic larva");

	private final ListAppender<ILoggingEvent> written = new ListAppender<>();
	private final Logger logger = (Logger) LoggerFactory.getLogger(CombatDiagnostics.class);
	private Level levelBefore;

	private DelveRun run = new DelveRun(Instant.now(), 1, false);
	private final CombatDiagnostics diagnostics = new CombatDiagnostics(client, config, items,
		() -> run);

	private int tick;

	private static NPC npc(int id, int index, String name)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		when(npc.getIndex()).thenReturn(index);
		when(npc.getName()).thenReturn(name);
		return npc;
	}

	@Before
	public void setUp()
	{
		levelBefore = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		written.start();
		logger.addAppender(written);

		when(config.debugLogging()).thenReturn(true);
		when(client.getLocalPlayer()).thenReturn(player);
		when(client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP)).thenReturn(625);
		when(player.getInteracting()).thenReturn(boss);
		at(100);
	}

	@After
	public void tearDown()
	{
		logger.detachAppender(written);
		logger.setLevel(levelBefore);
	}

	/** Moves the client on to a tick, a few cycles in, as events arrive. */
	private void at(int now)
	{
		tick = now;
		when(client.getTickCount()).thenReturn(now);
		when(client.getGameCycle()).thenReturn(now * CYCLES_PER_TICK + 2);
	}

	/** Ends the ticks from the one the client is on up to {@code last}, leaving it there. */
	private void endTicksTo(int last)
	{
		diagnostics.tickEnded(tick);

		while (tick < last)
		{
			at(tick + 1);
			diagnostics.tickEnded(tick);
		}
	}

	/** A projectile as it is first reported: starting to move now, with no source named. */
	private Projectile fire(int id, Actor to, int length)
	{
		Projectile projectile = mock(Projectile.class);
		int cycle = tick * CYCLES_PER_TICK + 2;

		when(projectile.getId()).thenReturn(id);
		when(projectile.getTargetActor()).thenReturn(to);
		when(projectile.getStartCycle()).thenReturn(cycle);
		when(projectile.getEndCycle()).thenReturn(cycle + length);

		moved(projectile);
		return projectile;
	}

	/** A bow shot at the boss made now: its arrow is first reported two ticks on, as it starts. */
	private Projectile shoot(int length)
	{
		diagnostics.swung(tick);
		endTicksTo(tick + 1);
		at(tick + 1);
		return fire(ARROW, boss, length);
	}

	private void moved(Projectile projectile)
	{
		ProjectileMoved event = new ProjectileMoved();
		event.setProjectile(projectile);
		diagnostics.projectileMoved(event);
	}

	private void hit(Actor on, int amount)
	{
		HitsplatApplied event = new HitsplatApplied();
		event.setActor(on);
		event.setHitsplat(new Hitsplat()
		{
			@Override
			public int getHitsplatType()
			{
				return amount == 0 ? HitsplatID.BLOCK_ME : HitsplatID.DAMAGE_ME;
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
		diagnostics.hitsplatApplied(event);
	}

	private List<String> lines(String startingWith)
	{
		List<String> lines = new ArrayList<>();

		for (ILoggingEvent event : written.list)
		{
			if (event.getFormattedMessage().startsWith(startingWith))
			{
				lines.add(event.getFormattedMessage());
			}
		}

		return lines;
	}

	private String only(String startingWith)
	{
		List<String> lines = lines(startingWith);
		assertEquals(lines.toString(), 1, lines.size());
		return lines.get(0);
	}

	@Test
	public void ourShotTakesTheHitOnTheTickItLands()
	{
		diagnostics.experienceGained(69, 100);
		shoot(20);
		endTicksTo(102);

		at(103);
		hit(boss, 31);
		endTicksTo(104);

		assertEquals("Projectile 1120 at Doom of Mokhaiotl (" + NpcID.DOM_BOSS + ") #7,"
			+ " cycles 0 to 20 from now: started tick 102, lands tick 103", only("Projectile"));
		assertEquals("Ledger: 31 on Doom of Mokhaiotl (" + NpcID.DOM_BOSS + ") #7 at tick 103 ="
			+ " shot 1120 made 100 due 103 (+0), experience 69 says 31 to 31", only("Ledger"));
	}

	/** Seen in game: the block of a hit taken while the arrow flew took the arrow's hit. */
	@Test
	public void aBlockWhileTheArrowFliesDoesNotTakeItsHit()
	{
		diagnostics.experienceGained(59, 100);
		shoot(35);
		when(player.getInteracting()).thenReturn(null);
		diagnostics.swung(102);
		endTicksTo(102);

		at(103);
		hit(boss, 26);
		endTicksTo(106);

		assertTrue(only("Ledger"), only("Ledger").endsWith(
			"= shot 1120 made 100 due 103 (+0), experience 59 says 26 to 26"));
	}

	/** The game reports a projectile on every frame it moves. */
	@Test
	public void aProjectileIsWrittenAndCountedOnce()
	{
		Projectile arrow = shoot(20);
		moved(arrow);
		moved(arrow);
		endTicksTo(102);

		at(103);
		hit(boss, 31);
		hit(boss, 12);
		endTicksTo(104);

		assertEquals(1, lines("Projectile").size());
		assertEquals(2, lines("Ledger").size());
		assertTrue(lines("Ledger").get(1), lines("Ledger").get(1).endsWith("= nothing due"));
	}

	@Test
	public void aThrallsBoltTakesItsSmallHitAndOursIsLeftToOurShot()
	{
		shoot(20);
		endTicksTo(102);

		at(103);
		fire(SpotanimID.THRALL_MAGIC_TRAVEL, boss, 25);
		hit(boss, 31);
		hit(boss, 2);
		endTicksTo(104);

		List<String> verdicts = lines("Ledger");
		assertTrue(verdicts.get(0), verdicts.get(0).contains("= shot 1120 made 100 due 103 (+0)"));
		assertTrue(verdicts.get(1), verdicts.get(1).contains("= thrall shot 1907 made 102 due 103 (+0)"));
	}

	@Test
	public void anAnimationWithNoProjectileIsASwingAtWhatWeAreAimedAt()
	{
		diagnostics.swung(100);
		diagnostics.experienceGained(89, 100);
		diagnostics.tickEnded(100);

		at(101);
		hit(boss, 40);
		endTicksTo(102);

		assertTrue(only("Swing at tick"), only("Swing at tick").startsWith(
			"Swing at tick 100 aimed at Doom of Mokhaiotl (" + NpcID.DOM_BOSS + ") #7"));
		assertTrue(only("Ledger"), only("Ledger").endsWith(
			"= swing made 100 due 101 (+0), experience 89 says 40 to 40"));
	}

	/** The bar reads the shield's 500 hitpoints while it is up; the experience is still the boss's. */
	@Test
	public void aHitOnTheShieldIsSizedAtTheBossesRate()
	{
		diagnostics.experienceGained(3, 100);
		endTicksTo(103);

		when(client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP)).thenReturn(500);
		when(player.getInteracting()).thenReturn(shield);
		at(104);
		diagnostics.swung(104);
		diagnostics.experienceGained(89, 104);
		endTicksTo(104);

		at(105);
		hit(shield, 40);
		endTicksTo(106);

		assertTrue(only("Ledger"), only("Ledger").endsWith(
			"= swing made 104 due 105 (+0), experience 89 says 40 to 40"));
	}

	@Test
	public void aSwingAtALarvaDoesNotTakeAHitOnTheBoss()
	{
		when(player.getInteracting()).thenReturn(larva);
		diagnostics.swung(100);
		diagnostics.tickEnded(100);

		at(101);
		hit(boss, 40);
		hit(larva, 2);
		endTicksTo(102);

		List<String> verdicts = lines("Ledger");
		assertTrue(verdicts.get(0), verdicts.get(0).endsWith("= nothing due"));
		assertTrue(verdicts.get(1), verdicts.get(1).contains("on Demonic larva"));
		assertTrue(verdicts.get(1), verdicts.get(1).contains("= swing made 100 due 101 (+0)"));
		assertEquals("Hitsplat 2 of type 16 (mine) on Demonic larva ("
			+ NpcID.DOM_DEMONIC_ENERGY_RANGE + ") #9 at tick 101", only("Hitsplat"));
	}

	@Test
	public void theSpecsHitSaysWhatItShouldGiveBack()
	{
		diagnostics.swung(100);
		diagnostics.specFired(100, SpecWeapon.SARADOMIN_GODSWORD);
		diagnostics.tickEnded(100);

		at(101);
		hit(boss, 40);
		endTicksTo(102);

		assertTrue(only("Ledger"), only("Ledger").contains("= spec swing made 100 due 101 (+0)"));
		assertEquals("Special attack made at tick 100 hit 40: a heal of 20 and a prayer restore of"
			+ " 10 would follow", only("Special attack made"));
	}

	@Test
	public void aProjectileAimedAtUsIsNotWritten()
	{
		fire(SpotanimID.VFX_STANDARD_PROJECTILE_MAGIC, player, 60);
		endTicksTo(103);

		assertTrue(written.list.toString(), lines("Projectile").isEmpty());
	}

	@Test
	public void anAncientGodswordSpecHasItsSacrificeToCome()
	{
		diagnostics.swung(100);
		diagnostics.experienceGained(89, 100);
		diagnostics.specFired(100, SpecWeapon.ANCIENT_GODSWORD);
		endTicksTo(100);

		at(101);
		hit(boss, 40);
		endTicksTo(108);

		at(109);
		hit(boss, 25);
		endTicksTo(110);

		List<String> verdicts = lines("Ledger");
		assertEquals(verdicts.toString(), 2, verdicts.size());
		assertTrue(verdicts.get(1), verdicts.get(1).endsWith("= spec sacrifice made 100 due 109 (+0)"));
	}

	/** The heal comes with the cast; the hit it is a share of comes two to five ticks later. */
	@Test
	public void aBloodSpellsHitsAreAddedUpOnceTheyAreIn()
	{
		diagnostics.bloodSpell(100);
		diagnostics.swung(100);
		diagnostics.experienceGained(65, 100);
		endTicksTo(103);

		at(104);
		hit(boss, 29);
		endTicksTo(106);

		assertTrue(only("Ledger"), only("Ledger").endsWith(
			"= cast made 100 due 102 (+2), experience 65 says 29 to 29"));
		assertEquals("Blood spell made at tick 100 hit 29 in all, a quarter of which is 7",
			only("Blood spell made"));
	}

	@Test
	public void nothingIsWrittenWithDebugLoggingOff()
	{
		when(config.debugLogging()).thenReturn(false);

		diagnostics.swung(100);
		diagnostics.experienceGained(69, 100);
		diagnostics.tickEnded(100);
		fire(ARROW, boss, 20);
		at(102);
		hit(boss, 31);
		endTicksTo(104);

		assertTrue(written.list.toString(), written.list.isEmpty());
	}

	@Test
	public void nothingIsWrittenOutsideARun()
	{
		run = null;

		diagnostics.swung(100);
		diagnostics.tickEnded(100);
		fire(ARROW, boss, 20);
		at(102);
		hit(boss, 31);
		endTicksTo(104);

		assertTrue(written.list.toString(), written.list.isEmpty());
	}
}
