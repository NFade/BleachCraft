package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Sternenstaub numbers ({@link UryuStarMath}). */
class UryuStarMathTest {
	@Test
	void flakesStandInTheRingAtLineUpAndOnTheLineAfter() {
		double[] o = new double[4];
		int n = 120;
		for (int i = 0; i < n; i += 7) {
			UryuStarMath.flake(i, n, 0.39, UryuStarMath.RING_REV, o);
			double ring = Math.hypot(o[0], o[1]);
			assertEquals(UryuStarMath.RING_RADIUS, ring, 0.3);
			assertEquals(UryuStarMath.RING_AHEAD, o[2], 0.3);
			UryuStarMath.flake(i, n, 0.7, UryuStarMath.RING_REV, o);
			assertEquals(UryuStarMath.SHAFT_RADIUS, Math.hypot(o[0], o[1]), 0.01);
			assertTrue(o[2] >= UryuStarMath.RING_AHEAD - 0.01 && o[2] <= UryuStarMath.headStart() + 0.01);
		}
	}

	@Test
	void timeStopsAtTheHeldBreath() {
		double[] a = new double[4];
		double[] b = new double[4];
		UryuStarMath.flake(5, 60, 0.7, 0.8, a);
		UryuStarMath.flake(5, 60, 0.79, 0.8, b);
		assertEquals(a[0], b[0], 1.0E-12);
		assertEquals(a[2], b[2], 1.0E-12);
	}

	@Test
	void flakesStartFarAndInvisible() {
		double[] o = new double[4];
		UryuStarMath.flake(3, 120, 0.0, 0.8, o);
		assertEquals(0.0, o[3], 1.0E-9);
		UryuStarMath.flake(3, 120, 0.002, 0.8, o);
		assertTrue(Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2]) > 1.0);
	}

	@Test
	void headTravelsAt140AndArrives() {
		assertEquals(UryuStarMath.headStart(), UryuStarMath.headDistance(40.0, 0.0), 1.0E-9);
		assertEquals(40.0, UryuStarMath.headDistance(40.0, 1.0), 1.0E-9);
		double arrive = UryuStarMath.arrivalTime(40.0);
		assertEquals((40.0 - UryuStarMath.headStart()) / 140.0, arrive, 1.0E-9);
		assertEquals(5.0, UryuStarMath.headDistance(5.0, 0.0), 1.0E-9);
		assertEquals(0.0, UryuStarMath.arrivalTime(5.0), 1.0E-9);
	}

	@Test
	void beamFadesFromTheTailForward() {
		double arrive = UryuStarMath.arrivalTime(40.0);
		assertEquals(0.0, UryuStarMath.tailFraction(40.0, arrive + 0.2), 1.0E-9);
		assertEquals(1.0, UryuStarMath.tailFraction(40.0, arrive + 0.4 + 0.3), 1.0E-9);
		assertTrue(UryuStarMath.totalTime(40.0) > arrive + 0.7);
	}

	@Test
	void shaftStretchesButIsCapped() {
		assertEquals(UryuStarMath.SHAFT_LENGTH, UryuStarMath.shaftLength(UryuStarMath.headStart()), 1.0E-9);
		assertTrue(UryuStarMath.shaftLength(20.0) > UryuStarMath.SHAFT_LENGTH);
		assertEquals(UryuStarMath.SHAFT_MAX, UryuStarMath.shaftLength(400.0), 1.0E-9);
	}

	@Test
	void shockRingsEveryFourBlocksUpToTen() {
		assertEquals(10, UryuStarMath.ringCount(40.0));
		assertEquals(2, UryuStarMath.ringCount(9.0));
		assertEquals(0.0, UryuStarMath.ringStart(1), 1.0E-9);
		assertTrue(UryuStarMath.ringStart(10) > UryuStarMath.ringStart(5));
		assertEquals(0.0, UryuStarMath.ringSize(0.0), 1.0E-9);
		assertEquals(2.2, UryuStarMath.ringSize(0.25), 1.0E-9);
	}

	@Test
	void tierCountsAndCalmCaps() {
		assertEquals(120, UryuStarMath.flakeCount(120, false));
		assertEquals(60, UryuStarMath.flakeCount(120, true));
		assertEquals(300, UryuStarMath.speckCount(300, 600));
		assertEquals(150, UryuStarMath.speckCount(300, 300));
		assertEquals(0, UryuStarMath.speckCount(300, 0));
		assertTrue(UryuStarMath.sideBars(60));
		assertTrue(!UryuStarMath.sideBars(30));
		assertEquals(0.40, UryuStarMath.flashAt(0.0, true), 1.0E-9);
		assertEquals(0.80, UryuStarMath.flashAt(0.0, false), 1.0E-9);
		assertTrue(UryuStarMath.flashAt(100.0, false) < UryuStarMath.flashAt(5.0, false));
	}
}
