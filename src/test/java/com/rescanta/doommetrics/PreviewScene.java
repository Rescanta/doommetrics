package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.runelite.api.gameval.ItemID;

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
	/**
	 * How long a delve at this depth takes, near enough that the pace figures read plausibly.
	 *
	 * <p>The growth flattens off, because the real fight's does: a delve gets harder as it goes
	 * deeper but the depth is uncapped, and a length that kept climbing linearly would have delve
	 * 350 taking twelve minutes and the deepest scene reporting a pace nobody has ever seen.
	 */
	private static Duration delveLength(int level)
	{
		return Duration.ofSeconds(30 + Math.min(level, 45) * 2L);
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

	private PreviewScene(String name, String note, PreviewConfig config, DelveRun run,
		CombatTotals session, CombatTotals lifetime, DoomMetricsPanel.Stats stats,
		List<MilestoneTablePanel.Row> rows)
	{
		this.name = name;
		this.note = note;
		this.config = config;
		this.run = run;
		this.session = session;
		this.lifetime = lifetime;
		this.stats = stats;
		this.rows = rows;
	}

	/** The run taken apart delve by delve, as the plugin hands it to the detail window. */
	RunDetail detail()
	{
		return RunDetail.of(run);
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
				from.showTargetDelve ? from.targetDelve : 0, from.targetPrediction);
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

	/**
	 * Every kind of line the plugin posts to chat, filled with this scene's run: the one the
	 * interval gives its last cleared delve, the one it would have had as the target, and the
	 * summary at the end - the scene's own ending when the run is over, and both kinds when it is
	 * not. Empty when there is no cleared delve to report.
	 *
	 * <p>Shown whether the interval would have posted them or not: which delves are announced is
	 * {@link ChatAnnouncement#isDue}'s business and tested there, and what is judged here is how
	 * each line reads. Worded from whichever config is driving the preview, like {@link #live}.
	 */
	List<ChatAnnouncement> chat(PreviewConfig from)
	{
		List<ChatAnnouncement> lines = new ArrayList<>();

		if (run == null || run.lastLevel() == 0)
		{
			return lines;
		}

		int last = run.lastLevel();
		lines.add(ChatAnnouncement.delveCleared(run, last, 0, from.paceMode));
		lines.add(ChatAnnouncement.delveCleared(run, last, last, from.paceMode));

		if (run.isFinished())
		{
			lines.add(ChatAnnouncement.runEnded(run, run.getEndReason(), from.paceMode));
		}
		else
		{
			lines.add(ChatAnnouncement.runEnded(run, EndReason.DIED, from.paceMode));
			lines.add(ChatAnnouncement.runEnded(run, EndReason.FINISHED, from.paceMode));
		}

		return lines;
	}

	/** Every state worth a look, in the order they are worth looking at. */
	static List<PreviewScene> all()
	{
		Instant now = Instant.now();

		return Arrays.asList(
			idle(),
			shallow(now),
			deep(now),
			totals(now),
			grid(now),
			died(now),
			lingering(now),
			bare(now),
			fresh(now),
			ceiling(now),
			record(now));
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
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows());
	}

	private static PreviewScene shallow(Instant now)
	{
		return new PreviewScene("shallow", "Four delves in: no deep delve completed yet, so the pace "
			+ "has nothing to report and the counters have barely moved",
			new PreviewConfig(), run(3, now, counters(1)), session(), lifetime(),
			stats(Duration.ofMinutes(4), 0, 4, 1387), rows());
	}

	private static PreviewScene deep(Instant now)
	{
		// A cloth, an eye, and a second eye ten delves later - the second placed where it landed,
		// and nothing on the delves between, which is what the game's warning on every descend
		// must not be read as.
		DelveRun run = run(23, now, counters(1),
			landing(7, ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth"),
			landing(10, ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)"),
			landing(20, ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)"));

		// Aiming for a delve, which is the fullest the run section gets: the two figures at the
		// head of it and three rows under them.
		PreviewConfig config = new PreviewConfig();
		config.showTargetDelve = true;

		return new PreviewScene("deep", "The ordinary mid-run state, every counter on its own line "
			+ "and a delve being aimed for",
			config, run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows());
	}

	private static PreviewScene totals(Instant now)
	{
		DelveRun run = run(23, now, counters(1));

		PreviewConfig config = new PreviewConfig();
		config.allModes(CounterMode.TOTAL);
		config.paceMode = PaceMode.RUN_THROUGHPUT;

		return new PreviewScene("totals", "The same run with every heading drawn as one total, "
			+ "and full pace in place of deep pace",
			config, run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows());
	}

	private static PreviewScene grid(Instant now)
	{
		DelveRun run = run(23, now, counters(1));

		// The compact way to read it: healing as one figure, and the gear that does the work
		// pictured two to a line.
		PreviewConfig config = new PreviewConfig();
		config.mode(CombatMetric.Group.HEALING, CounterMode.TOTAL);
		config.counterStyle = CounterStyle.ICON_GRID;

		return new PreviewScene("grid", "The same run with healing as a total and the prayer and "
			+ "damage counters pictured two to a line",
			config, run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows());
	}

	private static PreviewScene died(Instant now)
	{
		// Whatever made the hole glow off the last delve went with the run: no descend was tried
		// with it in the pile, and no claim reached it, so nothing ever named it. The run's only
		// unique, because a glow over one already known about places nothing - see
		// DelveRun#uniqueSignalled. A named drop lost the same way is the lingering scene.
		DelveRun run = run(31, now, counters(2),
			landing(31, RunLoot.UNKNOWN_UNIQUE, RunLoot.UNKNOWN_UNIQUE_NAME));
		// Died a few seconds into delve 32, which the game had announced like any other.
		run.enterLevel(32, now.minusSeconds(15));
		run.end(EndReason.DIED, now, 32);

		return new PreviewScene("died", "The seconds after a death, when the overlay grows a row "
			+ "and the delve row changes what it is counting",
			new PreviewConfig(), run, session(), lifetime(),
			stats(Duration.ofMinutes(112), 65, 141, 1387), rows());
	}

	private static PreviewScene lingering(Instant now)
	{
		// Claimed on the way out, so the treads are kept.
		DelveRun run = run(27, now, counters(2),
			landing(14, ItemID.AVERNIC_TREADS, "Avernic treads"));
		run.loot().recordLoot(ItemID.AVERNIC_TREADS, "Avernic treads", 1);
		run.end(EndReason.FINISHED, now, 0);

		return new PreviewScene("lingering", "A run walked out of, still up for the linger "
			+ "minutes: cleared rather than died, and a clock that has stopped",
			new PreviewConfig(), run, session(), lifetime(),
			stats(Duration.ofMinutes(104), 58, 128, 1387), rows());
	}

	private static PreviewScene bare(Instant now)
	{
		DelveRun run = run(23, now, counters(1));

		PreviewConfig config = new PreviewConfig();
		config.allModes(CounterMode.OFF);
		config.showPace = false;

		return new PreviewScene("bare", "Everything optional switched off, which is the narrowest "
			+ "the overlay ever gets",
			config, run, session(), lifetime(),
			stats(Duration.ofMinutes(96), 41, 92, 1387), rows());
	}

	private static PreviewScene fresh(Instant now)
	{
		return new PreviewScene("fresh", "A character with nothing behind them: empty tables, an "
			+ "empty chart, and rates with nothing to average",
			new PreviewConfig(), run(1, now, NOTHING), new CombatTotals(), new CombatTotals(),
			new DoomMetricsPanel.Stats(DoomFormat.duration(Duration.ofMinutes(2)),
				DoomFormat.pace(null), "No delves completed yet", "0",
				DoomFormat.pace(null), "No delves completed yet", null, null, null),
			Collections.emptyList());
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
		// Deep enough that the run clock is into the hours, which is the widest that clock ever
		// gets and so the one the panel's tile has to hold.
		long[] counters = new long[CombatMetric.values().length];
		Arrays.fill(counters, 99_999L);

		DelveRun run = run(60, now, counters);

		// A lifetime, unlike a run, really does reach seven figures, and the panel has to hold it.
		CombatTotals lifetime = new CombatTotals();

		for (CombatMetric metric : CombatMetric.values())
		{
			lifetime.add(metric, 9_481_255L);
		}

		// The same rows the deep scene has, so the two are comparable line for line - which is
		// what DoomMetricsOverlayTest measures the widest figures against. Aimed at the deepest
		// target there is, both because it is the longest wait and because a target already behind
		// the run would drop its countdown row and leave the two a line apart.
		PreviewConfig config = new PreviewConfig();
		config.showTargetDelve = true;
		config.targetDelve = DoomMetricsConfig.MAX_DELVE;

		return new PreviewScene("ceiling", "Every counter and every clock at the widest a run can "
			+ "make it, which is what the overlay labels and the panel's tiles have to fit beside",
			config, run, lifetime.copy(), lifetime,
			stats(Duration.ofHours(11), 486, 660, 41_920), rows(120));
	}

	/**
	 * A run past the deepest anybody has taken one, which is what the chart has to stay readable
	 * at: three hundred and fifty delves across a plot a few hundred pixels wide, with eight lines
	 * over each other and the delve markers too close together to draw.
	 */
	private static PreviewScene record(Instant now)
	{
		// Three drops a few delves apart, which at this width is closer than two icons can sit
		// side by side - what the lane stacks into rows for. Died, so every one of them is lost.
		DelveRun run = run(350, now, counters(40),
			landing(40, ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)"),
			landing(88, ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth"),
			landing(91, ItemID.AVERNIC_TREADS, "Avernic treads"),
			landing(95, ItemID.EYE_OF_AYAK_UNCHARGED, "Eye of ayak (uncharged)"),
			landing(160, ItemID.DOMPET, "Dom"),
			landing(301, ItemID.MOKHAIOTL_CLOTH, "Mokhaiotl cloth"));
		run.enterLevel(351, now.minusSeconds(15));
		run.end(EndReason.DIED, now, 351);

		return new PreviewScene("record", "Deeper than the world record: the depth the run detail "
			+ "chart has to stay legible at, and the one where its markers come off",
			new PreviewConfig(), run, session(), lifetime(),
			stats(Duration.ofHours(4), 340, 240, 41_920), rows(350));
	}

	/**
	 * A run that has cleared its way down to {@code reached} and is twenty seconds into the next
	 * delve, with the splits behind it that the pace figures are averaged over, and
	 * {@code counters} spread across those delves.
	 *
	 * <p>Built delve by delve rather than filled afterwards, because that is how the plugin builds
	 * one: a counter is credited to whichever delve was being fought when it fired, so a run whose
	 * figures were added at the end would put every one of them on the delve after the last, and
	 * the chart would draw an empty run with one spike off the end of it.
	 *
	 * <p>The same goes for {@code landings}: each is seen landing in the pile just after the delve
	 * it came off is cleared, which is where the plugin sees one.
	 */
	private static DelveRun run(int reached, Instant now, long[] counters, Landing... landings)
	{
		Duration total = Duration.ofSeconds(20);

		for (int level = 1; level <= reached; level++)
		{
			total = total.plus(delveLength(level));
		}

		Instant at = now.minus(total);
		DelveRun run = new DelveRun(at, 1, false);

		// Fixed seed, so the same picture comes out of every run of the harness and two of them
		// can be compared against each other rather than only against memory.
		Random random = new Random(19_244);
		long[][] plan = plan(reached, counters, random);

		for (int level = 1; level <= reached; level++)
		{
			// As the game announces each delve before it is fought: a delve's counters are filed
			// under the delve just killed until the next one starts.
			if (level > 1)
			{
				run.enterLevel(level, at);
			}

			for (CombatMetric metric : CombatMetric.values())
			{
				run.recordCombat(metric, plan[level - 1][metric.ordinal()], at);
			}

			at = at.plus(delveLength(level));
			run.complete(level, at, fightLength(level, random));

			for (Landing landing : landings)
			{
				if (landing.level == level && landing.itemId == RunLoot.UNKNOWN_UNIQUE)
				{
					run.loot().uniqueSignalled();
				}
				else if (landing.level == level)
				{
					run.loot().sawInPile(landing.itemId, landing.name, run.loot().held(landing.itemId) + 1);
				}
			}
		}

		return run;
	}

	/** A notable drop for a scene's run: what it was, and the delve it came off. */
	private static final class Landing
	{
		private final int level;
		private final int itemId;
		private final String name;

		private Landing(int level, int itemId, String name)
		{
			this.level = level;
			this.itemId = itemId;
			this.name = name;
		}
	}

	private static Landing landing(int level, int itemId, String name)
	{
		return new Landing(level, itemId, name);
	}

	/**
	 * How each counter's run total is spread across the delves that earned it: one row per delve.
	 *
	 * <p>Shaped rather than merely random. A source gives back more per delve the deeper the run
	 * goes, and varies a good deal delve to delve on top of that, and a sixth of delves see a given
	 * source not fire at all - so the chart has a trend to draw, spread around it, and lines that
	 * touch the floor. Pure noise would prove the chart can draw a band and nothing else.
	 *
	 * <p>Every row is scaled so the column sums to exactly what was asked for, with the rounding
	 * given to the largest delve. The run's totals are therefore the ones the overlay and panel
	 * layouts were chosen against, however the shares happen to fall.
	 */
	private static long[][] plan(int reached, long[] counters, Random random)
	{
		long[][] plan = new long[reached][counters.length];

		for (CombatMetric metric : CombatMetric.values())
		{
			int at = metric.ordinal();
			long total = counters[at];

			if (total <= 0)
			{
				continue;
			}

			double[] weights = new double[reached];
			double sum = 0;

			for (int i = 0; i < reached; i++)
			{
				weights[i] = random.nextInt(6) == 0
					? 0
					: (0.55 + 0.9 * i / Math.max(1, reached - 1)) * (0.7 + random.nextDouble() * 0.6);
				sum += weights[i];
			}

			long spent = 0;
			int largest = 0;

			for (int i = 0; i < reached; i++)
			{
				plan[i][at] = sum <= 0 ? 0 : Math.round(total * weights[i] / sum);
				spent += plan[i][at];

				if (plan[i][at] > plan[largest][at])
				{
					largest = i;
				}
			}

			plan[largest][at] = Math.max(0, plan[largest][at] + total - spent);
		}

		return plan;
	}

	/**
	 * The fight the game would have reported inside a segment of {@link #delveLength}: most of it,
	 * with the rest standing for the restocking and the drop down the hole. The gap between the
	 * two is what the time strip fills, so a preview where they were equal would never show it.
	 */
	private static Duration fightLength(int level, Random random)
	{
		Duration segment = delveLength(level);
		return segment.minusSeconds(3 + random.nextInt(12));
	}

	/**
	 * What a run's gear would have given back over the whole trip, scaled by {@code weight}.
	 *
	 * <p>One source is left on zero deliberately: a counter that has not fired is drawn differently
	 * from one that has, and a set of scenes where everything is non-zero would never show it.
	 */
	private static long[] counters(int weight)
	{
		long[] counters = new long[CombatMetric.values().length];
		counters[CombatMetric.BLOOD_BARRAGE_HEAL.ordinal()] = 806L * weight;
		counters[CombatMetric.OTHER_SPELL_HEAL.ordinal()] = 124L * weight;
		counters[CombatMetric.AGS_HEAL.ordinal()] = 58L * weight;
		counters[CombatMetric.BLOWPIPE_HEAL.ordinal()] = 47L * weight;
		counters[CombatMetric.SGS_HEAL.ordinal()] = 36L * weight;
		counters[CombatMetric.ELDRITCH_PRAYER.ordinal()] = 210L * weight;
		counters[CombatMetric.ZCB_DAMAGE.ordinal()] = 1502L * weight;
		counters[CombatMetric.OTHER_SPEC_DAMAGE.ordinal()] = 337L * weight;
		counters[CombatMetric.SCYTHE_PUNISH.ordinal()] = 612L * weight;
		counters[CombatMetric.NOXIOUS_HALBERD_PUNISH.ordinal()] = 158L * weight;
		counters[CombatMetric.CRYSTAL_HALBERD_PUNISH.ordinal()] = 245L * weight;
		counters[CombatMetric.OTHER_MELEE_PUNISH.ordinal()] = 71L * weight;
		return counters;
	}

	/** A run with nothing attributed to it at all. */
	private static final long[] NOTHING = new long[CombatMetric.values().length];

	/** What the sitting had banked before the run in progress. */
	private static CombatTotals session()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.BLOOD_BARRAGE_HEAL, 2_411);
		totals.add(CombatMetric.OTHER_SPELL_HEAL, 366);
		totals.add(CombatMetric.AGS_HEAL, 174);
		totals.add(CombatMetric.BLOWPIPE_HEAL, 141);
		totals.add(CombatMetric.SGS_HEAL, 108);
		totals.add(CombatMetric.ELDRITCH_PRAYER, 630);
		totals.add(CombatMetric.ZCB_DAMAGE, 4_506);
		totals.add(CombatMetric.OTHER_SPEC_DAMAGE, 1_011);
		totals.add(CombatMetric.SCYTHE_PUNISH, 1_836);
		totals.add(CombatMetric.NOXIOUS_HALBERD_PUNISH, 474);
		totals.add(CombatMetric.CRYSTAL_HALBERD_PUNISH, 735);
		totals.add(CombatMetric.OTHER_MELEE_PUNISH, 213);
		return totals;
	}

	private static CombatTotals lifetime()
	{
		CombatTotals totals = new CombatTotals();
		totals.add(CombatMetric.BLOOD_BARRAGE_HEAL, 184_233);
		totals.add(CombatMetric.OTHER_SPELL_HEAL, 21_408);
		totals.add(CombatMetric.AGS_HEAL, 9_611);
		totals.add(CombatMetric.BLOWPIPE_HEAL, 7_842);
		totals.add(CombatMetric.SGS_HEAL, 5_120);
		totals.add(CombatMetric.ELDRITCH_PRAYER, 38_150);
		totals.add(CombatMetric.ZCB_DAMAGE, 271_884);
		totals.add(CombatMetric.OTHER_SPEC_DAMAGE, 60_337);
		totals.add(CombatMetric.SCYTHE_PUNISH, 110_762);
		totals.add(CombatMetric.NOXIOUS_HALBERD_PUNISH, 28_604);
		totals.add(CombatMetric.CRYSTAL_HALBERD_PUNISH, 44_318);
		totals.add(CombatMetric.OTHER_MELEE_PUNISH, 12_890);
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
			DoomFormat.count(lifetime.deep),
			session.kph(),
			lifetime.kph());
	}

	/** Mirrors the tooltip the plugin hangs off a rate, so a hover in the preview reads as one. */
	private static String tooltip(DelveTotals totals)
	{
		return totals.isEmpty()
			? "No delves completed yet"
			: String.format("%d deep %s in %s of run time",
				totals.deep, totals.deep == 1 ? "delve" : "delves",
				DoomFormat.tickDuration(totals.ticks));
	}

	private static List<MilestoneTablePanel.Row> rows()
	{
		return rows(50);
	}

	/** The target every scene aims for, which is also the resets card's milestone. */
	private static final int RESET_TARGET = 50;

	/**
	 * The resets card: a character a few hundred runs in, or nothing at all for one whose table is
	 * empty.
	 */
	ResetSummary resets()
	{
		if (rows.isEmpty())
		{
			return new ResetSummary(RESET_TARGET, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}

		return new ResetSummary(RESET_TARGET, 297, 184, 41, 12_900, 11_600, 10, 12_450, 6, 3);
	}

	/** Resets an hour for the card's session line. */
	static final double RESETS_PER_HOUR = 2.1;

	/** A milestone row every ten delves down to {@code deepest}, one of them freshly beaten. */
	private static List<MilestoneTablePanel.Row> rows(int deepest)
	{
		List<MilestoneTablePanel.Row> rows = new ArrayList<>();
		int kc = 240;

		for (int delve = MilestoneTable.INTERVAL; delve <= deepest; delve += MilestoneTable.INTERVAL)
		{
			rows.add(new MilestoneTablePanel.Row(delve, kc, 1_100 + delve * 210, delve == 30,
				delve == RESET_TARGET));
			kc = Math.max(1, kc / 3);
		}

		return rows;
	}


	/** The name, so a scene can be dropped straight into a picker. */
	@Override
	public String toString()
	{
		return name;
	}
}
