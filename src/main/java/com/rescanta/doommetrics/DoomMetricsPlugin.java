package com.rescanta.doommetrics;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Doom of Mokhaiotl Metrics",
	description = "Times each delve and shows your deep delve completions per hour",
	tags = {"doom", "mokhaiotl", "delve", "timer", "pace", "pvm"}
)
public class DoomMetricsPlugin extends Plugin
{
	/**
	 * The Doom varplayers sit in a contiguous block. Only DOM_CURRENT_LEVEL_TEMP drives behaviour;
	 * the rest are logged under the debug toggle so timings can be diagnosed from a real run.
	 */
	private static final int DOM_VARP_FIRST = VarPlayerID.DOM_LAST_DELVE_LEVEL;
	private static final int DOM_VARP_LAST = VarPlayerID.DOM_CURRENT_LEVEL_TEMP;

	/**
	 * How long the boss may be absent before a run that has not cleared a single delve is given up
	 * on. This is the only way to notice someone walking straight back out of delve 1, which clears
	 * no varplayer and posts no chat message.
	 */
	private static final int ABANDON_TICKS = 100;

	private static final String CLEAR_OPTION = "Clear";

	/**
	 * The drops worth writing against a run. Everything else the Doom hands out is supplies and
	 * currency that say nothing about how the trip went, and listing all of it would bury the one
	 * line anybody will ever go looking for.
	 *
	 * <p>Both forms of the eye are here because only one of them is the drop and which is not
	 * something this can check from outside the game. Listing both costs nothing - the other
	 * simply never appears in a loot pile - and listing the wrong one alone would silently miss
	 * the rarest drop in the place.
	 */
	private static final Set<Integer> NOTABLE_DROPS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(
			ItemID.EYE_OF_AYAK,
			ItemID.EYE_OF_AYAK_UNCHARGED,
			ItemID.AVERNIC_TREADS,
			ItemID.MOKHAIOTL_CLOTH,
			ItemID.DOMPET)));

	/**
	 * How the game announces a pet, depending on whether it had room to walk out beside you.
	 * Matched on the opening words because the rest of each line varies.
	 */
	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You have a funny feeling like you would have been followed",
		"You feel something weird sneaking into your backpack"
	};

	/**
	 * How long one of our handlers may take before it is worth a line in the log.
	 *
	 * <p>Every handler here runs on the client thread, so its time is time the game loop is not
	 * running. A game tick is 600ms and a frame at 50fps is 20ms, which makes five a threshold
	 * quiet enough never to fire in normal play and small enough to catch anything a player could
	 * see. A lone report is more likely a garbage collection landing inside a handler than work we
	 * did; a repeated one is ours.
	 */
	private static final long SLOW_HANDLER_NANOS = Duration.ofMillis(5).toNanos();

	/**
	 * How long a gap between runs ends the session.
	 *
	 * <p>A session is a sitting, not a login: banking, restocking and walking back are all part of
	 * the sitting, and a run started straight after the last one carries on the same figure. Half
	 * an hour is long enough that nothing you do between two runs will break it, and short enough
	 * that coming back to the game tomorrow starts you clean rather than averaging you against
	 * yesterday.
	 *
	 * <p>This only decides which runs share a session figure. Nothing is lost when one ends - every
	 * run is banked to the lifetime total as it finishes, whatever session it belonged to.
	 */
	private static final Duration SESSION_IDLE = Duration.ofMinutes(30);

	/**
	 * What the game says when a blood spell takes health off something and gives it to you.
	 *
	 * <p>This is the signal, and it took a run's log to find out why nothing else was. The heal is
	 * not a hitsplat, the cast animation is shared by every ancient spell, and the impact graphic -
	 * the obvious answer, and the one tried first - is never reported to us at all: a whole session
	 * of barraging produced not one blood impact on any actor or on the ground. The line in the
	 * chat is the only thing the game says out loud, and it says it on the tick of the heal, every
	 * time.
	 *
	 * <p>Matched on the middle of the line because it comes both ways round - "your opponent's"
	 * for one target and "your opponents'" for several - and the apostrophe moves between them.
	 *
	 * <p>What it does not say is which blood spell it was, so all of them read as the barrage. The
	 * amulet of blood fury says the same line off a melee hit and would land here too. Both are the
	 * right way round for the only place this plugin runs: a delve is barrage territory, nobody
	 * brings a fury to it, and a figure that is occasionally something else is worth far more than
	 * a row that stays at zero.
	 */
	private static final String BLOOD_DRAIN = "drain some of your opponent";

	/**
	 * Healing spell impacts still worth watching on an actor, for the spells that heal without
	 * saying so - the Sanguinesti staff above all.
	 *
	 * <p>The blood spells are listed too, as a fallback for a version of the game that reports what
	 * this one does not. Only graphics a player can produce are here: the ids that merely have
	 * "blood" in the name belong to NPC attacks and quest scenes, and a boss playing one of its own
	 * would open a window that then took the credit for a real heal.
	 */
	private static final int[] OTHER_HEAL_SPELL_IMPACTS = {
		SpotanimID.BLOOD_RUSH_IMPACT,
		SpotanimID.SPELL_BLOOD_BURST_IMPACT,
		SpotanimID.BLOOD_BLITZ_IMPACT,
		SpotanimID.SANGUINESTI_STAFF_IMPACT,
		SpotanimID.SANGUINESTI_STAFF_IMPACT_JUSTICIAR
	};

	/** The name of the thing whose damage is not counted - see {@link #countsAsDamage}. */
	private static final String VOLATILE_EARTH = "volatile earth";

	@Inject
	private Client client;

	@Inject
	private DoomMetricsConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DoomMetricsOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private MilestoneStore milestoneStore;

	@Inject
	private TotalsStore totalsStore;

	@Inject
	private RunHistoryStore runHistoryStore;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	private DoomMetricsPanel panel;
	private NavigationButton navButton;
	/** Read on the Swing thread when the history window is built, cleared on shutdown. */
	private volatile BufferedImage icon;

	/**
	 * The history window while it is open, or null. Swing thread only - it is created, read and
	 * disposed there, so the client thread never touches a frame mid-layout.
	 */
	private HistoryWindow historyWindow;

	/**
	 * The last milestone snapshot pushed to the Swing thread, so a window opening between two runs
	 * has a table to draw without reading the model the client thread owns. Swing thread only.
	 */
	private List<MilestoneTablePanel.Row> tableRows = Collections.emptyList();

	/**
	 * The history the open window's chart is drawing, so a run finishing while it is up can be
	 * added without re-reading the file. Swing thread only.
	 */
	private RunSeries chartSeries = RunSeries.empty();

	/**
	 * The last lifetime combat snapshot pushed to the Swing thread, so a window opening between two
	 * runs has figures to draw without reading the tally the client thread owns. Swing thread only.
	 */
	private CombatTotals windowCombat = new CombatTotals();

	/** This character's lifetime table, reloaded whenever the profile changes. */
	private final MilestoneTable milestones = new MilestoneTable();

	/** Milestones whose personal best has been beaten since the client started. */
	private final Set<Integer> improvedThisSession = new HashSet<>();

	/** This character's lifetime deep delve rate, reloaded whenever the profile changes. */
	private DelveTotals lifetime = new DelveTotals();

	/** The runs of the current sitting. See {@link #SESSION_IDLE}. */
	private DelveTotals session = new DelveTotals();

	/** This character's lifetime healing, prayer and spec damage, banked run by run. */
	private CombatTotals lifetimeCombat = new CombatTotals();

	/** The same figures for the current sitting, thrown away when the sitting is. */
	private CombatTotals sessionCombat = new CombatTotals();

	/**
	 * Works out what caused each heal, prayer restore and spec hitsplat. Fed only while a run is in
	 * progress, so nothing that happens outside the cave is ever counted.
	 */
	private final CombatTracker combatTracker = new CombatTracker(this::recordCombat);

	/**
	 * The special attack energy as we last saw it. A spec is a drop in this - it only ever climbs
	 * on its own - and the weapon held when it drops is what fired.
	 */
	private int specEnergy;

	/** The boosted prayer level as we last saw it, so a restore can be read as the difference. */
	private int prayerPoints;

	/** The boosted hitpoints level as we last saw it, for the same reason. */
	private int hitpoints;

	/**
	 * When the session's most recent run ended, or null while one is in progress or before the
	 * session has any run in it at all. What {@link #SESSION_IDLE} is measured from.
	 */
	private Instant sessionEndedAt;

	/**
	 * When the session's first run started, or null before the session has a run in it at all.
	 * What the panel's session length is measured from.
	 *
	 * <p>Wall clock rather than summed run time, because it answers a different question from the
	 * rate under it: the rate is what the time inside runs bought, and the length is how long you
	 * have been at it - banking, restocking and walking back included, because those are the
	 * sitting too. Reading the two together is what tells you where an evening went.
	 */
	private Instant sessionStartedAt;

	/** The character the session belongs to, so an alt's runs never join it. */
	private String sessionProfile;

	private DelveRun run;

	/** The last run that ended for a reason worth showing, kept for the linger window. */
	private DelveRun lastRun;

	/**
	 * Set while {@link #run} is being held open across a lost connection, and null the rest of the
	 * time. See {@link ResumeCheck}.
	 */
	private ResumeCheck resumeCheck;

	/**
	 * The character {@link #run} was started on, so the run is written to their history even if the
	 * client is logged in as somebody else by the time it ends.
	 */
	private String runProfile;

	/**
	 * When this session logged in, if we were watching at the time. Used to bound the start of a
	 * run we joined part way through - see {@link DelveRun#pbElapsed()}.
	 */
	private Instant loginAt;

	/** What the live section last drew, so an unchanged tick costs nothing. */
	private String lastLiveKey;

	private int bossCount;
	private int ticksWithoutBoss;

	@Provides
	DoomMetricsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoomMetricsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);

		icon = ImageUtil.loadImageResource(DoomMetricsPlugin.class, "panel_icon.png");
		panel = new DoomMetricsPanel(this::openHistoryWindow);
		navButton = NavigationButton.builder()
			.tooltip("Doom Metrics")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		reset();
		loadMilestones();
		loadTotals();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		icon = null;

		// The window outlives the side panel unless it is taken down explicitly, and a disabled
		// plugin leaving a frame on screen would go on drawing data it no longer maintains.
		SwingUtilities.invokeLater(this::closeHistoryWindow);

		reset();
	}

	private void reset()
	{
		run = null;
		lastRun = null;
		resumeCheck = null;
		runProfile = null;
		loginAt = null;
		bossCount = 0;
		ticksWithoutBoss = 0;
		session = new DelveTotals();
		sessionCombat = new CombatTotals();
		combatTracker.reset();
		specEnergy = 0;
		prayerPoints = 0;
		hitpoints = 0;
		sessionEndedAt = null;
		sessionStartedAt = null;
		sessionProfile = null;
	}

	/**
	 * The run the overlay should draw: the live one, or the last finished one while it is still
	 * inside the linger window.
	 */
	DelveRun getDisplayRun()
	{
		if (run != null)
		{
			return run;
		}

		if (lastRun == null || lastRun.getEndedAt() == null)
		{
			return null;
		}

		Duration linger = Duration.ofMinutes(config.resultLingerMinutes());

		if (linger.isZero() || Duration.between(lastRun.getEndedAt(), Instant.now()).compareTo(linger) >= 0)
		{
			return null;
		}

		return lastRun;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		long started = System.nanoTime();
		handleChatMessage(event);
		reportSlow("onChatMessage", started);
	}

	private void handleChatMessage(ChatMessage event)
	{
		// Ahead of the type check: the drain line is spam, not a game message, and spam is where
		// it will stay.
		if (run != null && event.getMessage().contains(BLOOD_DRAIN))
		{
			combatTracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, client.getTickCount());
			log.debug("Blood spell drained at tick {}", client.getTickCount());
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (isPetMessage(event.getMessage()))
		{
			petDropped();
			return;
		}

		DelveMessage delve = DelveMessage.parse(event.getMessage());

		if (delve == null)
		{
			return;
		}

		if (delve.isCleared())
		{
			delveCleared(delve.getLevel(), delve.getFight());
		}
		else
		{
			delveStarted(delve.getLevel());
		}
	}

	private static boolean isPetMessage(String message)
	{
		for (String prefix : PET_MESSAGES)
		{
			if (message.startsWith(prefix))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Records the pet against the run in progress.
	 *
	 * <p>The pet is the one drop the loot pile cannot be relied on for: it is handed over the
	 * moment it rolls rather than waiting to be claimed with the rest, so this chat line is the
	 * only sight of it there is. Should it turn up in the pile as well, keying loot by item id
	 * means the run still only counts it once.
	 *
	 * <p>Attributing it to the Doom needs no further checking, because there is nothing else to
	 * attribute it to - a pet rolled while a delve is in progress came from the thing being fought.
	 */
	private void petDropped()
	{
		if (run == null)
		{
			return;
		}

		recordLoot(ItemID.DOMPET, 1);
		log.debug("Pet dropped on delve {}", run.currentLevel());
	}

	/**
	 * Takes the notable drops out of the Doom's loot pile and records them against the run.
	 *
	 * <p>The pile is only read when the loot in it is being claimed, never on a schedule, because
	 * an unclaimed pile is not yet yours - dying loses it. Reading it as it went would write down
	 * drops the player never walked out with.
	 */
	private void claimLootPile()
	{
		if (run == null)
		{
			return;
		}

		ItemContainer pile = client.getItemContainer(InventoryID.DOM_LOOTPILE);

		if (pile == null)
		{
			return;
		}

		// Totalled across the pile before anything is recorded, because two of the same unique are
		// two separate slots holding one each, and reporting them one slot at a time would look
		// like the same single drop seen twice.
		Map<Integer, Integer> claimed = new LinkedHashMap<>();

		for (Item item : pile.getItems())
		{
			if (NOTABLE_DROPS.contains(item.getId()))
			{
				claimed.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}

		claimed.forEach((itemId, quantity) ->
		{
			recordLoot(itemId, quantity);
			log.debug("Loot pile holds {} x item {} on delve {}",
				quantity, itemId, run.currentLevel());
		});
	}

	/** Names the item from the cache, so the history is not pinned to names hardcoded here. */
	private void recordLoot(int itemId, int quantity)
	{
		ItemComposition item = itemManager.getItemComposition(itemId);
		run.recordLoot(itemId, item == null ? null : item.getName(), quantity);
	}

	/**
	 * Delve 1 is the only unambiguous "you just walked into the cave" signal there is, so it always
	 * begins a fresh run. Anything deeper either continues the run we are watching or, if we were
	 * enabled part way through a trip, starts a partial one.
	 */
	private void delveStarted(int level)
	{
		if (level <= 1 || run == null)
		{
			startRun(Instant.now(), level, level > 1);
		}
		else
		{
			run.enterLevel(level);
			log.debug("Delve {} started", level);
		}
	}

	private void delveCleared(int level, Duration fight)
	{
		if (run == null)
		{
			// First thing we saw was a clear, so back-date the start to when that fight began.
			startRun(Instant.now().minus(fight), level, true);
		}

		if (level <= run.lastLevel())
		{
			log.debug("Ignoring repeat completion for delve {}", level);
			return;
		}

		DelveRun.Split split = run.complete(level, Instant.now(), fight);
		log.debug("Delve {} cleared in {} (segment {})",
			level, DoomFormat.preciseDuration(fight), DoomFormat.duration(split.segment));

		announceInterval(level, split);
		recordMilestone(level);
	}

	/**
	 * Banks a clear of every tenth delve into the lifetime table. The kill count goes up whatever
	 * the circumstances; the time only wins if it beats what is stored, and a run we joined part
	 * way through offers a deliberately pessimistic one that will rarely do so.
	 */
	private void recordMilestone(int level)
	{
		if (!MilestoneTable.isMilestone(level))
		{
			return;
		}

		int ticks = DoomFormat.toTicks(run.pbElapsed());

		if (milestones.record(level, ticks))
		{
			improvedThisSession.add(level);
			log.debug("Delve {} personal best is now {} ticks", level, ticks);
		}

		milestoneStore.save(milestones);
		refreshTable();
	}

	/**
	 * Claiming loot and leaving are both explicit "I am done" clicks, and they are the only end
	 * signal for a trip that never descended past delve 1: DOM_CURRENT_LEVEL_TEMP stays at zero
	 * throughout delve 1, so it has no transition to fire on.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		long started = System.nanoTime();
		handleMenuOptionClicked(event);
		reportSlow("onMenuOptionClicked", started);
	}

	private void handleMenuOptionClicked(MenuOptionClicked event)
	{
		if (run == null)
		{
			return;
		}

		int widgetId = event.getParam1();
		boolean claiming = widgetId == InterfaceID.DomEndLevelUi.BTN_CLAIM;

		if (!claiming && widgetId != InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			return;
		}

		if (claiming)
		{
			// Read here rather than waiting for the claim script, which lands after this click and
			// so after the run has been closed out and written - by which point there is no run
			// left to hang the drops on.
			claimLootPile();
		}

		endRun(EndReason.FINISHED, -1);
	}

	/**
	 * The game's own signal that the loot has been claimed.
	 *
	 * <p>Claiming is the moment the pile becomes yours - leave any other way, or die, and it stays
	 * behind - so it is the only moment worth reading the pile at, and this is the reading that is
	 * certain to be at it. The claim button is watched as well, because that click is what closes
	 * the run out and it cannot wait for this to arrive; between them one of the two is always in
	 * time, and taking the largest count seen means it costs nothing when both are.
	 */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		long started = System.nanoTime();
		handleScriptPreFired(event);
		reportSlow("onScriptPreFired", started);
	}

	private void handleScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.DOM_LOOT_CLAIM)
		{
			claimLootPile();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		long started = System.nanoTime();
		handleActorDeath(event);
		reportSlow("onActorDeath", started);
	}

	private void handleActorDeath(ActorDeath event)
	{
		if (run != null && event.getActor() == client.getLocalPlayer())
		{
			endRun(EndReason.DIED, run.currentLevel());
		}
	}

	/**
	 * Every hitsplat of yours on something else, offered to the tracker to identify. Only while a
	 * run is in progress: what your gear does in the bank is not what this plugin is about.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		long started = System.nanoTime();
		handleHitsplatApplied(event);
		reportSlow("onHitsplatApplied", started);
	}

	private void handleHitsplatApplied(HitsplatApplied event)
	{
		if (run == null)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		boolean onMe = event.getActor() == client.getLocalPlayer();
		int tick = client.getTickCount();

		// Damage only. Healing is read off the hitpoints level instead - see onStatChanged - and
		// reading it in both places would count every heal twice.
		if (!onMe && hitsplat.isMine())
		{
			// Passed on as a zero rather than skipped when it is a hit that does not count: the
			// spec still spent itself on it, and a budget left unspent would be taken by the
			// auto-attack behind it instead.
			int amount = countsAsDamage(event.getActor()) ? hitsplat.getAmount() : 0;
			logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
			combatTracker.damaged(amount, tick);
		}
	}

	/**
	 * Whether a hit on {@code target} is worth adding to a damage figure.
	 *
	 * <p>The volatile earth that comes up before the shockwaves is not a health bar anybody is
	 * racing. It guarantees a max hit, and two of them have to be broken to raise the shield that
	 * keeps you alive - which is what makes it the best thing in the delve to spend a blowpipe or
	 * an eldritch spec on, and what makes counting the damage misleading. A delve's spec damage
	 * would read as though the work had been done there.
	 *
	 * <p>Only the damage is dropped. The heal and the prayer that spec was fired for are counted
	 * exactly as they always were, which is the whole reason it was fired at that target.
	 */
	private boolean countsAsDamage(Actor target)
	{
		if (!(target instanceof NPC))
		{
			return true;
		}

		NPC npc = (NPC) target;
		String name = npc.getName();

		// By name as well as by id, for the same reason the spec weapons are: the id list is what
		// this version knew, and a form it did not know would quietly start counting again.
		if (npc.getId() == NpcID.DOM_SHOCKWAVE_SHIELD
			|| npc.getId() == NpcID.DOM_SHOCKWAVE_PATH_NODE
			|| (name != null && name.toLowerCase().contains(VOLATILE_EARTH)))
		{
			log.debug("Not counting damage to {} ({})", name, npc.getId());
			return false;
		}

		return true;
	}

	/**
	 * Says what the tracker is about to do with an effect, including when the answer is nothing.
	 *
	 * <p>A figure reading low is almost always something being dropped rather than something being
	 * miscounted, and a dropped effect leaves no other trace - so the line that says "this heal
	 * matched no open window" is the one worth having when a row will not move.
	 *
	 * <p>"Held" is not the same as dropped: a cause noticed later in the same tick still claims it,
	 * and a "Counted" line follows when one does.
	 */
	private void logAttribution(SpecEffect.Kind kind, int amount, int tick)
	{
		if (!config.debugLogging())
		{
			return;
		}

		CombatMetric metric = combatTracker.wouldCredit(kind, tick);
		log.debug("{} of {} at tick {} -> {}", kind, amount, tick,
			metric == null ? "nothing open, held for this tick" : metric.key());
	}

	/**
	 * Hitpoints and prayer points going up, offered to the tracker in case a spec or a spell put
	 * them there.
	 *
	 * <p>This is where healing is read, and it is not a matter of taste. A heal on your own head
	 * is not a hitsplat anybody outside the client ever sees - blood spells, the blowpipe's spec
	 * and Blood Sacrifice all simply raise the number, and a tracker watching for a green splat
	 * counted none of them. The prayer side proved it by working from the first run: the same
	 * signal, read the same way.
	 *
	 * <p>The level is tracked rather than the event's own figure because a restore is the size of
	 * the step, not the number it landed on. Only a rise is offered - drain and damage are not
	 * restores, and they still have to be recorded here so the next rise is measured from where
	 * they left off.
	 *
	 * <p>What this cannot see is a heal that had nowhere to go. Healing at full hitpoints moves no
	 * level and is counted as nothing, which is the honest reading - the gear gave back nothing
	 * that was not already there - and one more reason every figure here is a floor.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		long started = System.nanoTime();
		handleStatChanged(event);
		reportSlow("onStatChanged", started);
	}

	private void handleStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.PRAYER)
		{
			int was = prayerPoints;
			prayerPoints = event.getBoostedLevel();

			// No floor on where the rise started: an Eldritch spec fired on an empty prayer book is
			// exactly the case this is for, and a guard against zero would drop the one that matters
			// most. The run has to be in progress and the spec has to have been fired a tick or two
			// ago, which is what keeps the login flood out.
			if (run != null && prayerPoints > was)
			{
				int tick = client.getTickCount();
				logAttribution(SpecEffect.Kind.PRAYER, prayerPoints - was, tick);
				combatTracker.prayerGained(prayerPoints - was, tick);
			}
		}
		else if (event.getSkill() == Skill.HITPOINTS)
		{
			int was = hitpoints;
			hitpoints = event.getBoostedLevel();

			if (run != null && hitpoints > was)
			{
				int tick = client.getTickCount();
				logAttribution(SpecEffect.Kind.HEAL, hitpoints - was, tick);
				combatTracker.healed(hitpoints - was, tick);
			}
		}
	}

	/**
	 * A healing spell landing on something, for the spells that name themselves that way. The blood
	 * spells announce themselves in the chat instead - see {@link #BLOOD_DRAIN}.
	 */
	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		long started = System.nanoTime();
		handleGraphicChanged(event);
		reportSlow("onGraphicChanged", started);
	}

	private void handleGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();

		if (run == null || actor == null)
		{
			return;
		}

		// Logged for the caster too, even though only the target's graphics are attributed - if an
		// impact id turns out to be wrong, the player's own graphics are where the right one is.
		logSpotAnims(actor);

		if (actor == client.getLocalPlayer())
		{
			return;
		}

		if (hasAny(actor, OTHER_HEAL_SPELL_IMPACTS))
		{
			combatTracker.spellHit(CombatMetric.OTHER_SPELL_HEAL, client.getTickCount());
		}
	}

	/**
	 * Names every graphic on an actor while it changes.
	 *
	 * <p>Which id a spell's impact actually uses is the one thing here that cannot be established
	 * from outside the game: the cache has a dozen constants with "blood" in the name and no way to
	 * tell which of them a barrage plays today. This turns one run with the toggle on into the
	 * answer, and costs nothing with it off - RuneLite logs at INFO, where the line is never built.
	 */
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

	private static boolean hasAny(Actor actor, int[] spotAnims)
	{
		for (int spotAnim : spotAnims)
		{
			if (actor.hasSpotAnim(spotAnim))
			{
				return true;
			}
		}

		return false;
	}

	/** Credits an attributed amount to the run in progress. The tracker's only way out. */
	private void recordCombat(CombatMetric metric, long amount)
	{
		if (run == null)
		{
			return;
		}

		run.recordCombat(metric, amount);

		if (config.debugLogging())
		{
			log.debug("Counted {} to {} on delve {}", amount, metric.key(), run.currentLevel());
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		long started = System.nanoTime();
		handleVarbitChanged(event);
		reportSlow("onVarbitChanged", started);
	}

	private void handleVarbitChanged(VarbitChanged event)
	{
		int varpId = event.getVarpId();

		if (varpId == VarPlayerID.SA_ENERGY)
		{
			specialAttackEnergyChanged(event.getValue());
			return;
		}

		if (varpId < DOM_VARP_FIRST || varpId > DOM_VARP_LAST)
		{
			return;
		}

		if (config.debugLogging())
		{
			log.debug("Doom varp {} -> {}", varpId, event.getValue());
		}

		// The tick the game starts its own delve clock from. It lands a couple of seconds after the
		// chat line that opened the delve, so it is the anchor that makes our first segment agree
		// with the duration the game reports for that same delve.
		if (varpId == VarPlayerID.DOM_LEVEL_START_TIME && run != null
			&& run.reanchorStart(Instant.now()))
		{
			log.debug("Run start moved onto the delve {} start the game reported", run.currentLevel());
		}

		// Backstop for an exit that did not go through the end level panel. This only counts the
		// delves you descended to, so it sits at zero for the whole of delve 1 and has no
		// transition to offer for a trip that claimed and left without ever going deeper - that
		// case is caught by the button clicks instead.
		if (varpId == VarPlayerID.DOM_CURRENT_LEVEL_TEMP && event.getValue() == 0
			&& run != null && run.lastLevel() > 0)
		{
			endRun(EndReason.FINISHED, -1);
		}

		// Arrives in the login varp flood, which may land either side of the profile being ready.
		if (varpId == VarPlayerID.DOM_DEEPEST_LEVEL)
		{
			seedFromDeepestLevel();
		}
	}

	/**
	 * Notices a special attack being fired, and tells the tracker what fired it.
	 *
	 * <p>A drop in the energy is the signal. It is the only one that cannot be faked by something
	 * else: the bar climbs on its own and is only ever spent deliberately, so a fall means a spec,
	 * whatever weapon it was and whatever animation the weapon plays. The equipped weapon is read
	 * on the same tick, which is what tells a Zaryte spec from a blowpipe one.
	 *
	 * <p>Recorded even outside a run, so the first spec of a trip is measured against the energy we
	 * really had rather than against zero.
	 */
	private void specialAttackEnergyChanged(int energy)
	{
		int was = specEnergy;
		specEnergy = energy;

		if (run == null || energy >= was)
		{
			return;
		}

		// The tick the spec was fired on, captured now: this event runs before the player and the
		// equipment are updated for the tick, so the weapon has to be read a step later, by which
		// point the client's own tick counter may have moved on.
		int tick = client.getTickCount();

		clientThread.invokeLater(() ->
		{
			if (run == null)
			{
				return;
			}

			int itemId = equippedWeapon();
			SpecWeapon weapon = SpecWeapon.forItem(itemId, itemName(itemId));

			if (weapon == null)
			{
				// Nothing in hand, so whatever moved the bar was not a spec we can attribute.
				log.debug("Special attack energy fell with nothing equipped, ignoring it");
				return;
			}

			combatTracker.specFired(weapon, tick);
			log.debug("Special attack fired on delve {}: {} (item {} \"{}\") at tick {}",
				run.currentLevel(), weapon, itemId, itemName(itemId), tick);
		});
	}

	/** The item id in the weapon slot, or 0 when the slot is empty or unreadable. */
	private int equippedWeapon()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);

		if (worn == null)
		{
			return 0;
		}

		Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? 0 : weapon.getId();
	}

	/** An item's name from the cache, or null when there is nothing to name. */
	private String itemName(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}

		ItemComposition item = itemManager.getItemComposition(itemId);
		return item == null ? null : item.getName();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		long started = System.nanoTime();
		handleRuneScapeProfileChanged(event);
		reportSlow("onRuneScapeProfileChanged", started);
	}

	private void handleRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		closeRunFromAnotherCharacter();
		loadMilestones();
		loadTotals();
	}

	/**
	 * Closes out a run held open across a lost connection when the client comes back as somebody
	 * else. Only a held run can still be here to see this - anything else ended before the login
	 * screen did.
	 *
	 * <p>Left alone it would be settled by the alt's delve varplayer, which has nothing to say
	 * about where the character who made the run got to. It ends where we last saw it instead.
	 */
	private void closeRunFromAnotherCharacter()
	{
		if (resumeCheck == null || run == null || runProfile == null
			|| runProfile.equals(runHistoryStore.currentProfile()))
		{
			return;
		}

		log.debug("Back as another character, closing the run held open on the previous one");
		endRun(EndReason.FINISHED, -1);
	}

	private void loadMilestones()
	{
		if (!milestoneStore.hasProfile())
		{
			// Logged out, so there is no character to read. Leave the last table on show rather
			// than blanking the panel the moment you log out.
			return;
		}

		Map<Integer, MilestoneTable.Row> loaded = milestoneStore.load();
		milestones.replaceAll(loaded);
		improvedThisSession.clear();
		seedFromDeepestLevel();
		refreshTable();
	}

	private void loadTotals()
	{
		if (!totalsStore.hasProfile())
		{
			// Logged out, so there is no character to read. Leave the last figure on show rather
			// than blanking the panel the moment you log out.
			return;
		}

		DelveTotals loaded = totalsStore.load();
		lifetime = loaded == null ? new DelveTotals() : loaded;

		CombatTotals loadedCombat = totalsStore.loadCombat();
		lifetimeCombat = loadedCombat == null ? new CombatTotals() : loadedCombat;

		startSessionForCurrentCharacter();
		refreshLive();
		refreshLifetimeCombat();
	}

	/**
	 * Pushes the lifetime combat figures over to the Swing thread. Called from the client thread,
	 * which owns the tally, so a copy crosses rather than the tally itself.
	 */
	private void refreshLifetimeCombat()
	{
		CombatTotals totals = lifetimeCombat.copy();

		SwingUtilities.invokeLater(() ->
		{
			// Held so a window opened later has figures to draw without reaching back into a tally
			// the client thread may already be writing to again.
			windowCombat = totals;

			if (historyWindow != null)
			{
				historyWindow.setLifetimeCombat(totals);
			}
		});
	}

	/**
	 * Starts the session over when the client comes back as a different character.
	 *
	 * <p>Every other figure in the panel is the logged in character's own, and a session rate that
	 * had quietly summed two players' runs together would belong to neither of them. Only a change
	 * of character does this - logging the same one back in carries the sitting on, which is the
	 * point of measuring the gap in wall clock rather than in logins.
	 */
	private void startSessionForCurrentCharacter()
	{
		String profile = runHistoryStore.currentProfile();

		if (profile == null || profile.equals(sessionProfile))
		{
			return;
		}

		if (sessionProfile != null)
		{
			log.debug("New character, starting the session rate over");
		}

		sessionProfile = profile;
		session = new DelveTotals();
		sessionCombat = new CombatTotals();
		sessionEndedAt = null;
		sessionStartedAt = null;
	}

	/**
	 * Pre-fills the reached rows from the deepest delve the game itself remembers, once per
	 * character. Nothing is invented: the rows land with no kill count and no time, they just stop
	 * a returning player being told they have never been past delve 10.
	 */
	private void seedFromDeepestLevel()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !milestoneStore.hasProfile()
			|| milestoneStore.isSeeded())
		{
			return;
		}

		int deepest = client.getVarpValue(VarPlayerID.DOM_DEEPEST_LEVEL);

		if (deepest <= 0)
		{
			// The varp flood has not landed yet; the change event will bring us back here.
			return;
		}

		if (milestones.seedReached(deepest))
		{
			milestoneStore.save(milestones);
			refreshTable();
			log.debug("Seeded milestones up to delve {} from the game's deepest level", deepest);
		}

		milestoneStore.setSeeded();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		long started = System.nanoTime();
		handleNpcSpawned(event);
		reportSlow("onNpcSpawned", started);
	}

	private void handleNpcSpawned(NpcSpawned event)
	{
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount++;
			ticksWithoutBoss = 0;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		long started = System.nanoTime();
		handleNpcDespawned(event);
		reportSlow("onNpcDespawned", started);
	}

	private void handleNpcDespawned(NpcDespawned event)
	{
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount = Math.max(0, bossCount - 1);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		long started = System.nanoTime();
		handleGameTick(event);
		reportSlow("onGameTick", started);
	}

	private void handleGameTick(GameTick event)
	{
		checkResume();
		trackAbandonedRun();
		refreshLive();
	}

	/**
	 * Settles a run held open across a lost connection, now that we are back in the world and can
	 * see where the game has put us. Does nothing until then - game ticks only arrive once we are.
	 */
	private void checkResume()
	{
		if (resumeCheck == null)
		{
			return;
		}

		if (run == null)
		{
			// The delve varp beat us to it and ended the run on the way back in.
			resumeCheck = null;
			return;
		}

		ResumeCheck.Verdict verdict = resumeCheck.onTick(
			client.getVarpValue(VarPlayerID.DOM_CURRENT_LEVEL_TEMP), run.lastLevel());

		if (verdict == ResumeCheck.Verdict.WAIT)
		{
			return;
		}

		resumeCheck = null;

		if (verdict == ResumeCheck.Verdict.INSIDE)
		{
			log.debug("Back inside on delve {}, the run carries on", run.currentLevel());
			return;
		}

		log.debug("Back outside the cave, ending the run that was held open");
		endRun(EndReason.FINISHED, -1);
	}

	private void trackAbandonedRun()
	{
		if (bossCount > 0)
		{
			ticksWithoutBoss = 0;
			return;
		}

		if (ticksWithoutBoss < ABANDON_TICKS)
		{
			ticksWithoutBoss++;
			return;
		}

		// A run with nothing banked and no boss in sight for a minute never really got going.
		if (run != null && run.lastLevel() == 0)
		{
			log.debug("Abandoning delve {} - no boss for {} ticks", run.currentLevel(), ABANDON_TICKS);
			endRun(EndReason.ABANDONED, -1);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		long started = System.nanoTime();
		handleGameStateChanged(event);
		reportSlow("onGameStateChanged", started);
	}

	private void handleGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGIN_SCREEN)
		{
			loginAt = null;
		}
		else if (state == GameState.LOGGED_IN && loginAt == null)
		{
			// Deliberately not refreshed on a world hop, which never clears this: the earlier of
			// the two logins is the safer bound. A run held open across a dropped connection is
			// unaffected either way - it took its anchor when it started, and nothing moves it.
			loginAt = Instant.now();

			// In case the varp flood landed before the profile was ready.
			seedFromDeepestLevel();
		}

		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// Despawns are not delivered across a scene load, so the count has to be rebuilt.
			bossCount = 0;
			ticksWithoutBoss = 0;
		}

		// Leaving the world holds the run open rather than ending it, because leaving the world is
		// not the same as leaving the cave.
		//
		// On a clean hop or logout the game clears DOM_CURRENT_LEVEL_TEMP as it puts you outside
		// the entrance, and that lands first, so those runs are already over by the time we get
		// here and this sees nothing. What reaches here is the connection that went without one,
		// and that can still go either way: get back quickly enough and you are in the delve you
		// were already in, with the run worth carrying on. ResumeCheck settles which, once we are
		// logged in again and can see where the game has put us.
		if (run != null && resumeCheck == null && (state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST))
		{
			resumeCheck = new ResumeCheck();
			log.debug("Left the world on delve {}, holding the run open", run.currentLevel());
		}

		// Ticks stop at the login screen, so the panel would otherwise keep the stale run on show.
		refreshLive();
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		long started = System.nanoTime();
		handleOverlayMenuClicked(event);
		reportSlow("onOverlayMenuClicked", started);
	}

	private void handleOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() == overlay && CLEAR_OPTION.equals(event.getEntry().getOption()))
		{
			lastRun = null;
		}
	}

	/**
	 * Logs a handler that ran long, so a stutter can be pinned on this plugin or ruled out.
	 *
	 * <p>Each public event method above is a two line wrapper around the private one that does the
	 * work, purely so this can time it. Timing the wrapper rather than the body means an early
	 * return is measured too, and no handler can be added later that quietly escapes measurement.
	 *
	 * <p>The measurement itself is two clock reads and a comparison, and the report is at debug, so
	 * this costs nothing in production - RuneLite logs at INFO, where the line is never built. It
	 * only sees the client thread: work handed to the Swing thread is timed up to the handoff, not
	 * through the repaint.
	 */
	private void reportSlow(String handler, long startedNanos)
	{
		long took = System.nanoTime() - startedNanos;

		if (took >= SLOW_HANDLER_NANOS)
		{
			log.debug("{} took {}ms", handler, String.format(Locale.US, "%.1f", took / 1_000_000d));
		}
	}

	private void startRun(Instant startedAt, int level, boolean partial)
	{
		run = new DelveRun(startedAt, level, partial, partial ? sessionAnchor() : null);
		lastRun = null;
		resumeCheck = null;
		runProfile = runHistoryStore.currentProfile();
		// Give the boss the full grace period to appear, whatever the counter was doing before.
		ticksWithoutBoss = 0;
		// A spec fired on the way in belongs to nothing we are counting, and its window must not
		// be left open to swallow the first heal of the trip.
		combatTracker.reset();
		prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
		hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
		openSession(startedAt);
		log.debug("Doom run started on delve {} (partial={})", level, partial);
	}

	/**
	 * Puts the run about to start into a session: the one already going if the last run was recent
	 * enough, or a fresh one if the player has been away longer than {@link #SESSION_IDLE}.
	 *
	 * @param startedAt when the run about to start began, which is where a fresh session's length
	 *                  is measured from. A run we joined part way through dates the sitting from
	 *                  where we picked it up rather than from a start nobody saw.
	 */
	private void openSession(Instant startedAt)
	{
		startSessionForCurrentCharacter();

		if (sessionEndedAt != null && !sessionAlive(Instant.now()))
		{
			log.debug("Session lapsed, starting the session rate over");
			session = new DelveTotals();
			sessionCombat = new CombatTotals();
			sessionStartedAt = null;
		}

		if (sessionStartedAt == null)
		{
			sessionStartedAt = startedAt;
		}

		// Cleared either way: a run is in progress, so there is no idle gap to be measuring.
		sessionEndedAt = null;
	}

	/** Whether the last run ended recently enough that the session it belonged to is still going. */
	private boolean sessionAlive(Instant now)
	{
		return sessionEndedAt != null
			&& Duration.between(sessionEndedAt, now).compareTo(SESSION_IDLE) < 0;
	}

	/**
	 * Adds a finished run to the session and lifetime rates.
	 *
	 * <p>Every run counts, including the ones the history file leaves out. An {@link
	 * EndReason#ABANDONED} run is one we lost sight of rather than one that did not happen: the
	 * delves it banked and the time it took to bank them are both real up to the last clear we saw,
	 * and that pair is exactly what a rate is made of. A partial run is charged the deliberately
	 * pessimistic span its personal bests are measured over - see {@link DelveRun#pbElapsed()} - so
	 * joining a trip half way through can only ever drag the rate down, never flatter it.
	 *
	 * <p>A run that never banked a clear is left out entirely. There is no time to charge for it:
	 * the span every figure here is built on ends at the last clear, and it has none.
	 */
	private void bankRun(DelveRun ended)
	{
		// Whatever came of the run, the sitting's idle clock restarts from the moment it ended.
		sessionEndedAt = ended.getEndedAt();

		// Ahead of every other test here, and deliberately so. The combat figures are counts of
		// things that provably happened, not a rate with a denominator, so none of the reasons a
		// run can fail to contribute to a rate apply: a run that cleared nothing still healed you,
		// and a trip we only saw half of still healed you for the half we saw.
		bankCombat(ended.getCombat());

		if (ended.lastLevel() == 0)
		{
			return;
		}

		int deep = ended.deepCleared();
		long ticks = DoomFormat.toTicks(ended.pbElapsed());

		if (ticks <= 0)
		{
			return;
		}

		session.add(deep, ticks);

		// The lifetime figure can only be written to the character that is logged in, and a run
		// held open across a lost connection can outlive the login that made it. Rather than file
		// somebody else's delves against this character, such a run is left in the session figure
		// alone - and the session is started over for the new character on its way past anyway.
		if (!totalsStore.hasProfile()
			|| (runProfile != null && !runProfile.equals(runHistoryStore.currentProfile())))
		{
			log.debug("Not banking the run to a lifetime total - it belongs to another character");
			return;
		}

		lifetime.add(deep, ticks);
		totalsStore.save(lifetime);

		log.debug("Banked {} deep delves in {} ticks (session {}/{}, lifetime {}/{})",
			deep, ticks, session.deep, session.ticks, lifetime.deep, lifetime.ticks);
	}

	/**
	 * Adds a finished run's combat figures to the sitting and to the character's lifetime.
	 *
	 * <p>The lifetime copy is guarded the same way the delve rate's is: a run held open across a
	 * dropped connection can outlive the login that made it, and filing one character's healing
	 * against another's lifetime would be worse than not filing it at all. Such a run stays in the
	 * sitting's figure alone, and the sitting is started over for the new character anyway.
	 */
	private void bankCombat(CombatTotals ended)
	{
		if (ended == null || ended.isEmpty())
		{
			return;
		}

		sessionCombat.addAll(ended);

		if (!totalsStore.hasProfile()
			|| (runProfile != null && !runProfile.equals(runHistoryStore.currentProfile())))
		{
			log.debug("Not banking combat totals to a lifetime - they belong to another character");
			return;
		}

		lifetimeCombat.addAll(ended);
		totalsStore.saveCombat(lifetimeCombat);
		refreshLifetimeCombat();
	}

	/**
	 * Posts elapsed time and pace when the delve number is a multiple of the configured interval.
	 * Shallow delves are skipped, so an interval of 5 reports at delve 10, 15, 20 and so on.
	 */
	private void announceInterval(int level, DelveRun.Split split)
	{
		int interval = config.chatIntervalDelves();

		if (interval <= 0 || level < DelveRun.DEEP_DELVE_LEVEL || level % interval != 0)
		{
			return;
		}

		sendChat(String.format("Delve %d in %s | %s elapsed | %s",
			level,
			DoomFormat.preciseDuration(split.fight == null ? split.segment : split.fight),
			DoomFormat.duration(run.clearedElapsed()),
			DoomFormat.pace(pace(run))));
	}

	private void endRun(EndReason reason, int diedOnLevel)
	{
		DelveRun ended = run;
		run = null;
		resumeCheck = null;
		combatTracker.reset();

		if (ended == null)
		{
			return;
		}

		ended.end(reason, Instant.now(), diedOnLevel);
		log.debug("Doom run ended: {} after {} delves", reason, ended.lastLevel());

		// Ahead of the abandoned check: a run we lost sight of still banked the delves we watched
		// it bank, even though its ending is too uncertain to write down as history.
		bankRun(ended);

		if (reason == EndReason.ABANDONED)
		{
			return;
		}

		lastRun = ended;

		recordRun(ended, diedOnLevel);

		if (!config.announceRunEnd() || ended.lastLevel() == 0)
		{
			return;
		}

		String elapsed = DoomFormat.duration(ended.clearedElapsed());
		String pace = DoomFormat.pace(pace(ended));

		if (reason == EndReason.DIED)
		{
			sendChat(String.format("Died on delve %d | cleared %d in %s | %s",
				diedOnLevel, ended.lastLevel(), elapsed, pace));
		}
		else
		{
			sendChat(String.format("Cleared delve %d | %s | %s", ended.lastLevel(), elapsed, pace));
		}
	}

	/**
	 * The latest moment a run we joined part way through can be proven not to have started before.
	 *
	 * <p>The login is the tight answer, but the case this exists for - the plugin switched off for
	 * part of a trip and back on - is exactly the case where we were not watching to see one. The
	 * client cannot have started after the login did, so its own uptime is a looser bound that is
	 * still always on the safe side.
	 */
	private Instant sessionAnchor()
	{
		return loginAt != null
			? loginAt
			: Instant.now().minusMillis((long) client.getGameCycle() * 20L);
	}

	private void refreshLive()
	{
		DoomMetricsPanel target = panel;

		if (target == null)
		{
			return;
		}

		DelveRun display = getDisplayRun();
		DoomMetricsPanel.Live live = display == null
			? null
			: DoomMetricsPanel.Live.of(display, config.paceMode());
		DoomMetricsPanel.Stats stats = statsSnapshot();
		boolean showCombat = run != null || sessionAlive(Instant.now());
		String key = (live == null ? "" : String.join("|", live.delveLabel, live.delveValue,
			live.timeLabel, live.timeValue, live.paceLabel, live.paceValue))
			+ "|" + stats.key() + "|" + combatKey(showCombat);

		// The timers only move once a second, so most ticks have nothing to redraw. The rates move
		// less again - neither can change until a delve is cleared or a run ends - and the combat
		// figures only move when something heals or hits.
		if (key.equals(lastLiveKey))
		{
			return;
		}

		lastLiveKey = key;

		// Built only once something has actually moved, so an idle tick allocates nothing to hand
		// across to a panel that would draw the same eight numbers again.
		CombatTotals combat = showCombat ? combatSnapshot() : null;

		SwingUtilities.invokeLater(() ->
		{
			target.setLive(live);
			target.setStats(stats);
			target.setCombat(combat);
		});
	}

	/**
	 * The sitting's combat figures, the run in progress counted as it goes rather than only once it
	 * ends - so what the panel shows during a run is what banking it will leave behind.
	 */
	private CombatTotals combatSnapshot()
	{
		return run == null ? sessionCombat.copy() : sessionCombat.plus(run.getCombat());
	}

	/**
	 * Enough of the sitting's tally to tell one repaint from the next, read straight out of the two
	 * tallies rather than out of a snapshot of them - the point is to decide whether a snapshot is
	 * worth taking.
	 *
	 * <p>Empty between sittings, which is also what the panel is shown: there is nothing being
	 * earned, and leaving this morning's numbers up would say otherwise.
	 */
	private String combatKey(boolean showCombat)
	{
		if (!showCombat)
		{
			return "";
		}

		StringBuilder key = new StringBuilder();

		for (CombatMetric metric : CombatMetric.values())
		{
			long amount = sessionCombat.get(metric)
				+ (run == null ? 0 : run.getCombat().get(metric));
			key.append(amount).append(',');
		}

		return key.toString();
	}

	/**
	 * The sitting's figures and the character's, as strings for the panel to draw.
	 *
	 * <p>The session figures count the run in progress as it goes, rather than waiting for it to
	 * end. They are built from the same two numbers the run would be banked with, so what the
	 * panel shows during a run is what banking it will leave behind, and a sitting holding one run
	 * reads exactly what that run's Run pace does.
	 *
	 * <p>The lifetime figures cannot move while a run is in progress - a run joins them only once
	 * it is banked - so what is shown there is always the character as they stood when this sitting
	 * began, and the session block beside it is what is being added to them.
	 */
	private DoomMetricsPanel.Stats statsSnapshot()
	{
		Instant now = Instant.now();
		DelveTotals live;

		if (run != null)
		{
			live = session.plus(run.deepCleared(), DoomFormat.toTicks(run.pbElapsed()));
		}
		else
		{
			// Between sittings there is no session to report on, so the rows go blank rather than
			// leaving this morning's figures up as though they were still being earned.
			live = sessionAlive(now) ? session : null;
		}

		return new DoomMetricsPanel.Stats(
			live == null ? null : sessionLength(now),
			live == null ? null : DoomFormat.pace(live.kph()),
			live == null ? null : tooltip(live),
			live == null ? null : DoomFormat.count(live.deep),
			DoomFormat.pace(lifetime.kph()),
			tooltip(lifetime),
			lifetime.isEmpty() ? null : DoomFormat.count(lifetime.deep));
	}

	/**
	 * How long this sitting has been going, or null before it has a run in it.
	 *
	 * <p>Keeps counting between runs, because the gap between two runs is part of the sitting the
	 * same way the runs are. It stops when the sitting does: once the gap passes {@link
	 * #SESSION_IDLE} the whole session block goes blank rather than showing a clock still running
	 * on an evening that has ended.
	 */
	private String sessionLength(Instant now)
	{
		return sessionStartedAt == null
			? null
			: DoomFormat.duration(Duration.between(sessionStartedAt, now));
	}

	/** What a rate is made of, so the figure above it can be checked rather than taken on trust. */
	private static String tooltip(DelveTotals totals)
	{
		return totals.isEmpty()
			? "Nothing banked yet"
			: String.format("%d deep %s in %s of run time",
				totals.deep, totals.deep == 1 ? "delve" : "delves",
				DoomFormat.tickDuration(totals.ticks));
	}

	private void refreshTable()
	{
		DoomMetricsPanel target = panel;

		if (target == null)
		{
			return;
		}

		List<MilestoneTablePanel.Row> rows = new ArrayList<>();
		milestones.getRows().forEach((delve, row) -> rows.add(new MilestoneTablePanel.Row(
			delve, row.kc, row.pbTicks, improvedThisSession.contains(delve))));

		SwingUtilities.invokeLater(() ->
		{
			// Held so a window opened later has a table to draw without reaching back into the
			// model, which by then the client thread may already be writing to again.
			tableRows = rows;
			target.setRows(rows);

			if (historyWindow != null)
			{
				historyWindow.setRows(rows);
			}
		});
	}

	/**
	 * Opens the history window, or brings it forward if it is already up. Runs on the Swing
	 * thread, from the side panel's button.
	 */
	private void openHistoryWindow()
	{
		if (historyWindow == null)
		{
			historyWindow = new HistoryWindow(icon, () -> historyWindow = null);
			historyWindow.setRows(tableRows);
			historyWindow.setSeries(chartSeries);
			historyWindow.setLifetimeCombat(windowCombat);
		}

		historyWindow.open(SwingUtilities.getWindowAncestor(panel));

		// Re-read every time rather than trusting what is already drawn: the profile may have
		// changed, or another client may have written runs since this one last looked.
		loadHistory();
	}

	private void closeHistoryWindow()
	{
		HistoryWindow window = historyWindow;

		if (window == null)
		{
			return;
		}

		// Cleared first so the frame's own close callback has nothing left to do.
		historyWindow = null;
		chartSeries = RunSeries.empty();
		window.dispose();
	}

	/**
	 * Reads the whole history off disk and hands the chart every figure it can plot from it.
	 *
	 * <p>Reduced to per-metric lists here, on the executor thread, rather than each time the
	 * dropdown moves - switching metric should not cost a pass over a lifetime of runs.
	 */
	private void loadHistory()
	{
		runHistoryStore.load(records ->
		{
			RunSeries series = RunSeries.of(records);

			SwingUtilities.invokeLater(() ->
			{
				chartSeries = series;

				if (historyWindow != null)
				{
					historyWindow.setSeries(series);
				}
			});
		});
	}

	/**
	 * Writes a finished run to the history file, and adds it to the chart if it is on screen.
	 *
	 * <p>Only runs that ended in a way we saw are recorded. An {@link EndReason#ABANDONED} run has
	 * an ending we are guessing at, and its depth would be whatever the player happened to have
	 * cleared when we lost sight of them rather than where they stopped. Everything else - a run
	 * that {@link EndReason#FINISHED} or {@link EndReason#DIED} - stopped at a depth we watched
	 * them reach.
	 */
	private void recordRun(DelveRun ended, int diedOnLevel)
	{
		RunRecord record = new RunRecord();
		record.at = ended.getEndedAt().getEpochSecond();
		record.delve = ended.lastLevel();
		record.ticks = ended.isPartial() ? 0 : DoomFormat.toTicks(ended.clearedElapsed());
		record.end = ended.getEndReason();
		record.diedOn = Math.max(0, diedOnLevel);
		record.partial = ended.isPartial();
		record.loot = ended.getLoot();
		// Left out entirely for the many runs that attribute nothing, rather than written as eight
		// zeroes on every line of the file.
		record.combat = ended.getCombat().isEmpty() ? null : ended.getCombat().copy();

		runHistoryStore.append(record,
			runProfile != null ? runProfile : runHistoryStore.currentProfile());

		SwingUtilities.invokeLater(() ->
		{
			if (historyWindow == null)
			{
				return;
			}

			chartSeries = chartSeries.plus(record);
			historyWindow.setSeries(chartSeries);
		});
	}

	private Double pace(DelveRun target)
	{
		return target.pace(config.paceMode());
	}

	private static boolean isDoomBoss(int npcId)
	{
		return npcId == NpcID.DOM_BOSS || npcId == NpcID.DOM_BOSS_SHIELDED
			|| npcId == NpcID.DOM_BOSS_BURROWED;
	}

	private void sendChat(String message)
	{
		String formatted = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("[Doom] ")
			.append(ChatColorType.NORMAL)
			.append(message)
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(formatted)
			.build());
	}
}
