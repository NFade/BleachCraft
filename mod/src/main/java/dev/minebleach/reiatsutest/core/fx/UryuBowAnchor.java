package dev.minebleach.reiatsutest.core.fx;

/**
 * Where the bow of Uryu is in the world, for the glow layers of the forming, the draw and the Focus feedback (URYU_VFX_STORYBOARD 2, "Anchors
 * used"): the grip (the bow hand), the nock (the draw point of the string, pulled back along the aim) and the two tips (the bow stands upright).
 * Pure vectors as {@code double[3]}, no Minecraft types; the client feeds it the entity (feet, body yaw, aim) or the camera.
 *
 * <p>Third person: idle, the hand is {@code feet + R(bodyYaw) * (0.35 right, 1.10 up, 0.45 forward)} (the bow stands upright at the side, the arrow
 * axis forward); while the bow is drawn the arm is raised along the aim from the shoulder (0.31 to the side, 1.38 up, arm 0.70 long), as the vanilla
 * bow pose does. First person: {@code camera + (0.30 right, -0.28 up, 0.55 forward)} in camera space. {@code handSign} is +1 for the right hand
 * (the built default, option {@code uryu.bowHand}) and -1 for the left hand (the offsets to the side mirror).
 * Nothing here was checked in the game: the numbers are the storyboard's, the harness shots (p7_02_*) tell how far off they are.
 */
public final class UryuBowAnchor {
	public static final double SIDE = 0.35;
	public static final double UP = 1.10;
	public static final double FWD = 0.45;
	public static final double SHOULDER_SIDE = 0.31;
	public static final double SHOULDER_UP = 1.38;
	public static final double ARM = 0.70;
	public static final double FP_RIGHT = 0.24;
	public static final double FP_UP = -0.24;
	public static final double FP_FWD = 0.55;
	/** First person: the nock never goes closer than this to the camera plane (the full draw would put it behind the near plane). */
	public static final double FP_NOCK_MIN_FWD = 0.32;

	private UryuBowAnchor() {
	}

	/** Horizontal forward unit vector of a body yaw in degrees (Minecraft: yaw 0 looks along +z). */
	public static double[] forward(double yawDeg) {
		double y = Math.toRadians(yawDeg);
		return new double[] {-Math.sin(y), 0.0, Math.cos(y)};
	}

	/** Horizontal unit vector to the right of a body yaw (yaw 0: -x). */
	public static double[] right(double yawDeg) {
		double y = Math.toRadians(yawDeg);
		return new double[] {-Math.cos(y), 0.0, -Math.sin(y)};
	}

	/** Third person grip: {@code feet} (x, y, z), body yaw and aim unit vector; {@code drawing} raises the arm along the aim. */
	public static double[] thirdGrip(double[] feet, double bodyYawDeg, double[] aim, boolean drawing, double handSign) {
		double[] f = forward(bodyYawDeg);
		double[] r = right(bodyYawDeg);
		double s = handSign >= 0 ? 1.0 : -1.0;
		if (!drawing) {
			return new double[] {feet[0] + r[0] * SIDE * s + f[0] * FWD, feet[1] + UP, feet[2] + r[2] * SIDE * s + f[2] * FWD};
		}
		double sx = feet[0] + r[0] * SHOULDER_SIDE * s;
		double sy = feet[1] + SHOULDER_UP;
		double sz = feet[2] + r[2] * SHOULDER_SIDE * s;
		return new double[] {sx + aim[0] * ARM, sy + aim[1] * ARM, sz + aim[2] * ARM};
	}

	/** First person grip from the camera position and its right, up and forward unit vectors. */
	public static double[] firstGrip(double[] cam, double[] right, double[] up, double[] fwd, double handSign) {
		double s = handSign >= 0 ? 1.0 : -1.0;
		return new double[] {
				cam[0] + right[0] * FP_RIGHT * s + up[0] * FP_UP + fwd[0] * FP_FWD,
				cam[1] + right[1] * FP_RIGHT * s + up[1] * FP_UP + fwd[1] * FP_FWD,
				cam[2] + right[2] * FP_RIGHT * s + up[2] * FP_UP + fwd[2] * FP_FWD};
	}

	/** The nock: the grip pulled back along the aim by the draw (see {@link UryuDrawLight#nockDistance}). */
	public static double[] nock(double[] grip, double[] aim, double p, double displayScale) {
		double d = UryuDrawLight.nockDistance(p, displayScale);
		return new double[] {grip[0] - aim[0] * d, grip[1] - aim[1] * d, grip[2] - aim[2] * d};
	}

	/**
	 * First person nock: as {@link #nock} but shortened so that it stays at least {@link #FP_NOCK_MIN_FWD} in front of the camera plane
	 * (otherwise a full draw of the 1.8 times bow would put the nock behind the camera and its glow would be culled).
	 */
	public static double[] firstNock(double[] cam, double[] camFwd, double[] grip, double[] aim, double p, double displayScale) {
		double d = UryuDrawLight.nockDistance(p, displayScale);
		double gripFwd = (grip[0] - cam[0]) * camFwd[0] + (grip[1] - cam[1]) * camFwd[1] + (grip[2] - cam[2]) * camFwd[2];
		double aimFwd = aim[0] * camFwd[0] + aim[1] * camFwd[1] + aim[2] * camFwd[2];
		if (aimFwd > 1.0E-6) {
			d = Math.min(d, Math.max(0.0, (gripFwd - FP_NOCK_MIN_FWD) / aimFwd));
		}
		return new double[] {grip[0] - aim[0] * d, grip[1] - aim[1] * d, grip[2] - aim[2] * d};
	}

	/** A bow tip: {@code sign} +1 the upper one, -1 the lower one; the bow stands upright ({@code axis} is its long unit axis). */
	public static double[] tip(double[] grip, double[] axis, double sign, double displayScale) {
		double d = sign * UryuDrawLight.tipDistance(displayScale);
		return new double[] {grip[0] + axis[0] * d, grip[1] + axis[1] * d, grip[2] + axis[2] * d};
	}

	/** Point {@code along} metres from {@code a} towards {@code b} (b - a is not required to be a unit vector). */
	public static double[] towards(double[] a, double[] b, double along) {
		double dx = b[0] - a[0];
		double dy = b[1] - a[1];
		double dz = b[2] - a[2];
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0E-9) {
			return a.clone();
		}
		return new double[] {a[0] + dx / l * along, a[1] + dy / l * along, a[2] + dz / l * along};
	}
}
