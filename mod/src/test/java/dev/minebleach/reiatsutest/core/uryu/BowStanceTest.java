package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The clip positions of the arms are in the turned frame; the compensation carries them back so that the whole-model turn lands them where they were drawn. */
class BowStanceTest {
	@Test
	void leftShoulderOfTheClipReturnsToItsNaturalPivot() {
		// uryu_bow_draw at the full draw (tick 21): the left arm pivot (1.21, -4.851) with the body yaw of the stance 76 degrees to the right (-76 applied)
		double[] p = BowStance.rotateXZ(1.21, -4.851, Math.toRadians(-76.0));
		assertEquals(5.0, p[0], 0.02);
		assertEquals(0.0, p[1], 0.02);
	}

	@Test
	void roundTripIsTheIdentity() {
		double[] p = BowStance.rotateXZ(-3.2, 4.7, 0.9);
		double[] q = BowStance.rotateXZ(p[0], p[1], -0.9);
		assertEquals(-3.2, q[0], 1e-9);
		assertEquals(4.7, q[1], 1e-9);
	}

	@Test
	void zeroAngleKeepsThePoint() {
		double[] p = BowStance.rotateXZ(5.0, 2.0, 0.0);
		assertEquals(5.0, p[0], 1e-12);
		assertEquals(2.0, p[1], 1e-12);
	}
}
