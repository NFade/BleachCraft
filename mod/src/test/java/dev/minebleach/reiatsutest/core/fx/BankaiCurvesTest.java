package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BankaiCurvesTest {
	@Test
	void hiltFallsThenSinksHalfABlock() {
		assertEquals(1.1, BankaiCurves.hiltHeight(0, 1.1), 1e-9);
		assertEquals(0.0, BankaiCurves.hiltHeight(0.18, 1.1), 1e-9);
		assertEquals(-0.5, BankaiCurves.hiltHeight(0.8, 1.1), 1e-9);
	}

	@Test
	void ripplesStartInOrderAndGrowToTheirRadius() {
		assertEquals(0.0, BankaiCurves.rippleRadius(0, 0.1), 1e-9);
		assertEquals(6.0, BankaiCurves.rippleRadius(0, 1.5), 1e-9);
		assertEquals(4.5, BankaiCurves.rippleRadius(1, 2.0), 1e-9);
		assertEquals(3.0, BankaiCurves.rippleRadius(2, 2.0), 1e-9);
		assertEquals(0.0, BankaiCurves.rippleAlpha(2, 0.3), 1e-9);
		assertEquals(0.0, BankaiCurves.rippleAlpha(0, 1.2), 1e-9);
	}

	@Test
	void sweepRunsFromNearToFarOnce() {
		double c = 2.2;
		assertTrue(BankaiCurves.sweepBoost(2, c + 0.05, c) > 0.1);
		assertEquals(0.0, BankaiCurves.sweepBoost(30, c + 0.05, c), 1e-9);
		assertTrue(BankaiCurves.sweepBoost(30, c + 0.75, c) > 0.3);
	}

	@Test
	void gradeRampIsZeroBeforeNightFallsAndFullAfterNinetyHundredths() {
		assertEquals(0.0, BankaiCurves.gradeRamp(0.2), 1e-9);
		assertEquals(1.0, BankaiCurves.gradeRamp(0.95), 1e-9);
		assertEquals(0.5, BankaiCurves.gradeRamp(0.6), 1e-9);
	}

	@Test
	void faultLineLeadsTheEruption() {
		for (double d = 0; d <= 110; d += 5) {
			assertTrue(BankaiCurves.faultTime(d, 100) <= 1.0 + BladeRowLayout.ERUPT_PER_BLOCK * d + 0.001 || d < 1, "d " + d);
		}
	}
}
