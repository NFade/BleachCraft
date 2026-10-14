package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Shukei: Hakuteiken curves (6.3). */
class HakuteikenCurvesTest {
	private static final HakuteikenCurves.Timing T = new HakuteikenCurves.Timing();

	@Test
	void wingsFanFromFoldedToTheFullFanInAboutHalfASecond() {
		assertEquals(0.0, HakuteikenCurves.fan(T, 0.49), 1e-9);
		assertEquals(0.0, HakuteikenCurves.fan(T, 0.5), 1e-9);
		double mid = HakuteikenCurves.fan(T, 0.7);
		assertTrue(mid > 0.5 && mid < 1.2, "mid fan " + mid);
		assertEquals(1.0, HakuteikenCurves.fan(T, 0.5 + 0.45), 1e-9);
		assertEquals(1.0, HakuteikenCurves.fan(T, 3.0), 1e-9);
		// ease out back: it overshoots before it settles
		double peak = 0;
		for (double t = 0.5; t < 0.95; t += 0.01) {
			peak = Math.max(peak, HakuteikenCurves.fan(T, t));
		}
		assertTrue(peak > 1.0 && peak < 1.2, "overshoot " + peak);
	}

	@Test
	void fanVertexFoldsToMinusTwelveDegreesAndFifteenPercentLength() {
		double[] out = new double[3];
		// a vertex at the tip of the top feather: 6 m at 48 degrees, curved forward 0.8
		double r = 6.0;
		double x = r * Math.cos(Math.toRadians(48));
		double y = r * Math.sin(Math.toRadians(48));
		HakuteikenCurves.fanVertex(T, 1, x, y, 0.8, 1.0, out);
		assertEquals(x, out[0], 1e-9);
		assertEquals(y, out[1], 1e-9);
		assertEquals(0.8, out[2], 1e-9);
		HakuteikenCurves.fanVertex(T, 1, x, y, 0.8, 0.0, out);
		assertEquals(0.9 * Math.cos(Math.toRadians(-12)), out[0], 1e-9);
		assertEquals(0.9 * Math.sin(Math.toRadians(-12)), out[1], 1e-9);
		assertEquals(0.8 * 0.15 * 0.15, out[2], 1e-9);
		// the right wing is the mirror image
		double[] left = new double[3];
		HakuteikenCurves.fanVertex(T, 1, x, y, 0.8, 0.4, left);
		HakuteikenCurves.fanVertex(T, -1, -x, y, 0.8, 0.4, out);
		assertEquals(-left[0], out[0], 1e-9);
		assertEquals(left[1], out[1], 1e-9);
		assertEquals(left[2], out[2], 1e-9);
		// the root stays put
		HakuteikenCurves.fanVertex(T, 1, 0, 0, 0, 0.3, out);
		assertEquals(0.0, out[0], 1e-12);
	}

	@Test
	void haloBloomsOverThreeHundredFiftyMillisecondsFromSixTenthsAndFades() {
		assertEquals(0.6, HakuteikenCurves.haloScale(T, 0.3), 1e-9);
		assertEquals(1.0, HakuteikenCurves.haloScale(T, 0.5 + 0.35), 1e-9);
		assertEquals(0.0, HakuteikenCurves.haloAlpha(T, 0.4), 1e-9);
		assertEquals(1.0, HakuteikenCurves.haloAlpha(T, 1.0), 1e-9);
		assertEquals(0.0, HakuteikenCurves.haloAlpha(T, 3.0), 1e-9);
		assertTrue(HakuteikenCurves.haloAlpha(T, 2.75) > 0 && HakuteikenCurves.haloAlpha(T, 2.75) < 1);
	}

	@Test
	void wingsDissolveFromTheTipsInward() {
		// before the dissolve nothing is cut
		assertEquals(1.0, HakuteikenCurves.wingAlpha(T, 2.0, 1.0), 1e-9);
		// halfway: the tip (rho 1) is gone, the root (rho 0.1) still there
		double tip = HakuteikenCurves.wingAlpha(T, 2.75, 1.0);
		double root = HakuteikenCurves.wingAlpha(T, 2.75, 0.1);
		assertTrue(tip < root, "tip " + tip + " root " + root);
		assertEquals(0.0, HakuteikenCurves.wingAlpha(T, 3.0, 0.0), 1e-9);
	}

