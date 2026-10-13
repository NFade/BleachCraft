package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * First person block, the user's own pose (2026-10-13, replaces Pose B): the cross-body diagonal guard. Rules (design/results/block_pose_user_fp.md):
 * (1) blade direction in camera space within 4 degrees of (-0.508, 0.860, -0.037), cutting edge up and right in the frontal plane; (2) the closest
 * approach of the blade to the crosshair at least 8 degrees; (3) the arms (fists included) stay out of the central keep-out rectangle of {@link FpCentre}
 * and 0.15 blocks in front of the camera, the right fist is visible near the bottom edge; (4) the blend from the stance is smooth and does not sweep the
 * blade over the crosshair more than the old pose did; (5) the left fist stays on the hilt. All with the exact projection of {@link FpGeometry}
 * (vertical field of view 70) for 4:3, 16:9 and 21:9.
 */
class BlockPoseFpTest {
	private static final FpRig RIG = FpRig.DEFAULT;
	private static final float[] ASPECTS = {4f / 3f, 16f / 9f, 21f / 9f};
	private static final int SAMPLES = 400;
	private static final float[] TARGET_BLADE = {-0.508f, 0.860f, -0.037f};
	private static final float[] TARGET_EDGE = {0.861f, 0.509f, 0f};
	/** The r8 two handed guard and the one handed guard before the user's pose, the blends of which the new ones are measured against. */
	private static final FpPose R8_GUARD = FpPose.of(-0.32f, 0.10f, -0.06f, -14f, -16f, 54f);
	private static final FpPose OLD_ONE_HANDED = FpPose.of(-0.22f, 0.14f, -0.04f, -12f, -22f, 62f);

	/** Camera space points of the sword from the pommel to the tip. */
	private static float[][] blade(FpPose pose, float[] pivot) {
		float[][] r = new float[SAMPLES + 1][];
		for (int i = 0; i <= SAMPLES; i++) {
			float y = FpRig.POMMEL_Y + (FpRig.TIP_Y - FpRig.POMMEL_Y) * i / SAMPLES;
			r[i] = FpGeometry.cam(pose.apply(pivot, RIG.hand(0.5f, y, 0.5f)));
		}
		return r;
	}

	private static double azimuth(float[] c) {
		return Math.toDegrees(Math.atan2(c[0], -c[2]));
	}

	/** Azimuth (degrees) where the visible blade crosses the horizon (elevation 0), going up. */
	private static double horizonAzimuth(float[][] b) {
		for (int i = 0; i < b.length - 1; i++) {
			if (b[i][2] < -FpGeometry.NEAR && b[i + 1][2] < -FpGeometry.NEAR && b[i][1] <= 0f && b[i + 1][1] > 0f) {
				float t = -b[i][1] / (b[i + 1][1] - b[i][1]);
				float[] c = {b[i][0] + (b[i + 1][0] - b[i][0]) * t, 0f, b[i][2] + (b[i + 1][2] - b[i][2]) * t};
				return azimuth(c);
			}
		}
		throw new AssertionError("the blade does not cross the horizon in front of the camera");
	}

	/** Smallest angle (degrees) between the view axis and any visible point of the blade. */
	private static double closestApproach(float[][] b) {
		double best = 180;
		for (float[] c : b) {
			if (c[2] < -FpGeometry.NEAR) {
				double len = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
				best = Math.min(best, Math.toDegrees(Math.acos(-c[2] / len)));
			}
		}
		return best;
	}

	/** Margin of the visible blade to the central rectangle in ndc (negative: the blade is inside it). */
	private static float bladeRectMargin(float[][] b, float aspect) {
		float best = Float.MAX_VALUE;
		for (float[] c : b) {
			float[] n = FpGeometry.ndc(c, aspect);
			if (n != null) {
				best = Math.min(best, Math.max(Math.abs(n[0]) - FpCentre.HALF_X, Math.abs(n[1]) - FpCentre.HALF_Y));
			}
		}
		return best;
	}

