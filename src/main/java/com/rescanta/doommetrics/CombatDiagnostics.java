package com.rescanta.doommetrics;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;

/**
 * Writes what the game says and nothing counts from yet to the debug log, so a run shows how each
 * behaves before anything is built on it, and beside it what {@link AttackLedger} makes of every
 * hit, to set against what the trackers credited. Only with debug logging on; never changes a
 * figure. Client thread only.
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

	/** How far from 600ms a tick has to be to be worth a line. */
	private static final long TICK_SLACK_MILLIS = 150;
	private static final long TICK_MILLIS = 600;

	/** How long a tick's own hits are kept to set a heal against. */
	private static final int KEPT_TICKS = 4;

	private final Client client;
	private final DoomMetricsConfig config;
	private final GameItems items;
	private final Supplier<DelveRun> run;

	private final AttackLedger ledger = new AttackLedger(new Written());

	/** Projectiles already written: the game reports one on every frame it moves. */
	private final Set<Projectile> seen = Collections.newSetFromMap(new IdentityHashMap<>());

	private int swingTick = -1;
	private String aimAtSwing;
	private int armedAtSwing;

	private String lastCast;
	private int lastCastTick;

	/** Hitpoints experience so far on a tick, handed to the ledger at its end. */
	private int experience;
	private int experienceTick = -1;

	/**
	 * What a point of damage on the boss earned when its hitpoints bar was last its own: the bar
	 * reads the shield's while that is up and nothing between delves, and the rate stays.
	 */
	private double rateOnBoss;

	/** The weapon of the last spec, for what its hit should give back. */
	private SpecWeapon specWeapon;
	private int specTick = -1;

	private int bloodSpellTick = -1;

	/** What our hits on NPCs came to on each of the last few ticks. */
	private final TreeMap<Integer, Integer> dealt = new TreeMap<>();

	private long lastTickAt;

	CombatDiagnostics(Client client, DoomMetricsConfig config, GameItems items,
		Supplier<DelveRun> run)
	{
		this.client = client;
		this.config = config;
		this.items = items;
		this.run = run;
	}

	void reset()
	{
		seen.clear();
		ledger.reset();
		dealt.clear();
		swingTick = -1;
		experienceTick = -1;
		rateOnBoss = 0;
		specTick = -1;
		bloodSpellTick = -1;
		lastCast = null;
		lastTickAt = 0;
	}

	/** The ledger's verdicts, one line each. */
	private final class Written implements AttackLedger.Listener
	{
		@Override
		public void settled(AttackLedger.Verdict verdict)
		{
			log.debug("Ledger: {}", verdict);

			if (!verdict.spec || specWeapon == null || verdict.attack.made != specTick)
			{
				return;
			}

			int heal = HealBound.specHeal(specWeapon, verdict.amount);

			if (heal >= 0)
			{
				log.debug("Special attack made at tick {} hit {}: a heal of {}{} would follow",
					specTick, verdict.amount, heal, specWeapon == SpecWeapon.SARADOMIN_GODSWORD
						? " and a prayer restore of " + HealBound.sgsPrayer(verdict.amount)
						: "");
			}
		}

		@Override
		public void lapsed(AttackLedger.Attack attack)
		{
			log.debug("Ledger: {} took nothing", attack);
		}
	}

	private boolean on()
	{
		return run.get() != null && config.debugLogging();
	}

	/**
	 * What flies at an NPC and the tick it should land on, once per projectile. Doom is fought
	 * alone and a projectile names no source, so one that is not the thrall's is ours.
	 */
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

		Actor target = projectile.getTargetActor();

		if (!(target instanceof NPC))
		{
			return;
		}

		int cycle = client.getGameCycle();
		int started = Flight.startedTick(client.getTickCount(), cycle, projectile.getStartCycle());
		int length = projectile.getEndCycle() - projectile.getStartCycle();
		boolean thrall = isThrallShot(projectile);
		int lands = thrall
			? Flight.thrallLandingTick(started, length)
			: Flight.landingTick(started, length);
		int index = ((NPC) target).getIndex();

		log.debug("Projectile {} at {}, cycles {} to {} from now: started tick {}, lands tick {}",
			projectile.getId(), describe(target), projectile.getStartCycle() - cycle,
			projectile.getEndCycle() - cycle, started, lands);

		if (thrall)
		{
			ledger.thrallShot(projectile.getId(), started, lands, index);
		}
		else
		{
			ledger.shotStarted(projectile.getId(), started, lands, index);
		}
	}

	private static boolean isThrallShot(Projectile projectile)
	{
		return projectile.getId() == SpotanimID.THRALL_RANGED_TRAVEL
			|| projectile.getId() == SpotanimID.THRALL_MAGIC_TRAVEL;
	}

	/** Only a zombie's attack is taken from its animation: the other two have a projectile. */
	void thrallAttacked(ThrallTracker.Style style, int tick)
	{
		if (on() && style == ThrallTracker.Style.MELEE)
		{
			ledger.thrallSwung(tick);
		}
	}

	void experienceGained(int gained, int tick)
	{
		if (on())
		{
			experience = (experienceTick == tick ? experience : 0) + gained;
			experienceTick = tick;
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

	void specFired(int tick, SpecWeapon weapon)
	{
		if (on())
		{
			log.debug("Special attack at tick {} aimed at {}", tick, describe(aim()));
			specWeapon = weapon;
			specTick = tick;
			ledger.specFired(tick);
		}
	}

	/** The spell isn't in the drain line, so what could name it is written beside it. */
	void bloodSpell(int tick)
	{
		if (on())
		{
			log.debug("Blood spell at tick {}: autocast {}, last cast clicked \"{}\" at tick {}",
				tick, client.getVarbitValue(VarbitID.AUTOCAST_SPELL), lastCast, lastCastTick);
			bloodSpellTick = tick;
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

	/**
	 * A heal on the boss in any of its forms is a larva that got through. Every hit of ours on an
	 * NPC goes to the ledger, and one off the counted boss is written here, as nothing else does.
	 */
	void hitsplatApplied(HitsplatApplied event)
	{
		if (!on())
		{
			return;
		}

		Actor target = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		int tick = client.getTickCount();

		if (!(target instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) target;

		if (hitsplat.getHitsplatType() == HitsplatID.HEAL && DoomMetricsPlugin.isDoomBoss(npc.getId()))
		{
			log.debug("Boss healed {} at tick {} as {}", hitsplat.getAmount(), tick, npc.getId());
		}

		if (npc.getId() != NpcID.DOM_BOSS && npc.getId() != NpcID.DOM_BOSS_BURROWED)
		{
			log.debug("Hitsplat {} of type {} ({}) on {} at tick {}", hitsplat.getAmount(),
				hitsplat.getHitsplatType(),
				hitsplat.isMine() ? "mine" : hitsplat.isOthers() ? "others" : "neither",
				describe(npc), tick);
		}

		if (hitsplat.isMine())
		{
			ledger.splat(tick, npc.getIndex(), hitsplat.getAmount(), describe(npc));
			dealt.merge(tick, hitsplat.getAmount(), Integer::sum);
		}
	}

	/**
	 * A heal left once regeneration and food are out of it. With the blood fury on, what the tick's
	 * hits would give back through it is written beside it.
	 */
	void healed(int amount, int tick)
	{
		if (!on())
		{
			return;
		}

		String amulet = items.name(items.equipped(EquipmentInventorySlot.AMULET));

		if (amulet != null && amulet.toLowerCase().contains("blood fury"))
		{
			int now = dealt.getOrDefault(tick, 0);
			int before = dealt.getOrDefault(tick - 1, 0);

			log.debug("Heal of {} at tick {} with the blood fury on: our hits came to {} this tick"
					+ " and {} the tick before, which would give back {} and {}", amount, tick, now,
				before, HealBound.bloodFuryHeal(now), HealBound.bloodFuryHeal(before));
		}
	}

	void actorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();

		if (on() && actor instanceof NPC && DoomMetricsPlugin.isDoomBoss(((NPC) actor).getId()))
		{
			log.debug("Boss died at tick {}", client.getTickCount());
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

		tickLength(tick);

		if (swingTick == tick)
		{
			Actor aim = aim();

			log.debug("Swing at tick {} aimed at {} (at the animation: {}), spec bar armed {}",
				tick, describe(aim), aimAtSwing, armedAtSwing);
			ledger.swung(tick, aim instanceof NPC ? ((NPC) aim).getIndex() : AttackLedger.UNKNOWN);
		}

		// None on a swing's tick says it missed, once this run has shown experience is earned at all.
		if (experienceTick == tick)
		{
			ledger.experience(tick, experience, bossRate(aim()));
		}
		else if (swingTick == tick && experienceTick >= 0)
		{
			ledger.experience(tick, 0, bossRate(aim()));
		}

		ledger.tickEnded(tick);
		bloodSpellLanded(tick);
		dealt.headMap(tick - KEPT_TICKS).clear();

		int cycle = client.getGameCycle();
		seen.removeIf(projectile -> projectile.getEndCycle() < cycle);
	}

	/** What a point of damage earns on what we are aimed at; 0 for anything but the boss. */
	private double bossRate(Actor aim)
	{
		double shown = ExperienceRate.perDamage(client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP));
		rateOnBoss = shown > 0 ? shown : rateOnBoss;

		return aim instanceof NPC && DoomMetricsPlugin.isDoomBoss(((NPC) aim).getId())
			? rateOnBoss
			: 0;
	}

	/** A tick that came early or late throws off anything worked out from a projectile's cycles. */
	private void tickLength(int tick)
	{
		long now = System.nanoTime();
		long millis = lastTickAt == 0 ? TICK_MILLIS : (now - lastTickAt) / 1_000_000;
		lastTickAt = now;

		if (Math.abs(millis - TICK_MILLIS) > TICK_SLACK_MILLIS)
		{
			log.debug("Tick {} came {} ms after the one before", tick, millis);
		}
	}

	/** A blood spell heals a quarter of what it hit for, written once the hits of its tick are in. */
	private void bloodSpellLanded(int tick)
	{
		if (bloodSpellTick != tick)
		{
			return;
		}

		int now = dealt.getOrDefault(tick, 0);
		int before = dealt.getOrDefault(tick - 1, 0);

		log.debug("Blood spell at tick {}: our hits came to {} this tick and {} the tick before,"
				+ " which would heal {} and {}", tick, now, before, HealBound.bloodSpellHeal(now),
			HealBound.bloodSpellHeal(before));
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
