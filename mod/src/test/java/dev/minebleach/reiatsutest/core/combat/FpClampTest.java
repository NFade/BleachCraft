package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reach limits of the ability gestures (round 5): the hands and the blade stay in front of the camera, the limit is smooth. */
class FpClampTest {
	private static final FpRig RIG = FpRig.DEFAULT;
	private static final float ASPECT = 16f / 9f;
	private static final FpClamp.Limits LIM = FpClamp.Limits.of(ASPECT);
	/** Near plane only (the strikes may take the hands anywhere on the screen). */
	private static final FpClamp.Limits NEAR_ONLY = new FpClamp.Limits(FpGeometry.NEAR, ASPECT, 9f, -9f, 9f);

	@Test
	void solveFindsTheLargestAllowedFactor() {
		assertEquals(1f, FpClamp.solve(k -> 0.3f - 0.1f * k), 0f, "whole gesture allowed");
		assertEquals(0f, FpClamp.solve(k -> -0.1f - k), 0f, "not even nothing is allowed: nothing is played");
		float k = FpClamp.solve(kk -> 0.5f - kk);
		assertEquals(0.5f, k, 1e-3f);
		assertTrue(0.5f - k >= 0f, "never over the limit");
	}

	@Test
	void releaseFallsAtOnceAndComesBackSlowly() {
		assertEquals(0.4f, FpClamp.release(0.9f, 0.4f, 0.016f, 0.15f), 0f);
		float up = FpClamp.release(0.4f, 1f, 0.015f, 0.15f);
		assertEquals(0.5f, up, 1e-5f);
		assertEquals(1f, FpClamp.release(0.95f, 1f, 0.1f, 0.15f), 0f);
	}

	@Test
	void softForwardIsSmoothAndNeverBeyondTheMaximum() {
		assertEquals(0.2f, FpClamp.softForward(0.2f, 0.3f), 0f, "backwards is not limited");
		assertEquals(0f, FpClamp.softForward(0f, 0.3f), 0f);
		assertEquals(-0.02f, FpClamp.softForward(-0.02f, 0.3f), 1e-3f, "small ones stay as they are");
		float prev = 0f;
		for (float z = -0.01f; z > -3f; z -= 0.01f) {
			float v = FpClamp.softForward(z, 0.3f);
			assertTrue(v <= prev && v >= -0.3f, "monotone and not beyond the maximum at " + z + ": " + v);
			prev = v;
		}
		assertTrue(FpClamp.softForward(-0.44f, 0.3f) > -0.29f);
	}

