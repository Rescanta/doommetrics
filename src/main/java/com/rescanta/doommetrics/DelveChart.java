package com.rescanta.doommetrics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One figure per run over a character's whole history: one dot per run, with a rolling average
 * through them.
 *
 * <p>Which figure is the reader's choice - how deep the run got, how much the blood barrages healed
 * for, how much the Zaryte specs hit for. The chart does not know which of those it is holding; the
 * {@link ChartSeries} it is given carries its own axis, so the only thing that changes when the
 * reader picks another metric is the numbers up the side.
 *
 * <p>A plain line through every run is unreadable past a few hundred points - it fills in as a
 * solid band and the trend disappears into it. Dots let the density show through, and the average
 * carries the trend the dots no longer can.
 *
 * <p>The two colours were validated against this surface for lightness, chroma, contrast and
 * colour-blind separation rather than picked by eye: the worst of the protan and deutan
 * separations is 25.9 against a target of 8, and normal vision sees 29.5 against a floor of 15.
 * They are a step down from the RuneLite tokens they are drawn from, which sit above the dark
 * theme's lightness band. Identity does not rest on colour alone either way - one series is dots
 * and the other is a line.
 */
class DelveChart extends JPanel
{
	/** Per-run dots. A darkened cousin of {@link ColorScheme#GRAND_EXCHANGE_LIMIT}. */
	private static final Color RUN_COLOR = new Color(0x2E8FE0);

	/** The rolling average. A darkened {@link ColorScheme#BRAND_ORANGE}. */
	private static final Color TREND_COLOR = new Color(0xC97D00);

	private static final Color GRID_COLOR = new Color(48, 48, 48);
	private static final Color AXIS_COLOR = ColorScheme.MEDIUM_GRAY_COLOR;

	/** Dots overlap heavily on a long history, so they are drawn to let density read through. */
	private static final Color RUN_FILL =
		new Color(RUN_COLOR.getRed(), RUN_COLOR.getGreen(), RUN_COLOR.getBlue(), 170);

	private static final int DOT = 5;
	private static final BasicStroke TREND_STROKE =
		new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final int PAD_LEFT = 52;
	private static final int PAD_RIGHT = 12;
	private static final int PAD_TOP = 34;
	private static final int PAD_BOTTOM = 34;

	/** How close the pointer must get to a run before its tooltip is offered. */
	private static final int HOVER_RADIUS = 24;

	/** Below this many runs the dots stand on their own and no trend is drawn through them. */
	private static final int MIN_RUNS_FOR_TREND = 30;

	private ChartSeries series = ChartSeries.empty();
	private List<Integer> values = Collections.emptyList();
	private double[] trend = new double[0];
	private int window;
	private int yMin;
	private int yMax = 1;
	private int yStep = 1;

