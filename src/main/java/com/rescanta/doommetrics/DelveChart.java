package com.rescanta.doommetrics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One run, delve by delve: what each of the counters gave back on each delve, and how long each
 * delve took.
 *
 * <p>Two plots, stacked, sharing one delve axis. The upper one carries the eight counters, a line
 * each. The lower one carries the clock: the delve's segment and, under it, the fight the game
 * timed, with the band between them - the restocking, the walk in, the drop down the hole - filled.
 * They are two plots rather than one with two scales, because a second axis is two charts drawn on
 * top of each other and called one, and the alignment between the two scales would be arbitrary.
 * Sharing the x axis is what lets a slow delve be read against a quiet one without inventing a
 * relationship between seconds and hitpoints.
 *
 * <p>The eight counters do share their axis, and are three different units doing it - hitpoints,
 * prayer points and damage. That is only legible because of a fact about this fight rather than a
 * fact about charts: none of them clears a few hundred on a single delve, so all eight sit in one
 * band and the reader is comparing like sizes. It would be indefensible over a whole run, where
 * damage runs twenty times the healing, which is why the totals beside the chart are drawn as
 * meters scaled per unit instead. Which unit a line is counted in is on the legend, under the
 * heading the line is listed beneath.
 *
 * <p>Lines rather than dots, because the deepest runs go past three hundred delves and at that
 * width a dot per delve per series is a smear. Markers are drawn only when the delves are far
 * enough apart to hit.
 *
 * <p>Swing thread only.
 */
class DelveChart extends JPanel
{
	/** The delve axis, and the run's own clock, drawn a shade off the plot. */
	private static final Color GRID_COLOR = new Color(44, 44, 42);
	private static final Color AXIS_COLOR = new Color(56, 56, 53);
	private static final Color LABEL_COLOR = ColorScheme.LIGHT_GRAY_COLOR;

	/** The segment a delve took, and the fight the game timed inside it. */
	private static final Color SEGMENT_COLOR = new Color(0xC3C2B7);
	private static final Color FIGHT_COLOR = new Color(0x898781);

	/** The time between the two, which is the time not spent fighting. */
	private static final Color DOWNTIME_FILL = new Color(0xC3, 0xC2, 0xB7, 44);

	/** The delve under the pointer. */
	private static final Color CROSSHAIR_COLOR = new Color(0xFF, 0xFF, 0xFF, 90);

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
	 * not at all on the next - and past a couple of hundred delves eight lines of that fill in as
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

	private RunDetail detail = RunDetail.empty();

	/** Counters the reader has switched off. Never repainted for the ones left on - see below. */
	private Set<CombatMetric> hidden = EnumSet.noneOf(CombatMetric.class);

	/** The counter being pointed at in the legend, brought forward, or null for none. */
	private CombatMetric emphasis;

	/** The delve under the pointer, or 0 when the pointer is off the plot. */
	private int hovered;

	/** Told which delve is under the pointer, so the legend can read out that delve's figures. */
	private IntConsumer onHover = level ->
	{
	};

	private int deepest;

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
				hover(nearestDelve(event.getX(), event.getY()));
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
		this.window = windowFor(detail.delves().size());
		this.hovered = 0;
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
	void setHidden(Set<CombatMetric> hidden)
	{
		this.hidden = hidden;
		rescale();
		repaint();
	}

	/** @param emphasis the counter to bring forward, or null to draw all of them level */
	void setEmphasis(CombatMetric emphasis)
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
			for (CombatMetric metric : CombatMetric.values())
			{
				if (!hidden.contains(metric))
				{
					highest = (int) Math.max(highest, delve.combat.get(metric));
				}
			}

