package dev.minebleach.reiatsutest.core.combat;

/**
 * Geometry of the first person hands against the camera, pure maths (right hander layout, hand frame of the item renderer, see {@link FpRig}).
 * It reproduces what the client draws: the vanilla arm of an empty hand placed in the hand frame, then moved with the sword as one rigid
 * body ({@link FpPose#apply}), and the free off hand put on the hilt. It answers two questions: how far in front of the camera is the
 * nearest part of the hands and the blade (the near plane of the game camera is at 0.05 blocks: anything closer is cut off), and where
 * the fists are on the screen (vertical field of view 70 degrees).
 *
 * <p>Camera space is the hand frame moved by {@link #BASE} (the equip offset of the held item): the camera is at the origin looking along
 * -z. The right arm is drawn into the main hand frame, the left arm into the same frame mirrored ({@code (-0.56, -0.52, -0.72)}).
 */
public final class FpGeometry {
	/** Resting main hand frame in camera space (right hander). */
	public static final float[] BASE = {0.56f, -0.52f, -0.72f};
	/** Near clipping plane of the camera. */
	public static final float NEAR = 0.05f;
	/** Fist of the vanilla empty hand in the hand frame of the same side (right arm; the left arm mirrors x). */
	public static final float[] FIST = {0.134f, -0.018f, -0.316f};
	/** Held pose offset of the right forearm (manifest held.arm): the fist sits this far below the vanilla fist when the sword is drawn. */
	public static final float[] HELD_ARM = {0f, -0.12f, 0f};
	private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35.0));

	private static final float[][] RIGHT_ARM = armCorners(false);
	private static final float[][] LEFT_ARM = armCorners(true);

	private FpGeometry() {
	}

	// ------------------------------------------------------------------ the vanilla arm

	/**
	 * The eight corners of the vanilla empty arm in the hand frame of its own side (the chain of {@code HeldItemRenderer.renderArmHoldingItem}
	 * as {@code FirstPersonHand.renderVanillaArm} replays it, then the arm part: pivot 5 pixels aside, box 4 x 12 x 4 pixels, wide arm).
	 */
	public static float[][] armCorners(boolean leftArm) {
		double f = leftArm ? -1.0 : 1.0;
		double[] m = identity();
		m = mul(m, translate(f * 0.08, -0.08, 0.0));
		m = mul(m, rotY(f * 45.0));
		m = mul(m, translate(f * -1.0, 3.6, 3.5));
		m = mul(m, rotZ(f * 120.0));
		m = mul(m, rotX(200.0));
		m = mul(m, rotY(f * -135.0));
		m = mul(m, translate(f * 5.6, 0.0, 0.0));
		double px = leftArm ? 5.0 : -5.0;
		double x0 = leftArm ? -1.0 : -3.0;
		double x1 = x0 + 4.0;
		float[][] c = new float[8][];
		int n = 0;
		for (double x : new double[] {x0, x1}) {
			for (double y : new double[] {-2.0, 10.0}) {
				for (double z : new double[] {-2.0, 2.0}) {
					c[n++] = apply(m, (px + x) / 16.0, (2.0 + y) / 16.0, z / 16.0);
				}
			}
		}
		return c;
	}

	/** The corners of the right (or left) arm: {@code armCorners} cached. */
	public static float[][] corners(boolean leftArm) {
		float[][] src = leftArm ? LEFT_ARM : RIGHT_ARM;
		float[][] r = new float[8][];
		for (int i = 0; i < 8; i++) {
			r[i] = src[i].clone();
		}
		return r;
	}

	// ------------------------------------------------------------------ the hands of the stance

	/**
	 * Description of how the hands hold the sword for a pose: the pivot of the rotations, the right arm (offset from the vanilla fist,
	 * extra turn about the fist), the place and turn of the free left fist on the hilt.
	 *
	 * @param pivot pivot of the pose rotations, hand frame
	 * @param armOffset shift of the right arm from the vanilla empty hand (held offset plus {@code two.arm.shift}), hand frame
	 * @param armRot turn of the right arm about its fist (degrees, Rx*Ry*Rz), sword frame
	 * @param leftPoint the hilt point of the left fist at rest, hand frame (lift included)
	 * @param leftOffset shift of the left fist from the hilt point, turns with the sword
	 * @param leftArmRot turn of the left arm about its fist (degrees), sword frame: it turns with the sword on top of this
	 * @param left whether the left hand is on the hilt
	 */
	public record Hold(float[] pivot, float[] armOffset, float[] armRot, float[] leftPoint, float[] leftOffset, float[] leftArmRot, boolean left) {
		/** The two handed hold of the drawn sword with the shipped numbers of {@link TwoHandMotion} laid on {@code rig}. */
		public static Hold twoHanded(FpParams pr, FpRig rig) {
			float[] shift = pr.vec("two.arm.shift", TwoHandMotion.ARM_SHIFT);
			float[] off = {HELD_ARM[0] + shift[0], HELD_ARM[1] + shift[1], HELD_ARM[2] + shift[2]};
			return new Hold(rig.along(pr.num("two.pivot_s", TwoHandMotion.PIVOT_S)), off, pr.vec("two.arm.rot", TwoHandMotion.ARM_ROT),
					rig.along(pr.num("two.left.s", TwoHandMotion.LEFT_S)), pr.vec("two.left.offset", TwoHandMotion.LEFT_OFFSET),
					pr.vec("two.left.arm.rot", TwoHandMotion.LEFT_ARM_ROT), pr.num("two.left.enabled", 1f) > 0.5f);
		}
	}

	/** Absolute camera space point of a hand frame point of the main hand. */
	public static float[] cam(float[] p) {
		return new float[] {p[0] + BASE[0], p[1] + BASE[1], p[2] + BASE[2]};
	}

	/** The right arm corners (hand frame) for a hold and a pose: the pose moves them rigidly with the sword. */
	public static float[][] rightArm(Hold h, FpPose pose) {
		float[] fist = {FIST[0] + h.armOffset[0], FIST[1] + h.armOffset[1], FIST[2] + h.armOffset[2]};
		FpPose turn = FpPose.of(0f, 0f, 0f, h.armRot[0], h.armRot[1], h.armRot[2]);
		float[][] src = RIGHT_ARM;
		float[][] r = new float[8][];
		for (int i = 0; i < 8; i++) {
			float[] v = turn.applyDir(new float[] {src[i][0] - FIST[0], src[i][1] - FIST[1], src[i][2] - FIST[2]});
			float[] p = {fist[0] + v[0], fist[1] + v[1], fist[2] + v[2]};
			r[i] = pose.apply(h.pivot, p);
		}
		return r;
	}

	/** Centre of the right fist (hand frame) for a hold and a pose. */
	public static float[] rightFist(Hold h, FpPose pose) {
		return pose.apply(h.pivot, new float[] {FIST[0] + h.armOffset[0], FIST[1] + h.armOffset[1], FIST[2] + h.armOffset[2]});
	}

	/**
	 * Where the free left fist is, hand frame of the main hand: the hilt point carried by the pose plus the offset turned with the sword
	 * ({@link TwoHandMotion#leftFist}); {@code free} (0..1) keeps the stance place instead of riding with the sword, then {@code shift} is added.
	 */
	public static float[] leftFist(Hold h, FpPose pose, FpPose stanceBase, float free, float[] shift) {
		float[] on = pose.apply(h.pivot, h.leftPoint);
		float[] o = pose.applyDir(h.leftOffset);
		float[] t = {on[0] + o[0], on[1] + o[1], on[2] + o[2]};
		if (free > 0.001f) {
			float[] b = stanceBase.apply(h.pivot, h.leftPoint);
			float[] bo = stanceBase.applyDir(h.leftOffset);
			for (int k = 0; k < 3; k++) {
				t[k] += (b[k] + bo[k] - t[k]) * Math.min(1f, free);
			}
		}
		if (shift != null) {
			t[0] += shift[0];
			t[1] += shift[1];
			t[2] += shift[2];
		}
		return t;
	}

	/**
	 * The left arm corners (camera space, absolute) for a fist at {@code fist} (hand frame of the main hand): the vanilla left arm moved from
	 * its empty fist to the target and turned about the fist: first by the standing arm turn {@code turnArm} plus the gesture's
	 * {@code extraArm} (degrees Rx*Ry*Rz, sword frame), then by the rotation of the pose (the arm is rigid with the sword).
	 */
	public static float[][] leftArm(float[] fist, FpPose pose, FpPose stanceBase, float free, float[] turnArm, float[] extraArm) {
		float[] target = cam(fist);
		float[] rest = {-(BASE[0] + FIST[0]), BASE[1] + FIST[1], BASE[2] + FIST[2]};
		float[] d = {target[0] - rest[0], target[1] - rest[1], target[2] - rest[2]};
		float[] fl = {-FIST[0], FIST[1], FIST[2]};
		float[] qp = slerp(pose.quaternion(), stanceBase.quaternion(), Math.max(0f, Math.min(1f, free)));
		float[] qa = FpPose.of(0f, 0f, 0f, turnArm[0] + extraArm[0], turnArm[1] + extraArm[1], turnArm[2] + extraArm[2]).quaternion();
		float[] q = FpPose.mul(qp, qa);
		float[][] r = new float[8][];
		for (int i = 0; i < 8; i++) {
			float[] v = FpPose.rotate(q, new float[] {LEFT_ARM[i][0] - fl[0], LEFT_ARM[i][1] - fl[1], LEFT_ARM[i][2] - fl[2]});
			r[i] = new float[] {-BASE[0] + d[0] + fl[0] + v[0], BASE[1] + d[1] + fl[1] + v[1], BASE[2] + d[2] + fl[2] + v[2]};
		}
		return r;
	}

	/** Spherical interpolation of two unit quaternions (x, y, z, w) by the shortest way. */
	public static float[] slerp(float[] a, float[] b, float t) {
		if (t <= 0f) {
			return a.clone();
		}
		float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
		float[] bb = b;
		if (dot < 0f) {
			dot = -dot;
			bb = new float[] {-b[0], -b[1], -b[2], -b[3]};
		}
		float wa;
		float wb;
		if (dot > 0.9995f) {
			wa = 1f - t;
			wb = t;
		} else {
			double th = Math.acos(dot);
			double sn = Math.sin(th);
			wa = (float) (Math.sin((1.0 - t) * th) / sn);
			wb = (float) (Math.sin(t * th) / sn);
		}
		float x = a[0] * wa + bb[0] * wb;
		float y = a[1] * wa + bb[1] * wb;
		float z = a[2] * wa + bb[2] * wb;
		float w = a[3] * wa + bb[3] * wb;
		float l = (float) Math.sqrt(x * x + y * y + z * z + w * w);
		return new float[] {x / l, y / l, z / l, w / l};
	}

	// ------------------------------------------------------------------ the screen

	/** Normalised device coordinates (x, y in -1..1 over the screen) of a camera space point, or null when it is behind the near plane. */
	public static float[] ndc(float[] cam, float aspect) {
		if (cam[2] > -NEAR) {
			return null;
		}
		return new float[] {cam[0] / -cam[2] / (TAN_HALF_FOV * aspect), cam[1] / -cam[2] / TAN_HALF_FOV};
	}

	// ------------------------------------------------------------------ small matrix helpers (double, row major 4x4)

	private static double[] identity() {
		return new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	private static double[] translate(double x, double y, double z) {
		return new double[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
	}

	private static double[] rotX(double deg) {
		double c = Math.cos(Math.toRadians(deg));
		double s = Math.sin(Math.toRadians(deg));
		return new double[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
	}

	private static double[] rotY(double deg) {
		double c = Math.cos(Math.toRadians(deg));
		double s = Math.sin(Math.toRadians(deg));
		return new double[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
	}

	private static double[] rotZ(double deg) {
		double c = Math.cos(Math.toRadians(deg));
		double s = Math.sin(Math.toRadians(deg));
		return new double[] {c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	private static double[] mul(double[] a, double[] b) {
		double[] r = new double[16];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double s = 0;
				for (int k = 0; k < 4; k++) {
					s += a[i * 4 + k] * b[k * 4 + j];
				}
				r[i * 4 + j] = s;
			}
		}
		return r;
	}

	private static float[] apply(double[] m, double x, double y, double z) {
		return new float[] {(float) (m[0] * x + m[1] * y + m[2] * z + m[3]), (float) (m[4] * x + m[5] * y + m[6] * z + m[7]),
				(float) (m[8] * x + m[9] * y + m[10] * z + m[11])};
	}
}
