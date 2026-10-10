package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Hakuteiken condensation of the rows into the hand (6.3). */
class CondenseMathTest {
	private static CondenseMath.Cloud cloud() {
		float[] dist = new float[200];
		for (int i = 0; i < dist.length; i++) {
			dist[i] = 1.1f * (i % 50);
		}
		return new CondenseMath.Cloud(new CondenseMath.Params(), 4, 200, 10, dist);
	}

	@Test
	void petalsAreBornAtTheCutAndAbsorbedAtTheHandWithinAboutOnePointFiveSeconds() {
		CondenseMath.Cloud c = cloud();
		assertEquals(2000, c.m);
		assertTrue(c.endTime() < 1.6, "everything is in the hand by " + c.endTime());
		double[] hand = {0, 65, 0};
		double[] out = new double[3];
		for (int j : new int[] {0, 5, 999, 1999}) {
			double ts = c.birthTime(j);
			assertEquals(0.0, c.position(j, ts - 0.01, 8, 63.5, 5, hand, out), 1e-9);
			assertTrue(c.position(j, ts + 0.1, 8, 63.5, 5, hand, out) > 0.5);
			assertEquals(0.0, c.position(j, c.arriveTime(j) + 0.01, 8, 63.5, 5, hand, out), 1e-9);
		}
	}

	@Test
	void thePetalNearTheEndOfItsFlightIsCloseToTheHandAndWhite() {
		CondenseMath.Cloud c = cloud();
		double[] hand = {0, 65, 0};
		double[] out = new double[3];
		int j = 77;
		double t = c.arriveTime(j) - 0.03;
		c.position(j, t, 8, 63.5, 5, hand, out);
		double d = Math.sqrt(out[0] * out[0] + (out[1] - 65) * (out[1] - 65) + out[2] * out[2]);
		assertTrue(d < 1.5, "distance to the hand " + d);
		assertTrue(c.whiteness(d) > 0.0);
		assertEquals(0.0, c.whiteness(3.0), 1e-9);
		assertEquals(1.0, c.whiteness(0.0), 1e-9);
	}

	@Test
	void spiralStaysFinite() {
		CondenseMath.Cloud c = cloud();
		double[] hand = {0, 65, 0};
		double[] out = new double[3];
		for (int j = 0; j < c.m; j += 13) {
			for (double t = 0; t < 2.0; t += 0.04) {
				double sc = c.position(j, t, 8, 63.5, 5, hand, out);
				if (sc > 0) {
					assertTrue(Double.isFinite(out[0]) && Double.isFinite(out[1]) && Double.isFinite(out[2]));
				}
			}
		}
	}
}
