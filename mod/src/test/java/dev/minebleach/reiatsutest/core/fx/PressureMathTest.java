package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PressureMathTest {
	@Test
	void fullNearTheCasterAndNothingFarAway() {
		assertEquals(1.0, PressureMath.intensity(0.0), 1e-9);
		assertEquals(1.0, PressureMath.intensity(PressureMath.NEAR), 1e-9);
		assertEquals(0.0, PressureMath.intensity(PressureMath.FAR), 1e-9);
		assertEquals(0.0, PressureMath.intensity(500.0), 1e-9);
		assertEquals(1.0, PressureMath.strength(0.0), 1e-9);
		assertEquals(1.0, PressureMath.strength(PressureMath.NEAR), 1e-9);
		assertEquals(0.0, PressureMath.strength(PressureMath.FAR), 1e-9);
		assertEquals(0.0, PressureMath.strength(500.0), 1e-9);
	}

	@Test
	void fallsOffSmoothlyAndNeverRises() {
		double prev = 1.0;
		double prevStrength = 1.0;
		for (double d = 0; d <= 60; d += 0.5) {
			double k = PressureMath.intensity(d);
			assertTrue(k >= 0.0 && k <= 1.0, "range at " + d);
			assertTrue(k <= prev + 1e-12, "monotone at " + d);
			prev = k;
			double s = PressureMath.strength(d);
			assertTrue(s >= 0.0 && s <= 1.0, "strength range at " + d);
			assertTrue(s <= prevStrength + 1e-12, "strength monotone at " + d);
			assertTrue(s >= k - 1e-12, "the floor only raises the strength at " + d);
			prevStrength = s;
		}
		double mid = PressureMath.intensity((PressureMath.NEAR + PressureMath.FAR) / 2);
		assertEquals(0.5, mid, 1e-9, "the smoothstep is symmetric around the middle");
	}

	@Test
	void mediumRangeKeepsAVisibleFloor() {
		// R11 T7: at 20..30 blocks the old strength fell to 0.43 and the effect was lost; now it stays at 0.60 or more
		for (double d = 20; d <= 30; d += 0.25) {
			assertTrue(PressureMath.strength(d) >= PressureMath.MID_FLOOR - 1e-12, "floor at " + d);
		}
		assertTrue(PressureMath.intensity(30) < 0.5, "the old curve was below half there");
		assertTrue(PressureMath.strength(40) > 0.0 && PressureMath.strength(40) < PressureMath.MID_FLOOR, "falls off to nothing at 48");
	}

	@Test
	void theHeartbeatPulsesAreInsideTheHoldAndOrdered() {
		double prev = 0;
		for (double t : PressureMath.PULSES) {
			assertTrue(t > prev, "increasing");
			assertTrue(t >= PressureMath.RAMP && t <= PressureMath.RAMP + PressureMath.HOLD, "inside ramp..hold: " + t);
			prev = t;
		}
		assertEquals(6.0, PressureMath.totalTime(), 1e-9);
	}

	@Test
	void newShakeValues() {
		assertEquals(0.9, PressureMath.PULSE_SHAKE_DEG, 1e-9);
		assertEquals(0.15, PressureMath.TREMOR_DEG, 1e-9);
		assertEquals(2.0, PressureMath.BLUR_MAX_PX, 1e-9);
		assertTrue(PressureMath.PULSE_SHAKE_DEG > 0.35, "stronger than the first version");
	}

	@Test
	void envelopeRampsHoldsAndFades() {
		assertEquals(0.0, PressureMath.envelope(0.0), 1e-12);
		assertEquals(0.0, PressureMath.envelope(-1.0), 1e-12);
		assertEquals(0.5, PressureMath.envelope(PressureMath.RAMP / 2), 1e-9);
		assertEquals(1.0, PressureMath.envelope(PressureMath.RAMP + 1.0), 1e-12);
		assertEquals(0.5, PressureMath.envelope(PressureMath.RAMP + PressureMath.HOLD + PressureMath.FADE / 2), 1e-9);
		assertEquals(0.0, PressureMath.envelope(PressureMath.totalTime()), 1e-12);
		assertEquals(0.0, PressureMath.envelope(50), 1e-12);
	}

	@Test
	void pulseBeatsAtTheSetTimes() {
		assertEquals(0.0, PressureMath.pulse(0.5), 1e-12);
		for (double p : PressureMath.PULSES) {
			assertEquals(1.0, PressureMath.pulse(p + PressureMath.PULSE_RISE), 1e-9);
			assertTrue(PressureMath.pulse(p - 0.05) < 0.5, "quiet right before " + p);
			assertTrue(PressureMath.pulse(p + 0.7) < 0.1, "decayed " + p);
		}
	}

	@Test
	void blurIsAtMostTwoPixelsAndGrowsWithStrengthAndPulse() {
		double max = 0;
		for (double t = 0; t < 6.5; t += 0.01) {
			double b = PressureMath.blurPx(t, 1.0);
			assertTrue(b >= 0 && b <= PressureMath.BLUR_MAX_PX + 1e-9, "range at " + t);
			max = Math.max(max, b);
			assertTrue(PressureMath.blurPx(t, 0.4) <= b + 1e-12, "k monotone at " + t);
		}
		assertEquals(2.0, max, 0.02);
		double quiet = PressureMath.blurPx(1.3, 1.0);
		double onPulse = PressureMath.blurPx(0.8 + PressureMath.PULSE_RISE, 1.0);
		assertTrue(onPulse > quiet * 1.4, "pulse " + onPulse + " quiet " + quiet);
		assertTrue(quiet >= 0.9, "the quiet hold still blurs about a pixel: " + quiet);
		assertEquals(0.0, PressureMath.blurPx(0.0, 1.0), 1e-12);
		assertEquals(0.0, PressureMath.blurPx(7.0, 1.0), 1e-12);
		assertEquals(0.0, PressureMath.blurPx(1.0, 0.0), 1e-12);
	}

	@Test
	void tearIsTwoToFourPixelsOnlyOnAPulse() {
		for (double p : PressureMath.PULSES) {
			double full = PressureMath.tearPx(p + PressureMath.PULSE_RISE, 1.0);
			double weak = PressureMath.tearPx(p + PressureMath.PULSE_RISE, 0.1);
			assertEquals(4.0, full, 1e-9);
			assertTrue(weak >= 2.0 && weak < 2.5, "weak " + weak);
		}
		assertEquals(0.0, PressureMath.tearPx(1.3, 1.0), 1e-12, "no tear between pulses");
		assertEquals(0.0, PressureMath.tearPx(1.0, 0.0), 1e-12);
	}

	@Test
	void sharpBrightnessEventsStayUnderThreePerSecond() {
		assertTrue(PressureMath.maxPulsesPerSecond() <= 3, "pulses in one second: " + PressureMath.maxPulsesPerSecond());
		assertEquals(2, PressureMath.maxPulsesPerSecond(), "the pulses are 0.9 s apart");
	}
}
