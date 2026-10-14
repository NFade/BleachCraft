package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PressurePlanTest {
	private static PressurePlan plan(double k, boolean reduceMotion, boolean photoSafe) {
		return PressurePlan.of(k, 1.0, 1.0, PressurePlan.DEFAULT_SHAKE, 1.0, 1.0, reduceMotion, photoSafe);
	}

	@Test
	void defaultsGiveEveryLayer() {
		PressurePlan p = plan(1.0, false, false);
		assertEquals(0.9, p.shakeDeg(), 1e-9);
		assertEquals(0.15, p.tremorDeg(), 1e-9);
		assertEquals(1.0, p.blurScale(), 1e-9);
		assertEquals(1.0, p.stripes(), 1e-9);
		assertEquals(1.0, p.tearScale(), 1e-9);
		assertTrue(p.any());
	}

	@Test
	void reduceMotionTurnsOffTheShakeAndTheStripesButKeepsTheBlur() {
		PressurePlan p = plan(1.0, true, false);
		assertEquals(0.0, p.shakeDeg());
		assertEquals(0.0, p.tremorDeg());
		assertEquals(0.0, p.stripes());
		assertEquals(0.0, p.tearScale());
		assertTrue(p.blurScale() > 0);
	}

	@Test
	void photosensitiveSafeTurnsOffTheStripesAndTheBlurAndTheShake() {
		PressurePlan p = plan(1.0, false, true);
		assertEquals(0.0, p.stripes());
		assertEquals(0.0, p.blurScale());
		assertEquals(0.0, p.tearScale());
		assertEquals(0.0, p.shakeDeg());
		assertEquals(0.0, p.tremorDeg());
		assertFalse(p.any());
		assertFalse(plan(1.0, true, true).any() && plan(1.0, true, true).blurScale() > 0);
	}

	@Test
	void keysOfZeroSwitchOneLayerOffEach() {
		PressurePlan noShake = PressurePlan.of(1.0, 1.0, 1.0, 0.0, 1.0, 1.0, false, false);
		assertEquals(0.0, noShake.shakeDeg());
		assertEquals(0.0, noShake.tremorDeg());
		assertTrue(noShake.blurScale() > 0 && noShake.stripes() > 0);
		PressurePlan noBlur = PressurePlan.of(1.0, 1.0, 1.0, 0.9, 0.0, 1.0, false, false);
		assertEquals(0.0, noBlur.blurScale());
		assertTrue(noBlur.shakeDeg() > 0);
		PressurePlan noStripes = PressurePlan.of(1.0, 1.0, 1.0, 0.9, 1.0, 0.0, false, false);
		assertEquals(0.0, noStripes.stripes());
		assertEquals(0.0, noStripes.tearScale());
	}

	@Test
	void scaleAndStrengthDecideWhetherAnythingRuns() {
		assertFalse(PressurePlan.of(1.0, 0.0, 1.0, 0.9, 1.0, 1.0, false, false).any());
		assertFalse(plan(0.02, false, false).any());
		assertEquals(PressurePlan.OFF, plan(0.0, false, false));
		PressurePlan half = PressurePlan.of(1.0, 0.5, 1.0, 0.9, 1.0, 1.0, false, false);
		assertEquals(0.5, half.strength(), 1e-9);
		assertEquals(0.5, half.stripes(), 1e-9);
		// a scale above 1 cannot push the strength over 1
		assertEquals(1.0, PressurePlan.of(0.8, 3.0, 1.0, 0.9, 1.0, 1.0, false, false).strength(), 1e-9);
	}

	@Test
	void screenFxScaleScalesTheBlurAndTheStripesOnly() {
		PressurePlan p = PressurePlan.of(1.0, 1.0, 0.5, 0.9, 1.0, 1.0, false, false);
		assertEquals(0.5, p.blurScale(), 1e-9);
		assertEquals(0.5, p.stripes(), 1e-9);
		assertEquals(0.9, p.shakeDeg(), 1e-9);
		assertEquals(0.0, PressurePlan.of(1.0, 1.0, 0.0, 0.9, 1.0, 1.0, false, false).stripes());
	}

	@Test
	void negativeKeysAreTreatedAsZero() {
		PressurePlan p = PressurePlan.of(1.0, 1.0, 1.0, -1.0, -1.0, -1.0, false, false);
		assertEquals(0.0, p.shakeDeg());
		assertEquals(0.0, p.blurScale());
		assertEquals(0.0, p.stripes());
	}
}
