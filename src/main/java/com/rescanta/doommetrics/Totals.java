package com.rescanta.doommetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * The session's and the character's lifetime deep delve rate and combat totals, banked clear by
 * clear. The lifetime figures are saved on the RuneScape profile. Client thread only.
 */
@Slf4j
class Totals
{
	/** Display only: how long without a run before the session rows go blank. */
	private static final Duration SESSION_IDLE = Duration.ofMinutes(30);

	private final TotalsStore totalsStore;
	private final RunHistoryStore runHistoryStore;

	/** The character the run in progress was started on, or null. */
	private final Supplier<String> runProfile;

	private DelveTotals lifetime = new DelveTotals();
	private DelveTotals session = new DelveTotals();
	private CombatTotals lifetimeCombat = new CombatTotals();
	private CombatTotals sessionCombat = new CombatTotals();

	/** Combat counted since the lifetime figures were last written. */
	private CombatTotals unbankedCombat = new CombatTotals();

	/** When the session's last run ended; null while one is in progress or before any. */
	private Instant sessionEndedAt;

	private final SessionClock sessionClock = new SessionClock();

	/** The character the session belongs to, so an alt's runs never join it. */
	private String sessionProfile;

	Totals(TotalsStore totalsStore, RunHistoryStore runHistoryStore, Supplier<String> runProfile)
	{
		this.totalsStore = totalsStore;
		this.runHistoryStore = runHistoryStore;
		this.runProfile = runProfile;
	}

	/** Drops everything held in memory. The lifetime figures stay saved. */
	void reset()
	{
		forgetSession();
		lifetime = new DelveTotals();
		lifetimeCombat = new CombatTotals();
		sessionProfile = null;
	}

	/** Starts the session over. Combat not yet banked is dropped - flush it first to keep it. */
	void forgetSession()
	{
		session = new DelveTotals();
		sessionCombat = new CombatTotals();
		unbankedCombat = new CombatTotals();
		sessionEndedAt = null;
		sessionClock.reset();
	}

	/**
	 * Reads the logged in character's lifetime figures.
	 *
	 * @return false while logged out, when the last figures are left on show
	 */
	boolean load()
	{
		if (!totalsStore.hasProfile())
		{
			return false;
		}

		DelveTotals loaded = totalsStore.load();
		lifetime = loaded == null ? new DelveTotals() : loaded;

		CombatTotals loadedCombat = totalsStore.loadCombat();
		lifetimeCombat = loadedCombat == null ? new CombatTotals() : loadedCombat;

		startSessionForCurrentCharacter();
		return true;
	}

	/** Starts the session over when the client comes back as a different character. */
	private void startSessionForCurrentCharacter()
	{
		String profile = runHistoryStore.currentProfile();

		if (profile == null || profile.equals(sessionProfile))
		{
			return;
		}

		if (sessionProfile != null)
		{
			log.debug("New character, starting the session rate over");
		}

		sessionProfile = profile;
		session = new DelveTotals();
		sessionCombat = new CombatTotals();
		sessionEndedAt = null;
		sessionClock.reset();
	}

	void runStarted(Instant startedAt)
	{
		startSessionForCurrentCharacter();
		sessionClock.start(startedAt);
		sessionEndedAt = null;
	}

	/** Banks what is left of an ended run, and starts the session's idle clock. */
	void runEnded(Instant endedAt)
	{
		flushCombat();
		sessionEndedAt = endedAt;
	}

	void loggedOut(Instant at)
	{
		sessionClock.pause(at);
	}

	void connectionLost(Instant at)
	{
		sessionClock.connectionLost(at);
	}

	void loggedIn(Instant at)
	{
		sessionClock.resume(at);
	}

