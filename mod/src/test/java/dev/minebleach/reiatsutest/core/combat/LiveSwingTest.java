package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The first person strike from the live bones of the third person animation (round 6): conventions of the kinematics and the pose composition. */
class LiveSwingTest {
	private static final float EPS = 1e-3f;
	private static final float[] Z3 = new float[3];

	private static LiveSwing.Bones arm(boolean left, float pitchDeg, float yawDeg, float rollDeg) {
		return new LiveSwing.Bones(Z3, Z3, Z3, new float[] {left ? 5f : -5f, 2f, 0f},
				new float[] {(float) Math.toRadians(pitchDeg), (float) Math.toRadians(yawDeg), (float) Math.toRadians(rollDeg)}, 0f, 0f, Z3);
	}

	private static LiveSwing.Bones withTorso(LiveSwing.Bones b, float yawDeg) {
		return new LiveSwing.Bones(new float[] {0f, (float) Math.toRadians(yawDeg), 0f}, Z3, Z3, b.armPivot(), b.armRot(), b.bendAxis(), b.bend(), b.itemRot());
	}

	private static float[] rotate(float[] q, float x, float y, float z) {
		return FpPose.rotate(q, new float[] {x, y, z});
	}

	@Test
	void aHangingArmHasItsHandLowAndBesideTheBody() {
		LiveSwing.Frame r = LiveSwing.frame(arm(false, 0, 0, 0), 1f, true);
		LiveSwing.Frame l = LiveSwing.frame(arm(true, 0, 0, 0), 1f, true);
		assertTrue(r.fist()[0] > 0.2f && r.fist()[1] < -0.6f, "right hand: right of and below the shoulder in the camera frame");
		assertEquals(-r.fist()[0], l.fist()[0], EPS);
		assertEquals(r.fist()[1], l.fist()[1], EPS);
	}

	@Test
	void raisingTheArmForwardMovesTheHandForwardAndUpAndTurnsTheSwordAboutTheRightAxis() {
		LiveSwing.Frame rest = LiveSwing.frame(arm(false, 0, 0, 0), 1f, true);
		LiveSwing.Frame fwd = LiveSwing.frame(arm(false, -90, 0, 0), 1f, true);
		assertTrue(fwd.fist()[2] < -0.5f, "forward is -z in the camera frame");
		assertTrue(fwd.fist()[1] > rest.fist()[1] + 0.4f, "the hand comes up");
		LiveSwing.Delta d = LiveSwing.delta(rest, fwd, LiveSwing.Gains.DEFAULT);
		// a vector that pointed down (the arm) now points forward: a turn of +90 degrees about +x of the camera frame
		float[] v = rotate(d.q(), 0f, -1f, 0f);
		assertArrayEquals(new float[] {0f, 0f, -1f}, v, EPS);
		assertTrue(d.move()[2] < -0.4f && d.move()[1] > 0.4f);
	}

	@Test
	void yawingTheRaisedArmSweepsTheHandAcrossTheView() {
		LiveSwing.Frame fwd = LiveSwing.frame(arm(false, -90, 0, 0), 1f, true);
		LiveSwing.Frame pos = LiveSwing.frame(arm(false, -90, 40, 0), 1f, true);
		LiveSwing.Frame neg = LiveSwing.frame(arm(false, -90, -40, 0), 1f, true);
		// Ry(+yaw) turns the forward vector (0, 0, -1) to (-sin, 0, -cos) in model space, model x is the entity's left: the hand goes to the RIGHT in the camera
		assertTrue(pos.fist()[0] > fwd.fist()[0] + 0.1f);
		assertTrue(neg.fist()[0] < fwd.fist()[0] - 0.1f);
	}

	@Test
	void theTorsoTwistSwingsTheHandsAndTheSword() {
		LiveSwing.Bones b = arm(false, -90, 0, 0);
		LiveSwing.Frame still = LiveSwing.frame(b, 1f, true);
		LiveSwing.Frame twist = LiveSwing.frame(withTorso(b, 45f), 1f, true);
		assertTrue(Math.abs(twist.fist()[0] - still.fist()[0]) > 0.15f, "a torso twist moves the hand sideways");
		// switched off: the torso is ignored
		assertArrayEquals(still.fist(), LiveSwing.frame(withTorso(b, 45f), 1f, false).fist(), EPS);
	}

