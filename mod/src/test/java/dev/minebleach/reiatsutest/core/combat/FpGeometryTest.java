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

	@Test
	void rightFistIsFlushUnderTheTsubaWithoutEnteringIt() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, RIG);
		float[][] c = FpGeometry.rightArm(h, FpPose.ZERO);
		float[] axis = RIG.axis();
		float[] grip = RIG.along(0f);
		float tsubaBottom = dot(sub(RIG.hand(0.5f, FpRig.TSUBA_Y, 0.5f), grip), axis);
		float top = -9f;
		float topWide = -9f;
		for (int a = 0; a <= 12; a++) {
			for (int b = 0; b <= 24; b++) {
				for (int d = 0; d <= 12; d++) {
					float[] q = sub(boxPoint(c, a / 12f, b / 24f, d / 12f), grip);
					float height = dot(q, axis);
					float rad = (float) Math.sqrt(Math.max(0f, dot(q, q) - height * height));
					if (rad < FpRig.TSUBA_RADIUS) {
						top = Math.max(top, height - tsubaBottom);
					}
					if (rad < 0.14f) {
						topWide = Math.max(topWide, height - tsubaBottom);
					}
				}
			}
		}
		assertTrue(top <= 0f, "nothing of the fist inside the tsuba: " + top);
		assertTrue(top > -0.02f, "no visible gap under the tsuba: " + top);
		// the top of the fist is flat against the tsuba: the wide tsuba of Senbonzakura (0.13 blocks across the blade) is not entered either
		assertTrue(topWide <= 0.004f, "nothing of the fist inside a wide tsuba: " + topWide);
	}

	@Test
	void leftFistIsUnderTheRightOneAndThePommelSticksOutBelow() {
		FpParams p = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(p, RIG);
		FpPose zero = FpPose.ZERO;
		float[] axis = RIG.axis();
		float[] grip = RIG.along(0f);
		float[] fist = FpGeometry.leftFist(h, zero, zero, 0f, null);
		float[][] lc = FpGeometry.leftArm(fist, zero, zero, 0f, h.leftArmRot(), new float[3]);
		float lowL = 9f;
		float highL = -9f;
		for (int a = 0; a <= 8; a++) {
			for (int b = 0; b <= 24; b++) {
				for (int d = 0; d <= 8; d++) {
					float[] q = boxPoint(lc, a / 8f, b / 24f, d / 8f);
					q = new float[] {q[0] - FpGeometry.BASE[0], q[1] - FpGeometry.BASE[1], q[2] - FpGeometry.BASE[2]};
					q = sub(q, grip);
					float height = dot(q, axis);
					float rad = (float) Math.sqrt(Math.max(0f, dot(q, q) - height * height));
					if (rad < 0.04f) {
						lowL = Math.min(lowL, height);
						highL = Math.max(highL, height);
					}
				}
			}
		}
		float pommel = dot(sub(RIG.hand(0.5f, FpRig.POMMEL_Y, 0.5f), grip), axis);
		assertTrue(highL > lowL, "the hilt runs through the left fist");
		assertTrue(lowL > pommel, "the pommel sticks out below the left fist: " + lowL + " vs " + pommel);
		// the left fist is below the right one on the hilt
		float[] rc = FpGeometry.rightFist(h, zero);
		assertTrue(dot(sub(fist, grip), axis) < dot(sub(rc, grip), axis) - 0.15f);
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
