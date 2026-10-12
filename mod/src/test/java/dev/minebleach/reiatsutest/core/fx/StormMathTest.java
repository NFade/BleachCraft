package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Closed-form petal storm (6.2): phases, continuity, determinism, birth at the cut, return to the slot. */
class StormMathTest {
	private static final double[] OWNER = {10, 64, -3};
	private static final double[] AIM = {10, 64, 31};
	private static final double[] LOOK = {0, 0, 1};

	private static StormMath.Storm storm(int blades, int perBlade, long seed) {
		float[] dist = new float[blades];
		for (int i = 0; i < blades; i++) {
			dist[i] = 1.1f * (i % 50);
		}
		return new StormMath.Storm(new StormMath.Params(), seed, blades, perBlade, dist, 0.0, 34.0);
	}

	private static double scaleAt(StormMath.Storm s, int j, double t, double[] out) {
		int b = s.blade(j);
		return s.position(j, t, OWNER, 10 + (b % 7) * 1.3, 63.5, -3 + 6 + (b % 9), AIM, LOOK, out);
	}

	@Test
	void petalCountsPerBlade() {
		assertEquals(15, StormMath.petalsPerBlade(3000, 200));
		assertEquals(3, StormMath.petalsPerBlade(3000, 1000));
		assertEquals(15, StormMath.petalsPerBlade(1500, 100));
		assertEquals(7, StormMath.petalsPerBlade(750, 100));
		assertEquals(1, StormMath.petalsPerBlade(10, 1000));
		assertEquals(10, StormMath.condensePerBlade(200));
		assertEquals(2, StormMath.condensePerBlade(1000));
		assertEquals(300, StormMath.petalsPerBlade(3000, 10), "the open lane has few blades: many petals each, same total");
		assertEquals(200, StormMath.condensePerBlade(10));
	}

	@Test
	void twoThirdsStreamAndTheNearestToTheFrontLeaveFirst() {
		StormMath.Storm s = storm(200, 15, 5);
		int streams = 0;
		double minLaunch = 99;
		double maxLaunch = 0;
		for (int j = 0; j < s.m; j++) {
			if (s.isStream(j)) {
				streams++;
				minLaunch = Math.min(minLaunch, s.launchTime(j));
				maxLaunch = Math.max(maxLaunch, s.launchTime(j));
			}
		}
		assertEquals(2.0 / 3.0, streams / (double) s.m, 0.04);
		assertEquals(1.5, minLaunch, 1e-6);
		assertEquals(1.8, maxLaunch, 1e-6);
	}

	@Test
	void everyStreamPetalHasArrivedBeforeTheSphereContracts() {
		StormMath.Storm s = storm(200, 15, 7);
		for (int j = 0; j < s.m; j++) {
			if (s.isStream(j)) {
				assertTrue(s.arriveTime(j) <= 2.4 + 1e-6, "petal " + j + " arrives " + s.arriveTime(j));
				assertTrue(s.flightTime(j) > 0.1 && s.flightTime(j) <= 0.6 + 1e-6);
			}
		}
	}

	@Test
	void aPetalIsBornWhereTheCutPassesItsSlotHeightAndNotBefore() {
		StormMath.Storm s = storm(40, 15, 3);
		double[] out = new double[3];
		for (int j : new int[] {0, 7, 14, 100, 599}) {
			double ts = s.birthTime(j);
			assertEquals(0.0, scaleAt(s, j, ts - 0.01, out), 1e-9, "not born before the cut");
			assertTrue(scaleAt(s, j, ts + 0.2, out) > 0.5);
			// birth time matches the rows cut: blade fraction x 8 = slot height
			double d = 1.1 * (s.blade(j) % 50);
			assertEquals(s.slotHeight(j), RowsPhase.fraction(RowsPhase.Timing.scatter(), ts, d) * 8.0, 1e-6);
		}
		// the first slot of a blade (k = 0) is at the tip, the last one near the ground
		assertEquals(8.0, s.slotHeight(0), 1e-9);
		assertEquals(8.0 - 8.0 * 14 / 15, s.slotHeight(14), 1e-9);
	}

	@Test
	void helixHasACalmCoreAndTheStormRadiusAtTheTop() {
		StormMath.Storm s = storm(200, 15, 11);
		double[] o = new double[3];
		double maxR = 0;
		for (int j = 0; j < s.m; j++) {
			if (s.isLoose(j)) {
				continue;
			}
			for (double t = 1.0; t < 1.5; t += 0.1) {
				s.helix(j, t, 0, 0, 0, o);
				double r = Math.hypot(o[0], o[2]);
				assertTrue(r >= 1.5 - 0.4 - 1e-9, "calm core: r " + r);
				maxR = Math.max(maxR, r);
				assertTrue(o[1] >= 0 && o[1] < 8 + 1e-9);
			}
		}
		assertTrue(maxR > 4.5 && maxR < 5.5, "radius 5 at h 8: " + maxR);
	}

