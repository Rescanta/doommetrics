package com.rescanta.doommetrics;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.api.Constants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One run, delve by delve: what each of the counters gave back on each delve, and how long each
 * delve took.
 *
 * <p>Two plots, stacked, sharing one delve axis. The upper one carries the eight counters, a line
 * each - or, grouped, the five headings they sit under, which is the same figures added up and is
 * what a run long enough to fill the plot is read by - see {@link #setGrouped}. The lower one
 * carries the clock: the delve's full time - its kill and the wait after it -
 * and, under it, the kill the game timed, with the band between them - the restocking, the specs
 * fired before going down, the drop down the hole - filled.
 * They are two plots rather than one with two scales, because a second axis is two charts drawn on
 * top of each other and called one, and the alignment between the two scales would be arbitrary.
 * Sharing the x axis is what lets a slow delve be read against a quiet one without inventing a
 * relationship between seconds and hitpoints.
 *
 * <p>The counters do share their axis, and are three different units doing it - hitpoints,
 * prayer points and damage. That is only legible because of a fact about this fight rather than a
 * fact about charts: none of them clears a few hundred on a single delve, so all of them sit in one
 * band and the reader is comparing like sizes. It would be indefensible over a whole run, where
 * damage runs twenty times the healing, which is why the totals beside the chart are drawn as
 * meters scaled per unit instead. Which unit a line is counted in is on the legend, under the
 * heading the line is listed beneath.
 *
 * <p>Lines rather than dots, because the deepest runs go past three hundred delves and at that
 * width a dot per delve per series is a smear. Markers are drawn only when the delves are far
 * enough apart to hit.
 *
 * <p>The run's notable drops sit in a lane over the counters, each as its item's icon above the
 * delve it came off - over the plot rather than on it, so an icon never hides the lines, and
 * sharing the delve axis, so a drop can be read against what that delve cost. A drop the run did
 * not walk out with is still drawn where it dropped, faded.
 *
 * <p>Swing thread only.
 */
class DelveChart extends JPanel
{
	/** The delve axis, and the run's own clock, drawn a shade off the plot. */
	private static final Color GRID_COLOR = new Color(44, 44, 42);
	private static final Color AXIS_COLOR = new Color(56, 56, 53);
	private static final Color LABEL_COLOR = ColorScheme.LIGHT_GRAY_COLOR;

	/** The full time a delve took, and the kill the game timed inside it. */
	private static final Color FULL_TIME_COLOR = new Color(0xC3C2B7);
	private static final Color FIGHT_COLOR = new Color(0x898781);

	/** The time between the two, which is the time not spent fighting. */
	private static final Color DOWNTIME_FILL = new Color(0xC3, 0xC2, 0xB7, 44);

	/** The delve under the pointer. */
	private static final Color CROSSHAIR_COLOR = new Color(0xFF, 0xFF, 0xFF, 90);

	/** The figures in the readout of the delve under the pointer, a step brighter than their labels. */
	private static final Color READOUT_VALUE_COLOR = ColorScheme.TEXT_COLOR;

	/** Between one label and figure of the readout and the next, and between it and the caption. */
	private static final int READOUT_GAP = 12;

	/**
	 * The two lines on the time strip, named on its legend and in the readout. The full time runs
	 * from a delve starting to the next one starting, the wait after the kill included - see
	 * {@link RunDetail.Delve#fullTime}; the kill time is the one the game posts in chat.
	 */
	private static final String FULL_TIME = "Full time";
	private static final String KILL_TIME = "Kill time";

	private static final BasicStroke SERIES_STROKE =
		new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	/** The one series brought forward when the reader points at its name. */
	private static final BasicStroke EMPHASIS_STROKE =
		new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final BasicStroke TIME_STROKE =
		new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final BasicStroke HAIRLINE = new BasicStroke(1f);

	/** How far a series drawn behind an emphasised one is faded. */
	private static final int DIMMED_ALPHA = 55;

	/** How far the delve-by-delve line is faded once an average is drawn over it. */
	private static final int RAW_ALPHA = 60;

	/**
	 * Past this many delves the counters are drawn as a rolling average, with the delve-by-delve
	 * line left underneath at a fraction of the weight.
	 *
	 * <p>A counter varies a good deal from one delve to the next - a spec fires twice on one and
	 * not at all on the next - and past a couple of hundred delves a dozen lines of that fill in as
	 * a solid band with no trend left in it. The average carries the trend the lines no longer can,
	 * and the lines are kept because the spread they show is real: what a delve cost is not the
	 * average of the delves around it, and a chart of averages alone would say it was.
	 *
	 * <p>The exact figure for any one delve is never inferred from either. Pointing at a delve
	 * reads that delve's own numbers off {@link RunLegendPanel}.
	 */
	private static final int TREND_FROM_DELVES = 80;

	private static final int PAD_LEFT = 48;
	private static final int PAD_RIGHT = 14;

	/** Room over the counters for the caption naming what their axis is counting. */
	private static final int PAD_TOP = 24;

	/** Room under the time strip for the delve ticks and the axis caption. */
	private static final int PAD_BOTTOM = 32;

	/** Between the two plots: the strip's own legend sits in here. */
	private static final int PLOT_GAP = 26;

	/** The time strip's height. Fixed, so widening the window grows the counters. */
	private static final int STRIP_HEIGHT = 64;

	/** Under this the counters have less room than the strip and the window is too short to read. */
	private static final int MIN_PLOT_HEIGHT = 90;

	/** How far apart two delves have to be before a marker fits between them. */
	private static final int MARKER_SPACING = 9;

	private static final int MARKER = 5;

	/** Gridlines wanted up the counters, and up the time strip, before rounding to a nice step. */
	private static final int COUNT_TICKS = 6;

	/** Fewer up the strip: it is a third the height, and five clocks in it are unreadable. */
	private static final int TIME_TICKS = 2;

	/** Delve ticks along the bottom. Fewer than the counters have, because the labels are wider. */
	private static final int DELVE_TICKS = 10;

	/**
	 * Round spans for the time strip's gridlines, in seconds. {@link #niceStep} is wrong for a
	 * clock - it would put a gridline every fifty seconds and label it {@code 0:50} - so the steps
	 * a reader would actually pick are listed instead.
	 */
	private static final int[] TIME_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};

	/** A drop's icon: the item as the game draws it in the inventory, at the size it draws it. */
	private static final int ICON_WIDTH = Constants.ITEM_SPRITE_WIDTH;
	private static final int ICON_HEIGHT = Constants.ITEM_SPRITE_HEIGHT;

	/** Between two icons side by side, between two rows of them, and between the lane and plot. */
	private static final int ICON_GAP = 3;

	/**
	 * The most rows the lane stacks icons into when they are too close to sit side by side. Past
	 * this they overlap in the top row: a run with more drops than that bunched together is worth
	 * a crowded lane rather than a plot squeezed down to make room for it.
	 */
	private static final int MAX_ICON_ROWS = 3;

	/** How much of a lost drop is drawn - enough to see what it was, and that it is gone. */
	private static final float LOST_ALPHA = 0.35f;

	/** The hairline from an icon down to the plot, which says which delve it is over. */
	private static final Color DROP_TICK = new Color(0xFF, 0xFF, 0xFF, 50);

	/** The game's own colour for a stack's count. */
	private static final Color STACK_COLOR = new Color(0xFF, 0xFF, 0x00);

	/** What an unknown unique is drawn as, in a drop's slot. */
	private static final BufferedImage UNKNOWN_ICON = IconArt.unknownUnique(ICON_WIDTH, ICON_HEIGHT);

	private RunDetail detail = RunDetail.empty();

	/**
	 * The lines on the upper plot: one per counter, or one per heading once the reader groups
	 * them - see {@link #setGrouped}.
	 */
	private List<CombatSeries> series = CombatSeries.drawn(false);

	/** Counters the reader has switched off. Never repainted for the ones left on - see below. */
	private Set<CombatSeries> hidden = new HashSet<>();

	/** The counter being pointed at in the legend, brought forward, or null for none. */
	private CombatSeries emphasis;

	/** The delve under the pointer, or 0 when the pointer is off the plot. */
	private int hovered;

	/** Told which delve is under the pointer, so the legend can read out that delve's figures. */
	private IntConsumer onHover = level ->
	{
	};

	private int deepest;

	/**
	 * The delve at the left edge: 1 for a run watched from the start, and the first delve seen for
	 * one joined part way through, whose earlier delves have no columns to leave room for.
	 */
	private int shallowest = 1;

	/** Hands back an item's icon, or null while there is none to be had. */
	private IntFunction<BufferedImage> itemIcons = itemId -> null;

	/** Where each drop's icon was last drawn, in the order the run's drops are listed. */
	private Rectangle[] dropBounds = new Rectangle[0];

	/** How many rows of icons the lane took on the last paint, or 0 for a run with no drops. */
	private int iconRows;

	/** How many delves the average is taken over, or 0 for a run too short to draw one through. */
	private int window;

	private int countMax = 1;
	private int countStep = 1;
	private int timeMax = 1;
	private int timeStep = 1;

	DelveChart()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(640, PAD_TOP + 240 + PLOT_GAP + STRIP_HEIGHT + PAD_BOTTOM));

		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent event)
			{
				RunDetail.Drop drop = dropAt(event.getX(), event.getY());
				hover(drop != null ? drop.level : nearestDelve(event.getX(), event.getY()));
			}
		});

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent event)
			{
				hover(0);
			}
		});

		// For the drop icons, which name themselves on hover. Nothing else here has a tooltip.
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	/**
	 * @param itemIcons hands back an item's icon for a drop, or null while there is none - when
	 *                  the drop is drawn as a box holding its initial. Asked on every paint, so it
	 *                  has to be cheap; handing it over again repaints with whatever has arrived.
	 */
	void setItemIcons(IntFunction<BufferedImage> itemIcons)
	{
		this.itemIcons = itemIcons;
		repaint();
	}

	/** @param onHover told the delve under the pointer, or 0 when the pointer leaves the plot */
	void setHoverListener(IntConsumer onHover)
	{
		this.onHover = onHover;
	}

	void setDetail(RunDetail detail)
	{
		this.detail = detail;
		this.deepest = detail.deepest();
		this.shallowest = detail.shallowest();
		this.window = windowFor(detail.delves().size());
		this.hovered = 0;
		rescale();
		repaint();
	}

	/**
	 * @param grouped whether the plot carries one line per heading rather than one per counter
	 *
	 * <p>Five lines instead of eight, and each of them a figure the reader was going to add up
	 * anyway: whether the healing held up on the delves that went badly is a question about the
	 * healing, not about which of three sources did it. The lines it folds together share a unit,
	 * so the sum is a number and not an average of apples - see {@link CombatMetric.Group#unit}.
	 *
	 * <p>The heading's line counts the sources with no counter of their own as well, so grouping
	 * is also the only way to see a punish thrown with a weapon this plugin does not name.
	 */
	void setGrouped(boolean grouped)
	{
		series = CombatSeries.drawn(grouped);
		rescale();
		repaint();
	}

	/**
	 * @param hidden the counters to leave off the plot
	 *
	 * <p>Hiding one never restyles the rest: a colour belongs to a counter for as long as the
	 * window is open, so a reader who has learned that the violet line is the Zaryte crossbow is
	 * not told otherwise by switching the barrage off.
	 */
	void setHidden(Set<CombatSeries> hidden)
	{
		this.hidden = hidden;
		rescale();
		repaint();
	}

	/** @param emphasis the counter to bring forward, or null to draw all of them level */
	void setEmphasis(CombatSeries emphasis)
	{
		if (this.emphasis == emphasis)
		{
			return;
		}

		this.emphasis = emphasis;
		repaint();
	}

	/**
	 * Refits both axes to what is on show.
	 *
	 * <p>The counters are scaled to the visible ones only, so switching the Zaryte crossbow off
	 * gives the healing lines the height they were sharing with it. The clock is not: it is scaled
	 * to the run, so the strip does not rescale under the reader when a counter is toggled.
	 */
	private void rescale()
	{
		int highest = 0;
		int longest = 0;

		for (RunDetail.Delve delve : detail.delves())
		{
			for (CombatSeries line : series)
			{
				if (!hidden.contains(line))
				{
					highest = (int) Math.max(highest, line.amount(delve.combat));
				}
			}

			longest = Math.max(longest, (int) delve.fullTime.getSeconds());
		}

		countStep = Math.max(1, niceStep(highest, COUNT_TICKS));
		countMax = topFor(highest, countStep);

		timeStep = timeStep(longest, TIME_TICKS);
		timeMax = topFor(longest, timeStep);
	}

	private void hover(int level)
	{
		if (hovered == level)
		{
			return;
		}

		hovered = level;
		onHover.accept(level);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D) g.create();

		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.setFont(FontManager.getRunescapeSmallFont());

			if (detail.isEmpty() || getWidth() < PAD_LEFT + PAD_RIGHT + 40)
			{
				iconRows = 0;
				dropBounds = new Rectangle[0];
				drawEmpty(g2);
				return;
			}

			// First, because where the lane ends is where the counters begin.
			layoutDrops();

			drawCountGrid(g2);
			drawTimeGrid(g2);
			drawDelveAxis(g2);
			drawTimes(g2);
			drawCounters(g2);
			drawTimeLegend(g2);
			drawDrops(g2);
			drawCrosshair(g2);
			drawHeader(g2);
		}
		finally
		{
			g2.dispose();
		}
	}

	private void drawEmpty(Graphics2D g2)
	{
		String text;

		if (!detail.hasRun())
		{
			text = "No run yet this session.";
		}
		else if (detail.isFinished())
		{
			// Walked in and straight back out, or died on the first one - a real thing that
			// happened, and not the same as a run that has not completed its first delve yet.
			text = "This run completed no delves.";
		}
		else
		{
			text = "No delve completed yet.";
		}

		FontMetrics metrics = g2.getFontMetrics();
		g2.setColor(LABEL_COLOR);
		g2.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2, getHeight() / 2);
	}

	// -- geometry ---------------------------------------------------------------------------

	private int left()
	{
		return PAD_LEFT;
	}

	private int right()
	{
		return getWidth() - PAD_RIGHT;
	}

	private int countTop()
	{
		return PAD_TOP + laneHeight();
	}

	/** The room the drop icons take over the counters: none at all for a run without a drop. */
	private int laneHeight()
	{
		return iconRows * (ICON_HEIGHT + ICON_GAP);
	}

	private int countBottom()
	{
		return Math.max(countTop() + MIN_PLOT_HEIGHT, stripTop() - PLOT_GAP);
	}

	private int stripTop()
	{
		return stripBottom() - STRIP_HEIGHT;
	}

	private int stripBottom()
	{
		return getHeight() - PAD_BOTTOM;
	}

	/** Where a delve sits along the shared axis. */
	private int xFor(int level)
	{
		if (deepest <= shallowest)
		{
			return (left() + right()) / 2;
		}

		return left() + (int) Math.round(
			(double) (level - shallowest) / (deepest - shallowest) * (right() - left()));
	}

	private int yForCount(long amount)
	{
		double fraction = (double) amount / Math.max(1, countMax);
		return countBottom() - (int) Math.round(fraction * (countBottom() - countTop()));
	}

	private int yForTime(long seconds)
	{
		double fraction = (double) seconds / Math.max(1, timeMax);
		return stripBottom() - (int) Math.round(fraction * (stripBottom() - stripTop()));
	}

	// -- chrome -----------------------------------------------------------------------------

	private void drawCountGrid(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		g2.setStroke(HAIRLINE);

		for (int value = 0; value <= countMax; value += countStep)
		{
			int y = yForCount(value);

			g2.setColor(GRID_COLOR);
			g2.drawLine(left(), y, right(), y);

			String text = DoomFormat.compact(value);
			g2.setColor(LABEL_COLOR);
			g2.drawString(text, left() - 6 - metrics.stringWidth(text),
				y + metrics.getAscent() / 2);
		}

		g2.setColor(AXIS_COLOR);
		g2.drawLine(left(), countTop(), left(), countBottom());
	}

	/**
	 * The line over the plots: what the counters' axis is counting on the left, and the delve under
	 * the pointer read out on the right.
	 *
	 * <p>The readout sits in one place rather than following the crosshair, so it can never be drawn
	 * over the caption whichever delve is pointed at. Where the window is too narrow for both, the
	 * caption gives way for as long as the pointer is on a delve - it says the same thing on every
	 * paint, and the readout is what was asked for.
	 */
	private void drawHeader(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		int baseline = PAD_TOP - 8;

		// What the numbers up the side are. Not a unit, because three of them share this axis -
		// which unit a line is counted in is on the legend, under its heading. The averaging is
		// named here rather than left to be inferred from the shape of the lines.
		String caption = window > 0
			? "Counted per delve - bold lines are averaged over " + window + " delves"
			: "Counted per delve";

		String[][] readout = readout();
		int readoutWidth = 0;

		for (String[] part : readout)
		{
			readoutWidth += metrics.stringWidth(part[0]) + metrics.stringWidth(part[1]) + READOUT_GAP;
		}

		readoutWidth -= readout.length > 0 ? READOUT_GAP : 0;
		int readoutLeft = Math.max(left(), right() - readoutWidth);

		if (readout.length == 0 || left() + metrics.stringWidth(caption) + READOUT_GAP <= readoutLeft)
		{
			g2.setColor(LABEL_COLOR);
			g2.drawString(caption, left(), baseline);
		}

		int x = readoutLeft;

		for (String[] part : readout)
		{
			g2.setColor(LABEL_COLOR);
			g2.drawString(part[0], x, baseline);
			x += metrics.stringWidth(part[0]);

			g2.setColor(READOUT_VALUE_COLOR);
			g2.drawString(part[1], x, baseline);
			x += metrics.stringWidth(part[1]) + READOUT_GAP;
		}
	}

	/**
	 * The delve under the pointer and its two times, each as a label and its figure, or nothing when
	 * the pointer is off the plots. The counters' figures are not in here: they are on the legend
	 * beside the chart, which has a row for each.
	 */
	private String[][] readout()
	{
		if (hovered <= 0)
		{
			return new String[0][];
		}

		String[] delve = {"Delve ", Integer.toString(hovered)};
		RunDetail.Delve at = detail.at(hovered);

		if (at == null)
		{
			return new String[][]{delve};
		}

		return new String[][]{
			delve,
			{FULL_TIME + " ", DoomFormat.duration(at.fullTime)},
			{KILL_TIME + " ", at.fight == null ? "-" : DoomFormat.duration(at.fight)},
		};
	}

	private void drawTimeGrid(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		g2.setStroke(HAIRLINE);

		for (int value = 0; value <= timeMax; value += timeStep)
		{
			int y = yForTime(value);

			g2.setColor(GRID_COLOR);
			g2.drawLine(left(), y, right(), y);

			String text = DoomFormat.duration(Duration.ofSeconds(value));
			g2.setColor(LABEL_COLOR);
			g2.drawString(text, left() - 6 - metrics.stringWidth(text),
				y + metrics.getAscent() / 2);
		}

		g2.setColor(AXIS_COLOR);
		g2.drawLine(left(), stripTop(), left(), stripBottom());
	}

	/**
	 * The delve numbers, drawn once under the lower plot. One axis for both, because reading a
	 * slow delve against a quiet one is the whole reason the two are stacked.
	 */
	private void drawDelveAxis(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		int bottom = stripBottom();

		g2.setStroke(HAIRLINE);
		g2.setColor(AXIS_COLOR);
		g2.drawLine(left(), bottom, right(), bottom);
		g2.drawLine(left(), countBottom(), right(), countBottom());

		int step = niceStep(deepest - shallowest + 1, DELVE_TICKS);

		for (int level = ceilTo(shallowest, step); level <= deepest; level += step)
		{
			int x = xFor(level);
			String text = Integer.toString(level);

			g2.setColor(AXIS_COLOR);
			g2.drawLine(x, bottom, x, bottom + 3);
			g2.setColor(LABEL_COLOR);
			g2.drawString(text, x - metrics.stringWidth(text) / 2, bottom + 4 + metrics.getAscent());
		}

		String caption = "Delve";
		g2.setColor(LABEL_COLOR);
		g2.drawString(caption, (left() + right() - metrics.stringWidth(caption)) / 2,
			getHeight() - 3);
	}

	// -- the plots --------------------------------------------------------------------------

	/**
	 * The clock: the full time each delve took, the kill the game timed inside it, and the band
	 * between them filled - which is the wait after the kill: the restocking, the specs fired before
	 * going down, and the drop down the hole.
	 *
	 * <p>Drawn in ink rather than in a colour of its own. Every colour on this window already
	 * names a counter, so one more would be read as one more counter.
	 * The two lines are told apart by construction instead: a fight is part of the segment that
	 * contains it, so the fight line is never the upper of the two.
	 */
	private void drawTimes(Graphics2D g2)
	{
		List<RunDetail.Delve> delves = detail.delves();

		Path2D.Double band = new Path2D.Double();
		Path2D.Double segments = new Path2D.Double();
		Path2D.Double fights = new Path2D.Double();
		boolean anyFight = false;

		for (int i = 0; i < delves.size(); i++)
		{
			RunDetail.Delve delve = delves.get(i);
			int x = xFor(delve.level);
			int y = yForTime(delve.fullTime.getSeconds());

			if (i == 0)
			{
				segments.moveTo(x, y);
				band.moveTo(x, y);
			}
			else
			{
				segments.lineTo(x, y);
				band.lineTo(x, y);
			}
		}

		// Back along the fight times, which closes the band onto the segments above it.
		for (int i = delves.size() - 1; i >= 0; i--)
		{
			RunDetail.Delve delve = delves.get(i);
			int x = xFor(delve.level);
			Duration fight = delve.fight == null ? delve.fullTime : delve.fight;
			int y = yForTime(Math.min(fight.getSeconds(), delve.fullTime.getSeconds()));

			band.lineTo(x, y);

			if (delve.fight == null)
			{
				continue;
			}

			if (!anyFight)
			{
				fights.moveTo(x, y);
				anyFight = true;
			}
			else
			{
				fights.lineTo(x, y);
			}
		}

		band.closePath();
		g2.setColor(DOWNTIME_FILL);
		g2.fill(band);

		g2.setStroke(TIME_STROKE);

		if (anyFight)
		{
			g2.setColor(FIGHT_COLOR);
			g2.draw(fights);
		}

		g2.setColor(FULL_TIME_COLOR);
		g2.draw(segments);
	}

	/**
	 * The counters, one line each, in the order they are declared - so an emphasised line is drawn
	 * last and lands over the rest rather than under whichever happens to follow it.
	 */
	private void drawCounters(Graphics2D g2)
	{
		for (CombatSeries line : series)
		{
			if (line != emphasis)
			{
				drawCounter(g2, line);
			}
		}

		if (emphasis != null && series.contains(emphasis))
		{
			drawCounter(g2, emphasis);
		}
	}

	private void drawCounter(Graphics2D g2, CombatSeries line)
	{
		if (hidden.contains(line))
		{
			return;
		}

		List<RunDetail.Delve> delves = detail.delves();
		long[] values = new long[delves.size()];

		for (int i = 0; i < delves.size(); i++)
		{
			values[i] = line.amount(delves.get(i).combat);
		}

		boolean front = emphasis == null || emphasis == line;
		Color color = front ? line.seriesColor() : dim(line.seriesColor());
		Stroke stroke = emphasis == line ? EMPHASIS_STROKE : SERIES_STROKE;

		if (window > 0)
		{
			// Underneath, so the trend is what the eye lands on and the spread is still there to
			// be looked at.
			g2.setColor(fade(color, RAW_ALPHA));
			g2.setStroke(HAIRLINE);
			g2.draw(line(delves, values));

			g2.setColor(color);
			g2.setStroke(stroke);
			g2.draw(line(delves, rollingAverage(values, window)));
			return;
		}

		g2.setColor(color);
		g2.setStroke(stroke);
		g2.draw(line(delves, values));

		if (!markersFit())
		{
			return;
		}

		for (int i = 0; i < delves.size(); i++)
		{
			int x = xFor(delves.get(i).level);
			int y = yForCount(values[i]);
			g2.fillOval(x - MARKER / 2, y - MARKER / 2, MARKER, MARKER);
		}
	}

	/** One value per delve, joined up. */
	private Path2D.Double line(List<RunDetail.Delve> delves, long[] values)
	{
		Path2D.Double path = new Path2D.Double();

		for (int i = 0; i < delves.size(); i++)
		{
			int x = xFor(delves.get(i).level);
			int y = yForCount(values[i]);

			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}

		return path;
	}

	private Path2D.Double line(List<RunDetail.Delve> delves, double[] values)
	{
		Path2D.Double path = new Path2D.Double();

		for (int i = 0; i < delves.size(); i++)
		{
			int x = xFor(delves.get(i).level);
			int y = yForCount(Math.round(values[i]));

			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}

		return path;
	}

	/** Whether the delves are far enough apart that a marker on each is a mark and not a smear. */
	private boolean markersFit()
	{
		return deepest <= shallowest
			|| (right() - left()) / (deepest - shallowest) >= MARKER_SPACING;
	}

	private static Color dim(Color color)
	{
		return fade(color, DIMMED_ALPHA);
	}

	private static Color fade(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
			Math.min(alpha, color.getAlpha()));
	}

	/**
	 * How many delves the average is taken over, or 0 for a run short enough that the lines read on
	 * their own.
	 *
	 * <p>The window widens with the run so the line stays a trend rather than tracing the delves
	 * under it, and stops widening so a four hundred delve run does not flatten into a straight
	 * bar. Below {@link #TREND_FROM_DELVES} there is no average at all: one narrow enough to fit
	 * would sit on top of every delve and hide the thing it claims to summarise, and at that length
	 * the delves are far enough apart to read one by one.
	 *
	 * <p>The threshold is set where it is because it is also where {@link #MARKER_SPACING} runs
	 * out: a plot a few hundred pixels wide has room for a marker on each of eighty delves and no
	 * more, so the delve-by-delve reading and the averaged one hand over to each other rather than
	 * overlapping in a range where neither is much good.
	 *
	 * <p>Always odd. The average is centred, reaching as far to one side as to the other, so an
	 * even window would take in one delve more than the caption says it does.
	 */
	static int windowFor(int delves)
	{
		return delves < TREND_FROM_DELVES ? 0 : (Math.max(9, Math.min(25, delves / 10)) | 1);
	}

	/**
	 * A centred mean over {@code window} delves. Centred rather than trailing so the line sits on
	 * the delves it describes instead of lagging half a window behind them. The ends average over
	 * whatever is there, which is why the line reaches both edges.
	 */
	static double[] rollingAverage(long[] values, int window)
	{
		double[] out = new double[values.length];
		int half = window / 2;

		for (int i = 0; i < values.length; i++)
		{
			int from = Math.max(0, i - half);
			int to = Math.min(values.length - 1, i + half);
			long sum = 0;

			for (int j = from; j <= to; j++)
			{
				sum += values[j];
			}

			out[i] = (double) sum / (to - from + 1);
		}

		return out;
	}

	/**
	 * Names the two lines on the time strip, in the gap above it.
	 *
	 * <p>The counters have no legend here: theirs is the table beside the chart, which has the room
	 * to name every one and their figures beside them. These two have nowhere else to be named.
	 */
	private void drawTimeLegend(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		int y = stripTop() - 7;
		int x = left();

		g2.setStroke(TIME_STROKE);
		x = timeLegendEntry(g2, metrics, FULL_TIME_COLOR, FULL_TIME, x, y);
		timeLegendEntry(g2, metrics, FIGHT_COLOR, KILL_TIME, x, y);
	}

	private int timeLegendEntry(Graphics2D g2, FontMetrics metrics, Color color, String label,
		int x, int y)
	{
		g2.setColor(color);
		g2.drawLine(x, y - metrics.getAscent() / 2, x + 12, y - metrics.getAscent() / 2);
		x += 16;

		g2.setColor(LABEL_COLOR);
		g2.drawString(label, x, y);
		return x + metrics.stringWidth(label) + 16;
	}

	/**
	 * The delve under the pointer, marked across both plots. Its times are read out over the plots
	 * - see {@link #drawHeader}.
	 *
	 * <p>A line rather than a tooltip, because the answer it is asked for is eight figures wide and
	 * a box holding eight figures covers the thing it is describing. The figures go to the table
	 * beside the chart instead, which already has a row for each of them.
	 */
	private void drawCrosshair(Graphics2D g2)
	{
		if (hovered <= 0)
		{
			return;
		}

		int x = xFor(hovered);

		g2.setStroke(HAIRLINE);
		g2.setColor(CROSSHAIR_COLOR);
		g2.drawLine(x, countTop(), x, countBottom());
		g2.drawLine(x, stripTop(), x, stripBottom());
	}

	// -- the drops ----------------------------------------------------------------------------

	/**
	 * Places every drop's icon over its delve, stacking the ones too close together to sit side by
	 * side, and sizes the lane to fit them.
	 *
	 * <p>Worked out on every paint because it depends on the width, and it is a handful of drops.
	 */
	private void layoutDrops()
	{
		List<RunDetail.Drop> drops = detail.drops();
		int[] lefts = new int[drops.size()];

		for (int i = 0; i < drops.size(); i++)
		{
			// Kept inside the component, so a drop on the first or last delve is not cut in half.
			int centred = xFor(drops.get(i).level) - ICON_WIDTH / 2;
			lefts[i] = Math.max(0, Math.min(getWidth() - ICON_WIDTH, centred));
		}

		int[] rows = stackRows(lefts, ICON_WIDTH + ICON_GAP, MAX_ICON_ROWS);
		iconRows = 0;

		for (int row : rows)
		{
			iconRows = Math.max(iconRows, row + 1);
		}

		// Row 0 is the one nearest the plot, so the usual single drop sits right over its delve.
		dropBounds = new Rectangle[drops.size()];
		int nearest = countTop() - ICON_GAP - ICON_HEIGHT;

		for (int i = 0; i < drops.size(); i++)
		{
			int y = nearest - rows[i] * (ICON_HEIGHT + ICON_GAP);
			dropBounds[i] = new Rectangle(lefts[i], y, ICON_WIDTH, ICON_HEIGHT);
		}
	}

	/**
	 * Which row of the lane each icon goes in: the lowest one it fits in without overlapping an
	 * icon already there, or the last row when none has room.
	 *
	 * @param lefts   where each icon starts, in any order
	 * @param spacing how far along one icon has to start from the one before it to not overlap it
	 */
	static int[] stackRows(int[] lefts, int spacing, int maxRows)
	{
		Integer[] order = new Integer[lefts.length];

		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}

		Arrays.sort(order, (a, b) -> Integer.compare(lefts[a], lefts[b]));

		int[] rows = new int[lefts.length];
		int[] free = new int[maxRows];
		Arrays.fill(free, Integer.MIN_VALUE);

		for (int i : order)
		{
			int row = maxRows - 1;

			for (int r = 0; r < maxRows; r++)
			{
				if (lefts[i] >= free[r])
				{
					row = r;
					break;
				}
			}

			rows[i] = row;
			free[row] = lefts[i] + spacing;
		}

		return rows;
	}

	/** Every drop's icon, with a hairline down to the delve it is over. */
	private void drawDrops(Graphics2D g2)
	{
		List<RunDetail.Drop> drops = detail.drops();

		// The hairlines first, so an icon stacked over another is never crossed out by its line.
		g2.setStroke(HAIRLINE);
		g2.setColor(DROP_TICK);

		for (int i = 0; i < drops.size(); i++)
		{
			Rectangle at = dropBounds[i];
			int x = xFor(drops.get(i).level);
			g2.drawLine(x, at.y + at.height, x, countTop());
		}

		for (int i = 0; i < drops.size(); i++)
		{
			drawDrop(g2, drops.get(i), dropBounds[i]);
		}
	}

	private void drawDrop(Graphics2D g2, RunDetail.Drop drop, Rectangle at)
	{
		Composite composite = g2.getComposite();

		if (!drop.kept)
		{
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, LOST_ALPHA));
		}

		BufferedImage image = drop.isUnknown() ? UNKNOWN_ICON : itemIcons.apply(drop.itemId);

		if (image != null && image.getWidth() > 0 && image.getHeight() > 0)
		{
			// Centred in its slot, and never enlarged: a sprite is pixel art, and one to one is how
			// the game draws it. One larger than the slot is fitted rather than stretched.
			double scale = Math.min(1, Math.min((double) at.width / image.getWidth(),
				(double) at.height / image.getHeight()));
			int width = (int) Math.round(image.getWidth() * scale);
			int height = (int) Math.round(image.getHeight() * scale);

			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.drawImage(image, at.x + (at.width - width) / 2, at.y + (at.height - height) / 2,
				width, height, null);
		}
		else
		{
			// Nothing to draw it with - no game to take an icon from. A box with the item's initial
			// keeps the drop on the chart and legible.
			FontMetrics metrics = g2.getFontMetrics();
			String initial = drop.name.isEmpty() ? "?" : drop.name.substring(0, 1);

			g2.setStroke(HAIRLINE);
			g2.setColor(LABEL_COLOR);
			g2.drawRoundRect(at.x + 2, at.y + 1, at.width - 5, at.height - 3, 6, 6);
			g2.drawString(initial, at.x + (at.width - metrics.stringWidth(initial)) / 2,
				at.y + (at.height + metrics.getAscent()) / 2 - 1);
		}

		if (drop.quantity > 1)
		{
			// Where the game puts a stack's count, and how: yellow over a shadow.
			String count = Integer.toString(drop.quantity);
			int baseline = at.y + g2.getFontMetrics().getAscent() - 2;

			g2.setColor(Color.BLACK);
			g2.drawString(count, at.x + 1, baseline + 1);
			g2.setColor(STACK_COLOR);
			g2.drawString(count, at.x, baseline);
		}

		g2.setComposite(composite);
	}

	/** The drop whose icon is under the pointer, or null for none. */
	private RunDetail.Drop dropAt(int px, int py)
	{
		List<RunDetail.Drop> drops = detail.drops();

		// Last drawn is on top, so it is the one the pointer is over where two overlap.
		for (int i = Math.min(drops.size(), dropBounds.length) - 1; i >= 0; i--)
		{
			if (dropBounds[i].contains(px, py))
			{
				return drops.get(i);
			}
		}

		return null;
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		RunDetail.Drop drop = dropAt(event.getX(), event.getY());

		if (drop == null)
		{
			return null;
		}

		if (drop.isUnknown())
		{
			return "<html>" + drop.name + " - delve " + drop.level + "<br>"
				+ RunDetail.UNKNOWN_UNIQUE_CANDIDATES
				+ (drop.kept ? "" : "<br>" + lostHow(detail)) + "</html>";
		}

		String name = drop.quantity > 1 ? drop.quantity + " x " + drop.name : drop.name;
		return drop.kept
			? name + " - delve " + drop.level
			: "<html>" + name + " - delve " + drop.level + "<br>" + lostHow(detail) + "</html>";
	}

	/**
	 * How a drop that never left with the run was lost - to a death, or left in the pile - for the
	 * line under it on the chart and in the drops list alike.
	 */
	static String lostHow(RunDetail detail)
	{
		return detail.diedOn() > 0 ? "Lost when you died" : "Lost when the run ended unclaimed";
	}

	/** The delve nearest the pointer, or 0 when the pointer is outside either plot. */
	private int nearestDelve(int px, int py)
	{
		boolean inCounts = py >= countTop() && py <= countBottom();
		boolean inStrip = py >= stripTop() && py <= stripBottom();

		if (detail.isEmpty() || px < left() || px > right() || !(inCounts || inStrip))
		{
			return 0;
		}

		if (deepest <= shallowest)
		{
			return deepest;
		}

		double fraction = (double) (px - left()) / Math.max(1, right() - left());
		int level = shallowest + (int) Math.round(fraction * (deepest - shallowest));
		return Math.max(shallowest, Math.min(deepest, level));
	}

	// -- axis arithmetic --------------------------------------------------------------------

	/**
	 * Where an axis stops: the next gridline clear of {@code highest}.
	 *
	 * <p>Clear of it rather than on it. A figure landing exactly on a round number is common - a
	 * run of delves that all took two minutes - and an axis that stopped there would draw the line
	 * along the top edge of the plot, where it reads as clipped and runs into the caption above it.
	 */
	static int topFor(int highest, int step)
	{
		int top = ceilTo(highest, step);
		return top <= highest ? top + step : Math.max(step, top);
	}

	/** The next multiple of {@code step} at or above {@code value}. */
	static int ceilTo(int value, int step)
	{
		if (step <= 0 || value <= 0)
		{
			return 0;
		}

		int over = value % step;
		return over == 0 ? value : value + step - over;
	}

	/**
	 * The smallest of 1, 2, 5, 10, 20, 50, 100 and so on that divides {@code span} into no more
	 * than {@code maxTicks} intervals - a gap between gridlines that is both round and uncrowded.
	 */
	static int niceStep(int span, int maxTicks)
	{
		for (int decade = 1; ; decade *= 10)
		{
			for (int mantissa : new int[]{1, 2, 5})
			{
				int step = mantissa * decade;

				if (span / step <= maxTicks)
				{
					return step;
				}
			}
		}
	}

	/**
	 * The same for a clock, off {@link #TIME_STEPS} - so gridlines land on the spans a reader
	 * would pick, rather than on {@code 0:50} and {@code 1:40}. The longest step is used for
	 * anything past it, since a delve slow enough to need one is off any scale worth drawing.
	 */
	static int timeStep(int spanSeconds, int maxTicks)
	{
		for (int step : TIME_STEPS)
		{
			if (spanSeconds / step <= maxTicks)
			{
				return step;
			}
		}

		return TIME_STEPS[TIME_STEPS.length - 1];
	}
}
