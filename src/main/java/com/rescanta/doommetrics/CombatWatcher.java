package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;

/**
 * Feeds the game's heals, prayer restores, hitsplats, specs and swings to {@link CombatTracker}
 * and {@link PunishTracker}, which decide what caused them. Only while a run is in progress.
 * Client thread only.
 */
@Slf4j
class CombatWatcher
{
	/** The only signal a blood spell heal gives - its impact graphic is never reported. */
	private static final String BLOOD_DRAIN = "drain some of your opponent";

	/** Player-producible healing spell impacts, mainly for the Sanguinesti staff. */
	private static final int[] OTHER_HEAL_SPELL_IMPACTS = {
		SpotanimID.BLOOD_RUSH_IMPACT,
		SpotanimID.SPELL_BLOOD_BURST_IMPACT,
		SpotanimID.BLOOD_BLITZ_IMPACT,
		SpotanimID.SANGUINESTI_STAFF_IMPACT,
		SpotanimID.SANGUINESTI_STAFF_IMPACT_JUSTICIAR
	};

	private static final int PRAYER_REGEN_PERIOD = 12;
	private static final int HITPOINTS_REGEN_PERIOD = 100;

	private final Client client;
	private final ClientThread clientThread;
	private final DoomMetricsConfig config;
	private final GameItems items;
	private final Supplier<DelveRun> run;

	private final CombatTracker combatTracker;
	private final PunishTracker punishTracker;

	/** Held from its spawn; the same NPC across its standing, shielded and burrowed forms. */
	private NPC boss;

	private final SpecEnergy specEnergy = new SpecEnergy();

	// Last seen values, so a change can be read as a difference.
	private int prayerPoints;
	private int hitpoints;

	private final Regeneration prayerRegeneration = new Regeneration();
	private final Regeneration hitpointsRegeneration = new Regeneration();

	private final CombatTracker.Sink sink;

