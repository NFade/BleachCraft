package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UryuShunpoMathTest {
	@Test
	void dashCountFollowsTier() {
		assertEquals(20, UryuShunpoMath.dashCount(160, false));
		assertEquals(10, UryuShunpoMath.dashCount(160, true));
		assertEquals(4, UryuShunpoMath.dashCount(0, false));
		assertEquals(20, UryuShunpoMath.dashCount(10000, false));
	}

	@Test
	void imageCountIsCapped() {
		assertEquals(3, UryuShunpoMath.imageCount(3, false));
		assertEquals(3, UryuShunpoMath.imageCount(9, false));
		assertEquals(2, UryuShunpoMath.imageCount(3, true));
		assertEquals(1, UryuShunpoMath.imageCount(0, false));
	}

	@Test
	void midDiscOnlyOnLongPaths() {
		assertFalse(UryuShunpoMath.hasMidDisc(6.0));
		assertTrue(UryuShunpoMath.hasMidDisc(6.1));
	}

	@Test
	void discLevelRisesAndFades() {
		assertEquals(0.0, UryuShunpoMath.discLevel(-0.1), 1e-9);
		assertEquals(0.0, UryuShunpoMath.discLevel(0.0), 1e-9);
		assertTrue(UryuShunpoMath.discLevel(0.05) > 0.8);
		assertTrue(UryuShunpoMath.discLevel(0.3) < UryuShunpoMath.discLevel(0.1));
		assertEquals(0.0, UryuShunpoMath.discLevel(UryuShunpoMath.DISC_LIFE), 1e-9);
	}

	@Test
	void dashesAreStaggeredAlongTheTrail() {
		assertEquals(0.5, UryuShunpoMath.dashFraction(10, 20, 0.0), 1e-9);
		assertEquals(UryuShunpoMath.TRAIL_TIME, UryuShunpoMath.dashDelay(1.0), 1e-9);
		assertEquals(0.0, UryuShunpoMath.dashDelay(-1.0), 1e-9);
	}
}
