package dev.minebleach.reiatsutest.core.combat;

/**
 * Rotation maths of the third person Uryu pose (pure, no Minecraft): Euler angles of a vanilla model part, the look pitch of the aimed
 * arms, the mirror of a left hander and the rotation that keeps the bow upright in a moving hand.
 *
 * <p>Space and order are the ones of {@link TpMat4}: the model space of the player model (x toward the left of the character, y down,
 * front is -z, radians), a part rotates by {@code Rz(roll) Ry(yaw) Rx(pitch)} ({@code ModelPart#rotate}); an arm hangs along its local +y,
 * pitch -90 degrees points it forward, and pitch above 0 swings it back. Euler triples are {@code {pitch, yaw, roll}}.
 *
 * <p>The bow ({@code UryuBowPose}): the display transform {@code uryu_cross_display.json} is solved for the idle arm of a held item
 * (vanilla {@code ArmPose.ITEM}, pitch -18 degrees): the bow stands upright, its arrow axis points forward. The item frame of vanilla is
 * {@code Arm * Rx(-90) Ry(180) * T}, then the display; a rotation {@code Corr} between the two puts the bow on any orientation {@code O}:
 * {@code Corr = C^-1 Arm^-1 O ArmIdle C} with {@code C = Rx(-90) Ry(180)} (solved in design/results/uryu_u4_u5.md for the drawn arm; for the arm of vanilla's
 * bow pull the unit test shows that the old constant gives the level bow, the same orientation this formula gives for an arm pointing level forward).
 */
public final class UryuPoseMath {
	/** Idle arm of a held item: vanilla {@code ArmPose.ITEM}, pitch -pi/10, no yaw, no roll. */
	public static final double[] IDLE_ARM = {-Math.PI / 10.0, 0.0, 0.0};

	private static final TpMat4 CHAIN = TpMat4.rotX(-Math.PI / 2.0).mul(TpMat4.rotY(Math.PI));
	/** Display rotation of the idle pose in degrees ({@code thirdperson_righthand} of {@code uryu_cross_display.json}, X Y Z of {@code rotationXYZ}). */
	public static final double[] IDLE_DISPLAY_DEG = {-118.0, 0.0, -180.0};
	/** The bow standing upright with its arrow axis level forward in the entity frame ({@code diag(1, -1, -1)}): the orientation of a bow aimed along a level arm. */
	private static final TpMat4 LEVEL = TpMat4.rotX(Math.PI);
	/**
	 * Turn about the x axis from the idle bow to the level bow: the display of the idle hold keeps the arrow axis about 10 degrees off the level (the arm is at pitch
	 * -18 degrees), an aimed bow has its arrow axis exactly along the arm, so the tilt goes away as the axis follows the arm.
	 */
	public static final double IDLE_TILT = idleTilt();

	private static double idleTilt() {
		double r = Math.PI / 180.0;
		TpMat4 idleWorld = arm(IDLE_ARM).mul(CHAIN).mul(TpMat4.rotXYZ(IDLE_DISPLAY_DEG[0] * r, IDLE_DISPLAY_DEG[1] * r, IDLE_DISPLAY_DEG[2] * r));
		TpMat4 t = LEVEL.mul(idleWorld.inverse());
		return Math.atan2(t.m[9], t.m[10]);
	}

	private UryuPoseMath() {
	}

	/** Rotation of a model part from {@code pitch, yaw, roll}. */
	public static TpMat4 arm(double[] pyr) {
		return TpMat4.rotZYX(pyr[2], pyr[1], pyr[0]);
	}

	/** {@code pitch, yaw, roll} of a rotation made by {@link #arm} (principal branch, |yaw| at most 90 degrees). */
	public static double[] euler(TpMat4 r) {
		double[] m = r.m;
		double sy = -m[8];
		double yaw = Math.asin(Math.max(-1.0, Math.min(1.0, sy)));
		double pitch;
		double roll;
		if (Math.abs(Math.cos(yaw)) > 1e-6) {
			pitch = Math.atan2(m[9], m[10]);
			roll = Math.atan2(m[4], m[0]);
		} else {
			pitch = Math.atan2(-m[6], m[5]);
			roll = 0.0;
		}
		return new double[] {pitch, yaw, roll};
	}