	@Test
	void featherTableMatchesTheModel() {
		assertEquals(48.0, HakuteikenCurves.featherAngleDeg(0), 1e-9);
		assertEquals(-12.0, HakuteikenCurves.featherAngleDeg(8), 1e-9);
		assertEquals(6.0, HakuteikenCurves.featherLength(0), 1e-9);
		assertEquals(2.4, HakuteikenCurves.featherLength(8), 1e-9);
	}

	@Test
	void bladeFormsThenStrikesToTheLineLengthAndShrinksBack() {
		double len = 28;
		assertEquals(0.0, HakuteikenCurves.bladeLength(T, 0.5, len), 1e-9);
		assertTrue(HakuteikenCurves.bladeLength(T, 1.2, len) > 1.0);
		assertEquals(4.0, HakuteikenCurves.bladeLength(T, 1.45, len), 0.05);
		assertEquals(len, HakuteikenCurves.bladeLength(T, 1.5 + 0.08, len), 0.05);
		assertEquals(1.0, HakuteikenCurves.bladeLength(T, 2.6, len), 1e-9);
		assertEquals(0.0, HakuteikenCurves.lineReach(T, 1.49, len), 1e-9);
		assertEquals(len, HakuteikenCurves.lineReach(T, 1.7, len), 0.05);
		assertTrue(HakuteikenCurves.lineReach(T, 1.54, len) > 0.5 * len);
		assertEquals(1.0, HakuteikenCurves.lineAlpha(T, 1.55), 1e-9);
		assertEquals(0.0, HakuteikenCurves.lineAlpha(T, 2.0), 1e-9);
	}

	@Test
	void rippleStandsStillDuringTheHeldBreath() {
		double a = HakuteikenCurves.rippleTime(T, 1.40);
		double b = HakuteikenCurves.rippleTime(T, 1.49);
		assertEquals(a, b, 1e-9);
		assertTrue(HakuteikenCurves.rippleTime(T, 1.8) < 1.8);
		assertTrue(Math.abs(HakuteikenCurves.ripple(0.3, 6.0)) <= 0.08 + 1e-9);
	}

	@Test
	void gradeGoesToTheStoryboardColoursAndBackToTheNight() {
		double[] base = {0x5A / 255.0, 0x6A / 255.0, 0x9C / 255.0};
		double[] out = new double[3];
		HakuteikenCurves.gradeColour(0.0, base, out);
		assertEquals(base[0], out[0], 1e-9);
		HakuteikenCurves.gradeColour(0.4, base, out);
		assertEquals(0x3C / 255.0, out[0], 1e-9);
		assertEquals(0x48 / 255.0, out[1], 1e-9);
		assertEquals(0x70 / 255.0, out[2], 1e-9);
		HakuteikenCurves.gradeColour(1.35, base, out);
		assertEquals(0x2E / 255.0, out[0], 1e-9);
		HakuteikenCurves.gradeColour(1.8, base, out);
		assertEquals(0x2A / 255.0, out[0], 1e-9);
		HakuteikenCurves.gradeColour(3.5, base, out);
		assertEquals(base[0], out[0], 1e-9);
		HakuteikenCurves.gradeColour(9.0, base, out);
		assertEquals(base[2], out[2], 1e-9);
		// darker than the night while the blade is out
		HakuteikenCurves.gradeColour(1.8, base, out);
		assertTrue(out[2] < base[2]);
	}

	@Test
	void theLineVanishesRightAfterTheStrike() {
		// R11 T8: bright up to strike + 0.10, gone 0.08 s later, never later than 0.25 s after the strike
		assertEquals(T.strike + 0.10, T.lineFade, 1e-9);
		assertEquals(1.0, HakuteikenCurves.lineAlpha(T, T.strike + 0.05), 1e-9);
		assertEquals(1.0, HakuteikenCurves.lineAlpha(T, T.lineFade), 1e-9);
		assertEquals(0.0, HakuteikenCurves.lineAlpha(T, T.lineFade + T.lineFadeSpan), 1e-9);
		assertTrue(T.lineFade + T.lineFadeSpan <= T.strike + 0.25);
		assertEquals(0.0, HakuteikenCurves.lineAlpha(T, T.strike + 0.5), 1e-9);
		// the glow at the hand keeps its old fade
		assertEquals(2.20, T.haloFade, 1e-9);
	}
}
