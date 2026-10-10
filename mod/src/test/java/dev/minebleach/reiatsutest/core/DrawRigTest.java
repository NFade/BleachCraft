package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.DrawRig;
import dev.minebleach.reiatsutest.core.obj.DrawRig.Rigid;
import org.junit.jupiter.api.Test;

/** The first person draw rig: arc slide, never a straight pull, continuous swing to the held pose. */
class DrawRigTest {
	// the Rukia numbers in model space: arc centre 3.03 m "above" the mouth, travel 0.7225 m
	private static final float CY = 0.5f + 3.0276f; // model Y of the arc centre (blade axis, grip at 0.5)
	private static final float CZ = 0.5f;
	private static final float R = 3.0276f;

	private static Rigid stow() {
		// 90 degrees about model Z plus a shift, an arbitrary but non trivial stow pose
		float[] q = DrawRig.axisAngle(0, 0, 1, (float) Math.toRadians(90));
		return new Rigid(q, new float[] {-0.3f, 0.1f, 0.0f});
	}

	private static DrawRig rig() {
		return new DrawRig(CY, CZ, R, 0.7225f * 1.04f, stow(), 0.55f);
	}

	private static void assertVec(float[] want, float[] got, float eps, String what) {
		for (int i = 0; i < 3; i++) {
			assertEquals(want[i], got[i], eps, what + "[" + i + "]");
		}
	}

	@Test
	void endsAreTheStowPoseAndTheHeldPose() {
		DrawRig r = rig();
		float[] v = {0.4f, 0.9f, 0.55f};
		assertVec(stow().apply(v), r.at(0f).apply(v), 1e-5f, "p=0 is the stow pose");
		assertVec(v, r.at(1f).apply(v), 1e-5f, "p=1 is the held pose");
		assertVec(stow().apply(v), r.at(-0.2f).apply(v), 1e-5f, "below 0 clamps");
		assertVec(v, r.at(1.5f).apply(v), 1e-5f, "above 1 clamps");
	}

	@Test
	void theSlideFollowsTheSoriArcNotAStraightLine() {
		DrawRig r = rig();
		// a point on the blade axis, 0.3 m above the grip, in the saya frame
		float[] tip = {0.5f, 0.5f + 0.7f, 0.5f};
		float[] z0 = stow().apply(tip);
		float[] z1 = r.at(0.2f).apply(tip);
		// distance from the arc centre (transformed into the saya frame) is preserved: the sword moves on a circle
		float[] saya = stow().apply(new float[] {0, CY, CZ});
		float d0 = dist(z0, saya);
		float d1 = dist(z1, saya);
		assertEquals(d0, d1, 1e-4f, "constant radius about the arc centre");
		// and a straight pull of the same length would NOT keep that distance
		float travel = r.slideTravel * (0.2f / 0.55f);
		float[] straight = {tip[0], tip[1] - travel, tip[2]};
		float[] zs = stow().apply(straight);
		assertTrue(Math.abs(dist(zs, saya) - d0) > 1e-3f, "a straight pull leaves the circle");
	}

	@Test
	void slideLengthAtTheEndOfTheSlidePhaseIsTheClearTravel() {
		DrawRig r = rig();
		float[] grip = r.origin;
		float[] a = r.at(0f).apply(grip);
		float[] b = r.at(r.slideEnd).apply(grip);
		// the grip is 2.9 m from the centre... chord of the arc angle clearAngle: 2 R sin(a/2) about the travel
		float chord = dist(a, b);
		assertEquals(r.slideTravel, chord, 0.002f);
	}

	@Test
	void theWholeMotionIsContinuous() {
		DrawRig r = rig();
		float[] v = {0.45f, 1.0f, 0.5f};
		float[] prev = r.at(0f).apply(v);
		float maxStep = 0f;
		for (int i = 1; i <= 400; i++) {
			float[] cur = r.at(i / 400f).apply(v);
			maxStep = Math.max(maxStep, dist(prev, cur));
			prev = cur;
		}
		assertTrue(maxStep < 0.06f, "no jump between samples, max step " + maxStep);
		// continuity at the phase boundary
		float[] before = r.at(r.slideEnd - 1e-4f).apply(v);
		float[] after = r.at(r.slideEnd + 1e-4f).apply(v);
		assertTrue(dist(before, after) < 1e-3f, "phase boundary");
	}

