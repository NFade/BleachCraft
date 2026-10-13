package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Spark budgets, directions and rings of the block / parry feedback (r8 fx2 item 1). */
class BlockFxMathTest {
	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double len(double[] a) {
		return Math.sqrt(dot(a, a));
	}

	@Test
	void sparkBudgetsAreOrderedAndCapped() {
		int block = BlockFxMath.sparkCount(BlockFxMath.BLOCK, 4.0, 1.0);
		int parry = BlockFxMath.sparkCount(BlockFxMath.PARRY, 4.0, 1.0);
		int ability = BlockFxMath.sparkCount(BlockFxMath.ABILITY, 4.0, 1.0);
		assertEquals(36, block); // 24 + 3 * 4
		assertEquals(86, parry); // 70 + 4 * 4
		assertEquals(16, ability); // 10 + 1.5 * 4
		assertTrue(ability < block && block < parry, "a parry is the biggest, the ability block the lightest");
		// damage is capped at 8, the scale cannot pass the hard cap
		assertEquals(BlockFxMath.sparkCount(BlockFxMath.BLOCK, 8.0, 1.0), BlockFxMath.sparkCount(BlockFxMath.BLOCK, 100.0, 1.0));
		assertEquals(BlockFxMath.MAX_SPARKS, BlockFxMath.sparkCount(BlockFxMath.PARRY, 8.0, 50.0));
		// tuning 0 switches the sparks off, nonsense reads as the default
		assertEquals(0, BlockFxMath.sparkCount(BlockFxMath.BLOCK, 4.0, 0.0));
		assertEquals(0, BlockFxMath.sparkCount(BlockFxMath.BLOCK, 4.0, -3.0));
		assertEquals(24, BlockFxMath.sparkCount(BlockFxMath.BLOCK, Double.NaN, 1.0));
		assertEquals(36, BlockFxMath.sparkCount(BlockFxMath.BLOCK, 4.0, Double.NaN));
	}

	@Test
	void variantCodesRoundTrip() {
		assertEquals(BlockFxMath.BLOCK, BlockFxMath.variantOf(0f));
		assertEquals(BlockFxMath.PARRY, BlockFxMath.variantOf(1f));
		assertEquals(BlockFxMath.ABILITY, BlockFxMath.variantOf(2f));
		assertEquals(BlockFxMath.BLOCK, BlockFxMath.variantOf(7f), "an unknown code reads as a plain block");
		assertEquals(BlockFxMath.BLOCK, BlockFxMath.variantOf(Float.NaN));
	}

	@Test
	void distanceCutsTheSparks() {
		assertEquals(1.0, BlockFxMath.distanceFactor(3));
		assertEquals(1.0, BlockFxMath.distanceFactor(24));
		assertEquals(0.5, BlockFxMath.distanceFactor(30));
		assertEquals(0.25, BlockFxMath.distanceFactor(60));
		assertEquals(0.0, BlockFxMath.distanceFactor(BlockFxMath.FAR + 1));
	}

	@Test
	void wavesAddUpAndTheFirstIsTheBiggest() {
		for (int total : new int[] {0, 1, 7, 36, 86, 120}) {
			int[] w = BlockFxMath.waves(total, 3);
			assertEquals(3, w.length);
			assertEquals(total, w[0] + w[1] + w[2], "no spark is lost or invented, total " + total);
			assertTrue(w[0] >= w[1] && w[1] >= w[2] - 1, "the sparks die away along the scrape: " + w[0] + " " + w[1] + " " + w[2]);
		}
		assertEquals(1, BlockFxMath.waves(10, 0).length, "at least one wave");
	}

	@Test
	void bladeAxisIsAUnitVectorAcrossTheFront() {
		// the attacker stands on +z: the guard runs along x, tilted up
		double[] a = BlockFxMath.bladeAxis(0, 1, Math.toRadians(35), false);
		assertEquals(1.0, len(a), 1e-9);
		assertEquals(0.0, a[2], 1e-9, "across the front, not towards the attacker");
		assertEquals(Math.sin(Math.toRadians(35)), a[1], 1e-9);
		double[] b = BlockFxMath.bladeAxis(0, 1, Math.toRadians(35), true);
		assertEquals(-a[0], b[0], 1e-9, "flip mirrors the side");
		assertEquals(a[1], b[1], 1e-9);
		// any direction stays perpendicular to the attacker line in the horizontal plane
		double[] c = BlockFxMath.bladeAxis(0.6, -0.8, 0.5, false);
		assertEquals(1.0, len(c), 1e-9);
		assertEquals(0.0, c[0] * 0.6 + c[2] * -0.8, 1e-9);
		// a degenerate vector still gives a unit axis
		assertEquals(1.0, len(BlockFxMath.bladeAxis(0, 0, 0.2, false)), 1e-9);
	}

