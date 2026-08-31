package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * One state of the interface, built out of invented numbers rather than out of a game.
 *
 * <p>These are the states worth looking at: the ones that are hard to reach in play (a lifetime of
 * runs behind the chart), fleeting when reached (the seconds after a death), or only interesting
 * for what they do to a layout (seven figure counters, every counter switched off). Having them as
 * data means the widgets can be drawn, judged and changed without a client, a login or a trip into
 * the Doom - see {@link PreviewWindow} for looking at one and {@link PreviewShots} for writing all
 * of them out as images.
 *
 * <p>What a scene cannot show is whether the plugin would ever produce these numbers. The figures
 * are hand-made, so a scene proves a layout and nothing about the tracking behind it - that is
 * what the unit tests are for. Everything is built through the plugin's own types and formatted
 * through {@link DoomFormat}, so what a scene also cannot do is show a shape the real widgets
 * never take.
 */
final class PreviewScene
{
	/** How long a delve at this depth takes, near enough that the pace figures read plausibly. */
	private static Duration delveLength(int level)
	{
		return Duration.ofSeconds(30 + level * 2L);
	}

	final String name;

	/** What this state is here to show, printed beside the picture. */
	final String note;

	final PreviewConfig config;

	/** The run the overlay draws, or null for no run in progress and none lingering. */
	final DelveRun run;

	/** The sitting's tally, without the run in progress - see {@link #panelCombat()}. */
	final CombatTotals session;

	final CombatTotals lifetime;
	final DoomMetricsPanel.Stats stats;
	final List<MilestoneTablePanel.Row> rows;
	final RunSeries series;

	private PreviewScene(String name, String note, PreviewConfig config, DelveRun run,
		CombatTotals session, CombatTotals lifetime, DoomMetricsPanel.Stats stats,
		List<MilestoneTablePanel.Row> rows, RunSeries series)
	{
		this.name = name;
		this.note = note;
		this.config = config;
		this.run = run;
		this.session = session;
		this.lifetime = lifetime;
		this.stats = stats;
		this.rows = rows;
		this.series = series;
	}

	/**
	 * The panel's live rows, or null when there is no run to draw. Read out of whichever config is
	 * driving the preview rather than the scene's own, so a knob turned in the window moves the
	 * side panel and the overlay together.
	 */
	DoomMetricsPanel.Live live(PreviewConfig from)
	{
		return run == null
			? null
			: DoomMetricsPanel.Live.of(run, from.paceMode,
				from.showTargetDelve ? from.targetDelve : 0);
	}

	/** The sitting's figures with the run in progress counted in, as the panel is handed them. */
	CombatTotals panelCombat()
	{
		if (session == null)
		{
			return null;
		}

		return run == null ? session.copy() : session.plus(run.getCombat());
	}

	/** Every state worth a look, in the order they are worth looking at. */
	static List<PreviewScene> all()
	{
		Instant now = Instant.now();

		return Arrays.asList(
			idle(),
			shallow(now),
			deep(now),
			combined(now),
			died(now),
			lingering(now),
			bare(now),
			fresh(now),
			ceiling(now));
	}

	/** One scene by name, for a test that is about a particular state rather than all of them. */
	static PreviewScene named(String name)
	{
		for (PreviewScene scene : all())
		{
			if (scene.name.equals(name))
			{
				return scene;
			}
		}

		throw new IllegalArgumentException("No scene called " + name);
	}

	private static PreviewScene idle()
	{
		return new PreviewScene("idle", "Between runs: no overlay at all, and a panel with only "
			+ "the sitting and the lifetime to report",
			new PreviewConfig(), null, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows(), history(240));
	}

	private static PreviewScene shallow(Instant now)
	{
		return new PreviewScene("shallow", "Four delves in: no deep delve banked yet, so the pace "
			+ "has nothing to report and the counters have barely moved",
			new PreviewConfig(), run(3, now), session(), lifetime(),
			stats(Duration.ofMinutes(4), 0, 4, 1387), rows(), history(240));
	}

	private static PreviewScene deep(Instant now)
	{
		DelveRun run = run(23, now);
		fill(run, 1);

		return new PreviewScene("deep", "The ordinary mid-run state, every counter on its own line",
			new PreviewConfig(), run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows(), history(240));
	}

	private static PreviewScene combined(Instant now)
	{
		DelveRun run = run(23, now);
		fill(run, 1);

		PreviewConfig config = new PreviewConfig();
		config.grouping = MetricDisplay.COMBINED;
		config.paceMode = PaceMode.RUN_THROUGHPUT;

		return new PreviewScene("combined", "The same run with the counters folded into their "
			+ "groups, and run pace in place of deep pace",
			config, run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows(), history(240));
	}

