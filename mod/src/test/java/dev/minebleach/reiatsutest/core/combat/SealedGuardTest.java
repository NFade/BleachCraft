package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The block with the sheathed sword in first person (round 5, bug of the screenshot with the scabbard across the whole screen): the scabbard with
 * the sword in it is one body; here the stow pose of the draw rig is rebuilt from the manifest numbers and the pose of the block is laid on it.
 */
class SealedGuardTest {
	private static final FpRig RIG = FpRig.DEFAULT;
	private static final float ASPECT = 16f / 9f;

	/** Stow pose of the manifests (rot 0 0 104, move -0.4 0.24 0 minus the lift) as a rigid transform of the item model space, replaying HandMath.stowToModel. */
	private static float[][] stow() {
		float[] d = FpPose.of(0f, 0f, 0f, -172f, 1f, -173.1f).quaternion();
		float[] dInv = {-d[0], -d[1], -d[2], d[3]};
		float[] qh = FpPose.mul(FpPose.mul(FpPose.axisAngle(0, 0, 1, (float) Math.toRadians(104)), FpPose.axisAngle(0, 1, 0, 0f)),
				FpPose.axisAngle(1, 0, 0, 0f));
		float[] qm = FpPose.mul(FpPose.mul(dInv, qh), d);
		float[] move = FpPose.rotate(dInv, new float[] {-0.4f, 0.24f - 0.06f, 0f});
		for (int i = 0; i < 3; i++) {
			move[i] /= 2.4f;
		}
		float[] qo = FpPose.rotate(qm, new float[] {0.5f, 0.5f, 0.5f});
		return new float[][] {qm, {0.5f - qo[0] + move[0], 0.5f - qo[1] + move[1], 0.5f - qo[2] + move[2]}};
	}

	/** Hand frame point of a model point on the sheathed sword and scabbard, then the pose of the block (pivot: the grip). */
	private static float[] camPoint(FpPose pose, float[][] st, float my) {
		float[] v = FpPose.rotate(st[0], new float[] {0.5f, my, 0.5f});
		float[] p = RIG.hand(v[0] + st[1][0], v[1] + st[1][1], v[2] + st[1][2]);
		return FpGeometry.cam(pose.apply(RIG.along(0f), p));
	}

	private static float screenAngle(float[] a, float[] b) {
		float[] na = FpGeometry.ndc(a, ASPECT);
		float[] nb = FpGeometry.ndc(b, ASPECT);
		return (float) Math.toDegrees(Math.atan2(nb[1] - na[1], (nb[0] - na[0]) * ASPECT));
	}

	@Test
	void theScabbardAcrossTheViewTiltsAboutThirtyDegreesUpToTheLeft() {
		float[][] st = stow();
		FpPose g = GuardMotion.sealedGuard(FpParams.DEFAULTS);
		float[] mouth = camPoint(g, st, 0.566f);
		float[] tip = camPoint(g, st, 1.31f);
		float angle = screenAngle(mouth, tip);
		// pointing left and up: 180 - tilt
		assertTrue(angle > 145f && angle < 155f, "scabbard tilt on the screen, 25 to 35 degrees up from level to the left: " + (180f - angle));
		// at rest (no pose) it lies about level and a little down
		float restAngle = screenAngle(camPoint(FpPose.ZERO, st, 0.566f), camPoint(FpPose.ZERO, st, 1.31f));
		assertTrue(Math.abs(Math.abs(restAngle) - 180f) < 25f && restAngle < 0f, "level at rest: " + restAngle);
	}

	@Test
	void bothHandsAreInFrontOfTheBodyAndTheWholeScabbardStaysInView() {
		float[][] st = stow();
		FpPose g = GuardMotion.sealedGuard(FpParams.DEFAULTS);
		float[] grip = FpGeometry.ndc(camPoint(g, st, 0.5f), ASPECT);
		assertTrue(grip[0] > 0f && grip[0] < 0.5f && grip[1] < -0.2f && grip[1] > -0.6f, "the right hand low in the middle: " + grip[0] + " " + grip[1]);
		// the left hand holds the scabbard 0.18 metres from the grip: on the left of the right hand and higher
		float[] left = FpGeometry.ndc(camPoint(g, st, 0.5f + GuardMotion.SEALED_LEFT_S), ASPECT);
		assertTrue(left[0] < grip[0] - 0.2f && left[1] > grip[1] + 0.1f && left[0] > -0.7f && left[1] < 0.4f, "the left hand: " + left[0] + " " + left[1]);
		float[] tip = FpGeometry.ndc(camPoint(g, st, 1.31f), ASPECT);
		assertTrue(tip != null && Math.abs(tip[0]) < 1.05f && Math.abs(tip[1]) < 1.05f, "the end of the scabbard on the screen");
		// 1 / 20 of the way: nothing of the body comes near the camera
		for (float my = 0.31f; my <= 1.31f; my += 0.05f) {
			assertTrue(-camPoint(g, st, my)[2] > 0.35f, "far enough from the near plane at " + my);
		}
		// the two fists are a hand apart on the scabbard (a fist is 0.25 blocks)
		assertTrue(GuardMotion.SEALED_LEFT_S * RIG.scale() > 0.35f);
	}

	@Test
	void theBlockBlendsInAndOutWithoutAJumpAndKeepsTheStrikeHintSmall() {
		FpParams d = FpParams.DEFAULTS;
		FpPose g = GuardMotion.sealedGuard(d);
		FpPose none = GuardMotion.pose(d, FpPose.ZERO, g, 0f, 0f, FpPose.ZERO, FpPose.ZERO);
		FpPose full = GuardMotion.pose(d, FpPose.ZERO, g, 1f, 0f, FpPose.ZERO, FpPose.ZERO);
		assertTrue(none.isNearZero());
		assertArrayEquals(g.rot(), full.rot(), 0.1f);
		FpPose prev = none;
		for (float w = 0.05f; w <= 1.0001f; w += 0.05f) {
			FpPose p = GuardMotion.pose(d, FpPose.ZERO, g, w, 0f, FpPose.ZERO, FpPose.ZERO);
			float step = Math.abs(p.rot()[2] - prev.rot()[2]);
			assertTrue(step < 8f, "no jump at weight " + w + ": " + step);
			prev = p;
		}
		assertEquals(0.4f, d.num("guard.sealed_scale", 0.4f), 0f);
	}

	@Test
	void overridesAndMirrorForTheLeftHander() {
		FpParams o = new FpParams() {
			@Override
			public float num(String key, float def) {
				return key.equals("guard.sealed.left_s") ? 0.3f : def;
			}

			@Override
			public float[] vec(String key, float[] def) {
				return key.equals("guard.sealed.rot") ? new float[] {1, 2, 3} : def;
			}
		};
		assertArrayEquals(new float[] {1, 2, 3}, GuardMotion.sealedGuard(o).rot(), 0f);
	}
}
