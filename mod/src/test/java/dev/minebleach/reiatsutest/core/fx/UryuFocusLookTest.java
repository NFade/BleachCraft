package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Focus feedback of URYU_VFX_STORYBOARD 2.4. */
class UryuFocusLookTest {
	private static final double EPS = 1e-9;

	@Test
	void dashesPerSecondByStackAndTier() {
		assertEquals(0.0, UryuFocusLook.dashRate(0, 1.0), EPS);
		assertEquals(6.0, UryuFocusLook.dashRate(1, 1.0), EPS);
		assertEquals(10.0, UryuFocusLook.dashRate(2, 1.0), EPS);
		assertEquals(14.0, UryuFocusLook.dashRate(3, 1.0), EPS);
		assertEquals(14.0 * 0.75, UryuFocusLook.dashRate(3, 0.5), EPS);
		assertEquals(7.0, UryuFocusLook.dashRate(3, 0.25), EPS, "low: halved");
		assertEquals(14.0, UryuFocusLook.dashRate(7, 1.0), EPS, "clamped to three stacks");
		assertEquals(0.0, UryuFocusLook.dashRate(-1, 1.0), EPS);
		assertEquals(1.6, UryuFocusLook.DASH_RADIUS, EPS);
	}

	@Test
	void layersAddUpAndTheLowTierKeepsOnlyOneAndTwo() {
		for (int stacks = 0; stacks <= 3; stacks++) {
			for (int layer = 1; layer <= 3; layer++) {
				assertEquals(stacks >= layer, UryuFocusLook.layerOn(stacks, layer, 1.0), "high " + stacks + "/" + layer);
				assertEquals(stacks >= layer, UryuFocusLook.layerOn(stacks, layer, 0.5), "medium " + stacks + "/" + layer);
				assertEquals(stacks >= layer && layer <= 2, UryuFocusLook.layerOn(stacks, layer, 0.25), "low " + stacks + "/" + layer);
			}
		}
		assertEquals(3, UryuFocusLook.maxLayer(1.0));
		assertEquals(2, UryuFocusLook.maxLayer(0.25));
	}

	@Test
	void theBowGlowsThirtyPercentMoreFromTheFirstStack() {
		assertEquals(1.0, UryuFocusLook.bowGlow(0), EPS);
		assertEquals(1.3, UryuFocusLook.bowGlow(1), EPS);
		assertTrue(UryuFocusLook.bowGlow(2) > UryuFocusLook.bowGlow(1));
		assertTrue(UryuFocusLook.bowGlow(3) > UryuFocusLook.bowGlow(2));
		assertEquals(UryuFocusLook.bowGlow(3), UryuFocusLook.bowGlow(9), EPS);
	}

	@Test
	void aNewLayerFadesInOverTwoTenths() {
		assertEquals(0.0, UryuFocusLook.layerFade(0.0), EPS);
		assertEquals(0.5, UryuFocusLook.layerFade(0.1), EPS);
		assertEquals(1.0, UryuFocusLook.layerFade(0.2), EPS);
		assertEquals(1.0, UryuFocusLook.layerFade(5.0), EPS);
		assertEquals(0.0, UryuFocusLook.layerFade(-1.0), EPS);
	}

	@Test
	void theRingBreathesAtOneHertzAndHoldsStillWithReduceMotion() {
		double lo = 2;
		double hi = 0;
		for (double t = 0; t < 2; t += 0.001) {
			double b = UryuFocusLook.ringBreath(t, false);
			lo = Math.min(lo, b);
			hi = Math.max(hi, b);
		}
		assertEquals(0.8, lo, 0.001);
		assertEquals(1.0, hi, 0.001);
		assertEquals(0.9, UryuFocusLook.ringBreath(0.37, true), EPS);
		assertEquals(0.9, UryuFocusLook.ringBreath(1.9, true), EPS);
		assertEquals(0.0, UryuFocusLook.rotation(0.2, 3.0, true), EPS);
		assertEquals(2 * Math.PI * 0.2 * 3.0, UryuFocusLook.rotation(0.2, 3.0, false), EPS);
		assertEquals(-0.15, UryuFocusLook.PENTAGON_REV_PER_S, EPS, "the pentagon turns the other way");
	}

