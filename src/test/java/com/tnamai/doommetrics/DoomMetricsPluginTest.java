package com.tnamai.doommetrics;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DoomMetricsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(DoomMetricsPlugin.class);
		RuneLite.main(args);
	}
}
