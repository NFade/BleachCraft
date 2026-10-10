package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.fx.SwarmMath.Kind;
import dev.minebleach.reiatsutest.core.fx.SwarmMath.Mode;
import dev.minebleach.reiatsutest.core.fx.SwarmMath.Slots;
import org.junit.jupiter.api.Test;

/** Closed-form swarm: determinism, shapes of 4.1 to 4.3, continuity (no teleporting petals). */
class SwarmMathTest {
	private static final int N = 1000;

	private static double[] at(Slots s, int i, double t, Mode m) {
		double[] o = new double[3];
		SwarmMath.position(s, i, t, m, 0.7, o);
		return o;
	}

	private static double dist(double[] a, double[] b) {
		return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
	}

	@Test
	void sameSeedGivesSamePetalsOnEveryClient() {
		Slots a = new Slots(1234, N);
		Slots b = new Slots(1234, N);
		Mode m = new Mode(Kind.IDLE, 0);
		for (int i = 0; i < N; i += 37) {
			assertArrayEquals(at(a, i, 3.3, m), at(b, i, 3.3, m), 0.0);
		}
		Slots c = new Slots(1235, N);
		assertTrue(dist(at(a, 5, 3.3, m), at(c, 5, 3.3, m)) > 1e-6);
	}

	@Test
	void idleOrbitStaysInTheBodyVolume() {
		Slots s = new Slots(7, N);
		Mode m = new Mode(Kind.IDLE, 0);
		for (int i = 0; i < N; i++) {
			for (double t : new double[] {0.5, 1.7, 9.2}) {
				double[] p = at(s, i, t, m);
				double r = Math.hypot(p[0], p[2]);
				assertTrue(r > 1.2 && r < 2.6, "radius " + r);
				assertTrue(p[1] > 0.0 && p[1] < 1.9, "height " + p[1]);
			}
		}
	}

	@Test
	void releaseStreamStartsAtTheHandAndEndsInTheOrbit() {
		Slots s = new Slots(9, N);
		Mode m = new Mode(Kind.RELEASE, 0);
		double[] hand = new double[3];
		SwarmMath.hand(0.7, hand);
		double[] p0 = at(s, 0, 0.0, m);
		assertTrue(dist(p0, hand) < 0.4, "first petal leaves the tang tip");
		Mode idle = new Mode(Kind.IDLE, 0);
		for (int i = 0; i < N; i += 11) {
			assertArrayEquals(at(s, i, 1.2, idle), at(s, i, 1.2, m), 1e-9);
		}
		assertEquals(0.0, SwarmMath.scale(s, N - 1, 0.1, m), 1e-9, "the last petal has not launched at 0.1 s");
		assertEquals(1.0, SwarmMath.scale(s, 0, 0.5, m), 1e-9);
	}

	@Test
	void attackRibbonReachesTheAimEnvelopsAndComesHome() {
		Slots s = new Slots(11, N);
		Mode m = new Mode(Kind.ATTACK, 4.0).aim(10, 1.0, 5.0);
		Mode idle = new Mode(Kind.IDLE, 0);
		int ribbon = 0;
		for (int i = 0; i < N; i++) {
			if (!s.ribbon(i)) {
				assertArrayEquals(at(s, i, 4.8, idle), at(s, i, 4.8, m), 1e-9, "non-ribbon petals keep orbiting");
				continue;
			}
			ribbon++;
			double[] p = at(s, i, 4.0 + 1.0, m);
			assertTrue(dist(p, new double[] {10, 1, 5}) < 1.9, "enveloping the aim at 1.0 s: " + dist(p, new double[] {10, 1, 5}));
			assertArrayEquals(at(s, i, 4.0 + 2.3, idle), at(s, i, 4.0 + 2.3, m), 1e-9, "home by 2.2 s");
		}
		assertTrue(ribbon > 500 && ribbon < 700, "about 60 percent: " + ribbon);
	}

	@Test
	void domeIsAHemisphereOfFourLayers() {
		Slots s = new Slots(13, N);
		double[] o = new double[3];
		for (int i = 0; i < N; i++) {
			SwarmMath.dome(s, i, 2.0, o);
			double r = Math.sqrt(o[0] * o[0] + (o[1] - 0.05) * (o[1] - 0.05) + o[2] * o[2]);
			assertTrue(r > 2.55 && r < 3.15, "layer radius " + r);
			assertTrue(o[1] >= 0.04, "above the ground");
		}
	}

	@Test
	void noPetalTeleportsInAnyMode() {
		Slots s = new Slots(21, 300);
		Mode[] modes = {new Mode(Kind.RELEASE, 0), new Mode(Kind.ATTACK, 2.0).aim(-12, 0.5, 14), new Mode(Kind.BARRIER, 2.0)};
		modes[2].collapseAt = 7.0;
		double dt = 1.0 / 240;
		for (Mode m : modes) {
			for (int i = 0; i < 300; i += 7) {
				double[] prev = at(s, i, 0.0, m);
				for (double t = dt; t < 10.0; t += dt) {
					double[] p = at(s, i, t, m);
					assertTrue(dist(prev, p) / dt < 140.0, m.kind + " petal " + i + " at t=" + t + " jumps " + dist(prev, p) / dt + " b/s");
					prev = p;
				}
			}
		}
	}

	@Test
	void barrierCollapseReturnsToTheOrbit() {
		Slots s = new Slots(5, 200);
		Mode m = new Mode(Kind.BARRIER, 1.0);
		m.collapseAt = 6.0;
		Mode idle = new Mode(Kind.IDLE, 0);
		for (int i = 0; i < 200; i++) {
			assertArrayEquals(at(s, i, 7.0, idle), at(s, i, 7.0, m), 1e-9);
		}
	}
}