	/**
	 * @param sink where an attributed amount is credited
	 */
	CombatWatcher(Client client, ClientThread clientThread, DoomMetricsConfig config, GameItems items,
		Supplier<DelveRun> run, CombatTracker.Sink sink)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.items = items;
		this.run = run;
		this.sink = sink;
		this.combatTracker = new CombatTracker(sink);
		this.punishTracker = new PunishTracker(sink, this::handBack);
	}

	/** Forgets everything, including the levels last seen. */
	void reset()
	{
		stopTracking();
		boss = null;
		specEnergy.forget();
		prayerPoints = 0;
		hitpoints = 0;
		prayerRegeneration.reset();
		hitpointsRegeneration.reset();
	}

	/**
	 * The player died: a window still open would take the respawn's restore, which can be a full
	 * heal. Punishes held for the tick still settle.
	 */
	void playerDied()
	{
		combatTracker.reset();
	}

	/** Forgets every cause in flight. */
	void stopTracking()
	{
		combatTracker.reset();
		punishTracker.reset();
	}

	/**
	 * A spec fired on the way in must not swallow the first heal of the trip. The levels are read
	 * afresh: after the plugin is turned on, or a run is carried on, the energy is only sent again
	 * once it changes, and at full energy that change is the first spec - read against zero, it
	 * would look like a rise and go uncounted.
	 */
	void runStarted()
	{
		stopTracking();
		specEnergy.seed(client.getVarpValue(VarPlayerID.SA_ENERGY));
		prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
		hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
	}

	void bossSpawned(NPC npc)
	{
		boss = npc;
	}

	void npcDespawned(NPC npc)
	{
		if (npc == boss)
		{
			boss = null;
		}
	}

	/** Despawns are not delivered across a scene load. */
	void sceneCleared()
	{
		boss = null;
	}

	/**
	 * The blood spell chat line, which is spam rather than a game message.
	 *
	 * @return true if the message was the drain line
	 */
	boolean chatMessage(String message)
	{
		if (run.get() == null || !message.contains(BLOOD_DRAIN))
		{
			return false;
		}

		combatTracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, client.getTickCount());
		log.debug("Blood spell drained at tick {}", client.getTickCount());
		return true;
	}

	void hitsplatApplied(HitsplatApplied event)
	{
		if (run.get() == null)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		Actor target = event.getActor();
		int tick = client.getTickCount();

		// Healing is read off the hitpoints level instead.
		if (target == client.getLocalPlayer())
		{
			return;
		}

		boolean onBoss = isCountedBoss(target);

		if (onBoss)
		{
			logBossHitsplat(hitsplat, tick);
		}

		// Only a spec burns (the scorching bow's, burning claws'), and the fight is solo, so a burn
		// is our spec's, however long after it.
		if (hitsplat.getHitsplatType() == HitsplatID.BURN)
		{
			if (countsAsDamage(target) && hitsplat.getAmount() > 0)
			{
				sink.record(CombatMetric.OTHER_SPEC_DAMAGE, hitsplat.getAmount());
				log.debug("Burn of {} at tick {} -> {}", hitsplat.getAmount(), tick,
					CombatMetric.OTHER_SPEC_DAMAGE.key());
			}

			return;
		}

		if (onBoss && punishTracker.mayBePunish(tick) && isPunishSplat(hitsplat))
		{
			// Held to the end of the tick; comes back through handBack if it was no punish.
			punishTracker.hit(hitsplat.getAmount(), hitsplat.isMine(), tick);
			return;
		}

		if (hitsplat.isMine())
		{
			if (onBoss)
			{
				punishTracker.ownHitNotHeld(tick);
			}

			// A zero rather than skipped, so the spec's budget is spent on this hit.
			int amount = countsAsDamage(target) ? hitsplat.getAmount() : 0;
			logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
			combatTracker.damaged(amount, tick);
		}
	}

	/**
	 * Punish bonus splats aren't {@code isMine()}; the fight is solo, so others' colours are ours.
	 */
	private static boolean isPunishSplat(Hitsplat hitsplat)
	{
		return hitsplat.isMine() || hitsplat.isOthers()
			|| hitsplat.getHitsplatType() == HitsplatID.DOOM;
	}

	private void logBossHitsplat(Hitsplat hitsplat, int tick)
	{
		if (!config.debugLogging())
		{
			return;
		}

		log.debug("Boss hitsplat {} of type {} at tick {}{}", hitsplat.getAmount(),
			hitsplat.getHitsplatType(), tick,
			punishTracker.mayBePunish(tick) ? ", held for the punish check" : "");
	}

	/** A hit held for the punish check, back to be counted as ordinary damage. */
	private void handBack(int amount, int tick)
	{
		logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
		combatTracker.damaged(amount, tick);
	}

	/** Only hits on the standing or burrowed boss count as damage. */
	private boolean countsAsDamage(Actor target)
	{
		if (isCountedBoss(target))
		{
			return true;
		}

		if (target instanceof NPC)
		{
			log.debug("Not counting damage to {} ({})", target.getName(), ((NPC) target).getId());
		}

		return false;
	}

	private static boolean isCountedBoss(Actor target)
	{
		if (!(target instanceof NPC))
		{
			return false;
		}

		int id = ((NPC) target).getId();
		return id == NpcID.DOM_BOSS || id == NpcID.DOM_BOSS_BURROWED;
	}

	/** Logs what the tracker would credit an effect to, including nothing. */
	private void logAttribution(SpecEffect.Kind kind, int amount, int tick)
	{
		if (!config.debugLogging())
		{
			return;
		}

		CombatMetric metric = combatTracker.wouldCredit(kind, amount, tick);
		log.debug("{} of {} at tick {} -> {}", kind, amount, tick,
			metric == null ? "nothing open, held for this tick" : metric.key());
	}

	void animationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();

		if (run.get() == null || actor == null)
		{
			return;
		}

		int animation = actor.getAnimation();
		int tick = client.getTickCount();

		if (actor == boss)
		{
			// Behind its shield the boss cancels the beam on its own.
			if (animation == AnimationID.DOM_BEAM_CANCEL && boss.getId() != NpcID.DOM_BOSS_SHIELDED)
			{
				punishTracker.beamCancelled(tick);
			}

			if (config.debugLogging())
			{
				log.debug("Boss animation {} at tick {}", animation, tick);
			}

			return;
		}

		if (actor != client.getLocalPlayer() || animation < 0 || animation == AnimationID.HUMAN_EAT)
		{
			return;
		}

		punishTracker.swung(tick);

		if (config.debugLogging())
		{
			log.debug("Animation {} at tick {}", animation, tick);
		}
	}

	/** Healing and prayer restores are read here, as rises in the boosted level. */
	void statChanged(StatChanged event)
	{
		boolean running = run.get() != null;

		if (event.getSkill() == Skill.PRAYER)
		{
			int was = prayerPoints;
			prayerPoints = event.getBoostedLevel();

			// No floor on the start: an Eldritch spec on an empty prayer book is the main case.
			if (running && prayerPoints > was)
			{
				rose(SpecEffect.Kind.PRAYER, prayerRegeneration, prayerRegenerationPeriod(),
					was, prayerPoints, event.getLevel());
			}
		}
		else if (event.getSkill() == Skill.HITPOINTS)
		{
			int was = hitpoints;
			hitpoints = event.getBoostedLevel();

			if (running && hitpoints > was)
			{
				rose(SpecEffect.Kind.HEAL, hitpointsRegeneration, hitpointsRegenerationPeriod(),
					was, hitpoints, event.getLevel());
			}
		}
	}

	/** Offers a rise to the tracker with natural regeneration taken out. */
	private void rose(SpecEffect.Kind kind, Regeneration regeneration, int period, int from, int to,
		int natural)
	{
		int rise = to - from;
		int tick = client.getTickCount();
		boolean spare = combatTracker.wouldCredit(kind, rise, tick) == null;
		int gain = regeneration.without(from, to, natural, tick, period, spare);

		if (gain < rise && config.debugLogging())
		{
			log.debug("{} of {} at tick {} has {} that came back on its own in it", kind, rise, tick,
				rise - gain);
		}

		if (gain <= 0)
		{
			return;
		}

		logAttribution(kind, gain, tick);

		if (kind == SpecEffect.Kind.PRAYER)
		{
			combatTracker.prayerGained(gain, tick);
		}
		else
		{
			combatTracker.healed(gain, tick);
		}
	}

	/** 0 while no prayer regeneration dose is in effect. */
	private int prayerRegenerationPeriod()
	{
		return client.getVarbitValue(VarbitID.PRAYER_REGENERATION_POTION_TIMER) > 0
			? PRAYER_REGEN_PERIOD
			: 0;
	}

	/** Rapid Heal or a hitpoints/max cape doubles the rate; a regen bracelet doubles it again. */
	private int hitpointsRegenerationPeriod()
	{
		int rate = 1;

		if (client.getVarbitValue(VarbitID.PRAYER_RAPIDHEAL) == 1 || isWearingRegenCape())
		{
			rate *= 2;
		}

		if (items.equipped(EquipmentInventorySlot.GLOVES) == ItemID.JEWL_BRACELET_REGEN)
		{
			rate *= 2;
		}

		return HITPOINTS_REGEN_PERIOD / rate;
	}

	/** Max capes come in too many recolours for an id list, so they're matched by name. */
	private boolean isWearingRegenCape()
	{
		int itemId = items.equipped(EquipmentInventorySlot.CAPE);

		if (itemId == ItemID.SKILLCAPE_HITPOINTS || itemId == ItemID.SKILLCAPE_HITPOINTS_TRIMMED)
		{
			return true;
		}

		String name = items.name(itemId);
		return name != null && name.toLowerCase().contains("max cape");
	}

	/** Healing spell impacts on a target. Blood spells are read from chat instead. */
	void graphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();

		if (run.get() == null || actor == null)
		{
			return;
		}

		logSpotAnims(actor);

		if (actor == client.getLocalPlayer())
		{
			return;
		}

		for (int spotAnim : OTHER_HEAL_SPELL_IMPACTS)
		{
			if (actor.hasSpotAnim(spotAnim))
			{
				combatTracker.spellHit(CombatMetric.OTHER_SPELL_HEAL, client.getTickCount());
				return;
			}
		}
	}

	/** Which id a spell's impact uses can only be found in game, so every one is logged. */
	private void logSpotAnims(Actor actor)
	{
		if (!config.debugLogging())
		{
			return;
		}

		StringBuilder ids = new StringBuilder();

		for (ActorSpotAnim spotAnim : actor.getSpotAnims())
		{
			ids.append(ids.length() == 0 ? "" : ",").append(spotAnim.getId());
		}

		if (ids.length() > 0)
		{
			log.debug("Spotanim {} on {} at tick {}", ids, actor.getName(), client.getTickCount());
		}
	}

	/** A drop in special attack energy is a spec. Tracked outside runs too. */
	void specEnergyChanged(int energy)
	{
		boolean spent = specEnergy.spent(energy);

		if (run.get() == null || !spent)
		{
			return;
		}

		// Equipment isn't updated yet for this tick, so the weapon is read later.
		int tick = client.getTickCount();

		clientThread.invokeLater(() ->
		{
			DelveRun current = run.get();

			if (current == null)
			{
				return;
			}

			int itemId = items.equipped(EquipmentInventorySlot.WEAPON);
			SpecWeapon weapon = SpecWeapon.forItem(itemId, items.name(itemId));

			if (weapon == null)
			{
				log.debug("Special attack energy fell with nothing equipped, ignoring it");
				return;
			}

			weapon = weapon.fired(items.isMeleeWeapon(itemId));

			combatTracker.specFired(weapon, tick);
			log.debug("Special attack fired on delve {}: {} (item {} \"{}\") at tick {}",
				current.currentLevel(), weapon, itemId, items.name(itemId), tick);
		});
	}

	/** Boss prayer and weapon in hand are both settled at the end of the tick. */
	void tickEnded()
	{
		if (run.get() == null)
		{
			return;
		}

		int tick = client.getTickCount();
		boolean praying = isBossPraying();

		if (praying != punishTracker.isPraying() && config.debugLogging())
		{
			log.debug("Boss overhead {} {} at tick {}",
				boss == null ? "gone" : Arrays.toString(boss.getOverheadArchiveIds()),
				boss == null ? "" : Arrays.toString(boss.getOverheadSpriteIds()), tick);
		}

		punishTracker.tickEnded(tick, praying, this::equippedPunishWeapon);
	}

	/** The only overhead the boss uses is the one a punish answers, so any icon will do. */
	private boolean isBossPraying()
	{
		if (boss == null)
		{
			return false;
		}

		int[] overheads = boss.getOverheadArchiveIds();

		if (overheads == null)
		{
			return false;
		}

		for (int overhead : overheads)
		{
			if (overhead >= 0)
			{
				return true;
			}
		}

		return false;
	}

	/** The weapon in hand as a punish weapon, or null for an empty hand or anything but melee. */
	private PunishWeapon equippedPunishWeapon()
	{
		int itemId = items.equipped(EquipmentInventorySlot.WEAPON);

		if (itemId <= 0)
		{
			return null;
		}

		String name = items.name(itemId);
		PunishWeapon weapon = PunishWeapon.forItem(itemId, name);

		if (weapon == PunishWeapon.OTHER && !items.isMeleeWeapon(itemId))
		{
			weapon = null;
		}

		if (config.debugLogging())
		{
			log.debug("Swing read at tick {}: {} (item {} \"{}\")", client.getTickCount(),
				weapon == null ? "not melee" : weapon, itemId, name);
		}

		return weapon;
	}
}