	/**
	 * Banks the clear's segment into the session and lifetime rates. A joined run's first clear is
	 * left out when its start was a guess.
	 */
	void bankClear(DelveRun run, DelveRun.Split split)
	{
		flushCombat();

		if (!run.lastClearTimed())
		{
			log.debug("Not banking delve {} to the rates - the run was joined part way into it",
				split.level);
			return;
		}

		int deep = split.level >= DelveRun.DEEP_DELVE_LEVEL ? 1 : 0;
		long ticks = run.lastClearTicks();

		if (ticks <= 0)
		{
			return;
		}

		session.add(deep, ticks);

		if (!lifetimeBelongsToRun())
		{
			log.debug("Not banking delve {} to a lifetime total - it belongs to another character",
				split.level);
			return;
		}

		lifetime.add(deep, ticks);
		totalsStore.save(lifetime);

		log.debug("Banked delve {}: {} deep in {} ticks (session {}/{}, lifetime {}/{})",
			split.level, deep, ticks, session.deep, session.ticks, lifetime.deep, lifetime.ticks);
	}

	/** Counts an amount into the session, and into the lifetime at the next flush. */
	void recordCombat(CombatMetric metric, long amount)
	{
		sessionCombat.add(metric, amount);
		unbankedCombat.add(metric, amount);
	}

	/** Writes the combat counted since the last write to the lifetime totals. */
	void flushCombat()
	{
		if (unbankedCombat.isEmpty())
		{
			return;
		}

		CombatTotals pending = unbankedCombat;
		unbankedCombat = new CombatTotals();

		if (!lifetimeBelongsToRun())
		{
			log.debug("Not banking combat totals to a lifetime - they belong to another character");
			return;
		}

		lifetimeCombat.addAll(pending);
		totalsStore.saveCombat(lifetimeCombat);
	}

	/** Whether the logged in character is the one the run was started on. */
	private boolean lifetimeBelongsToRun()
	{
		String profile = runProfile.get();
		return totalsStore.hasProfile()
			&& (profile == null || profile.equals(runHistoryStore.currentProfile()));
	}

	/** Whether the session figures are worth showing: a run is going, or one ended recently. */
	boolean sessionShown(boolean running, Instant now)
	{
		return running || (sessionEndedAt != null
			&& Duration.between(sessionEndedAt, now).compareTo(SESSION_IDLE) < 0);
	}

	/** Enough of both combat tallies to tell one repaint from the next. */
	String combatKey(boolean showSession)
	{
		StringBuilder key = new StringBuilder();

		for (CombatMetric metric : CombatMetric.values())
		{
			key.append(showSession ? sessionCombat.get(metric) : 0).append(',')
				.append(lifetimeCombat.get(metric)).append(',');
		}

		return key.toString();
	}

	CombatTotals sessionCombat()
	{
		return sessionCombat.copy();
	}

	CombatTotals lifetimeCombat()
	{
		return lifetimeCombat.copy();
	}

	/** The panel's session and lifetime figures, with the session blank while it isn't shown. */
	DoomMetricsPanel.Stats stats(boolean showSession, Instant now)
	{
		DelveTotals live = showSession ? session : null;

		return new DoomMetricsPanel.Stats(
			live == null ? null : sessionLength(now),
			live == null ? null : DoomFormat.pace(live.kph()),
			live == null ? null : tooltip(live),
			live == null ? null : DoomFormat.count(live.deep),
			DoomFormat.pace(lifetime.kph()),
			tooltip(lifetime),
			lifetime.isEmpty() ? null : DoomFormat.count(lifetime.deep),
			live == null ? null : live.kph(),
			lifetime.kph());
	}

	private String sessionLength(Instant now)
	{
		Duration elapsed = sessionClock.elapsed(now);
		return elapsed == null ? null : DoomFormat.duration(elapsed);
	}

	private static String tooltip(DelveTotals totals)
	{
		return totals.isEmpty()
			? "No delves completed yet"
			: String.format("%d deep %s in %s of run time",
				totals.deep, totals.deep == 1 ? "delve" : "delves",
				DoomFormat.tickDuration(totals.ticks));
	}
}