	@Test
	void contactPointIsInFrontOfTheDefenderAtChestHeight() {
		double[] c = BlockFxMath.contactPoint(10, 64, -3, 1.8, 0, 2, 0.6, 0.62);
		assertArrayEquals(new double[] {10, 64 + 1.8 * 0.62, -3 + 0.6}, c, 1e-9, "the vector is normalised: 2 blocks away still gives 0.6");
		double[] d = BlockFxMath.contactPoint(1, 2, 3, 2.0, 0, 0, 0.6, 0.5);
		assertArrayEquals(new double[] {1, 3, 3}, d, 1e-9, "no direction, no forward offset");
	}

	@Test
	void slidingSparksRunAlongTheBlade() {
		double[] axis = BlockFxMath.bladeAxis(0, 1, Math.toRadians(35), false);
		double[] normal = {0, 0, 1};
		double[][] v = BlockFxMath.slideVelocities(42L, 60, axis, normal, 3.5, 11.0, 1.0, 0.6, 0.25);
		assertEquals(60, v.length);
		double alongSum = 0;
		for (double[] s : v) {
			assertTrue(Double.isFinite(s[0]) && Double.isFinite(s[1]) && Double.isFinite(s[2]));
			double along = dot(s, axis);
			assertTrue(along > 0, "bias 1: every spark leaves in the +axis direction, got " + along);
			assertTrue(len(s) <= 11.0 * 1.3, "no spark is faster than the table allows: " + len(s));
			alongSum += along;
		}
		assertTrue(alongSum / v.length > 3.0, "the average spark really slides along the blade");
		// a 50/50 bias sends sparks both ways
		double[][] both = BlockFxMath.slideVelocities(7L, 80, axis, normal, 3.5, 11.0, 0.5, 0.6, 0.25);
		int forward = 0;
		for (double[] s : both) {
			forward += dot(s, axis) > 0 ? 1 : 0;
		}
		assertTrue(forward > 15 && forward < 65, "both directions are used: " + forward);
	}

	@Test
	void velocityTablesAreDeterministicAndBounded() {
		double[] axis = {1, 0, 0};
		double[] normal = {0, 0, 1};
		assertTrue(java.util.Arrays.deepEquals(BlockFxMath.slideVelocities(5L, 20, axis, normal, 3, 9, 0.8, 0.5, 0.2), BlockFxMath.slideVelocities(5L, 20, axis, normal, 3, 9, 0.8, 0.5, 0.2)));
		assertTrue(java.util.Arrays.deepEquals(BlockFxMath.burstVelocities(5L, 20, normal, 1.2, 6, 19), BlockFxMath.burstVelocities(5L, 20, normal, 1.2, 6, 19)));
		assertEquals(BlockFxMath.MAX_SPARKS, BlockFxMath.slideVelocities(1L, 9999, axis, normal, 3, 9, 0.8, 0.5, 0.2).length, "the table is capped");
		assertEquals(0, BlockFxMath.burstVelocities(1L, -5, normal, 1.2, 6, 19).length);
	}

	@Test
	void burstStaysInsideTheConeTowardsTheAttacker() {
		double[] normal = {0, 0, 1};
		double cone = Math.toRadians(80);
		double[][] v = BlockFxMath.burstVelocities(99L, 100, normal, cone, 6.0, 19.0);
		assertEquals(100, v.length);
		for (double[] s : v) {
			double speed = len(s);
			assertTrue(speed >= 6.0 - 1e-9 && speed <= 19.0 + 1e-9, "speed inside the range: " + speed);
			assertTrue(dot(s, normal) / speed >= Math.cos(cone) - 1e-9, "inside the cone of 80 degrees");
		}
	}

	@Test
	void ringEasesOutFromTheStartToTheEnd() {
		assertEquals(0.2, BlockFxMath.ringRadius(0.2, 2.2, 0.0, 0.25), 1e-9);
		assertEquals(2.2, BlockFxMath.ringRadius(0.2, 2.2, 0.25, 0.25), 1e-9);
		assertEquals(2.2, BlockFxMath.ringRadius(0.2, 2.2, 9.0, 0.25), 1e-9, "past the life it stays at the end radius");
		double mid = BlockFxMath.ringRadius(0.2, 2.2, 0.125, 0.25);
		assertTrue(mid > 1.2, "ease out: more than half way after half the time: " + mid);
		assertEquals(2.2, BlockFxMath.ringRadius(0.2, 2.2, 0.0, 0.0), 1e-9, "zero life jumps to the end");
	}
}
