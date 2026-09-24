package com.rescanta.doommetrics;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
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
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.InfoBoxMenuClicked;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Tracks the run: when it starts and ends, and which delves it clears. Every game event arrives
 * here and is routed to {@link LootWatcher}, {@link CombatWatcher}, {@link Totals},
 * {@link MilestoneTracker} and {@link PanelFeed}.
 */
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

	/** Ticks a run is held open after a death, for the clear of a boss that died with the player. */
	private static final int DEATH_SETTLE_TICKS = 10;

	/** Package-private so the infobox can carry the same option the overlay does. */
	static final String CLEAR_OPTION = "Clear";

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
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	// Built on start up.
	private LootWatcher loot;
	private CombatWatcher combat;
	private Totals totals;
	private MilestoneTracker milestones;
	private PanelFeed feed;

	private NavigationButton navButton;

	private DoomMetricsInfoBox infoBox;

	/** The picture the client last scaled for the infobox. */
	private BufferedImage infoBoxPicture;

	private volatile GameIcons icons;

	private DelveRun run;

	/** The last run that ended for a reason worth showing. */
	private DelveRun lastRun;

	/** Clear took {@link #lastRun} off the game screen (the detail window still shows it). */
	private boolean lastRunCleared;

	/** Set while {@link #run} is held open across a lost connection. */
	private ResumeCheck resumeCheck;

	/** The character {@link #run} was started on. */
	private String runProfile;

	/**
	 * A run the plugin was turned off during, kept to carry on if it is turned back on in the same
	 * trip. Its incomplete record is held by {@link RunHistoryStore#holdPending} meanwhile.
	 */
	private DelveRun suspended;
	private String suspendedProfile;

	/** {@code TOTAL_DOM_LEVELS} when {@link #suspended} was: it goes up one per delve cleared. */
	private int suspendedClears;

	/** The first login seen since the plugin started - see {@link LoginBound}. */
	private Instant loginAt;

	/** Tells a login's LOGGED_IN from a loading screen's. */
	private boolean loggingIn;

	/** Pick up a player already in the cave on the next tick - see {@link #pickUpRun}. */
	private boolean pickUpPending;

	private int bossCount;
	private int ticksWithoutBoss;

	/** The delve the player died on while {@link #run} is held open after it, or -1. */
	private int deathLevel = -1;
	private int ticksSinceDeath;

	@Provides
	DoomMetricsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoomMetricsConfig.class);
	}

	@Override
	protected void startUp()
	{
		GameItems items = new GameItems(client, itemManager);
		loot = new LootWatcher(client, clientThread, items, () -> run,
			() -> endRun(EndReason.FINISHED, -1));
		combat = new CombatWatcher(client, clientThread, config, items, () -> run, this::recordCombat);
		totals = new Totals(totalsStore, runHistoryStore, () -> runProfile);
		milestones = new MilestoneTracker(client, milestoneStore, this::resetTarget,
			totals::lifetimeBelongsToRun,
			() -> feed.refreshTable());
		feed = new PanelFeed(this, config, totals, milestones);

		overlayManager.add(overlay);

		BufferedImage icon = ImageUtil.loadImageResource(DoomMetricsPlugin.class, "panel_icon.png");
		DoomMetricsPanel panel = feed.start(icon, () -> clientThread.invoke(this::resetSession));

		icons = new GameIcons(itemManager, spriteManager,
			() -> SwingUtilities.invokeLater(feed::iconsArrived));
		icons.preload(LootWatcher.NOTABLE_DROPS);
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

		// Plugins start on the Swing thread; the run is the client thread's. Queued ahead of the
		// spawn events the client replays for a plugin starting, so they land on a clean slate.
		clientThread.invoke(() ->
		{
			reset();
			milestones.load();
			loadTotals();

			// Held by a run from before the client last closed, which cannot be carried on now.
			if (suspended == null)
			{
				runHistoryStore.releasePending();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		infoBoxManager.removeInfoBox(infoBox);
		infoBox = null;
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		icons = null;
		infoBoxPicture = null;
		feed.stop();

		// Plugins stop on the Swing thread; the run is the client thread's.
		clientThread.invoke(() ->
		{
			suspendRun();
			totals.flushCombat();
			reset();
		});
	}

	/**
	 * Closing the client does not shut plugins down, so a run still going is written here. The
	 * client saves its config while this event is handed round, before the client thread gets to
	 * the run, so what the run changes in the profile is saved again afterwards.
	 */
	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		CompletableFuture<Void> written = new CompletableFuture<>();
		clientThread.invoke(() -> recordUnfinishedRun()
			.thenRunAsync(configManager::sendConfig, executor)
			.whenComplete((ignored, error) -> written.complete(null)));
		event.waitFor(written);
	}

	/**
	 * Writes a run the plugin stops watching mid-way to the history as incomplete, its depth a
	 * floor. One with no clear yet is dropped, as it would be at any other ending.
	 */
	private CompletableFuture<Void> recordUnfinishedRun()
	{
		DelveRun unfinished = run;

		if (unfinished == null || unfinished.lastLevel() == 0)
		{
			return CompletableFuture.completedFuture(null);
		}

		run = null;

		// Died a moment ago: the run is over, not unfinished.
		if (deathLevel >= 0)
		{
			milestones.runEnded(deathLevel, unfinished.loot().getClaimed().size());
			return runHistoryStore.append(
				recordOf(unfinished, Instant.now(), EndReason.DIED, deathLevel, false),
				profileOf(runProfile));
		}

		log.debug("Recording the run on delve {} as incomplete", unfinished.currentLevel());
		return runHistoryStore.append(incompleteRecord(unfinished), profileOf(runProfile));
	}

	/**
	 * The plugin is being turned off mid-run: keeps the run to carry on if it is turned back on in
	 * the same trip, and holds its record as incomplete in case it is not.
	 */
	private void suspendRun()
	{
		DelveRun unfinished = run;

		if (unfinished == null)
		{
			return;
		}

		// The player respawns outside, so there is nothing to carry on - a death on delve 1 too.
		if (deathLevel >= 0)
		{
			endRun(EndReason.DIED, deathLevel);
			return;
		}

		if (unfinished.lastLevel() == 0)
		{
			return;
		}

		runHistoryStore.holdPending(incompleteRecord(unfinished), profileOf(runProfile));

		// Logged out, the delve counter cannot be read, so the run could never be matched again.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			suspended = unfinished;
			suspendedProfile = profileOf(runProfile);
			suspendedClears = client.getVarpValue(VarPlayerID.TOTAL_DOM_LEVELS);
			log.debug("Suspending the run on delve {}", unfinished.currentLevel());
		}
	}

	/**
	 * Carries the suspended run on if the player on {@code level} is still in it: nothing else fits
	 * the delves cleared since. Otherwise it stays suspended.
	 */
	private boolean tryResume(int level)
	{
		if (suspended == null || suspendedProfile == null
			|| !suspendedProfile.equals(runHistoryStore.currentProfile()))
		{
			return false;
		}

		int clearsSince = client.getVarpValue(VarPlayerID.TOTAL_DOM_LEVELS) - suspendedClears;

		// A clear's time is kept from the clear until the next delve's varps move; the trip's
		// total is zeroed on the way into a new trip.
		boolean betweenDelves = client.getVarpValue(VarPlayerID.DOM_LAST_LEVEL_DURATION) > 0
			&& client.getVarpValue(VarPlayerID.DOM_TOTAL_DURATION) > 0;

		if (!suspended.continuesAt(level, clearsSince, betweenDelves))
		{
			return false;
		}

		DelveRun resumed = suspended;
		String profile = suspendedProfile;
		suspended = null;
		suspendedProfile = null;
		runHistoryStore.dropPending();

		Instant now = Instant.now();
		resumed.resumeOn(level, now, clearsSince);
		watch(resumed, profile, now);
		log.debug("Carried the run on at delve {}, {} delves cleared unwatched", level, clearsSince);
		return true;
	}

	/** The suspended run was not carried on, so its held record goes to the history. */
	private void settleSuspended()
	{
		if (suspended == null)
		{
			return;
		}

		log.debug("The run suspended on delve {} was not carried on", suspended.currentLevel());
		suspended = null;
		suspendedProfile = null;
		runHistoryStore.releasePending();
	}

	/** Package-private so the preview harness can hand over its own. */
	Icons getIcons()
	{
		GameIcons held = icons;
		return held == null ? Icons.NONE : held;
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
		totals.reset();
		milestones.reset();
		combat.reset();

		loginAt = null;
		loggingIn = false;
		bossCount = 0;
	}

	/** Drops the runs and the session, but not what the plugin is watching (login, boss, levels). */
	private void forgetRuns()
	{
		run = null;
		lastRun = null;
		lastRunCleared = false;
		resumeCheck = null;
		runProfile = null;
		pickUpPending = true;
		ticksWithoutBoss = 0;
		deathLevel = -1;
		loot.reset();
		combat.stopTracking();
		totals.forgetSession();
		milestones.forgetSession();
		feed.forget();
	}

	/**
	 * The panel's reset button. The run in progress is thrown away, not ended; what it already
	 * banked to lifetime stays.
	 */
	void resetSession()
	{
		log.debug("Session reset from the panel");
		settleSuspended();
		totals.flushCombat();
		forgetRuns();

		feed.refreshTable();
		feed.refreshLive();
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

	/** The run the detail window shows: ignores linger and Clear. */
	DelveRun getDetailRun()
	{
		return run != null ? run : lastRun;
	}

	boolean isRunInProgress()
	{
		return run != null;
	}

	/** The target delve, or 0 when the target is switched off. */
	int targetDelve()
	{
		return config.showTargetDelve() ? config.targetDelve() : 0;
	}

	/**
	 * The milestone the resets card follows: the target delve rounded down to a row, whether or not
	 * the target is shown.
	 */
	int resetTarget()
	{
		return Math.max(MilestoneTable.INTERVAL, MilestoneTable.milestoneAtOrBelow(config.targetDelve()));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!DoomMetricsConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("hideEmptyCounters".equals(event.getKey()))
		{
			feed.hideEmptyChanged();
		}
		else if ("targetDelve".equals(event.getKey()))
		{
			// The milestone table marks the target's row and the resets card follows it. Config
			// changes arrive on the thread that made them; the table is the client thread's.
			clientThread.invoke(feed::refreshTable);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (combat.chatMessage(event.getMessage()))
		{
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		// The pet line comes coloured.
		if (LootWatcher.isPetMessage(Text.removeTags(event.getMessage())))
		{
			loot.petClaimed();
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

	/** Delve 1 always starts a fresh run; a deeper delve with no run starts a joined one. */
	private void delveStarted(int level)
	{
		if (level <= 1 || run == null)
		{
			Instant now = Instant.now();

			if (level <= 1 || !tryResume(level))
			{
				startRun(now, level, level > 1);
			}

			if (level > 1)
			{
				run.watchedFromDelveStart(now);
			}
		}
		else
		{
			run.enterLevel(level, Instant.now());
			loot.delveStarted();
			log.debug("Delve {} started", level);
		}
	}

	private void delveCleared(int level, Duration fight)
	{
		if (run == null && !tryResume(level))
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

		totals.bankClear(run, split);

		announceClear(level);
		milestones.recordClear(level, run);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		loot.itemContainerChanged(event);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		loot.menuOptionClicked(event);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		loot.scriptPreFired(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (run == null || deathLevel >= 0 || event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		// A boss the same ticks' hits killed is cleared a few ticks later, so wait for it.
		deathLevel = run.currentLevel();
		ticksSinceDeath = 0;
		combat.playerDied();
		log.debug("Died on delve {}, holding the run open for a clear", deathLevel);
	}

	/** Ends a run held open after a death once the time for a clear has gone by. */
	private void settleDeath()
	{
		if (deathLevel >= 0 && ++ticksSinceDeath >= DEATH_SETTLE_TICKS)
		{
			endRun(EndReason.DIED, deathLevel);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		combat.hitsplatApplied(event);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		combat.animationChanged(event);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		combat.statChanged(event);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		combat.graphicChanged(event);
	}

	/** The trackers' only way out: credits the run, the session and the lifetime buffer. */
	private void recordCombat(CombatMetric metric, long amount)
	{
		if (run == null)
		{
			return;
		}

		run.recordCombat(metric, amount, Instant.now());
		totals.recordCombat(metric, amount);

		if (config.debugLogging())
		{
			log.debug("Counted {} to {} on delve {}", amount, metric.key(), run.creditLevel());
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varpId = event.getVarpId();

		if (varpId == VarPlayerID.SA_ENERGY)
		{
			combat.specEnergyChanged(event.getValue());
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

		// Held across a lost connection, the login flood sends every Doom varp as 0 and then its
		// value again; ResumeCheck settles the run instead.
		boolean flooding = resumeCheck != null;

		// The game's own delve clock start, so our first segment matches its reported time.
		if (varpId == VarPlayerID.DOM_LEVEL_START_TIME && run != null && !flooding
			&& run.reanchorStart(Instant.now()))
		{
			log.debug("Run start moved onto the delve {} start the game reported", run.currentLevel());
		}

		if (varpId == VarPlayerID.DOM_CURRENT_LEVEL_TEMP && run != null && !flooding)
		{
			delveVarpChanged(event.getValue());
		}

		// Arrives in the login varp flood, either side of the profile being ready.
		if (varpId == VarPlayerID.DOM_DEEPEST_LEVEL)
		{
			milestones.seedFromDeepestLevel();
		}
	}

	private void delveVarpChanged(int descended)
	{
		// Backstop for an exit that skipped the end level panel. Never fires on delve 1.
		if (descended == 0)
		{
			if (run.lastLevel() > 0)
			{
				endRun(EndReason.FINISHED, -1);
			}

			return;
		}

		// Picked up in the seconds between a delve's chat line and the varp moving.
		if (run.caughtUpTo(descended + 1))
		{
			log.debug("Run moved up to delve {}, whose start it missed", descended + 1);
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		closeRunFromAnotherCharacter();
		milestones.load();
		loadTotals();
	}

	/**
	 * A run held open across a lost connection ends where it was if we come back as an alt: the
	 * character left behind was put outside, as after a long disconnect.
	 */
	private void closeRunFromAnotherCharacter()
	{
		if (resumeCheck == null || run == null || runProfile == null
			|| runProfile.equals(runHistoryStore.currentProfile()))
		{
			return;
		}

		log.debug("Back as another character, closing the run held open on the previous one");
		endRun(EndReason.DIED, run.currentLevel());
	}

	private void loadTotals()
	{
		if (totals.load())
		{
			feed.refreshLive();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount++;
			ticksWithoutBoss = 0;
			combat.bossSpawned(event.getNpc());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount = Math.max(0, bossCount - 1);
		}

		combat.npcDespawned(event.getNpc());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		settleDeath();
		checkResume();
		pickUpRun();
		trackAbandonedRun();
		combat.tickEnded();
		feed.refreshLive();
		refreshInfoBoxPicture();
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

		// Put outside with the pile lost, as a death does.
		log.debug("Back outside the cave, ending the run that was held open");
		endRun(EndReason.DIED, run.currentLevel());
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

		// The delve counter arrives with the login varps; until then a match cannot be judged.
		if (suspended != null && client.getVarpValue(VarPlayerID.TOTAL_DOM_LEVELS) <= 0)
		{
			return;
		}

		if (!tryResume(descended + 1))
		{
			startRun(Instant.now(), descended + 1, true);
		}

		feed.refreshLive();
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
			totals.loggedOut(Instant.now());
		}
		else if (state == GameState.CONNECTION_LOST)
		{
			totals.connectionLost(Instant.now());
		}
		else if (state == GameState.LOGGED_IN)
		{
			totals.loggedIn(Instant.now());
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
			milestones.seedFromDeepestLevel();
		}

		// Despawns are not delivered across a scene load. A lost connection picked up again in a
		// few seconds carries on in the same scene, so it keeps them.
		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING)
		{
			bossCount = 0;
			ticksWithoutBoss = 0;
			combat.sceneCleared();
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
		feed.refreshLive();
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
		settleSuspended();
		watch(new DelveRun(startedAt, level, partial, partial ? sessionAnchor() : null),
			runHistoryStore.currentProfile(), startedAt);
		milestones.runStarted(partial, level);
		log.debug("Doom run started on delve {} (partial={})", level, partial);
	}

	/** Makes {@code started} the run in progress. */
	private void watch(DelveRun started, String profile, Instant sessionFrom)
	{
		run = started;
		lastRun = null;
		lastRunCleared = false;
		resumeCheck = null;
		runProfile = profile;
		pickUpPending = false;
		ticksWithoutBoss = 0;
		deathLevel = -1;
		combat.runStarted();
		loot.runStarted();
		totals.runStarted(sessionFrom);
	}

	private void endRun(EndReason reason, int diedOnLevel)
	{
		// However a run the player died in comes to an end, it ended in the death.
		if (deathLevel >= 0)
		{
			reason = EndReason.DIED;
			diedOnLevel = deathLevel;
			deathLevel = -1;
		}

		DelveRun ended = run;
		run = null;
		resumeCheck = null;
		loot.reset();
		combat.stopTracking();

		if (ended == null)
		{
			return;
		}

		ended.end(reason, Instant.now(), diedOnLevel);
		loot.runEnded();
		log.debug("Doom run ended: {} after {} delves", reason, ended.lastLevel());

		// Delves are already banked; this flushes the combat since the last one, abandoned or not.
		totals.runEnded(ended.getEndedAt());

		if (reason == EndReason.ABANDONED)
		{
			milestones.runAbandoned();
			return;
		}

		milestones.runEnded(reason == EndReason.DIED ? diedOnLevel : 0,
			ended.loot().getClaimed().size());

		lastRun = ended;
		lastRunCleared = false;

		runHistoryStore.append(recordOf(ended, ended.getEndedAt(), reason, diedOnLevel, false),
			profileOf(runProfile));

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

	/** A run the plugin stopped watching, ending now as far as the history can tell. */
	private static RunRecord incompleteRecord(DelveRun unfinished)
	{
		return recordOf(unfinished, Instant.now(), EndReason.ABANDONED, -1, true);
	}

	/** The history line for a run; never read back by the plugin. */
	private static RunRecord recordOf(DelveRun run, Instant endedAt, EndReason reason,
		int diedOnLevel, boolean incomplete)
	{
		RunRecord record = new RunRecord();
		record.at = endedAt.getEpochSecond();
		record.delve = run.lastLevel();
		record.ticks = run.isPartial() ? 0 : DoomFormat.toTicks(run.clearedElapsed());
		record.end = reason;
		record.diedOn = Math.max(0, diedOnLevel);
		record.partial = run.isPartial();
		record.incomplete = incomplete;
		record.loot = run.loot().getClaimed();
		// Left out entirely for runs that attribute nothing.
		record.combat = run.getCombat().isEmpty() ? null : run.getCombat().copy();
		return record;
	}

	/** The character a run belongs to: the one it started on, or whoever is logged in. */
	private String profileOf(String startedOn)
	{
		return startedOn != null ? startedOn : runHistoryStore.currentProfile();
	}

	private void announceClear(int level)
	{
		int target = targetDelve();

		if (ChatAnnouncement.isDue(level, config.chatIntervalDelves(), target))
		{
			sendChat(ChatAnnouncement.delveCleared(run, level, target, config.paceMode()));
		}
	}

	private void sendChat(ChatAnnouncement announcement)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatAnnouncement.TYPE)
			.runeLiteFormattedMessage(announcement.formatted())
			.build());
	}

	static boolean isDoomBoss(int npcId)
	{
		return npcId == NpcID.DOM_BOSS || npcId == NpcID.DOM_BOSS_SHIELDED
			|| npcId == NpcID.DOM_BOSS_BURROWED;
	}
}
