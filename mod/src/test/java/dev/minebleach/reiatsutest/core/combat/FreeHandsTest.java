package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The empty hands of the Byakuya bankai (round 6): the idle pose, the turn of the vanilla arm, the gestures against the screen. */
class FreeHandsTest {
	private static final FpParams P = FpParams.DEFAULTS;
	private static final float[] ASPECTS = {16f / 9f, 4f / 3f};

	/** Camera space corners of the arm of a side after the frame {@code f} (right hander layout: the right arm for {@code left} false). */
	private static float[][] corners(boolean left, FreeHands.Frame f) {
		float[] base = left ? new float[] {-FpGeometry.BASE[0], FpGeometry.BASE[1], FpGeometry.BASE[2]} : FpGeometry.BASE;
		float[] fist = {base[0] + (left ? -1f : 1f) * FpGeometry.FIST[0], base[1] + FpGeometry.FIST[1], base[2] + FpGeometry.FIST[2]};
		float[][] src = FpGeometry.corners(left);
		float[][] out = new float[8][];
		for (int i = 0; i < 8; i++) {
			float[] v = {src[i][0] + base[0] - fist[0], src[i][1] + base[1] - fist[1], src[i][2] + base[2] - fist[2]};
			float[] r = FpPose.rotate(f.q(), v);
			out[i] = new float[] {fist[0] + f.move()[0] + r[0], fist[1] + f.move()[1] + r[1], fist[2] + f.move()[2] + r[2]};
		}
		return out;
	}

	/** Corners at the shoulder end of the box (the first of every pair along y in the corner order of {@link FpGeometry#corners}). */
	private static final int[] REAR = {0, 1, 4, 5};

	private static int stumpCorners(float[][] c, float aspect) {
		int n = 0;
		for (int i : REAR) {
			float[] ndc = FpGeometry.ndc(c[i], aspect);
			if (ndc != null && Math.abs(ndc[0]) < 0.98f && Math.abs(ndc[1]) < 0.98f) {
				n++;
			}
		}
		return n;
	}

	private static float nearest(float[][] c) {
		float m = Float.MAX_VALUE;
		for (float[] p : c) {
			m = Math.min(m, -p[2]);
		}
		return m;
	}

	private static float[] fistCam(boolean left, FreeHands.Frame f) {
		float sx = left ? -1f : 1f;
		return new float[] {sx * FreeHands.REST_FIST[0] + f.move()[0], FreeHands.REST_FIST[1] + f.move()[1], FreeHands.REST_FIST[2] + f.move()[2]};
	}

	@Test
	void theTurnTakesTheVanillaArmToTheAskedForearmAndPalm() {
		for (boolean left : new boolean[] {false, true}) {
			float s = left ? -1f : 1f;
			float[] dir = FreeHands.unit(new float[] {s * -0.4f, 0.5f, -0.8f});
			float[] palm = {0f, 1f, 0f};
			float[] q = FreeHands.quat(left, dir, palm);
			float[] d0 = {s * FreeHands.ARM_DIR[0], FreeHands.ARM_DIR[1], FreeHands.ARM_DIR[2]};
			assertArrayEquals(dir, FpPose.rotate(q, d0), 1e-4f, "forearm");
			float[] n0 = {s * FreeHands.ARM_PALM[0], FreeHands.ARM_PALM[1], FreeHands.ARM_PALM[2]};
			float[] n = FpPose.rotate(q, n0);
			float[] want = FreeHands.unit(FreeHands.perp(palm, dir));
			assertArrayEquals(want, n, 1e-4f, "palm");
			assertEquals(0f, FreeHands.dot(n, dir), 1e-4f, "the palm is square to the forearm");
		}
	}

	@Test
	void theLeftTurnIsTheMirrorOfTheRightOne() {
		float[] dir = {-0.4f, 0.5f, -0.8f};
		float[] palm = {-0.3f, 1f, 0.1f};
		float[] qr = FreeHands.quat(false, dir, palm);
		float[] ql = FreeHands.quat(true, new float[] {-dir[0], dir[1], dir[2]}, new float[] {-palm[0], palm[1], palm[2]});
		assertArrayEquals(FreeHands.mirrorQuat(qr), sameHemisphere(ql, FreeHands.mirrorQuat(qr)), 1e-4f);
	}

	private static float[] sameHemisphere(float[] q, float[] ref) {
		float dot = q[0] * ref[0] + q[1] * ref[1] + q[2] * ref[2] + q[3] * ref[3];
		return dot < 0f ? new float[] {-q[0], -q[1], -q[2], -q[3]} : q;
	}

