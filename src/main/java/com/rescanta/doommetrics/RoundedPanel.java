package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * A panel filled as a rounded rectangle, for cards, tiles and pills. Its children keep their own
 * backgrounds, so they should sit inside its padding. Swing thread only.
 */
class RoundedPanel extends JPanel
{
	private Color fill;
	private final int arc;

	RoundedPanel(Color fill, int arc)
	{
		this.fill = fill;
		this.arc = arc;
		setOpaque(false);
		setBackground(fill);
	}

	/** @param fill the colour inside the corners, or null for none */
	void setFill(Color fill)
	{
		this.fill = fill;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D graphics = (Graphics2D) g.create();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (fill != null)
		{
			graphics.setColor(fill);
			graphics.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
		}

		graphics.dispose();
	}
}
