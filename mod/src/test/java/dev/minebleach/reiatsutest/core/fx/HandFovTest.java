package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R12 round 4: the first person hand is drawn with a fixed fov of 70, so a world effect on the sword must scale the offset across the view. */
class HandFovTest {
	@Test
	void atTheHandFovNothingChanges() {
		assertEquals(1.0, HandFov.scale(70.0), 1e-12);
	}

	@Test
	void aWiderWorldFovPushesThePointOutAcrossTheView() {
		assertTrue(HandFov.scale(90.0) > 1.0);
		assertTrue(HandFov.scale(110.0) > HandFov.scale(90.0));
		assertTrue(HandFov.scale(50.0) < 1.0);
	}

	@Test
	void reproducesTheMeasurementOfTheScreenshot() {
		// the blade came out at 0.62 of its distance from the screen centre in a 96.6 degree world: the correction is the inverse
		assertEquals(1.0 / 0.62, HandFov.scale(96.6), 0.01);
		// the point at the screen position of the hand pass lands on the same screen position: x / (z tan(70/2)) == x' / (z tan(F/2))
		double f = 100.0;
		double x = 0.3;
		double z = 0.8;
		double hand = x / (z * Math.tan(Math.toRadians(HandFov.HAND_FOV) / 2));
		double world = x * HandFov.scale(f) / (z * Math.tan(Math.toRadians(f) / 2));
		assertEquals(hand, world, 1e-12);
	}

	@Test
	void theValueIsClamped() {
		assertTrue(Double.isFinite(HandFov.scale(0)));
		assertTrue(Double.isFinite(HandFov.scale(500)));
	}

	@Test
	void theProjectionOfTheFrameGivesTheSameFactorAsTheFov() {
		// m11 of a perspective matrix is 1 / tan(fov / 2): a sprinting player's wider fov is read from the frame itself
		for (double fov : new double[] {70.0, 85.0, 96.6, 110.0, 130.0}) {
			double m11 = 1.0 / Math.tan(Math.toRadians(fov) / 2);
			assertEquals(HandFov.scale(fov), HandFov.scaleFromTan(1.0 / m11), 1e-12);
		}
		assertTrue(HandFov.scaleFromTan(Math.tan(Math.toRadians(55))) > HandFov.scaleFromTan(Math.tan(Math.toRadians(48))), "sprint widens it");
	}
}
