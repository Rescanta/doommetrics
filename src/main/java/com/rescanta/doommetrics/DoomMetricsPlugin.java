package com.rescanta.doommetrics;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Doom of Mokhaiotl Metrics",
	description = "Times each delve, shows your deep delve rate, and keeps a lifetime record of every run",
	tags = {"doom", "mokhaiotl", "delve", "timer", "pace", "pvm", "metrics", "history", "stats"}
)
public class DoomMetricsPlugin extends Plugin
{
	@Inject
	private DoomMetricsConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Doom of Mokhaiotl Metrics started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Doom of Mokhaiotl Metrics stopped");
	}

	@Provides
	DoomMetricsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoomMetricsConfig.class);
	}
}