	DelveChart()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(640, 320));
		// Dots are far smaller than a hit target, so the tooltip is answered by nearest point.
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	/** @param series the value to plot for each recorded run, oldest first */
	void setSeries(ChartSeries series)
	{
		this.series = series;
		this.values = series.values();
		this.window = windowFor(values.size());
		this.trend = window == 0 ? new double[0] : rollingAverage(values, window);

		if (!values.isEmpty())
		{
			int low = series.axis().isFromZero() ? 0 : Collections.min(values);
			int high = Math.max(low, Collections.max(values));
			this.yStep = Math.max(series.axis().stepFloor(), niceStep(high - low, 8));

			// Both ends land on a gridline, which also keeps the deepest run off the very top of
			// the plot where it would otherwise sit under the legend.
			this.yMin = Math.max(0, low - Math.floorMod(low, yStep));
			this.yMax = Math.max(yMin + yStep, high + (yStep - Math.floorMod(high, yStep)) % yStep);
		}

		revalidate();
		repaint();
	}

	/**
	 * How many runs the average is taken over, or 0 for a history too short to draw a trend
	 * through.
	 *
	 * <p>The window widens with the history so the line stays a trend rather than tracking the
	 * dots: a couple of hundred runs average over about ten, and anything longer settles at fifty
	 * so the line does not flatten into a straight bar. Below thirty runs there is no line at all -
	 * a window narrow enough to fit would sit on top of every dot and hide the thing it claims to
	 * summarise, and at that size the dots are sparse enough to read on their own.
	 */
	static int windowFor(int count)
	{
		return count < MIN_RUNS_FOR_TREND ? 0 : Math.max(5, Math.min(50, count / 20));
	}

	/**
	 * A centred mean over {@code window} runs. Centred rather than trailing so the line sits on
	 * the dots it describes instead of lagging behind them by half a window. The ends average over
	 * whatever is available, which is why the line reaches both edges.
	 */
	static double[] rollingAverage(List<Integer> values, int window)
	{
		double[] out = new double[values.size()];
		int half = window / 2;

		for (int i = 0; i < values.size(); i++)
		{
			int from = Math.max(0, i - half);
			int to = Math.min(values.size() - 1, i + half);
			long sum = 0;

			for (int j = from; j <= to; j++)
			{
				sum += values.get(j);
			}

			out[i] = (double) sum / (to - from + 1);
		}

		return out;
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

			if (values.isEmpty())
			{
				drawEmpty(g2);
				return;
			}

			drawGrid(g2);
			drawRuns(g2);
			drawTrend(g2);
			drawLegend(g2);
		}
		finally
		{
			g2.dispose();
		}
	}

	private void drawEmpty(Graphics2D g2)
	{
		String text = "No finished runs recorded yet.";
		FontMetrics metrics = g2.getFontMetrics();
		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g2.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2, getHeight() / 2);
	}

	private void drawGrid(Graphics2D g2)
	{
		int left = PAD_LEFT;
		int right = getWidth() - PAD_RIGHT;
		int top = PAD_TOP;
		int bottom = getHeight() - PAD_BOTTOM;
		FontMetrics metrics = g2.getFontMetrics();

		for (int value = yMin; value <= yMax; value += yStep)
		{
			int y = yFor(value);

			g2.setColor(GRID_COLOR);
			g2.drawLine(left, y, right, y);

			String text = series.axis().gridLabel(value);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawString(text, left - 6 - metrics.stringWidth(text), y + metrics.getAscent() / 2);
		}

		g2.setColor(AXIS_COLOR);
		g2.drawLine(left, top, left, bottom);
		g2.drawLine(left, bottom, right, bottom);

		int step = xLabelStep(values.size());

		for (int run = step; run <= values.size(); run += step)
		{
			int x = xFor(run - 1);
			String text = Integer.toString(run);
			g2.setColor(AXIS_COLOR);
			g2.drawLine(x, bottom, x, bottom + 3);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawString(text, x - metrics.stringWidth(text) / 2, bottom + 4 + metrics.getAscent());
		}

		String caption = "Run";
		g2.drawString(caption, (left + right - metrics.stringWidth(caption)) / 2,
			getHeight() - 2);
	}

	private static int xLabelStep(int count)
	{
		return niceStep(count, 10);
	}

	/**
	 * The smallest of 1, 2, 5, 10, 20, 50, 100 and so on that divides {@code span} into no more
	 * than {@code maxTicks} intervals - a gap between labels that is both round and uncrowded.
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

	private void drawRuns(Graphics2D g2)
	{
		g2.setColor(RUN_FILL);

		for (int i = 0; i < values.size(); i++)
		{
			g2.fillOval(xFor(i) - DOT / 2, yFor(values.get(i)) - DOT / 2, DOT, DOT);
		}
	}

	private void drawTrend(Graphics2D g2)
	{
		if (trend.length < 2)
		{
			return;
		}

		Path2D.Double path = new Path2D.Double();
		path.moveTo(xFor(0), yFor(trend[0]));

		for (int i = 1; i < trend.length; i++)
		{
			path.lineTo(xFor(i), yFor(trend[i]));
		}

		g2.setColor(TREND_COLOR);
		g2.setStroke(TREND_STROKE);
		g2.draw(path);
	}

	/**
	 * Two series means a legend, always. It also names the averaging window, which is the one
	 * thing about the orange line a reader cannot work out from looking at it. With no trend line
	 * there is only one series, and the metric and run count stand alone as a caption.
	 */
	private void drawLegend(Graphics2D g2)
	{
		FontMetrics metrics = g2.getFontMetrics();
		// Clear of the plot, so a run at the very top of the range cannot sit under the legend.
		int y = PAD_TOP - 16;
		int x = PAD_LEFT;

		g2.setColor(RUN_COLOR);
		g2.fillOval(x, y - DOT, DOT + 2, DOT + 2);
		x += DOT + 8;

		String runs = series.label() + " - " + values.size() + (values.size() == 1 ? " run" : " runs");
		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g2.drawString(runs, x, y);

		if (window == 0)
		{
			return;
		}

		x += metrics.stringWidth(runs) + 16;

		g2.setColor(TREND_COLOR);
		g2.setStroke(TREND_STROKE);
		g2.drawLine(x, y - DOT / 2 - 1, x + 14, y - DOT / 2 - 1);
		x += 20;

		String label = "Average of " + window;
		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g2.drawString(label, x, y);
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		int index = nearestRun(event.getX(), event.getY());
		return index < 0 ? null : series.describe(index);
	}

	/** The run whose dot is closest to the pointer, or -1 if none is within reach. */
	private int nearestRun(int px, int py)
	{
		if (values.isEmpty())
		{
			return -1;
		}

		int best = -1;
		long bestDistance = (long) HOVER_RADIUS * HOVER_RADIUS;

		for (int i = 0; i < values.size(); i++)
		{
			long dx = xFor(i) - px;
			long dy = yFor(values.get(i)) - py;
			long distance = dx * dx + dy * dy;

			if (distance <= bestDistance)
			{
				bestDistance = distance;
				best = i;
			}
		}

		return best;
	}

	private int xFor(int index)
	{
		int left = PAD_LEFT;
		int right = getWidth() - PAD_RIGHT;

		if (values.size() <= 1)
		{
			return (left + right) / 2;
		}

		return left + (int) Math.round((double) index / (values.size() - 1) * (right - left));
	}

	private int yFor(double value)
	{
		int top = PAD_TOP;
		int bottom = getHeight() - PAD_BOTTOM;
		double fraction = (value - yMin) / Math.max(1, yMax - yMin);
		return bottom - (int) Math.round(fraction * (bottom - top));
	}
}
