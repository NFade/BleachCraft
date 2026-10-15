package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.combat.FpParams;

/**
 * First person pose of Uryu's bow for right handed players (R12, bow in the LEFT hand, the right hand draws the string): where the bow
 * stands on the screen, how it is turned, and where the two fists are relative to it. All numbers are in the camera frame of the first
 * person hand pass (x right, y up, -z forward, blocks) with the hand origin of the vanilla item hold (see {@code FpBow}). The bow model is
 * placed by its grip (model centre) and scaled uniformly (model units to blocks); the draw hand is put on the nock point of the model
 * ({@link BowDraw#nockZ}) so it travels exactly with the string and the arrow.
 *
 * <p>Every number can be overridden from the dev file {@code pose_override.json}, section {@code fp_bow} (hot reload, no rebuild):
 * <pre>
 * "fp_bow": { "enabled": 1, "grip": [x, y, z], "scale": 1.5, "rot": [yaw, pitch, roll],
 *             "left":  { "off": [x, y, z], "rot": [rx, ry, rz] },
 *             "right": { "off": [x, y, z], "rot": [rx, ry, rz] },
 *             "ready": { "in": 0.10, "out": 0.20 }, "snap": 0.09 }
 * </pre>
 * {@code yaw} turns the arrow tip to the LEFT of the screen (degrees, 0 = arrow points away from the camera, 90 = across the screen: the
 * side view of the bow), {@code pitch} raises the arrow tip, {@code roll} turns the bow counter-clockwise on the screen. {@code off} is a
 * shift of the fist from the grip (left) or from the nock (right) in the camera frame; {@code rot} turns the forearm about its fist
 * (Euler X, Y, Z in degrees, camera frame): with 0 the arm comes from the bottom of the screen like a vanilla empty hand.
 */
