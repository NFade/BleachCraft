package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void swordThrowHangsThenSinksSlowlyAndStays() {
		double hang = 1.05;
		double end = -0.4;
		assertEquals(0.0, BankaiCurves.swordFlight(0), 1e-9);
		assertEquals(1.0, BankaiCurves.swordFlight(0.3), 1e-9);
		// hangs (within the sway) until the sink starts
		assertEquals(hang, BankaiCurves.swordHeight(0.4, hang, end), 0.03);
		assertEquals(hang, BankaiCurves.swordHeight(BankaiCurves.SWORD_HANG_END - 0.01, hang, end), 0.03);
		// monotone descent after that, ends at endY and stays there
		double prev = BankaiCurves.swordHeight(BankaiCurves.SWORD_HANG_END, hang, end);
		for (double t = BankaiCurves.SWORD_HANG_END + 0.02; t <= BankaiCurves.SWORD_SINK_END; t += 0.02) {
			double h = BankaiCurves.swordHeight(t, hang, end);
			assertTrue(h <= prev + 1e-9, "t " + t);
			prev = h;
		}
		assertEquals(end, BankaiCurves.swordHeight(BankaiCurves.SWORD_SINK_END, hang, end), 1e-9);
		assertEquals(end, BankaiCurves.swordHeight(5.0, hang, end), 1e-9);
		// slow: the whole descent takes about 0.9 s, the ground is touched in the middle of it
		double c = BankaiCurves.swordContactTime(hang, end);
		assertTrue(c > BankaiCurves.SWORD_HANG_END + 0.3 && c < BankaiCurves.SWORD_SINK_END, "contact " + c);
		assertEquals(0.0, BankaiCurves.swordHeight(c, hang, end), 1e-6);
		// splash: nothing before the contact, rings grow and fade
		assertEquals(0.0, BankaiCurves.splashAlpha(0, c - 0.01, c), 1e-9);
		assertEquals(0.0, BankaiCurves.splashRadius(0, c, c, 1.0), 1e-9);
		assertEquals(3.5, BankaiCurves.splashRadius(0, c + 2, c, 1.0), 1e-9);
		assertEquals(0.0, BankaiCurves.splashAlpha(0, c + 1.0, c), 1e-9);
		assertTrue(BankaiCurves.splashAlpha(1, c + 0.5, c) > 0.5);
	}

	@Test
	void swordVanishesCompletelyWhenSunk() {
		double len = BankaiCurves.HILT_LENGTH * 1.8;
		double end = -len - BankaiCurves.SWORD_HIDE_MARGIN; // swordEndY with bankai.hiltRemain = 0
		assertTrue(BankaiCurves.swordVisible(BankaiCurves.swordHeight(0.4, 1.0, end), len));
		// at the ground contact the sword is still above the ground; after the sinking nothing of it is
		double c = BankaiCurves.swordContactTime(1.0, end);
		assertTrue(BankaiCurves.swordVisible(BankaiCurves.swordHeight(c, 1.0, end), len));
		assertFalse(BankaiCurves.swordVisible(BankaiCurves.swordHeight(BankaiCurves.SWORD_SINK_END, 1.0, end), len));
		assertFalse(BankaiCurves.swordVisible(BankaiCurves.swordHeight(5.0, 1.0, end), len));
		// a remainder (bankai.hiltRemain > 0, the old behaviour) stays visible
		assertTrue(BankaiCurves.swordVisible(-(len - 0.38), len));
	}
}