	private static PreviewScene died(Instant now)
	{
		DelveRun run = run(31, now);
		fill(run, 2);
		run.end(EndReason.DIED, now, 32);

		return new PreviewScene("died", "The seconds after a death, when the overlay grows a row "
			+ "and the delve row changes what it is counting",
			new PreviewConfig(), run, session(), lifetime(),
			stats(Duration.ofMinutes(112), 65, 141, 1387), rows(), history(240));
	}

	private static PreviewScene lingering(Instant now)
	{
		DelveRun run = run(27, now);
		fill(run, 2);
		run.end(EndReason.FINISHED, now, 0);

		return new PreviewScene("lingering", "A run walked out of, still up for the linger "
			+ "minutes: cleared rather than died, and a clock that has stopped",
			new PreviewConfig(), run, session(), lifetime(),
			stats(Duration.ofMinutes(104), 58, 128, 1387), rows(), history(240));
	}

	private static PreviewScene bare(Instant now)
	{
		DelveRun run = run(23, now);
		fill(run, 1);

		PreviewConfig config = new PreviewConfig();
		config.allCounters(false);
		config.showPace = false;

		return new PreviewScene("bare", "Everything optional switched off, which is the narrowest "
			+ "the overlay ever gets",
			config, run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows(), history(240));
	}

	private static PreviewScene fresh(Instant now)
	{
		return new PreviewScene("fresh", "A character with nothing behind them: empty tables, an "
			+ "empty chart, and rates with nothing to average",
			new PreviewConfig(), run(1, now), new CombatTotals(), new CombatTotals(),
			new DoomMetricsPanel.Stats(DoomFormat.duration(Duration.ofMinutes(2)),
				DoomFormat.pace(null), "Nothing banked yet", "0",
				DoomFormat.pace(null), "Nothing banked yet", null),
			Collections.emptyList(), RunSeries.empty());
	}

	/**
	 * Every counter at the most a single run can put on one: five figures.
	 *
	 * <p>Nothing a delve can do puts more than that on one source - the deepest runs heal for a few
	 * thousand - so this is the widest figure the overlay ever has to fit beside a label, and the
	 * state its labels are chosen against.
	 */
	private static PreviewScene ceiling(Instant now)
	{
		DelveRun run = run(23, now);

		for (CombatMetric metric : CombatMetric.values())
		{
			run.recordCombat(metric, 99_999);
		}

		// A lifetime, unlike a run, really does reach seven figures, and the panel has to hold it.
		CombatTotals lifetime = new CombatTotals();

		for (CombatMetric metric : CombatMetric.values())
		{
			lifetime.add(metric, 9_481_255L);
		}

		return new PreviewScene("ceiling", "Every counter at the widest a run can make it, which "
			+ "is what the overlay labels have to fit beside",
			new PreviewConfig(), run, lifetime.copy(), lifetime,
			stats(Duration.ofHours(11), 486, 660, 41_920), rows(120), history(2400));
	}

	/**
	 * A run that has cleared its way down to {@code reached} and is twenty seconds into the next
	 * delve, with the splits behind it that the pace figures are averaged over.
	 */
	private static DelveRun run(int reached, Instant now)
	{
		Duration total = Duration.ofSeconds(20);

		for (int level = 1; level <= reached; level++)
		{
			total = total.plus(delveLength(level));
		}

		Instant at = now.minus(total);
		DelveRun run = new DelveRun(at, 1, false);

		for (int level = 1; level <= reached; level++)
		{
			at = at.plus(delveLength(level));
			run.complete(level, at, delveLength(level));
		}

		return run;
	}

	/**
	 * Credits a run with what its gear would have given back, scaled by {@code weight}.
	 *
	 * <p>One source is left on zero deliberately: a counter that has not fired is drawn differently
	 * from one that has, and a set of scenes where everything is non-zero would never show it.
	 */
	private static void fill(DelveRun run, int weight)
	{
		run.recordCombat(CombatMetric.BLOOD_BARRAGE_HEAL, 806L * weight);
		run.recordCombat(CombatMetric.OTHER_SPELL_HEAL, 124L * weight);
		run.recordCombat(CombatMetric.AGS_HEAL, 58L * weight);
		run.recordCombat(CombatMetric.BLOWPIPE_HEAL, 47L * weight);
		run.recordCombat(CombatMetric.ELDRITCH_PRAYER, 210L * weight);
		run.recordCombat(CombatMetric.ZCB_DAMAGE, 1502L * weight);
		run.recordCombat(CombatMetric.OTHER_SPEC_DAMAGE, 337L * weight);
	}

