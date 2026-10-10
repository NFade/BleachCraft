package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Row layout of 6.1 and ART_BIBLE 2.5: counts, ranks, lengths, timing. */
class BladeRowLayoutTest {
	@Test
	void defaultLayoutIsTwoRowsOfTwoRanksOfFifty() {
		BladeRowLayout l = BladeRowLayout.of(200, 1);
		assertEquals(200, l.n);
		assertEquals(2, l.ranks);
		assertEquals(50, l.perRank);
		assertEquals(55.0, l.length(), 1.0);
		assertEquals(2.2, l.completionTime(), 0.2);
	}

	@Test
	void maximumLayoutIsFiveRanksAndSlightlyLonger() {
		BladeRowLayout l = BladeRowLayout.of(1000, 1);
		assertEquals(1000, l.n);
		assertEquals(5, l.ranks);
		assertEquals(100, l.perRank);
		assertEquals(110.0, l.length(), 2.0);
		assertEquals(2.8, l.completionTime(), 0.15);
	}

	@Test
	void smallCountsKeepTwoRanksAndNeverExceedTheRequest() {
		for (int n : new int[] {4, 50, 100, 199, 200, 201, 400, 999, 1000, 5000}) {
			BladeRowLayout l = BladeRowLayout.of(n, 3);
			assertTrue(l.ranks >= 2 && l.ranks <= 5);
			assertTrue(l.n <= Math.max(n, 8) && l.n <= 1000, "n " + n + " -> " + l.n);
			assertEquals(l.n, l.along.length);
		}
	}

	@Test
	void ranksStaggerAndScaleOutward() {
		BladeRowLayout l = BladeRowLayout.of(1000, 1);
		int rightStart = l.ranks * l.perRank;
		int r0 = rightStart;
		int r1 = rightStart + l.perRank;
		assertEquals(6.0, l.across[r0], 1e-5);
		assertEquals(7.2, l.across[r1], 1e-5);
		assertEquals(3.0, l.along[r0], 1e-5);
		assertEquals(2.45, l.along[r1], 1e-5);
		assertEquals(1.0, l.scale[r0], 1e-5);
		assertEquals(1.1, l.scale[r1], 1e-5);
		assertEquals(-6.0, l.across[0], 1e-5);
	}

	@Test
	void riseGoesFromUnderTheGroundThroughASmallOvershoot() {
		assertEquals(-8.5, BladeRowLayout.riseOffset(0), 1e-9);
		assertEquals(0.0, BladeRowLayout.riseOffset(BladeRowLayout.RISE_TIME), 1e-9);
		double max = -9;
		for (double t = 0; t <= 0.45; t += 0.001) {
			max = Math.max(max, BladeRowLayout.riseOffset(t));
		}
		assertEquals(0.3, max, 0.1, "overshoot about 0.3 blocks");
	}

	@Test
	void eruptionTimeGrowsWithDistanceAndWorldTransformIsRigid() {
		BladeRowLayout l = BladeRowLayout.of(200, 1);
		double[] w = new double[3];
		l.world(l.ranks * l.perRank, 0, 10, 20, w);
		// yaw 0 faces +z, the right hand is -x: the first right blade is 6 blocks to -x and 3 ahead
		assertEquals(10 - 6.0, w[0], 1e-6);
		assertEquals(20 + 3.0, w[2], 1e-6);
		for (int i = 1; i < l.perRank; i++) {
			assertTrue(l.start[i] != l.start[i - 1]);
		}
		assertEquals(1.0 + 0.012 * l.dist[0], l.start[0], 1e-5);
	}

	@Test
	void leanArrivesAfterCompletionAndStaysSmall() {
		BladeRowLayout l = BladeRowLayout.of(200, 1);
		assertEquals(0.0, l.leanDeg(l.completionTime(), 3), 1e-9);
		assertEquals(3.0, l.leanDeg(l.settledTime() + 1, 0), 0.6);
	}
}
