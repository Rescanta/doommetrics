package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The pictures drawn in place of names, taken from the game: an item's inventory sprite, or an
 * interface sprite for a spell or a skill.
 *
 * <p>None of them is there the moment it is asked for. The game draws an item's sprite on the
 * client thread, and an interface sprite is only readable once the client has loaded, so each
 * picture is asked for once, arrives later, and is kept. Until it arrives the answer is null and
 * the name is drawn instead; when it does, {@code onArrived} is told, so the side panel and the
 * detail window can put it in. The overlay and the infobox simply find it there on their next
 * frame.
 *
 * <p>Safe from any thread. The overlay and the infobox read these on the client thread and the
 * panels on the Swing thread, and a picture arrives on whichever thread the game hands it over on.
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

	/**
	 * Asks for every counter's picture and each of {@code itemIds}, so they are in hand before
	 * anything goes looking for them.
	 */
	void preload(Collection<Integer> itemIds)
	{
		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			counter(metric);
		}

		for (int itemId : itemIds)
		{
			askItem(itemId);
		}
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
	public BufferedImage item(int itemId)
	{
		askItem(itemId);
		return items.get(itemId);
	}

	@Override
	public BufferedImage smallItem(int itemId)
	{
		askItem(itemId);
		return smallItems.get(itemId);
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
		if (!askedSprites.add(spriteId))
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
