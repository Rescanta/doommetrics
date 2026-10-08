package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;

/**
 * Writes what the game says and nothing counts from yet to the debug log, so a run shows how each
 * behaves before anything is built on it. Only with debug logging on; never changes a figure.
 * Client thread only.
 */
@Slf4j
class CombatDiagnostics
{
	/** Varbits worth seeing move during a run, by what each says. */
	private static final Map<Integer, String> VARBITS = Map.of(
		VarbitID.HPBAR_HUD_BASEHP, "Boss max hitpoints",
		VarbitID.PRAYER_RAPIDHEAL, "Rapid Heal",
		VarbitID.ARCEUUS_RESURRECTION_COOLDOWN, "Thrall varbit cooldown",
		VarbitID.ARCEUUS_RESURRECTION_USED, "Thrall varbit used",
		VarbitID.ARCEUUS_RESURRECTION_ACTIVE, "Thrall varbit active");

	private final Client client;
	private final DoomMetricsConfig config;
	private final Supplier<DelveRun> run;

	/** Projectiles already written: the game reports one on every frame it moves. */
	private final Set<Projectile> seen = Collections.newSetFromMap(new IdentityHashMap<>());

	/** Ids of projectiles aimed at us, by the tick each should come down on. */
	private final TreeMap<Integer, List<Integer>> landingOnUs = new TreeMap<>();

	private int swingTick = -1;
	private String aimAtSwing;
	private int armedAtSwing;

	private String lastCast;
	private int lastCastTick;

	CombatDiagnostics(Client client, DoomMetricsConfig config, Supplier<DelveRun> run)
	{
		this.client = client;
		this.config = config;
		this.run = run;
	}

	void reset()
	{
		seen.clear();
		landingOnUs.clear();
		swingTick = -1;
		lastCast = null;
	}

	private boolean on()
	{
		return run.get() != null && config.debugLogging();
	}

	/** Who fired what at whom, and the tick it should land on, once per projectile. */
	void projectileMoved(ProjectileMoved event)
	{
		if (!on())
		{
			return;
		}

		Projectile projectile = event.getProjectile();

		if (!seen.add(projectile))
		{
			return;
		}

		int tick = client.getTickCount();
		int cycle = client.getGameCycle();
		int lands = Flight.landingTick(tick, cycle, projectile.getEndCycle());
		Actor target = projectile.getTargetActor();

		log.debug("Projectile {} from {} at {}, cycles {} to {} from now: fired tick {}, lands"
				+ " tick {}", projectile.getId(), describe(projectile.getSourceActor()),
			target == null ? "the ground at " + projectile.getTargetPoint() : describe(target),
			projectile.getStartCycle() - cycle, projectile.getEndCycle() - cycle,
			Flight.firedTick(tick, cycle, projectile.getStartCycle()), lands);

		if (target != null && target == client.getLocalPlayer())
		{
			landingOnUs.computeIfAbsent(lands, at -> new ArrayList<>()).add(projectile.getId());
		}
	}

	void interactingChanged(InteractingChanged event)
	{
		if (on() && event.getSource() == client.getLocalPlayer())
		{
			log.debug("Target -> {} at tick {}", describe(event.getTarget()), client.getTickCount());
		}
	}

	/** A swing's aim is written at the end of its tick, when the tick's updates are all in. */
	void swung(int tick)
	{
		if (!on())
		{
			return;
		}

		swingTick = tick;
		aimAtSwing = describe(aim());
		armedAtSwing = client.getVarpValue(VarPlayerID.SA_ATTACK);
	}

	void specFired(int tick)
	{
		if (on())
		{
			log.debug("Special attack at tick {} aimed at {}", tick, describe(aim()));
		}
	}

	/** The spell isn't in the drain line, so what could name it is written beside it. */
	void bloodSpell(int tick)
	{
		if (on())
		{
			log.debug("Blood spell at tick {}: autocast {}, last cast clicked \"{}\" at tick {}",
				tick, client.getVarbitValue(VarbitID.AUTOCAST_SPELL), lastCast, lastCastTick);
		}
	}

	void menuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();

