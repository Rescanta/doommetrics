package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * The run as one square: a picture, one figure over it, and a tooltip for everything the figure
 * left out.
 *
 * <p>Which figure is the config's to say, and it can be any of the sixteen in
 * {@link InfoBoxFigure}, counters included. The counter checkboxes have no say here - they choose
 * which lines the panel draws, and a square has one line.
 *
 * <p>Added once at startup and taken down at shutdown rather than added and removed as runs come
 * and go: whether the square is on screen is answered by {@link #render()}, which the client asks
 * every frame anyway. That keeps the infobox out of the tick handlers entirely, and keeps its
 * place in the infobox bar from moving every time a run starts.
 */
class DoomMetricsInfoBox extends InfoBox
{
	private final DoomMetricsPlugin plugin;
	private final DoomMetricsConfig config;

	DoomMetricsInfoBox(BufferedImage image, DoomMetricsPlugin plugin, DoomMetricsConfig config)
	{
		super(image, plugin);
		this.plugin = plugin;
		this.config = config;

		// The panel's own Clear sits on the overlay, which is not drawn in this mode, so without
		// this there would be no way to dismiss a finished run before its linger minutes are up.
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_INFOBOX,
			DoomMetricsPlugin.CLEAR_OPTION, "Doom Metrics"));
	}

	@Override
	public boolean render()
	{
		return config.displayStyle() == DisplayStyle.INFOBOX && plugin.getDisplayRun() != null;
	}

	@Override
	public String getText()
	{
		DelveRun run = plugin.getDisplayRun();
		return run == null ? "" : config.infoboxFigure().text(run, config, Instant.now());
	}

	@Override
	public Color getTextColor()
	{
		DelveRun run = plugin.getDisplayRun();
		return run == null
			? DoomColors.PLAIN
			: config.infoboxFigure().color(run, config, Instant.now());
	}

	@Override
	public String getTooltip()
	{
		DelveRun run = plugin.getDisplayRun();

		return run == null
			? null
			: config.infoboxFigure().tooltip(run, config, Instant.now());
	}
}