public record BowFpPose(boolean enabled, float[] grip, float scale, float yaw, float pitch, float roll, float[] leftOff, float[] leftRot,
		float[] rightOff, float[] rightRot, float readyIn, float readyOut, float snap, float[] idleGrip, float[] idleRot, float[] idleLeftRot) {
	/**
	 * R13 (user feedback): at rest the bow hand hangs low, the forearm comes in from the left edge of the screen and holds the bow to the left of the string; when the arrow
	 * is drawn the bow rises and turns towards the crosshair (the arrow tip a little to the RIGHT of the nock, so the shot seems to go where you aim). {@code idle*} is the rest
	 * pose, {@code grip}/{@code rot}/{@code left.rot} the pose at full draw.
	 */
	public static final float[] IDLE_GRIP = {-0.45f, -0.84f, -1.10f};
	public static final float[] IDLE_ROT = {30f, 0f, -32f}; // R13: lowered, the bow leans to the RIGHT
	public static final float[] IDLE_LEFT_ROT = {0f, 0f, -80f};

	/** A bow placement: grip, the Euler turn (yaw, pitch, roll degrees) and the turn of the bow forearm. */
	public record Placed(float[] grip, float yaw, float pitch, float roll, float[] leftRot) {
	}

	/**
	 * The bow placement for the raise {@code raise} 0..1 (0 = rests low, 1 = in the shooting stance) and the draw progress {@code pull} 0..1: the position and the forearm
	 * follow the raise, the turn towards the aim follows the raise and, more, the pull. Pure, smooth, equals the full draw pose at (1, 1) and the rest pose at (0, any).
	 */
	public Placed placed(double raise, double pull) {
		float r = (float) Math.max(0.0, Math.min(1.0, raise));
		float pl = (float) Math.max(0.0, Math.min(1.0, pull));
		// one progress for everything (position, turn, forearm), so the bow rises and turns to the aim in one smooth motion while the string is pulled: it starts to lift at once
		// (a quarter of the way) and stands in the shooting stance at about two thirds of the draw
		float x = Math.min(1f, 0.25f + 1.5f * pl);
		float t = r * (x * x * (3f - 2f * x));
		float[] g = new float[3];
		float[] lr = new float[3];
		for (int i = 0; i < 3; i++) {
			g[i] = idleGrip[i] + (grip[i] - idleGrip[i]) * t;
			lr[i] = idleLeftRot[i] + (leftRot[i] - idleLeftRot[i]) * t;
		}
		return new Placed(g, idleRot[0] + (yaw - idleRot[0]) * t, idleRot[1] + (pitch - idleRot[1]) * t, idleRot[2] + (roll - idleRot[2]) * t, lr);
	}

	/** Grip (model centre) of the bow: left of the centre and low, a little farther from the camera (R13: was -0.42, -0.42, -1.30), so the bow stands in the left third, the limbs span the view and both fists stay apart. */
	public static final float[] GRIP = {0.18f, -0.42f, -1.35f}; // R13 (user references: Skyrim, Mordhau): at full draw the bow body stands right of the crosshair, the sight stays open, the arrow points to the crosshair
	/** Blocks per model unit: the bow model is 1.1 long, so 1.5 makes it 1.65 blocks, about 55 percent of the screen height at that depth. */
	public static final float SCALE = 1.2f; // R13: was 1.5
	/**
	 * R13: the arrow tip turned 8 degrees to the left at full draw: the bow stands right of the crosshair and the arrow points back to it (was 80, the side view: the bow looked as if it shot to the left, and a second 60 degree turn of the
	 * old hold added to it). The arrow converges on the crosshair, the shot leaves along the look direction, the bow arc, the string and the hands are seen in perspective.
	 */
	public static final float YAW = 8f;
	/** Slight lift of the arrow tip (the real shot starts under the eye and rises to the crosshair). */
	public static final float PITCH = 5f;
	public static final float ROLL = -12f; // R13: the bow leans, its top to the right
	/** Bow hand fist from the grip: a little farther from the camera than the bow, so the bow is drawn in front of the fist. */
	public static final float[] LEFT_OFF = {0f, 0f, -0.10f};
	public static final float[] LEFT_ROT = {0f, 0f, -62f}; // R13: the bow forearm comes in from the lower LEFT corner and points to the right (Z turn about the fist, negative = clockwise) // R13: the bow forearm comes from the lower left
	/** Draw hand fist from the nock: a little behind the arrow, so the nocked arrow is drawn in front of the fingers. */
	public static final float[] RIGHT_OFF = {0.22f, -0.12f, -0.06f}; // R13: was 0.03, 0, -0.10: the draw fist stays right of the bow fist
	/** The draw forearm fans out to the lower right (Z turn about the fist). */
	public static final float[] RIGHT_ROT = {0f, 0f, 20f};
	/** Seconds for the draw hand to travel from the resting (vanilla empty hand) place to the string, and back after the shot. */
	public static final float READY_IN = 0.10f;
	public static final float READY_OUT = 0.20f;
	/** Seconds of the forward snap of the hand after the release. */
	public static final float SNAP = 0.09f;

	/** The shipped numbers. */
	public static BowFpPose defaults() {
		return of(FpParams.DEFAULTS);
	}

	/** The numbers of {@code p} ({@code fp_bow} section of the dev override) with the shipped defaults for everything not given. */
	public static BowFpPose of(FpParams p) {
		float[] rot = p.vec("bow.rot", new float[] {YAW, PITCH, ROLL});
		return new BowFpPose(p.num("bow.enabled", 1f) > 0.5f, p.vec("bow.grip", GRIP), p.num("bow.scale", SCALE), rot[0], rot[1], rot[2],
				p.vec("bow.left.off", LEFT_OFF), p.vec("bow.left.rot", LEFT_ROT), p.vec("bow.right.off", RIGHT_OFF),
				p.vec("bow.right.rot", RIGHT_ROT), Math.max(0.01f, p.num("bow.ready.in", READY_IN)),
				Math.max(0.01f, p.num("bow.ready.out", READY_OUT)), Math.max(0.01f, p.num("bow.snap", SNAP)), p.vec("bow.idle.grip", IDLE_GRIP), p.vec("bow.idle.rot", IDLE_ROT), p.vec("bow.idle.left.rot", IDLE_LEFT_ROT));
	}
}