		if (option != null && option.startsWith("Cast") && event.getMenuTarget() != null && on())
		{
			lastCast = Text.removeTags(event.getMenuTarget());
			lastCastTick = client.getTickCount();
		}
	}

	void varbitChanged(VarbitChanged event)
	{
		if (!config.debugLogging())
		{
			return;
		}

		int varbit = event.getVarbitId();

		// The larvae count may only go back to nothing once the run is over.
		if (varbit == VarbitID.DOM_MISSED_ORBS)
		{
			log.debug("Larvae absorbed -> {} at tick {}", event.getValue(), client.getTickCount());
			return;
		}

		String name = VARBITS.get(varbit);

		if (name != null && run.get() != null)
		{
			log.debug("{} -> {} at tick {}", name, event.getValue(), client.getTickCount());
		}
	}

	/** A heal on the boss in any of its forms: a larva got through. */
	void hitsplatApplied(HitsplatApplied event)
	{
		Actor target = event.getActor();

		if (event.getHitsplat().getHitsplatType() == HitsplatID.HEAL && target instanceof NPC
			&& DoomMetricsPlugin.isDoomBoss(((NPC) target).getId()) && on())
		{
			log.debug("Boss healed {} at tick {} as {}", event.getHitsplat().getAmount(),
				client.getTickCount(), ((NPC) target).getId());
		}
	}

	/**
	 * @param boss the boss if it is about, to say how far from it the larva was
	 */
	void npcDespawned(NPC npc, NPC boss)
	{
		if (!on() || !isLarva(npc.getId()))
		{
			return;
		}

		WorldPoint at = npc.getWorldLocation();
		WorldArea area = boss == null ? null : boss.getWorldArea();

		log.debug("Larva {} gone at tick {}: {}, {} tiles from the boss", npc.getId(),
			client.getTickCount(), npc.isDead() ? "dead" : "not dead",
			at == null || area == null ? "unknown" : String.valueOf(area.distanceTo(at)));
	}

	void npcChanged(NpcChanged event)
	{
		int was = event.getOld() == null ? -1 : event.getOld().getId();
		int now = event.getNpc().getId();

		if (on() && (DoomMetricsPlugin.isDoomBoss(was) || DoomMetricsPlugin.isDoomBoss(now)))
		{
			log.debug("Boss form {} -> {} at tick {}", was, now, client.getTickCount());
		}
	}

	/** Experience in a skill at its cap arrives this way, with no level change behind it. */
	void fakeXpDrop(FakeXpDrop event)
	{
		if (on())
		{
			log.debug("Experience drop without a level change: {} +{} at tick {}", event.getSkill(),
				event.getXp(), client.getTickCount());
		}
	}

	void tickEnded(int tick)
	{
		if (!on())
		{
			return;
		}

		if (swingTick == tick)
		{
			log.debug("Swing at tick {} aimed at {} (at the animation: {}), spec bar armed {}",
				tick, describe(aim()), aimAtSwing, armedAtSwing);
		}

		landed(tick);

		int cycle = client.getGameCycle();
		seen.removeIf(projectile -> projectile.getEndCycle() < cycle);
	}

	/** A projectile first seen after its tick's end is written a tick late, and says so. */
	private void landed(int tick)
	{
		Iterator<Map.Entry<Integer, List<Integer>>> due = landingOnUs.headMap(tick, true)
			.entrySet().iterator();

		while (due.hasNext())
		{
			Map.Entry<Integer, List<Integer>> landing = due.next();
			Player player = client.getLocalPlayer();

			log.debug("Projectile {} due on us at tick {}: overhead {} at the end of tick {}",
				landing.getValue(), landing.getKey(),
				player == null || player.getOverheadIcon() == null
					? "none"
					: player.getOverheadIcon(),
				tick);
			due.remove();
		}
	}

	private Actor aim()
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : player.getInteracting();
	}

	private String describe(Actor actor)
	{
		if (actor == null)
		{
			return "nothing";
		}

		if (actor == client.getLocalPlayer())
		{
			return "us";
		}

		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			return npc.getName() + " (" + npc.getId() + ") #" + npc.getIndex();
		}

		return String.valueOf(actor.getName());
	}

	private static boolean isLarva(int npcId)
	{
		return npcId == NpcID.DOM_DEMONIC_ENERGY || npcId == NpcID.DOM_DEMONIC_ENERGY_RANGE
			|| npcId == NpcID.DOM_DEMONIC_ENERGY_MAGE || npcId == NpcID.DOM_DEMONIC_ENERGY_MELEE
			|| npcId == NpcID.DOM_DEMONIC_ENERGY_GIANT_RANGE
			|| npcId == NpcID.DOM_DEMONIC_ENERGY_GIANT_MAGE;
	}
}