	@Test
	void theVanillaArmConstantsMatchTheArmBoxOfTheHandCode() {
		float[][] c = FpGeometry.corners(false);
		// shoulder end (centre of the first face) to the hand end: the arm axis of the box
		float[] top = new float[3];
		float[] bottom = new float[3];
		for (int i : REAR) {
			for (int a = 0; a < 3; a++) {
				top[a] += c[i][a] / 4f;
			}
		}
		for (int i : new int[] {2, 3, 6, 7}) {
			for (int a = 0; a < 3; a++) {
				bottom[a] += c[i][a] / 4f;
			}
		}
		float[] axis = FreeHands.unit(new float[] {bottom[0] - top[0], bottom[1] - top[1], bottom[2] - top[2]});
		assertTrue(FreeHands.dot(axis, FreeHands.ARM_DIR) > 0.995f, "the constant arm direction is the box axis: " + FreeHands.dot(axis, FreeHands.ARM_DIR));
		assertEquals(FpGeometry.BASE[0] + FpGeometry.FIST[0], FreeHands.REST_FIST[0], 1e-3f);
		assertEquals(FpGeometry.BASE[1] + FpGeometry.FIST[1], FreeHands.REST_FIST[1], 1e-3f);
		assertEquals(FpGeometry.BASE[2] + FpGeometry.FIST[2], FreeHands.REST_FIST[2], 1e-3f);
	}

	@Test
	void theIdleHandsAreInThePictureAndTheirShoulderEndsAreNot() {
		for (boolean left : new boolean[] {false, true}) {
			FreeHands.Frame f = FreeHands.frame(P, left, null, 0f, 0f, 1f);
			float[][] c = corners(left, f);
			for (float aspect : ASPECTS) {
				assertEquals(0, stumpCorners(c, aspect), "no stump of the shoulder end, left=" + left + " aspect " + aspect);
			}
			assertTrue(nearest(c) > 0.10f, "nothing closer than the near plane margin: " + nearest(c));
			float[] fist = fistCam(left, f);
			float[] ndc = FpGeometry.ndc(fist, 16f / 9f);
			assertTrue(ndc != null && Math.abs(ndc[0]) < 0.9f && ndc[1] > -1.0f && ndc[1] < 0.2f, "the fist is in the lower part of the picture, ndc " + (ndc == null ? "null" : ndc[0] + " " + ndc[1]));
		}
		// the right hand is raised in front of the body, the left one low
		float[] r = fistCam(false, FreeHands.frame(P, false, null, 0f, 0f, 1f));
		float[] l = fistCam(true, FreeHands.frame(P, true, null, 0f, 0f, 1f));
		assertTrue(r[1] > l[1] + 0.05f, "the right hand is higher than the left one (r8: the right idle is 0.12 lower, it was 0.2 higher)");
		assertEquals(FreeHands.IDLE_R[0], r[0], 1e-4f);
		assertEquals(FreeHands.IDLE_L[1], l[1], 1e-4f);
	}

	@Test
	void theRightPalmFacesDownAndTheLeftOneTowardsTheBody() {
		float[] n = FpPose.rotate(FreeHands.idleQuat(P, false), FreeHands.ARM_PALM);
		assertTrue(n[1] < -0.5f, "palm down: " + n[1]);
		float[] nl = FpPose.rotate(FreeHands.idleQuat(P, true), new float[] {-FreeHands.ARM_PALM[0], FreeHands.ARM_PALM[1], FreeHands.ARM_PALM[2]});
		assertTrue(nl[0] > 0.3f, "the left palm faces towards the middle (to the right of the left hand): " + nl[0]);
	}

	@Test
	void presenceBringsTheHandsInFromBelowTheEdge() {
		for (boolean left : new boolean[] {false, true}) {
			FreeHands.Frame hidden = FreeHands.frame(P, left, null, 0f, 0f, 0f);
			FreeHands.Frame shown = FreeHands.frame(P, left, null, 0f, 0f, 1f);
			assertTrue(hidden.move()[1] < shown.move()[1] - 0.8f, "starts below the picture");
			assertTrue(stumpCorners(corners(left, hidden), 16f / 9f) == 0);
			// halfway the hand is already on its way up
			FreeHands.Frame mid = FreeHands.frame(P, left, null, 0f, 0f, 0.5f);
			assertTrue(mid.move()[1] > hidden.move()[1] && mid.move()[1] < shown.move()[1]);
		}
		assertEquals(0f, FreeHands.stepPresence(0.7f, false, 0.05f, 0.45f), 1e-6f);
		assertEquals(0.1f, FreeHands.stepPresence(0f, true, 0.045f, 0.45f), 1e-4f);
		assertEquals(1f, FreeHands.stepPresence(0.99f, true, 0.1f, 0.45f), 1e-6f);
	}