	@Test
	void theFlakesOrbitAtNinetyCentimetresBetweenHalfAndOnePointSixMetres() {
		assertEquals(3, UryuFocusLook.ORBIT_FLAKES);
		for (int i = 0; i < UryuFocusLook.ORBIT_FLAKES; i++) {
			for (double t = 0; t < 12; t += 0.25) {
				double[] o = UryuFocusLook.orbitOffset(i, t);
				assertEquals(0.9, Math.hypot(o[0], o[2]), 1e-9);
				assertTrue(o[1] >= 0.5 - 1e-9 && o[1] <= 1.6 + 1e-9, "height " + o[1]);
			}
		}
		double[] a = UryuFocusLook.orbitOffset(0, 0);
		double[] b = UryuFocusLook.orbitOffset(1, 0);
		assertTrue(Math.hypot(a[0] - b[0], a[2] - b[2]) > 0.5, "the three flakes are spread around the body");
	}

	@Test
	void theGainChimeRisesWithTheStackAndTheLossIsAGlassTick() {
		assertEquals(1.2, UryuFocusLook.gainPitch(1), EPS);
		assertEquals(1.5, UryuFocusLook.gainPitch(2), EPS);
		assertEquals(1.9, UryuFocusLook.gainPitch(3), EPS);
		assertEquals(0.4, UryuFocusLook.GAIN_VOLUME, EPS);
		assertTrue(UryuFocusLook.isGain(0, 1));
		assertTrue(UryuFocusLook.isGain(1, 3));
		assertFalse(UryuFocusLook.isGain(2, 2));
		assertFalse(UryuFocusLook.isGain(3, 0));
		assertTrue(UryuFocusLook.isLoss(3, 0));
		assertTrue(UryuFocusLook.isLoss(2, 1));
		assertFalse(UryuFocusLook.isLoss(1, 1));
		assertEquals(8, UryuFocusLook.POP_DASHES);
		assertEquals(12, UryuFocusLook.RESET_DASHES);
		assertEquals(1.6, UryuFocusLook.RESET_RADIUS, EPS);
	}

	@Test
	void theHumOnlyAtTwoAndThreeStacks() {
		assertEquals(0.0, UryuFocusLook.humInterval(0), EPS);
		assertEquals(0.0, UryuFocusLook.humInterval(1), EPS);
		assertEquals(2.0, UryuFocusLook.humInterval(2), EPS);
		assertEquals(1.4, UryuFocusLook.humInterval(3), EPS);
		assertEquals(1.8, UryuFocusLook.humPitch(2), EPS);
		assertEquals(2.0, UryuFocusLook.humPitch(3), EPS);
		assertEquals(0.08, UryuFocusLook.humVolume(2), EPS);
		assertEquals(0.12, UryuFocusLook.humVolume(3), EPS);
	}

	@Test
	void theCrossAndTheGlyphSizesOfTheTable() {
		assertEquals(1.8, UryuFocusLook.RING_SIZE, EPS);
		assertEquals(0.35, UryuFocusLook.RING_ALPHA, EPS);
		assertEquals(0.9, UryuFocusLook.CROSS_SIZE, EPS);
		assertEquals(0.5, UryuFocusLook.CROSS_ALPHA, EPS);
		assertEquals(0.2, UryuFocusLook.CROSS_REV_PER_S, EPS);
		assertEquals(2.8, UryuFocusLook.PENTAGON_SIZE, EPS, "pent_line GROUND r 1.4");
		assertEquals(0.45, UryuFocusLook.PENTAGON_ALPHA, EPS);
		assertEquals(2.4, UryuFocusLook.BODY_GLOW_SIZE, EPS);
		assertEquals(0.18, UryuFocusLook.BODY_GLOW_ALPHA, EPS);
		assertTrue(UryuFocusLook.CROSS_SIZE_FIRST < UryuFocusLook.CROSS_SIZE);
		assertTrue(UryuFocusLook.CROSS_ALPHA_FIRST < UryuFocusLook.CROSS_ALPHA);
	}
}
