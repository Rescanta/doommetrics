package com.rescanta.doommetrics;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * One overlay line cut into equal columns, for {@link CounterStyle#ICON_GRID}. RuneLite's
 * {@code SplitComponent} gives its first child the whole width. A short row leaves columns empty.
 */
final class OverlayColumns implements LayoutableRenderableEntity
{
	private final List<LayoutableRenderableEntity> cells;
	private final int columns;
	private final int gap;

	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension();

	/**
	 * @param cells   what goes in each column, left to right: no more than {@code columns}
	 * @param columns how many columns the width is cut into
	 * @param gap     the space between two columns
	 */
	OverlayColumns(List<LayoutableRenderableEntity> cells, int columns, int gap)
	{
		this.cells = cells;
		this.columns = columns;
		this.gap = gap;
	}

	/** How wide each column is on a line {@code width} wide. */
	static int columnWidth(int width, int columns, int gap)
	{
		return (width - gap * (columns - 1)) / columns;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int width = columnWidth(preferredSize.width, columns, gap);
		int height = 0;

		graphics.translate(preferredLocation.x, preferredLocation.y);

		for (int i = 0; i < cells.size(); i++)
		{
			LayoutableRenderableEntity cell = cells.get(i);
			cell.setPreferredLocation(new Point(i * (width + gap), 0));
			cell.setPreferredSize(new Dimension(width, 0));
			height = Math.max(height, cell.render(graphics).height);
		}

		graphics.translate(-preferredLocation.x, -preferredLocation.y);

		bounds.setLocation(preferredLocation);
		bounds.setSize(preferredSize.width, height);
		return new Dimension(preferredSize.width, height);
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		preferredLocation = position;
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		preferredSize = dimension;
	}
}
