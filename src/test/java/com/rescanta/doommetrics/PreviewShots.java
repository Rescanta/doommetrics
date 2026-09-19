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

	/** A run detail window too short for its sidebar, so the scrollbar is up. */
	private static final int SHORT_WINDOW = 470;

	/** How many squares a row of the figure grid holds, keeping it about as wide as it is tall. */
	private static final int GRID_COLUMNS = 5;

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
			written.add(shot(infoBoxes(scene, PreviewRender.Backdrop.CAVE),
				directory.resolve(prefix + "-infobox.png")));
			written.add(shot(panel(scene), directory.resolve(prefix + "-panel.png")));
			written.add(shot(detail(scene), directory.resolve(prefix + "-detail.png")));
			written.add(shot(chat(scene, PreviewRender.Chatbox.TRANSPARENT,
				PreviewRender.Backdrop.CAVE), directory.resolve(prefix + "-chat.png")));

			index.append(prefix).append(" - ").append(scene.note).append(System.lineSeparator());
		}

		// One state against the two backdrops that are hardest on a translucent panel, since what
		// the overlay is drawn over is not something the plugin gets to choose.
		PreviewScene deep = scenes.get(2);
		written.add(shot(overlay(deep, PreviewRender.Backdrop.STONE),
			directory.resolve("backdrop-stone.png")));
		written.add(shot(overlay(deep, PreviewRender.Backdrop.GLARE),
			directory.resolve("backdrop-glare.png")));

		// The combat table's other tab, which nothing but a click reaches and so no scene above
		// shows. Worth its own picture for the tab strip and for the meters, which are filled
		// against the largest figure in the tally on show rather than across both.
		written.add(shot(panel(deep, true), directory.resolve("combat-lifetime.png")));

		// The run detail window's other way of reading the counters, which a click reaches and no
		// scene above shows: five lines and five rows instead of eight, and the only view in the
		// plugin where the sources with no counter of their own are in a figure.
		written.add(shot(detail(deep, true), directory.resolve("detail-grouped.png")));

		// The sidebar at a height too short to hold it, both ways round: the one state a scrollbar
		// is up in, and the pair that has to lay the rows out identically whether it is or not.
		written.add(shot(detail(deep, false, SHORT_WINDOW),
			directory.resolve("detail-short.png")));
		written.add(shot(detail(deep, true, SHORT_WINDOW),
			directory.resolve("detail-short-grouped.png")));

		// The counters drawn as icons, separate and combined, and the squares that take one.
		PreviewConfig iconic = new PreviewConfig();
		iconic.adopt(deep.config);
		iconic.counterIcons = true;
		written.add(shot(overlay(deep, iconic, PreviewRender.Backdrop.CAVE),
			directory.resolve("icons-overlay.png")));
		iconic.grouping = MetricDisplay.COMBINED;
		written.add(shot(overlay(deep, iconic, PreviewRender.Backdrop.CAVE),
			directory.resolve("icons-overlay-combined.png")));
		iconic.grouping = deep.config.grouping;
		written.add(shot(infoBoxes(deep, iconic, PreviewRender.Backdrop.CAVE),
			directory.resolve("icons-infobox.png")));

		// The same for chat: the opaque box, and the transparent one over the brightest floor,
		// which is the hardest place for its white words to be read.
		written.add(shot(chat(deep, PreviewRender.Chatbox.OPAQUE, PreviewRender.Backdrop.CAVE),
			directory.resolve("chat-opaque.png")));
		written.add(shot(chat(deep, PreviewRender.Chatbox.TRANSPARENT, PreviewRender.Backdrop.GLARE),
			directory.resolve("chat-glare.png")));

		Files.createDirectories(directory);
		Files.write(directory.resolve("index.txt"),
			index.toString().getBytes(StandardCharsets.UTF_8));

		return written;
	}

	private static BufferedImage overlay(PreviewScene scene, PreviewRender.Backdrop backdrop)
	{
		return overlay(scene, scene.config, backdrop);
	}

	private static BufferedImage overlay(PreviewScene scene, PreviewConfig config,
		PreviewRender.Backdrop backdrop)
	{
		PreviewPlugin plugin = new PreviewPlugin();
		plugin.run = scene.run;

		BufferedImage drawn = PreviewRender.overlay(new DoomMetricsOverlay(plugin, config));

		return PreviewRender.scale(PreviewRender.against(drawn, backdrop, 8), ZOOM);
	}

	/**
	 * Every figure the square can hold, for one scene, in one picture.
	 *
	 * <p>All of them rather than the one the scene has picked, because the square only ever shows
	 * one and the thing worth judging is the set: whether every figure is legible at that size,
	 * and whether the colours tell the units apart when the labels are not there to.
	 */
	private static BufferedImage infoBoxes(PreviewScene scene, PreviewRender.Backdrop backdrop)
	{
		return infoBoxes(scene, scene.config, backdrop);
	}

	private static BufferedImage infoBoxes(PreviewScene scene, PreviewConfig settings,
		PreviewRender.Backdrop backdrop)
	{
		PreviewPlugin plugin = new PreviewPlugin();
		plugin.run = scene.run;

		PreviewConfig config = new PreviewConfig();
		config.adopt(settings);
		config.displayStyle = DisplayStyle.INFOBOX;

		DoomMetricsInfoBox box = new DoomMetricsInfoBox(PreviewRender.icon(), plugin, config);
		List<BufferedImage> cells = new ArrayList<>();
		List<String> labels = new ArrayList<>();

		for (InfoBoxFigure figure : InfoBoxFigure.values())
		{
			config.infoboxFigure = figure;
			cells.add(PreviewRender.infoBox(box));
			labels.add(figure.toString());
		}

		return PreviewRender.scale(PreviewRender.grid(cells, labels, GRID_COLUMNS, backdrop), ZOOM);
	}

	private static BufferedImage panel(PreviewScene scene)
	{
		return panel(scene, false);
	}

	private static BufferedImage panel(PreviewScene scene, boolean lifetimeCombat)
	{
		DoomMetricsPanel panel = new DoomMetricsPanel(() ->
		{
		}, () ->
		{
		});
		panel.setIcons(PreviewIcons.INSTANCE);
		panel.showLifetime(lifetimeCombat);

		panel.setLive(scene.live(scene.config));
		panel.setStats(scene.stats);
		panel.setCombat(scene.panelCombat(), scene.lifetime);
		panel.setRows(scene.rows);

		return PreviewRender.scale(PreviewRender.component(panel), ZOOM);
	}

	/** Every line the scene's run would post to chat, as that chatbox shows them. */
	private static BufferedImage chat(PreviewScene scene, PreviewRender.Chatbox box,
		PreviewRender.Backdrop backdrop)
	{
		BufferedImage drawn = PreviewRender.chat(scene.chat(scene.config), box, backdrop);

		return PreviewRender.scale(drawn == null
			? PreviewRender.against(null, backdrop, 8, "(nothing posted to chat)")
			: drawn, ZOOM);
	}

	private static BufferedImage detail(PreviewScene scene)
	{
		return detail(scene, false);
	}

	private static BufferedImage detail(PreviewScene scene, boolean grouped)
	{
		return detail(scene, grouped, 600);
	}

	/** The run detail window at the size it opens at. Left unscaled: it is large enough to read. */
	private static BufferedImage detail(PreviewScene scene, boolean grouped, int height)
	{
		RunDetailWindow window = new RunDetailWindow(null, () ->
		{
		});
		window.setIcons(PreviewIcons.INSTANCE);
		window.setHideEmpty(scene.config.hideEmptyCounters);
		window.showGrouped(grouped);

		window.setLive(scene.live(scene.config));
		window.setDetail(scene.detail());

		return PreviewRender.window(window, 980, height);
	}

	private static String shot(BufferedImage image, Path file) throws IOException
	{
		PreviewRender.write(image, file);
		return file + " (" + image.getWidth() + "x" + image.getHeight() + ")";
	}
}
