package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Rotation maths of the third person Uryu pose ({@link UryuPoseMath}). */
class UryuPoseMathTest {
	private static final double EPS = 1e-9;
	private static final double D = Math.PI / 180.0;

	private static void assertMat(TpMat4 a, TpMat4 b, double eps, String msg) {
		assertArrayEquals(a.m, b.m, eps, msg);
	}

	private static double[] deg(double p, double y, double r) {
		return new double[] {p * D, y * D, r * D};
	}

	@Test
	void eulerRoundTripsForTheAnglesOfThePoses() {
		double[][] cases = {deg(-18, 4, 6), deg(-90, -55, 0), deg(-170, 20, 10), deg(30, -80, -95), deg(0, 0, 0), deg(-100, 45, 130)};
		for (double[] c : cases) {
			double[] e = UryuPoseMath.euler(UryuPoseMath.arm(c));
			assertMat(UryuPoseMath.arm(c), UryuPoseMath.arm(e), 1e-9, "same rotation");
			assertEquals(c[1], e[1], 1e-9, "yaw is on the principal branch");
		}
	}

	@Test
	void nearestPicksTheBranchNextToThePreviousValue() {
		double[] prev = deg(-170, 10, 0);
		double[] cand = UryuPoseMath.euler(UryuPoseMath.arm(deg(190, 10, 0))); // the same rotation as -170 pitch
		double[] n = UryuPoseMath.nearest(prev, cand);
		assertMat(UryuPoseMath.arm(n), UryuPoseMath.arm(cand), 1e-9, "still the same rotation");
		assertEquals(prev[0], n[0], 1e-6);
		// a blend between two equal rotations on different branches does not swing
		double[] mid = UryuPoseMath.lerp(prev, cand, 0.5);
		assertArrayEquals(prev, mid, 1e-6);
	}

	@Test
	void lerpIsAPlainBlendWhenTheBranchesAgree() {
		double[] a = deg(-10, 0, 0);
		double[] b = deg(-90, 40, 20);
		double[] m = UryuPoseMath.lerp(a, b, 0.25);
		assertEquals(-30 * D, m[0], 1e-9);
		assertEquals(10 * D, m[1], 1e-9);
		assertEquals(5 * D, m[2], 1e-9);
		assertArrayEquals(a, UryuPoseMath.lerp(a, b, 0.0), 1e-9);
		assertArrayEquals(b, UryuPoseMath.lerp(a, b, 1.0), 1e-9);
	}

	@Test
	void anArmPointsAlongItsPitch() {
		// hanging
		assertArrayEquals(new double[] {0, 1, 0}, UryuPoseMath.armDirection(deg(0, 0, 0)), EPS);
		// pitch -90: forward (-z)
		assertArrayEquals(new double[] {0, 0, -1}, UryuPoseMath.armDirection(deg(-90, 0, 0)), EPS);
		// pitch -180: straight up (y is down in the model space)
		assertArrayEquals(new double[] {0, -1, 0}, UryuPoseMath.armDirection(deg(-180, 0, 0)), EPS);
	}

	@Test
	void theLookPitchTurnsTheAimedArmAboutTheShoulderLine() {
		double[] fwd = deg(-90, 0, 0);
		double[] down = UryuPoseMath.aimed(fwd, 30 * D);
		double[] d = UryuPoseMath.armDirection(down);
		assertEquals(Math.sin(30 * D), d[1], 1e-9, "looking down 30 degrees points the arm down (y is down)");
		assertEquals(-Math.cos(30 * D), d[2], 1e-9);
		assertEquals(0.0, d[0], 1e-9);
		double[] up = UryuPoseMath.aimed(fwd, -45 * D);
		assertEquals(-Math.sin(45 * D), UryuPoseMath.armDirection(up)[1], 1e-9);
		assertTrue(UryuPoseMath.aimed(fwd, 0.0) == fwd, "no look pitch, no change");
		// an arm with a yaw keeps its yaw (the turn is about the x axis of the entity)
		double[] yawed = UryuPoseMath.aimed(deg(-90, -40, 0), 20 * D);
		assertEquals(UryuPoseMath.armDirection(deg(-90, -40, 0))[0], UryuPoseMath.armDirection(yawed)[0], 1e-9);
	}

	@Test
	void theAxisFrameTakesTheForwardAxisToTheArm() {
		assertMat(TpMat4.identity(), UryuPoseMath.axisFrame(deg(-90, 20, 0), 0.0), EPS, "axis 0 keeps the level bow");
		double[] arm = deg(-120, -30, 0);
		double[] dir = UryuPoseMath.armDirection(arm);
		double[] f = UryuPoseMath.axisFrame(arm, 1.0).dir(0, 0, -1);
		assertArrayEquals(dir, f, 1e-9);
		// the half way blend lies between the two directions
		double[] h = UryuPoseMath.axisFrame(arm, 0.5).dir(0, 0, -1);
		double dot0 = h[0] * 0 + h[1] * 0 + h[2] * -1;
		double dot1 = h[0] * dir[0] + h[1] * dir[1] + h[2] * dir[2];
		assertTrue(dot0 > 0.5 && dot1 > 0.5);
	}

