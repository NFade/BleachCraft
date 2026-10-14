package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The bow forming timeline of URYU_VFX_STORYBOARD 2.1. */
class UryuBowFormTest {
	private static final double EPS = 1e-9;

	@Test
	void theEventTimesAreTheRowsOfTheStoryboard() {
		assertEquals(6, UryuBowForm.EVENT_TIMES.length);
		assertEquals(0.00, UryuBowForm.EVENT_TIMES[UryuBowForm.EV_GATHER], EPS);
		assertEquals(0.10, UryuBowForm.EVENT_TIMES[UryuBowForm.EV_WHEEL], EPS);
		assertEquals(0.18, UryuBowForm.EVENT_TIMES[UryuBowForm.EV_LIMBS], EPS);
		assertEquals(0.30, UryuBowForm.EVENT_TIMES[UryuBowForm.EV_SOLID], EPS);
		assertEquals(0.38, UryuBowForm.EVENT_TIMES[UryuBowForm.EV_STRING], EPS);
		assertEquals(0.42, UryuBowForm.EVENT_TIMES[UryuBowForm.EV_SETTLE], EPS);
		for (int i = 1; i < UryuBowForm.EVENT_TIMES.length; i++) {
			assertTrue(UryuBowForm.EVENT_TIMES[i] > UryuBowForm.EVENT_TIMES[i - 1]);
		}
		assertEquals(0.45, UryuBowForm.DURATION, EPS);
		assertTrue(UryuBowForm.TOTAL > UryuBowForm.T_WHEEL_END);
		assertEquals(40, UryuBowForm.GATHER_DASHES);
		assertEquals(24, UryuBowForm.GATHER_MOTES);
		assertEquals(12, UryuBowForm.GATHER_SPECKS);
		assertEquals(6, UryuBowForm.SETTLE_FLAKES);
		assertEquals(14, UryuBowForm.SETTLE_FLAKE_LIFE_TICKS);
	}

	@Test
	void theWheelOpensGrowsHoldsShrinksAndGoes() {
		assertEquals(0.0, UryuBowForm.wheelSize(0.05, false), EPS);
		assertEquals(UryuBowForm.WHEEL_FROM, UryuBowForm.wheelSize(0.10, false), EPS);
		double mid = UryuBowForm.wheelSize(0.20, false);
		assertTrue(mid > UryuBowForm.WHEEL_FROM && mid < UryuBowForm.WHEEL_TO, "growing at 0.20: " + mid);
		assertEquals(UryuBowForm.WHEEL_TO, UryuBowForm.wheelSize(0.36, false), 0.01, "easeOutExpo has almost arrived 0.25 s after the start");
		assertEquals(UryuBowForm.WHEEL_TO, UryuBowForm.wheelSize(0.42, false), 0.01);
		assertEquals(UryuBowForm.WHEEL_SETTLE_SIZE, UryuBowForm.wheelSize(0.5499, false), 0.01);
		assertEquals(0.0, UryuBowForm.wheelSize(0.55, false), EPS);
		// monotone: up to 0.42, then down
		double prev = 0;
		for (double t = 0.10; t <= 0.42; t += 0.01) {
			double s = UryuBowForm.wheelSize(t, false);
			assertTrue(s >= prev - 1e-12, "grows at " + t);
			prev = s;
		}
		for (double t = 0.43; t < 0.55; t += 0.01) {
			double s = UryuBowForm.wheelSize(t, false);
			assertTrue(s <= prev + 1e-12, "shrinks at " + t);
			prev = s;
		}
	}

	@Test
	void theFirstPersonWheelNeverExceedsTheCrosshairCap() {
		for (double t = 0; t < 0.7; t += 0.005) {
			assertTrue(UryuBowForm.wheelSize(t, true) <= UryuBowForm.FP_WHEEL_CAP + EPS, "t " + t);
		}
		assertEquals(UryuBowForm.FP_WHEEL_CAP, UryuBowForm.wheelSize(0.40, true), EPS);
		assertEquals(0.55, UryuBowForm.FP_WHEEL_CAP, EPS);
	}

