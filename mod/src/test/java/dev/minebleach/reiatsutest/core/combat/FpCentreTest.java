package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * r8 rule of the first person hands: no part of an arm (the vanilla arm box, fist included) projects into the central 18 percent x 18 percent
 * of the picture, in every held pose and at every moment of every ability gesture (the sword is not an arm and may cross the middle). Checked with
 * the exact projection of {@link FpCentre} (box clipped at the near plane, convex hull, separating axis test) for the widths 4:3, 16:9 and 21:9.
 */
class FpCentreTest {
	private static final FpRig RIG = FpRig.DEFAULT;
	private static final float[] ASPECTS = {4f / 3f, 16f / 9f, 21f / 9f};
	/** Shipped amplitude of the hand rows of the gestures (FpCast.HANDS_SCALE), forward limit (fp_cast.clamp.fwd) and drop. */
	private static final float SCALE = 0.75f;
	private static final float DROP = 0f;
	private static final float FWD = 0.30f;
	private static final float EPS = 2e-4f;

	private static FpGeometry.Hold sealedHold() {
		return new FpGeometry.Hold(RIG.along(0f), FpGeometry.HELD_ARM, new float[3], RIG.along(0f), new float[3], new float[3], false);
	}

	/** Highest point of the picture of the right arm, ndc y. */
	private static float armTop(FpGeometry.Hold h, FpPose pose, float aspect) {
		float top = -9f;
		for (float[] c : FpGeometry.rightArm(h, pose)) {
			float[] n = FpGeometry.ndc(FpGeometry.cam(c), aspect);
			if (n != null) {
				top = Math.max(top, n[1]);
			}
		}
		return top;
	}

	// ------------------------------------------------------------------ the projection maths itself

	/** Camera space corners of an axis aligned box in the order of FpGeometry.armCorners (x outer, y, z inner). */
	private static float[][] box(float x0, float x1, float y0, float y1, float z0, float z1) {
		float[][] c = new float[8][];
		int n = 0;
		for (float x : new float[] {x0, x1}) {
			for (float y : new float[] {y0, y1}) {
				for (float z : new float[] {z0, z1}) {
					c[n++] = new float[] {x, y, z};
				}
			}
		}
		return c;
	}

	@Test
	void aBoxInFrontOfTheMiddleIsInsideAndOneAtTheSideIsClear() {
		float a = 16f / 9f;
		assertTrue(FpCentre.boxMargin(box(-0.1f, 0.1f, -0.1f, 0.1f, -1.2f, -0.8f), a, 0.18f, 0.18f) < -0.05f, "centred box: penetrates");
		assertTrue(FpCentre.boxMargin(box(0.6f, 0.8f, -0.1f, 0.1f, -1.2f, -0.8f), a, 0.18f, 0.18f) > 0.1f, "box far to the right: clear");
		assertTrue(FpCentre.boxMargin(box(-0.1f, 0.1f, -0.9f, -0.6f, -1.2f, -0.8f), a, 0.18f, 0.18f) > 0.1f, "box low in the picture: clear");
		// exactly touching: the box edge at x = hx * tan(35) * aspect * depth
		float depth = 1.0f;
		float xEdge = 0.18f * (float) Math.tan(Math.toRadians(35.0)) * a * depth;
		assertEquals(0f, FpCentre.boxMargin(box(xEdge, xEdge + 0.3f, -0.1f, 0.1f, -depth, -depth - 0.01f), a, 0.18f, 0.18f), 0.01f);
	}

	@Test
	void aBoxThatCrossesTheNearPlaneIsClippedNotDropped() {
		float a = 16f / 9f;
		// a forearm reaching from far in front to behind the eye, straight through the middle of the picture
		assertTrue(FpCentre.boxMargin(box(-0.05f, 0.05f, -0.05f, 0.05f, -2f, 0.4f), a, 0.18f, 0.18f) < 0f, "crosses the middle, part of it behind the plane");
		// the same box low and to the side, also crossing the plane: its clipped part shows near the edges, not the middle
		assertTrue(FpCentre.boxMargin(box(0.4f, 0.5f, -0.5f, -0.4f, -2f, 0.4f), a, 0.18f, 0.18f) > 0f);
		// all behind the camera: nothing to see
		assertTrue(FpCentre.boxMargin(box(-0.1f, 0.1f, -0.1f, 0.1f, 0.5f, 1f), a, 0.18f, 0.18f) > 0.5f);
	}

