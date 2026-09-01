package com.rescanta.doommetrics;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import net.runelite.client.ui.FontManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * What the infobox square comes out as, measured and read rather than described.
 *
 * <p>The square has one constraint the overlay does not: it is a fixed box, and text wider than it
 * is not wrapped or shrunk but drawn straight out over the game either side of it. That is what
 * picks the shortened formats in {@link DoomFormat}, so it is measured here in the font the client
 * draws an infobox in.
 */
public class DoomMetricsInfoBoxTest
{
	/** The size a fresh install draws an infobox at - {@code RuneLiteConfig.infoBoxSize}. */
	private static final int SQUARE = PreviewRender.INFOBOX_SIZE;

	/** The most a whole run can put on one counter, as the overlay's own test uses. */
	private static final long WIDEST_COUNTER = 999_999;

	@Test
	public void everyFigureFitsTheSquareAtItsWidest()
	{
		FontMetrics metrics = metrics();
		PreviewScene ceiling = PreviewScene.named("ceiling");
		Instant now = Instant.now();

		for (InfoBoxFigure figure : InfoBoxFigure.values())
		{
			assertFits(metrics, figure, figure.text(ceiling.run, ceiling.config, now));
		}

		// The widest each figure can ever reach, which no scene does: a delve deeper than anyone
		// has been, a run longer than anyone sits through, a pace nobody holds, and a counter at
		// six figures - which a run cannot reach, so the square has room to spare on it.
		assertFits(metrics, InfoBoxFigure.DELVE, Integer.toString(DoomMetricsConfig.MAX_DELVE));
		assertFits(metrics, InfoBoxFigure.RUN_TIMER,
			DoomFormat.compactDuration(Duration.ofMinutes(59).plusSeconds(59)));
		assertFits(metrics, InfoBoxFigure.RUN_TIMER,
			DoomFormat.compactDuration(Duration.ofHours(9).plusMinutes(59)));
		assertFits(metrics, InfoBoxFigure.RUN_TIMER,
			DoomFormat.compactDuration(Duration.ofHours(10)));
		assertFits(metrics, InfoBoxFigure.PACE, DoomFormat.compactPace(999.9));
		assertFits(metrics, InfoBoxFigure.ZCB_DAMAGE, DoomFormat.compact(WIDEST_COUNTER));

		// The target figure has one reading that is not a clock, and the deepest target anyone can
		// set is a long way off at the pace the shallow delves are going.
		ceiling.config.targetDelve = DoomMetricsConfig.MAX_DELVE;
		assertFits(metrics, InfoBoxFigure.TIME_TO_TARGET,
			InfoBoxFigure.TIME_TO_TARGET.text(ceiling.run, ceiling.config, now));

		ceiling.config.targetDelve = 10;
		assertFits(metrics, InfoBoxFigure.TIME_TO_TARGET,
			InfoBoxFigure.TIME_TO_TARGET.text(ceiling.run, ceiling.config, now));
	}

	@Test
	public void isUpOnlyForInfoboxStyleAndOnlyWithARun()
	{
		PreviewScene scene = PreviewScene.named("deep");
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		DoomMetricsInfoBox box = box(plugin, config);

		plugin.run = scene.run;

		config.displayStyle = DisplayStyle.PANEL;
		assertFalse("the panel draws the run, so the square stays down", box.render());

		config.displayStyle = DisplayStyle.OFF;
		assertFalse("nothing is drawn at all", box.render());

		config.displayStyle = DisplayStyle.INFOBOX;
		assertTrue("a run and the style that draws it", box.render());

		plugin.run = null;
		assertFalse("no run to report", box.render());
	}