	@Test
	void theWheelEnvelopeAndRotation() {
		assertEquals(0.0, UryuBowForm.wheelEnvelope(0.09), EPS);
		assertEquals(1.0, UryuBowForm.wheelEnvelope(0.30), EPS);
		assertEquals(0.0, UryuBowForm.wheelEnvelope(0.55), EPS);
		assertTrue(UryuBowForm.wheelEnvelope(0.12) > 0 && UryuBowForm.wheelEnvelope(0.12) < 1);
		assertEquals(0.9, UryuBowForm.wheelAlpha(0.30), EPS);
		assertEquals(0.35, UryuBowForm.haloAlpha(0.30), EPS);
		assertEquals(0.0, UryuBowForm.wheelRotation(0.05), EPS);
		assertEquals(2 * Math.PI * 0.4 * 0.25, UryuBowForm.wheelRotation(0.35), 1e-9, "0.4 revolutions per second");
	}

	@Test
	void theLimbsShootOutThenFadeAsTheSolidBowTakesOver() {
		assertEquals(0.0, UryuBowForm.limbLength(0.17), EPS);
		assertEquals(0.0, UryuBowForm.limbLength(0.18), EPS);
		assertEquals(UryuBowForm.LIMB_LENGTH, UryuBowForm.limbLength(0.38 + 0.2), EPS);
		assertEquals(0.55, UryuBowForm.LIMB_LENGTH, EPS);
		assertTrue(UryuBowForm.limbLength(0.30) > 0.5 * UryuBowForm.LIMB_LENGTH, "easeOutExpo is mostly there by 0.30");
		assertEquals(0.0, UryuBowForm.limbAlpha(0.10), EPS);
		assertEquals(1.0, UryuBowForm.limbAlpha(0.20), EPS);
		assertEquals(1.0, UryuBowForm.limbAlpha(0.30), EPS);
		assertEquals(0.5, UryuBowForm.limbAlpha(0.375), 1e-9);
		assertEquals(0.0, UryuBowForm.limbAlpha(0.45), EPS);
	}

	@Test
	void sixNotchesPopOneAfterTheOtherEveryTwoHundredthOfASecond() {
		assertEquals(6, UryuBowForm.NOTCHES);
		for (int k = 0; k < UryuBowForm.NOTCHES; k++) {
			assertEquals(0.18 + 0.02 * k, UryuBowForm.notchTime(k), EPS);
			assertEquals((k + 1) / 6.0, UryuBowForm.notchFraction(k), EPS);
			assertEquals(1.0, UryuBowForm.notchAlpha(k, UryuBowForm.notchTime(k)), EPS);
			assertEquals(0.0, UryuBowForm.notchAlpha(k, UryuBowForm.notchTime(k) - 0.001), EPS);
			assertEquals(0.0, UryuBowForm.notchAlpha(k, UryuBowForm.notchTime(k) + 0.11), EPS);
		}
		assertEquals(1.0, UryuBowForm.notchFraction(UryuBowForm.NOTCHES - 1), EPS, "the last notch is at the tip");
	}

	@Test
	void thePendantIsReplacedByTheBowAtThirtyHundredths() {
		assertTrue(UryuBowForm.pendantShown(0.0));
		assertTrue(UryuBowForm.pendantShown(0.29));
		assertFalse(UryuBowForm.pendantShown(0.30));
		assertEquals(1.0, UryuBowForm.pendantBoost(0.0), EPS);
		assertEquals(1.6, UryuBowForm.pendantBoost(0.10), EPS);
		assertEquals(1.6, UryuBowForm.pendantBoost(0.25), EPS);
		assertTrue(UryuBowForm.pendantBoost(0.05) > 1.0 && UryuBowForm.pendantBoost(0.05) < 1.6);
		assertEquals(0.0, UryuBowForm.bowAlpha(0.29), EPS);
		assertEquals(0.0, UryuBowForm.bowAlpha(0.30), EPS);
		assertEquals(0.5, UryuBowForm.bowAlpha(0.375), 1e-9);
		assertEquals(1.0, UryuBowForm.bowAlpha(0.45), EPS);
		assertEquals(1.0, UryuBowForm.bowAlpha(1.0), EPS);
		assertTrue(UryuBowForm.itemForming(0.0));
		assertTrue(UryuBowForm.itemForming(0.449));
		assertFalse(UryuBowForm.itemForming(0.45));
		assertFalse(UryuBowForm.itemForming(-0.1));
	}

