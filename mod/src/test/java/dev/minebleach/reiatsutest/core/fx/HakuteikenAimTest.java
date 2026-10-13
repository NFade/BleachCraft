package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Beam direction of Hakuteiken against the crosshair (look) at several yaw, pitch and distances. */
class HakuteikenAimTest {
	private static double[] look(double yawDeg, double pitchDeg) {
		double y = Math.toRadians(yawDeg);
		double p = Math.toRadians(pitchDeg);
		return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
	}

	private static double[] origin(double[] eye, double[] l, double side) {
		double[] o = new double[3];
		HakuteikenAim.origin(eye[0], eye[1], eye[2], l[0], l[1], l[2], side, HakuteikenAim.HAND_DOWN, HakuteikenAim.HAND_FORWARD, o);
		return o;
	}

	private static double[] end(double[] eye, double[] l, double reach) {
		double[] e = new double[3];
		HakuteikenAim.target(eye[0], eye[1], eye[2], l[0], l[1], l[2], reach, e);
		return e;
	}

	@Test
	void rightIsMinecraftRight() {
		double[] r = new double[2];
		HakuteikenAim.right(0, 1, r); // yaw 0 looks +z, right is -x
		assertEquals(-1.0, r[0], 1e-12);
		assertEquals(0.0, r[1], 1e-12);
		HakuteikenAim.right(-1, 0, r); // yaw 90 looks -x, right is -z
		assertEquals(0.0, r[0], 1e-12);
		assertEquals(-1.0, r[1], 1e-12);
		HakuteikenAim.right(0, 0, r); // straight up: defined, unit
		assertEquals(1.0, Math.hypot(r[0], r[1]), 1e-12);
	}

	@Test
	void originStaysInTheLookFrameAtAnyYawAndPitch() {
		double[] eye = {10, 65.62, -3};
		for (double yaw = -180; yaw <= 180; yaw += 45) {
			for (double pitch = -80; pitch <= 80; pitch += 40) {
				double[] l = look(yaw, pitch);
				double[] o = origin(eye, l, HakuteikenAim.HAND_SIDE);
				double dx = o[0] - eye[0];
				double dy = o[1] - eye[1];
				double dz = o[2] - eye[2];
				double[] r = new double[2];
				HakuteikenAim.right(l[0], l[2], r);
				assertEquals(HakuteikenAim.HAND_SIDE, dx * r[0] + dz * r[1], 1e-9, "side at yaw " + yaw + " pitch " + pitch);
				// eye - up * down + look * forward: along the look that is forward - down * ly
				assertEquals(HakuteikenAim.HAND_FORWARD - HakuteikenAim.HAND_DOWN * l[1], dx * l[0] + dy * l[1] + dz * l[2], 1e-9, "forward at yaw " + yaw + " pitch " + pitch);
			}
		}
	}

	@Test
	void firstPersonBeamHasNoSidewaysBiasAtAnyDepth() {
		double[] eye = {0, 1.62, 0};
		for (double yaw : new double[] {0, 37, 90, -135, 200}) {
			for (double pitch : new double[] {-60, -10, 0, 25, 70}) {
				double[] l = look(yaw, pitch);
				for (double reach : new double[] {5, 15, 28}) {
					double[] from = origin(eye, l, 0.0);
					double[] to = end(eye, l, reach);
					for (double depth : new double[] {1.0, 3.0, reach * 0.5, reach}) {
						double[] o = HakuteikenAim.offsetAtDepth(eye, l, from, to, depth);
						assertEquals(0.0, o[0], 1e-9, "right bias yaw " + yaw + " pitch " + pitch + " reach " + reach + " depth " + depth);
					}
					// the beam ends exactly on the crosshair
					double[] o = HakuteikenAim.offsetAtDepth(eye, l, from, to, reach);
					assertEquals(0.0, o[2], 1e-9);
				}
			}
		}
	}

