package com.rescanta.doommetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * A made-up fight with every hit's attack known, run through the ledger: a bow shot every five
 * ticks, a ghost thrall every four, a melee swing now and then, some shots at a larva, each of
 * ours earning its experience as it is made. Hits land in the order their attacks were made, which
 * is the rule the ledger goes by; what this measures is how it copes when the tick a projectile
 * was read to land on is wrong.
 */
public class AttackLedgerSimulationTest
{
	private static final int TICKS = 20_000;
	private static final int BOSS = 1;
	private static final int LARVA = 2;
	private static final double RATE = ExperienceRate.perDamage(625);

	private static final class Hit
	{
		private final int tick;
		private final int target;
		private final int amount;
		private final AttackLedger.Kind kind;
		private final int made;

		private Hit(int tick, int target, int amount, AttackLedger.Kind kind, int made)
		{
			this.tick = tick;
			this.target = target;
			this.amount = amount;
			this.kind = kind;
			this.made = made;
		}
	}

	private static final class Shot
	{
		private final boolean thrall;
		private final int made;
		private final int due;
		private final int target;

		private Shot(boolean thrall, int made, int due, int target)
		{
			this.thrall = thrall;
			this.made = made;
			this.due = due;
			this.target = target;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private final List<Shot> shots = new ArrayList<>();
	private final List<Integer> swings = new ArrayList<>();

	/** The hitpoints experience each tick's attack earned, and whether the rate for it is known. */
	private final int[] experience = new int[TICKS];
	private final boolean[] onBoss = new boolean[TICKS];
	private final List<AttackLedger.Verdict> verdicts = new ArrayList<>();
	private int lapsed;

	/**
	 * @param lateInHundred how many landings in a hundred come a tick after the tick read for them
	 */
	private void fight(long seed, int lateInHundred)
	{
		Random random = new Random(seed);

		for (int tick = 0; tick < TICKS; tick++)
		{
			if (tick % 4 == 1)
			{
				int due = tick + 1 + random.nextInt(2);
				shots.add(new Shot(true, tick, due, BOSS));
				hits.add(new Hit(due + late(random, lateInHundred), BOSS, random.nextInt(4),
					AttackLedger.Kind.THRALL_SHOT, tick));
			}

			if (tick % 5 != 2)
			{
				continue;
			}

			if (random.nextInt(8) == 0)
			{
				swings.add(tick);

				int total = 0;

				for (int i = 0; i < 3; i++)
				{
					int amount = random.nextInt(51);
					total += amount;
					hits.add(new Hit(tick + 1, BOSS, amount, AttackLedger.Kind.SWING, tick));
				}

				earn(tick, total, true);
			}
			else
			{
				int target = random.nextInt(10) == 0 ? LARVA : BOSS;
				int due = tick + 2 + random.nextInt(2);
				int amount = random.nextInt(10) < 3 ? 0 : 1 + random.nextInt(80);
				shots.add(new Shot(false, tick, due, target));
				hits.add(new Hit(due + late(random, lateInHundred), target, amount,
					AttackLedger.Kind.SHOT, tick));
				earn(tick, amount, target == BOSS);
			}
		}

		// Each tick's hits in the order their attacks were made, a thrall's first of two made at once.
		hits.sort(Comparator.<Hit>comparingInt(hit -> hit.tick).thenComparingInt(hit -> hit.made)
			.thenComparingInt(hit -> hit.kind.isThrall() ? 0 : 1));
	}

	private void earn(int tick, int damage, boolean boss)
	{
		experience[tick] = (int) Math.round(damage * RATE);
		onBoss[tick] = boss;
	}

	private static int late(Random random, int lateInHundred)
	{
		return random.nextInt(100) < lateInHundred ? 1 : 0;
	}

	/** Feeds the fight as the game would: a tick's hits, then its swing, then the shots seen after. */
	private void run()
	{
		AttackLedger ledger = new AttackLedger(new AttackLedger.Listener()
		{
			@Override
			public void settled(AttackLedger.Verdict verdict)
			{
				verdicts.add(verdict);
			}

			@Override
			public void lapsed(AttackLedger.Attack attack)
			{
				lapsed++;
			}
		});

		int hit = 0;
		int shot = 0;
		int swing = 0;

		for (int tick = 0; tick < TICKS + 8; tick++)
		{
			for (; hit < hits.size() && hits.get(hit).tick == tick; hit++)
			{
				ledger.splat(tick, hits.get(hit).target, hits.get(hit).amount, "npc");
			}

			if (swing < swings.size() && swings.get(swing) == tick)
			{
				ledger.swung(tick, BOSS);
				swing++;
			}

			if (tick < TICKS && tick % 5 == 2)
			{
				ledger.experience(tick, experience[tick], onBoss[tick] ? RATE : 0);
			}

			ledger.tickEnded(tick);

			for (; shot < shots.size() && shots.get(shot).made == tick; shot++)
			{
				Shot fired = shots.get(shot);

				if (fired.thrall)
				{
					ledger.thrallShot(1907, fired.made, fired.due, fired.target);
				}
				else
				{
					ledger.shot(1120, fired.made, fired.due, fired.target);
				}
			}
		}
	}

	/** How many hits went to an attack that was not theirs, or to none. */
	private int misplaced()
	{
		assertEquals(hits.size(), verdicts.size());

		int wrong = 0;

		for (int i = 0; i < hits.size(); i++)
		{
			Hit hit = hits.get(i);
			AttackLedger.Attack attack = verdicts.get(i).attack;

			if (attack == null || attack.kind != hit.kind || attack.made != hit.made)
			{
				wrong++;
			}
		}

		return wrong;
	}

	@Test
	public void withEveryLandingReadRightEveryHitIsPlaced()
	{
		fight(42, 0);
		run();

		assertEquals(0, misplaced());
		assertEquals(0, lapsed);
	}

	/**
	 * One landing in five a tick late: 172 of 9,902 hits misplaced with this seed. Most are a
	 * thrall's hit and a small one of ours changing places. The costly ones are 16 hits of a swing
	 * left to nothing, after a late thrall's hit was taken into the swing and used up what its
	 * experience allows. Without the experience check it was 626.
	 */
	@Test
	public void withALandingInFiveATickLateFewHitsAreMisplaced()
	{
		fight(42, 20);
		run();

		int wrong = misplaced();
		assertTrue(wrong + " of " + hits.size() + " misplaced", wrong * 40 < hits.size());
	}
}
