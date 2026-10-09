package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
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

	/** A rise in hitpoints or prayer, waiting on the end of its tick. */
	private static final class Rise
	{
		private final SpecEffect.Kind kind;
		private final int from;
		private final int to;
		private final int natural;
		private final int period;

		private Rise(SpecEffect.Kind kind, int from, int to, int natural, int period)
		{
			this.kind = kind;
			this.from = from;
			this.to = to;
			this.natural = natural;
			this.period = period;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final DoomMetricsConfig config;
	private final GameItems items;
	private final Supplier<DelveRun> run;

	private final CombatTracker combatTracker;
	private final PunishTracker punishTracker;
	private final ThrallTracker thrallTracker = new ThrallTracker();

	/** Held from its spawn; the same NPC across its standing, shielded and burrowed forms. */
	private NPC boss;

	private final SpecEnergy specEnergy = new SpecEnergy();

	// Last seen values, so a change can be read as a difference.
	private int prayerPoints;
	private int hitpoints;
	private int hitpointsXp;

	/** The hitpoints experience gained so far on a tick: what the attack made on it earned. */
	private int experience;
	private int experienceTick;

	/**
	 * Damage splatted on us this tick and not yet taken out of the hitpoints level. The splat comes
	 * just ahead of the drop it causes, on the same tick, so a heal on that tick is the change
	 * plus this.
	 */
	private int taken;
	private int takenTick;

	private final Regeneration prayerRegeneration = new Regeneration();
	private final Regeneration hitpointsRegeneration = new Regeneration();
	private final Consumables consumables = new Consumables();

	/** How many of each item the inventory held when it was last sent; null until it is read. */
	private Map<Integer, Integer> carried;

	/** Whether the player has died this run: what then leaves the inventory was not eaten. */
	private boolean dead;

	/**
	 * The tick's rises, settled at its end: what was eaten or drunk on the tick is only known
	 * once the whole tick is in, whichever of the two the game sent first.
	 */
	private final List<Rise> rises = new ArrayList<>();

	/** The tick of the last spec, whose windows only open once its tick has ended. */
	private int specTick = -1;

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
		this.punishTracker = new PunishTracker(sink, new HandedBack());
	}

	/** Forgets everything, including the levels last seen. */
	void reset()
	{
		stopTracking();
		boss = null;
		specEnergy.forget();
		prayerPoints = 0;
		hitpoints = 0;
		hitpointsXp = 0;
		taken = 0;
		carried = null;
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
		dead = true;
	}

	/** Forgets every cause in flight. */
	void stopTracking()
	{
		combatTracker.reset();
		punishTracker.reset();
		thrallTracker.reset();
		consumables.reset();
		rises.clear();
		dead = false;
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
		hitpointsXp = client.getSkillExperience(Skill.HITPOINTS);
		carried = items.carried();
	}

	/**
	 * Food and potions leaving the inventory: a heal or a restore on the same tick is theirs, or
	 * partly theirs. A dropped one reads the same, which costs nothing unless a heal lands with it.
	 */
	void itemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		Map<Integer, Integer> before = carried;
		carried = GameItems.count(event.getItemContainer());

		// A death empties the inventory as the respawn restores every level.
		if (before == null || carried == null || run.get() == null || dead)
		{
			return;
		}

		int tick = client.getTickCount();

		for (Map.Entry<Integer, Integer> held : before.entrySet())
		{
			int gone = held.getValue() - carried.getOrDefault(held.getKey(), 0);
			Boolean drunk = gone > 0 ? items.isDrunk(held.getKey()) : null;

			if (drunk == null)
			{
				continue;
			}

			String name = withoutDose(items.name(held.getKey()));

			for (int i = 0; i < gone; i++)
			{
				consumables.consumed(name, drunk, tick);
			}

			if (config.debugLogging())
			{
				log.debug("{} {} at tick {}", drunk ? "Drank" : "Ate", name, tick);
			}
		}
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

		// Healing is read off the hitpoints level instead, with the hits taken on the tick added
		// back - see taken.
		if (target == client.getLocalPlayer())
		{
			if (config.debugLogging())
			{
				log.debug("Hitsplat {} of type {} on us at tick {}", hitsplat.getAmount(),
					hitsplat.getHitsplatType(), tick);
			}

			if (hitsplat.getHitsplatType() != HitsplatID.HEAL && hitsplat.getAmount() > 0)
			{
				taken = (takenTick == tick ? taken : 0) + hitsplat.getAmount();
				takenTick = tick;
			}

			return;
		}

		boolean onBoss = isCountedBoss(target);

		if (onBoss)
		{
			logBossHitsplat(hitsplat, tick);
		}

		if (isThrallSplat(hitsplat) && thrallTracker.isThrallHit(hitsplat.getAmount(), tick, onBoss))
		{
			if (config.debugLogging())
			{
				log.debug("Thrall hit {} at tick {}{}, not counted", hitsplat.getAmount(), tick,
					onBoss ? "" : " off the boss");
			}

			return;
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
			punishTracker.ownHitNotHeld(tick, onBoss);

			// A zero rather than skipped, so the spec's budget is spent on this hit.
			int amount = countsAsDamage(target) ? hitsplat.getAmount() : 0;
			logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
			combatTracker.damaged(amount, tick);
		}
	}

	/** A thrall's hit or miss is drawn as ours; it never brings a punish bonus splat. */
	private static boolean isThrallSplat(Hitsplat hitsplat)
	{
		int type = hitsplat.getHitsplatType();
		return hitsplat.isMine() && (type == HitsplatID.DAMAGE_ME || type == HitsplatID.BLOCK_ME);
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

		log.debug("Boss hitsplat {} of type {} ({}) at tick {}{}", hitsplat.getAmount(),
			hitsplat.getHitsplatType(),
			hitsplat.isMine() ? "mine" : hitsplat.isOthers() ? "others" : "neither", tick,
			punishTracker.mayBePunish(tick) ? ", held for the punish check" : "");
	}

	/** Hits held for the punish check, back for the spec tracker. */
	private final class HandedBack implements PunishTracker.Handback
	{
		@Override
		public void damaged(int amount, int tick)
		{
			logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
			combatTracker.damaged(amount, tick);
		}

		@Override
		public void strayed(int amount, int tick, int swing)
		{
			CombatMetric metric = combatTracker.damagedBefore(amount, tick, swing);

			if (config.debugLogging())
			{
				log.debug("DAMAGE of {} at tick {}, from before the swing at {} -> {}", amount, tick,
					swing, metric == null ? "nothing open" : metric.key());
			}
		}

		@Override
		public void punished(int tick, int swing)
		{
			CombatMetric metric = combatTracker.spent(tick, swing);

			if (metric != null && config.debugLogging())
			{
				log.debug("Punish hit at tick {} spent a hit of {}", tick, metric.key());
			}
		}

		@Override
		public int tookForThrall(int tick)
		{
			return thrallTracker.tookOnBoss(tick);
		}

		@Override
		public void notThralls(int amount, int tick)
		{
			thrallTracker.handBack();
			log.debug("Punish at tick {}: the {} the thrall's window took is the swing's by its"
				+ " experience", tick, amount);
		}

		@Override
		public void thralls(int amount, int tick)
		{
			log.debug("Punish at tick {}: a {} is the thrall's by the swing's experience, not"
				+ " counted", tick, amount);
		}
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

		if (actor instanceof NPC)
		{
			int id = ((NPC) actor).getId();
			ThrallTracker.Style style = ThrallTracker.attackStyle(id, animation);

			if (style != null)
			{
				thrallTracker.attacked(style, tick);

				if (config.debugLogging())
				{
					log.debug("Thrall {} attacked at tick {}", style, tick);
				}
			}
			else if (ThrallTracker.isSummoning(animation))
			{
				// A ghost's first attack is never animated, so it is expected from here.
				thrallTracker.spawned(id, tick);

				if (config.debugLogging())
				{
					log.debug("Thrall spawned: {} at tick {}", id, tick);
				}
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

	/** A potion's doses are one thing to learn, not four: "Saradomin brew(3)" is a Saradomin brew. */
	static String withoutDose(String name)
	{
		return name == null ? "" : name.replaceFirst("\\(\\d\\)$", "");
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
				rises.add(new Rise(SpecEffect.Kind.PRAYER, was, prayerPoints, event.getLevel(),
					prayerRegenerationPeriod()));
			}
		}
		else if (event.getSkill() == Skill.HITPOINTS)
		{
			int tick = client.getTickCount();
			experienceChanged(event.getXp(), running, tick);
			int hit = takenTick == tick ? taken : 0;
			int was = hitpoints;
			hitpoints = event.getBoostedLevel();

			// One update carries the whole tick, so the hits taken are all in this one.
			taken = 0;

			// Where the level would be had nothing healed; what it is above that was healed.
			int unhealed = Math.max(0, was - hit);

			if (running && hitpoints > unhealed)
			{
				if (hit > 0 && config.debugLogging())
				{
					log.debug("Hitpoints went {} -> {} at tick {} with {} taken", was, hitpoints,
						tick, hit);
				}

				rises.add(new Rise(SpecEffect.Kind.HEAL, unhealed, hitpoints, event.getLevel(),
					hitpointsRegenerationPeriod()));
			}
			else if (running && hitpoints < was && config.debugLogging())
			{
				log.debug("Hitpoints fell by {} at tick {}", was - hitpoints, tick);
			}
		}
	}

	/** Every attack earns hitpoints experience in proportion to its damage - thralls' don't. */
	private void experienceChanged(int xp, boolean running, int tick)
	{
		int gained = xp - hitpointsXp;
		boolean known = hitpointsXp > 0;
		hitpointsXp = xp;

		if (!running || !known || gained <= 0)
		{
			return;
		}

		experience = (experienceTick == tick ? experience : 0) + gained;
		experienceTick = tick;
		punishTracker.experienceGained(gained, tick);

		if (config.debugLogging())
		{
			log.debug("Hitpoints experience +{} at tick {}", gained, tick);
		}
	}

	/**
	 * Offers the tick's rises to the tracker, with natural regeneration and what was eaten or drunk
	 * on the tick taken out. A level with room to rise that didn't says what was taken gives none.
	 */
	private void settleRises(int tick)
	{
		boolean healed = false;
		boolean restored = false;

		for (Rise rise : rises)
		{
			healed |= rise.kind == SpecEffect.Kind.HEAL;
			restored |= rise.kind == SpecEffect.Kind.PRAYER;
			settle(rise, tick);
		}

		rises.clear();

		if (!consumables.anyAt(tick))
		{
			return;
		}

		if (!healed && hitpoints < client.getRealSkillLevel(Skill.HITPOINTS))
		{
			consumables.didNotRise(SpecEffect.Kind.HEAL, tick);
			log.debug("Hitpoints had room and did not rise with what was taken at tick {}", tick);
		}

		if (!restored && prayerPoints < client.getRealSkillLevel(Skill.PRAYER))
		{
			consumables.didNotRise(SpecEffect.Kind.PRAYER, tick);
			log.debug("Prayer had room and did not rise with what was taken at tick {}", tick);
		}
	}

	private void settle(Rise rise, int tick)
	{
		SpecEffect.Kind kind = rise.kind;
		Regeneration regeneration = kind == SpecEffect.Kind.PRAYER
			? prayerRegeneration
			: hitpointsRegeneration;
		int size = rise.to - rise.from;
		boolean spare = combatTracker.wouldCredit(kind, size, tick) == null;
		int gain = regeneration.without(rise.from, rise.to, rise.natural, tick, rise.period, spare);

		if (gain < size && config.debugLogging())
		{
			log.debug("{} of {} at tick {} has {} that came back on its own in it", kind, size, tick,
				size - gain);
		}

		if (gain <= 0)
		{
			return;
		}

		// A spec made this tick opens its windows after this, and its heal is among these.
		boolean unexplained = specTick != tick
			&& combatTracker.wouldCredit(kind, gain, tick) == null;
		int left = (int) consumables.without(kind, gain, tick, unexplained,
			rise.to >= rise.natural);

		if (left < gain && config.debugLogging())
		{
			log.debug("{} of {} at tick {} has {} from what was eaten or drunk in it", kind, gain,
				tick, gain - left);
		}

		if (left <= 0)
		{
			return;
		}

		logAttribution(kind, left, tick);

		if (kind == SpecEffect.Kind.PRAYER)
		{
			combatTracker.prayerGained(left, tick);
		}
		else
		{
			combatTracker.healed(left, tick);
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
		specTick = tick;

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

			// The experience comes ahead of this, with the rest of the tick the spec was made on.
			combatTracker.specFired(weapon, tick, leastHit(weapon),
				experienceTick == tick ? experience : 0);
			log.debug("Special attack fired on delve {}: {} (item {} \"{}\") at tick {}",
				current.creditLevel(), weapon, itemId, items.name(itemId), tick);
		});
	}

	/**
	 * A Zaryte crossbow spec with ruby bolts takes a share of the boss's hitpoints, so a small hit
	 * in its window is something else's. 0 for any other spec.
	 */
	private int leastHit(SpecWeapon weapon)
	{
		int ammo = items.equipped(EquipmentInventorySlot.AMMO);

		if (weapon != SpecWeapon.ZARYTE_CROSSBOW || !SpecWeapon.isRubyBolt(ammo, items.name(ammo)))
		{
			return 0;
		}

		int bossHitpoints = client.getVarbitValue(VarbitID.HPBAR_HUD_HP);
		int least = SpecWeapon.leastRubyBoltHit(bossHitpoints);

		if (config.debugLogging())
		{
			log.debug("Ruby bolts with the boss on {} hitpoints: a hit under {} is not the bolt's",
				bossHitpoints, least);
		}

		return least;
	}

	/** Boss prayer and weapon in hand are both settled at the end of the tick. */
	void tickEnded()
	{
		if (run.get() == null)
		{
			return;
		}

		int tick = client.getTickCount();
		settleRises(tick);

		boolean praying = isBossPraying();

		if (praying != punishTracker.isPraying() && config.debugLogging())
		{
			log.debug("Boss overhead {} {} at tick {}",
				boss == null ? "gone" : Arrays.toString(boss.getOverheadArchiveIds()),
				boss == null ? "" : Arrays.toString(boss.getOverheadSpriteIds()), tick);
		}

		punishTracker.tickEnded(tick, praying, this::equippedPunishWeapon,
			this::experiencePerDamage);
	}

	/**
	 * What a point of damage on the boss earns, by the hitpoints its bar shows; 0 while the bar is
	 * the shield's or not up.
	 */
	private double experiencePerDamage()
	{
		return ExperienceRate.perDamage(client.getVarbitValue(VarbitID.HPBAR_HUD_BASEHP));
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
