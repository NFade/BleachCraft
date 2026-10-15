package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The third person pose of one frame ({@link UryuTpPose}) against the shipped animation files. */
class UryuTpPoseTest {
	private static final double D = Math.PI / 180.0;

	private static UryuTpPose.Frame frame(String anim, double tick) {
		return UryuTestData.frame(anim, tick, false, 0.0, 0.0, 0.0, false);
	}

	private static UryuTpPose.Frame withWeight(UryuTpPose.Frame f, double w) {
		return new UryuTpPose.Frame(f.cur(), f.prev(), f.xfade(), w, f.mirror(), f.lookPitch(), f.time(), f.breath(), f.tremor());
	}

	@Test
	void aFadedOutPoseChangesNothing() {
		double[] v = {0.1, 0.2, 0.3};
		UryuTpPose.Frame f = withWeight(frame("draw", 10), 0.0);
		assertSame(v, UryuTpPose.rotation(f, "rightArm", v));
		assertSame(v, UryuTpPose.position(f, "rightArm", v));
		assertSame(v, UryuTpPose.rotation(new UryuTpPose.Frame(null, null, 1, 1, false, 0, 0, 0, false), "torso", v));
	}

	@Test
	void keyedPartsTakeTheAnimationValueAndUnkeyedPartsKeepTheIncomingOne() {
		UryuTpPose.Frame f = frame("idle", 5);
		double[] arm = UryuTpPose.rotation(f, "rightArm", new double[] {9, 9, 9});
		double[] keyed = UryuTestData.track("idle").rotation("rightArm", 5);
		assertArrayEquals(keyed, arm, 1e-12);
		// the loose left arm of the idle swings with the walk of the vanilla model
		double[] walk = {0.4, 0.0, -0.05};
		assertArrayEquals(walk, UryuTpPose.rotation(f, "leftArm", walk), 0.0);
		assertArrayEquals(walk, UryuTpPose.rotation(f, "rightLeg", walk), 0.0);
	}

	@Test
	void theHeadNodIsAddedToTheCameraPitch() {
		UryuTpPose.Frame f = frame("blut", 12);
		double nod = UryuTestData.track("blut").rotation("head", 12)[0];
		assertTrue(Math.abs(nod) > 5 * D, "the burst throws the head back");
		double[] out = UryuTpPose.rotation(f, "head", new double[] {0.3, 0.7, 0.0});
		assertEquals(0.3 + nod, out[0], 1e-12);
		assertEquals(0.7, out[1], 0.0, "the yaw of the camera is not touched");
	}

	@Test
	void theWeightBlendsTowardsTheIncomingValue() {
		UryuTpPose.Frame full = frame("draw", 20);
		double[] in = {0.2, -0.1, 0.05};
		double[] a = UryuTpPose.rotation(full, "rightArm", in);
		double[] h = UryuTpPose.rotation(withWeight(full, 0.5), "rightArm", in);
		for (int i = 0; i < 3; i++) {
			assertEquals((in[i] + a[i]) / 2.0, h[i], 1e-9);
		}
	}

	@Test
	void aCrossfadeBlendsTheTwoAnimations() {
		UryuTpPose.Layer cur = new UryuTpPose.Layer(UryuTestData.track("draw"), UryuAnims.meta("draw"), 20);
		UryuTpPose.Layer prev = new UryuTpPose.Layer(UryuTestData.track("idle"), UryuAnims.meta("idle"), 0);
		double[] in = {0, 0, 0};
		double[] c = UryuTpPose.rotation(new UryuTpPose.Frame(cur, null, 1, 1, false, 0, 0, 0, false), "rightArm", in);
		double[] p = UryuTpPose.rotation(new UryuTpPose.Frame(prev, null, 1, 1, false, 0, 0, 0, false), "rightArm", in);
		double[] m = UryuTpPose.rotation(new UryuTpPose.Frame(cur, prev, 0.5, 1, false, 0, 0, 0, false), "rightArm", in);
		for (int i = 0; i < 3; i++) {
			assertEquals((c[i] + p[i]) / 2.0, m[i], 1e-9);
		}
		assertArrayEquals(c, UryuTpPose.rotation(new UryuTpPose.Frame(cur, prev, 1.0, 1, false, 0, 0, 0, false), "rightArm", in), 1e-12);
		assertEquals(0.5, UryuTpPose.axis(new UryuTpPose.Frame(cur, prev, 0.5, 1, false, 0, 0, 0, false)), 1e-12, "the bow axis follows the blend");
	}

