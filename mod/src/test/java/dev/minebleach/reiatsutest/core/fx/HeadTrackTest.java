package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.fx.SwarmMath.Kind;
import dev.minebleach.reiatsutest.core.fx.SwarmMath.Mode;
import dev.minebleach.reiatsutest.core.fx.SwarmMath.Slots;
import org.junit.jupiter.api.Test;

/** Head path recording and the steerable (tracked) attack ribbon of SwarmMath. */
class HeadTrackTest {
	private static HeadTrack line() {
		HeadTrack t = new HeadTrack();
		for (int i = 0; i <= 10; i++) {
			t.add(1.0 + 0.1 * i, 0, 1.5, 3.0 * i); // 30 blocks per second along +z
		}
		return t;
	}

	@Test
	void interpolatesClampsAndMeasuresSpeed() {
		HeadTrack t = line();
		double[] o = new double[3];
		assertTrue(t.at(1.25, o));
		assertEquals(0.0, o[0], 1e-9);
		assertEquals(7.5, o[2], 1e-9);
		t.at(0.0, o);
		assertEquals(0.0, o[2], 1e-9);
		t.at(99.0, o);
		assertEquals(30.0, o[2], 1e-9);
		assertEquals(30.0, t.speedAt(1.55), 1e-6);
		double[] d = new double[3];
		assertTrue(t.dirAt(1.55, d));
		assertEquals(1.0, d[2], 1e-9);
	}

	@Test
	void equalTimesAreKeptMonotoneAndAClosedTrackIgnoresMoreSamples() {
		HeadTrack t = new HeadTrack();
		t.add(1.0, 0, 0, 0);
		t.add(1.0, 1, 0, 0);
		assertEquals(2, t.size());
		assertTrue(t.lastTime() > 1.0);
		t.close(1.5);
		t.add(2.0, 5, 0, 0);
		assertEquals(2, t.size());
		assertTrue(t.closed());
		assertFalse(t.speedAt(1.0) < 0);
	}

	@Test
	void ringBufferKeepsTheNewestSamples() {
		HeadTrack t = new HeadTrack();
		for (int i = 0; i < 1000; i++) {
			t.add(i * 0.05, i, 0, 0);
		}
		assertEquals(256, t.size());
		double[] o = new double[3];
		t.at(999 * 0.05, o);
		assertEquals(999.0, o[0], 1e-9);
	}

	private static double[] pos(Slots s, int i, double t, Mode m) {
		double[] o = new double[3];
		SwarmMath.position(s, i, t, m, 0.0, o);
		return o;
	}

	private static Mode tracked() {
		Mode m = new Mode(Kind.ATTACK, 1.0);
		m.track = line();
		m.feet(0, 0, 0);
		return m;
	}

	@Test
	void trackedRibbonFollowsTheHeadAndStaysBehindIt() {
		Slots s = new Slots(99, 1000);
		Mode m = tracked();
		double t = 1.55 + m.trackDelay; // the head is at z = 16.5 at the drawn time
		int ribbon = 0;
		for (int i = 0; i < 1000; i++) {
			if (!s.ribbon(i)) {
				continue;
			}
			ribbon++;
			double[] p = pos(s, i, t, m);
			assertTrue(p[2] <= 16.5 + 0.5, "petal " + i + " ahead of the head: z=" + p[2]);
			assertTrue(p[2] >= 16.5 - m.trackLag * 30.0 - 1.0, "petal " + i + " too far behind: z=" + p[2]);
		}
		assertTrue(ribbon > 500);
	}

	@Test
	void petalsStayInOrbitUntilTheHeadHasDeparted() {
		Slots s = new Slots(99, 1000);
		Mode m = tracked();
		for (int i = 0; i < 1000; i += 13) {
			double[] a = pos(s, i, 0.5, m);
			double[] orbit = new double[3];
			SwarmMath.orbit(s, i, 0.5, orbit);
			assertEquals(orbit[0], a[0], 1e-9);
			assertEquals(orbit[2], a[2], 1e-9);
		}
	}

	@Test
	void trackedPetalsNeverTeleportBetweenFrames() {
		Slots s = new Slots(5, 1000);
		Mode m = tracked();
		m.track.close(2.0);
		double dt = 1.0 / 60.0;
		for (int i = 0; i < 1000; i += 7) {
			double[] prev = pos(s, i, 0.9, m);
			for (double t = 0.9 + dt; t < 4.0; t += dt) {
				double[] p = pos(s, i, t, m);
				double d = Math.sqrt((p[0] - prev[0]) * (p[0] - prev[0]) + (p[1] - prev[1]) * (p[1] - prev[1]) + (p[2] - prev[2]) * (p[2] - prev[2]));
				assertTrue(d < 2.0, "petal " + i + " jumped " + d + " at t=" + t); // 2 blocks per frame = 120 blocks per second, far above any real motion
				prev = p;
			}
		}
	}

	@Test
	void afterTheHeadIsBackThePetalsSettleInTheOrbit() {
		Slots s = new Slots(7, 1000);
		Mode m = tracked();
		m.track.close(2.0);
		double t = 2.0 + m.trackDelay + m.trackLag + 0.6;
		for (int i = 0; i < 1000; i += 11) {
			double[] p = pos(s, i, t, m);
			double[] orbit = new double[3];
			SwarmMath.orbit(s, i, t, orbit);
			assertEquals(orbit[0], p[0], 1e-6);
			assertEquals(orbit[1], p[1], 1e-6);
			assertEquals(orbit[2], p[2], 1e-6);
		}
	}

	@Test
	void withoutATrackTheOldClosedFormAttackIsUsed() {
		Slots s = new Slots(3, 1000);
		Mode m = new Mode(Kind.ATTACK, 1.0).aim(0, 1.5, 10);
		assertFalse(m.tracked());
		Mode withEmpty = new Mode(Kind.ATTACK, 1.0).aim(0, 1.5, 10);
		withEmpty.track = new HeadTrack();
		withEmpty.track.add(1.0, 0, 0, 0);
		assertFalse(withEmpty.tracked(), "one sample is not a path");
		for (int i = 0; i < 1000; i += 17) {
			double[] a = pos(s, i, 1.4, m);
			double[] b = pos(s, i, 1.4, withEmpty);
			assertEquals(a[0], b[0], 0.0);
			assertEquals(a[2], b[2], 0.0);
		}
	}
}
