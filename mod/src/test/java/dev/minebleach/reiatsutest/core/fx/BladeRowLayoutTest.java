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
		// R11 T13: the blade is buried by its length plus 0.5 (the old 8 m mesh: 8.5); 5.5 + 0.5 = 6.0
		assertEquals(-6.0, BladeRowLayout.riseOffset(0), 1e-9);
		assertEquals(-(BladeSpec.length() + BladeRowLayout.SINK_MARGIN), BladeRowLayout.riseOffset(-1), 1e-9);
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
		assertEquals(BladeRowLayout.LEAN_DEG, l.leanDeg(l.settledTime() + 1, 0), 0.6);
	}

	@Test
	void rowsScaleShrinksSpacingAndCorridorButKeepsCountsAndTheOriginalAt1() {
		BladeRowLayout o = BladeRowLayout.of(200, 1);
		BladeRowLayout one = BladeRowLayout.of(200, 1, 1.0);
		BladeRowLayout s = BladeRowLayout.of(200, 1, 0.5);
		assertEquals(o.n, s.n);
		for (int i = 0; i < o.n; i++) {
			assertEquals(o.along[i], one.along[i], 1e-6);
			assertEquals(o.across[i], one.across[i], 1e-6);
		}
		assertEquals(3.0, s.corridorHalfWidth(), 1e-9);
		assertEquals(0.55, s.along[0] - s.along[1], 1e-5, "spacing 1.1 x 0.5");
		assertEquals(3.0, Math.abs(s.across[0]), 1e-5);
		assertEquals(3.6, Math.abs(s.across[s.perRank]), 1e-5, "second rank 1.2 x 0.5 further out");
		assertEquals(0.2, BladeRowLayout.of(200, 1, 0.0).space, 1e-9, "clamped");
	}

	@Test
	void laneIsOneRankPerSideWithAWideOpenCorridor() {
		BladeRowLayout l = BladeRowLayout.lane(5, 1, 6.5, 2.5, 5.0);
		assertEquals(10, l.n, "about 10 blades in all");
		assertEquals(1, l.ranks);
		assertEquals(5, l.perRank);
		for (int i = 0; i < l.n; i++) {
			assertEquals(6.5, Math.abs(l.across[i]), 1e-5, "one rank: every blade on the same line each side");
			assertEquals(1.0, l.scale[i], 1e-5);
		}
		assertEquals(5.0, l.along[0], 1e-5);
		assertEquals(-5.0, l.along[4], 1e-5);
		assertEquals(10.0, l.length(), 1e-5);
		assertEquals(6.5, l.corridorHalfWidth(), 1e-9);
		assertTrue(l.completionTime() < 2.0, "short rows complete quickly: " + l.completionTime());
		assertEquals(-6.5, l.across[0], 1e-5, "left side first");
		assertEquals(6.5, l.across[5], 1e-5);
	}

	@Test
	void laneClampsItsInputs() {
		assertEquals(2, BladeRowLayout.lane(0, 1, 6, 2.5, 5).n);
		assertEquals(500, BladeRowLayout.lane(10000, 1, 6, 2.5, 5).n);
	}

	// ---- r7: wide slab blades in one tight row per side (anime references ep52 / ep58)

	@Test
	void defaultWallIsTenSlabsPerSideThatAlmostTouch() {
		BladeRowLayout l = BladeRowLayout.laneDefault(BladeRowLayout.DEFAULT_PER_SIDE, 1);
		assertEquals(20, l.n);
		assertEquals(1, l.ranks);
		double scale = BladeRowLayout.DEFAULT_BLADE_SCALE;
		double height = BladeRowLayout.height(scale);
		double width = BladeSpec.width() * scale;
		// R11 T13: the straight blade of the user at its true size (anime measurement: 5.5 tall, 0.37 wide); the old pins were 7 to 9 tall and 1.2 to 1.8 wide
		assertEquals(5.5, height, 0.01, "5.5 blocks tall");
		assertEquals(0.4026, width, 0.01, "about 0.40 blocks wide");
		double gap = BladeRowLayout.gap(BladeRowLayout.DEFAULT_PITCH, scale);
		assertEquals(1.0 - 0.1477, gap, 1e-9, "pitch 1.0 minus the 0.1477 thick slab");
		for (int j = 0; j + 1 < BladeRowLayout.DEFAULT_PER_SIDE; j++) {
			assertEquals(BladeRowLayout.DEFAULT_PITCH, l.along[j] - l.along[j + 1], 1e-5, "even pitch along the row");
		}
		assertEquals(BladeRowLayout.DEFAULT_START_AHEAD, l.along[0], 1e-5);
		assertEquals(-BladeRowLayout.DEFAULT_HALF_WIDTH, l.across[0], 1e-5, "left side first");
		assertEquals(BladeRowLayout.DEFAULT_HALF_WIDTH, l.across[BladeRowLayout.DEFAULT_PER_SIDE], 1e-5);
	}

	@Test
	void laneStaysOpenAroundTheCasterEvenAtTheTips() {
		double scale = BladeRowLayout.DEFAULT_BLADE_SCALE;
		double width = BladeSpec.width() * scale;
		double innerFoot = BladeRowLayout.DEFAULT_HALF_WIDTH - width / 2;
		assertTrue(innerFoot >= 6.0, "the inner face of a blade foot is at least 6 blocks from the axis: " + innerFoot);
		// the mesh is straight now (R11 T13): only the apex bevel (0.201 m off the axis of the blade) and the 3 degree lean move the tip toward the axis
		double tipAcross = BladeRowLayout.DEFAULT_HALF_WIDTH - Math.abs(BladeSpec.tipZ()) * scale - BladeRowLayout.height(scale) * Math.sin(Math.toRadians(BladeRowLayout.LEAN_DEG + 0.5));
		assertTrue(tipAcross >= 6.0, "the lane at the tips is at least 12 blocks wide: " + 2 * tipAcross);
		assertTrue(tipAcross < BladeRowLayout.DEFAULT_HALF_WIDTH, "the tips lean inward");
	}

	@Test
	void r9LayoutIsOnlyConstantsTheDefaultsStayAsTheyWere() {
		// R11 T13: the default layout (start, pitch, count) is NOT changed by the new blade; the R9 numbers exist for bankai.layout = r9 only
		assertEquals(-2.0, BladeRowLayout.DEFAULT_START_AHEAD, 0);
		assertEquals(1.0, BladeRowLayout.DEFAULT_PITCH, 0);
		assertEquals(10, BladeRowLayout.DEFAULT_PER_SIDE);
		assertEquals(7.0, BladeRowLayout.DEFAULT_HALF_WIDTH, 0);
		BladeRowLayout r9 = BladeRowLayout.lane(BladeRowLayout.R9_PER_SIDE, 1, BladeRowLayout.R9_HALF_WIDTH, BladeRowLayout.R9_PITCH, BladeRowLayout.R9_START_AHEAD);
		assertEquals(24, r9.n);
		assertEquals(-1.0, r9.along[0], 1e-5, "first blade 1 block behind the feet");
		assertEquals(-1.0 - 2.0 * 11, r9.along[11], 1e-5, "pitch 2.0, 12 per side");
		assertEquals(3.7, r9.corridorHalfWidth(), 1e-9);
		assertEquals("r9", BladeRowLayout.LAYOUT_R9);
	}

	@Test
	void lowerTiersKeepTheCorridorLengthUpToTwiceThePitch() {
		assertEquals(1.0, BladeRowLayout.tierPitch(10, 10, 1.0), 1e-9, "High keeps the pitch");
		assertEquals(2.0, BladeRowLayout.tierPitch(5, 10, 1.0), 1e-9, "Medium: 9/4 capped at 2");
		assertEquals(2.0, BladeRowLayout.tierPitch(3, 10, 1.0), 1e-9, "Low: capped at 2");
		assertEquals(1.0, BladeRowLayout.tierPitch(12, 10, 1.0), 1e-9, "more than High never tightens");
		BladeRowLayout med = BladeRowLayout.laneDefault(5, 1);
		assertEquals(10, med.n);
		assertEquals(2.0, med.along[0] - med.along[1], 1e-5);
	}

	@Test
	void theDefaultRowsStandBehindTheCharacter() {
		// r7 user request: the big blades appear behind the character (the first blade 2 blocks behind the feet, the rest further back)
		BladeRowLayout l = BladeRowLayout.laneDefault(BladeRowLayout.DEFAULT_PER_SIDE, 1);
		for (int i = 0; i < l.n; i++) {
			assertTrue(l.along[i] <= -2.0 + 1e-6, "blade " + i + " at " + l.along[i]);
		}
	}
}
