package com.rescanta.doommetrics;

/**
 * The plugin as far as the overlay is concerned: something with a run to draw.
 *
 * <p>The overlay asks its plugin for one thing, so the preview harness supplies exactly that and
 * leaves the rest of the plugin - the client, the config manager, the executor - unbuilt. Nothing
 * here starts up, so none of the injected fields are ever touched.
 */
class PreviewPlugin extends DoomMetricsPlugin
{
	DelveRun run;

	@Override
	DelveRun getDisplayRun()
	{
		return run;
	}
}
