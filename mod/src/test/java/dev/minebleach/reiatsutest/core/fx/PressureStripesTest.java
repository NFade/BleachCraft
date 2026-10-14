package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PressureStripesTest {
	private static final int W = 1920;

	@Test
	void sameInputGivesTheSameLayout() {
		List<PressureStripes.Stripe> a = PressureStripes.layout(42L, 2.04, 0.8, W);
		List<PressureStripes.Stripe> b = PressureStripes.layout(42L, 2.04, 0.8, W);
		assertFalse(a.isEmpty());
		assertEquals(a, b);
		assertNotEquals(a, PressureStripes.layout(43L, 2.04, 0.8, W), "another seed gives another layout");
	}

	@Test
	void countIsSixToFourteenAndGrowsWithStrength() {
		assertEquals(6, PressureStripes.count(0.0));
		assertEquals(14, PressureStripes.count(1.0));
		int prev = 0;
		for (double k = 0; k <= 1.0; k += 0.05) {
			int n = PressureStripes.count(k);
			assertTrue(n >= prev && n >= 6 && n <= 14);
			prev = n;
		}
		// at full strength, in the middle of a slot on the hold, all of them are there
		List<PressureStripes.Stripe> l = PressureStripes.layout(7L, 1.3 + 1.0 / 24, 1.0, W);
		assertEquals(14, l.size());
	}

	@Test
	void everyStripeIsInsideTheScreenWithAWidthOfTwoToSixPixels() {
		for (long seed = 0; seed < 40; seed++) {
			for (double t = 0.2; t < PressureMath.totalTime(); t += 0.037) {
				for (int w : new int[] {640, 1280, 1920, 3840}) {
					for (PressureStripes.Stripe s : PressureStripes.layout(seed, t, 0.9, w)) {
						assertTrue(s.x0() >= 0 && s.x1() <= 1.0 + 1e-12 && s.x1() > s.x0(), "x range");
						assertTrue(s.y0() >= 0 && s.y1() <= 1.0 + 1e-12 && s.y1() > s.y0(), "y range");
						double px = s.width(w);
						assertTrue(px > 1.999 && px < 6.001, "width px " + px);
						assertEquals(Math.rint(px), px, 1e-6, "whole pixels");
						assertTrue(s.alpha() > 0 && s.alpha() <= PressureStripes.ALPHA_MAX + 1e-9, "alpha " + s.alpha());
						assertTrue(s.tone() >= 0 && s.tone() <= 2);
					}
				}
			}
		}
	}

	@Test
	void aSizeableShareRunsTheWholeHeightAndTheRestAreShorter() {
		int full = 0;
		int part = 0;
		for (long seed = 0; seed < 30; seed++) {
			for (PressureStripes.Stripe s : PressureStripes.layout(seed, 1.3 + 1.0 / 24, 1.0, W)) {
				if (s.y0() == 0.0 && s.y1() == 1.0) {
					full++;
				} else {
					part++;
					assertTrue(s.y1() - s.y0() >= 0.15 - 1e-9 && s.y1() - s.y0() <= 0.60 + 1e-9);
				}
			}
		}
		assertTrue(full > 100 && part > 100, "full " + full + " part " + part);
	}

	@Test
	void allThreeTonesAppear() {
		boolean[] seen = new boolean[3];
		for (long seed = 0; seed < 10; seed++) {
			for (PressureStripes.Stripe s : PressureStripes.layout(seed, 1.3 + 1.0 / 24, 1.0, W)) {
				seen[s.tone()] = true;
			}
		}
		assertTrue(seen[PressureStripes.TONE_DARK] && seen[PressureStripes.TONE_LIGHT] && seen[PressureStripes.TONE_TINT]);
	}

	@Test
	void layoutIsRenewedTwelveTimesASecondAndNotInsideASlot() {
		assertEquals(12, PressureStripes.SLOTS_PER_SECOND);
		double t0 = 1.0;
		int changes = 0;
		List<PressureStripes.Stripe> prev = null;
		double dt = 1.0 / 240;
		for (int i = 0; i < 240; i++) {
			double t = t0 + i * dt + 1e-6;
			List<PressureStripes.Stripe> l = PressureStripes.layout(5L, t, 1.0, W);
			// compare the geometry only (alpha moves softly inside a slot)
			List<List<Double>> geo = geometry(l);
			if (prev != null && !geo.equals(geometry(prev))) {
				changes++;
			}
			prev = l;
		}
		assertTrue(changes >= 11 && changes <= 13, "changes in one second: " + changes);
	}

	private static List<List<Double>> geometry(List<PressureStripes.Stripe> l) {
		return l.stream().map(s -> List.of(s.x0(), s.x1(), s.y0(), s.y1(), (double) s.tone())).toList();
	}

	@Test
	void brightnessMovesSoftlyEachStripeIsInvisibleAtTheEdgesOfItsSlot() {
		for (int slot = 12; slot < 60; slot++) {
			assertEquals(0.0, PressureStripes.slotFade(slot / 12.0), 1e-9, "start of slot");
			assertEquals(1.0, PressureStripes.slotFade((slot + 0.5) / 12.0), 1e-9, "middle of slot");
			assertTrue(PressureStripes.slotFade((slot + 0.999999) / 12.0) < 1e-3, "end of slot");
		}
		// the alpha never steps by more than a small amount between two frames at 60 fps
		double prev = 0;
		double maxStep = 0;
		for (double t = 0.8; t < 3.6; t += 1.0 / 60) {
			double total = PressureStripes.coverage(PressureStripes.layout(9L, t, 1.0, W));
			maxStep = Math.max(maxStep, Math.abs(total - prev));
			prev = total;
		}
		assertTrue(maxStep < 0.02, "largest frame to frame change of the covered brightness " + maxStep);
	}

	@Test
	void coverageStaysTinyForAnyLayout() {
		double max = 0;
		for (long seed = 0; seed < 60; seed++) {
			for (double t = 0.1; t < PressureMath.totalTime(); t += 0.021) {
				for (int w : new int[] {640, 854, 1280, 1920, 2560}) {
					max = Math.max(max, PressureStripes.coverage(PressureStripes.layout(seed, t, 1.0, w)));
				}
			}
		}
		assertTrue(max <= PressureStripes.MAX_COVERAGE, "max coverage " + max);
	}

	@Test
	void emptyOutsideTheEffectAndForNoStrength() {
		assertTrue(PressureStripes.layout(1L, -0.1, 1.0, W).isEmpty());
		assertTrue(PressureStripes.layout(1L, 0.0, 1.0, W).isEmpty());
		assertTrue(PressureStripes.layout(1L, PressureMath.totalTime(), 1.0, W).isEmpty());
		assertTrue(PressureStripes.layout(1L, 99.0, 1.0, W).isEmpty());
		assertTrue(PressureStripes.layout(1L, 2.04, 0.0, W).isEmpty());
		assertTrue(PressureStripes.layout(1L, 2.04, 0.005, W).isEmpty());
		assertTrue(PressureStripes.layout(1L, 2.04, 1.0, 4).isEmpty());
	}


	private static double meanAlpha(double t) {
		double sum = 0;
		int n = 0;
		for (long seed = 0; seed < 20; seed++) {
			for (PressureStripes.Stripe st : PressureStripes.layout(seed, t, 1.0, W)) {
				sum += st.alpha();
				n++;
			}
		}
		return sum / n;
	}

	@Test
	void pulsesMakeThemBrighterThanTheQuietHold() {
		// middles of slots: 10.5 / 12 is 0.075 s after the first pulse, 15.5 / 12 is a quiet moment of the hold
		double onPulse = meanAlpha(10.5 / 12);
		double quiet = meanAlpha(15.5 / 12);
		assertTrue(onPulse > quiet * 1.5, "pulse " + onPulse + " quiet " + quiet);
		assertTrue(onPulse <= PressureStripes.ALPHA_MAX && quiet >= PressureStripes.ALPHA_MIN * 0.8 * 0.99);
	}
}