	@Test
	void aBodyLeanKeepsTheHandWhereItIsAgainstTheHead() {
		LiveSwing.Bones b = arm(false, -70, 10, 0);
		LiveSwing.Bones leaned = new LiveSwing.Bones(Z3, new float[] {(float) Math.toRadians(-25), 0f, 0f}, Z3, b.armPivot(), b.armRot(), 0f, 0f, Z3);
		LiveSwing.Frame still = LiveSwing.frame(b, 1f, true);
		LiveSwing.Frame lean = LiveSwing.frame(leaned, 1f, true);
		// a rigid turn about the hips does not change the distance between the head and the hand
		float d0 = (float) Math.sqrt(still.fist()[0] * still.fist()[0] + still.fist()[1] * still.fist()[1] + still.fist()[2] * still.fist()[2]);
		float d1 = (float) Math.sqrt(lean.fist()[0] * lean.fist()[0] + lean.fist()[1] * lean.fist()[1] + lean.fist()[2] * lean.fist()[2]);
		assertEquals(d0, d1, 1e-4f);
	}

	@Test
	void theBendFoldsTheForearmAndStaysAFiniteFrame() {
		LiveSwing.Bones b = new LiveSwing.Bones(Z3, Z3, Z3, new float[] {-5f, 2f, 0f}, new float[] {(float) Math.toRadians(-90), 0f, 0f}, 0f,
				(float) Math.toRadians(60), Z3);
		LiveSwing.Frame straight = LiveSwing.frame(arm(false, -90, 0, 0), 1f, true);
		LiveSwing.Frame bent = LiveSwing.frame(b, 1f, true);
		assertTrue(Math.abs(bent.fist()[1] - straight.fist()[1]) > 0.02f || Math.abs(bent.fist()[2] - straight.fist()[2]) > 0.02f);
		assertArrayEquals(straight.fist(), LiveSwing.frame(b, 0f, true).fist(), EPS, "bend gain 0 switches the bend off");
	}

	@Test
	void noChangeIsNoDeltaAndTheStanceStaysExactlyWhereItIs() {
		LiveSwing.Frame f = LiveSwing.frame(arm(false, -50, 20, 10), 1f, true);
		LiveSwing.Delta d = LiveSwing.delta(f, f, LiveSwing.Gains.DEFAULT);
		assertEquals(0f, d.magnitude(), 1e-3f);
		FpPose stance = FpPose.of(-0.47f, 0.105f, 0.06f, -36f, 0f, -8f);
		FpPose p = LiveSwing.pose(stance, d, false);
		assertArrayEquals(stance.pos(), p.pos(), 1e-5f);
		// the Euler angles come back as the same rotation (they may differ as numbers only at a gimbal)
		assertArrayEquals(stance.quaternion(), sameHemisphere(p.quaternion(), stance.quaternion()), 1e-4f);
	}

	private static float[] sameHemisphere(float[] q, float[] ref) {
		float dot = q[0] * ref[0] + q[1] * ref[1] + q[2] * ref[2] + q[3] * ref[3];
		return dot < 0f ? new float[] {-q[0], -q[1], -q[2], -q[3]} : q;
	}

	@Test
	void eulerExtractionRoundTripsTheFpPoseConvention() {
		Random rnd = new Random(7);
		for (int i = 0; i < 400; i++) {
			FpPose p = FpPose.of(0f, 0f, 0f, (rnd.nextFloat() - 0.5f) * 340f, (rnd.nextFloat() - 0.5f) * 170f, (rnd.nextFloat() - 0.5f) * 340f);
			float[] e = LiveSwing.eulerXyzDeg(p.quaternion());
			FpPose back = FpPose.of(0f, 0f, 0f, e[0], e[1], e[2]);
			assertArrayEquals(p.quaternion(), sameHemisphere(back.quaternion(), p.quaternion()), 2e-4f, "euler " + p.rot()[0] + " " + p.rot()[1] + " " + p.rot()[2]);
		}
		// at the gimbal (yaw 90) the rotation is still the same
		FpPose g = FpPose.of(0f, 0f, 0f, 30f, 90f, 10f);
		float[] e = LiveSwing.eulerXyzDeg(g.quaternion());
		assertArrayEquals(g.quaternion(), sameHemisphere(FpPose.of(0f, 0f, 0f, e[0], e[1], e[2]).quaternion(), g.quaternion()), 2e-3f);
	}

