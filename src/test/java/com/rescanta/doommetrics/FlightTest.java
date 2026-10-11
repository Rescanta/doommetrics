package com.rescanta.doommetrics;

import net.runelite.api.gameval.SpotanimID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FlightTest
{
	private static final int ARROW = SpotanimID.II_DRAGON_ARROW_NORMAL_PROJANIM;

	@Test
	public void aProjectileSeenAsItStartsStartedThisTick()
	{
		assertEquals(100, Flight.startedTick(100, 5000, 5000));
		assertEquals(100, Flight.startedTick(100, 5001, 5000));
	}

	@Test
	public void aProjectileFirstSeenMidFlightStartedEarlier()
	{
		assertEquals(99, Flight.startedTick(100, 5000, 4965));
		assertEquals(100, Flight.startedTick(100, 5000, 4990));
	}

	/** As logged with a bow: by the cycles the arrow flies for, the ticks from its start to the hit. */
	@Test
	public void ourShotLandsByHowLongItFlies()
	{
		assertEquals(100, Flight.landingTick(100, 10, ARROW));
		assertEquals(100, Flight.landingTick(100, 15, ARROW));
		assertEquals(101, Flight.landingTick(100, 20, ARROW));
		assertEquals(101, Flight.landingTick(100, 45, ARROW));
		assertEquals(102, Flight.landingTick(100, 50, ARROW));
	}

	/** As logged: on the tick it starts from one or two tiles, the tick after from three to eight. */
	@Test
	public void theEyeOfAyaksBoltLandsATickLaterFromThreeTiles()
	{
		assertEquals(100, Flight.landingTick(100, 0, SpotanimID.VFX_AYAK_NORMAL_PROJECTILE));
		assertEquals(100, Flight.landingTick(100, 5, SpotanimID.VFX_AYAK_NORMAL_PROJECTILE));
		assertEquals(101, Flight.landingTick(100, 10, SpotanimID.VFX_AYAK_NORMAL_PROJECTILE));
		assertEquals(101, Flight.landingTick(100, 35, SpotanimID.VFX_AYAK_NORMAL_PROJECTILE));
	}

	/** As logged with a toxic blowpipe: a dart from five tiles still lands on the tick it starts. */
	@Test
	public void aDartLandsATickLaterOnlyFromSixTiles()
	{
		assertEquals(100, Flight.landingTick(100, 5, SpotanimID.II_DRAGON_DART_PROJANIM));
		assertEquals(100, Flight.landingTick(100, 25, SpotanimID.II_DRAGON_DART_PROJANIM));
		assertEquals(101, Flight.landingTick(100, 30, SpotanimID.AMETHYST_DART_TRAVEL));
	}

	/** As logged, twice: 21 cycles on the tick it starts, 28 the tick after. */
	@Test
	public void theBlowpipesSpecLandsATickLaterFromFourTiles()
	{
		assertEquals(100, Flight.landingTick(100, 21, SpotanimID.TOXIC_BLOWPIPE_SPECIALATTACK));
		assertEquals(101, Flight.landingTick(100, 28, SpotanimID.TOXIC_BLOWPIPE_SPECIALATTACK));
	}

	/** As logged: 8 to 24 cycles land the tick after it starts, 28 and 32 the one after that. */
	@Test
	public void theScorchingBowsSpecIsATickBehindItsShots()
	{
		assertEquals(101, Flight.landingTick(100, 8, SpotanimID.VFX_SCORCHING_BOW_PROJECTILE));
		assertEquals(101, Flight.landingTick(100, 24, SpotanimID.VFX_SCORCHING_BOW_PROJECTILE));
		assertEquals(102, Flight.landingTick(100, 28, SpotanimID.VFX_SCORCHING_BOW_PROJECTILE));
		assertEquals(102, Flight.landingTick(100, 32, SpotanimID.VFX_SCORCHING_BOW_PROJECTILE));
	}

	/** As logged with a ghost. */
	@Test
	public void aGhostsShotLandsAheadOfItsProjectile()
	{
		assertEquals(100, Flight.thrallLandingTick(100, 15, true));
		assertEquals(100, Flight.thrallLandingTick(100, 40, true));
		assertEquals(101, Flight.thrallLandingTick(100, 45, true));
		assertEquals(101, Flight.thrallLandingTick(100, 60, true));
	}

	/** As logged with a skeleton, which lands a tick later than a ghost from 35 to 40 cycles. */
	@Test
	public void aSkeletonsShotIsLessAheadOfItsProjectile()
	{
		assertEquals(100, Flight.thrallLandingTick(100, 25, false));
		assertEquals(100, Flight.thrallLandingTick(100, 30, false));
		assertEquals(101, Flight.thrallLandingTick(100, 35, false));
		assertEquals(101, Flight.thrallLandingTick(100, 60, false));
	}
}
