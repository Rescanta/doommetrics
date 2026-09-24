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

	/** Between a counter's icon and its figure - see {@code DoomMetricsOverlay.ICON_GAP}. */
	private static final int ICON_GAP = 4;

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

		// As totals, the counters are drawn under these instead, and reach the same figures.
		for (CombatMetric.Group group : CombatMetric.Group.values())
		{
			assertFits(metrics, group.overlayHeading(), figure);
			assertFits(metrics, group.overlayHeading(), gain);
		}

		// Two to a line, an icon and a figure have half a line each - less the gap between them.
		int column = OverlayColumns.columnWidth(ROW_WIDTH, DoomMetricsOverlay.GRID_COLUMNS,
			DoomMetricsOverlay.GRID_GAP);

		for (CombatMetric metric : CombatMetric.DISPLAYED)
		{
			int icon = PreviewIcons.INSTANCE.smallCounter(metric).getWidth() + ICON_GAP;

			// The widest figure each length of number comes out as, whole or shortened.
			for (long widest : new long[]{9_999, 99_999, 999_999, 9_999_999})
			{
				String total = DoomMetricsOverlay.figure(widest, 0, true);
				String gained = DoomMetricsOverlay.figure(widest, widest, true);

				assertTrue(metric + "'s icon beside " + total + " needs more than a column's "
					+ column + "px", icon + metrics.stringWidth(total) <= column);
				assertTrue(metric + "'s icon beside " + gained + " needs more than a column's "
					+ column + "px", icon + metrics.stringWidth(gained) <= column);
			}
		}

		assertEquals("a figure that fits is kept whole", "9,999",
			DoomMetricsOverlay.figure(9_999, 0, true));
		assertEquals("a gain that fits is kept whole", "+999",
			DoomMetricsOverlay.figure(9_999, 999, true));
		assertEquals("25k", DoomMetricsOverlay.figure(25_400, 0, true));
		assertEquals("+1.2k", DoomMetricsOverlay.figure(25_400, 1_234, true));
		assertEquals("a whole line never shortens", "25,400",
			DoomMetricsOverlay.figure(25_400, 0, false));

		// The rows above the counters, at the widest each of them gets: a delve deeper than anyone
		// has reached, a run longer than anyone sits through, and a pace nobody will ever hold.
		String deepest = Integer.toString(DoomMetricsConfig.MAX_DELVE);
		assertFits(metrics, "Died on", "Delve " + deepest);
		assertFits(metrics, "Cleared", deepest);
		assertFits(metrics, "Delve", deepest);
		assertFits(metrics, "Time*", DoomFormat.duration(Duration.ofHours(10)));
		assertFits(metrics, PaceMode.DEEP_AVERAGE.toString(), "999.9/hr");
		assertFits(metrics, PaceMode.RUN_THROUGHPUT.toString(), "999.9/hr");

		// The target rows, at the deepest target that can be set and the longest wait it implies.
		String longest = DoomFormat.duration(Duration.ofHours(99));
		assertFits(metrics, "Target", deepest);
		assertFits(metrics, "Target reached", deepest);
		assertFits(metrics, "To go", longest);
		assertFits(metrics, "Total", longest);
		assertFits(metrics, "Total*", longest);
		assertFits(metrics, "Total*", "Reached");
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

	/**
	 * The catch-alls never get a line of their own, and a total line always counts them.
	 *
	 * <p>The two halves are the same rule read from either end: there is no counter for "some
	 * other melee weapon", so a line for it is a line nobody asked for, while a damage total that
	 * left it out would be short by every punish thrown with a weapon this plugin does not name.
	 */
	@Test
	public void countsTheCatchAllsOnlyIntoATotalLine()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(600), 5, false);
		plugin.run = run;
		config.hideEmptyCounters = true;

		config.allModes(CounterMode.EACH);
		BufferedImage each = draw(plugin, config);
		config.allModes(CounterMode.TOTAL);
		BufferedImage total = draw(plugin, config);

		run.recordCombat(CombatMetric.OTHER_SPELL_HEAL, 90, now.minusSeconds(60));
		run.recordCombat(CombatMetric.OTHER_MELEE_PUNISH, 90, now.minusSeconds(60));

		assertEquals("the catch-alls should give the two headings they feed a line each",
			total.getHeight() + ruleHeight() + 2 * lineHeight(), draw(plugin, config).getHeight());

		config.allModes(CounterMode.EACH);
		assertEquals("no catch-all has a line of its own",
			each.getHeight(), draw(plugin, config).getHeight());
	}

	/**
	 * Each heading is drawn its own way: one can be a total while the next is a line per counter
	 * and the third is not drawn at all.
	 */
	@Test
	public void drawsEachHeadingTheWayItIsSetTo()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(600), 5, false);
		run.recordCombat(CombatMetric.BLOOD_BARRAGE_HEAL, 300, now.minusSeconds(60));
		run.recordCombat(CombatMetric.AGS_HEAL, 40, now.minusSeconds(60));
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 90, now.minusSeconds(60));
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 300, now.minusSeconds(60));
		run.recordCombat(CombatMetric.SCYTHE_PUNISH, 200, now.minusSeconds(60));
		plugin.run = run;

		config.allModes(CounterMode.OFF);
		int none = draw(plugin, config).getHeight();

		config.allModes(CounterMode.EACH);
		assertEquals("a line for each of the five counters that has counted",
			none + ruleHeight() + 5 * lineHeight(), draw(plugin, config).getHeight());

		config.allModes(CounterMode.TOTAL);
		assertEquals("a line for each heading", none + ruleHeight() + 3 * lineHeight(),
			draw(plugin, config).getHeight());

		config.mode(CombatMetric.Group.HEALING, CounterMode.TOTAL);
		config.mode(CombatMetric.Group.PRAYER, CounterMode.OFF);
		config.mode(CombatMetric.Group.DAMAGE, CounterMode.EACH);
		assertEquals("healing as one line, prayer not at all, and damage by weapon",
			none + ruleHeight() + 3 * lineHeight(), draw(plugin, config).getHeight());
	}

	/**
	 * Two to a line, the counters drawn by icon take half the lines, and a total keeps a whole line
	 * of its own between them.
	 */
	@Test
	public void theIconGridPutsTwoCountersToALine()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(600), 5, false);
		plugin.run = run;
		config.hideEmptyCounters = false;

		config.allModes(CounterMode.OFF);
		int none = draw(plugin, config).getHeight();

		config.allModes(CounterMode.EACH);
		config.counterStyle = CounterStyle.ICONS;
		int icons = draw(plugin, config).getHeight();

		config.counterStyle = CounterStyle.ICON_GRID;
		BufferedImage grid = draw(plugin, config);

		assertEquals("ten counters, a line each", none + ruleHeight() + 10 * lineHeight(), icons);
		assertEquals("the grid keeps the overlay's width", ComponentConstants.STANDARD_WIDTH,
			grid.getWidth());
		assertEquals("ten counters, two to a line", none + ruleHeight() + 5 * lineHeight(),
			grid.getHeight());

		// Healing's four counters take two lines; prayer's total takes one of its own; the four
		// damage counters take two more.
		config.mode(CombatMetric.Group.PRAYER, CounterMode.TOTAL);
		assertEquals(none + ruleHeight() + 5 * lineHeight(), draw(plugin, config).getHeight());

		// Without the damage, the two prayer counters share a line after healing's two.
		config.mode(CombatMetric.Group.PRAYER, CounterMode.EACH);
		config.mode(CombatMetric.Group.DAMAGE, CounterMode.OFF);
		assertEquals("blood barrage, AGS, blowpipe, SGS, eldritch and SGS prayer - three lines",
			none + ruleHeight() + 3 * lineHeight(), draw(plugin, config).getHeight());

		// Healing alone fills its two lines.
		config.mode(CombatMetric.Group.PRAYER, CounterMode.OFF);
		assertEquals("four healing counters - two lines",
			none + ruleHeight() + 2 * lineHeight(), draw(plugin, config).getHeight());
	}

	/**
	 * A counter whose picture has not arrived from the game is drawn by name, and a name needs the
	 * whole line - so the grid falls back to a line each rather than wrapping a name into half one.
	 */
	@Test
	public void theIconGridDrawsCountersWithNoIconYetOneToALine()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin()
		{
			@Override
			Icons getIcons()
			{
				return Icons.NONE;
			}
		};
		plugin.run = new DelveRun(Instant.now().minusSeconds(600), 5, false);
		config.hideEmptyCounters = false;

		config.counterStyle = CounterStyle.NAMES;
		int names = draw(plugin, config).getHeight();

		config.counterStyle = CounterStyle.ICON_GRID;
		assertEquals(names, draw(plugin, config).getHeight());
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

		config.allModes(CounterMode.EACH);
		int hidden = draw(plugin, config).getHeight();

		config.hideEmptyCounters = false;
		int drawn = draw(plugin, config).getHeight();

		assertEquals("every counter drawn but the crossbow is still at 0",
			(CombatMetric.DISPLAYED.size() - 1) * lineHeight(), drawn - hidden);

		// As totals, a heading with nothing counted under it has no line either.
		config.allModes(CounterMode.TOTAL);
		int totalDrawn = draw(plugin, config).getHeight();

		config.hideEmptyCounters = true;
		int totalHidden = draw(plugin, config).getHeight();

		assertEquals("only the damage line has counted anything",
			(CombatMetric.Group.values().length - 1) * lineHeight(), totalDrawn - totalHidden);

		// The line arrives with the first thing it counts.
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 40, now.minusSeconds(30));
		assertEquals(totalHidden + lineHeight(), draw(plugin, config).getHeight());
	}

	/**
	 * How much taller the rule between the run and its counters makes the overlay, which it only
	 * has once it has both.
	 */
	private static int ruleHeight()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(60), 5, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 300, now.minusSeconds(30));
		plugin.run = run;

		config.allModes(CounterMode.OFF);
		int without = draw(plugin, config).getHeight();

		config.mode(CombatMetric.Group.DAMAGE, CounterMode.EACH);
		return draw(plugin, config).getHeight() - without - lineHeight();
	}

	@Test
	public void theRuleOnlyComesBetweenTheRunAndItsCounters()
	{
		assertTrue("the rule takes some room", ruleHeight() > 0);

		// Everything but the counters switched off: counters alone need nothing to set them apart.
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		Instant now = Instant.now();
		DelveRun run = new DelveRun(now.minusSeconds(60), 5, false);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 300, now.minusSeconds(30));
		plugin.run = run;
		config.hidePluginName = true;
		config.showDelveNumber = false;
		config.showRunTimer = false;
		config.showPace = false;
		config.showTargetDelve = false;
		config.allModes(CounterMode.OFF);
		config.mode(CombatMetric.Group.DAMAGE, CounterMode.EACH);
		int alone = draw(plugin, config).getHeight();

		config.showPace = true;
		assertEquals(alone + ruleHeight() + lineHeight(), draw(plugin, config).getHeight());
	}

	/** How much taller one more line makes the overlay: the line and the gap under it. */
	private static int lineHeight()
	{
		PreviewConfig config = new PreviewConfig();
		PreviewPlugin plugin = new PreviewPlugin();
		plugin.run = new DelveRun(Instant.now().minusSeconds(60), 5, false);
		config.allModes(CounterMode.OFF);

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
	 * picture - or puts a heading's skill icon before its name - without moving a single row: the
	 * overlay stays the size you placed it at.
	 */
	@Test
	public void iconsTakeTheNamesPlaceWithoutResizingTheOverlay()
	{
		for (CounterMode mode : new CounterMode[]{CounterMode.EACH, CounterMode.TOTAL})
		{
			// A run walked out of, so its clock has stopped and the two drawings differ only where
			// the icons do.
			PreviewScene scene = PreviewScene.named("lingering");
			scene.config.allModes(mode);
			BufferedImage names = draw(scene);

			scene.config.counterStyle = CounterStyle.ICONS;
			BufferedImage icons = draw(scene);

			assertEquals(mode + " width", names.getWidth(), icons.getWidth());
			assertEquals(mode + " height", names.getHeight(), icons.getHeight());
			assertTrue(mode + ": every line has an icon", !samePixels(names, icons));
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