	/** The triple that makes the same rotation as {@code cand} and is closest to {@code prev} (keeps blends and frames continuous across the Euler branches). */
	public static double[] nearest(double[] prev, double[] cand) {
		double two = 2.0 * Math.PI;
		double[][] opts = {
			{cand[0], cand[1], cand[2]},
			{cand[0] + Math.PI, Math.PI - cand[1], cand[2] + Math.PI},
			{cand[0] - Math.PI, Math.PI - cand[1], cand[2] - Math.PI},
			{cand[0] + Math.PI, -Math.PI - cand[1], cand[2] + Math.PI},
			{cand[0] - Math.PI, -Math.PI - cand[1], cand[2] - Math.PI}};
		double[] best = null;
		double bestD = Double.MAX_VALUE;
		for (double[] o : opts) {
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					for (int c = -1; c <= 1; c++) {
						double[] t = {o[0] + two * a, o[1] + two * b, o[2] + two * c};
						if (Math.abs(t[1]) > Math.PI) {
							continue;
						}
						double d = Math.max(Math.abs(t[0] - prev[0]), Math.max(Math.abs(t[1] - prev[1]), Math.abs(t[2] - prev[2])));
						if (d < bestD - 1e-12) {
							bestD = d;
							best = t;
						}
					}
				}
			}
		}
		return best;
	}

	/** {@code a} weighted {@code 1 - k} plus {@code b} weighted {@code k}, with {@code b} first moved to the Euler branch of {@code a}. */
	public static double[] lerp(double[] a, double[] b, double k) {
		double[] bb = nearest(a, b);
		return new double[] {a[0] + (bb[0] - a[0]) * k, a[1] + (bb[1] - a[1]) * k, a[2] + (bb[2] - a[2]) * k};
	}

	/**
	 * The arm turned about the horizontal axis through the shoulders (the x axis of the entity) by the look pitch: an aimed arm follows the camera.
	 * {@code lookPitch} in radians, above 0 looks down (vanilla); the result keeps the Euler branch of the input.
	 */
	public static double[] aimed(double[] pyr, double lookPitch) {
		if (Math.abs(lookPitch) < 1e-9) {
			return pyr;
		}
		return nearest(pyr, euler(TpMat4.rotX(lookPitch).mul(arm(pyr))));
	}

	/** Direction an arm points to (its local +y turned by the arm rotation). */
	public static double[] armDirection(double[] pyr) {
		return arm(pyr).dir(0.0, 1.0, 0.0);
	}

	/**
	 * Orientation of the arrow axis of the bow in the entity frame, as a rotation from the idle axis (forward, level): {@code axis} 0 keeps the
	 * idle axis, 1 points it along the arm (the aimed poses), in between follows the blend of the two directions. The limbs stay in the vertical plane of the axis,
	 * the limb tip leans back when the axis points up.
	 */
	public static TpMat4 axisFrame(double[] armPyr, double axis) {
		if (axis <= 1e-9) {
			return TpMat4.identity();
		}
		double[] d = armDirection(armPyr);
		double[] f = {0.0, 0.0, -1.0};
		double x = f[0] + (d[0] - f[0]) * axis;
		double y = f[1] + (d[1] - f[1]) * axis;
		double z = f[2] + (d[2] - f[2]) * axis;
		double n = Math.sqrt(x * x + y * y + z * z);
		if (n < 1e-9) {
			return TpMat4.identity();
		}
		x /= n;
		y /= n;
		z /= n;
		// Rx(a) takes the forward axis to (0, sin a, -cos a), Ry(b) then turns it about the vertical: a = asin(y), b = atan2(-x, -z)
		double a = Math.asin(Math.max(-1.0, Math.min(1.0, y)));
		double b = Math.atan2(-x, -z);
		return TpMat4.rotY(b).mul(TpMat4.rotX(a));
	}

	/**
	 * The rotation to put between the item chain and the display transform so that the bow keeps its upright orientation (turned to the arrow axis of
	 * {@link #axisFrame}, level when {@code axis} is 1) while the arm is at {@code armPyr}. About the grip (the display translation of the third person entries is zero).
	 */
	public static TpMat4 bowCorrection(double[] armPyr, double axis) {
		// the bow in the world is O * (the idle bow): turned to the arrow axis, with the idle tilt of the arrow axis taken out in the same measure
		TpMat4 o = axisFrame(armPyr, axis).mul(TpMat4.rotX(IDLE_TILT * axis)).mul(arm(IDLE_ARM));
		return CHAIN.inverse().mul(arm(armPyr).inverse()).mul(o).mul(CHAIN);
	}

	/** {@code Mx R Mx}: the mirror image of a rotation across the plane x = 0 (the left hand version of a right hand pose). */
	public static TpMat4 mirror(TpMat4 r) {
		return r.mirrorX();
	}

	/** A left hand pose from a right hand one: the pitch stays, yaw and roll change sign. */
	public static double[] mirrorEuler(double[] pyr) {
		return new double[] {pyr[0], -pyr[1], -pyr[2]};
	}

	/** The part that a mirrored pose reads its data from ({@code rightArm} for {@code leftArm} and so on; the others are their own mirror). */
	public static String mirrorPart(String part) {
		return switch (part) {
			case "rightArm" -> "leftArm";
			case "leftArm" -> "rightArm";
			case "rightLeg" -> "leftLeg";
			case "leftLeg" -> "rightLeg";
			default -> part;
		};
	}
}