	@Test
	void rotationsStayNormalised() {
		DrawRig r = rig();
		for (int i = 0; i <= 100; i++) {
			float[] q = r.at(i / 100f).q();
			float n = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3];
			assertEquals(1f, n, 1e-4f);
		}
	}

	@Test
	void composeAndInverseAgree() {
		Rigid a = stow();
		Rigid b = new DrawRig(CY, CZ, R, 0.7f, a, 0.5f).arc(0.1f);
		float[] v = {0.3f, 0.2f, 0.9f};
		assertVec(a.apply(b.apply(v)), a.after(b).apply(v), 1e-5f, "after()");
	}

	private static DrawRig pulled() {
		return new DrawRig(CY, CZ, R, 0.7225f * 1.04f, stow(), 0.55f, 0.55f, 0.15f);
	}

	@Test
	void theScabbardIsPulledBackWhileTheBladeStaysOnTheSayaWall() {
		DrawRig r = pulled();
		float[] v = {0.5f, 0.9f, 0.5f};
		assertVec(stow().apply(v), r.sayaAt(0f).apply(v), 1e-5f, "saya starts at the stow pose");
		assertVec(stow().apply(v), r.at(0f).apply(v), 1e-5f, "sword starts at the stow pose");
		for (int i = 1; i <= 20; i++) {
			float p = r.slideEnd * i / 20f;
			// relative pose sword = saya after arc(travel): the blade rides the saya wall exactly as with a fixed saya
			float travelAngle = r.clearAngle() * (p / r.slideEnd);
			DrawRig.Rigid want = r.sayaAt(p).after(r.arc(travelAngle));
			assertVec(want.apply(v), r.at(p).apply(v), 1e-4f, "sword = saya o arc at p=" + p);
		}
		// the left hand's share: the saya moved by pull * travel, the sword by the rest
		float[] g0 = r.origin;
		float[] sword = r.at(r.slideEnd).apply(g0);
		float[] sword0 = stow().apply(g0);
		assertEquals(r.slideTravel * (1f - r.pull), dist(sword, sword0), 0.002f);
		float[] saya = r.sayaAt(r.slideEnd).apply(g0);
		assertEquals(r.slideTravel * r.pull, dist(saya, sword0), 0.002f);
	}

	@Test
	void theScabbardEndsAtTheCarryPoseAndStaysThere() {
		DrawRig r = pulled();
		float[] v = {0.5f, 0.8f, 0.5f};
		DrawRig.Rigid carry = stow().after(r.arc(-r.retract / R));
		assertVec(carry.apply(v), r.sayaAt(1f).apply(v), 1e-5f, "p=1");
		assertVec(carry.apply(v), r.sayaAt(3f).apply(v), 1e-5f, "above 1 clamps");
		float[] prev = r.sayaAt(0f).apply(v);
		for (int i = 1; i <= 400; i++) {
			float[] cur = r.sayaAt(i / 400f).apply(v);
			assertTrue(dist(prev, cur) < 0.05f, "saya moves smoothly");
			prev = cur;
		}
		// no pull and no retract: the scabbard never moves (the old rig)
		DrawRig fixed = rig();
		assertVec(stow().apply(v), fixed.sayaAt(0.3f).apply(v), 1e-6f, "fixed saya");
		assertVec(stow().apply(v), fixed.sayaAt(1f).apply(v), 1e-6f, "fixed saya, carry");
	}

	private static float dist(float[] a, float[] b) {
		float x = a[0] - b[0];
		float y = a[1] - b[1];
		float z = a[2] - b[2];
		return (float) Math.sqrt(x * x + y * y + z * z);
	}
}
