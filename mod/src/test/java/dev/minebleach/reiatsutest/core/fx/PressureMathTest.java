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
	}

	@Test
	void fallsOffSmoothlyAndNeverRises() {
		double prev = 1.0;
		for (double d = 0; d <= 60; d += 0.5) {
			double k = PressureMath.intensity(d);
			assertTrue(k >= 0.0 && k <= 1.0, "range at " + d);
			assertTrue(k <= prev + 1e-12, "monotone at " + d);
			prev = k;
		}
		double mid = PressureMath.intensity((PressureMath.NEAR + PressureMath.FAR) / 2);
		assertEquals(0.5, mid, 1e-9, "the smoothstep is symmetric around the middle");
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
}
