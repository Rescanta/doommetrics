package com.rescanta.doommetrics;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.Subscribe;

/**
 * Debug-only logger for what the game shows around the end of a delve, written to find out how a
 * unique drop can be seen without opening the loot pile. Found the glowing hole; ruled out sounds.
 * On the event bus only while debug logging is on, and only writes from the boss's death to the
 * next delve and shortly after a run ends. Every line carries the game tick. Client thread only.
 */
@Slf4j
class LootDiagnostics
{
	/** How long after the boss dies to keep watching before the clear arrives to take over. */
	private static final int AFTER_BOSS_DEATH_TICKS = 20;

	/** How long after a run ends to keep watching: the claim script and its chat lines come later. */
	private static final int AFTER_RUN_TICKS = 100;

	private final Client client;
	private final DoomMetricsConfig config;

	/** Whether a run is going. */
	private final BooleanSupplier runActive;

	/** Whether the run going has cleared a delve and not yet started the next. */
	private final BooleanSupplier betweenDelves;

	private int bossDiedTick = Integer.MIN_VALUE / 2;
	private int runEndedTick = Integer.MIN_VALUE / 2;
	private boolean wasActive;

	/** Scripts fired this tick and how many times each, written out on the tick. */
	private final Map<Integer, Integer> scripts = new TreeMap<>();

	/** Interfaces loaded this tick, whose text is written out on the next once their scripts ran. */
	private final Map<Integer, Integer> pendingDumps = new TreeMap<>();

	LootDiagnostics(Client client, DoomMetricsConfig config, BooleanSupplier runActive,
		BooleanSupplier betweenDelves)
	{
		this.client = client;
		this.config = config;
		this.runActive = runActive;
		this.betweenDelves = betweenDelves;
	}

	private boolean capturing()
	{
		if (!config.debugLogging())
		{
			return false;
		}

		int tick = client.getTickCount();
		return (runActive.getAsBoolean() && betweenDelves.getAsBoolean())
			|| tick - bossDiedTick <= AFTER_BOSS_DEATH_TICKS
			|| tick - runEndedTick <= AFTER_RUN_TICKS;
	}

