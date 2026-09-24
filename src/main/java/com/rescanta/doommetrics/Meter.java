package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * A slim rounded bar for a figure with a natural range: a counter against the largest in its unit,
 * a run against its target. Painted, so it has no text of its own. Swing thread only.
 */
class Meter extends JComponent
{
	private final int height;
	private Color color;
	private Color track = PanelStyle.TRACK;

	/** From 0 to 1. */
	private double fill;

	Meter(Color color, int height)
	{
		this.color = color;
		this.height = height;
	}

	/** @param fraction how much of the bar is filled, clamped to 0..1 */
	void setFill(double fraction)
	{
		double clamped = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));

		if (clamped != fill)
		{
			fill = clamped;
			repaint();
		}
	}

	void setColor(Color color)
	{
		this.color = color;
		repaint();
	}

	/** @param track the colour of the unfilled part, or null to leave it undrawn */
	void setTrack(Color track)
	{
		this.track = track;
		repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(0, height);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D graphics = (Graphics2D) g.create();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int width = getWidth();
		int y = (getHeight() - height) / 2;

		if (track != null)
		{
			graphics.setColor(track);
			graphics.fillRoundRect(0, y, width, height, height, height);
		}

		// A sliver still shows as a dot, so a small figure is not read as nothing.
		int filled = fill <= 0 ? 0 : Math.max(height, (int) Math.round(width * fill));

		if (filled > 0)
		{
			graphics.setColor(color);
			graphics.fillRoundRect(0, y, filled, height, height, height);
		}

		graphics.dispose();
	}
}
