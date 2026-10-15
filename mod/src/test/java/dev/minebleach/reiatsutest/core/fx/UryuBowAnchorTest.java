package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The anchors of the bow (storyboard 2, "Anchors used"), the cue tables of the forming and the draw, and the spark spacing of the arrow. */
class UryuBowAnchorTest {
	private static final double EPS = 1e-9;

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	@Test
	void yawConventionIsMinecraft() {
		double[] f = UryuBowAnchor.forward(0);
		assertEquals(0.0, f[0], EPS);
		assertEquals(1.0, f[2], EPS);
		double[] r = UryuBowAnchor.right(0);
		assertEquals(-1.0, r[0], EPS, "facing +z the right hand is at -x");
		double[] f90 = UryuBowAnchor.forward(90);
		assertEquals(-1.0, f90[0], EPS, "yaw 90 looks along -x");
		assertEquals(0.0, dot(UryuBowAnchor.forward(37), UryuBowAnchor.right(37)), EPS);
	}

	@Test
	void idleGripIsTheStoryboardOffset() {
		double[] g = UryuBowAnchor.thirdGrip(new double[] {10, 64, -5}, 0, new double[] {0, 0, 1}, false, 1);
		assertEquals(10 - 0.35, g[0], EPS);
		assertEquals(64 + 1.10, g[1], EPS);
		assertEquals(-5 + 0.45, g[2], EPS);
		double[] left = UryuBowAnchor.thirdGrip(new double[] {10, 64, -5}, 0, new double[] {0, 0, 1}, false, -1);
		assertEquals(10 + 0.35, left[0], EPS, "the left hand mirrors the side offset");
		assertEquals(g[1], left[1], EPS);
		assertEquals(g[2], left[2], EPS);
	}

	@Test
	void drawingGripLiesAlongTheAimFromTheShoulder() {
		double[] aim = {0, 0, 1};
		double[] g = UryuBowAnchor.thirdGrip(new double[] {0, 0, 0}, 0, aim, true, 1);
		assertEquals(-UryuBowAnchor.SHOULDER_SIDE, g[0], EPS);
		assertEquals(UryuBowAnchor.SHOULDER_UP, g[1], EPS);
		assertEquals(UryuBowAnchor.ARM, g[2], EPS);
		double[] up = UryuBowAnchor.thirdGrip(new double[] {0, 0, 0}, 0, new double[] {0, 1, 0}, true, 1);
		assertEquals(UryuBowAnchor.SHOULDER_UP + UryuBowAnchor.ARM, up[1], EPS);
	}

	@Test
	void firstPersonGripIsInCameraSpace() {
		double[] cam = {1, 70, 2};
		double[] g = UryuBowAnchor.firstGrip(cam, new double[] {1, 0, 0}, new double[] {0, 1, 0}, new double[] {0, 0, 1}, 1);
		assertEquals(1 + 0.30, g[0], EPS);
		assertEquals(70 - 0.28, g[1], EPS);
		assertEquals(2 + 0.55, g[2], EPS);
	}

	@Test
	void nockMovesBackWithTheDrawAndTheTipsAreHalfTheBowAway() {
		double[] grip = {0, 1, 0};
		double[] aim = {0, 0, 1};
		double[] n0 = UryuBowAnchor.nock(grip, aim, 0, 1.4);
		double[] n1 = UryuBowAnchor.nock(grip, aim, 1, 1.4);
		assertEquals(-UryuDrawLight.nockDistance(0, 1.4), n0[2], EPS);
		assertTrue(n1[2] < n0[2], "full draw pulls further back");
		assertEquals(0.0, n1[0], EPS);
		double[] up = UryuBowAnchor.tip(grip, new double[] {0, 1, 0}, 1, 1.4);
		double[] dn = UryuBowAnchor.tip(grip, new double[] {0, 1, 0}, -1, 1.4);
		assertEquals(1 + 0.55 * 1.4, up[1], EPS);
		assertEquals(1 - 0.55 * 1.4, dn[1], EPS);
	}

	@Test
	void theFirstPersonNockStaysInFrontOfTheCamera() {
		double[] cam = {0, 0, 0};
		double[] fwd = {0, 0, 1};
		double[] grip = {0.30, -0.28, 0.55};
		double[] n = UryuBowAnchor.firstNock(cam, fwd, grip, fwd, 1.0, UryuDrawLight.SCALE_FIRST);
		assertTrue(n[2] >= UryuBowAnchor.FP_NOCK_MIN_FWD - EPS, "nock z " + n[2]);
		double[] plain = UryuBowAnchor.nock(grip, fwd, 1.0, UryuDrawLight.SCALE_FIRST);
		assertTrue(plain[2] < UryuBowAnchor.FP_NOCK_MIN_FWD, "the unclamped nock would be too close (z " + plain[2] + ")");
		double[] idle = UryuBowAnchor.firstNock(cam, fwd, grip, fwd, 0.0, 1.0);
		assertEquals(grip[2] - UryuDrawLight.nockDistance(0.0, 1.0), idle[2], EPS, "a short pull is not clamped");
	}

	@Test
	void towardsMovesAlongTheLine() {
		double[] p = UryuBowAnchor.towards(new double[] {0, 0, 0}, new double[] {0, 10, 0}, 2.5);
		assertEquals(2.5, p[1], EPS);
		double[] same = UryuBowAnchor.towards(new double[] {1, 1, 1}, new double[] {1, 1, 1}, 3);
		assertEquals(1.0, same[0], EPS);
	}