	@Test
	void theDeltaIsAWorldTurnOfTheStanceAboutThePivot() {
		LiveSwing.Frame rest = LiveSwing.frame(arm(false, -60, 0, 0), 1f, true);
		LiveSwing.Frame cur = LiveSwing.frame(arm(false, -60, 50, 0), 1f, true);
		LiveSwing.Delta d = LiveSwing.delta(rest, cur, LiveSwing.Gains.DEFAULT);
		FpPose stance = FpPose.of(0f, 0f, 0f, -36f, 0f, -8f);
		FpPose p = LiveSwing.pose(stance, d, false);
		float[] expect = FpPose.mul(d.q(), stance.quaternion());
		assertArrayEquals(expect, sameHemisphere(p.quaternion(), expect), 2e-4f);
	}

	@Test
	void aLeftHanderGetsTheMirrorImage() {
		LiveSwing.Frame rest = LiveSwing.frame(arm(false, -60, 0, 0), 1f, true);
		LiveSwing.Frame cur = LiveSwing.frame(arm(false, -75, 35, 8), 1f, true);
		LiveSwing.Delta right = LiveSwing.delta(rest, cur, LiveSwing.Gains.DEFAULT);
		// the same animation on the left arm (the mirrored one: yaw and roll negated) must give the right hander numbers back after the mirroring
		LiveSwing.Frame lrest = LiveSwing.frame(arm(true, -60, 0, 0), 1f, true);
		LiveSwing.Frame lcur = LiveSwing.frame(arm(true, -75, -35, -8), 1f, true);
		LiveSwing.Delta left = LiveSwing.delta(lrest, lcur, LiveSwing.Gains.DEFAULT);
		FpPose stance = FpPose.of(-0.47f, 0.105f, 0.06f, -36f, 0f, -8f);
		FpPose a = LiveSwing.pose(stance, right, false);
		FpPose b = LiveSwing.pose(stance, left, true);
		assertArrayEquals(a.pos(), b.pos(), 2e-3f);
		assertArrayEquals(a.quaternion(), sameHemisphere(b.quaternion(), a.quaternion()), 2e-3f);
	}

	@Test
	void gainsFlipAxesAndTheLimitsAreSoft() {
		LiveSwing.Frame rest = LiveSwing.frame(arm(false, 0, 0, 0), 1f, true);
		LiveSwing.Frame cur = LiveSwing.frame(arm(false, -90, 0, 0), 1f, true);
		LiveSwing.Gains flip = new LiveSwing.Gains(new float[] {1f, 1f, -1f}, new float[] {-1f, 1f, 1f}, 150f, 0.55f, 0f, 1f, true);
		LiveSwing.Delta d0 = LiveSwing.delta(rest, cur, LiveSwing.Gains.DEFAULT);
		LiveSwing.Delta d1 = LiveSwing.delta(rest, cur, flip);
		assertEquals(-d0.move()[2], d1.move()[2], 5e-3f, "z gain -1 flips the forward shift (the soft limit is odd)");
		float[] v = rotate(d1.q(), 0f, -1f, 0f);
		assertEquals(0f, v[0], EPS);
		assertEquals(1f, v[2], EPS, "x rotation flipped: the arm now turns backwards");
		// a huge turn is capped below the maximum, a huge shift below the limit
		LiveSwing.Frame far = new LiveSwing.Frame(new float[] {3f, 0f, 0f}, FpPose.axisAngle(0f, 1f, 0f, (float) Math.toRadians(175)));
		LiveSwing.Frame zero = new LiveSwing.Frame(new float[3], new float[] {0f, 0f, 0f, 1f});
		LiveSwing.Delta big = LiveSwing.delta(zero, far, LiveSwing.Gains.DEFAULT);
		assertTrue(big.magnitude() < 150f + 0.56f * 100f + 1f);
		assertTrue(Math.abs(big.move()[0]) <= 0.55f);
	}

