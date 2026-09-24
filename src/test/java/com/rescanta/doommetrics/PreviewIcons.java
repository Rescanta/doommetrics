package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.util.ImageUtil;

/**
 * The pictures the plugin takes from the game, for the preview harness to draw with.
 *
 * <p>The harness has no game. These are the same inventory and interface sprites as the Old School
 * RuneScape Wiki shows them, kept with the tests so the harness draws what the client would rather
 * than a box with a letter in it. They are test resources, so none of them ship with the plugin.
 */
final class PreviewIcons implements Icons
{
	private static final Map<Integer, String> ITEMS = new HashMap<>();
	private static final Map<Integer, String> SPRITES = new HashMap<>();

	static
	{
		ITEMS.put(ItemID.EYE_OF_AYAK, "eye_of_ayak");
		ITEMS.put(ItemID.EYE_OF_AYAK_UNCHARGED, "eye_of_ayak_uncharged");
		ITEMS.put(ItemID.AVERNIC_TREADS, "avernic_treads");
		ITEMS.put(ItemID.MOKHAIOTL_CLOTH, "mokhaiotl_cloth");
		ITEMS.put(ItemID.DOMPET, "dom");

		ITEMS.put(ItemID.ANCIENT_GODSWORD, "ancient_godsword");
		ITEMS.put(ItemID.TOXIC_BLOWPIPE, "toxic_blowpipe");
		ITEMS.put(ItemID.SGS, "saradomin_godsword");
		ITEMS.put(ItemID.NIGHTMARE_STAFF_ELDRITCH, "eldritch_nightmare_staff");
		ITEMS.put(ItemID.ZARYTE_XBOW, "zaryte_crossbow");
		ITEMS.put(ItemID.SCYTHE_OF_VITUR, "scythe_of_vitur");
		ITEMS.put(ItemID.NOXIOUS_HALBERD, "noxious_halberd");
		ITEMS.put(ItemID.CRYSTAL_HALBERD, "crystal_halberd");

		SPRITES.put(SpriteID.Magicon2.BLOOD_BARRAGE, "blood_barrage");
		SPRITES.put(SpriteID.Staticons.HITPOINTS, "hitpoints");
		SPRITES.put(SpriteID.Staticons.PRAYER, "prayer");
		SPRITES.put(SpriteID.Staticons.STRENGTH, "strength");
		SPRITES.put(IconArt.BOSS, "doom_boss");
	}

	/** Built after the tables above, which it loads from. */
	static final PreviewIcons INSTANCE = new PreviewIcons();

	private final Map<String, BufferedImage> full = new HashMap<>();
	private final Map<String, BufferedImage> small = new HashMap<>();

	private PreviewIcons()
	{
		for (String file : ITEMS.values())
		{
			load(file);
		}

		for (String file : SPRITES.values())
		{
			load(file);
		}
	}

	private void load(String file)
	{
		BufferedImage image =
			ImageUtil.loadImageResource(PreviewIcons.class, "sprites/" + file + ".png");
		full.put(file, image);
		small.put(file, IconArt.shrink(image, IconArt.SMALL));
	}

	@Override
	public BufferedImage counter(CombatMetric metric)
	{
		return full.get(fileFor(metric));
	}

	@Override
	public BufferedImage smallCounter(CombatMetric metric)
	{
		return small.get(fileFor(metric));
	}

	@Override
	public BufferedImage item(int itemId)
	{
		return full.get(ITEMS.get(itemId));
	}

	@Override
	public BufferedImage smallItem(int itemId)
	{
		return small.get(ITEMS.get(itemId));
	}

	@Override
	public BufferedImage sprite(int spriteId)
	{
		return full.get(SPRITES.get(spriteId));
	}

	@Override
	public BufferedImage smallSprite(int spriteId)
	{
		return small.get(SPRITES.get(spriteId));
	}

	/** The file a counter's picture is kept in, or null for a counter the harness has none for. */
	static String fileFor(CombatMetric metric)
	{
		int itemId = IconArt.itemFor(metric);
		return itemId > 0 ? ITEMS.get(itemId) : SPRITES.get(IconArt.spriteFor(metric));
	}
}
