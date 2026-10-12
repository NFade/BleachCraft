package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DissolveSwirlTest {
	@Test
	void detachProgressIsTheInverseOfTheFront() {
		for (boolean tipFirst : new boolean[] {true, false}) {
			for (double s = 0.0; s <= 1.0; s += 0.1) {
				double p = DissolveMath.detachProgress(s, tipFirst);
				assertTrue(p >= 0 && p <= 1);
				assertEquals(s, DissolveMath.boundary(p, tipFirst), 1e-6);
			}
		}
	}

	@Test
	void tipFragmentsComeLooseFirstWhenTipFirst() {
		assertTrue(DissolveMath.detachProgress(0.95, true) < DissolveMath.detachProgress(0.5, true));
		assertTrue(DissolveMath.detachProgress(0.5, true) < DissolveMath.detachProgress(0.05, true));
		assertTrue(DissolveMath.detachProgress(0.05, false) < DissolveMath.detachProgress(0.95, false));
	}

	@Test
	void glowRisesThroughTheHoldAndFallsAtTheEnd() {
		assertTrue(DissolveMath.glowLevel(0, -1) < DissolveMath.glowLevel(0.5, -1));
		assertTrue(DissolveMath.glowLevel(0.5, -1) < DissolveMath.glowLevel(1, -1));
		assertEquals(1.0, DissolveMath.glowLevel(1, -1), 1e-9);
		assertEquals(1.0, DissolveMath.glowLevel(1, 0), 1e-9); // continuous into the dissolve
		assertTrue(DissolveMath.glowLevel(1, 0.28) > 1.3); // the swell before the break
		assertEquals(0.0, DissolveMath.glowLevel(1, 1), 1e-9);
		for (double p = 0; p <= 1; p += 0.01) {
			assertTrue(DissolveMath.glowLevel(1, p) <= 1.6);
		}
	}

	@Test
	void breakFlashIsASingleShortPulse() {
		assertEquals(0.0, DissolveMath.breakFlash(-1), 1e-9);
		assertEquals(0.0, DissolveMath.breakFlash(0), 1e-9);
		assertTrue(DissolveMath.breakFlash(0.06) > 0.99);
		assertEquals(0.0, DissolveMath.breakFlash(0.3), 1e-9);
		assertEquals(0.0, DissolveMath.breakFlash(0.9), 1e-9);
		// no strobing: the flash never goes up twice
		boolean falling = false;
		double last = 0;
		for (double p = 0; p <= 1; p += 0.005) {
			double f = DissolveMath.breakFlash(p);
			if (f < last - 1e-12) {
				falling = true;
			}
			assertTrue(!falling || f <= last + 1e-12);
			last = f;
		}
	}

	@Test
	void hiltAfterglowFades() {
		assertEquals(1.0, DissolveMath.hiltAfterglow(-0.1, 0.5), 1e-9);
		assertEquals(1.0, DissolveMath.hiltAfterglow(0, 0.5), 1e-9);
		assertEquals(0.0, DissolveMath.hiltAfterglow(0.5, 0.5), 1e-9);
	}

	@Test
	void parametersAreDeterministicAndOrderIndependent() {
		double[] a = new double[DissolveSwirl.COUNT];
		double[] b = new double[DissolveSwirl.COUNT];
		DissolveSwirl.params(42L, 17, 1.0, 0.9, 1.8, 0.9, a);
		DissolveSwirl.params(42L, 3, 1.0, 0.9, 1.8, 0.9, b);
		double[] c = new double[DissolveSwirl.COUNT];
		DissolveSwirl.params(42L, 17, 1.0, 0.9, 1.8, 0.9, c);
		assertArrayEquals(a, c, 0.0);
		assertTrue(a[DissolveSwirl.PHI0] != b[DissolveSwirl.PHI0]);
		double len = Math.sqrt(a[DissolveSwirl.AX] * a[DissolveSwirl.AX] + a[DissolveSwirl.AY] * a[DissolveSwirl.AY] + a[DissolveSwirl.AZ] * a[DissolveSwirl.AZ]);
		assertEquals(1.0, len, 1e-9);
		assertTrue(a[DissolveSwirl.LIFE] >= 0.9 * 0.75 && a[DissolveSwirl.LIFE] <= 0.9 * 1.25);
	}

	@Test
	void fragmentsRiseAndOpenUpWithoutJumping() {
		double[] prm = new double[DissolveSwirl.COUNT];
		DissolveSwirl.params(7L, 5, 1.0, 0.9, 1.8, 0.9, prm);
		double[] o = new double[3];
		DissolveSwirl.offset(prm, 0, o);
		assertArrayEquals(new double[] {0, 0, 0}, o, 1e-12);
		double lastH = -1;
		double lastR = -1;
		double[] prev = new double[3];
		DissolveSwirl.offset(prm, 0, prev);
		for (double t = 0.01; t <= prm[DissolveSwirl.LIFE]; t += 0.01) {
			DissolveSwirl.offset(prm, t, o);
			assertTrue(o[1] > lastH, "rises");
			double r = Math.hypot(o[0], o[2]);
			assertTrue(r >= lastR - 1e-12, "opens");
			double step = Math.sqrt(Math.pow(o[0] - prev[0], 2) + Math.pow(o[1] - prev[1], 2) + Math.pow(o[2] - prev[2], 2));
			assertTrue(step < 0.2, "no jump " + step);
			lastH = o[1];
			lastR = r;
			prev = o.clone();
		}
	}

	@Test
	void envelopesAreBoundedAndEnd() {
		double[] prm = new double[DissolveSwirl.COUNT];
		DissolveSwirl.params(9L, 1, 1.0, 0.9, 1.8, 0.9, prm);
		assertEquals(0.0, DissolveSwirl.sizeEnvelope(prm, -0.1, 0.1), 0.0);
		assertEquals(0.0, DissolveSwirl.sizeEnvelope(prm, 0, 0.1), 1e-9);
		assertEquals(1.0, DissolveSwirl.sizeEnvelope(prm, 0.3 * prm[DissolveSwirl.LIFE], 0.1), 1e-9);
		assertTrue(DissolveSwirl.sizeEnvelope(prm, prm[DissolveSwirl.LIFE], 0.1) > 0.3);
		assertEquals(1.0, DissolveSwirl.alphaEnvelope(prm, 0.2 * prm[DissolveSwirl.LIFE], 0.7), 1e-9);
		assertEquals(0.0, DissolveSwirl.alphaEnvelope(prm, prm[DissolveSwirl.LIFE], 0.7), 1e-9);
		assertEquals(1.0, DissolveSwirl.heat(0, 0.2), 1e-9);
		assertEquals(0.0, DissolveSwirl.heat(0.2, 0.2), 1e-9);
	}

	@Test
	void petalCountFollowsTheTierAndStaysInRange() {
		assertEquals(300, DissolveSwirl.petalCount(1.0, 1.0, 1000));
		assertEquals(200, DissolveSwirl.petalCount(0.5, 1.0, 1000));
		assertEquals(110, DissolveSwirl.petalCount(0.25, 1.0, 1000));
		assertEquals(180, DissolveSwirl.petalCount(1.0, 0.6, 1000));
		assertEquals(50, DissolveSwirl.petalCount(1.0, 1.0, 50)); // the particle cap wins
		for (double q = 0; q <= 1.0; q += 0.05) {
			int n = DissolveSwirl.petalCount(q, 1.0, 1500);
			assertTrue(n >= 20 && n <= 400);
		}
	}
}