	/** What the sitting had banked before the run in progress. */
	private static CombatTotals session()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.BLOOD_BARRAGE_HEAL, 2_411);
		totals.add(CombatMetric.OTHER_SPELL_HEAL, 366);
		totals.add(CombatMetric.AGS_HEAL, 174);
		totals.add(CombatMetric.BLOWPIPE_HEAL, 141);
		totals.add(CombatMetric.ELDRITCH_PRAYER, 630);
		totals.add(CombatMetric.ZCB_DAMAGE, 4_506);
		totals.add(CombatMetric.OTHER_SPEC_DAMAGE, 1_011);
		return totals;
	}

	private static CombatTotals lifetime()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.BLOOD_BARRAGE_HEAL, 184_233);
		totals.add(CombatMetric.OTHER_SPELL_HEAL, 21_408);
		totals.add(CombatMetric.AGS_HEAL, 9_611);
		totals.add(CombatMetric.BLOWPIPE_HEAL, 7_842);
		totals.add(CombatMetric.ELDRITCH_PRAYER, 38_150);
		totals.add(CombatMetric.ZCB_DAMAGE, 271_884);
		totals.add(CombatMetric.OTHER_SPEC_DAMAGE, 60_337);
		return totals;
	}

	/**
	 * The sitting's and the character's figures, formatted the way the plugin formats them - which
	 * is why they are built through {@link DelveTotals} rather than written out as strings.
	 */
	private static DoomMetricsPanel.Stats stats(Duration sessionLength, int sessionDeep,
		int sessionMinutes, int lifetimeDeep)
	{
		DelveTotals session = new DelveTotals();
		session.add(sessionDeep, DoomFormat.toTicks(Duration.ofMinutes(sessionMinutes)));

		DelveTotals lifetime = new DelveTotals();
		lifetime.add(lifetimeDeep, DoomFormat.toTicks(Duration.ofMinutes(lifetimeDeep * 3L)));

		return new DoomMetricsPanel.Stats(
			DoomFormat.duration(sessionLength),
			DoomFormat.pace(session.kph()),
			tooltip(session),
			DoomFormat.count(session.deep),
			DoomFormat.pace(lifetime.kph()),
			tooltip(lifetime),
			DoomFormat.count(lifetime.deep));
	}

	/** Mirrors the tooltip the plugin hangs off a rate, so a hover in the preview reads as one. */
	private static String tooltip(DelveTotals totals)
	{
		return totals.isEmpty()
			? "Nothing banked yet"
			: String.format("%d deep %s in %s of run time",
				totals.deep, totals.deep == 1 ? "delve" : "delves",
				DoomFormat.tickDuration(totals.ticks));
	}

	private static List<MilestoneTablePanel.Row> rows()
	{
		return rows(50);
	}

	/** A milestone row every ten delves down to {@code deepest}, one of them freshly beaten. */
	private static List<MilestoneTablePanel.Row> rows(int deepest)
	{
		List<MilestoneTablePanel.Row> rows = new ArrayList<>();
		int kc = 240;

		for (int delve = MilestoneTable.INTERVAL; delve <= deepest; delve += MilestoneTable.INTERVAL)
		{
			rows.add(new MilestoneTablePanel.Row(delve, kc, 1_100 + delve * 210, delve == 30));
			kc = Math.max(1, kc / 3);
		}

		return rows;
	}

	/**
	 * A character's history, deep enough that the chart is drawing more runs than it has pixels.
	 *
	 * <p>Fixed seed, so the same picture comes out of every run of the harness and two of them can
	 * be compared against each other rather than only against memory.
	 */
	private static RunSeries history(int runs)
	{
		Random random = new Random(19_244);
		List<RunRecord> records = new ArrayList<>(runs);

		for (int i = 0; i < runs; i++)
		{
			RunRecord record = new RunRecord();
			// A climb across the character's history, with the spread of a real evening on top.
			int trend = 8 + (i * 22) / Math.max(1, runs);
			record.delve = Math.max(1, trend + random.nextInt(9) - 4);
			record.at = i;
			record.ticks = record.delve * 190;
			record.end = random.nextInt(4) == 0 ? EndReason.FINISHED : EndReason.DIED;
			record.diedOn = record.end == EndReason.DIED ? record.delve + 1 : 0;
			record.combat = new CombatTotals();

			for (CombatMetric metric : CombatMetric.values())
			{
				record.combat.add(metric, (long) record.delve * (12 + random.nextInt(40)));
			}

			records.add(record);
		}

		return RunSeries.of(records);
	}

	/** The name, so a scene can be dropped straight into a picker. */
	@Override
	public String toString()
	{
		return name;
	}
}
