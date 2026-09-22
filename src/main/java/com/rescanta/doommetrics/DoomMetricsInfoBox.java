package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * The run as one square: a picture, one configured figure, and a tooltip. Added once at start up;
 * {@link #render()} decides when it shows.
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

	/**
	 * The counter's icon when it shows one counter and icons are on, otherwise the plugin's. The client
	 * only rescales it when told, which the plugin does each tick.
	 */
	@Override
	public BufferedImage getImage()
	{
		CombatMetric metric = config.infoboxFigure().metric();

		if (config.counterStyle().icons() && metric != null)
		{
			BufferedImage picture = plugin.getIcons().counter(metric);

			if (picture != null)
			{
				return picture;
			}
		}

		return super.getImage();
	}

	@Override
	public boolean render()
	{
		if (config.displayStyle() != DisplayStyle.INFOBOX)
		{
			return false;
		}

		DelveRun run = plugin.getDisplayRun();
		return run != null && config.infoboxFigure().shown(run, config);
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
