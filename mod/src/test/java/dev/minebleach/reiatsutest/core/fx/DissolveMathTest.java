package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DissolveMathTest {
	@Test
	void frontStartsAndEndsOutsideTheBlade() {
		assertEquals(-DissolveMath.MARGIN, DissolveMath.boundary(0, false), 1e-9);
		assertEquals(1 + DissolveMath.MARGIN, DissolveMath.boundary(1, false), 1e-9);
		assertEquals(1 + DissolveMath.MARGIN, DissolveMath.boundary(0, true), 1e-9);
		assertEquals(-DissolveMath.MARGIN, DissolveMath.boundary(1, true), 1e-9);
	}

	@Test
	void hiltFirstRemovesTheBladeFromTheBaseUpward() {
		double early = DissolveMath.boundary(0.2, false);
		double late = DissolveMath.boundary(0.8, false);
		assertTrue(early < late);
		// a point near the hilt goes first, a point near the tip last
		assertFalse(DissolveMath.keep(0.1, late, false));
		assertTrue(DissolveMath.keep(0.98, late, false));
		assertTrue(DissolveMath.keep(0.1, early, false));
	}

	@Test
	void tipFirstIsTheMirrorImage() {
		double early = DissolveMath.boundary(0.2, true);
		double late = DissolveMath.boundary(0.8, true);
		assertTrue(late < early);
		assertFalse(DissolveMath.keep(0.9, late, true));
		assertTrue(DissolveMath.keep(0.03, late, true));
	}

	@Test
	void tipFirstTakesTheTopOfTheBladeBeforeTheHilt() {
		// the default: the top (tip, held up) goes first, so the point near the tip is gone while the one near the hilt is still there
		double p = 0.2;
		double b = DissolveMath.boundary(p, true);
		assertFalse(DissolveMath.keep(0.95, b, true));
		assertTrue(DissolveMath.keep(0.1, b, true));
	}

	@Test
	void everythingIsKeptAtTheStartAndGoneAtTheEnd() {
		for (boolean tipFirst : new boolean[] {false, true}) {
			double start = DissolveMath.boundary(0, tipFirst);
			double end = DissolveMath.boundary(1, tipFirst);
			for (double s = 0; s <= 1.0001; s += 0.05) {
				assertTrue(DissolveMath.keep(s, start, tipFirst), "start keeps s=" + s);
				assertFalse(DissolveMath.keep(s, end, tipFirst), "end drops s=" + s);
			}
		}
	}

	@Test
	void alongMeasuresTheBladeFractionAndIsZeroForADegenerateAxis() {
		// blade from (0,0,0) to (0,2,0): the point (0,1,0) is halfway, (0,0.5,3) is a quarter (projection ignores the sideways offset)
		assertEquals(0.5, DissolveMath.along(0, 1, 0, 0, 0, 0, 0, 1, 0, 2), 1e-9);
		assertEquals(0.25, DissolveMath.along(0, 0.5, 3, 0, 0, 0, 0, 1, 0, 2), 1e-9);
		assertEquals(0.0, DissolveMath.along(1, 1, 1, 0, 0, 0, 0, 1, 0, 0), 1e-9);
	}

	@Test
	void glowShellFadesOutOverTheLastThirtyPercent() {
		assertEquals(1.0, DissolveMath.shellAlpha(0), 1e-9);
		assertEquals(1.0, DissolveMath.shellAlpha(0.7), 1e-9);
		assertEquals(0.0, DissolveMath.shellAlpha(1), 1e-9);
		assertTrue(DissolveMath.shellAlpha(0.85) > 0 && DissolveMath.shellAlpha(0.85) < 1);
	}

	@Test
	void hiltReturnsToItsOwnColour() {
		assertEquals(0.0, DissolveMath.hiltMix(0), 1e-9);
		assertEquals(1.0, DissolveMath.hiltMix(1), 1e-9);
	}
}
