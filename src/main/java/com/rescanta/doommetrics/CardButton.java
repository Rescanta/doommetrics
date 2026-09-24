package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** A raised grey button with the game's orange on it, yellow under the pointer. Swing thread only. */
class CardButton extends JButton
{
	private static final Color FACE = new Color(52, 52, 52);
	private static final Color FACE_HOVER = new Color(62, 62, 62);

	/** The lit top edge and the shaded bottom one, which are what make it read as raised. */
	private static final Color LIGHT = new Color(74, 74, 74);
	private static final Color SHADE = new Color(22, 22, 22);

	CardButton(String text, String tooltip, Runnable onPress)
	{
		super(text);

		setFont(FontManager.getRunescapeBoldFont());
		setForeground(DoomColors.ORANGE);
		setBorder(new EmptyBorder(7, 8, 7, 8));
		setContentAreaFilled(false);
		setBorderPainted(false);
		setFocusPainted(false);
		setOpaque(false);
		setRolloverEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText(tooltip);
		addActionListener(event -> onPress.run());

		// The words light up to yellow under the pointer, as the game's own buttons do.
		getModel().addChangeListener(event ->
			setForeground(getModel().isRollover() ? DoomColors.YELLOW : DoomColors.ORANGE));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D graphics = (Graphics2D) g.create();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int width = getWidth();
		int height = getHeight();
		boolean pressed = getModel().isPressed();

		// The shade under the face, then the light over it, then the face; pressed swaps the two
		// edges so the button looks pushed in.
		graphics.setColor(pressed ? LIGHT : SHADE);
		graphics.fillRoundRect(0, 0, width, height, PanelStyle.ARC, PanelStyle.ARC);
		graphics.setColor(pressed ? SHADE : LIGHT);
		graphics.fillRoundRect(0, 0, width, height - 1, PanelStyle.ARC, PanelStyle.ARC);
		graphics.setColor(getModel().isRollover() ? FACE_HOVER : FACE);
		graphics.fillRoundRect(0, 1, width, height - 2, PanelStyle.ARC, PanelStyle.ARC);

		graphics.dispose();
		super.paintComponent(g);
	}
}