	@Test
	void separationIsExactForADiagonalPolygon() {
		// a thin diagonal bar from the lower left to the upper right passing the corner of the rectangle at a distance (the bounding boxes overlap, the shapes do not)
		float[][] bar = {{-0.5f, 0.05f}, {0.2f, 0.75f}, {0.25f, 0.7f}, {-0.45f, 0.0f}};
		float[][] hull = FpCentre.hull(bar);
		float sep = FpCentre.separation(hull, 0.18f, 0.18f);
		assertTrue(sep > 0.05f, "clear of the rectangle although the boxes of both overlap: " + sep);
		float[][] inside = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, 0.5f}};
		assertTrue(FpCentre.separation(FpCentre.hull(inside), 0.18f, 0.18f) < -0.3f, "the rectangle inside a big square: deep penetration");
	}

	@Test
	void shapeScalesDropsAndLimitsForwardButKeepsTheFreeWeight() {
		float[] r = {0.1f, 0.2f, -0.8f, 10f, 20f, 30f};
		float[] l = {0.1f, 0.2f, 0.1f, 10f, 20f, 30f, 1f};
		FpCentre.Shaped s = FpCentre.shape(r, l, 1f, 0.5f, 0.1f, 0.3f);
		assertEquals(0.05f, s.r()[0], 1e-6f);
		assertEquals(0.1f - 0.1f, s.r()[1], 1e-6f, "scaled 0.1, dropped by 0.1");
		assertTrue(s.r()[2] > -0.3f && s.r()[2] < -0.2f, "forward reach soft limited: " + s.r()[2]);
		assertEquals(10f, s.r()[4], 1e-4f);
		assertEquals(1f, s.l()[6], 0f, "the free weight is not an amplitude");
		assertEquals(0.05f, s.l()[2], 1e-6f, "backwards untouched, scaled");
		assertEquals(0.2f, r[1], 0f, "the input is not modified");
		FpCentre.Shaped half = FpCentre.shape(r, l, 0.5f, 1f, 0.1f, 9f);
		assertEquals(0.2f - 0.05f, half.r()[1], 1e-6f, "the drop follows the envelope");
	}

	// ------------------------------------------------------------------ the held poses

	@Test
	void theDrawnStanceAndTheBlockKeepTheArmsOutOfTheMiddleAndLowerThanBefore() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		FpPose old = FpPose.of(-0.47f, 0.105f, 0.06f, -36f, 0f, -8f);
		FpPose oldGuard = FpPose.of(-0.32f, 0.22f, -0.06f, -14f, -16f, 54f);
		for (float a : ASPECTS) {
			float m = FpCentre.margin(h, st, st, null, a, FpCentre.HALF_X, FpCentre.HALF_Y);
			assertTrue(m > 0.15f, "stance clear of the middle by " + m + " at " + a);
			float g = FpCentre.margin(h, TwoHandMotion.guard(FpParams.DEFAULTS), st, null, a, FpCentre.HALF_X, FpCentre.HALF_Y);
			assertTrue(g > 0.08f, "guard clear of the middle by " + g + " at " + a);
			assertTrue(armTop(h, st, a) < armTop(h, old, a) - 0.08f, "the stance is lower than the old one: " + armTop(h, st, a) + " against " + armTop(h, old, a));
			assertTrue(armTop(h, TwoHandMotion.guard(FpParams.DEFAULTS), a) < armTop(h, oldGuard, a) - 0.08f, "the guard is lower than the old one");
			float[] rf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.rightFist(h, st)), a);
			assertTrue(rf[1] < -0.65f, "the right fist in the lower sixth: " + rf[1]);
		}
		assertTrue(FpCentre.margin(h, old, old, null, 16f / 9f, 0.18f, 0.18f) < FpCentre.margin(h, st, st, null, 16f / 9f, 0.18f, 0.18f) - 0.1f);
	}

	@Test
	void theSheathedHoldAndItsBlockKeepTheRightArmOutOfTheMiddle() {
		FpGeometry.Hold h = sealedHold();
		float[] rest = GuardMotion.SEALED_REST;
		FpPose lowered = FpPose.of(rest[0], rest[1], rest[2], 0f, 0f, 0f);
		for (float a : ASPECTS) {
			assertTrue(FpCentre.right(h, lowered, a, 0.18f, 0.18f) > 0.3f, "sheathed rest at " + a);
			assertTrue(armTop(h, lowered, a) < armTop(h, FpPose.ZERO, a) - 0.03f, "the sheathed hold sits lower than before");
			assertTrue(FpCentre.right(h, GuardMotion.SEALED_GUARD, a, 0.18f, 0.18f) > 0.3f, "sheathed block at " + a);
		}
		assertTrue(rest[1] < 0f && Math.abs(rest[0]) < 1e-6f, "a pure drop");
	}

	@Test
	void theIdleOfTheEmptyHandsKeepsBothArmsOutOfTheMiddle() {
		for (float a : ASPECTS) {
			for (boolean left : new boolean[] {false, true}) {
				float m = FpCentre.boxMargin(FpCentre.freeArm(left, FreeHands.frame(FpParams.DEFAULTS, left, null, 0f, 0f, 1f)), a, 0.18f, 0.18f);
				assertTrue(m > 0.08f, (left ? "left" : "right") + " idle arm clear by " + m + " at " + a);
			}
		}
	}

	// ------------------------------------------------------------------ every gesture, every frame

	@Test
	void everyGestureWithTheSwordStaysOutOfTheMiddleAtEveryFrame() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		for (float a : ASPECTS) {
			FpClamp.Limits lim = new FpClamp.Limits(FpClamp.NEAR, a, 1.0f, -1.2f, 0.95f, FpCentre.HALF_X, FpCentre.HALF_Y);
			float sumMean = 0f;
			int gestures = 0;
			for (Map.Entry<String, CastGesture> e : CastGestures.defaults().entrySet()) {
				CastGesture g = e.getValue();
				if (g.freeHands()) {
					continue;
				}
				float worst = 9f;
				float sumK = 0f;
				int n = 0;
				for (float t = 0f; t <= g.totalSeconds(); t += 1f / 60f) {
					CastGesture.Sample s = g.sampleReal(t);
					FpCentre.Shaped sh = FpCentre.shape(s.r(), s.l(), s.env(), SCALE, DROP, FWD);
					FpClamp.Factors f = FpClamp.factors(h, RIG, st, sh.r(), sh.l(), lim);
					float[] r = sh.r().clone();
					float[] l = sh.l().clone();
					for (int i = 0; i < 6; i++) {
						r[i] *= f.right();
						l[i] *= f.left();
					}
					FpPose pose = st.plus(FpPose.of(r[0], r[1], r[2], r[3], r[4], r[5]));
					worst = Math.min(worst, FpCentre.margin(h, pose, st, l, a, FpCentre.HALF_X, FpCentre.HALF_Y));
					sumK += Math.min(f.right(), f.left());
					n++;
				}
				assertTrue(worst >= -EPS, e.getKey() + ": an arm covers the middle of the picture, margin " + worst + " at aspect " + a);
				sumMean += sumK / n;
				gestures++;
			}
			// the rule keeps the gestures: on average at least 85 percent of the amplitude stays
			assertTrue(sumMean / gestures > 0.85f, "the gestures are not silenced: mean factor " + sumMean / gestures + " at " + a);
		}
	}

	@Test
	void everyGestureOfTheEmptyHandsStaysOutOfTheMiddleAtEveryFrame() {
		FpParams pr = FpParams.DEFAULTS;
		for (float a : ASPECTS) {
			for (Map.Entry<String, CastGesture> e : CastGestures.defaults().entrySet()) {
				CastGesture g = e.getValue();
				if (!g.freeHands()) {
					continue;
				}
				float worst = 9f;
				for (float t = 0f; t <= g.totalSeconds(); t += 1f / 60f) {
					CastGesture.Sample s = g.sampleReal(t);
					FpCentre.Shaped sh = FpCentre.shape(s.r(), s.l(), s.env(), SCALE, DROP, 0.50f);
					for (boolean left : new boolean[] {false, true}) {
						float[] row = (left ? sh.l() : sh.r()).clone();
						float k = FpCentre.freeFactor(pr, left, row, t, a, FpCentre.HALF_X, FpCentre.HALF_Y);
						assertTrue(k >= 0f && k <= 1f);
						for (int i = 0; i < 6; i++) {
							row[i] *= k;
						}
						float m = FpCentre.boxMargin(FpCentre.freeArm(left, FreeHands.frame(pr, left, row, t, 0f, 1f)), a, FpCentre.HALF_X, FpCentre.HALF_Y);
						worst = Math.min(worst, m);
					}
				}
				assertTrue(worst >= -EPS, e.getKey() + ": an empty arm covers the middle, margin " + worst + " at aspect " + a);
			}
		}
	}

	@Test
	void theLimitCanBeSwitchedOffAndTheOldLimitsStillWork() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		// a hand pushed up into the middle of the view
		FpPose up = st.plus(FpPose.of(0f, 0.45f, 0f, 0f, 0f, 0f));
		FpClamp.Limits off = new FpClamp.Limits(FpClamp.NEAR, 16f / 9f, 9f, -9f, 9f);
		FpClamp.Limits on = new FpClamp.Limits(FpClamp.NEAR, 16f / 9f, 9f, -9f, 9f, FpCentre.HALF_X, FpCentre.HALF_Y);
		assertTrue(FpClamp.margin(h, RIG, up, st, null, off) > 0f, "no centre rule: allowed");
		assertTrue(FpClamp.margin(h, RIG, up, st, null, on) < 0f, "with the centre rule: the arm is in the middle");
		float[] r = {0f, 0.45f, 0f, 0f, 0f, 0f};
		assertTrue(FpClamp.factors(h, RIG, st, r, null, on).right() < 0.9f);
		assertEquals(1f, FpClamp.factors(h, RIG, st, r, null, off).right(), 0f);
	}
}