	@Test
	public void readsWhicheverFigureItWasPointedAt()
	{
		PreviewScene scene = PreviewScene.named("deep");
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		DoomMetricsInfoBox box = box(plugin, config);

		plugin.run = scene.run;
		config.displayStyle = DisplayStyle.INFOBOX;

		config.infoboxFigure = InfoBoxFigure.DELVE;
		assertEquals("24", box.getText());
		assertEquals("a delve number is not counted in anything", DoomColors.PLAIN,
			box.getTextColor());

		config.infoboxFigure = InfoBoxFigure.AGS_HEAL;
		assertEquals("58", box.getText());
		assertEquals("hitpoints, and the colour says so", CombatMetric.Unit.HITPOINTS.color(),
			box.getTextColor());

		// The two spec heals this run has, plus the one it has not, summed under their heading.
		config.infoboxFigure = InfoBoxFigure.ALL_SPEC_HEALING;
		assertEquals("105", box.getText());

		config.infoboxFigure = InfoBoxFigure.OTHER_SPEC_HEAL;
		assertEquals("0", box.getText());
		assertEquals("a counter that has not fired is drawn back", DoomColors.DIMMED,
			box.getTextColor());

		// The shape rather than the figure: a live clock read twice can differ by a second.
		config.infoboxFigure = InfoBoxFigure.RUN_TIMER;
		assertTrue("a run in progress reads as a clock: " + box.getText(),
			box.getText().matches("\\d+:\\d\\d"));
	}

	/**
	 * The predicted figure is the panel's Predicted row in a square, and it reads the target out of
	 * the config rather than out of the run - so the delve being aimed for is the one setting, set
	 * in one place, whichever of the two is drawing it.
	 */
	@Test
	public void countsDownToWhicheverTargetTheConfigNames()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		DoomMetricsInfoBox box = box(plugin, config);

		plugin.run = PreviewScene.named("deep").run;
		config.displayStyle = DisplayStyle.INFOBOX;
		config.infoboxFigure = InfoBoxFigure.TIME_TO_TARGET;

		config.targetDelve = 50;
		String further = box.getText();

		// Not compared against a figure worked out here: the prediction counts down as it is read,
		// so two reads a millisecond apart can differ by a second. The shape of it is the claim.
		assertTrue("a target ahead of you reads as a wait: " + further,
			further.matches("\\d+:\\d\\d"));
		assertTrue("a target ahead of you is a wait to be read",
			box.getTooltip().startsWith("Predicted to delve 50"));

		config.targetDelve = 40;
		assertNotEquals("a nearer target is a shorter wait", further, box.getText());

		// Delve 23 is behind this run, so there is nothing left to predict.
		config.targetDelve = 10;
		assertEquals("Done", box.getText());
		assertEquals("Delve 10</br>Reached", box.getTooltip());

		// A run with no delve 9 behind it has no average, so there is nothing to predict from.
		plugin.run = PreviewScene.named("shallow").run;
		config.targetDelve = 50;
		assertEquals("-", box.getText());
		assertEquals("nothing behind the figure yet", DoomColors.DIMMED, box.getTextColor());
	}

	@Test
	public void tooltipCarriesWhatTheSquareCouldNotFit()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		DoomMetricsInfoBox box = box(plugin, config);

		plugin.run = PreviewScene.named("deep").run;
		config.displayStyle = DisplayStyle.INFOBOX;

		config.infoboxFigure = InfoBoxFigure.ZCB_DAMAGE;
		assertEquals("Spec damage: Zaryte crossbow</br>1,502 damage dealt", box.getTooltip());

		config.infoboxFigure = InfoBoxFigure.PACE;
		assertTrue("the unit the square dropped belongs in the tooltip",
			box.getTooltip().endsWith("/hr"));

		// A death is reported on the delve it happened on, the way the panel reports it.
		plugin.run = PreviewScene.named("died").run;
		config.infoboxFigure = InfoBoxFigure.DELVE;
		assertEquals("32", box.getText());
		assertEquals("Died on delve 32", box.getTooltip());
	}

	private static DoomMetricsInfoBox box(PreviewPlugin plugin, PreviewConfig config)
	{
		return new DoomMetricsInfoBox(PreviewRender.icon(), plugin, config);
	}

	private static void assertFits(FontMetrics metrics, InfoBoxFigure figure, String text)
	{
		int width = metrics.stringWidth(text);

		assertTrue(figure + " reads \"" + text + "\", which needs " + width
			+ "px, and an infobox has " + SQUARE, width <= SQUARE);
	}

	/** The font the client draws an infobox in - {@code FontType.REGULAR}, the game font. */
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
}
