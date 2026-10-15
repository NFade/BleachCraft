package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The light of the drawn bow, URYU_VFX_STORYBOARD 2.2. */
class UryuDrawLightTest {
	private static final double EPS = 1e-9;

	@Test
	void powerIsTheVanillaCurveAndTheInverseRoundTrips() {
		assertEquals(0.0, UryuDrawLight.power(0), EPS);
		assertEquals(1.0, UryuDrawLight.power(24), EPS);
		assertEquals(1.0, UryuDrawLight.power(48), EPS);
		for (double p : new double[] {0.1, 0.25, 0.5, 0.75, 1.0}) {
			assertEquals(p, UryuDrawLight.power(UryuDrawLight.ticksForPower(p)), 1e-9, "p " + p);
		}
		assertEquals(24.0, UryuDrawLight.ticksForPower(1.0), 1e-9);
		assertEquals(3.6, UryuDrawLight.ticksForPower(0.1), 0.3, "p 0.1 is about 3.6 ticks of the 24 tick draw");
		assertEquals(13.94, UryuDrawLight.ticksForPower(0.5), 0.05, "p 0.5 is after 13.9 ticks of the 24 tick draw");
	}

	@Test
	void theStringBrightensOverTheFirstTenthAndTheNockPinpointAppears() {
		assertEquals(0.0, UryuDrawLight.stringMix(0.0), EPS);
		assertEquals(0.5, UryuDrawLight.stringMix(0.05), EPS);
		assertEquals(1.0, UryuDrawLight.stringMix(0.1), EPS);
		assertEquals(1.0, UryuDrawLight.stringMix(0.8), EPS);
		assertEquals(0.0, UryuDrawLight.nockIntensity(0.0), EPS);
		assertEquals(1.0, UryuDrawLight.nockIntensity(0.1), EPS);
		assertEquals(0.12, UryuDrawLight.NOCK_SIZE, EPS);
	}

	@Test
	void reishiFlowsIntoTheNockAtSixPlusTwentySixPerPSeconds() {
		assertEquals(0.0, UryuDrawLight.dashRate(0.0), EPS);
		assertEquals(6.0 + 26.0 * 0.1, UryuDrawLight.dashRate(0.1), EPS);
		assertEquals(32.0, UryuDrawLight.dashRate(1.0), EPS);
		assertEquals(32.0, UryuDrawLight.dashRate(2.0), EPS);
		assertEquals(1.2, UryuDrawLight.DASH_RADIUS, EPS);
		assertEquals(0.18, UryuDrawLight.DASH_LIFE, EPS);
		assertEquals(0.15, UryuDrawLight.arrowStreakLength(0.0), EPS);
		assertEquals(0.60, UryuDrawLight.arrowStreakLength(1.0), EPS);
	}

	@Test
	void theTipsAndTheGhostWheelLightFromHalfPower() {
		assertFalse(UryuDrawLight.tipsLit(0.49));
		assertTrue(UryuDrawLight.tipsLit(0.5));
		assertEquals(0.0, UryuDrawLight.ghostAlpha(0.3), EPS);
		assertEquals(0.12, UryuDrawLight.ghostAlpha(0.6), EPS);
		for (double t = 0; t < 2; t += 0.013) {
			double tw = UryuDrawLight.tipTwinkle(t);
			assertTrue(tw >= 0.4 - 1e-9 && tw <= 1.0 + 1e-9, "twinkle in range " + tw);
		}
	}

	@Test
	void theFullDrawPingRingGrowsAndFades() {
		assertTrue(UryuDrawLight.fullDraw(1.0));
		assertFalse(UryuDrawLight.fullDraw(0.99));
		assertEquals(0.2, UryuDrawLight.pingRingSize(0.0), EPS);
		assertEquals(0.7, UryuDrawLight.pingRingSize(0.18), EPS);
		assertEquals(0.0, UryuDrawLight.pingRingSize(0.19), EPS);
		assertEquals(0.0, UryuDrawLight.pingRingSize(-0.01), EPS);
		assertEquals(1.0, UryuDrawLight.pingRingAlpha(0.0), EPS);
		assertEquals(0.0, UryuDrawLight.pingRingAlpha(0.18), EPS);
		assertEquals(1.0, UryuDrawLight.pingStarAlpha(0.0), EPS);
		assertEquals(0.0, UryuDrawLight.pingStarAlpha(0.11), EPS);
	}

	@Test
	void theGlowBreathesBetweenEightyFiveAndOneHundredPercentOnlyAtFullDraw() {
		assertEquals(1.0, UryuDrawLight.breath(0.3, false, 0.5), EPS);
		double lo = 2;
		double hi = 0;
		for (double t = 0; t < 2; t += 0.001) {
			double b = UryuDrawLight.breath(t, false, 1.0);
			lo = Math.min(lo, b);
			hi = Math.max(hi, b);
		}
		assertEquals(0.85, lo, 0.001);
		assertEquals(1.0, hi, 0.001);
		assertEquals(0.92, UryuDrawLight.breath(0.123, true, 1.0), EPS, "reduce motion: a constant");
		assertEquals(0.92, UryuDrawLight.breath(0.9, true, 1.0), EPS);
	}

	@Test
	void theNockSitsBehindTheGripByTheStringDistanceAndTheDrawTravel() {
		double idle = UryuDrawLight.nockDistance(0.0, 1.0);
		assertEquals(0.105 + 0.04, idle, EPS);
		double full = UryuDrawLight.nockDistance(1.0, 1.0);
		assertEquals(0.105 + 0.04 + 0.26, full, EPS);
		assertEquals(1.4 * full, UryuDrawLight.nockDistance(1.0, 1.4), EPS);
		assertTrue(UryuDrawLight.nockDistance(0.5, 1.4) > UryuDrawLight.nockDistance(0.2, 1.4));
		assertEquals(0.77, UryuDrawLight.tipDistance(1.4), EPS);
	}
}