	private static float angleDeg(float[] a, float[] b) {
		double la = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		double lb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
		double c = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb);
		return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
	}

	private static float[] modelDir(float x, float y, float z) {
		float[] a = RIG.hand(0.5f, 0.5f, 0.5f);
		float[] b = RIG.hand(0.5f + 0.1f * x, 0.5f + 0.1f * y, 0.5f + 0.1f * z);
		float l = (float) Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
		return new float[] {(b[0] - a[0]) / l, (b[1] - a[1]) / l, (b[2] - a[2]) / l};
	}

	/** Angle (degrees) the sword turns between two poses. */
	private static double turn(FpPose a, FpPose b) {
		float[] qa = a.quaternion();
		float[] qb = b.quaternion();
		double dot = Math.abs(qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]);
		return Math.toDegrees(2 * Math.acos(Math.min(1, dot)));
	}

	/** Smallest closest approach of the blade to the crosshair along the blend {@code base -> guard} (linear in the pose, as {@link FpPose#lerp}). */
	private static double blendClosest(FpPose base, FpPose guard, float[] pivot) {
		double best = 180;
		for (int i = 0; i <= 400; i++) {
			best = Math.min(best, closestApproach(blade(base.lerp(guard, i / 400f), pivot)));
		}
		return best;
	}

	/** Minimal depth in front of the camera of the corners of both arms of the two handed hold. */
	private static float armDepth(FpGeometry.Hold h, FpPose pose, FpPose st) {
		float d = Float.MAX_VALUE;
		for (float[] c : FpGeometry.rightArm(h, pose)) {
			d = Math.min(d, -FpGeometry.cam(c)[2]);
		}
		float[] fist = FpGeometry.leftFist(h, pose, st, 0f, null);
		for (float[] c : FpGeometry.leftArm(fist, pose, st, 0f, h.leftArmRot(), new float[3])) {
			d = Math.min(d, -c[2]);
		}
		return d;
	}

	// ------------------------------------------------------------------ rule 1: the direction and the roll of the user's pose

	@Test
	void theBladeAndTheEdgeHaveTheDirectionsOfTheUsersPose() {
		for (FpPose g : new FpPose[] {TwoHandMotion.guard(FpParams.DEFAULTS), GuardMotion.GUARD}) {
			float[] d = g.applyDir(RIG.axis());
			assertTrue(angleDeg(d, TARGET_BLADE) < 1f, "blade direction within 1 degree of (-0.508, 0.860, -0.037): " + angleDeg(d, TARGET_BLADE));
			// azimuth 86 degrees to the left, elevation 59 degrees
			assertEquals(-85.9, Math.toDegrees(Math.atan2(d[0], -d[2])), 1.5);
			assertEquals(59.4, Math.toDegrees(Math.asin(d[1])), 1.0);
			float[] edge = g.applyDir(modelDir(0f, 0f, 1f));
			assertTrue(angleDeg(edge, TARGET_EDGE) < 1f, "cutting edge up and right in the frontal plane: " + angleDeg(edge, TARGET_EDGE));
			assertTrue(Math.abs(g.applyDir(modelDir(1f, 0f, 0f))[2]) > 0.99f, "the flats of the blade face forward and back");
		}
	}

	// ------------------------------------------------------------------ rule 2: the crosshair

	@Test
	void theBladeStaysAwayFromTheCrosshairAndEntersAtTheBottomMiddle() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		float[][] two = blade(TwoHandMotion.guard(FpParams.DEFAULTS), h.pivot());
		float[][] one = blade(GuardMotion.GUARD, RIG.along(0f));
		for (float[][] b : new float[][][] {two, one}) {
			assertTrue(closestApproach(b) >= 8.0, "closest approach to the crosshair " + closestApproach(b));
			double hz = horizonAzimuth(b);
			assertTrue(hz < -15 && hz > -25, "the blade crosses the horizon about 20 degrees left of the middle: " + hz);
			// the blade rises to the upper left: the tip is higher and further left than the horizon crossing, and above the horizon
			assertTrue(azimuth(b[SAMPLES]) < hz - 5 && b[SAMPLES][1] > 0f, "tip up and to the left: " + azimuth(b[SAMPLES]));
			for (float a : ASPECTS) {
				// it comes in through the bottom edge near the middle (a little to the right of it)
				float[] bottom = null;
				for (int i = 0; i < SAMPLES; i++) {
					float[] p = FpGeometry.ndc(b[i], a);
					float[] q = FpGeometry.ndc(b[i + 1], a);
					if (p != null && q != null && p[1] <= -1f && q[1] > -1f) {
						float t = (-1f - p[1]) / (q[1] - p[1]);
						bottom = new float[] {p[0] + (q[0] - p[0]) * t, -1f};
						break;
					}
				}
				assertTrue(bottom != null, "the blade crosses the bottom edge at " + a);
				assertTrue(bottom[0] > -0.05f && bottom[0] < 0.15f, "at the bottom edge near the middle, a little to the right: " + bottom[0] + " at " + a);
				// the exact central rectangle: the blade is not allowed in it (it only touches it at 21:9, margin about +0.001)
				assertTrue(bladeRectMargin(b, a) >= 0f, "the blade is outside the keep-out rectangle at aspect " + a + ": margin " + bladeRectMargin(b, a));
			}
		}
	}

	// ------------------------------------------------------------------ rule 3: the arms

	@Test
	void theFistsAreAtTheBottomEdgeAndTheArmsAreOutOfTheMiddleAndInFrontOfTheCamera() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		FpPose g = TwoHandMotion.guard(FpParams.DEFAULTS);
		for (float a : ASPECTS) {
			float[] rf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.rightFist(h, g)), a);
			assertTrue(rf != null && Math.abs(rf[0]) < 0.3f, "right fist near the middle line at " + a);
			assertTrue(rf[1] < -0.8f && rf[1] > -1.0f, "right fist visible near the bottom edge at " + a + ": " + rf[1]);
			float[] lf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.leftFist(h, g, st, 0f, null)), a);
			assertTrue(lf == null || lf[1] < rf[1] - 0.2f, "the left fist below the right one");
			assertTrue(FpCentre.margin(h, g, st, null, a, FpCentre.HALF_X, FpCentre.HALF_Y) >= 0.2f, "arms clear of the middle at " + a);
		}
		assertTrue(armDepth(h, g, st) >= 0.15f, "both arms at least 0.15 blocks in front of the camera: " + armDepth(h, g, st));
		// the one handed fallback: vanilla arms, the left hand 0.30 up the blade
		FpGeometry.Hold one = new FpGeometry.Hold(RIG.along(0f), FpGeometry.HELD_ARM, new float[3], RIG.along(0f), new float[3], new float[3], false);
		float[] left = GuardMotion.leftFist(FpParams.DEFAULTS, GuardMotion.GUARD, RIG.along(0f), RIG.along(0.30f));
		float[][] la = FpGeometry.leftArm(left, GuardMotion.GUARD, FpPose.ZERO, 0f, new float[3], new float[3]);
		float depth = Float.MAX_VALUE;
		for (float[] c : FpGeometry.rightArm(one, GuardMotion.GUARD)) {
			depth = Math.min(depth, -FpGeometry.cam(c)[2]);
		}
		for (float[] c : la) {
			depth = Math.min(depth, -c[2]);
		}
		assertTrue(depth >= 0.15f, "one handed arms in front of the camera: " + depth);
		for (float a : ASPECTS) {
			assertTrue(FpCentre.right(one, GuardMotion.GUARD, a, FpCentre.HALF_X, FpCentre.HALF_Y) >= 0.1f, "one handed right arm clear of the middle at " + a);
			assertTrue(FpCentre.boxMargin(la, a, FpCentre.HALF_X, FpCentre.HALF_Y) >= 0.1f, "one handed left arm clear of the middle at " + a);
		}
		float[] rf1 = FpGeometry.ndc(FpGeometry.cam(FpGeometry.rightFist(one, GuardMotion.GUARD)), 16f / 9f);
		assertTrue(rf1 != null && rf1[1] < -0.8f && rf1[1] > -1.0f && Math.abs(rf1[0]) < 0.3f, "one handed right fist near the bottom edge");
	}

	// ------------------------------------------------------------------ rule 4: the blend from the stance

	@Test
	void theBlendFromTheStanceIsSmoothAndSafe() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		FpPose g = TwoHandMotion.guard(FpParams.DEFAULTS);
		// the Euler triple of the pose is one of several equivalent ones: the shipped one turns the sword about as little as possible (the direct turn is 103 degrees)
		double total = 0;
		double maxStep = 0;
		FpPose prev = GuardMotion.pose(FpParams.DEFAULTS, st, g, 0f, 0f, FpPose.ZERO, FpPose.ZERO);
		for (int i = 1; i <= 100; i++) {
			FpPose p = GuardMotion.pose(FpParams.DEFAULTS, st, g, i / 100f, 0f, FpPose.ZERO, FpPose.ZERO);
			double s = turn(prev, p);
			total += s;
			maxStep = Math.max(maxStep, s);
			prev = p;
		}
		assertTrue(total < 140, "the sword turns about 123 degrees on the way in, not around the world: " + total);
		assertTrue(maxStep < 4, "no jump of the blade between two steps of 1 percent of the weight: " + maxStep);
		float worstDepth = Float.MAX_VALUE;
		float worstArm = Float.MAX_VALUE;
		for (int i = 0; i <= 40; i++) {
			FpPose p = GuardMotion.pose(FpParams.DEFAULTS, st, g, i / 40f, 0f, FpPose.ZERO, FpPose.ZERO);
			worstDepth = Math.min(worstDepth, armDepth(h, p, st));
			worstArm = Math.min(worstArm, FpCentre.margin(h, p, st, null, 16f / 9f, FpCentre.HALF_X, FpCentre.HALF_Y));
		}
		assertTrue(worstDepth >= 0.15f, "the arms never come closer than 0.15 blocks to the camera during the blend: " + worstDepth);
		assertTrue(worstArm >= 0.1f, "the arms stay out of the middle during the blend: " + worstArm);
		// the blade has to sweep from the right of the middle (stance) to the left, so it passes the crosshair once; not closer than the r8 pose did
		double fresh = blendClosest(st, g, h.pivot());
		double old = blendClosest(st, R8_GUARD, h.pivot());
		assertTrue(fresh >= old, "the blade passes the crosshair at " + fresh + " degrees, the r8 guard at " + old);
		// the one handed fallback comes from the rest pose: both the old and the new pose pass over the crosshair (within a fraction of a degree)
		double fresh1 = blendClosest(FpPose.ZERO, GuardMotion.GUARD, RIG.along(0f));
		double old1 = blendClosest(FpPose.ZERO, OLD_ONE_HANDED, RIG.along(0f));
		assertTrue(fresh1 >= old1 - 0.2, "one handed: " + fresh1 + " degrees, before " + old1);
	}

	// ------------------------------------------------------------------ rule 5: the left fist

	@Test
	void theLeftFistSitsOnTheHilt() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		FpPose g = TwoHandMotion.guard(FpParams.DEFAULTS);
		float[] left = FpGeometry.leftFist(h, g, st, 0f, null);
		float[] a = g.apply(h.pivot(), RIG.hand(0.5f, FpRig.POMMEL_Y, 0.5f));
		float[] b = g.apply(h.pivot(), RIG.hand(0.5f, FpRig.TSUBA_Y, 0.5f));
		float[] d = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
		float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		float[] v = {left[0] - a[0], left[1] - a[1], left[2] - a[2]};
		float s = (v[0] * d[0] + v[1] * d[1] + v[2] * d[2]) / len;
		float[] off = {v[0] - d[0] / len * s, v[1] - d[1] / len * s, v[2] - d[2] / len * s};
		assertEquals(0f, (float) Math.sqrt(off[0] * off[0] + off[1] * off[1] + off[2] * off[2]), 1e-4f, "the left fist is on the blade axis");
		assertTrue(s > 0f && s < len, "between the pommel and the underside of the tsuba: " + s + " of " + len);
		// the right fist is above the left one on the hilt, a fist apart
		float[] right = FpGeometry.rightFist(h, g);
		float gap = (float) Math.sqrt(Math.pow(right[0] - left[0], 2) + Math.pow(right[1] - left[1], 2) + Math.pow(right[2] - left[2], 2));
		assertTrue(gap > 0.2f && gap < 0.5f, "two fists on the hilt: " + gap);
	}
}
