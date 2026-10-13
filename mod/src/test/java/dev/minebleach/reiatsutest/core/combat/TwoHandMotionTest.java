package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure maths of the two handed hold, strikes and guard of the first person sword (BC5c). */
class TwoHandMotionTest {
	private static final float EPS = 1e-4f;
	/** Blade axis of the sword in the hand frame at rest (display transform of the zanpakuto items, scale 2.4). */
	private static final float[] BLADE = {0.118f, 0.967f, 0.225f};
	/** Grip of the sword in the hand frame (display translation plus the held lift). */
	private static final float[] GRIP = {0.134f, 0.042f, -0.3125f};
	private static final float SCALE = 2.4f;

	private static FpParams params(Map<String, float[]> m) {
		return new FpParams() {
			@Override
			public float num(String key, float def) {
				float[] v = m.get(key);
				return v == null ? def : v[0];
			}

			@Override
			public float[] vec(String key, float[] def) {
				float[] v = m.get(key);
				return v == null ? def : v;
			}
		};
	}

	private static float[] along(float s) {
		return new float[] {GRIP[0] + BLADE[0] * s * SCALE, GRIP[1] + BLADE[1] * s * SCALE, GRIP[2] + BLADE[2] * s * SCALE};
	}

	private static float dist(float[] a, float[] b) {
		float dx = a[0] - b[0];
		float dy = a[1] - b[1];
		float dz = a[2] - b[2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	@Test
	void stanceBlendsFromTheOneHandedHold() {
		FpParams d = FpParams.DEFAULTS;
		assertTrue(TwoHandMotion.stance(d, 0f).isNearZero());
		assertArrayEquals(TwoHandMotion.STANCE.pos(), TwoHandMotion.stance(d, 1f).pos(), EPS);
		assertArrayEquals(TwoHandMotion.STANCE.rot(), TwoHandMotion.stance(d, 1f).rot(), EPS);
		// halfway the smoothstep is exactly half
		float[] h = TwoHandMotion.stance(d, 0.5f).pos();
		assertEquals(TwoHandMotion.STANCE.pos()[0] * 0.5f, h[0], EPS);
		assertEquals(TwoHandMotion.STANCE.pos()[1] * 0.5f, h[1], EPS);
	}

	@Test
	void stanceLeansTheBladeForwardAndToTheRight() {
		float[] d = TwoHandMotion.stance(FpParams.DEFAULTS, 1f).applyDir(FpRig.DEFAULT.axis());
		float forward = (float) Math.toDegrees(Math.asin(-d[2]));
		float right = (float) Math.toDegrees(Math.asin(d[0]));
		assertTrue(forward > 20f && forward < 32f, "leans forward about 25 degrees: " + forward);
		assertTrue(right > 3f && right < 20f, "a little to the right: " + right);
		assertTrue(d[1] > 0.8f, "still upright: " + d[1]);
		// the rest of the one handed hold leans back towards the camera
		assertTrue(FpRig.DEFAULT.axis()[2] > 0f);
	}

	@Test
	void stanceKeepsTheSwordInTheFrameAndTheFistsLowAndNearTheMiddle() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, FpRig.DEFAULT);
		FpPose st = TwoHandMotion.stance(p, 1f);
		for (float aspect : new float[] {16f / 9f, 4f / 3f}) {
			float[] tip = FpGeometry.ndc(FpGeometry.cam(st.apply(h.pivot(), FpRig.DEFAULT.hand(0.5f, FpRig.TIP_Y, 0.5f))), aspect);
			assertTrue(tip != null && tip[1] > 0.4f && tip[1] < 1.0f, "the tip is in the upper third: " + (tip == null ? "none" : tip[1]));
			assertTrue(tip[0] > 0.05f && tip[0] < 0.6f, "right of the middle: " + tip[0]);
			float[] rf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.rightFist(h, st)), aspect);
			assertTrue(rf[1] < -0.5f && rf[1] > -0.8f, "the right fist in the lower quarter: " + rf[1]);
			assertTrue(rf[0] > 0f && rf[0] < 0.4f, "near the middle: " + rf[0]);
			float[] lf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.leftFist(h, st, st, 0f, null)), aspect);
			assertTrue(lf[1] < rf[1] - 0.2f && lf[1] > -1.15f, "the left fist below the right one, at the bottom edge: " + lf[1]);
		}
		// hands lower and nearer to the player than the old stance (0.27 up, level)
		assertTrue(TwoHandMotion.STANCE.pos()[1] < 0.15f && TwoHandMotion.STANCE.pos()[2] > 0f);
	}

	@Test
	void leftHandRidesOnTheHiltRigidly() {
		FpParams d = FpParams.DEFAULTS;
		float[] pivot = along(TwoHandMotion.PIVOT_S);
		float[] gripRest = along(0f);
		float[] leftRest = along(TwoHandMotion.LEFT_S);
		float gap = dist(gripRest, TwoHandMotion.leftFist(d, FpPose.ZERO, pivot, leftRest));
		assertTrue(gap > 0.25f && gap < 0.45f, "two fists on the hilt, a fist apart: " + gap);
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			SwingMotion.Keys k = TwoHandMotion.keys(d, p);
			for (FpPose pose : new FpPose[] {k.wind(), k.hit(), k.follow(), TwoHandMotion.guard(d)}) {
				float[] right = pose.apply(pivot, gripRest);
				float[] left = TwoHandMotion.leftFist(d, pose, pivot, leftRest);
				assertEquals(gap, dist(right, left), 1e-3f, "left hand keeps its place on the hilt in " + p);
			}
		}
	}

	@Test
	void leftHandSitsBelowTheRightHand() {
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		float[] pivot = along(TwoHandMotion.PIVOT_S);
		float[] right = st.apply(pivot, along(0f));
		float[] left = TwoHandMotion.leftFist(FpParams.DEFAULTS, st, pivot, along(TwoHandMotion.LEFT_S));
		assertTrue(left[1] < right[1] - 0.15f, "left lower than right");
	}

	@Test
	void leftFistGripsTheHiltAndTheArmComesFromTheLowerLeft() {
		FpParams d = FpParams.DEFAULTS;
		float[] pivot = along(TwoHandMotion.PIVOT_S);
		float[] on = along(TwoHandMotion.LEFT_S);
		// round 5: the hilt runs through the fist (no sideways offset); the arm is turned square to the hilt and comes in from the lower left
		assertArrayEquals(on, TwoHandMotion.leftFist(d, FpPose.ZERO, pivot, on), 1e-6f);
		float[] r = TwoHandMotion.leftArmRot(d, false);
		float[] m = TwoHandMotion.leftArmRot(d, true);
		assertTrue(r[1] < -15f && r[2] < -15f, "forearm swung to the left: " + r[1]);
		assertEquals(-r[1], m[1], EPS);
		assertEquals(-r[2], m[2], EPS);
		assertEquals(r[0], m[0], EPS);
		// the arm shift and the right forearm turn mirror the other way for a left hander
		float[] sh = TwoHandMotion.armShift(d, false);
		assertEquals(-sh[0], TwoHandMotion.armShift(d, true)[0], EPS);
		assertEquals(sh[1], TwoHandMotion.armShift(d, true)[1], EPS);
		float[] ar = TwoHandMotion.armRot(d, false);
		assertTrue(ar[1] > 15f && ar[2] > 15f, "right forearm from the lower right: " + ar[1]);
		assertEquals(-ar[1], TwoHandMotion.armRot(d, true)[1], EPS);
		// a sideways offset is carried by the rotation of the sword and mirrors for a left hander
		FpParams o = params(Map.of("two.left.arm.rot", new float[] {5f, -50f, 3f}, "two.left.offset", new float[] {-0.1f, 0f, 0f}));
		assertArrayEquals(new float[] {5f, -50f, 3f}, TwoHandMotion.leftArmRot(o, false), EPS);
		float[] side = TwoHandMotion.leftFist(o, FpPose.ZERO, pivot, on);
		assertEquals(on[0] - 0.1f, side[0], 1e-5f);
		assertEquals(on[0] + 0.1f, TwoHandMotion.leftFist(o, FpPose.ZERO, pivot, on, true)[0], 1e-5f);
		float[] g = TwoHandMotion.leftFist(o, TwoHandMotion.guard(d), pivot, on);
		float[] rg = TwoHandMotion.guard(d).apply(pivot, on);
		// the offset is carried by exactly the rotation of the pose
		float[] turned = TwoHandMotion.guard(d).applyDir(new float[] {-0.1f, 0f, 0f});
		assertArrayEquals(turned, new float[] {g[0] - rg[0], g[1] - rg[1], g[2] - rg[2]}, 1e-5f);
		assertTrue(Math.abs(turned[1]) + Math.abs(turned[2]) > 0.005f, "the offset turns with the blade");
	}

	@Test
	void pivotLiesBetweenTheFistsNearerTheLeftOne() {
		assertTrue(TwoHandMotion.PIVOT_S < 0f && TwoHandMotion.PIVOT_S > TwoHandMotion.LEFT_S);
		assertTrue(Math.abs(TwoHandMotion.PIVOT_S - TwoHandMotion.LEFT_S) < Math.abs(TwoHandMotion.PIVOT_S));
		assertEquals(0f, TwoHandMotion.pivotS(FpParams.DEFAULTS, 0f), EPS);
		assertEquals(TwoHandMotion.PIVOT_S, TwoHandMotion.pivotS(FpParams.DEFAULTS, 1f), EPS);
	}

	@Test
	void everyProfileReachesTheHitKeyAtTheHitAndReturnsToTheStance() {
		FpParams d = FpParams.DEFAULTS;
		FpPose rest = TwoHandMotion.stance(d, 1f);
		SwingMotion.Timing tm = SwingMotion.Timing.DEFAULT;
		float up = 0.25f;
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			SwingMotion.Keys k = TwoHandMotion.keys(d, p);
			assertArrayEquals(k.hit().pos(), SwingMotion.sample(k, rest, rest, up, up, tm).pos(), EPS, p + " hit pos");
			assertArrayEquals(k.hit().rot(), SwingMotion.sample(k, rest, rest, up, up, tm).rot(), EPS, p + " hit rot");
			assertArrayEquals(k.wind().rot(), SwingMotion.sample(k, rest, rest, up * tm.windFrac(), up, tm).rot(), EPS, p + " wind rot");
			assertArrayEquals(rest.pos(), SwingMotion.sample(k, rest, rest, SwingMotion.duration(up, tm) + 0.01f, up, tm).pos(), EPS);
			assertArrayEquals(rest.rot(), SwingMotion.sample(k, rest, rest, SwingMotion.duration(up, tm) + 0.01f, up, tm).rot(), EPS);
			assertArrayEquals(rest.rot(), SwingMotion.sample(k, rest, rest, 0f, up, tm).rot(), EPS, "starts from where it was");
		}
	}

	@Test
	void arcBendsThePathOnlyBetweenWindAndHit() {
		FpParams d = FpParams.DEFAULTS;
		FpPose rest = TwoHandMotion.stance(d, 1f);
		SwingMotion.Timing tm = SwingMotion.Timing.DEFAULT;
		float up = 0.30f;
		SwingMotion.Keys k = TwoHandMotion.keys(d, SwingMotion.Profile.H_R);
		SwingMotion.Keys flat = new SwingMotion.Keys(k.wind(), k.hit(), k.follow(), FpPose.ZERO);
		float tw = up * tm.windFrac();
		float mid = tw + (up - tw) * 0.7f; // eased fraction is not 0.5: just compare with the flat path at the same time
		float[] a = SwingMotion.sample(k, rest, rest, mid, up, tm).pos();
		float[] b = SwingMotion.sample(flat, rest, rest, mid, up, tm).pos();
		assertTrue(Math.abs(a[2] - b[2]) > 0.02f, "the arc pushes the hands forward in the middle of the cut");
		assertArrayEquals(SwingMotion.sample(k, rest, rest, tw * 0.5f, up, tm).pos(), SwingMotion.sample(flat, rest, rest, tw * 0.5f, up, tm).pos(), EPS);
	}

	@Test
	void theCutAcceleratesIntoTheHit() {
		FpParams d = FpParams.DEFAULTS;
		FpPose rest = TwoHandMotion.stance(d, 1f);
		SwingMotion.Timing tm = SwingMotion.Timing.DEFAULT;
		float up = 0.30f;
		SwingMotion.Keys k = TwoHandMotion.keys(d, SwingMotion.Profile.H_R);
		float dt = 0.01f;
		float tw = up * tm.windFrac();
		float early = angleStep(k, rest, tw + 0.02f, dt, up, tm);
		float late = angleStep(k, rest, up - 0.03f, dt, up, tm);
		assertTrue(late > early * 2f, "the blade turns faster just before the hit: early " + early + " late " + late);
	}

	private static float angleStep(SwingMotion.Keys k, FpPose rest, float t, float dt, float up, SwingMotion.Timing tm) {
		float[] d0 = SwingMotion.sample(k, rest, rest, t, up, tm).applyDir(BLADE);
		float[] d1 = SwingMotion.sample(k, rest, rest, t + dt, up, tm).applyDir(BLADE);
		float dot = d0[0] * d1[0] + d0[1] * d1[1] + d0[2] * d1[2];
		return (float) Math.acos(Math.max(-1f, Math.min(1f, dot)));
	}

	@Test
	void horizontalCutsSweepOppositeWaysAndTheBladeStartsOnTheirOwnSide() {
		FpParams d = FpParams.DEFAULTS;
		float[] hr0 = TwoHandMotion.keys(d, SwingMotion.Profile.H_R).wind().applyDir(BLADE);
		float[] hr1 = TwoHandMotion.keys(d, SwingMotion.Profile.H_R).hit().applyDir(BLADE);
		float[] hl0 = TwoHandMotion.keys(d, SwingMotion.Profile.H_L).wind().applyDir(BLADE);
		float[] hl1 = TwoHandMotion.keys(d, SwingMotion.Profile.H_L).hit().applyDir(BLADE);
		assertTrue(hr0[0] > 0.3f && hr1[0] < -0.3f, "right to left");
		assertTrue(hl0[0] < -0.3f && hl1[0] > 0.3f, "left to right");
		// overhead cuts end with the tip forward, the stab ends level
		for (SwingMotion.Profile p : new SwingMotion.Profile[] {SwingMotion.Profile.V_R, SwingMotion.Profile.V_L, SwingMotion.Profile.STAB}) {
			float[] h = TwoHandMotion.keys(d, p).hit().applyDir(BLADE);
			assertTrue(h[2] < -0.7f, p + " tip forward: " + h[2]);
			assertTrue(h[1] < 0.4f, p + " blade near level: " + h[1]);
		}
	}

	/**
	 * The user's own pose (2026-10-13): the cross-body diagonal guard. Camera frame (+x right, +y up, -z ahead): the blade (-0.508, 0.860, -0.037) is a diagonal
	 * in the frontal plane with the tip up and to the left, the cutting edge (model +Z) points up and right (0.861, 0.509, 0), the flats face forward and back.
	 */
	@Test
	void guardIsTheCrossBodyDiagonalWithTheTipUpAndLeft() {
		FpPose g = TwoHandMotion.guard(FpParams.DEFAULTS);
		float[] d = g.applyDir(FpRig.DEFAULT.axis());
		assertTrue(d[0] < -0.45f && d[1] > 0.7f && Math.abs(d[2]) < 0.1f, "across the chest, tip up left: " + java.util.Arrays.toString(d));
		assertEquals(0f, angleDeg(d, new float[] {-0.508f, 0.860f, -0.037f}), 1f, "blade direction of the user's pose");
		float[] edge = g.applyDir(modelDir(0f, 0f, 1f));
		assertEquals(0f, angleDeg(edge, new float[] {0.861f, 0.509f, 0f}), 1f, "cutting edge up and right in the frontal plane");
		float[] flat = g.applyDir(modelDir(1f, 0f, 0f));
		assertTrue(Math.abs(flat[2]) > 0.99f, "the flats of the blade face forward and back: " + flat[2]);
	}

	private static float[] modelDir(float x, float y, float z) {
		float[] a = FpRig.DEFAULT.hand(0.5f, 0.5f, 0.5f);
		float[] b = FpRig.DEFAULT.hand(0.5f + 0.1f * x, 0.5f + 0.1f * y, 0.5f + 0.1f * z);
		float l = (float) Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
		return new float[] {(b[0] - a[0]) / l, (b[1] - a[1]) / l, (b[2] - a[2]) / l};
	}

	private static float angleDeg(float[] a, float[] b) {
		double la = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		double lb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
		double c = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb);
		return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
	}

	@Test
	void guardPoseBlendsFromTheStanceByTheWeight() {
		FpParams d = FpParams.DEFAULTS;
		FpPose st = TwoHandMotion.stance(d, 1f);
		FpPose g = TwoHandMotion.guard(d);
		FpPose none = GuardMotion.pose(d, st, g, 0f, 0f, FpPose.ZERO, FpPose.ZERO);
		FpPose full = GuardMotion.pose(d, st, g, 1f, 0f, FpPose.ZERO, FpPose.ZERO);
		assertArrayEquals(st.pos(), none.pos(), EPS);
		assertArrayEquals(g.rot(), full.rot(), 1e-2f);
	}

	@Test
	void overridesReplaceKeysAndScaleWorksAboutTheStance() {
		Map<String, float[]> m = new java.util.HashMap<>();
		m.put("two.pos", new float[] {0.1f, 0.2f, 0.3f});
		m.put("two.rot", new float[] {1, 2, 3});
		m.put("two.swing.h_r.hit.pos", new float[] {1, 1, 1});
		FpParams o = params(m);
		assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, TwoHandMotion.stance(o, 1f).pos(), EPS);
		assertArrayEquals(new float[] {1, 1, 1}, TwoHandMotion.keys(o, SwingMotion.Profile.H_R).hit().pos(), EPS);
		// scale 0 collapses every key onto the stance, scale 1 leaves the shipped numbers
		m.clear();
		m.put("two.swing.scale", new float[] {0f});
		FpPose st = TwoHandMotion.stance(params(m), 1f);
		SwingMotion.Keys k = TwoHandMotion.keys(params(m), SwingMotion.Profile.V_R);
		assertArrayEquals(st.pos(), k.hit().pos(), EPS);
		assertArrayEquals(st.rot(), k.follow().rot(), EPS);
	}

	@Test
	void leftHandArrivesAfterTheStanceHasStarted() {
		assertEquals(0f, TwoHandMotion.leftWeight(0f), EPS);
		assertEquals(0f, TwoHandMotion.leftWeight(0.2f), EPS);
		assertEquals(1f, TwoHandMotion.leftWeight(1f), EPS);
		assertTrue(TwoHandMotion.leftWeight(0.6f) > 0.2f && TwoHandMotion.leftWeight(0.6f) < 0.8f);
	}

	@Test
	void idleSwayIsTinyAndOffWithoutTheStance() {
		FpParams d = FpParams.DEFAULTS;
		assertTrue(TwoHandMotion.idle(d, 1.3f, 0f).isNearZero());
		FpPose s = TwoHandMotion.idle(d, 1.3f, 1f);
		assertTrue(Math.abs(s.pos()[1]) <= 0.0031f && Math.abs(s.rot()[2]) < 0.5f);
	}

	@Test
	void oldOneHandedSampleStillEndsAtZero() {
		FpParams d = FpParams.DEFAULTS;
		SwingMotion.Timing tm = SwingMotion.Timing.DEFAULT;
		assertTrue(SwingMotion.sample(d, SwingMotion.Profile.H_R, FpPose.ZERO, 5f, 0.25f, tm).isNearZero());
	}
}