	@Test
	void theBreathIsTinyAndSilentWhenAGestureDrivesTheHand() {
		float maxMove = 0f;
		float maxRot = 0f;
		for (float t = 0f; t < 20f; t += 0.05f) {
			FreeHands.Frame still = FreeHands.frame(P, false, null, t, 0f, 1f);
			FreeHands.Frame b = FreeHands.frame(P, false, null, t, 1f, 1f);
			for (int i = 0; i < 3; i++) {
				maxMove = Math.max(maxMove, Math.abs(b.move()[i] - still.move()[i]));
			}
			float[] dq = FpPose.mul(b.q(), LiveSwing.conj(still.q()));
			maxRot = Math.max(maxRot, (float) Math.toDegrees(2.0 * Math.acos(Math.min(1f, Math.abs(dq[3])))));
		}
		assertTrue(maxMove > 0.002f && maxMove < 0.015f, "breath moves the hand a few millimetres: " + maxMove);
		assertTrue(maxRot > 0.3f && maxRot < 4f, "and turns it a degree or two: " + maxRot);
	}

	@Test
	void aGestureRowMovesAndTurnsTheHandFromItsIdle() {
		FreeHands.Frame idle = FreeHands.frame(P, false, null, 0f, 0f, 1f);
		FreeHands.Frame moved = FreeHands.frame(P, false, new float[] {0.1f, 0.2f, -0.3f, 0f, 90f, 0f}, 0f, 0f, 1f);
		assertEquals(idle.move()[0] + 0.1f, moved.move()[0], 1e-5f);
		assertEquals(idle.move()[1] + 0.2f, moved.move()[1], 1e-5f);
		assertEquals(idle.move()[2] - 0.3f, moved.move()[2], 1e-5f);
		float[] dq = FpPose.mul(moved.q(), LiveSwing.conj(idle.q()));
		assertEquals(90f, (float) Math.toDegrees(2.0 * Math.acos(Math.abs(dq[3]))), 0.1f, "a rotation vector of 90 degrees about y");
		assertEquals(1f, Math.abs(dq[1]) / (float) Math.sqrt(dq[0] * dq[0] + dq[1] * dq[1] + dq[2] * dq[2]), 1e-4f);
	}

	// ------------------------------------------------------------------ the gestures of the empty hands

	private static final String[] FREE_GESTURES = {CastGestures.BYAKUYA_BANKAI, "byakuya.bankai.scatter", "byakuya.bankai.hakuteiken",
			"byakuya.bankai.senkei"};

	@Test
	void theEmptyHandGesturesKeepBothHandsInThePictureWithoutAStump() {
		for (String id : FREE_GESTURES) {
			CastGesture g = CastGestures.defaults().get(id);
			assertTrue(g != null && g.freeHands(), id);
			int frames = 0;
			int stumps = 0;
			for (float t = 0f; t <= g.totalSeconds(); t += 0.02f) {
				CastGesture.Sample s = g.sampleReal(t);
				for (boolean left : new boolean[] {false, true}) {
					FreeHands.Frame f = FreeHands.frame(P, left, left ? s.l() : s.r(), t, 0f, 1f);
					float[][] c = corners(left, f);
					frames++;
					assertTrue(nearest(c) > 0.06f, id + " nothing at the near plane, t=" + t + " left=" + left + " nearest " + nearest(c));
					float[] ndc = FpGeometry.ndc(fistCam(left, f), 16f / 9f);
					assertTrue(ndc != null && Math.abs(ndc[0]) < 1.0f && ndc[1] > -1.1f && ndc[1] < 0.5f,
							id + " fist in the picture, t=" + t + " left=" + left + " ndc " + (ndc == null ? "null" : ndc[0] + " " + ndc[1]));
					for (float aspect : ASPECTS) {
						if (stumpCorners(c, aspect) > 0) {
							stumps++;
							break;
						}
					}
				}
			}
			assertTrue(stumps <= frames * 0.16f, id + " a stump of the shoulder end shows in " + stumps + " of " + frames + " frames");
		}
	}

	@Test
	void everyEmptyHandGestureStartsAndEndsOnTheIdle() {
		for (String id : FREE_GESTURES) {
			CastGesture g = CastGestures.defaults().get(id);
			for (float t : new float[] {0f, g.totalSeconds() + 0.01f}) {
				CastGesture.Sample s = g.sampleReal(t);
				for (int i = 0; i < 6; i++) {
					assertEquals(0f, s.r()[i], 1e-5f, id + " R " + i + " t=" + t);
					assertEquals(0f, s.l()[i], 1e-5f, id + " L " + i + " t=" + t);
				}
			}
		}
	}