	// ------------------------------------------------------------------ cue tables

	@Test
	void formingCuesAreTheStoryboardRowsInOrder() {
		assertEquals(5, UryuBowForm.CUES.size());
		double prev = -1;
		for (UryuBowForm.Cue c : UryuBowForm.CUES) {
			assertTrue(c.t() > prev, "time order");
			prev = c.t();
			assertTrue(c.id().matches("[a-z_]+(\\.[a-z_]+)+"), c.id());
			assertTrue(c.pitch() >= 0.5 && c.pitch() <= 2.0, c.id() + " pitch " + c.pitch());
			assertTrue(c.volume() > 0 && c.volume() <= 1.0, c.id() + " volume " + c.volume());
		}
		assertEquals("block.amethyst_block.resonate", UryuBowForm.CUES.get(0).id());
		assertEquals(1.6, UryuBowForm.CUES.get(0).pitch(), 0);
		assertEquals(UryuBowForm.T_STRING, UryuBowForm.CUES.get(4).t(), 0);
		assertEquals("entity.arrow.shoot", UryuBowForm.CUES.get(4).id());
		assertEquals(0.25, UryuBowForm.REVERSE_CUE.volume(), 0);
		assertTrue(UryuBowForm.CUES.get(3).t() >= UryuBowForm.T_SOLID);
	}

	@Test
	void drawSoundsAndFovHook() {
		assertEquals(1.3, UryuDrawLight.HALF_PITCH, 0);
		assertEquals(1.7, UryuDrawLight.FULL_PITCH, 0);
		assertEquals(0.0, UryuDrawLight.fovTarget(0.99, false), 0);
		assertEquals(-1.5, UryuDrawLight.fovTarget(1.0, false), 0);
		assertEquals(0.0, UryuDrawLight.fovTarget(1.0, true), 0, "reduce motion: no FOV change");
		double v = 0;
		for (int i = 0; i < 100; i++) {
			v = UryuDrawLight.fovStep(v, -1.5, 0.01);
		}
		assertEquals(-1.5, v, 1e-9);
		double half = UryuDrawLight.fovStep(0, -1.5, UryuDrawLight.FOV_TIME / 2);
		assertEquals(-0.75, half, 1e-9, "linear ramp: half the time, half the angle");
		assertEquals(0.0, UryuDrawLight.fovStep(-0.2, 0.0, 1.0), 0, "returns and stops at the target");
	}

	@Test
	void arrowCuesFollowTheTable() {
		java.util.List<UryuBowForm.Cue> m = UryuArrowLook.muzzleCues(1.0);
		assertEquals("entity.arrow.shoot", m.get(0).id());
		assertEquals(2.0, m.get(0).pitch(), 1e-9, "1.6 + 0.4 at full power");
		assertEquals(0.5, m.get(0).volume(), 0);
		assertEquals("block.amethyst_block.chime", m.get(1).id());
		assertEquals(2, UryuArrowLook.hitCues(0, true).size());
		assertEquals(3, UryuArrowLook.hitCues(3, true).size(), "Focus 3 adds the glass break");
		assertEquals("block.amethyst_block.break", UryuArrowLook.hitCues(3, true).get(2).id());
		assertEquals(1, UryuArrowLook.hitCues(3, false).size(), "a block hit is one quiet tick");
		for (int lvl = 0; lvl <= 3; lvl++) {
			for (UryuBowForm.Cue c : UryuArrowLook.hitCues(lvl, true)) {
				assertTrue(c.pitch() <= 2.0 && c.volume() <= 1.0);
			}
		}
	}

	@Test
	void sparksAreSpacedByDistanceWithACarry() {
		// 5 blocks in one step at 2 blocks spacing: sparks at 2 and 4 blocks, 1 block carried
		double[] r = UryuArrowLook.sparkFractions(0.0, 5.0, 2.0);
		assertEquals(3, r.length);
		assertEquals(0.4, r[0], 1e-9);
		assertEquals(0.8, r[1], 1e-9);
		assertEquals(1.0, r[2], 1e-9);
		// the carry shifts the next sparks: 1 block carried, 1 block step: one spark at the end
		double[] c = UryuArrowLook.sparkFractions(1.0, 1.0, 2.0);
		assertEquals(2, c.length);
		assertEquals(1.0, c[0], 1e-9);
		assertEquals(0.0, c[1], 1e-9);
		// a short step makes none and grows the carry
		double[] none = UryuArrowLook.sparkFractions(0.3, 0.5, 2.0);
		assertEquals(1, none.length);
		assertEquals(0.8, none[0], 1e-9);
		// spacing 0 (no sparks): the carry just grows
		assertEquals(1, UryuArrowLook.sparkFractions(0.0, 3.0, 0.0).length);
		// over a long flight the number of sparks is the distance over the spacing
		double carry = 0;
		int n = 0;
		for (int i = 0; i < 100; i++) {
			double[] s = UryuArrowLook.sparkFractions(carry, 2.2, 2.0);
			n += s.length - 1;
			carry = s[s.length - 1];
		}
		assertEquals(110, n, 1, "220 blocks at 2.0 spacing");
	}
}
