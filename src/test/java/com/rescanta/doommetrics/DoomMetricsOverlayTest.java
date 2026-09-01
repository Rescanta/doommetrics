package com.rescanta.doommetrics;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
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
	 * The least space worth leaving between a label and the figure beside it. The tightest row
	 * there is - "Other spells" beside five figures - clears it with two pixels to spare.
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

		for (CombatMetric metric : CombatMetric.values())
		{
			assertFits(metrics, metric.overlayLabel(), figure);
		}

		// Combined, the counters are drawn under these instead, and reach the same figures.
		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			assertFits(metrics, group.overlayHeading(), figure);
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

		assertEquals("a run's widest figures should not cost the overlay a line",
			draw(PreviewScene.named("deep")).getHeight(),
			draw(PreviewScene.named("ceiling")).getHeight());
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

		return PreviewRender.overlay(new DoomMetricsOverlay(plugin, scene.config));
	}
}
