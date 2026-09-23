package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A rounded button: the main one filled in the accent colour, a lesser one an outline on the
 * background. Swing thread only.
 */
class CardButton extends JButton
{
	/** The accent a shade lighter, for the pointer being over the main button. */
	private static final Color ACCENT_HOVER = new Color(240, 158, 30);

	private final boolean primary;

	/**
	 * @param primary whether this is the button the panel is leading to, drawn filled
	 */
	CardButton(String text, String tooltip, boolean primary, Runnable onPress)
	{
		super(text);
		this.primary = primary;

		setFont(FontManager.getRunescapeBoldFont());
		setForeground(primary ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		setBorder(new EmptyBorder(primary ? 7 : 5, 8, primary ? 7 : 5, 8));
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText(tooltip);
		addActionListener(event -> onPress.run());
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D graphics = (Graphics2D) g.create();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean over = getModel().isRollover();

		if (primary)
		{
			graphics.setColor(over ? ACCENT_HOVER : PanelStyle.ACCENT);
			graphics.fillRoundRect(0, 0, getWidth(), getHeight(), PanelStyle.ARC, PanelStyle.ARC);
		}
		else
		{
			graphics.setColor(over ? ColorScheme.DARKER_GRAY_HOVER_COLOR : PanelStyle.BACKGROUND);
			graphics.fillRoundRect(0, 0, getWidth(), getHeight(), PanelStyle.ARC, PanelStyle.ARC);
			graphics.setColor(PanelStyle.TRACK);
			graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, PanelStyle.ARC,
				PanelStyle.ARC);
		}

		graphics.dispose();
		super.paintComponent(g);
	}
}
