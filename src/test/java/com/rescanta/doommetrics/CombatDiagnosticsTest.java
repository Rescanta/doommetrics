package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
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
	private final NPC larva = npc(NpcID.DOM_DEMONIC_ENERGY_RANGE, 9, "Demonic larva");
	private final NPC ghost = npc(NpcID.ARCEUUS_THRALL_GHOST_GREATER, 3, "Greater ghostly thrall");

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

	/** A projectile as it is first reported: after its tick has ended, flying for some ticks. */
	private Projectile fire(int id, Actor from, Actor to, int flightTicks)
	{
		Projectile projectile = mock(Projectile.class);
		int cycle = tick * CYCLES_PER_TICK + 2;

		when(projectile.getId()).thenReturn(id);
		when(projectile.getSourceActor()).thenReturn(from);
		when(projectile.getTargetActor()).thenReturn(to);
		when(projectile.getStartCycle()).thenReturn(cycle + 10);
		when(projectile.getEndCycle()).thenReturn(cycle + flightTicks * CYCLES_PER_TICK);

		moved(projectile);
		return projectile;
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
		diagnostics.swung(100);
		diagnostics.experienceGained(69, 100);
		diagnostics.tickEnded(100);
		fire(ARROW, player, boss, 2);

		at(102);
		hit(boss, 31);
		endTicksTo(103);

		assertEquals("Projectile 1120 from us at Doom of Mokhaiotl (" + NpcID.DOM_BOSS + ") #7,"
			+ " cycles 10 to 60 from now: fired tick 100, lands tick 102", only("Projectile"));
		assertEquals("Ledger: 31 on Doom of Mokhaiotl (" + NpcID.DOM_BOSS + ") #7 at tick 102 ="
			+ " shot 1120 made 100 due 102 (+0), experience 69 says 31 to 31", only("Ledger"));
	}

	/** The game reports a projectile on every frame it moves. */
	@Test
	public void aProjectileIsWrittenAndCountedOnce()
	{
		diagnostics.tickEnded(100);
		Projectile arrow = fire(ARROW, player, boss, 2);
		moved(arrow);
		moved(arrow);

		at(102);
		hit(boss, 31);
		hit(boss, 12);
		endTicksTo(103);

		assertEquals(1, lines("Projectile").size());
		assertEquals(2, lines("Ledger").size());
		assertTrue(lines("Ledger").get(1), lines("Ledger").get(1).endsWith("= nothing due"));
	}

	@Test
	public void aThrallsBoltTakesItsSmallHitAndOursIsLeftToOurShot()
	{
		diagnostics.swung(100);
		diagnostics.tickEnded(100);
		fire(ARROW, player, boss, 2);

		at(101);
		diagnostics.tickEnded(101);
		fire(SpotanimID.THRALL_MAGIC_TRAVEL, ghost, boss, 1);

		at(102);
		hit(boss, 31);
		hit(boss, 2);
		endTicksTo(103);

		List<String> verdicts = lines("Ledger");
		assertTrue(verdicts.get(0), verdicts.get(0).contains("= shot 1120 made 100 due 102 (+0)"));
		assertTrue(verdicts.get(1), verdicts.get(1).contains("= thrall shot 1907 made 101 due 102 (+0)"));
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
	public void aHitOnUsIsOnlyWrittenWithSomethingOnThatHitsBack()
	{
		hit(player, 34);
		diagnostics.tickEnded(100);
		assertTrue(lines("Taken").isEmpty());

		when(items.name(anyInt())).thenReturn("Ring of suffering (i)");
		at(101);
		hit(player, 34);
		diagnostics.tickEnded(101);

		assertEquals("Taken 34 at tick 101 with \"Ring of suffering (i)\" on and vengeance 0",
			only("Taken"));
	}

	@Test
	public void nothingIsWrittenWithDebugLoggingOff()
	{
		when(config.debugLogging()).thenReturn(false);

		diagnostics.swung(100);
		diagnostics.experienceGained(69, 100);
		diagnostics.tickEnded(100);
		fire(ARROW, player, boss, 2);
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
		fire(ARROW, player, boss, 2);
		at(102);
		hit(boss, 31);
		endTicksTo(104);

		assertTrue(written.list.toString(), written.list.isEmpty());
	}
}