			longest = Math.max(longest, (int) delve.segment.getSeconds());
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
				drawEmpty(g2);
				return;
			}

			drawCountGrid(g2);
			drawTimeGrid(g2);
			drawDelveAxis(g2);
			drawTimes(g2);
			drawCounters(g2);
			drawTimeLegend(g2);
			drawCrosshair(g2);
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
			// happened, and not the same as a run that has not banked its first delve yet.
			text = "This run banked no delves.";
		}
		else
		{
			text = "No delve banked yet.";
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
		return PAD_TOP;
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
		if (deepest <= 1)
		{
			return (left() + right()) / 2;
		}

		return left() + (int) Math.round(
			(double) (level - 1) / (deepest - 1) * (right() - left()));
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

		// What the numbers up the side are. Not a unit, because three of them share this axis -
		// which unit a line is counted in is on the legend, under its heading. The averaging is
		// named here rather than left to be inferred from the shape of the lines.
		g2.setColor(LABEL_COLOR);
		g2.drawString(window > 0
			? "Counted per delve - bold lines are a " + window + " delve average"
			: "Counted per delve", left(), countTop() - 8);
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

		int step = niceStep(deepest, DELVE_TICKS);

		for (int level = step; level <= deepest; level += step)
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
	 * The clock: the segment each delve took, the fight the game timed inside it, and the band
	 * between them filled - which is the restocking, the walk in and the drop down the hole.
	 *
	 * <p>Drawn in ink rather than in a colour of its own. There are eight colours on this window
	 * already and every one of them names a counter, so a ninth would be read as a ninth counter.
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
			int y = yForTime(delve.segment.getSeconds());

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
			Duration fight = delve.fight == null ? delve.segment : delve.fight;
			int y = yForTime(Math.min(fight.getSeconds(), delve.segment.getSeconds()));

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

		g2.setColor(SEGMENT_COLOR);
		g2.draw(segments);
	}

	/**
	 * The counters, one line each, in the order they are declared - so an emphasised line is drawn
	 * last and lands over the rest rather than under whichever happens to follow it.
	 */
	private void drawCounters(Graphics2D g2)
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			if (metric != emphasis)
			{
				drawCounter(g2, metric);
			}
		}

		if (emphasis != null)
		{
			drawCounter(g2, emphasis);
		}
	}

	private void drawCounter(Graphics2D g2, CombatMetric metric)
	{
		if (hidden.contains(metric))
		{
			return;
		}

		List<RunDetail.Delve> delves = detail.delves();
		long[] values = new long[delves.size()];

		for (int i = 0; i < delves.size(); i++)
		{
			values[i] = delves.get(i).combat.get(metric);
		}

		boolean front = emphasis == null || emphasis == metric;
		Color color = front ? metric.seriesColor() : dim(metric.seriesColor());
		Stroke stroke = emphasis == metric ? EMPHASIS_STROKE : SERIES_STROKE;

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
		return deepest <= 1 || (right() - left()) / (deepest - 1) >= MARKER_SPACING;
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
	 */
	static int windowFor(int delves)
	{
		return delves < TREND_FROM_DELVES ? 0 : Math.max(9, Math.min(25, delves / 10));
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
	 * to name all eight and their figures beside them. These two have nowhere else to be named.
	 */
	private void drawTimeLegend(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		int y = stripTop() - 7;
		int x = left();

		g2.setStroke(TIME_STROKE);
		x = timeLegendEntry(g2, metrics, SEGMENT_COLOR, "Delve time", x, y);
		timeLegendEntry(g2, metrics, FIGHT_COLOR, "Fight", x, y);
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
	 * The delve under the pointer, marked across both plots and named at the top.
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

		String text = "Delve " + hovered;
		FontMetrics metrics = g2.getFontMetrics();
		int width = metrics.stringWidth(text);
		// Flipped to the inside at the right hand edge, where it would otherwise run off the plot.
		int at = Math.min(x + 5, right() - width);

		g2.setColor(LABEL_COLOR);
		g2.drawString(text, Math.max(left(), at), countTop() - 8 + metrics.getAscent() - 8);
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

		if (deepest <= 1)
		{
			return deepest;
		}

		double fraction = (double) (px - left()) / Math.max(1, right() - left());
		int level = 1 + (int) Math.round(fraction * (deepest - 1));
		return Math.max(1, Math.min(deepest, level));
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
