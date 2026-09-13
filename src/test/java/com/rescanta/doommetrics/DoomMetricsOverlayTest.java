package com.rescanta.doommetrics;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the overlay comes out as, measured and drawn rather than described.
 *
 * <p>Every row has to fit the width an overlay starts at, because a row that does not is not drawn
 * narrow - it is drawn wrapped, with the figure laid back over the end of its own label. That is
 * what picks the labels, so it is checked here in the font they are drawn in rather than left to
 * whoever next writes a longer one.
 *
 * <p>None of it needs a screen: an overlay draws into an image and asks nothing of the desktop.
 */
public class DoomMetricsOverlayTest
{
	/** The most a single run can put on one counter - see {@link CombatMetric#overlayLabel()}. */
	private static final long WIDEST_FIGURE = 99_999;

	/**
	 * The most a few seconds can put on one line, drawn as {@code +9,999} while it is on show. A
	 * punish or a spec is a few hundred; this is a heading's worth of them landing together, with
	 * room to spare.
	 */
	private static final long WIDEST_GAIN = 9_999;

	/**
	 * The least space worth leaving between a label and the figure beside it. The tightest row
	 * there is clears it with room to spare.
	 */
	private static final int GAP = 6;

	/** What a row has to fit into: the standard overlay width, less its border on both sides. */
	private static final int ROW_WIDTH =
		ComponentConstants.STANDARD_WIDTH - ComponentConstants.STANDARD_BORDER * 2;

	@Test
	public void everyRowFitsBesideTheWidestFigureItCanReach()
	{
		FontMetrics metrics = metrics();
		String figure = DoomFormat.count(WIDEST_FIGURE);
		String gain = "+" + DoomFormat.count(WIDEST_GAIN);

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			assertFits(metrics, metric.overlayLabel(), figure);
			assertFits(metrics, metric.overlayLabel(), gain);
		}

