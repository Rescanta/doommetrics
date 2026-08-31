package com.rescanta.doommetrics;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * Writes a picture of every interface state to {@code build/preview}, one pass, no window shown.
 *
 * <p>Run it with {@code gradlew previewShots}. What the preview window is for looking at, this is
 * for keeping: run it before a change and after one and the two directories are a before and an
 * after of every state at once, including the ones you would not have thought to open.
 *
 * <p>The scenes are fixed and their history is seeded, so the only thing that moves between two
 * runs is what the drawing code does - bar the run clocks, which are built from the moment the
 * harness started and tick on regardless.
 */
public class PreviewShots
{
	/** How much the small game font is blown up by, so a saved image can be read at all. */
	private static final int ZOOM = 2;

	public static void main(String[] args) throws Exception
	{
		PreviewRender.requireDisplay();
		RuneLiteLAF.setup();

		Path directory = Paths.get(args.length > 0 ? args[0] : "build/preview");
		List<String> written = new ArrayList<>();

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					written.addAll(writeAll(directory));
				}
				catch (IOException e)
				{
					throw new IllegalStateException(e);
				}
			});
		}
		catch (InvocationTargetException e)
		{
			throw new IllegalStateException("Could not write the preview images", e.getCause());
		}

		for (String line : written)
		{
			System.out.println(line);
		}

		System.out.println(written.size() + " images in " + directory.toAbsolutePath());

		// Building the windows started the event thread, and nothing here will end it.
		System.exit(0);
	}

	/**
	 * Writes every scene into {@code directory} and returns it. Must be called on the Swing thread,
	 * since it lays widgets out.
	 */
	static Path write(Path directory) throws IOException
	{
		writeAll(directory);
		return directory;
	}

	private static List<String> writeAll(Path directory) throws IOException
	{
		List<String> written = new ArrayList<>();
		List<PreviewScene> scenes = PreviewScene.all();
		StringBuilder index = new StringBuilder();

		for (int i = 0; i < scenes.size(); i++)
		{
			PreviewScene scene = scenes.get(i);
			String prefix = String.format("%02d-%s", i + 1, scene.name);

			written.add(shot(overlay(scene, PreviewRender.Backdrop.CAVE),
				directory.resolve(prefix + "-overlay.png")));
			written.add(shot(panel(scene), directory.resolve(prefix + "-panel.png")));
			written.add(shot(history(scene), directory.resolve(prefix + "-history.png")));

			index.append(prefix).append(" - ").append(scene.note).append(System.lineSeparator());
		}

		// One state against the two backdrops that are hardest on a translucent panel, since what
		// the overlay is drawn over is not something the plugin gets to choose.
		PreviewScene deep = scenes.get(2);
		written.add(shot(overlay(deep, PreviewRender.Backdrop.STONE),
			directory.resolve("backdrop-stone.png")));
		written.add(shot(overlay(deep, PreviewRender.Backdrop.GLARE),
			directory.resolve("backdrop-glare.png")));

		Files.createDirectories(directory);
		Files.write(directory.resolve("index.txt"),
			index.toString().getBytes(StandardCharsets.UTF_8));

		return written;
	}

	private static BufferedImage overlay(PreviewScene scene, PreviewRender.Backdrop backdrop)
	{
		PreviewPlugin plugin = new PreviewPlugin();
		plugin.run = scene.run;

		BufferedImage drawn = PreviewRender.overlay(
			new DoomMetricsOverlay(plugin, scene.config));

		return PreviewRender.scale(PreviewRender.against(drawn, backdrop, 8), ZOOM);
	}

	private static BufferedImage panel(PreviewScene scene)
	{
		DoomMetricsPanel panel = new DoomMetricsPanel(() ->
		{
		});

		panel.setLive(scene.live(scene.config));
		panel.setStats(scene.stats);
		panel.setCombat(scene.panelCombat());
		panel.setRows(scene.rows);

		return PreviewRender.scale(PreviewRender.component(panel), ZOOM);
	}

	/** The history window at the size it opens at. Left unscaled: it is large enough to read. */
	private static BufferedImage history(PreviewScene scene)
	{
		HistoryWindow window = new HistoryWindow(null, () ->
		{
		});

		window.setRows(scene.rows);
		window.setLifetimeCombat(scene.lifetime);
		window.setSeries(scene.series);

		return PreviewRender.window(window, 900, 560);
	}

	private static String shot(BufferedImage image, Path file) throws IOException
	{
		PreviewRender.write(image, file);
		return file + " (" + image.getWidth() + "x" + image.getHeight() + ")";
	}
}
