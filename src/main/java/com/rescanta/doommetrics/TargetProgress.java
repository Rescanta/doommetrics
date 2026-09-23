package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * The target row as a meter: its name and {@code cleared / target} over a bar that fills towards
 * the target and turns green once it is reached. Shared by the side panel and the detail window.
 * Swing thread only.
 */
class TargetProgress extends JPanel
{
	private final JLabel caption = PanelStyle.caption("Target", SwingConstants.LEFT);
	private final JLabel value = PanelStyle.body("", SwingConstants.RIGHT);
	private final Meter meter;

	/** @param height how thick the bar is */
	TargetProgress(int height)
	{
		super(new BorderLayout(0, 3));
		meter = new Meter(PanelStyle.ACCENT, height);

		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.add(caption, BorderLayout.WEST);
		line.add(value, BorderLayout.EAST);

		setOpaque(false);
		setToolTipText("Delves cleared towards the target delve");
		add(line, BorderLayout.NORTH);
		add(meter, BorderLayout.CENTER);
	}

	/** @param live a run with its target row on */
	void show(DoomMetricsPanel.Live live)
	{
		int row = DoomMetricsPanel.Live.TARGET_ROW;
		boolean reached = live.progress() >= 1;

		caption.setText(live.labels[row]);
		value.setText(reached ? live.values[row] : live.cleared + " / " + live.values[row]);
		meter.setColor(reached ? DoomColors.LIVE : PanelStyle.ACCENT);
		meter.setFill(live.progress());
	}
}