	@Test
	void anAimedPoseFollowsTheCameraPitchAndAFixedOneDoesNot() {
		double[] in = {0, 0, 0};
		double[] level = UryuTpPose.rotation(UryuTestData.frame("draw", 20, false, 0.0, 0.0, 0.0, false), "rightArm", in);
		double[] down = UryuTpPose.rotation(UryuTestData.frame("draw", 20, false, 30 * D, 0.0, 0.0, false), "rightArm", in);
		double[] dl = UryuPoseMath.armDirection(level);
		double[] dd = UryuPoseMath.armDirection(down);
		// turned about the x axis of the entity by the look pitch: x stays, the angle in the y/z plane changes by 30 degrees
		assertEquals(dl[0], dd[0], 1e-9);
		double angL = Math.atan2(dl[1], -dl[2]);
		double angD = Math.atan2(dd[1], -dd[2]);
		assertEquals(30 * D, angD - angL, 1e-9);
		// the vollstandig pose is fixed, the camera does not move the arms
		double[] a = UryuTpPose.rotation(UryuTestData.frame("vollstandig", 20, false, 0.0, 0.0, 0.0, false), "rightArm", in);
		double[] b = UryuTpPose.rotation(UryuTestData.frame("vollstandig", 20, false, 40 * D, 0.0, 0.0, false), "rightArm", in);
		assertArrayEquals(a, b, 0.0);
	}

	@Test
	void aLeftHanderGetsTheMirrorImage() {
		UryuTpPose.Frame r = UryuTestData.frame("draw", 14, false, 0.0, 0.0, 0.0, false);
		UryuTpPose.Frame l = UryuTestData.frame("draw", 14, true, 0.0, 0.0, 0.0, false);
		double[] in = {-0.3, 0.1, 0.05};
		double[] rightBowArm = UryuTpPose.rotation(r, "rightArm", UryuPoseMath.mirrorEuler(in));
		double[] leftBowArm = UryuTpPose.rotation(l, "leftArm", in);
		assertArrayEquals(UryuPoseMath.mirrorEuler(rightBowArm), leftBowArm, 1e-9);
		double[] rightDraw = UryuTpPose.rotation(r, "leftArm", in);
		double[] leftDraw = UryuTpPose.rotation(l, "rightArm", UryuPoseMath.mirrorEuler(in));
		assertArrayEquals(UryuPoseMath.mirrorEuler(rightDraw), leftDraw, 1e-9);
		// the torso turns the other way
		double[] tr = UryuTpPose.rotation(r, "torso", new double[3]);
		double[] tl = UryuTpPose.rotation(l, "torso", new double[3]);
		assertEquals(tr[0], tl[0], 1e-12);
		assertEquals(-tr[1], tl[1], 1e-12);
		// the bow arm the layer asks for is the right hander layout in both cases
		assertArrayEquals(UryuTpPose.bowArm(r), UryuTpPose.bowArm(l), 1e-9);
		// positions: the shoulder shift mirrors in x
		double[] pr = UryuTpPose.position(r, "rightArm", new double[] {-5, 2, 0});
		double[] pl = UryuTpPose.position(l, "leftArm", new double[] {5, 2, 0});
		assertEquals(-pr[0], pl[0], 1e-9);
		assertEquals(pr[1], pl[1], 1e-9);
		assertEquals(pr[2], pl[2], 1e-9);
	}

	@Test
	void shouldersFollowTheTurnedChest() {
		UryuTpPose.Frame f = frame("draw", 20);
		double[] v = {5, 2, 0};
		double[] p = UryuTpPose.position(f, "leftArm", v);
		assertNotNull(p);
		double shift = Math.abs(p[0] - v[0]) + Math.abs(p[1] - v[1]) + Math.abs(p[2] - v[2]);
		assertTrue(shift > 0.1 && shift < 8.0, "a draw pose moves the draw shoulder by a few pixels: " + shift);
		// half weight, half the shift
		double[] h = UryuTpPose.position(withWeight(f, 0.5), "leftArm", v);
		assertEquals(v[0] + (p[0] - v[0]) / 2.0, h[0], 1e-9);
		// a part no animation of the frame moves keeps its (same) value
		double[] head = {0, 0, 0};
		assertSame(head, UryuTpPose.position(frame("idle", 3), "head", head));
	}

	@Test
	void theBowAxisFollowsTheArmOnlyInTheAimedPoses() {
		assertEquals(0.0, UryuTpPose.axis(frame("idle", 0)), 0.0);
		assertEquals(1.0, UryuTpPose.axis(frame("draw", 10)), 0.0);
		assertEquals(0.5, UryuTpPose.axis(withWeight(frame("draw", 10), 0.5)), 1e-12);
		assertEquals(0.0, UryuTpPose.axis(new UryuTpPose.Frame(null, null, 1, 1, false, 0, 0, 0, false)), 0.0);
		assertEquals(0.0, UryuTpPose.axis(frame("seele", 3)), 0.0);
	}

