package dev.minebleach.reiatsutest.core.combat;

/**
 * The empty hands of the first person view (round 6: the Byakuya bankai, the sword is scattered and the right hand holds nothing).
 * Vanilla draws no arm for a held item that shows no model, and the ability gestures drew hands only while they ran, so in the bankai
 * the screen was empty. Now both vanilla arms stay in a quiet pose of their own (the idle) and the gestures ({@link CastGesture} rows
 * {@code R} for the main hand, {@code L} for the other one) move them away from it and back.
 *
 * <p>Layout: the right hander layout of the hand frame of the item renderer (camera axes: x to the right, y up, forward is -z; blocks).
 * The caller mirrors x of positions and (y, z) of rotation vectors for a left hander. All positions here are CAMERA SPACE positions of the
 * fist of the vanilla arm (the point {@link FpGeometry#FIST} of the arm of that side), not shifts; {@link #frame} returns the shift from the
 * resting vanilla fist, which is what the client translates by.
 *
 * <p>An arm is the vanilla box of 4 x 12 x 4 pixels. It is placed by three things: where the fist is, which way the forearm points
 * (the direction shoulder to fist, {@code dir}) and which way the palm faces ({@code palm}, the normal of the inner side of the box, turned about
 * the forearm axis). The turn is a quaternion about the fist ({@link #quat}) taking the vanilla arm to that. Rows of a gesture are
 * rotation VECTORS in degrees (axis times angle, hand frame axes), not Euler angles: palm down to palm up is half a turn about the arm axis, which
 * wraps in Euler angles; a rotation vector interpolates it as the shortest turn. The turn of a row is applied after the idle turn:
 * {@code q = row * idle}.
 *
 * <p>Shipped numbers were laid out in a python model of the vanilla arm chain (FOV 70, 16:9 and 4:3: no stump of the shoulder end inside the
 * picture, no arm closer than the near plane) and have not been seen in the game (UNVERIFIED). Every number can be replaced from
 * {@code pose_override.json}, section {@code fp_cast}, flat keys {@code free.<key>} (see the keys below).
 */
public final class FreeHands {
	/** Axis of the box of the vanilla right arm, shoulder end to hand end (unit; the left arm mirrors x). */
	public static final float[] ARM_DIR = {0.33360f, 0.46985f, -0.81729f};
	/** Normal of the inner (palm) side of the vanilla right arm, perpendicular to {@link #ARM_DIR} (the left arm mirrors x). */
	public static final float[] ARM_PALM = unit(perp(new float[] {-0.36795f, -0.73329f, -0.57175f}, ARM_DIR));
	/** Camera space position of the fist of the resting vanilla arm of the main hand (the other hand mirrors x). */
	public static final float[] REST_FIST = {0.694f, -0.538f, -1.036f};

	/** Idle of the main (right) hand: the forearm raised in front of the body across to the left, palm down and a little inward. */
	public static final float[] IDLE_R = {0.20f, -0.39f, -0.85f};
	public static final float[] IDLE_R_DIR = {-0.45f, 0.55f, -0.70f};
	public static final float[] IDLE_R_PALM = {-0.45f, -1f, 0f};
	/** Idle of the other (left) hand: low at the hip, partly out of the picture, palm to the body. */
	public static final float[] IDLE_L = {-0.54f, -0.47f, -0.84f};
	public static final float[] IDLE_L_DIR = {0.25f, 0.55f, -0.79f};
	public static final float[] IDLE_L_PALM = {0.8f, -0.3f, 0f};

	private FreeHands() {
	}

	/** Where one hand is this frame: the shift of its fist from the resting vanilla fist and the turn of the arm about the fist. */
	public record Frame(float[] move, float[] q) {
	}

	// ------------------------------------------------------------------ idle

	/** Camera space idle fist position (right hander layout; {@code left}: the other hand), numbers from {@code idle_r} / {@code idle_l}. */
	public static float[] idlePos(FpParams pr, boolean left) {
		return left ? pr.vec("idle_l", IDLE_L) : pr.vec("idle_r", IDLE_R);
	}

	/** Quaternion (x, y, z, w) of the idle turn of an arm about its fist. */
	public static float[] idleQuat(FpParams pr, boolean left) {
		float[] dir = left ? pr.vec("idle_l_dir", IDLE_L_DIR) : pr.vec("idle_r_dir", IDLE_R_DIR);
		float[] palm = left ? pr.vec("idle_l_palm", IDLE_L_PALM) : pr.vec("idle_r_palm", IDLE_R_PALM);
		return quat(left, dir, palm);
	}

	/**
	 * The turn about the fist that takes the vanilla arm of this side to the forearm direction {@code dir} with the palm side facing
	 * {@code palm} (as near as the forearm direction allows). Quaternion (x, y, z, w) in the camera axes.
	 */
	public static float[] quat(boolean left, float[] dir, float[] palm) {
		float s = left ? -1f : 1f;
		float[] d0 = {s * ARM_DIR[0], ARM_DIR[1], ARM_DIR[2]};
		float[] n0 = {s * ARM_PALM[0], ARM_PALM[1], ARM_PALM[2]};
		float[] d = unit(dir);
		float[] q1 = rotationTo(d0, d);
		float[] n1 = FpPose.rotate(q1, n0);
		float[] p = unit(perp(palm, d));
		// signed angle from n1 to p about d
		float[] cr = cross(n1, p);
		float ang = (float) Math.atan2(dot(cr, d), dot(n1, p));
		return FpPose.mul(FpPose.axisAngle(d[0], d[1], d[2], ang), q1);
	}

