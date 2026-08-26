package com.tnamai.doommetrics;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class DoomMetricsOverlay extends OverlayPanel
{
	private final DoomMetricsPlugin plugin;
	private final DoomMetricsConfig config;

	@Inject
	private DoomMetricsOverlay(DoomMetricsPlugin plugin, DoomMetricsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG,
			OverlayManager.OPTION_CONFIGURE, "Doom Metrics overlay"));
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,
			"Clear", "Doom Metrics overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		DelveRun run = plugin.getDisplayRun();
		if (run == null)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Doom Metrics")
			.build());

		if (run.isFinished())
		{
			if (run.getEndReason() == EndReason.DIED)
			{
				addLine("Died on", "Delve " + run.getDiedOnLevel());
			}

			if (config.showDelveNumber())
			{
				addLine("Cleared", Integer.toString(run.lastLevel()));
			}
		}
		else if (config.showDelveNumber())
		{
			addLine("Delve", Integer.toString(run.currentLevel()));
		}

		if (config.showRunTimer())
		{
			// The asterisk marks a run we joined part way through, whose start time is a guess.
			addLine(run.isPartial() ? "Run*" : "Run",
				DoomFormat.duration(run.displayElapsed(Instant.now())));
		}

		if (config.showPace())
		{
			PaceMode mode = config.paceMode();
			addLine(mode.toString(), DoomFormat.pace(
				run.pace(mode, config.deepDelveLevel(), config.paceAverageFromLevel())));
		}

		return super.render(graphics);
	}

	private void addLine(String left, String right)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.build());
	}
}