	@Test
	void theLimbsRelaxFromEightDegreesThroughTheFlexBowsToTheRestBow() {
		assertEquals(8.0, UryuBowForm.bendDegrees(0.0), EPS);
		assertEquals(8.0, UryuBowForm.bendDegrees(0.30), EPS);
		assertEquals(0.0, UryuBowForm.bendDegrees(0.45), EPS);
		assertEquals(3, UryuBowForm.flexIndex(0.30), "8 degrees is nearest to the 9 degree flex bow");
		assertEquals(0, UryuBowForm.flexIndex(0.45));
		int prev = 3;
		for (double t = 0.30; t <= 0.451; t += 0.005) {
			double bend = UryuBowForm.bendDegrees(t);
			assertTrue(bend >= 0, "never negative: " + bend);
			int idx = UryuBowForm.flexIndex(t);
			assertTrue(idx >= 0 && idx <= UryuBowForm.FLEX_MAX);
			assertTrue(idx <= prev, "the bend only relaxes: t " + t + " idx " + idx + " prev " + prev);
			prev = idx;
		}
	}

	@Test
	void theStringSweepRunsFor70MillisecondsFrom380() {
		assertEquals(-1.0, UryuBowForm.stringSweep(0.37), EPS);
		assertEquals(0.0, UryuBowForm.stringSweep(0.38), EPS);
		assertEquals(0.5, UryuBowForm.stringSweep(0.415), 1e-9);
		assertEquals(1.0, UryuBowForm.stringSweep(0.4499), 0.01);
		assertEquals(-1.0, UryuBowForm.stringSweep(0.46), EPS);
	}

	@Test
	void theGatherDirectionsCoverTheSphereEvenly() {
		int n = UryuBowForm.GATHER_DASHES;
		double sx = 0;
		double sy = 0;
		double sz = 0;
		double minY = 2;
		double maxY = -2;
		for (int i = 0; i < n; i++) {
			double[] d = UryuBowForm.gatherDirection(i, n);
			assertEquals(1.0, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
			sx += d[0];
			sy += d[1];
			sz += d[2];
			minY = Math.min(minY, d[1]);
			maxY = Math.max(maxY, d[1]);
		}
		assertTrue(Math.abs(sx) < 3 && Math.abs(sy) < 0.5 && Math.abs(sz) < 3, "balanced: " + sx + " " + sy + " " + sz);
		assertTrue(minY < -0.9 && maxY > 0.9, "from pole to pole");
		assertEquals(3.0, UryuBowForm.GATHER_RADIUS, EPS);
		assertEquals(0.30, UryuBowForm.GATHER_LIFE, EPS);
	}

	@Test
	void theReverseIsShorterAndQuieter() {
		assertTrue(UryuBowForm.REVERSE_DURATION < UryuBowForm.DURATION);
		assertEquals(1.0, UryuBowForm.reverseWheelAlpha(0.0), EPS);
		assertEquals(0.0, UryuBowForm.reverseWheelAlpha(0.10), EPS);
		assertEquals(1.0, UryuBowForm.reverseOutlineAlpha(0.0), EPS);
		assertEquals(0.0, UryuBowForm.reverseOutlineAlpha(0.2), EPS);
		assertEquals(0.15, UryuBowForm.REVERSE_WHEEL_SIZE, EPS);
		assertEquals(16, UryuBowForm.REVERSE_DASHES);
		assertEquals(-1.0, UryuBowForm.reverseDashPosition(0), EPS);
		assertEquals(1.0, UryuBowForm.reverseDashPosition(UryuBowForm.REVERSE_DASHES - 1), EPS);
	}
}