	@Test
	void aBladeHeldUpOverTheHeadIsNotLimitedButOnePointedAtTheEyeIs() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		// over the head and back (the tip goes behind the camera plane far above the screen): the bar crosses the near plane outside of the picture
		FpPose over = st.plus(FpPose.of(0f, 0.15f, -0.06f, 62f, 0f, 6f));
		assertTrue(FpClamp.bladeMargin(h, RIG, over, FpClamp.NEAR, ASPECT) > 0f, "overhead is fine");
		// the sword turned about the view axis keeps its place: fine too
		assertTrue(FpClamp.bladeMargin(h, RIG, st.plus(FpPose.of(0f, 0f, 0f, 0f, 0f, 80f)), FpClamp.NEAR, ASPECT) > 0f);
		// the tip pushed to the eye: raise the hands to the camera height and turn the blade back, the bar crosses the plane right in front of the eye
		FpPose eye = FpPose.of(-0.7f, 0.7f, 0.5f, 80f, 0f, -7f);
		float m = FpClamp.bladeMargin(h, RIG, eye, FpClamp.NEAR, ASPECT);
		assertTrue(m < 0f, "a blade into the face is limited: " + m);
	}

	@Test
	void anOverReachingGestureIsScaledDownJustEnough() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		float[] r = {-0.7f + 0.47f, 0.7f - 0.06f, 0.5f - 0.06f, 116f, 0f, 1f}; // the pose of a blade pointing into the face, as a delta on the stance
		FpClamp.Factors f = FpClamp.factors(h, RIG, st, r, null, LIM);
		assertTrue(f.right() < 0.95f && f.right() > 0f, "scaled down: " + f.right());
		FpPose delta = FpPose.of(r[0], r[1], r[2], r[3], r[4], r[5]);
		float inside = FpClamp.margin(h, RIG, st.plus(delta.scaled(f.right())), st, null, LIM);
		assertTrue(inside >= -1e-3f, "allowed at the factor: " + inside);
		// a hair more is not allowed: the factor is the largest
		float outside = FpClamp.margin(h, RIG, st.plus(delta.scaled(Math.min(1f, f.right() + 0.02f))), st, null, LIM);
		assertTrue(outside < 0f, "a hair more is over the limit: " + outside);
	}

	@Test
	void theLeftArmTurnedBackToTheCameraIsLimited() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		// the free left hand pulled right up to the camera
		float[] l = {0.1f, 0.5f, 0.75f, 0f, 0f, 0f, 1f};
		FpClamp.Factors f = FpClamp.factors(h, RIG, st, new float[6], l, LIM);
		assertEquals(1f, f.right(), 0f, "the right hand does not move");
		assertTrue(f.left() < 1f, "the left hand is limited: " + f.left());
	}

	@Test
	void noShippedGestureNeedsTheLimitWithTheNewStance() {
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(FpParams.DEFAULTS, RIG);
		FpPose st = TwoHandMotion.stance(FpParams.DEFAULTS, 1f);
		for (Map.Entry<String, CastGesture> e : CastGestures.defaults().entrySet()) {
			CastGesture g = e.getValue();
			if (g.freeHands()) {
				continue; // the empty hands of the Byakuya bankai have no sword in the hand
			}
			float worst = 1f;
			float worstMargin = 9f;
			float tot = g.totalSeconds();
			for (float t = 0f; t <= tot; t += 1f / 60f) {
				CastGesture.Sample s = g.sampleReal(t);
				float[] r = s.r().clone();
				float[] l = s.l().clone();
				r[2] = FpClamp.softForward(r[2], 0.30f);
				l[2] = FpClamp.softForward(l[2], 0.30f);
				FpClamp.Factors f = FpClamp.factors(h, RIG, st, r, l, LIM);
				worst = Math.min(worst, Math.min(f.right(), f.left()));
				FpPose pose = st.plus(FpPose.of(r[0], r[1], r[2], r[3], r[4], r[5]));
				worstMargin = Math.min(worstMargin, FpClamp.margin(h, RIG, pose, st, l, LIM));
				// the fists stay on the screen
				float[] rf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.rightFist(h, pose)), ASPECT);
				assertTrue(rf != null && Math.abs(rf[0]) < 1f && rf[1] > -1.06f && rf[1] < 0.96f, e.getKey() + " right fist on screen at " + t);
				float[] lf = FpGeometry.ndc(FpGeometry.cam(FpGeometry.leftFist(h, pose, st, l[6], new float[] {l[0], l[1], l[2]})), ASPECT);
				assertTrue(lf != null && Math.abs(lf[0]) < 1f && lf[1] > -1.06f && lf[1] < 0.96f, e.getKey() + " left fist on screen at " + t);
			}
			assertTrue(worst > 0.9f, e.getKey() + " needs the reach limit: factor " + worst);
			assertTrue(worstMargin >= -0.01f, e.getKey() + " too close to the camera: margin " + worstMargin);
		}
	}

	@Test
	void strikeAndGuardKeysOfTheNewStanceStayInFrontOfTheNearPlane() {
		FpParams d = FpParams.DEFAULTS;
		FpGeometry.Hold h = FpGeometry.Hold.twoHanded(d, RIG);
		FpPose st = TwoHandMotion.stance(d, 1f);
		assertTrue(FpClamp.margin(h, RIG, TwoHandMotion.guard(d), st, null, NEAR_ONLY) > 0.1f, "guard");
		for (SwingMotion.Profile p : SwingMotion.Profile.values()) {
			SwingMotion.Keys k = TwoHandMotion.keys(d, p);
			for (FpPose pose : new FpPose[] {k.wind(), k.hit(), k.follow()}) {
				float m = FpClamp.margin(h, RIG, pose, st, null, NEAR_ONLY);
				assertTrue(m > 0.05f, p + " key too close to the camera: " + m);
			}
		}
	}
}