	@Test
	void positionIsContinuousAtTheImpactAndAtTheReturn() {
		StormMath.Storm s = storm(100, 10, 13);
		double[] a = new double[3];
		double[] b = new double[3];
		double maxJump = 0;
		for (int j = 0; j < s.m; j++) {
			if (!s.isStream(j)) {
				continue;
			}
			scaleAt(s, j, 2.5 - 1e-4, a);
			scaleAt(s, j, 2.5 + 1e-4, b);
			maxJump = Math.max(maxJump, dist(a, b));
		}
		assertTrue(maxJump < 0.05, "no jump at the impact: " + maxJump);
		for (int j = 0; j < s.m; j++) {
			double tr = s.returnTime(j);
			if (scaleAt(s, j, tr - 1e-4, a) > 0.2 && scaleAt(s, j, tr + 1e-4, b) > 0.2) {
				assertTrue(dist(a, b) < 0.2, "no jump at the return start of petal " + j + ": " + dist(a, b));
			}
		}
	}

	@Test
	void sphereIsAtFiveThenContractsToPointEightAtTheImpact() {
		StormMath.Storm s = storm(10, 10, 1);
		assertEquals(5.0, s.sphereRadius(1.9), 1e-9);
		assertEquals(5.0, s.sphereRadius(2.0), 1e-9);
		assertEquals(2.2, s.sphereRadius(2.44), 1e-6);
		assertEquals(0.8, s.sphereRadius(2.5), 1e-6);
		assertEquals(0.8, s.sphereRadius(3.0), 1e-6);
		assertTrue(s.sphereRadius(2.2) < 5.0 && s.sphereRadius(2.2) > 2.2);
	}

	@Test
	void burstReachesAboutNineAndAHalfBlocksAtThree() {
		StormMath.Storm s = storm(100, 10, 17);
		double[] o = new double[3];
		double max = 0;
		for (int j = 0; j < s.m; j++) {
			if (!s.isStream(j)) {
				continue;
			}
			s.burst(j, 3.0, AIM, o);
			max = Math.max(max, Math.hypot(o[0] - AIM[0], o[2] - AIM[2]));
		}
		assertTrue(max > 8.0 && max < 12.5, "burst radius " + max);
	}

	@Test
	void everyPetalIsBackAtItsSlotByFourPointFiveAndEatenByTheRegrowth() {
		StormMath.Storm s = storm(100, 10, 19);
		for (int j = 0; j < s.m; j++) {
			assertTrue(s.settledTime(j) <= 4.5 + 1e-9, "petal " + j + " settles " + s.settledTime(j));
			assertTrue(s.removeTime(j) > s.birthTime(j));
			assertTrue(s.removeTime(j) <= 5.7 + 1e-9);
		}
		double[] out = new double[3];
		double[] slot = new double[3];
		int j = 33;
		int b = s.blade(j);
		double bx = 10 + (b % 7) * 1.3;
		double bz = -3 + 6 + (b % 9);
		s.slotPoint(j, bx, 63.5, bz, slot);
		double t = s.settledTime(j) + 0.01;
		if (t < s.removeTime(j) - 0.2) {
			double sc = scaleAt(s, j, t, out);
			assertTrue(sc > 0.9);
			assertTrue(dist(out, slot) < 0.2, "sits at its slot: " + dist(out, slot));
		}
		// consumed: invisible after the regrowth passes the slot
		assertEquals(0.0, scaleAt(s, j, s.removeTime(j) + 0.01, out), 1e-9);
		assertFalse(Double.isNaN(out[0]));
	}

	@Test
	void deterministicForTheSameSeedAndDifferentForAnother() {
		StormMath.Storm a = storm(50, 15, 99);
		StormMath.Storm b = storm(50, 15, 99);
		StormMath.Storm c = storm(50, 15, 100);
		double[] pa = new double[3];
		double[] pb = new double[3];
		double[] pc = new double[3];
		int diff = 0;
		for (int j = 0; j < a.m; j += 7) {
			scaleAt(a, j, 1.7, pa);
			scaleAt(b, j, 1.7, pb);
			scaleAt(c, j, 1.7, pc);
			assertEquals(pa[0], pb[0], 0);
			assertEquals(pa[1], pb[1], 0);
			assertEquals(pa[2], pb[2], 0);
			if (dist(pa, pc) > 1e-6) {
				diff++;
			}
		}
		assertTrue(diff > 20);
	}

	@Test
	void everyPositionIsFiniteOverTheWholeStorm() {
		StormMath.Storm s = storm(200, 15, 23);
		double[] out = new double[3];
		for (int j = 0; j < s.m; j += 5) {
			for (double t = 0; t < 6.0; t += 0.05) {
				double sc = scaleAt(s, j, t, out);
				assertTrue(sc >= 0 && sc <= 1.0 + 1e-9);
				if (sc > 0) {
					assertTrue(Double.isFinite(out[0]) && Double.isFinite(out[1]) && Double.isFinite(out[2]), "j " + j + " t " + t);
				}
			}
		}
	}

	private static double dist(double[] a, double[] b) {
		return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
	}
}
