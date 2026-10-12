package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Geometry of the first person hands against the sword and the camera (round 5): the vanilla arm, the flush fist, the pommel below the fists. */
class FpGeometryTest {
	private static final FpRig RIG = FpRig.DEFAULT;

	/** Point of the box of an arm: (u, v, w) in 0..1 across x, along y and across z of the arm part (corner order of {@link FpGeometry#corners}). */
	private static float[] boxPoint(float[][] c, float u, float v, float w) {
		float[] r = new float[3];
		for (int xi = 0; xi < 2; xi++) {
			for (int yi = 0; yi < 2; yi++) {
				for (int zi = 0; zi < 2; zi++) {
					float k = (xi == 0 ? 1f - u : u) * (yi == 0 ? 1f - v : v) * (zi == 0 ? 1f - w : w);
					float[] p = c[xi * 4 + yi * 2 + zi];
					for (int a = 0; a < 3; a++) {
						r[a] += p[a] * k;
					}
				}
			}
		}
		return r;
	}

	private static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static float[] sub(float[] a, float[] b) {
		return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	@Test
	void vanillaArmBoxHasItsFistWhereTheHandCodeAssumesIt() {
		for (boolean left : new boolean[] {false, true}) {
			float[][] c = FpGeometry.corners(left);
			float[] start = boxPoint(c, 0.5f, 0f, 0.5f);
			float[] end = boxPoint(c, 0.5f, 1f, 0.5f);
			float[] ax = sub(end, start);
			float len = (float) Math.sqrt(dot(ax, ax));
			assertEquals(0.75f, len, 1e-3f, "the arm is 12 pixels long");
			float[] fist = {end[0] - 0.125f * ax[0] / len * 1f, end[1] - 0.125f * ax[1] / len, end[2] - 0.125f * ax[2] / len};
			assertEquals((left ? -1f : 1f) * FpGeometry.FIST[0], fist[0], 2e-3f, "fist x, left=" + left);
			assertEquals(FpGeometry.FIST[1], fist[1], 2e-3f);
			assertEquals(FpGeometry.FIST[2], fist[2], 2e-3f);
		}
		// the first corner of the right arm, from the projection model (python replay of the chain)
		float[] c0 = FpGeometry.corners(false)[0];
		assertEquals(-0.137f, c0[0], 2e-3f);
		assertEquals(-0.158f, c0[1], 2e-3f);
		assertEquals(0.257f, c0[2], 2e-3f);
	}

	/** Height along the blade (blocks above the grip) of the highest and lowest point of an arm box within {@code rad} blocks of the blade axis. */
	private static float[] heightRange(float[][] corners, float[] gripCam, float[] axis, float rad) {
		float lo = 9f;
		float hi = -9f;
		for (int a = 0; a <= 10; a++) {
			for (int b = 0; b <= 20; b++) {
				for (int d = 0; d <= 10; d++) {
					float[] q = sub(boxPoint(corners, a / 10f, b / 20f, d / 10f), gripCam);
					float height = dot(q, axis);
					float r = (float) Math.sqrt(Math.max(0f, dot(q, q) - height * height));
					if (r < rad) {
						lo = Math.min(lo, height);
						hi = Math.max(hi, height);
					}
				}
			}
		}
		return new float[] {lo, hi};
	}

	@Test
	void rightFistIsUnderTheTsubaWithAStretchOfHiltAbove() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, RIG);
		FpPose st = TwoHandMotion.stance(p, 1f);
		float[][] c = FpGeometry.rightArm(h, st);
		float[] axis = FpGeometry.bladeDir(h, st);
		float[] grip = FpGeometry.cam(st.apply(h.pivot(), RIG.along(0f)));
		float tsuba = dot(sub(FpGeometry.cam(st.apply(h.pivot(), RIG.hand(0.5f, FpRig.TSUBA_Y, 0.5f))), grip), axis);
		float[] range = heightRange(c, grip, axis, 0.05f);
		assertTrue(range[1] < tsuba - 0.02f, "the top of the fist is under the tsuba: " + range[1] + " vs " + tsuba);
		assertTrue(range[1] > tsuba - 0.12f, "but not far under it (a visible stretch of hilt, not a gap in the grip): " + range[1]);
	}

	@Test
	void leftFistIsUnderTheRightOneAndThePommelSticksOutBelow() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, RIG);
		FpPose st = TwoHandMotion.stance(p, 1f);
		float[] axis = FpGeometry.bladeDir(h, st);
		float[] grip = FpGeometry.cam(st.apply(h.pivot(), RIG.along(0f)));
		float[] fist = FpGeometry.leftFist(h, st, st, 0f, null);
		float[][] lc = FpGeometry.leftArm(h, fist, st, st, 0f);
		float[] range = heightRange(lc, grip, axis, 0.04f);
		float pommel = dot(sub(FpGeometry.cam(st.apply(h.pivot(), RIG.hand(0.5f, FpRig.POMMEL_Y, 0.5f))), grip), axis);
		assertTrue(range[1] > range[0], "the hilt runs through the left fist");
		float fistHeight = dot(sub(FpGeometry.cam(fist), grip), axis);
		assertTrue(fistHeight - 0.125f > pommel - 0.01f, "the pommel sticks out below the left fist cube: " + (fistHeight - 0.125f) + " vs " + pommel);
		// the left fist is below the right one on the hilt
		float[] rc = FpGeometry.cam(FpGeometry.rightFist(h, st));
		assertTrue(dot(sub(FpGeometry.cam(fist), grip), axis) < dot(sub(rc, grip), axis) - 0.15f);
	}

	@Test
	void bothArmsComeFromTheBodyNotFromTheAir() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, RIG);
		for (FpPose pose : new FpPose[] {TwoHandMotion.stance(p, 1f), TwoHandMotion.guard(p), GuardMotion.sealedIdle(p)}) {
			float[][] r = FpGeometry.rightArm(h, pose);
			// the far end of the arm (y = 0 face) is lower than the fist and nearer to the camera: it leaves the picture at the bottom
			float[] far = boxPoint(r, 0.5f, 0f, 0.5f);
			float[] near = boxPoint(r, 0.5f, 1f, 0.5f);
			assertTrue(far[1] < near[1] && far[2] > near[2], "the arm comes from below and behind the fist");
		}
	}

	@Test
	void stanceKeepsEverythingWellInFrontOfTheCamera() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, RIG);
		FpPose st = TwoHandMotion.stance(p, 1f);
		float m = FpClamp.margin(h, RIG, st, st, null, new FpClamp.Limits(FpGeometry.NEAR, 16f / 9f, 9f, -9f, 9f));
		assertTrue(m > 0.25f, "margin in front of the near plane: " + m);
	}

	@Test
	void projectionIsBehindTheNearPlaneIsNull() {
		assertNull(FpGeometry.ndc(new float[] {0f, 0f, -0.01f}, 16f / 9f));
		float[] c = FpGeometry.ndc(new float[] {0f, 0.7f, -1f}, 16f / 9f);
		assertEquals(0f, c[0], 1e-6f);
		assertEquals(1f, c[1], 2e-3f, "vertical field of view 70: 0.7 up at one block is the top edge");
	}
}
