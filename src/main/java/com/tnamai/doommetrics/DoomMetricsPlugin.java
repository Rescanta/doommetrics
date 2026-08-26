package com.tnamai.doommetrics;

import com.google.inject.Provides;
import java.time.Instant;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
	 * The Doom varplayers sit in a contiguous block. Only DOM_LAST_DELVE_LEVEL drives behaviour;
	 * the rest are logged under the debug toggle so timings can be diagnosed from a real run.
	 */
	private static final int DOM_VARP_FIRST = VarPlayerID.DOM_LAST_DELVE_LEVEL;
	private static final int DOM_VARP_LAST = VarPlayerID.DOM_PREVIOUS_DEEPEST_LEVEL;

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

	@Provides
	DoomMetricsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoomMetricsConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		run = null;
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		run = null;
	}

	DelveRun getRun()
	{
		return run;
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

		if (varpId != VarPlayerID.DOM_LAST_DELVE_LEVEL)
		{
			return;
		}

		int level = event.getValue();

		if (level > 0)
		{
			if (run == null)
			{
				startRun(level > 1);
			}
		}
		else if (run != null)
		{
			// The delve state cleared without us seeing a claim, a leave or a death.
			endRun(EndReason.ABANDONED, -1);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (run != null)
		{
			return;
		}

		int id = event.getNpc().getId();
		if (id == NpcID.DOM_BOSS || id == NpcID.DOM_BOSS_SHIELDED || id == NpcID.DOM_BOSS_BURROWED)
		{
			startRun(client.getVarpValue(VarPlayerID.DOM_LAST_DELVE_LEVEL) > 1);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.DOM_END_LEVEL_UI)
		{
			return;
		}

		if (run == null)
		{
			// Enabled part way through a run, or every start signal was missed.
			startRun(true);
		}

		int varpLevel = client.getVarpValue(VarPlayerID.DOM_LAST_DELVE_LEVEL);
		int expected = run.currentLevel();
		int level = varpLevel > 0 ? varpLevel : expected;

		if (config.debugLogging())
		{
			log.debug("End level UI: varp={} expected={} using={}", varpLevel, expected, level);
		}

		if (level <= run.lastLevel())
		{
			log.debug("Ignoring repeat completion for delve {}", level);
			return;
		}

		DelveRun.Split split = run.complete(level, Instant.now());
		log.debug("Delve {} cleared in {}", level, DoomFormat.duration(split.duration));

		announceInterval(level);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (run == null)
		{
			return;
		}

		int widgetId = event.getParam1();

		if (widgetId == InterfaceID.DomEndLevelUi.BTN_CLAIM)
		{
			endRun(EndReason.CLAIMED, -1);
		}
		else if (widgetId == InterfaceID.DomEndLevelUi.BTN_LEAVE)
		{
			endRun(EndReason.LEFT, -1);
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
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (run != null && (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST))
		{
			endRun(EndReason.ABANDONED, -1);
		}
	}

	private void startRun(boolean partial)
	{
		run = new DelveRun(Instant.now(), partial);
		log.debug("Doom run started (partial={})", partial);
	}

	/**
	 * Posts elapsed time and pace when the delve number is a multiple of the configured interval.
	 * Shallow delves are skipped, so an interval of 5 reports at delve 10, 15, 20 and so on.
	 */
	private void announceInterval(int level)
	{
		int interval = config.chatIntervalDelves();

		if (interval <= 0 || level < config.deepDelveLevel() || level % interval != 0)
		{
			return;
		}

		sendChat(String.format("Delve %d | %s elapsed | %s",
			level,
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

		ended.end(reason, diedOnLevel);
		log.debug("Doom run ended: {} after {} delves", reason, ended.lastLevel());

		if (reason == EndReason.ABANDONED || !config.announceRunEnd() || ended.lastLevel() == 0)
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
			sendChat(String.format("Run complete: delve %d | %s | %s",
				ended.lastLevel(), elapsed, pace));
		}
	}

	private Double pace(DelveRun target)
	{
		return target.pace(config.paceMode(), config.deepDelveLevel(), config.paceAverageFromLevel());
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