	private int tick()
	{
		return client.getTickCount();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean active = runActive.getAsBoolean();

		if (wasActive && !active)
		{
			runEndedTick = tick();
		}

		wasActive = active;

		if (!pendingDumps.isEmpty())
		{
			for (int group : pendingDumps.keySet())
			{
				dumpInterface(group);
			}

			pendingDumps.clear();
		}

		if (!scripts.isEmpty())
		{
			if (capturing())
			{
				log.debug("[diag] tick {} scripts fired {}", tick(), scripts);
			}

			scripts.clear();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();

		if (config.debugLogging() && actor instanceof NPC
			&& DoomMetricsPlugin.isDoomBoss(((NPC) actor).getId()))
		{
			bossDiedTick = tick();
			log.debug("[diag] tick {} boss died ({})", tick(), ((NPC) actor).getId());
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} sound {} delay {} source {}", tick(), event.getSoundId(),
				event.getDelay(), actorName(event.getSource()));
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} area sound {} at scene {},{} range {} delay {} source {}",
				tick(), event.getSoundId(), event.getSceneX(), event.getSceneY(), event.getRange(),
				event.getDelay(), actorName(event.getSource()));
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (capturing())
		{
			logObject("game object spawned", event.getGameObject());
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (capturing())
		{
			logObject("game object despawned", event.getGameObject());
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (capturing())
		{
			logObject("ground object spawned", event.getGroundObject());
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		if (capturing())
		{
			logObject("ground object despawned", event.getGroundObject());
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} graphics object {} at {}", tick(),
				event.getGraphicsObject().getId(), event.getGraphicsObject().getLocation());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} clicked '{}' on '{}' action {} id {} param0 {} param1 {} item {}",
				tick(), event.getMenuOption(), event.getMenuTarget(), event.getMenuAction(),
				event.getId(), event.getParam0(), event.getParam1(), event.getItemId());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} interface {} loaded", tick(), event.getGroupId());
			pendingDumps.put(event.getGroupId(), tick());
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} interface {} closed (modal {}, unload {})", tick(),
				event.getGroupId(), event.getModalMode(), event.isUnload());
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (capturing())
		{
			scripts.merge(event.getScriptId(), 1, Integer::sum);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (capturing())
		{
			log.debug("[diag] tick {} varp {} varbit {} -> {}", tick(), event.getVarpId(),
				event.getVarbitId(), event.getValue());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();

		// Both piles whenever a run is going or just ended, whatever the moment: whether the game
		// sends them without the player investigating is one of the things being found out.
		boolean pile = id == InventoryID.DOM_LOOTPILE || id == InventoryID.DOM_LOOTPILE_DURING;

		if (pile ? config.debugLogging() && (runActive.getAsBoolean()
			|| tick() - runEndedTick <= AFTER_RUN_TICKS) : capturing())
		{
			log.debug("[diag] tick {} container {} changed: {}", tick(), id,
				items(event.getItemContainer()));
		}
	}

	private void logObject(String what, TileObject object)
	{
		int id = object.getId();
		ObjectComposition definition = client.getObjectDefinition(id);
		String name = definition == null ? "?" : definition.getName();
		String impostor = "";

		if (definition != null && definition.getImpostorIds() != null)
		{
			ObjectComposition current = definition.getImpostor();
			impostor = " impostor " + (current == null ? "none" : current.getId() + " '"
				+ current.getName() + "'") + " of " + Arrays.toString(definition.getImpostorIds());
		}

		String extra = "";

		if (object instanceof GameObject)
		{
			GameObject game = (GameObject) object;
			extra = " size " + game.sizeX() + "x" + game.sizeY();
		}
		else if (object instanceof GroundObject)
		{
			extra = " (ground)";
		}

		log.debug("[diag] tick {} {} {} '{}' at {}{}{}", tick(), what, id, name,
			object.getWorldLocation(), extra, impostor);
	}

	/** Logs every text, item and sprite in an interface with its component. */
	private void dumpInterface(int group)
	{
		Widget[] roots = client.getWidgetRoots();

		if (roots == null)
		{
			return;
		}

		for (Widget root : roots)
		{
			dumpWidget(root, group, 0);
		}
	}

	private void dumpWidget(Widget widget, int group, int depth)
	{
		if (widget == null || widget.isHidden() || depth > 64)
		{
			return;
		}

		if (WidgetUtil.componentToInterface(widget.getId()) == group)
		{
			String text = widget.getText();
			boolean hasText = text != null && !text.isEmpty();
			boolean hasItem = widget.getItemId() > 0;

			if (hasText || hasItem || widget.getModelId() > 0 || widget.getSpriteId() > 0)
			{
				log.debug("[diag] tick {} interface {} component {} index {} text '{}' item {} x{}"
						+ " model {} sprite {} actions {}", tick(), group,
					WidgetUtil.componentToId(widget.getId()), widget.getIndex(), hasText ? text : "",
					widget.getItemId(), widget.getItemQuantity(), widget.getModelId(),
					widget.getSpriteId(), widget.getActions() == null ? "[]"
						: Arrays.toString(widget.getActions()));
			}
		}

		dumpChildren(widget.getStaticChildren(), group, depth);
		dumpChildren(widget.getDynamicChildren(), group, depth);
		dumpChildren(widget.getNestedChildren(), group, depth);
	}

	private void dumpChildren(Widget[] children, int group, int depth)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			dumpWidget(child, group, depth + 1);
		}
	}

	private static String items(ItemContainer container)
	{
		if (container == null)
		{
			return "(none)";
		}

		StringBuilder out = new StringBuilder("[");

		for (Item item : container.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}

			if (out.length() > 1)
			{
				out.append(", ");
			}

			out.append(item.getId()).append(" x").append(item.getQuantity());
		}

		return out.append(']').toString();
	}

	private static String actorName(Actor actor)
	{
		return actor == null ? "none" : actor.getName();
	}
}
