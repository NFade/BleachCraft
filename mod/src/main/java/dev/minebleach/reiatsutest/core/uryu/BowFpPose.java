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
		float[] rightOff, float[] rightRot, float readyIn, float readyOut, float snap) {
	/** Grip (model centre) of the bow: left of the centre and low, so the lower limb leaves the screen and the upper limb stands in the left third. */
	public static final float[] GRIP = {-0.42f, -0.42f, -1.30f};
	/** Blocks per model unit: the bow model is 1.1 long, so 1.5 makes it 1.65 blocks, about 55 percent of the screen height at that depth. */
	public static final float SCALE = 1.5f;
	/** Arrow tip turned 80 degrees to the left: nearly the side view, so the bow arc, the string and the whole arrow can be read. */
	public static final float YAW = 80f;
	/** Slight lift of the arrow tip (the real shot starts under the eye and rises to the crosshair). */
	public static final float PITCH = 4f;
	public static final float ROLL = 0f;
	/** Bow hand fist from the grip: a little farther from the camera than the bow, so the bow is drawn in front of the fist. */
	public static final float[] LEFT_OFF = {0f, 0f, -0.10f};
	public static final float[] LEFT_ROT = {0f, 0f, 0f};
	/** Draw hand fist from the nock: a little behind the arrow, so the nocked arrow is drawn in front of the fingers. */
	public static final float[] RIGHT_OFF = {0.03f, 0f, -0.10f};
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
				Math.max(0.01f, p.num("bow.ready.out", READY_OUT)), Math.max(0.01f, p.num("bow.snap", SNAP)));
	}
}