	/**
	 * One hand this frame. {@code row} is the gesture row of the hand (px py pz, then the rotation vector in degrees; at least six numbers,
	 * or null), {@code breath} scales the quiet sway (0 while a gesture drives the hand), {@code presence} (0..1) brings the hand in from
	 * below the screen edge on the side of its arm. All right hander layout.
	 */
	public static Frame frame(FpParams pr, boolean left, float[] row, float time, float breath, float presence) {
		float[] idle = idlePos(pr, left);
		float s = left ? -1f : 1f;
		float amp = pr.num("breath", 0.006f) * breath;
		float hz = pr.num("breath_hz", 0.25f);
		float ph = left ? 1.7f : 0f;
		float w = (float) (2.0 * Math.PI * hz * time + ph);
		float by = amp * (float) Math.sin(w);
		float bz = amp * 0.6f * (float) Math.sin(w * 0.5f + 0.8f);
		float bx = amp * 0.5f * (float) Math.cos(w * 0.7f + ph);
		float e = FpEase.smooth(presence);
		float rise = pr.num("rise_drop", 0.95f);
		float[] r = row == null || row.length < 6 ? new float[6] : row;
		float[] move = {idle[0] + r[0] + bx + s * 0.30f * (1f - e) * 1.0f - (left ? -REST_FIST[0] : REST_FIST[0]),
				idle[1] + r[1] + by - rise * (1f - e) - REST_FIST[1], idle[2] + r[2] + bz - REST_FIST[2]};
		// the turn: breath roll of a degree or two about the forearm, the row on top of the idle
		float[] qi = idleQuat(pr, left);
		float[] qb = rotVecQuat(new float[] {amp * 150f * (float) Math.sin(w + 0.5f), amp * 100f * (float) Math.sin(w * 0.5f), amp * 200f * (float) Math.sin(w * 0.8f)});
		float[] qr = rotVecQuat(new float[] {r[3], r[4], r[5]});
		return new Frame(move, FpPose.mul(qr, FpPose.mul(qb, qi)));
	}

	/** Weight of the presence of the empty hands: rises over {@code riseSec} while they are shown, drops at once when they are not. */
	public static float stepPresence(float w, boolean shown, float dt, float riseSec) {
		if (!shown) {
			return 0f;
		}
		return Math.min(1f, w + dt / Math.max(0.05f, riseSec));
	}

	// ------------------------------------------------------------------ rotation helpers

	/** Quaternion of a rotation vector in degrees (axis times angle). */
	public static float[] rotVecQuat(float[] deg) {
		float a = (float) Math.sqrt(deg[0] * deg[0] + deg[1] * deg[1] + deg[2] * deg[2]);
		if (a < 1e-6f) {
			return new float[] {0f, 0f, 0f, 1f};
		}
		return FpPose.axisAngle(deg[0] / a, deg[1] / a, deg[2] / a, (float) Math.toRadians(a));
	}

	/** Mirrors a quaternion across the plane x = 0 (right hander layout to the left hander): (x, -y, -z, w). */
	public static float[] mirrorQuat(float[] q) {
		return new float[] {q[0], -q[1], -q[2], q[3]};
	}

	/** Shortest rotation taking the unit vector {@code a} to the unit vector {@code b}. */
	static float[] rotationTo(float[] a, float[] b) {
		float d = dot(a, b);
		if (d > 0.999999f) {
			return new float[] {0f, 0f, 0f, 1f};
		}
		if (d < -0.999999f) {
			float[] ax = cross(a, new float[] {1f, 0f, 0f});
			if (dot(ax, ax) < 1e-6f) {
				ax = cross(a, new float[] {0f, 1f, 0f});
			}
			ax = unit(ax);
			return FpPose.axisAngle(ax[0], ax[1], ax[2], (float) Math.PI);
		}
		float[] c = cross(a, b);
		float s = (float) Math.sqrt(dot(c, c));
		return FpPose.axisAngle(c[0] / s, c[1] / s, c[2] / s, (float) Math.atan2(s, d));
	}

	static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	static float[] cross(float[] a, float[] b) {
		return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	static float[] unit(float[] a) {
		float l = (float) Math.sqrt(dot(a, a));
		return l < 1e-9f ? new float[] {0f, 0f, -1f} : new float[] {a[0] / l, a[1] / l, a[2] / l};
	}

	/** {@code v} without its part along the unit vector {@code d}. */
	static float[] perp(float[] v, float[] d) {
		float k = dot(v, unit(d));
		float[] u = unit(d);
		return new float[] {v[0] - u[0] * k, v[1] - u[1] * k, v[2] - u[2] * k};
	}
}