	@Test
	void theRotationVectorOfTheRowsStaysAwayFromAHalfTurn() {
		// a rotation vector near 180 degrees flips its axis when interpolated: keep every row clear of it
		for (String id : FREE_GESTURES) {
			CastGesture g = CastGestures.defaults().get(id);
			for (char ch : new char[] {'R', 'L'}) {
				for (CastGesture.Key k : g.keys(ch)) {
					float a = (float) Math.sqrt(k.v()[3] * k.v()[3] + k.v()[4] * k.v()[4] + k.v()[5] * k.v()[5]);
					assertTrue(a < 168f, id + " " + ch + " at " + k.t() + " turns " + a);
				}
			}
		}
	}

	@Test
	void theFourRequestedGesturesHaveTheRequestedMeaning() {
		// release: both palms up in front, then parted to the sides
		CastGesture rel = CastGestures.defaults().get(CastGestures.BYAKUYA_BANKAI);
		FreeHands.Frame r09 = FreeHands.frame(P, false, rel.sampleReal(0.9f).r(), 0.9f, 0f, 1f);
		FreeHands.Frame l09 = FreeHands.frame(P, true, rel.sampleReal(0.9f).l(), 0.9f, 0f, 1f);
		assertTrue(FpPose.rotate(r09.q(), FreeHands.ARM_PALM)[1] > 0.5f, "release: the right palm is up");
		assertTrue(FpPose.rotate(l09.q(), new float[] {-FreeHands.ARM_PALM[0], FreeHands.ARM_PALM[1], FreeHands.ARM_PALM[2]})[1] > 0.5f, "release: the left palm is up");
		float gap1 = fistCam(false, r09)[0] - fistCam(true, l09)[0];
		FreeHands.Frame r23 = FreeHands.frame(P, false, rel.sampleReal(2.3f).r(), 2.3f, 0f, 1f);
		FreeHands.Frame l23 = FreeHands.frame(P, true, rel.sampleReal(2.3f).l(), 2.3f, 0f, 1f);
		assertTrue(fistCam(false, r23)[0] - fistCam(true, l23)[0] > gap1 + 0.5f, "release: the hands part to the sides");
		// scatter: the right hand reaches forward with the palm forward, then it opens
		CastGesture sc = CastGestures.defaults().get("byakuya.bankai.scatter");
		FreeHands.Frame aim = FreeHands.frame(P, false, sc.sampleReal(1.5f).r(), 1.5f, 0f, 1f);
		float[] n = FpPose.rotate(aim.q(), FreeHands.ARM_PALM);
		assertTrue(n[2] < -0.6f, "scatter: the palm looks forward (away from the player) when the hand reaches to the aim: " + n[2]);
		assertTrue(fistCam(false, aim)[2] < FreeHands.IDLE_R[2] - 0.2f, "scatter: the hand goes forward");
		FreeHands.Frame open = FreeHands.frame(P, false, sc.sampleReal(1.95f).r(), 1.95f, 0f, 1f);
		assertTrue(FpPose.rotate(open.q(), FreeHands.ARM_PALM)[1] > 0.5f, "scatter: then it opens, the palm turns up");
		// hakuteiken: the right hand forward with the palm up, the left hand comes to it
		CastGesture hk = CastGestures.defaults().get("byakuya.bankai.hakuteiken");
		FreeHands.Frame hr = FreeHands.frame(P, false, hk.sampleReal(1.0f).r(), 1.0f, 0f, 1f);
		FreeHands.Frame hl = FreeHands.frame(P, true, hk.sampleReal(1.4f).l(), 1.4f, 0f, 1f);
		assertTrue(FpPose.rotate(hr.q(), FreeHands.ARM_PALM)[1] > 0.5f && fistCam(false, hr)[2] < -0.95f, "hakuteiken: the right hand forward palm up");
		FreeHands.Frame hr14 = FreeHands.frame(P, false, hk.sampleReal(1.4f).r(), 1.4f, 0f, 1f);
		float dx = fistCam(false, hr14)[0] - fistCam(true, hl)[0];
		assertTrue(dx < 0.35f, "hakuteiken: the left hand has come to the right one: " + dx);
		// senkei: both palms forward
		CastGesture se = CastGestures.defaults().get("byakuya.bankai.senkei");
		FreeHands.Frame sr = FreeHands.frame(P, false, se.sampleReal(1.4f).r(), 1.4f, 0f, 1f);
		FreeHands.Frame sl = FreeHands.frame(P, true, se.sampleReal(1.4f).l(), 1.4f, 0f, 1f);
		assertTrue(FpPose.rotate(sr.q(), FreeHands.ARM_PALM)[2] < -0.6f, "senkei: right palm forward");
		assertTrue(FpPose.rotate(sl.q(), new float[] {-FreeHands.ARM_PALM[0], FreeHands.ARM_PALM[1], FreeHands.ARM_PALM[2]})[2] < -0.6f, "senkei: left palm forward");
	}
}