	@Test
	void theBowArmOfTheIdleIsTheVanillaHeldItemArm() {
		double[] arm = UryuTpPose.bowArm(frame("idle", 0));
		assertEquals(UryuPoseMath.IDLE_ARM[0], arm[0], 0.02);
		// idle: the bow stands upright, so the correction is a small tilt (the 4 and 6 degrees of the pose), nothing like a turn of the bow
		TpMat4 c = UryuPoseMath.bowCorrection(arm, UryuTpPose.axis(frame("idle", 0)));
		double[] e = UryuPoseMath.euler(c);
		for (double v : e) {
			assertTrue(Math.abs(v) < 12 * D, "small: " + v);
		}
	}

	@Test
	void breathingAndTremorAreSmallAndSwitchable() {
		double t = 1.0 / (4.0 * 0.22);
		double[] base = UryuTpPose.rotation(UryuTestData.frame("idle", 0, false, 0.0, t, 0.0, true), "torso", new double[3]);
		double[] br = UryuTpPose.rotation(UryuTestData.frame("idle", 0, false, 0.0, t, 1.0, true), "torso", new double[3]);
		assertEquals(0.8 * D, br[0] - base[0], 1e-9, "0.8 degrees at the top of the breath");
		double[] half = UryuTpPose.rotation(UryuTestData.frame("idle", 0, false, 0.0, t, 0.5, true), "torso", new double[3]);
		assertEquals(0.4 * D, half[0] - base[0], 1e-9, "reduce motion halves it");
		double[] arm0 = UryuTpPose.rotation(UryuTestData.frame("idle", 0, false, 0.0, t, 0.0, true), "rightArm", new double[3]);
		double[] arm1 = UryuTpPose.rotation(UryuTestData.frame("idle", 0, false, 0.0, t, 1.0, true), "rightArm", new double[3]);
		assertEquals(1.2 * D, arm1[0] - arm0[0], 1e-9);
		// the draw trembles on the string arm only at the full draw, and only when the tremor is on
		double tt = 1.0 / (4.0 * 9.0);
		double[] still = UryuTpPose.rotation(UryuTestData.frame("draw", 20, false, 0.0, tt, 1.0, false), "leftArm", new double[3]);
		double[] shake = UryuTpPose.rotation(UryuTestData.frame("draw", 20, false, 0.0, tt, 1.0, true), "leftArm", new double[3]);
		assertEquals(0.6 * D, shake[0] - still[0], 1e-9);
		double[] early = UryuTpPose.rotation(UryuTestData.frame("draw", 10, false, 0.0, tt, 1.0, true), "leftArm", new double[3]);
		double[] earlyStill = UryuTpPose.rotation(UryuTestData.frame("draw", 10, false, 0.0, tt, 1.0, false), "leftArm", new double[3]);
		assertArrayEquals(earlyStill, early, 0.0);
		double[] bow = UryuTpPose.rotation(UryuTestData.frame("draw", 20, false, 0.0, tt, 1.0, true), "rightArm", new double[3]);
		double[] bowStill = UryuTpPose.rotation(UryuTestData.frame("draw", 20, false, 0.0, tt, 1.0, false), "rightArm", new double[3]);
		assertArrayEquals(bowStill, bow, 0.0, "the bow arm does not tremble in the basic draw");
		assertNull(UryuTpPose.micro("seele", "leftArm", 5, 0.1, 1.0, true));
	}

	@Test
	void theRaisedBowOfTheRainShakesOnlyWhileItIsUp() {
		assertNull(UryuTpPose.micro("licht_regen", "rightArm", 5, 0.1, 1.0, true));
		assertNotNull(UryuTpPose.micro("licht_regen", "rightArm", 30, 0.1, 1.0, true));
		assertNull(UryuTpPose.micro("licht_regen", "rightArm", 90, 0.1, 1.0, true));
		assertNull(UryuTpPose.micro("licht_regen", "rightArm", 30, 0.1, 1.0, false));
		assertNull(UryuTpPose.micro("licht_regen", "torso", 30, 0.1, 1.0, true));
		assertNotNull(UryuTpPose.micro("sternenstaub", "leftArm", 15, 0.1, 1.0, true));
		assertNull(UryuTpPose.micro("sternenstaub", "leftArm", 30, 0.1, 1.0, true));
	}
}