	@Test
	void thirdPersonBeamConvergesOnTheCrosshairTarget() {
		double[] eye = {0, 1.62, 0};
		double[] l = look(30, 0);
		double reach = 28;
		double[] from = origin(eye, l, HakuteikenAim.HAND_SIDE);
		double[] to = end(eye, l, reach);
		double[] near = HakuteikenAim.offsetAtDepth(eye, l, from, to, 5.0);
		double[] mid = HakuteikenAim.offsetAtDepth(eye, l, from, to, 15.0);
		double[] atEnd = HakuteikenAim.offsetAtDepth(eye, l, from, to, reach);
		// right bias shrinks linearly to the end point: 0.30 * (28 - d) / (28 - 0.6)
		assertEquals(0.30 * (28 - 5.0) / (28 - 0.6), near[0], 1e-9);
		assertEquals(0.30 * (28 - 15.0) / (28 - 0.6), mid[0], 1e-9);
		assertEquals(0.0, atEnd[0], 1e-9);
		assertTrue(near[0] > mid[0] && mid[0] > atEnd[0]);
		// a depth beyond the beam end is outside the segment
		assertTrue(Double.isNaN(HakuteikenAim.offsetAtDepth(eye, l, from, to, 30.0)[0]));
	}

	@Test
	void directionPointsAtTheCrosshairTargetNotParallelToTheLook() {
		double[] eye = {5, 70.62, 5};
		double[] l = look(-50, 20);
		double[] from = origin(eye, l, HakuteikenAim.HAND_SIDE);
		double[] to = end(eye, l, 15);
		double[] d = new double[3];
		HakuteikenAim.direction(from, to, l, d);
		assertEquals(1.0, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-12);
		// extending the beam direction from the hand passes through the target
		double len = Math.sqrt(Math.pow(to[0] - from[0], 2) + Math.pow(to[1] - from[1], 2) + Math.pow(to[2] - from[2], 2));
		assertEquals(to[0], from[0] + d[0] * len, 1e-9);
		assertEquals(to[1], from[1] + d[1] * len, 1e-9);
		assertEquals(to[2], from[2] + d[2] * len, 1e-9);
		// the beam leans toward the look axis: angle to the look is smaller than the parallax angle of the hand anchor
		double dot = d[0] * l[0] + d[1] * l[1] + d[2] * l[2];
		double angle = Math.toDegrees(Math.acos(Math.min(1.0, dot)));
		double naive = Math.toDegrees(Math.atan2(Math.hypot(HakuteikenAim.HAND_SIDE, HakuteikenAim.HAND_DOWN), 15 - HakuteikenAim.HAND_FORWARD));
		assertEquals(naive, angle, 0.3);
		double[] same = new double[3];
		HakuteikenAim.direction(from, from, l, same);
		assertEquals(l[0], same[0], 1e-12);
		assertEquals(l[2], same[2], 1e-12);
	}

	/** The numbers of the report: lateral right bias of the OLD shoulder anchor (0.30 right, convergent) at depth 5 and 15 for a target 28 m away. */
	@Test
	void oldAnchorBiasNumbers() {
		double[] eye = {0, 1.62, 0};
		double[] l = look(0, 0);
		double[] from = origin(eye, l, HakuteikenAim.HAND_SIDE);
		double[] to = end(eye, l, 28);
		double[] d5 = HakuteikenAim.offsetAtDepth(eye, l, from, to, 5);
		double[] d15 = HakuteikenAim.offsetAtDepth(eye, l, from, to, 15);
		assertEquals(0.252, d5[0], 0.001);
		assertEquals(0.185, -d5[1], 0.001); // and 0.185 below it
		assertEquals(3.57, d5[2], 0.02); // total angle to the crosshair at 5 m (right and down)
		assertEquals(0.142, d15[0], 0.001);
		assertEquals(0.67, d15[2], 0.02);
		// parallel beam (no convergence) would stay 0.30 right: 3.43 / 1.15 / 0.57 degrees at 5 / 15 / 30 m
		assertEquals(3.43, Math.toDegrees(Math.atan2(0.30, 5)), 0.01);
		assertEquals(1.15, Math.toDegrees(Math.atan2(0.30, 15)), 0.01);
		assertEquals(0.57, Math.toDegrees(Math.atan2(0.30, 30)), 0.01);
	}

	@Test
	void reachClipsToRangeAndNeverNegative() {
		assertEquals(28.0, HakuteikenAim.reach(28, 40), 1e-12);
		assertEquals(9.5, HakuteikenAim.reach(28, 9.5), 1e-12);
		assertEquals(0.0, HakuteikenAim.reach(28, -1), 1e-12);
	}
}
