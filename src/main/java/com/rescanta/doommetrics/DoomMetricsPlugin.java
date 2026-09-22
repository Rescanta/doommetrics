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

	/** How often the prayer regeneration potion gives a point back, in game ticks. */
	private static final int PRAYER_REGEN_PERIOD = 12;

	/**
	 * How often a hitpoint comes back on its own, in game ticks - one a minute, which is what a
	 * player who is not going out of their way to heal faster regenerates at.
	 */
	private static final int HITPOINTS_REGEN_PERIOD = 100;

	/** Package-private so the infobox can carry the same option the overlay does. */
	static final String CLEAR_OPTION = "Clear";

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
	 * The warning the game puts up for each unique in the pile as you try to go deeper - one per
	 * copy, every try: "Your loot contains Dom! Are you sure you want to descend further?". The
	 * name is taken from it only as a fallback, since the dialog also shows the item itself.
	 */
	private static final Pattern LOOT_WARNING = Pattern.compile("^Your loot contains (.+?)! Are you sure");

	/** The menu option that goes deeper, on the hole and on the loot screen alike. */
	private static final String DESCEND_OPTION = "Descend";

	/**
	 * How long a gap between runs takes the session off the panel.
	 *
	 * <p>Display only. The session itself lasts until the client closes, the character changes, or
	 * the panel's reset button is pressed - logging out and back in, or stepping away for an hour,
	 * carries it on. What this decides is whether the session figures are shown while no run is
	 * going: after half an hour without one the rows go blank rather than sitting there looking
	 * current, and the next run brings them straight back, the gap and all.
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

	/** Logs what the game shows around a delve's end, under the debug setting. See the class. */
	private LootDiagnostics lootDiagnostics;

	/**
	 * Whether {@link #lootDiagnostics} is on the event bus, which it only is while debug logging is
	 * on. It listens to scripts, varbits, sounds and object spawns, which fire far too often to hand
	 * to a listener with nothing to do - see {@link #updateDiagnostics}.
	 */
	private boolean diagnosticsRegistered;

	private DoomMetricsPanel panel;
	private NavigationButton navButton;

	/** The square, up for as long as the plugin is. It decides for itself when to draw. */
	private DoomMetricsInfoBox infoBox;

	/** The picture the client last scaled for the square - see {@link #refreshInfoBoxPicture}. */
	private BufferedImage infoBoxPicture;

	/** The pictures drawn in place of names, from the game, for as long as the plugin is up. */
	private volatile GameIcons icons;

	/** Read on the Swing thread when the detail window is built, cleared on shutdown. */
	private volatile BufferedImage icon;

	/**
	 * The run detail window while it is open, or null. Swing thread only - it is created, read and
	 * disposed there, so the client thread never touches a frame mid-layout.
	 */
	private RunDetailWindow detailWindow;

	/**
	 * The run the open window is drawing, held so a window opened between two runs has the last
	 * one to show without reaching back into a run the client thread owns. Swing thread only.
	 */
	private RunDetail windowDetail = RunDetail.empty();

	/** The last live rows pushed across, for the same reason. Swing thread only. */
	private DoomMetricsPanel.Live windowLive;

	/** This character's lifetime table, reloaded whenever the profile changes. */
	private final MilestoneTable milestones = new MilestoneTable();

	/** Milestones whose personal best has been beaten since the client started. */
	private final Set<Integer> improvedThisSession = new HashSet<>();

	/** This character's lifetime deep delve rate, reloaded whenever the profile changes. */
	private DelveTotals lifetime = new DelveTotals();

	/**
	 * The runs of the current sitting, kept until the client closes, the character changes, or the
	 * session is reset - see {@link #resetSession()}.
	 */
	private DelveTotals session = new DelveTotals();

	/** This character's lifetime healing, prayer and spec damage, banked clear by clear. */
	private CombatTotals lifetimeCombat = new CombatTotals();

	/** The same figures for the current sitting, thrown away when the sitting is. */
	private CombatTotals sessionCombat = new CombatTotals();

	/** Combat counted since the lifetime figures were last written - see {@link #flushCombat}. */
	private CombatTotals unbankedCombat = new CombatTotals();

	/**
	 * Works out what caused each heal, prayer restore and spec hitsplat. Fed only while a run is in
	 * progress, so nothing that happens outside the cave is ever counted.
	 */
	private final CombatTracker combatTracker = new CombatTracker(this::recordCombat);

	/** Works out which hits on the boss were a melee punish. Fed only while a run is in progress. */
	private final PunishTracker punishTracker = new PunishTracker(this::recordCombat, this::handBack);

	/**
	 * The boss, standing, shielded or burrowed, while it is in the scene - the one NPC whose prayer
	 * is read every tick. Held from its spawn rather than looked for, and the same object across
	 * its forms: shielding and burrowing change what it is, not which NPC it is.
	 */
	private NPC boss;

	/**
	 * The special attack energy as we last saw it. A spec is a drop in this - it only ever climbs
	 * on its own - and the weapon held when it drops is what fired.
	 */
	private int specEnergy;

	/** The boosted prayer level as we last saw it, so a restore can be read as the difference. */
	private int prayerPoints;

	/** The boosted hitpoints level as we last saw it, for the same reason. */
	private int hitpoints;

	/** Tells the regeneration potion's drip from the prayer points the gear gave back. */
	private final Regeneration prayerRegeneration = new Regeneration();

	/** Tells the hitpoints that come back on their own from the ones the gear gave back. */
	private final Regeneration hitpointsRegeneration = new Regeneration();

	/**
	 * When the session's most recent run ended, or null while one is in progress or before the
	 * session has any run in it at all. What {@link #SESSION_IDLE} is measured from.
	 */
	private Instant sessionEndedAt;

	/**
	 * What the panel's session length is measured with, started by the session's first run.
	 *
	 * <p>Logged in time rather than summed run time, because it answers a different question from
	 * the rate under it: the rate is what the time inside runs bought, and the length is how long
	 * you have been at it - banking, restocking and walking back included, because those are the
	 * sitting too. Time at the login screen is left out, since the session now outlives a logout.
	 */
	private final SessionClock sessionClock = new SessionClock();

	/** The character the session belongs to, so an alt's runs never join it. */
	private String sessionProfile;

	private DelveRun run;

	/**
	 * The last run that ended for a reason worth showing: kept for the linger window, and drawn by
	 * the run detail window until the next run starts.
	 */
	private DelveRun lastRun;

	/**
	 * Whether Clear has taken {@link #lastRun} off the game screen. The detail window goes on
	 * drawing it: Clear is for what sits over the game, and the window is only up because it was
	 * opened.
	 */
	private boolean lastRunCleared;

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
	 * The first login seen since the plugin started, or null if it started logged in and has not
	 * seen one since. One of the two bounds on the start of a run we joined part way through - see
	 * {@link LoginBound}.
	 *
	 * <p>The first rather than the latest, because a dropped connection can log you back in to the
	 * delve you were already in, so a later login does not prove a run started after it. Nothing
	 * that happened before the first one we saw can have been a run we were watching.
	 */
	private Instant loginAt;

	/**
	 * Set from the login screen's LOGGING_IN until the LOGGED_IN it leads to. Every loading screen
	 * also ends in LOGGED_IN, so this is what tells a login from walking down to the next delve.
	 */
	private boolean loggingIn;

	/**
	 * Set when the plugin starts and on a reset, and cleared once a run starts. While it is set, a
	 * player found in the cave is picked up as a run joined part way through straight away - see
	 * {@link #pickUpRun} - rather than waiting on the next delve's chat line.
	 */
	private boolean pickUpPending;

	/** What the live section last drew, so an unchanged tick costs nothing. */
	private String lastLiveKey;

	/** What the run detail window last drew, for the same reason - see {@link #refreshDetail}. */
	private String lastDetailKey;

	private int bossCount;
	private int ticksWithoutBoss;

	/**
	 * Set when "Claim and leave" is clicked, and cleared by anything that shows the run carrying on.
	 * Only while it is set is the claimed loot filling in taken as the claim - see
	 * {@link #onItemContainerChanged} - so a copy of it sent for any other reason cannot end a
	 * run. The claim script and the buttons that take the claimed loot still close the run out on
	 * their own.
	 */
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

		// Asked for up front, so they are in hand by the time anything is drawn with them.
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

		// The window outlives the side panel unless it is taken down explicitly, and a disabled
		// plugin leaving a frame on screen would go on drawing data it no longer maintains.
		SwingUtilities.invokeLater(this::closeDetailWindow);

		// Combat counted since the last clear really happened, so it is written out before the
		// memory holding it is let go.
		flushCombat();
		reset();
	}

	/**
	 * The pictures drawn in place of names: the game's, while the plugin is up, and none at all
	 * otherwise. Package-private so the preview harness can hand over its own.
	 */
	Icons getIcons()
	{
		GameIcons held = icons;
		return held == null ? Icons.NONE : held;
	}

	/**
	 * Hands the pictures over again as each one arrives from the game, so a name drawn in words
	 * while its picture was on its way is swapped for it. Swing thread.
	 */
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

	/**
	 * Tells the client when the square's picture has changed - a single counter picked, icons
	 * switched on or off, or the icon arriving from the game. The client scales an infobox's
	 * picture once, when told to, rather than on every frame, so a picture swapped without telling
	 * it would never be drawn. Looked at once a tick, which costs a comparison.
	 */
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

	/**
	 * Puts the plugin back to how it was before it saw anything, dropping every run, session and
	 * lifetime figure it holds in memory. Called on start up and on shut down, so a disabled plugin
	 * holds nothing but its own empty collections. What is saved - the lifetime totals, the
	 * milestone table and the run history - is left on disk, and read back once a character is
	 * logged in again.
	 */
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

	/**
	 * Drops everything gathered about runs and the session: the run in progress, the last one and
	 * what the detail window draws of it, the session figures and clock, and the combat not yet
	 * written out. What the plugin is merely watching - who is logged in, whether the boss is in the
	 * scene, the prayer and hitpoints last read - is not data about a run, and is left alone.
	 */
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

		// Forgotten too, so the next refresh pushes the emptied figures rather than deciding
		// nothing has changed.
		lastLiveKey = null;
		lastDetailKey = null;
	}

	/**
	 * Starts the session over and drops the run in progress, from the panel's reset button.
	 *
	 * <p>Everything the plugin holds about runs and the session is let go - see {@link
	 * #forgetRuns}. The run is thrown away rather than ended: it is not written to the history file
	 * and nothing is announced. What it already banked to the lifetime figures and the milestone
	 * table stays, because a delve cleared is a delve cleared - the combat counted since its last
	 * clear is written out first for the same reason. Those lifetime figures stay in memory as well,
	 * since the panel is showing them. If the player is still in the cave, the delve they are in is
	 * picked up on the next tick as a run joined part way through - see {@link #pickUpRun}. That
	 * run's first clear is left out of the rates, unless it was picked up between delves and saw
	 * the next one start.
	 */
	void resetSession()
	{
		log.debug("Session reset from the panel");
		flushCombat();
		forgetRuns();

		refreshTable();
		refreshLive();
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

	/** Puts the diagnostics on the event bus while debug logging is on, and takes them off after. */
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
		// Ahead of the type check: the drain line is spam, not a game message, and spam is where
		// it will stay.
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

		// Tags stripped first: the pet line comes coloured, so the raw message never starts with
		// the words.
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

	/**
	 * The pet leaving with a claim.
	 *
	 * <p>The game says nothing about the pet until the loot is claimed, and then says this whether
	 * or not the pet is new - "would have been followed" is the one already owned, which never
	 * reaches the claimed loot at all. So this is the claim's word that the pet was in the pile.
	 * One the warnings already placed stays where it is; one that dropped on the last delve, and so
	 * never came up in a warning, is placed there.
	 *
	 * <p>It lands in the same tick as the claim, ahead of the claimed loot that closes the run out -
	 * see {@link #finishClaim}.
	 */
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

	/**
	 * A "Your loot contains" warning, as you try to go deeper with a unique still in the pile.
	 *
	 * <p>This is the one sight of a unique nobody can skip: the loot screen is optional and the
	 * pile is only sent when it is opened, but every descend brings these up, one per copy. The
	 * dialog holds the item itself as well as its name, and the item is read on the way out of this
	 * event rather than in it, once the dialog has been filled in.
	 */
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

		// Both copies of the pile, taking the larger count of each drop. The claim script reads the
		// first, which is the one the game's own loot tracking reads; the second is the pile as it
		// stands mid-run, read too so a claim never misses a drop the run watched land.
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

	/**
	 * How many of each notable drop a copy of the pile holds, keyed by item id. Empty for a pile
	 * the game has not sent.
	 *
	 * <p>Totalled across the pile, because two of the same unique are two separate slots holding
	 * one each, and reporting them one slot at a time would look like the same single drop seen
	 * twice.
	 */
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

	/** Names the item from the cache, so the history is not pinned to names hardcoded here. */
	private void recordLoot(int itemId, int quantity)
	{
		run.recordLoot(itemId, itemName(itemId), quantity);
	}

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
			// The delve it was written down on rather than the one we are standing on: a drop the
			// glow had already marked keeps the glow's delve, and the two are not the same.
			int delve = run.sawInPile(itemId, itemName(itemId), quantity);

			if (delve != DelveRun.NOT_RECORDED)
			{
				log.debug("Item {} recorded on delve {}, pile now holds {}", itemId, delve, quantity);
			}
		});

		// The claimed loot, filled in as the claim is confirmed - the surest sign the claim went
		// through. Empty is the same copy being cleared as the loot is taken, long after. Only once
		// "Claim and leave" has been clicked, so a copy sent for any other reason cannot end the run.
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

	/**
	 * Closes out a run whose loot has just been claimed, reading the claim first.
	 *
	 * <p>Claiming is two clicks - "Claim and leave", then Confirm on the warning that it ends the run
	 * - and only the second one claims anything. Backing out at the warning leaves you in the delve
	 * with the run going. Everything that says the claim happened lands on the Confirm: the claimed
	 * loot, the claim script, and the pet's chat line ahead of both.
	 */
	private void finishClaim()
	{
		if (run == null)
		{
			return;
		}

		claimLootPile();
		endRun(EndReason.FINISHED, -1);
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
			Instant now = Instant.now();
			startRun(now, level, level > 1);

			if (level > 1)
			{
				// Joined, but at the delve's own start, so its first clear is timed like the rest.
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

		// A run joined with nothing to bound its start still counts the kill, but offers no time.
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

	/**
	 * Trying to descend, and the clicks around a claim.
	 *
	 * <p>The claim and leaving are the only end signal for a trip that never descended past delve 1:
	 * DOM_CURRENT_LEVEL_TEMP stays at zero throughout delve 1, so it has no transition to fire on.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (run == null)
		{
			return;
		}

		int widgetId = event.getParam1();

		// Trying to go deeper, from the hole or the loot screen. The warnings this brings up are
		// counted afresh - but not the Descend on a warning itself, which is the same try going on
		// to the next warning in the row.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_DESCEND
			|| (DESCEND_OPTION.equals(Text.removeTags(event.getMenuOption()))
				&& WidgetUtil.componentToInterface(widgetId) != InterfaceID.OBJECTBOX))
		{
			run.descending();
			claimRequested = false;
			return;
		}

		// "Claim and leave" is not the claim: it asks for a Confirm first, and backing out of that
		// carries on the run. The claim closes the run out when it lands - see finishClaim. The
		// click does say a claim may be on its way, which is what the claimed loot is read against.
		if (widgetId == InterfaceID.DomEndLevelUi.BTN_CLAIM)
		{
			claimRequested = true;
			return;
		}

		// Taking the claimed loot to the inventory or the bank, and leaving, all come after the
		// claim on the same screen, so a run still open by then has had its claim missed.
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

	/**
	 * The game's own signal that the loot has been claimed.
	 *
	 * <p>Claiming is the moment the pile becomes yours - leave any other way, or die, and it stays
	 * behind - so it is the only moment worth reading the pile at. The script fires on the Confirm,
	 * in among the claimed loot arriving, so the claim is read once the tick's events are through
	 * rather than in the middle of them. The claimed loot arriving closes the run out too, and
	 * whichever gets there first does it - see {@link #finishClaim}.
	 */
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

	/**
	 * Every hitsplat of yours on something else, offered to the tracker to identify. Only while a
	 * run is in progress: what your gear does in the bank is not what this plugin is about.
	 */
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

		// Damage only. Healing is read off the hitpoints level instead - see onStatChanged - and
		// reading it in both places would count every heal twice.
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
			// Held to the end of the tick, when whether it was a punish is known. A hit that was
			// not comes back through handBack and is counted as any other hit is.
			punishTracker.hit(hitsplat.getAmount(), hitsplat.isMine(), tick);
			return;
		}

		if (hitsplat.isMine())
		{
			// Passed on as a zero rather than skipped when it is a hit that does not count: the
			// spec still spent itself on it, and a budget left unspent would be taken by the
			// auto-attack behind it instead.
			int amount = countsAsDamage(target) ? hitsplat.getAmount() : 0;
			logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
			combatTracker.damaged(amount, tick);
		}
	}

	/**
	 * Whether a hitsplat on the boss can be part of a punish: one of ours, or one drawn in another
	 * player's colours.
	 *
	 * <p>The strength-bonus hitsplats a punish brings are not the ones {@link Hitsplat#isMine()}
	 * accepts - they are drawn greyer than a hit, and the game does not even draw all of them - so
	 * they would be dropped with everything else not ours. The fight is solo, which makes a splat
	 * in another player's colours on the boss ours all the same, and the Doom has a type of its own
	 * that is taken for the same reason. What stays out is damage over time: poison, venom and burn
	 * are ticking on their own schedule, not landing with the swing.
	 *
	 * <p>Which of these the bonus splats really are is written to the log with every splat on the
	 * boss - see {@link #logBossHitsplat}.
	 */
	private static boolean isPunishSplat(Hitsplat hitsplat)
	{
		return hitsplat.isMine() || hitsplat.isOthers()
			|| hitsplat.getHitsplatType() == HitsplatID.DOOM;
	}

	/** Every hitsplat on the boss, with its type, while the debug toggle is on. */
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

	/**
	 * A hit of ours on the boss that was held for the punish check, back to be counted the way any
	 * other is - whole if it was no punish, and as nothing if it was. See {@link PunishTracker}.
	 */
	private void handBack(int amount, int tick)
	{
		logAttribution(SpecEffect.Kind.DAMAGE, amount, tick);
		combatTracker.damaged(amount, tick);
	}

	/**
	 * Whether a hit on {@code target} is worth adding to a damage figure: only a hit on the boss
	 * itself, standing or burrowed.
	 *
	 * <p>Everything else a spec gets fired at down there is a job rather than a health bar anybody
	 * is racing. The volatile earth before the shockwaves guarantees a max hit and has to be broken
	 * to raise the shield that keeps you alive, and the larvae have to die before they reach you -
	 * which makes both the best things in the delve to spend a blowpipe or an eldritch spec on, and
	 * what makes counting the damage misleading. Nor does the boss count while its demonic shield
	 * is up: what lands on the shield is not what gets the delve done. A delve's spec damage would
	 * read as though the work had been done there.
	 *
	 * <p>The two that count are listed rather than the things that do not, so an add this version
	 * has never seen stays out of the figure instead of quietly joining it. Every figure here is a
	 * floor, and an unknown target costs one a hit rather than inflating it.
	 *
	 * <p>Only the damage is dropped. The heal and the prayer that spec was fired for are counted
	 * exactly as they always were, which is the whole reason it was fired at that target.
	 */
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

	/** The boss standing or burrowed - the two forms a hit on counts. See {@link #countsAsDamage}. */
	private static boolean isCountedBoss(Actor target)
	{
		if (!(target instanceof NPC))
		{
			return false;
		}

		int id = ((NPC) target).getId();
		return id == NpcID.DOM_BOSS || id == NpcID.DOM_BOSS_BURROWED;
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

		CombatMetric metric = combatTracker.wouldCredit(kind, amount, tick);
		log.debug("{} of {} at tick {} -> {}", kind, amount, tick,
			metric == null ? "nothing open, held for this tick" : metric.key());
	}

	/**
	 * The player starting an animation, offered to the punish tracker as a possible swing - which
	 * it turns out to be only if a melee weapon is in hand once the tick ends. See
	 * {@link PunishTracker#swung}.
	 *
	 * <p>The boss's own animations are logged under the debug toggle too, as the other place a
	 * punish could be read from if the prayer ever stops saying so.
	 */
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
			// The beam being cut off is the one sign of a punish landed before the prayer shows.
			// Behind its shield the boss cuts it off over and over on its own, which is why only
			// the standing boss's is taken - see PunishTracker.
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

		// An animation ending is not a swing starting, and nor is eating or drinking - the one
		// thing a player is likely to do with a melee weapon already in hand under the prayer.
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

	/**
	 * A level going up, offered to the tracker with whatever came back on its own taken out of it
	 * first - see {@link Regeneration}.
	 */
	private void rose(SpecEffect.Kind kind, Regeneration regeneration, int period, int from, int to,
		int natural)
	{
		int rise = to - from;
		int tick = client.getTickCount();
		// The whole rise, before what came back on its own is taken out of it: no window that takes
		// a heal or a restore cares what size it is, so this only has to be the effect being asked
		// about - see SpecEffect#isSized.
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

	/** How often the prayer regeneration potion is dripping, or 0 while no dose is in effect. */
	private int prayerRegenerationPeriod()
	{
		return client.getVarbitValue(VarbitID.PRAYER_REGENERATION_POTION_TIMER) > 0
			? PRAYER_REGEN_PERIOD
			: 0;
	}

	/**
	 * How often a hitpoint comes back on its own, in ticks.
	 *
	 * <p>One a minute as standard. Rapid Heal or a cape carrying the Hitpoints cape's perk doubles
	 * it, and the two do not stack with each other; a regen bracelet doubles it again and does
	 * stack with either, which is what makes four a minute the ceiling.
	 */
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

	/**
	 * Whether the cape being worn doubles hitpoints regeneration. The Hitpoints cape does, and so
	 * does a max cape, which inherits every skillcape's perk - and comes in more recolours than a
	 * list of ids can keep up with, so it is caught by name as {@link SpecWeapon} catches weapons.
	 */
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

	/**
	 * A healing spell landing on something, for the spells that name themselves that way. The blood
	 * spells announce themselves in the chat instead - see {@link #BLOOD_DRAIN}.
	 */
	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
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

	/**
	 * Credits an attributed amount to the run in progress, and to the session and the lifetime
	 * buffer alongside it - see {@link #flushCombat}. The tracker's only way out.
	 */
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

			int itemId = equipped(EquipmentInventorySlot.WEAPON);
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
	}

	/**
	 * Starts the session over when the client comes back as a different character.
	 *
	 * <p>Every other figure in the panel is the logged in character's own, and a session rate that
	 * had quietly summed two players' runs together would belong to neither of them. Only a change
	 * of character does this - logging the same one back in carries the sitting on, however long
	 * it was away.
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
		sessionClock.reset();
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

	/**
	 * The hole a cleared delve is left by, which comes up as the glowing one while there is a
	 * unique in the pile - the only sign of a drop the game gives a client that touches nothing.
	 * Everything else that names a unique waits on the pile being investigated, a descend being
	 * tried or the loot being claimed, and a player who descends straight past all three would
	 * otherwise have the drop appear out of nowhere at the end of the run.
	 *
	 * <p>Only between delves, which is where a cleared delve's hole belongs. The scene sends every
	 * object in it again whenever it is rebuilt, so this fires for the same hole more than once -
	 * which places nothing more, see {@link DelveRun#uniqueSignalled}.
	 */
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

	/**
	 * Tells the punish tracker whether the boss is praying, and what is in hand if a swing is
	 * waiting to be read - at the end of the tick, which is when both are settled.
	 */
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

	/**
	 * Whether the boss has an overhead prayer up. Down here that is only ever the one against magic
	 * and ranged that a punish answers, so any icon at all is the signal, and which one it is
	 * matters only to the log.
	 */
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

	/** Whether a weapon's bonuses say melee - see {@link PunishWeapon#isMelee}. */
	private boolean isMeleeWeapon(int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		ItemEquipmentStats bonuses = stats == null ? null : stats.getEquipment();

		return bonuses != null && PunishWeapon.isMelee(bonuses.getAstab(), bonuses.getAslash(),
			bonuses.getAcrush(), bonuses.getArange(), bonuses.getAmagic());
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

	/**
	 * Picks up the run the player is already in when the plugin was turned on or reset, so the
	 * overlay and the combat counters start now rather than at the next delve's chat line.
	 *
	 * <p>DOM_CURRENT_LEVEL_TEMP reads one less than the delve being fought, and is cleared on the
	 * way out of the cave, so anything above zero is delve 2 or deeper. It reads zero on delve 1
	 * too, so there the boss being in the scene is what says we are inside. The spawn events for
	 * NPCs already there are replayed when a plugin starts, so the count is right by the first
	 * tick. For the few seconds between a delve's chat line and the game moving the varplayer the
	 * delve is read one short; the clear puts that right.
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
		GameState state = event.getGameState();

		if (state == GameState.LOGIN_SCREEN)
		{
			sessionClock.pause(Instant.now());
		}
		else if (state == GameState.CONNECTION_LOST)
		{
			// Only noted: whether the wait counts depends on where the drop ends - see the clock.
			sessionClock.connectionLost(Instant.now());
		}
		else if (state == GameState.LOGGED_IN)
		{
			// Every login, not just the first: a hop never pauses the clock, so this is a no-op.
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
			// Only ever the first - see the field. A run held open across a dropped connection is
			// unaffected either way: it took its anchor when it started, and nothing moves it.
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
			boss = null;
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
		// Give the boss the full grace period to appear, whatever the counter was doing before.
		ticksWithoutBoss = 0;
		// A spec fired on the way in belongs to nothing we are counting, and its window must not
		// be left open to swallow the first heal of the trip.
		combatTracker.reset();
		punishTracker.reset();
		claimRequested = false;
		prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);
		hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);

		if (partial)
		{
			// Whatever is already in the pile dropped before we were watching, on delves we cannot
			// name. A fresh run starts with an empty pile, so only a joined one needs this.
			notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE))
				.forEach(run::pileAlreadyHeld);
			notableDrops(client.getItemContainer(InventoryID.DOM_LOOTPILE_DURING))
				.forEach(run::pileAlreadyHeld);
		}

		openSession(startedAt);
		log.debug("Doom run started on delve {} (partial={})", level, partial);
	}

	/**
	 * Puts the run about to start into the session already going, however long ago its last run
	 * ended, starting the session's clock if this is its first run.
	 *
	 * @param startedAt when the run about to start began, which is where a fresh session's length
	 *                  is measured from. A run we joined part way through dates the sitting from
	 *                  where we picked it up rather than from a start nobody saw.
	 */
	private void openSession(Instant startedAt)
	{
		startSessionForCurrentCharacter();
		sessionClock.start(startedAt);

		// Cleared either way: a run is in progress, so there is no idle gap to be measuring.
		sessionEndedAt = null;
	}

	/**
	 * Whether the last run ended recently enough for the session to be shown between runs. See
	 * {@link #SESSION_IDLE} - this only decides what the panel draws, never what the session holds.
	 */
	private boolean sessionAlive(Instant now)
	{
		return sessionEndedAt != null
			&& Duration.between(sessionEndedAt, now).compareTo(SESSION_IDLE) < 0;
	}

	/**
	 * Adds the delve just cleared to the session and lifetime rates, and settles the combat figures
	 * counted since the last clear.
	 *
	 * <p>Banked a delve at a time rather than a run at a time, so the figures never depend on how a
	 * run ends: a client closed mid-run keeps every delve cleared before it, and a session reset
	 * only takes the session with it. Every run counts, including the ones the history file leaves
	 * out - an {@link EndReason#ABANDONED} run still cleared the delves we watched it clear.
	 *
	 * <p>Each clear is charged its segment, the time since the clear before it, so a run's charge
	 * sums to the span from its start to its last clear - the span every other figure here is built
	 * on. The one exception is the first clear of a run we joined part way into a delve: its segment
	 * starts wherever we happened to pick the run up, which is a guess, so that delve is left out
	 * of the rates rather than charged a time nobody measured. A run joined between delves, or on
	 * a delve's chat line, saw that delve start, and is charged like any other. The run's own pace
	 * leaves out the same delve - see {@link DelveRun#fullPace} - so what the overlay and the chat
	 * say of a joined run is what it adds here.
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

	/**
	 * Writes the combat figures counted since the last write to the character's lifetime.
	 *
	 * <p>They reach the session and the in-memory buffer as they are counted - see {@link
	 * #recordCombat} - but a heal is far too frequent a thing to write config on, so the buffer is
	 * written out on each clear, when the run ends, on a reset, and when the plugin is turned off.
	 * Closing the client does not turn plugins off, so heals since the last clear are lost if the
	 * client closes mid-delve, the same way the delve itself is.
	 *
	 * <p>Guarded the same way the delve rate is: a run held open across a dropped connection can
	 * outlive the login that made it, and filing one character's healing against another's
	 * lifetime would be worse than not filing it at all.
	 */
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

	/**
	 * Whether the logged in character is the one the run was started on, and so the one whose
	 * lifetime figures it may be written to.
	 */
	private boolean lifetimeBelongsToRun()
	{
		return totalsStore.hasProfile()
			&& (runProfile == null || runProfile.equals(runHistoryStore.currentProfile()));
	}

	/**
	 * Posts elapsed time and pace for the delve just cleared, when the interval or the target
	 * says it is due - see {@link ChatAnnouncement#isDue}.
	 */
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

		// The delves are already banked, clear by clear; what is left is the combat counted since
		// the last one, and the sitting's idle clock, which restarts from the moment the run ended.
		// Ahead of the abandoned check: a run we lost sight of still healed you.
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

	/**
	 * A moment a run we joined part way through can be proven not to have started before, or null
	 * when there is none - see {@link LoginBound}.
	 */
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

		// The same rows for the window, except that it keeps drawing a run the overlay's linger
		// has taken down - so the head of the window cannot blank out from under a chart that is
		// still showing the run those figures belong to.
		DoomMetricsPanel.Live detailLive = detail == display
			? live
			: (detail == null ? null : DoomMetricsPanel.Live.of(detail, config.paceMode(),
				targetDelve(), config.targetPrediction()));

		DoomMetricsPanel.Stats stats = statsSnapshot();
		boolean showCombat = run != null || sessionAlive(Instant.now());
		String key = (live == null ? "" : live.key())
			+ "|" + stats.key() + "|" + combatKey(showCombat)
			+ (detailLive == live ? "" : "|" + (detailLive == null ? "" : detailLive.key()));

		// The timers only move once a second, so most ticks have nothing to redraw. The rates move
		// less again - neither can change until a delve is cleared or a run ends - and the combat
		// figures only move when something heals or hits.
		if (key.equals(lastLiveKey))
		{
			return;
		}

		lastLiveKey = key;

		// Built only once something has actually moved, so an idle tick allocates nothing to hand
		// across to a panel that would draw the same numbers again.
		CombatTotals combat = showCombat ? combatSnapshot() : null;

		// Handed over whether or not a sitting is going: the lifetime tab is the one left worth
		// reading between runs, and a delve banked while the panel sits idle still moves it.
		CombatTotals lifetimeShown = lifetimeCombat.copy();

		SwingUtilities.invokeLater(() ->
		{
			target.setLive(live);
			target.setStats(stats);
			target.setCombat(combat, lifetimeShown);

			// Held so a window opened between two ticks has a head to draw, rather than sitting
			// blank until the next figure moves.
			windowLive = detailLive;

			if (detailWindow != null)
			{
				detailWindow.setLive(detailLive);
			}
		});
	}

	/**
	 * The sitting's combat figures, the run in progress included - every amount reaches the session
	 * the moment it is counted.
	 */
	private CombatTotals combatSnapshot()
	{
		return sessionCombat.copy();
	}

	/**
	 * Enough of both tallies the panel can draw to tell one repaint from the next, read straight
	 * out of them rather than out of a snapshot - the point is to decide whether a snapshot is
	 * worth taking.
	 *
	 * <p>The sitting's reads as zeroes between sittings, which is also what the panel is shown:
	 * there is nothing being earned, and leaving this morning's numbers up would say otherwise.
	 * The character's is always in the key, because the lifetime tab is drawn between sittings
	 * too and a delve banked into it has to reach the panel.
	 */
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

	/**
	 * The sitting's figures and the character's, as strings for the panel to draw.
	 *
	 * <p>Both move clear by clear as a run goes - see {@link #bankClear} - so a sitting holding one
	 * run reads what that run's Full pace does, and what is shown mid-run is already saved.
	 */
	private DoomMetricsPanel.Stats statsSnapshot()
	{
		Instant now = Instant.now();

		// Long enough without a run and the rows go blank rather than looking current. The session
		// is still held, and the next run puts it back on show.
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

	/**
	 * How long this sitting has been going, or null before it has a run in it.
	 *
	 * <p>Keeps counting between runs while logged in, because the gap between two runs is part of
	 * the sitting the same way the runs are, and stands still while logged out - see {@link
	 * SessionClock}.
	 */
	private String sessionLength(Instant now)
	{
		Duration elapsed = sessionClock.elapsed(now);
		return elapsed == null ? null : DoomFormat.duration(elapsed);
	}

	/** What a rate is made of, so the figure above it can be checked rather than taken on trust. */
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

	/**
	 * Opens the run detail window, or brings it forward if it is already up. Runs on the Swing
	 * thread, from the side panel's button.
	 */
	private void openDetailWindow()
	{
		if (detailWindow == null)
		{
			detailWindow = new RunDetailWindow(icon, () -> detailWindow = null);
			detailWindow.setIcons(getIcons());
			detailWindow.setHideEmpty(config.hideEmptyCounters());
			detailWindow.setFolding(GroupHeading.parseFolded(config.foldedDetailGroups()),
				folded -> config.foldedDetailGroups(GroupHeading.formatFolded(folded)));
			// Whatever was last pushed across, so a window opened mid-delve shows the run it is
			// in the middle of rather than filling in on the next clear.
			detailWindow.setDetail(windowDetail);
			detailWindow.setLive(windowLive);
		}

		detailWindow.open(SwingUtilities.getWindowAncestor(panel));
	}

	private void closeDetailWindow()
	{
		RunDetailWindow window = detailWindow;

		// Cleared first so the frame's own close callback has nothing left to do, and cleared
		// whether or not a window is up, so a plugin switched off holds no run to draw. Only ever
		// reached on shutdown - a window closed by the reader goes through that callback alone,
		// and keeps what was pushed to it so reopening shows the run rather than an empty frame.
		detailWindow = null;
		windowDetail = RunDetail.empty();
		windowLive = null;

		if (window != null)
		{
			window.dispose();
		}
	}

	/**
	 * The run the detail window is about: the one in progress, or the last one that ended.
	 *
	 * <p>Unlike {@link #getDisplayRun} this ignores the linger setting and Clear. Both are there so
	 * an overlay nobody asked for does not sit over the game world for the rest of the evening; a
	 * window is only on screen because it was opened, and a reader who opens it half an hour after
	 * a run wants the run rather than an empty frame.
	 */
	private DelveRun detailRun()
	{
		return run != null ? run : lastRun;
	}

	/**
	 * Takes the current run apart and pushes it to the window, if anything about it has changed.
	 *
	 * <p>Called on every tick, and almost always does nothing: what a snapshot holds only moves when
	 * a delve is killed, when something is counted in the wait after a kill, and when that wait ends
	 * - see {@link RunDetail#keyFor}. A run four hundred delves deep is therefore taken apart a few
	 * times a delve, not once per tick and not once per heal.
	 */
	private void refreshDetail()
	{
		DelveRun target = detailRun();
		String key = RunDetail.keyFor(target);

		if (key.equals(lastDetailKey))
		{
			return;
		}

		lastDetailKey = key;

		// Built on the client thread, which owns the run, and immutable once built - so the Swing
		// thread never reads a tally this thread is still adding to.
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

	/**
	 * Writes a finished run to the history file.
	 *
	 * <p>Nothing reads it back: what the plugin shows is this session's runs, which are in memory.
	 * It is still written, because the record is cheap to keep and impossible to recover once a
	 * run is over - see {@link RunHistoryStore}.
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
		// Left out entirely for the many runs that attribute nothing, rather than written as a row
		// of zeroes on every line of the file.
		record.combat = ended.getCombat().isEmpty() ? null : ended.getCombat().copy();

		runHistoryStore.append(record,
			runProfile != null ? runProfile : runHistoryStore.currentProfile());
	}

	/**
	 * The delve being aimed for, or 0 when the target is switched off - the one form the panel and
	 * the announcement both read it in, so neither has to test the checkbox for itself.
	 */
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