	@Test
	void theCorrectionIsNothingInTheIdleHoldAndKeepsTheBowOrientationElsewhere() {
		assertMat(TpMat4.identity(), UryuPoseMath.bowCorrection(UryuPoseMath.IDLE_ARM, 0.0), EPS, "idle arm, level bow: nothing to correct");
		TpMat4 chain = TpMat4.rotX(-Math.PI / 2.0).mul(TpMat4.rotY(Math.PI));
		double[][] arms = {deg(-18, 4, 6), deg(-90, -55, 3), deg(-150, -10, 0), deg(-110, -30, -20)};
		for (double[] arm : arms) {
			for (double axis : new double[] {0.0, 0.5, 1.0}) {
				TpMat4 corr = UryuPoseMath.bowCorrection(arm, axis);
				// the bow in the world: Arm * chain * Corr is the idle bow chain turned to the axis frame
				TpMat4 world = UryuPoseMath.arm(arm).mul(chain).mul(corr);
				TpMat4 want = UryuPoseMath.axisFrame(arm, axis).mul(TpMat4.rotX(UryuPoseMath.IDLE_TILT * axis)).mul(UryuPoseMath.arm(UryuPoseMath.IDLE_ARM)).mul(chain);
				assertMat(want, world, 1e-9, "arm " + java.util.Arrays.toString(arm) + " axis " + axis);
			}
		}
	}

	@Test
	void anAimedBowIsLevelWithItsArrowAxisAlongTheArm() {
		TpMat4 chain = TpMat4.rotX(-Math.PI / 2.0).mul(TpMat4.rotY(Math.PI));
		TpMat4 dsp = TpMat4.rotXYZ(-118 * D, 0, -180 * D);
		TpMat4 level = TpMat4.rotX(Math.PI);
		// the idle tilt is the 10 degrees the display of the idle hold keeps (about the x axis)
		assertEquals(-10.0, Math.toDegrees(UryuPoseMath.IDLE_TILT), 0.5);
		// a level arm, aimed: exactly the level bow
		double[] fwd = deg(-90, 0, 0);
		assertMat(level, UryuPoseMath.arm(fwd).mul(chain).mul(UryuPoseMath.bowCorrection(fwd, 1.0)).mul(dsp), 1e-9, "level bow along a level arm");
		// the idle hold has the tilt, and the correction of the idle arm leaves it
		assertMat(UryuPoseMath.arm(UryuPoseMath.IDLE_ARM).mul(chain).mul(dsp),
				UryuPoseMath.arm(UryuPoseMath.IDLE_ARM).mul(chain).mul(UryuPoseMath.bowCorrection(UryuPoseMath.IDLE_ARM, 0.0)).mul(dsp), 1e-9, "idle");
		// a raised arm: the arrow axis points along it, the bow stays upright (the limb plane is vertical: the bow's up axis has no x component)
		double[] up = deg(-140, 0, 0);
		TpMat4 w = UryuPoseMath.arm(up).mul(chain).mul(UryuPoseMath.bowCorrection(up, 1.0)).mul(dsp);
		assertArrayEquals(UryuPoseMath.armDirection(up), w.dir(0, 0, 1), 1e-9, "the arrow axis of the model (+z) along the arm");
		assertEquals(0.0, w.dir(0, 1, 0)[0], 1e-9, "no sideways lean of the limbs (the model's +y is the limb line)");
	}

	@Test
	void theFirstSolutionOfTheDrawnBowIsTheLevelBowToo() {
		// design/results/uryu_u4_u5.md: display {-180, 5.73, -180} against the idle {-118, 0, -180} for the vanilla BOW_AND_ARROW arm (pitch -90, yaw -0.1)
		TpMat4 chain = TpMat4.rotX(-Math.PI / 2.0).mul(TpMat4.rotY(Math.PI));
		TpMat4 idle = TpMat4.rotXYZ(-118 * D, 0, -180 * D);
		TpMat4 old = TpMat4.rotXYZ(-180 * D, 5.73 * D, -180 * D).mul(idle.inverse());
		TpMat4 world = UryuPoseMath.arm(new double[] {-Math.PI / 2.0, -0.1, 0.0}).mul(chain).mul(old).mul(idle);
		assertMat(TpMat4.rotX(Math.PI), world, 2e-3, "the old constant puts the bow level, arrow axis forward, whatever the 0.1 yaw of vanilla's arm");
	}

	@Test
	void mirrorsAreTheirOwnInverse() {
		TpMat4 r = UryuPoseMath.arm(deg(-90, -55, 12));
		assertMat(r, UryuPoseMath.mirror(UryuPoseMath.mirror(r)), EPS, "twice is the identity");
		double[] e = deg(-90, -55, 12);
		assertArrayEquals(new double[] {e[0], 55 * D, -12 * D}, UryuPoseMath.mirrorEuler(e), EPS);
		// the mirror image of a rotation is the rotation of the mirrored euler angles
		assertMat(UryuPoseMath.mirror(UryuPoseMath.arm(e)), UryuPoseMath.arm(UryuPoseMath.mirrorEuler(e)), 1e-9, "mirrored arm");
		assertEquals("leftArm", UryuPoseMath.mirrorPart("rightArm"));
		assertEquals("rightArm", UryuPoseMath.mirrorPart("leftArm"));
		assertEquals("leftLeg", UryuPoseMath.mirrorPart("rightLeg"));
		assertEquals("torso", UryuPoseMath.mirrorPart("torso"));
		assertEquals("head", UryuPoseMath.mirrorPart("head"));
	}
}
