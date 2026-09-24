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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One run, delve by delve: the counters on an upper plot, the delve times on a strip under it,
 * sharing the delve axis. Swing thread only.
 */
class DelveChart extends JPanel
{
	private static final Color GRID_COLOR = new Color(44, 44, 42);
	private static final Color AXIS_COLOR = new Color(56, 56, 53);
	private static final Color LABEL_COLOR = ColorScheme.LIGHT_GRAY_COLOR;

	private static final Color FULL_TIME_COLOR = new Color(0xC3C2B7);
	private static final Color FIGHT_COLOR = new Color(0x898781);

	/** The wait after the kill. */
	private static final Color DOWNTIME_FILL = new Color(0xC3, 0xC2, 0xB7, 44);

	private static final Color CROSSHAIR_COLOR = new Color(0xFF, 0xFF, 0xFF, 90);

	/** The hover readout names its figures in the game's orange, so they stand off the values. */
	private static final Color READOUT_LABEL_COLOR = DoomColors.ORANGE;

	private static final Color READOUT_VALUE_COLOR = DoomColors.PLAIN;

	private static final int READOUT_GAP = 12;

	private static final String FULL_TIME = "Full time";
	private static final String KILL_TIME = "Kill time";

	private static final BasicStroke SERIES_STROKE =
		new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final BasicStroke EMPHASIS_STROKE =
		new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final float[] DASH = {6f, 5f};

	private static final BasicStroke DASHED_STROKE =
		new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, DASH, 0f);

	private static final BasicStroke DASHED_EMPHASIS_STROKE =
		new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, DASH, 0f);

	private static final BasicStroke TIME_STROKE =
		new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final BasicStroke HAIRLINE = new BasicStroke(1f);

	private static final int DIMMED_ALPHA = 55;

	private static final int RAW_ALPHA = 60;

	private static final int PAD_LEFT = 48;
	private static final int PAD_RIGHT = 14;

	private static final int PAD_TOP = 24;

	private static final int PAD_BOTTOM = 32;

	private static final int PLOT_GAP = 26;

	/** Fixed, so widening the window grows the counters. */
	private static final int STRIP_HEIGHT = 64;

	private static final int MIN_PLOT_HEIGHT = 90;

	private static final int MARKER_SPACING = 9;

	private static final int MARKER = 5;

	/** Gridlines wanted, before rounding to a nice step. */
	private static final int COUNT_TICKS = 6;

	private static final int TIME_TICKS = 2;

	private static final int DELVE_TICKS = 10;

	private RunDetail detail = RunDetail.empty();

	/** The delves the counters are drawn over: none cleared while the plugin was off. */
	private List<RunDetail.Delve> counted = detail.watchedDelves();

	/** One line per counter, or per heading when grouped. */
	private List<CombatSeries> series = CombatSeries.drawn(false);

	private Set<CombatSeries> hidden = new HashSet<>();

	/** Brought forward, or null for none. */
	private CombatSeries emphasis;

	/** The delve under the pointer, or 0. */
	private int hovered;

	private IntConsumer onHover = level ->
	{
	};

	private int deepest;

	/** The delve at the left edge: the first delve seen for a joined run. */
	private int shallowest = 1;

	/** Delves averaged over, or 0 for none. */
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

	void setHoverListener(IntConsumer onHover)
	{
		this.onHover = onHover;
	}

	void setDetail(RunDetail detail)
	{
		this.detail = detail;
		this.deepest = detail.deepest();
		this.shallowest = detail.shallowest();
		this.counted = detail.watchedDelves();
		this.window = ChartMath.windowFor(counted.size());
		this.hovered = 0;
		rescale();
		repaint();
	}

	void setGrouped(boolean grouped)
	{
		series = CombatSeries.drawn(grouped);
		rescale();
		repaint();
	}

	void setHidden(Set<CombatSeries> hidden)
	{
		this.hidden = hidden;
		rescale();
		repaint();
	}

	void setEmphasis(CombatSeries emphasis)
	{
		if (this.emphasis == emphasis)
		{
			return;
		}

		this.emphasis = emphasis;
		repaint();
	}

	/** Counters scale to the visible series; the time strip scales to the whole run. */
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

		countStep = Math.max(1, ChartMath.niceStep(highest, COUNT_TICKS));
		countMax = ChartMath.topFor(highest, countStep);

		timeStep = ChartMath.timeStep(longest, TIME_TICKS);
		timeMax = ChartMath.topFor(longest, timeStep);
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
			text = "This run cleared no delves.";
		}
		else
		{
			text = "No delves cleared yet.";
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
	 * The caption on the left and the hovered delve's readout on the right; the caption gives way
	 * when there is no room for both.
	 */
	private void drawHeader(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		int baseline = PAD_TOP - 8;

		String caption = window > 0
			? "Counted per delve - bold lines are a " + window + "-delve average"
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
			g2.setColor(READOUT_LABEL_COLOR);
			g2.drawString(part[0], x, baseline);
			x += metrics.stringWidth(part[0]);

			g2.setColor(READOUT_VALUE_COLOR);
			g2.drawString(part[1], x, baseline);
			x += metrics.stringWidth(part[1]) + READOUT_GAP;
		}
	}

	/** Label and value pairs for the hovered delve, or none. */
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
			// An even share of a stretch the plugin did not watch.
			{FULL_TIME + " ", (at.estimated ? "~" : "") + DoomFormat.duration(at.fullTime)},
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

	private void drawDelveAxis(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		int bottom = stripBottom();

		g2.setStroke(HAIRLINE);
		g2.setColor(AXIS_COLOR);
		g2.drawLine(left(), bottom, right(), bottom);
		g2.drawLine(left(), countBottom(), right(), countBottom());

		int step = ChartMath.niceStep(deepest - shallowest + 1, DELVE_TICKS);

		for (int level = ChartMath.ceilTo(shallowest, step); level <= deepest; level += step)
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
	 * Full time and kill time, with the wait between them filled. Ink, not a colour: every colour
	 * here names a counter.
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

		// Back along the fight times, closing the band.
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

	/** The emphasised line is drawn last, on top. */
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

		List<RunDetail.Delve> delves = counted;
		long[] values = new long[delves.size()];

		for (int i = 0; i < delves.size(); i++)
		{
			values[i] = line.amount(delves.get(i).combat);
		}

		boolean front = emphasis == null || emphasis == line;
		Color color = front ? line.seriesColor() : dim(line.seriesColor());
		Stroke stroke = line.dashed()
			? (emphasis == line ? DASHED_EMPHASIS_STROKE : DASHED_STROKE)
			: (emphasis == line ? EMPHASIS_STROKE : SERIES_STROKE);

		if (window > 0)
		{
			// The raw line underneath the average.
			g2.setColor(fade(color, RAW_ALPHA));
			g2.setStroke(HAIRLINE);
			g2.draw(line(delves, values));

			g2.setColor(color);
			g2.setStroke(stroke);
			g2.draw(line(delves, ChartMath.rollingAverage(values, window)));
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

	private Path2D.Double line(List<RunDetail.Delve> delves, long[] values)
	{
		double[] exact = new double[values.length];

		for (int i = 0; i < values.length; i++)
		{
			exact[i] = values[i];
		}

		return line(delves, exact);
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

	/** Names the time strip's two lines. The counters' legend is the table beside the chart. */
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

	/** The delve nearest the pointer, or 0 outside both plots. */
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
}
