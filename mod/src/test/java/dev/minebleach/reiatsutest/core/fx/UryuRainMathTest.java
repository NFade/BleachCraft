package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Licht Regen square numbers ({@link UryuRainMath}). */
class UryuRainMathTest {
	@Test
	void snapsToTheBlockCentre() {
		assertEquals(3.5, UryuRainMath.snap(3.9), 1.0E-9);
		assertEquals(-0.5, UryuRainMath.snap(-0.2), 1.0E-9);
	}

	@Test
	void outlineGrowsInQuarterSecondAndFades() {
		assertEquals(0.0, UryuRainMath.outlineGrow(0.0), 1.0E-9);
		assertEquals(1.0, UryuRainMath.outlineGrow(0.25), 1.0E-9);
		assertTrue(UryuRainMath.outlineGrow(0.1) > 0.5);
		assertEquals(0.0, UryuRainMath.outlineLevel(3.0, 3.0), 1.0E-9);
		assertEquals(1.0, UryuRainMath.outlineLevel(1.0, 3.0), 1.0E-9);
	}

	@Test
	void cardRisesAndFades() {
		assertEquals(0.0, UryuRainMath.cardLevel(0.0, 3.0), 1.0E-9);
		assertEquals(1.0, UryuRainMath.cardLevel(1.5, 3.0), 1.0E-9);
		assertEquals(0.0, UryuRainMath.cardLevel(3.0, 3.0), 1.0E-9);
	}

	@Test
	void landingsAndTiers() {
		assertTrue(UryuRainMath.isLanding(0.5, 10, 0.5, 4.0, 10.2, -4.0));
		assertFalse(UryuRainMath.isLanding(0.5, 10, 0.5, 9.0, 10.2, 0.0));
		assertFalse(UryuRainMath.isLanding(0.5, 10, 0.5, 0.0, 14.0, 0.0));
		int low = 0;
		int high = 0;
		for (int n = 0; n < 60; n++) {
			low += UryuRainMath.landingDrawn(n, 0.2, false) ? 1 : 0;
			high += UryuRainMath.landingDrawn(n, 1.0, false) ? 1 : 0;
		}
		assertEquals(20, low);
		assertEquals(60, high);
		assertTrue(UryuRainMath.landingDrawn(1, 0.2, true));
		assertFalse(UryuRainMath.landingPuff(6, 0.2, false));
		assertTrue(UryuRainMath.landingPuff(6, 1.0, false));
	}
}
