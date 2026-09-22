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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.InfoBoxMenuClicked;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Doom of Mokhaiotl Metrics",
	description = "Times each delve, shows your deep delve rate, and keeps a lifetime record of"
		+ " every run",
	tags = {"doom", "mokhaiotl", "delve", "timer", "pace", "pvm", "metrics", "history", "stats"}
)
public class DoomMetricsPlugin extends Plugin
{
	/** The Doom varplayers are a contiguous block; all are logged under debug. */
	private static final int DOM_VARP_FIRST = VarPlayerID.DOM_LAST_DELVE_LEVEL;
	private static final int DOM_VARP_LAST = VarPlayerID.DOM_CURRENT_LEVEL_TEMP;

	/** Ticks without the boss before a run with no clears is given up on. */
	private static final int ABANDON_TICKS = 100;

	private static final int PRAYER_REGEN_PERIOD = 12;
	private static final int HITPOINTS_REGEN_PERIOD = 100;

	/** Package-private so the infobox can carry the same option the overlay does. */
	static final String CLEAR_OPTION = "Clear";

	/** Both forms of the eye, since we can't tell which one is the drop. */
	private static final Set<Integer> NOTABLE_DROPS = Collections.unmodifiableSet(new HashSet<>(
		Arrays.asList(
			ItemID.EYE_OF_AYAK,
			ItemID.EYE_OF_AYAK_UNCHARGED,
			ItemID.AVERNIC_TREADS,
			ItemID.MOKHAIOTL_CLOTH,
			ItemID.DOMPET)));

	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You have a funny feeling like you would have been followed",
		"You feel something weird sneaking into your backpack"
	};

	/** One per unique copy, on every descend try. */
	private static final Pattern LOOT_WARNING = Pattern.compile("^Your loot contains (.+?)! Are you sure");

	private static final String DESCEND_OPTION = "Descend";

	/** Display only: how long without a run before the session rows go blank. */
	private static final Duration SESSION_IDLE = Duration.ofMinutes(30);

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
	private InfoBoxManager infoBoxManager;

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
	private SpriteManager spriteManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	private LootDiagnostics lootDiagnostics;

	/** Diagnostics are only on the event bus while debug logging is on. */
	private boolean diagnosticsRegistered;

	private DoomMetricsPanel panel;
	private NavigationButton navButton;

	private DoomMetricsInfoBox infoBox;

	/** The picture the client last scaled for the infobox. */
	private BufferedImage infoBoxPicture;

	private volatile GameIcons icons;

	private volatile BufferedImage icon;

	// Swing thread only.
	private RunDetailWindow detailWindow;
	private RunDetail windowDetail = RunDetail.empty();
	private DoomMetricsPanel.Live windowLive;

	private final MilestoneTable milestones = new MilestoneTable();

	/** Milestones whose personal best has been beaten since the client started. */
	private final Set<Integer> improvedThisSession = new HashSet<>();

	private DelveTotals lifetime = new DelveTotals();
	private DelveTotals session = new DelveTotals();
	private CombatTotals lifetimeCombat = new CombatTotals();
	private CombatTotals sessionCombat = new CombatTotals();

	/** Combat counted since the lifetime figures were last written. */
	private CombatTotals unbankedCombat = new CombatTotals();

	private final CombatTracker combatTracker = new CombatTracker(this::recordCombat);
	private final PunishTracker punishTracker = new PunishTracker(this::recordCombat, this::handBack);

	/** Held from its spawn; the same NPC across its standing, shielded and burrowed forms. */
	private NPC boss;

	// Last seen values, so a change can be read as a difference.
	private int specEnergy;
	private int prayerPoints;
	private int hitpoints;

	private final Regeneration prayerRegeneration = new Regeneration();
	private final Regeneration hitpointsRegeneration = new Regeneration();

	/** When the session's last run ended; null while one is in progress or before any. */
	private Instant sessionEndedAt;

	private final SessionClock sessionClock = new SessionClock();

	/** The character the session belongs to, so an alt's runs never join it. */
	private String sessionProfile;

	private DelveRun run;

	/** The last run that ended for a reason worth showing. */
	private DelveRun lastRun;

	/** Clear took {@link #lastRun} off the game screen (the detail window still shows it). */
	private boolean lastRunCleared;

	/** Set while {@link #run} is held open across a lost connection. */
	private ResumeCheck resumeCheck;

	/** The character {@link #run} was started on. */
	private String runProfile;

	/** The first login seen since the plugin started - see {@link LoginBound}. */
	private Instant loginAt;

	/** Tells a login's LOGGED_IN from a loading screen's. */
	private boolean loggingIn;

	/** Pick up a player already in the cave on the next tick - see {@link #pickUpRun}. */
	private boolean pickUpPending;

	private String lastLiveKey;
	private String lastDetailKey;

	private int bossCount;
	private int ticksWithoutBoss;

	/** "Claim and leave" was clicked; only then does the claimed loot filling in end the run. */
	private boolean claimRequested;

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
		panel = new DoomMetricsPanel(this::openDetailWindow,
			() -> clientThread.invoke(this::resetSession));
		panel.setCombatFolding(GroupHeading.parseFolded(config.foldedCombatGroups()),
			folded -> config.foldedCombatGroups(GroupHeading.formatFolded(folded)));

		icons = new GameIcons(itemManager, spriteManager,
			() -> SwingUtilities.invokeLater(this::iconsArrived));
		icons.preload(NOTABLE_DROPS);
		panel.setIcons(icons);
		navButton = NavigationButton.builder()
			.tooltip("Doom Metrics")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		infoBox = new DoomMetricsInfoBox(icon, this, config);
		infoBoxManager.addInfoBox(infoBox);

		lootDiagnostics = new LootDiagnostics(client, config, () -> run != null,
			() -> run != null && run.isBetweenDelves());
		updateDiagnostics();

		reset();
		loadMilestones();
		loadTotals();
	}

	@Override
	protected void shutDown()
	{
		if (diagnosticsRegistered)
		{
			eventBus.unregister(lootDiagnostics);
			diagnosticsRegistered = false;
		}

		lootDiagnostics = null;

		overlayManager.remove(overlay);
		infoBoxManager.removeInfoBox(infoBox);
		infoBox = null;
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		icon = null;
		icons = null;
		infoBoxPicture = null;

		SwingUtilities.invokeLater(this::closeDetailWindow);

		flushCombat();
		reset();
	}

	/** Package-private so the preview harness can hand over its own. */
	Icons getIcons()
	{
		GameIcons held = icons;
		return held == null ? Icons.NONE : held;
	}

	/** Swing thread. */
	private void iconsArrived()
	{
		DoomMetricsPanel shown = panel;

		if (shown != null)
		{
			shown.setIcons(getIcons());
		}

		if (detailWindow != null)
		{
			detailWindow.setIcons(getIcons());
		}
	}

	/** The client only rescales an infobox picture when told to. */
	private void refreshInfoBoxPicture()
	{
		DoomMetricsInfoBox box = infoBox;

		if (box == null)
		{
			return;
		}

		BufferedImage picture = box.getImage();

		if (picture != infoBoxPicture)
		{
			infoBoxPicture = picture;
			infoBoxManager.updateInfoBoxImage(box);
		}
	}

	/** Drops everything held in memory. What is saved stays on disk. */
	private void reset()
	{
		forgetRuns();

		lifetime = new DelveTotals();
		lifetimeCombat = new CombatTotals();
		milestones.replaceAll(Collections.emptyMap());

		loginAt = null;
		loggingIn = false;
		bossCount = 0;
		boss = null;
		specEnergy = 0;
		prayerPoints = 0;
		hitpoints = 0;
		prayerRegeneration.reset();
		hitpointsRegeneration.reset();
		sessionProfile = null;
	}

	/** Drops the runs and the session, but not what the plugin is watching (login, boss, levels). */
	private void forgetRuns()
	{
		run = null;
		lastRun = null;
		lastRunCleared = false;
		resumeCheck = null;
		runProfile = null;
		claimRequested = false;
		pickUpPending = true;
		ticksWithoutBoss = 0;
		combatTracker.reset();
		punishTracker.reset();

		session = new DelveTotals();
		sessionCombat = new CombatTotals();
		unbankedCombat = new CombatTotals();
		sessionEndedAt = null;
		sessionClock.reset();
		improvedThisSession.clear();

		lastLiveKey = null;
		lastDetailKey = null;
	}

	/**
	 * The panel's reset button. The run in progress is thrown away, not ended; what it already
	 * banked to lifetime stays.
	 */
	void resetSession()
	{
		log.debug("Session reset from the panel");
		flushCombat();
		forgetRuns();

		refreshTable();
		refreshLive();
	}

	/** The live run, or the last one while it lingers and has not been cleared. */
	DelveRun getDisplayRun()
	{
		if (run != null)
		{
			return run;
		}

		if (lastRun == null || lastRunCleared || lastRun.getEndedAt() == null)
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

	private void updateDiagnostics()
	{
		boolean wanted = config.debugLogging();

		if (lootDiagnostics == null || wanted == diagnosticsRegistered)
		{
			return;
		}

		if (wanted)
		{
			eventBus.register(lootDiagnostics);
		}
		else
		{
			eventBus.unregister(lootDiagnostics);
		}

		diagnosticsRegistered = wanted;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!DoomMetricsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("debugLogging".equals(event.getKey()))
		{
			updateDiagnostics();
			return;
		}

		if (!"hideEmptyCounters".equals(event.getKey()))
		{
			return;
		}

		// The overlay reads the setting every frame; the window only when told.
		boolean hideEmpty = config.hideEmptyCounters();
		SwingUtilities.invokeLater(() ->
		{
			if (detailWindow != null)
			{
				detailWindow.setHideEmpty(hideEmpty);
			}
		});
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// Before the type check: the drain line is spam, not a game message.
		if (run != null && event.getMessage().contains(BLOOD_DRAIN))
		{
			combatTracker.spellHit(CombatMetric.BLOOD_BARRAGE_HEAL, client.getTickCount());
			log.debug("Blood spell drained at tick {}", client.getTickCount());
			return;
		}

		if (event.getType() == ChatMessageType.MESBOX)
		{
			lootWarning(event.getMessage());
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		// The pet line comes coloured.
		if (isPetMessage(Text.removeTags(event.getMessage())))
		{
			petClaimed();
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

	static boolean isPetMessage(String message)
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

	/** The pet line only comes with the claim, ahead of the claimed loot. */
	private void petClaimed()
	{
		if (run == null)
		{
			return;
		}

		int pets = Math.max(1, run.held(ItemID.DOMPET));
		int delve = run.sawInPile(ItemID.DOMPET, itemName(ItemID.DOMPET), pets);
		recordLoot(ItemID.DOMPET, pets);
		log.debug("Pet claimed, from delve {}",
			delve == DelveRun.NOT_RECORDED ? run.dropLevel() : delve);
	}

	/** A "Your loot contains" warning. The dialog's item is read once it has been filled in. */
	private void lootWarning(String message)
	{
		if (run == null)
		{
			return;
		}

		Matcher matcher = LOOT_WARNING.matcher(Text.removeTags(message));

		if (!matcher.find())
		{
			return;
		}

		String named = matcher.group(1);

		clientThread.invokeLater(() ->
		{
			if (run == null)
			{
				return;
			}

			Widget shown = client.getWidget(InterfaceID.Objectbox.ITEM);
			int itemId = shown != null && NOTABLE_DROPS.contains(shown.getItemId())
				? shown.getItemId()
				: notableNamed(named);

			if (itemId < 0)
			{
				log.debug("Loot warning for \"{}\", which is not a drop we track", named);
				return;
			}

			int delve = run.warnedOf(itemId, itemName(itemId));
			log.debug("Loot warning for item {} while on delve {}, recorded on delve {}",
				itemId, run.dropLevel(), delve == DelveRun.NOT_RECORDED ? "none" : delve);
		});
	}

	/** The notable drop the game calls {@code name}, or -1 for none. */
	private int notableNamed(String name)
	{
		for (int itemId : NOTABLE_DROPS)
		{
			if (name.equalsIgnoreCase(itemName(itemId)))
			{
				return itemId;
			}
		}

		return -1;
	}

	/** Records what the claim took. Only read at the claim: an unclaimed pile is lost on death. */
	private void claimLootPile()
	{
		if (run == null)
		{
			return;
		}

		// Both copies of the pile, taking the larger count of each drop.
		Map<Integer, Integer> claimed = notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE));
		notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE_DURING))
			.forEach((itemId, quantity) -> claimed.merge(itemId, quantity, Math::max));

		claimed.forEach((itemId, quantity) ->
		{
			recordLoot(itemId, quantity);
			log.debug("Loot pile holds {} x item {} on delve {}",
				quantity, itemId, run.currentLevel());
		});
	}

	/** Notable drop counts in a pile, totalled across slots. Empty for a missing pile. */
	private static Map<Integer, Integer> notableDrops(ItemContainer pile)
	{
		Map<Integer, Integer> drops = new LinkedHashMap<>();

		if (pile == null)
		{
			return drops;
		}

		for (Item item : pile.getItems())
		{
			if (NOTABLE_DROPS.contains(item.getId()))
			{
				drops.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}

		return drops;
	}

	private void recordLoot(int itemId, int quantity)
	{
		run.recordLoot(itemId, itemName(itemId), quantity);
	}

	/** Places drops on delves as the pile grows. Claiming is decided separately. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();

		if (run == null
			|| (containerId != InventoryID.DOM_LOOTPILE && containerId != InventoryID.DOM_LOOTPILE_DURING))
		{
			return;
		}

		Map<Integer, Integer> drops = notableDrops(event.getItemContainer());
		log.debug("Loot pile {} sent on delve {} (between delves: {}), notable drops {}",
			containerId, run.currentLevel(), run.dropLevel() != run.currentLevel(), drops);

		drops.forEach((itemId, quantity) ->
		{
			int delve = run.sawInPile(itemId, itemName(itemId), quantity);

			if (delve != DelveRun.NOT_RECORDED)
			{
				log.debug("Item {} recorded on delve {}, pile now holds {}", itemId, delve, quantity);
			}
		});

		// The claimed loot filling in after "Claim and leave" is the claim going through.
		if (containerId == InventoryID.DOM_LOOTPILE && claimRequested
			&& !isEmpty(event.getItemContainer()))
		{
			finishClaim();
		}
	}

	private static boolean isEmpty(ItemContainer container)
	{
		if (container == null)
		{
			return true;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				return false;
			}
		}

		return true;
	}

	private void finishClaim()
	{
		if (run == null)
		{
			return;
		}

		claimLootPile();
		endRun(EndReason.FINISHED, -1);
	}

	/** Delve 1 always starts a fresh run; a deeper delve with no run starts a joined one. */
	private void delveStarted(int level)
	{
		if (level <= 1 || run == null)
		{
			Instant now = Instant.now();
			startRun(now, level, level > 1);

			if (level > 1)
			{
				run.watchedFromDelveStart(now);
			}
		}
		else
		{
			run.enterLevel(level, Instant.now());
			claimRequested = false;
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

		bankClear(split);

		announceClear(level);
		recordMilestone(level);
	}

	private void recordMilestone(int level)
	{
		if (!MilestoneTable.isMilestone(level))
		{
			return;
		}

		Duration elapsed = run.pbElapsed();
		int ticks = elapsed == null ? 0 : DoomFormat.toTicks(elapsed);

		if (milestones.record(level, ticks))
		{
			improvedThisSession.add(level);
			log.debug("Delve {} personal best is now {} ticks", level, ticks);
		}

		milestoneStore.save(milestones);
		refreshTable();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (run == null)
		{
			return;
		}

		int widgetId = event.getParam1();

		// A new descend try - but the Descend on a warning dialog continues the same try.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_DESCEND
			|| (DESCEND_OPTION.equals(Text.removeTags(event.getMenuOption()))
				&& WidgetUtil.componentToInterface(widgetId) != InterfaceID.OBJECTBOX))
		{
			run.descending();
			claimRequested = false;
			return;
		}

		// Not the claim yet: it asks for a Confirm first.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_CLAIM)
		{
			claimRequested = true;
			return;
		}

		// These come after the claim, so a run still open has missed it.
		boolean claimed = widgetId == InterfaceID.DomEndLevelUi.BTN_INV_ALL
			|| widgetId == InterfaceID.DomEndLevelUi.BTN_BANK_ALL;

		if (claimed)
		{
			finishClaim();
		}
		else if (widgetId == InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			endRun(EndReason.FINISHED, -1);
		}
	}

	/** The claim script fires mid-tick, so the pile is read once the tick's events are through. */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.DOM_LOOT_CLAIM && run != null)
		{
			clientThread.invokeLater(this::finishClaim);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (run != null && event.getActor() == client.getLocalPlayer())
		{
			endRun(EndReason.DIED, run.currentLevel());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (run == null)
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

		if (onBoss && punishTracker.mayBePunish(tick) && isPunishSplat(hitsplat))
		{
			// Held to the end of the tick; comes back through handBack if it was no punish.
			punishTracker.hit(hitsplat.getAmount(), hitsplat.isMine(), tick);
			return;
		}

		if (hitsplat.isMine())
		{
			// A zero rather than skipped, so the spec's budget is spent on this hit.
			int amount = countsAsDamage(target) ? hitsplat.getAmount() : 0;
			logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
			combatTracker.damaged(amount, tick);
		}
	}

	/** Punish bonus splats aren't {@code isMine()}; the fight is solo, so others' colours are ours. */
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

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();

		if (run == null || actor == null)
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
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.PRAYER)
		{
			int was = prayerPoints;
			prayerPoints = event.getBoostedLevel();

			// No floor on the start: an Eldritch spec on an empty prayer book is the main case.
			if (run != null && prayerPoints > was)
			{
				rose(SpecEffect.Kind.PRAYER, prayerRegeneration, prayerRegenerationPeriod(),
					was, prayerPoints, event.getLevel());
			}
		}
		else if (event.getSkill() == Skill.HITPOINTS)
		{
			int was = hitpoints;
			hitpoints = event.getBoostedLevel();

			if (run != null && hitpoints > was)
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

		if (isWearingRegenBracelet())
		{
			rate *= 2;
		}

		return HITPOINTS_REGEN_PERIOD / rate;
	}

	/** Max capes come in too many recolours for an id list, so they're matched by name. */
	private boolean isWearingRegenCape()
	{
		int itemId = equipped(EquipmentInventorySlot.CAPE);

		if (itemId == ItemID.SKILLCAPE_HITPOINTS || itemId == ItemID.SKILLCAPE_HITPOINTS_TRIMMED)
		{
			return true;
		}

		String name = itemName(itemId);
		return name != null && name.toLowerCase().contains("max cape");
	}

	private boolean isWearingRegenBracelet()
	{
		return equipped(EquipmentInventorySlot.GLOVES) == ItemID.JEWL_BRACELET_REGEN;
	}

	/** Healing spell impacts on a target. Blood spells are read from chat - see {@link #BLOOD_DRAIN}. */
	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();

		if (run == null || actor == null)
		{
			return;
		}

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

	/** The trackers' only way out: credits the run, the session and the lifetime buffer. */
	private void recordCombat(CombatMetric metric, long amount)
	{
		if (run == null)
		{
			return;
		}

		run.recordCombat(metric, amount, Instant.now());
		sessionCombat.add(metric, amount);
		unbankedCombat.add(metric, amount);

		if (config.debugLogging())
		{
			log.debug("Counted {} to {} on delve {}", amount, metric.key(), run.dropLevel());
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
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

		// The game's own delve clock start, so our first segment matches its reported time.
		if (varpId == VarPlayerID.DOM_LEVEL_START_TIME && run != null
			&& run.reanchorStart(Instant.now()))
		{
			log.debug("Run start moved onto the delve {} start the game reported", run.currentLevel());
		}

		// Backstop for an exit that skipped the end level panel. Never fires on delve 1.
		if (varpId == VarPlayerID.DOM_CURRENT_LEVEL_TEMP && event.getValue() == 0
			&& run != null && run.lastLevel() > 0)
		{
			endRun(EndReason.FINISHED, -1);
		}

		// Arrives in the login varp flood, either side of the profile being ready.
		if (varpId == VarPlayerID.DOM_DEEPEST_LEVEL)
		{
			seedFromDeepestLevel();
		}
	}

	/** A drop in special attack energy is a spec. Tracked outside runs too. */
	private void specialAttackEnergyChanged(int energy)
	{
		int was = specEnergy;
		specEnergy = energy;

		if (run == null || energy >= was)
		{
			return;
		}

		// Equipment isn't updated yet for this tick, so the weapon is read later.
		int tick = client.getTickCount();

		clientThread.invokeLater(() ->
		{
			if (run == null)
			{
				return;
			}

			int itemId = equipped(EquipmentInventorySlot.WEAPON);
			SpecWeapon weapon = SpecWeapon.forItem(itemId, itemName(itemId));

			if (weapon == null)
			{
				log.debug("Special attack energy fell with nothing equipped, ignoring it");
				return;
			}

			combatTracker.specFired(weapon, tick);
			log.debug("Special attack fired on delve {}: {} (item {} \"{}\") at tick {}",
				run.currentLevel(), weapon, itemId, itemName(itemId), tick);
		});
	}

	/** The item id worn in {@code slot}, or 0 when the slot is empty or unreadable. */
	private int equipped(EquipmentInventorySlot slot)
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);

		if (worn == null)
		{
			return 0;
		}

		Item item = worn.getItem(slot.getSlotIdx());
		return item == null ? 0 : item.getId();
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
		closeRunFromAnotherCharacter();
		loadMilestones();
		loadTotals();
	}

	/** A run held open across a lost connection ends where it was if we come back as an alt. */
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
		// Logged out: keep the last table on show.
		if (!milestoneStore.hasProfile())
		{
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
		// Logged out: keep the last figures on show.
		if (!totalsStore.hasProfile())
		{
			return;
		}

		DelveTotals loaded = totalsStore.load();
		lifetime = loaded == null ? new DelveTotals() : loaded;

		CombatTotals loadedCombat = totalsStore.loadCombat();
		lifetimeCombat = loadedCombat == null ? new CombatTotals() : loadedCombat;

		startSessionForCurrentCharacter();
		refreshLive();
	}

	/** Starts the session over when the client comes back as a different character. */
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
		sessionClock.reset();
	}

	/** Pre-fills the reached milestone rows from the game's deepest delve, once per character. */
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
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount++;
			ticksWithoutBoss = 0;
			boss = event.getNpc();
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount = Math.max(0, bossCount - 1);
		}

		if (event.getNpc() == boss)
		{
			boss = null;
		}
	}

	/** The glowing hole: the pile holds a unique. Scene rebuilds re-send it, which is harmless. */
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (event.getGameObject().getId() != ObjectID.DOM_DESCEND_HOLE_UNIQUE || run == null
			|| !run.isBetweenDelves())
		{
			return;
		}

		if (run.uniqueSignalled())
		{
			log.debug("Delve {} was left by the glowing hole: a unique is in the pile",
				run.dropLevel());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkResume();
		pickUpRun();
		trackAbandonedRun();
		trackPunish();
		refreshLive();
		refreshInfoBoxPicture();
	}

	/** Boss prayer and weapon in hand are both settled at the end of the tick. */
	private void trackPunish()
	{
		if (run == null)
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
		int itemId = equipped(EquipmentInventorySlot.WEAPON);

		if (itemId <= 0)
		{
			return null;
		}

		String name = itemName(itemId);
		PunishWeapon weapon = PunishWeapon.forItem(itemId, name);

		if (weapon == PunishWeapon.OTHER && !isMeleeWeapon(itemId))
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

	private boolean isMeleeWeapon(int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		ItemEquipmentStats bonuses = stats == null ? null : stats.getEquipment();

		return bonuses != null && PunishWeapon.isMelee(bonuses.getAstab(), bonuses.getAslash(),
			bonuses.getAcrush(), bonuses.getArange(), bonuses.getAmagic());
	}

	/** Settles a run held open across a lost connection, once ticks arrive again. */
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

	/**
	 * Picks up a player already in the cave after start up or a reset. The varp reads one less
	 * than the delve and zero on delve 1, where the boss being there is the signal.
	 */
	private void pickUpRun()
	{
		if (!pickUpPending || run != null || resumeCheck != null)
		{
			return;
		}

		int descended = client.getVarpValue(VarPlayerID.DOM_CURRENT_LEVEL_TEMP);

		if (descended <= 0 && bossCount == 0)
		{
			return;
		}

		startRun(Instant.now(), descended + 1, true);
		refreshLive();
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

		if (run != null && run.lastLevel() == 0)
		{
			log.debug("Abandoning delve {} - no boss for {} ticks", run.currentLevel(), ABANDON_TICKS);
			endRun(EndReason.ABANDONED, -1);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGIN_SCREEN)
		{
			sessionClock.pause(Instant.now());
		}
		else if (state == GameState.CONNECTION_LOST)
		{
			sessionClock.connectionLost(Instant.now());
		}
		else if (state == GameState.LOGGED_IN)
		{
			sessionClock.resume(Instant.now());
		}

		if (state == GameState.LOGGING_IN)
		{
			loggingIn = true;
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			loggingIn = false;
		}

		boolean loggedIn = state == GameState.LOGGED_IN && loggingIn;

		if (state == GameState.LOGGED_IN)
		{
			loggingIn = false;
		}

		if (loggedIn && loginAt == null)
		{
			loginAt = Instant.now();

			// In case the varp flood landed before the profile was ready.
			seedFromDeepestLevel();
		}

		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// Despawns are not delivered across a scene load.
			bossCount = 0;
			ticksWithoutBoss = 0;
			boss = null;
		}

		// A clean hop or logout has already ended the run via the varp; what reaches here is a
		// lost connection, which is held open until ResumeCheck can see where we are.
		if (run != null && resumeCheck == null && (state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST))
		{
			resumeCheck = new ResumeCheck();
			log.debug("Left the world on delve {}, holding the run open", run.currentLevel());
		}

		// Ticks stop at the login screen, so refresh now.
		refreshLive();
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() == overlay && CLEAR_OPTION.equals(event.getEntry().getOption()))
		{
			lastRunCleared = true;
		}
	}

	/** The same Clear the overlay carries, for the display style where the overlay is not drawn. */
	@Subscribe
	public void onInfoBoxMenuClicked(InfoBoxMenuClicked event)
	{
		if (event.getInfoBox() == infoBox && CLEAR_OPTION.equals(event.getEntry().getOption()))
		{
			lastRunCleared = true;
		}
	}

	private void startRun(Instant startedAt, int level, boolean partial)
	{
		run = new DelveRun(startedAt, level, partial, partial ? sessionAnchor() : null);
		lastRun = null;
		lastRunCleared = false;
		resumeCheck = null;
		runProfile = runHistoryStore.currentProfile();
		pickUpPending = false;
		ticksWithoutBoss = 0;
		// A spec fired on the way in must not swallow the first heal of the trip.
		combatTracker.reset();
		punishTracker.reset();
		claimRequested = false;
		prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
		hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);

		if (partial)
		{
			// Already in the pile, from delves we did not see.
			notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE))
				.forEach(run::pileAlreadyHeld);
			notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE_DURING))
				.forEach(run::pileAlreadyHeld);
		}

		openSession(startedAt);
		log.debug("Doom run started on delve {} (partial={})", level, partial);
	}

	private void openSession(Instant startedAt)
	{
		startSessionForCurrentCharacter();
		sessionClock.start(startedAt);
		sessionEndedAt = null;
	}

	private boolean sessionAlive(Instant now)
	{
		return sessionEndedAt != null
			&& Duration.between(sessionEndedAt, now).compareTo(SESSION_IDLE) < 0;
	}

	/**
	 * Banks the clear's segment into the session and lifetime rates. A joined run's first clear is
	 * left out when its start was a guess.
	 */
	private void bankClear(DelveRun.Split split)
	{
		flushCombat();

		if (!run.lastClearTimed())
		{
			log.debug("Not banking delve {} to the rates - the run was joined part way into it",
				split.level);
			return;
		}

		int deep = split.level >= DelveRun.DEEP_DELVE_LEVEL ? 1 : 0;
		long ticks = run.lastClearTicks();

		if (ticks <= 0)
		{
			return;
		}

		session.add(deep, ticks);

		if (!lifetimeBelongsToRun())
		{
			log.debug("Not banking delve {} to a lifetime total - it belongs to another character",
				split.level);
			return;
		}

		lifetime.add(deep, ticks);
		totalsStore.save(lifetime);

		log.debug("Banked delve {}: {} deep in {} ticks (session {}/{}, lifetime {}/{})",
			split.level, deep, ticks, session.deep, session.ticks, lifetime.deep, lifetime.ticks);
	}

	/** Writes the combat counted since the last write to the lifetime totals. */
	private void flushCombat()
	{
		if (unbankedCombat.isEmpty())
		{
			return;
		}

		CombatTotals pending = unbankedCombat;
		unbankedCombat = new CombatTotals();

		if (!lifetimeBelongsToRun())
		{
			log.debug("Not banking combat totals to a lifetime - they belong to another character");
			return;
		}

		lifetimeCombat.addAll(pending);
		totalsStore.saveCombat(lifetimeCombat);
	}

	/** Whether the logged in character is the one the run was started on. */
	private boolean lifetimeBelongsToRun()
	{
		return totalsStore.hasProfile()
			&& (runProfile == null || runProfile.equals(runHistoryStore.currentProfile()));
	}

	private void announceClear(int level)
	{
		int target = targetDelve();

		if (ChatAnnouncement.isDue(level, config.chatIntervalDelves(), target))
		{
			sendChat(ChatAnnouncement.delveCleared(run, level, target, config.paceMode()));
		}
	}

	private void endRun(EndReason reason, int diedOnLevel)
	{
		DelveRun ended = run;
		run = null;
		resumeCheck = null;
		claimRequested = false;
		combatTracker.reset();
		punishTracker.reset();

		if (ended == null)
		{
			return;
		}

		ended.end(reason, Instant.now(), diedOnLevel);
		log.debug("Doom run ended: {} after {} delves", reason, ended.lastLevel());

		// Delves are already banked; flush the combat since the last one, abandoned runs too.
		flushCombat();
		sessionEndedAt = ended.getEndedAt();

		if (reason == EndReason.ABANDONED)
		{
			return;
		}

		lastRun = ended;
		lastRunCleared = false;

		recordRun(ended, diedOnLevel);

		if (!config.announceRunEnd() || ended.lastLevel() == 0)
		{
			return;
		}

		sendChat(ChatAnnouncement.runEnded(ended, reason, config.paceMode()));
	}

	/** The earliest a joined run can have started, or null - see {@link LoginBound}. */
	private Instant sessionAnchor()
	{
		int ticks = client.getGameState() == GameState.LOGGED_IN ? client.getTickCount() : -1;
		Instant anchor = LoginBound.of(Instant.now(), ticks, loginAt);
		log.debug("Joined run bounded by login at {} (seen login {}, {} ticks)", anchor, loginAt, ticks);
		return anchor;
	}

	private void refreshLive()
	{
		DoomMetricsPanel target = panel;

		if (target == null)
		{
			return;
		}

		refreshDetail();

		DelveRun display = getDisplayRun();
		DelveRun detail = detailRun();
		DoomMetricsPanel.Live live = display == null
			? null
			: DoomMetricsPanel.Live.of(display, config.paceMode(), targetDelve(),
				config.targetPrediction());

		// The window keeps drawing a run the overlay's linger has taken down.
		DoomMetricsPanel.Live detailLive = detail == display
			? live
			: (detail == null ? null : DoomMetricsPanel.Live.of(detail, config.paceMode(),
				targetDelve(), config.targetPrediction()));

		DoomMetricsPanel.Stats stats = statsSnapshot();
		boolean showCombat = run != null || sessionAlive(Instant.now());
		String key = (live == null ? "" : live.key())
			+ "|" + stats.key() + "|" + combatKey(showCombat)
			+ (detailLive == live ? "" : "|" + (detailLive == null ? "" : detailLive.key()));

		if (key.equals(lastLiveKey))
		{
			return;
		}

		lastLiveKey = key;

		CombatTotals combat = showCombat ? combatSnapshot() : null;
		CombatTotals lifetimeShown = lifetimeCombat.copy();

		SwingUtilities.invokeLater(() ->
		{
			target.setLive(live);
			target.setStats(stats);
			target.setCombat(combat, lifetimeShown);

			windowLive = detailLive;

			if (detailWindow != null)
			{
				detailWindow.setLive(detailLive);
			}
		});
	}

	private CombatTotals combatSnapshot()
	{
		return sessionCombat.copy();
	}

	/** Enough of both combat tallies to tell one repaint from the next. */
	private String combatKey(boolean showCombat)
	{
		StringBuilder key = new StringBuilder();

		for (CombatMetric metric : CombatMetric.values())
		{
			key.append(showCombat ? sessionCombat.get(metric) : 0).append(',')
				.append(lifetimeCombat.get(metric)).append(',');
		}

		return key.toString();
	}

	private DoomMetricsPanel.Stats statsSnapshot()
	{
		Instant now = Instant.now();

		// Idle long enough and the session rows go blank; the session itself is kept.
		DelveTotals live = run != null || sessionAlive(now) ? session : null;

		return new DoomMetricsPanel.Stats(
			live == null ? null : sessionLength(now),
			live == null ? null : DoomFormat.pace(live.kph()),
			live == null ? null : tooltip(live),
			live == null ? null : DoomFormat.count(live.deep),
			DoomFormat.pace(lifetime.kph()),
			tooltip(lifetime),
			lifetime.isEmpty() ? null : DoomFormat.count(lifetime.deep));
	}

	private String sessionLength(Instant now)
	{
		Duration elapsed = sessionClock.elapsed(now);
		return elapsed == null ? null : DoomFormat.duration(elapsed);
	}

	private static String tooltip(DelveTotals totals)
	{
		return totals.isEmpty()
			? "No delves completed yet"
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

		SwingUtilities.invokeLater(() -> target.setRows(rows));
	}

	/** Swing thread. */
	private void openDetailWindow()
	{
		if (detailWindow == null)
		{
			detailWindow = new RunDetailWindow(icon, () -> detailWindow = null);
			detailWindow.setIcons(getIcons());
			detailWindow.setHideEmpty(config.hideEmptyCounters());
			detailWindow.setFolding(GroupHeading.parseFolded(config.foldedDetailGroups()),
				folded -> config.foldedDetailGroups(GroupHeading.formatFolded(folded)));
			detailWindow.setDetail(windowDetail);
			detailWindow.setLive(windowLive);
		}

		detailWindow.open(SwingUtilities.getWindowAncestor(panel));
	}

	/** Shutdown only; a window the reader closes keeps its data for reopening. */
	private void closeDetailWindow()
	{
		RunDetailWindow window = detailWindow;

		detailWindow = null;
		windowDetail = RunDetail.empty();
		windowLive = null;

		if (window != null)
		{
			window.dispose();
		}
	}

	/** The run the detail window shows: ignores linger and Clear. */
	private DelveRun detailRun()
	{
		return run != null ? run : lastRun;
	}

	private void refreshDetail()
	{
		DelveRun target = detailRun();
		String key = RunDetail.keyFor(target);

		if (key.equals(lastDetailKey))
		{
			return;
		}

		lastDetailKey = key;

		// Built on the client thread, which owns the run, and immutable once built.
		RunDetail detail = RunDetail.of(target);

		SwingUtilities.invokeLater(() ->
		{
			windowDetail = detail;

			if (detailWindow != null)
			{
				detailWindow.setDetail(detail);
			}
		});
	}

	/** Written for finished and died runs; never read back by the plugin. */
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
		// Left out entirely for runs that attribute nothing.
		record.combat = ended.getCombat().isEmpty() ? null : ended.getCombat().copy();

		runHistoryStore.append(record,
			runProfile != null ? runProfile : runHistoryStore.currentProfile());
	}

	/** The target delve, or 0 when the target is switched off. */
	private int targetDelve()
	{
		return config.showTargetDelve() ? config.targetDelve() : 0;
	}

	static boolean isDoomBoss(int npcId)
	{
		return npcId == NpcID.DOM_BOSS || npcId == NpcID.DOM_BOSS_SHIELDED
			|| npcId == NpcID.DOM_BOSS_BURROWED;
	}

	private void sendChat(ChatAnnouncement announcement)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatAnnouncement.TYPE)
			.runeLiteFormattedMessage(announcement.formatted())
			.build());
	}
}