		// Combined, the counters are drawn under these instead, and reach the same figures.
		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			assertFits(metrics, group.overlayHeading(), figure);
			assertFits(metrics, group.overlayHeading(), gain);
		}

		// The rows above the counters, at the widest each of them gets: a delve deeper than anyone
		// has reached, a run longer than anyone sits through, and a pace nobody will ever hold.
		String deepest = Integer.toString(DoomMetricsConfig.MAX_DELVE);
		assertFits(metrics, "Died on", "Delve " + deepest);
		assertFits(metrics, "Cleared", deepest);
		assertFits(metrics, "Delve", deepest);
		assertFits(metrics, "Time*", DoomFormat.duration(Duration.ofHours(10)));
		assertFits(metrics, PaceMode.DEEP_AVERAGE.toString(), "999.9/hr");
		assertFits(metrics, PaceMode.RUN_THROUGHPUT.toString(), "999.9/hr");

		// The target rows, at the deepest target that can be set and the longest wait it implies -
		// "Predicted" is the widest label the overlay has, so it is the one worth measuring.
		assertFits(metrics, "Target", deepest);
		assertFits(metrics, "Predicted", DoomFormat.duration(Duration.ofHours(99)));
		assertFits(metrics, "Predicted", DoomFormat.prediction(null, true));
	}

	@Test
	public void drawsEveryStateAtTheStandardWidth()
	{
		for (PreviewScene scene : PreviewScene.all())
		{
			BufferedImage drawn = draw(scene);

			// A scene with no run is one where the overlay is not on screen at all.
			if (drawn != null)
			{
				assertEquals(scene.name + " should draw at the standard overlay width",
					ComponentConstants.STANDARD_WIDTH, drawn.getWidth());
			}
		}

		// With zeros drawn, so both runs have a line for every counter and only the figures differ.
		PreviewScene deep = PreviewScene.named("deep");
		PreviewScene ceiling = PreviewScene.named("ceiling");
		deep.config.hideEmptyCounters = false;
		ceiling.config.hideEmptyCounters = false;

		assertEquals("a run's widest figures should not cost the overlay a line",
			draw(deep).getHeight(), draw(ceiling).getHeight());
	}

	/** The catch-alls are counted but never drawn, alone or summed into a combined line. */
	@Test
	public void neverDrawsTheCatchAlls()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(600), 5, false);
		plugin.run = run;
		config.allCounters(true);
		config.hideEmptyCounters = true;

		BufferedImage before = draw(plugin, config);

		run.recordCombat(CombatMetric.OTHER_SPELL_HEAL, 90, now.minusSeconds(60));
		run.recordCombat(CombatMetric.OTHER_SPEC_HEAL, 90, now.minusSeconds(60));
		run.recordCombat(CombatMetric.OTHER_SPEC_DAMAGE, 90, now.minusSeconds(60));
		run.recordCombat(CombatMetric.OTHER_MELEE_PUNISH, 90, now.minusSeconds(60));

		assertEquals("separate", before.getHeight(), draw(plugin, config).getHeight());

		config.grouping = MetricDisplay.COMBINED;
		assertEquals("combined", before.getHeight(), draw(plugin, config).getHeight());
	}

	/** A line per counter that has counted something, and none for one still at 0. */
	@Test
	public void leavesCountersAtZeroOffUnlessAskedToDrawThem()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(600), 5, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 300, now.minusSeconds(60));
		plugin.run = run;

		config.allCounters(true);
		int hidden = draw(plugin, config).getHeight();

		config.hideEmptyCounters = false;
		int drawn = draw(plugin, config).getHeight();

		assertEquals("every counter drawn but the crossbow is still at 0",
			(CombatMetric.DISPLAYED.size() - 1) * lineHeight(), drawn - hidden);

		// Combined, a group with nothing counted under it has no line either.
		config.grouping = MetricDisplay.COMBINED;
		int combinedDrawn = draw(plugin, config).getHeight();

		config.hideEmptyCounters = true;
		int combinedHidden = draw(plugin, config).getHeight();

		assertEquals("only the spec damage line has counted anything",
			(CombatMetric.Group.values().length - 1) * lineHeight(),
			combinedDrawn - combinedHidden);

		// The line arrives with the first thing it counts.
		run.recordCombat(CombatMetric.SCYTHE_PUNISH, 40, now.minusSeconds(30));
		assertEquals(combinedHidden + lineHeight(), draw(plugin, config).getHeight());
	}

	/** How much taller one more line makes the overlay: the line and the gap under it. */
	private static int lineHeight()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		plugin.run = new DelveRun(Instant.now().minusSeconds(60), 5, false);
		config.allCounters(false);

		config.showPace = false;
		int without = draw(plugin, config).getHeight();

		config.showPace = true;
		return draw(plugin, config).getHeight() - without;
	}

	@Test
	public void drawsNothingUnlessThePanelIsTheChosenStyle()
	{
		PreviewScene scene = PreviewScene.named("deep");

		for (DisplayStyle style : DisplayStyle.values())
		{
			scene.config.displayStyle = style;
			BufferedImage drawn = draw(scene);

			if (style == DisplayStyle.PANEL)
			{
				assertNotNull("the panel style draws the panel", drawn);
			}
			else
			{
				assertNull(style + " is drawn by the infobox, or not at all", drawn);
			}
		}
	}

	/**
	 * An icon is a line of text high, so switching icons on swaps each counter's name for its
	 * picture without moving a single row - the overlay stays the size you placed it at.
	 */
	@Test
	public void iconsTakeTheNamesPlaceWithoutResizingTheOverlay()
	{
		for (MetricDisplay grouping : MetricDisplay.values())
		{
			// A run walked out of, so its clock has stopped and the two drawings differ only where
			// the icons do.
			PreviewScene scene = PreviewScene.named("lingering");
			scene.config.grouping = grouping;
			BufferedImage names = draw(scene);

			scene.config.counterIcons = true;
			BufferedImage icons = draw(scene);

			assertEquals(grouping + " width", names.getWidth(), icons.getWidth());
			assertEquals(grouping + " height", names.getHeight(), icons.getHeight());
			assertEquals(grouping + ": only separate counters have an icon each",
				grouping == MetricDisplay.SEPARATE, !samePixels(names, icons));
		}
	}

	private static boolean samePixels(BufferedImage a, BufferedImage b)
	{
		for (int y = 0; y < a.getHeight(); y++)
		{
			for (int x = 0; x < a.getWidth(); x++)
			{
				if (a.getRGB(x, y) != b.getRGB(x, y))
				{
					return false;
				}
			}
		}

		return true;
	}

	private static void assertFits(FontMetrics metrics, String left, String right)
	{
		int width = metrics.stringWidth(left) + GAP + metrics.stringWidth(right);

		assertTrue("\"" + left + "\" beside \"" + right + "\" needs " + width
			+ "px, and an overlay row has " + ROW_WIDTH, width <= ROW_WIDTH);
	}

	/** The font the client draws an overlay in, which is the only one worth measuring against. */
	private static FontMetrics metrics()
	{
		Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

		try
		{
			return graphics.getFontMetrics(FontManager.getRunescapeFont());
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static BufferedImage draw(PreviewScene scene)
	{
		PreviewPlugin plugin = new PreviewPlugin();
		plugin.run = scene.run;

		return draw(plugin, scene.config);
	}

	private static BufferedImage draw(PreviewPlugin plugin, PreviewConfig config)
	{
		return PreviewRender.overlay(new DoomMetricsOverlay(plugin, config));
	}
}
