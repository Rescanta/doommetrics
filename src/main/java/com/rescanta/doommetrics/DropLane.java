package com.rescanta.doommetrics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Constants;
import net.runelite.client.ui.ColorScheme;

/**
 * The lane of drop icons over {@link DelveChart}'s counters, each over the delve it came off and
 * faded if the run lost it. Swing thread only.
 */
final class DropLane
{
	private static final int ICON_WIDTH = Constants.ITEM_SPRITE_WIDTH;
	private static final int ICON_HEIGHT = Constants.ITEM_SPRITE_HEIGHT;

	private static final int ICON_GAP = 3;

	/** Past this many rows, icons overlap in the top row. */
	private static final int MAX_ICON_ROWS = 3;

	private static final float LOST_ALPHA = 0.35f;

	private static final Color DROP_TICK = new Color(0xFF, 0xFF, 0xFF, 50);

	private static final Color STACK_COLOR = new Color(0xFF, 0xFF, 0x00);

	private static final Color LABEL_COLOR = ColorScheme.LIGHT_GRAY_COLOR;

	private static final BasicStroke HAIRLINE = new BasicStroke(1f);

	private static final BufferedImage UNKNOWN_ICON = IconArt.unknownUnique(ICON_WIDTH, ICON_HEIGHT);

	private IntFunction<BufferedImage> itemIcons = itemId -> null;

	/** The drops as last laid out, and where each icon went. */
	private List<RunDetail.Drop> drops = Collections.emptyList();
	private Rectangle[] bounds = new Rectangle[0];

	private int rows;

	/** Item icons, or null while there is none. Called on every paint. */
	void setItemIcons(IntFunction<BufferedImage> itemIcons)
	{
		this.itemIcons = itemIcons;
	}

	void clear()
	{
		drops = Collections.emptyList();
		bounds = new Rectangle[0];
		rows = 0;
	}

	/** The room the lane takes: none at all for a run without a drop. */
	int height()
	{
		return rows * (ICON_HEIGHT + ICON_GAP);
	}

	/**
	 * Places every drop's icon over its delve, stacking those too close together.
	 *
	 * @param xFor  where a delve sits along the chart
	 * @param width the chart's width, which icons are kept inside
	 * @param top   where the lane starts; the plot starts {@link #height()} below it
	 */
	void layout(List<RunDetail.Drop> drops, IntUnaryOperator xFor, int width, int top)
	{
		this.drops = drops;
		int[] lefts = new int[drops.size()];

		for (int i = 0; i < drops.size(); i++)
		{
			// Kept inside the component so an edge drop isn't cut in half.
			int centred = xFor.applyAsInt(drops.get(i).level) - ICON_WIDTH / 2;
			lefts[i] = Math.max(0, Math.min(width - ICON_WIDTH, centred));
		}

		int[] placed = ChartMath.stackRows(lefts, ICON_WIDTH + ICON_GAP, MAX_ICON_ROWS);
		rows = 0;

		for (int row : placed)
		{
			rows = Math.max(rows, row + 1);
		}

		// Row 0 is nearest the plot.
		bounds = new Rectangle[drops.size()];
		int nearest = top + height() - ICON_GAP - ICON_HEIGHT;

		for (int i = 0; i < drops.size(); i++)
		{
			int y = nearest - placed[i] * (ICON_HEIGHT + ICON_GAP);
			bounds[i] = new Rectangle(lefts[i], y, ICON_WIDTH, ICON_HEIGHT);
		}
	}

	/** Every icon, with a hairline down to the plot at {@code plotTop}. */
	void draw(Graphics2D g2, IntUnaryOperator xFor, int plotTop)
	{
		// Hairlines first, so no icon is crossed out by another's line.
		g2.setStroke(HAIRLINE);
		g2.setColor(DROP_TICK);

		for (int i = 0; i < drops.size(); i++)
		{
			Rectangle at = bounds[i];
			int x = xFor.applyAsInt(drops.get(i).level);
			g2.drawLine(x, at.y + at.height, x, plotTop);
		}

		for (int i = 0; i < drops.size(); i++)
		{
			drawDrop(g2, drops.get(i), bounds[i]);
		}
	}

	private void drawDrop(Graphics2D g2, RunDetail.Drop drop, Rectangle at)
	{
		Composite composite = g2.getComposite();

		if (!drop.kept)
		{
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, LOST_ALPHA));
		}

		BufferedImage image = drop.isUnknown() ? UNKNOWN_ICON : itemIcons.apply(drop.itemId);

		if (image != null && image.getWidth() > 0 && image.getHeight() > 0)
		{
			// Never enlarged: sprites are pixel art.
			double scale = Math.min(1, Math.min((double) at.width / image.getWidth(),
				(double) at.height / image.getHeight()));
			int width = (int) Math.round(image.getWidth() * scale);
			int height = (int) Math.round(image.getHeight() * scale);

			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.drawImage(image, at.x + (at.width - width) / 2, at.y + (at.height - height) / 2,
				width, height, null);
		}
		else
		{
			// No icon to draw (no game): a box with the item's initial.
			FontMetrics metrics = g2.getFontMetrics();
			String initial = drop.name.isEmpty() ? "?" : drop.name.substring(0, 1);

			g2.setStroke(HAIRLINE);
			g2.setColor(LABEL_COLOR);
			g2.drawRoundRect(at.x + 2, at.y + 1, at.width - 5, at.height - 3, 6, 6);
			g2.drawString(initial, at.x + (at.width - metrics.stringWidth(initial)) / 2,
				at.y + (at.height + metrics.getAscent()) / 2 - 1);
		}

		if (drop.quantity > 1)
		{
			// Drawn where and how the game draws a stack's count.
			String count = Integer.toString(drop.quantity);
			int baseline = at.y + g2.getFontMetrics().getAscent() - 2;

			g2.setColor(Color.BLACK);
			g2.drawString(count, at.x + 1, baseline + 1);
			g2.setColor(STACK_COLOR);
			g2.drawString(count, at.x, baseline);
		}

		g2.setComposite(composite);
	}

	/** The drop whose icon is under the pointer, or null for none. */
	RunDetail.Drop at(int px, int py)
	{
		// Last drawn is on top.
		for (int i = bounds.length - 1; i >= 0; i--)
		{
			if (bounds[i].contains(px, py))
			{
				return drops.get(i);
			}
		}

		return null;
	}

	/** What a drop's icon says on hover: its name and delve, and how it was lost. */
	static String tooltip(RunDetail.Drop drop, RunDetail detail)
	{
		if (drop.isUnknown())
		{
			return "<html>" + drop.name + " - delve " + drop.level + "<br>"
				+ RunDetail.UNKNOWN_UNIQUE_CANDIDATES
				+ (drop.kept ? "" : "<br>" + detail.lostHow()) + "</html>";
		}

		String name = drop.quantity > 1 ? drop.quantity + " x " + drop.name : drop.name;
		return drop.kept
			? name + " - delve " + drop.level
			: "<html>" + name + " - delve " + drop.level + "<br>" + detail.lostHow() + "</html>";
	}
}
