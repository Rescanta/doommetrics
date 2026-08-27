package com.rescanta.doommetrics;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

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

	private DelveRun run;

	/** The last run that ended for a reason worth showing, kept for the linger window. */
	private DelveRun lastRun;

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
		reset();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		reset();
	}

	private void reset()
	{
		run = null;
		lastRun = null;
		bossCount = 0;
		ticksWithoutBoss = 0;
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
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
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
	}

	/**
	 * Claiming loot and leaving are both explicit "I am done" clicks, and they are the only end
	 * signal for a trip that never descended past delve 1: DOM_CURRENT_LEVEL_TEMP stays at zero
	 * throughout delve 1, so it has no transition to fire on.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (run == null)
		{
			return;
		}

		int widgetId = event.getParam1();

		if (widgetId == InterfaceID.DomEndLevelUi.BTN_CLAIM
			|| widgetId == InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			endRun(EndReason.FINISHED, -1);
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
	public void onVarbitChanged(VarbitChanged event)
	{
		int varpId = event.getVarpId();

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
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
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
		if (isDoomBoss(event.getNpc().getId()))
		{
			bossCount = Math.max(0, bossCount - 1);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
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

		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			// Despawns are not delivered across a scene load, so the count has to be rebuilt.
			bossCount = 0;
			ticksWithoutBoss = 0;
		}

		if (run != null && (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST))
		{
			endRun(EndReason.ABANDONED, -1);
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() == overlay && CLEAR_OPTION.equals(event.getEntry().getOption()))
		{
			lastRun = null;
		}
	}

	private void startRun(Instant startedAt, int level, boolean partial)
	{
		run = new DelveRun(startedAt, level, partial);
		lastRun = null;
		// Give the boss the full grace period to appear, whatever the counter was doing before.
		ticksWithoutBoss = 0;
		log.debug("Doom run started on delve {} (partial={})", level, partial);
	}

	/**
	 * Posts elapsed time and pace when the delve number is a multiple of the configured interval.
	 * Shallow delves are skipped, so an interval of 5 reports at delve 10, 15, 20 and so on.
	 */
	private void announceInterval(int level, DelveRun.Split split)
	{
		int interval = config.chatIntervalDelves();

		if (interval <= 0 || level < config.deepDelveLevel() || level % interval != 0)
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

		if (ended == null)
		{
			return;
		}

		ended.end(reason, Instant.now(), diedOnLevel);
		log.debug("Doom run ended: {} after {} delves", reason, ended.lastLevel());

		if (reason == EndReason.ABANDONED)
		{
			return;
		}

		lastRun = ended;

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

	private Double pace(DelveRun target)
	{
		return target.pace(config.paceMode(), config.deepDelveLevel(), config.paceAverageFromLevel());
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