	@Test
	void theLookPitchAdjustmentOfBetterCombatIsTakenOutOfTheBones() {
		float p = (float) Math.toRadians(35);
		LiveSwing.Bones plain = arm(false, -50, 20, 5);
		// what the attack layer does to the bones (third person pass): body -0.75 p, arm +0.25 p on the pitch
		LiveSwing.Bones adjusted = new LiveSwing.Bones(plain.torsoRot(), new float[] {-0.75f * p, 0f, 0f}, plain.torsoPos(), plain.armPivot(),
				new float[] {plain.armRot()[0] + 0.25f * p, plain.armRot()[1], plain.armRot()[2]}, 0f, 0f, plain.itemRot());
		LiveSwing.Bones fixed = LiveSwing.compensate(adjusted, p, 1f);
		assertArrayEquals(new float[3], fixed.bodyRot(), 1e-6f);
		assertArrayEquals(plain.armRot(), fixed.armRot(), 1e-6f);
		assertArrayEquals(LiveSwing.frame(plain, 1f, true).fist(), LiveSwing.frame(fixed, 1f, true).fist(), 1e-5f);
		// the adjusted frame really differs, and 0 switches the correction off
		assertTrue(Math.abs(LiveSwing.frame(adjusted, 1f, true).fist()[1] - LiveSwing.frame(plain, 1f, true).fist()[1]) > 0.02f);
		assertTrue(LiveSwing.compensate(adjusted, p, 0f) == adjusted);
	}

	@Test
	void theBlendOfTwoPosesTakesTheShortWayEvenAcrossAnEulerWrap() {
		FpPose a = FpPose.of(0f, 0f, 0f, 0f, 0f, 179f);
		FpPose b = FpPose.of(0.2f, 0f, 0f, 0f, 0f, -179f);
		FpPose m = LiveSwing.blend(a, b, 0.5f);
		// the rotation between them is 2 degrees: halfway is a half turn about z, not zero (a plain Euler blend would give 0)
		float[] q = m.quaternion();
		assertEquals(1f, Math.abs(q[2]), 1e-3f);
		assertEquals(0.1f, m.pos()[0], 1e-5f);
		assertArrayEquals(a.quaternion(), sameHemisphere(LiveSwing.blend(a, b, 0f).quaternion(), a.quaternion()), 1e-5f);
		assertArrayEquals(b.quaternion(), sameHemisphere(LiveSwing.blend(a, b, 1f).quaternion(), b.quaternion()), 1e-5f);
	}

	@Test
	void theRunWaitsForTheAnimationAndGivesUpWithoutIt() {
		LiveSwing.Run run = new LiveSwing.Run();
		assertEquals(0, run.step(false, 0.02f, 0.02f, 0.12f, 0.08f, 2f));
		assertEquals(0, run.step(false, 0.10f, 0.08f, 0.12f, 0.08f, 2f));
		assertEquals(2, run.step(false, 0.14f, 0.04f, 0.12f, 0.08f, 2f), "never seen after the gate: fall back to the profile");
		LiveSwing.Run r2 = new LiveSwing.Run();
		assertEquals(0, r2.step(true, 0.05f, 0.05f, 0.12f, 0.08f, 2f));
		assertEquals(0, r2.step(false, 0.30f, 0.05f, 0.12f, 0.08f, 2f));
		assertEquals(1, r2.step(false, 0.40f, 0.10f, 0.12f, 0.08f, 2f), "quiet for the tail: over");
		assertEquals(1, r2.step(true, 0.45f, 0.05f, 0.12f, 0.08f, 2f), "over stays over");
		LiveSwing.Run r3 = new LiveSwing.Run();
		assertEquals(1, r3.step(true, 2.5f, 0.05f, 0.12f, 0.08f, 2f), "the hard maximum");
	}
}
