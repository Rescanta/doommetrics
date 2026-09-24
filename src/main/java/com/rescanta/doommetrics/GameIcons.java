package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Item and interface sprites from the game, requested once and cached as they arrive; null until
 * then, and {@code onArrived} is told. Safe from any thread.
 */
final class GameIcons implements Icons
{
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final Runnable onArrived;

	/** What has been asked for, so a picture on its way is never asked for twice. */
	private final Set<Integer> askedItems = ConcurrentHashMap.newKeySet();
	private final Set<Integer> askedSprites = ConcurrentHashMap.newKeySet();

	private final Map<Integer, BufferedImage> items = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> smallItems = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> sprites = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> smallSprites = new ConcurrentHashMap<>();

	/** @param onArrived told whenever a picture arrives, on whichever thread it arrived on */
	GameIcons(ItemManager itemManager, SpriteManager spriteManager, Runnable onArrived)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.onArrived = onArrived;
	}

	/** Asks for every counter's picture up front. */
	void preload()
	{
		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			counter(metric);
		}

		for (CombatMetric.Unit unit : CombatMetric.Unit.values())
		{
			askSprite(IconArt.spriteFor(unit));
		}

		askSprite(IconArt.BOSS);
	}

	@Override
	public BufferedImage counter(CombatMetric metric)
	{
		return picture(metric, items, sprites);
	}

	@Override
	public BufferedImage smallCounter(CombatMetric metric)
	{
		return picture(metric, smallItems, smallSprites);
	}

	@Override
	public BufferedImage sprite(int spriteId)
	{
		askSprite(spriteId);
		return sprites.get(spriteId);
	}

	@Override
	public BufferedImage smallSprite(int spriteId)
	{
		askSprite(spriteId);
		return smallSprites.get(spriteId);
	}

	private BufferedImage picture(CombatMetric metric, Map<Integer, BufferedImage> byItem,
		Map<Integer, BufferedImage> bySprite)
	{
		int itemId = IconArt.itemFor(metric);

		if (itemId > 0)
		{
			askItem(itemId);
			return byItem.get(itemId);
		}

		int spriteId = IconArt.spriteFor(metric);

		if (spriteId >= 0)
		{
			askSprite(spriteId);
			return bySprite.get(spriteId);
		}

		return null;
	}

	private void askItem(int itemId)
	{
		if (itemId <= 0 || !askedItems.add(itemId))
		{
			return;
		}

		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> arrived(items, smallItems, itemId, image));
	}

	private void askSprite(int spriteId)
	{
		if (spriteId < 0 || !askedSprites.add(spriteId))
		{
			return;
		}

		spriteManager.getSpriteAsync(spriteId, 0, image ->
		{
			if (image != null)
			{
				arrived(sprites, smallSprites, spriteId, image);
			}
		});
	}

	private void arrived(Map<Integer, BufferedImage> full, Map<Integer, BufferedImage> small,
		int id, BufferedImage image)
	{
		BufferedImage shrunk = IconArt.shrink(image, IconArt.SMALL);

		// The small one first, so a reader that finds the full one can count on the other too.
		if (shrunk != null)
		{
			small.put(id, shrunk);
		}

		full.put(id, image);
		onArrived.run();
	}
}
