package dev.minebleach.reiatsutest.core.combat;

/**
 * Two handed hold of the drawn sword in first person (BC5c). The user decided that a drawn zanpakuto (BASE, SHIKAI, BANKAI with a
 * sword in the hand) is held upright with both hands in front of the body, in both views (the reference is the katana pose of Better
 * Combat in third person). The right hand (the one that holds the sword in the hand frame) leads; the free left hand sits on the
 * hilt lower down, at {@code two.left.s} model units from the grip, and rides on the sword rigidly, so it follows every strike and
 * guard without extra keys.
 *
 * <p>Everything is a {@link FpPose} of the right hand in the hand frame of the item renderer, an absolute pose against the one
 * handed rest (ZERO, blade axis about 0.12 / 0.97 / 0.23 in the hand frame, display scale 2.4). The pivot of every rotation is
 * not the grip but the point {@code two.pivot_s} model units along the blade (between the two fists, nearer the left), so the
 * sword turns about the hands like a real two handed cut and the left hand barely moves while the right one sweeps.
 *
 * <ul>
 * <li>{@link #STANCE}: hands low and close to the player, the blade rising forward and to the right (about 40 degrees from the vertical), the tip in
 * the upper part of the screen, the right fist under the tsuba with a stretch of hilt above it, the left fist right under it on the hilt.</li>
 * <li>{@link #keys}: wind, hit, follow and arc of the six strike profiles of {@link SwingMotion}; the sword returns to the stance.</li>
 * <li>{@link #guard}: the two handed block: the blade across the view, tip up to the left.</li>
 * </ul>
 * Numbers were laid out against a projection (FOV 70, 16:9) of the real sword model and are UNVERIFIED in game. Every one can be
 * replaced from {@code pose_override.json}, section {@code fp_two} (see design/results/fp_two_handed.md).
 */
public final class TwoHandMotion {
	/**
	 * Two handed hold (fp2): authored in Blender as camera space targets (grip at ndc 0.22 / -0.45, one block away, blade 40 degrees from the vertical forward
	 * and 12 to the right, rolled so the curve of the blade shows), see blender/fp2/author_poses.py and design/results/fp2.md.
	 */
	public static final FpPose STANCE = FpPose.of(-0.413f, 0.211f, 0.179f, -44.38f, 61.77f, 3.76f);
	/** Two handed block pose: the blade crosses the view from the lower right to the upper left (fp2, authored in Blender). */
	public static final FpPose GUARD = FpPose.of(-0.26f, 0.337f, 0.191f, -63.88f, 10.68f, 51.1f);
	/** Pivot of the rotations, model units along the blade from the grip (negative: towards the pommel). */
	public static final float PIVOT_S = -0.10f;
	/**
	 * Place of the left fist on the hilt, model units along the blade from the grip. The hilt is 0.19 below the grip and 0.06 above it
	 * (2.4 blocks per unit); the right fist (a cube of 0.25 blocks) sits flush under the tsuba, the left one right under it, so the
	 * pommel sticks out below the fists.
	 */
	public static final float LEFT_S = -0.115f;
	/** Left fist shifted off the hilt (hand frame blocks, right hander layout, turns with the sword): zero, the hilt runs through the fist. */
	public static final float[] LEFT_OFFSET = {0f, 0f, 0f};
	/** Place of the right fist on the hilt, model units along the blade from the grip: the cube sits under the tsuba with a visible stretch of hilt above it. */
	public static final float RIGHT_S = -0.03f;

	private TwoHandMotion() {
	}

	/** The stance scaled by the weight (0 = one handed hold, 1 = stance; smoothstep applied here), numbers from {@code two.pos / two.rot}. */
	public static FpPose stance(FpParams pr, float w) {
		FpPose s = FpPose.of(pr.vec("two.pos", STANCE.pos()), pr.vec("two.rot", STANCE.rot()));
		return FpPose.ZERO.lerp(s, FpEase.smooth(w));
	}

	/** The two handed block pose (absolute), numbers from {@code two.guard.pos / two.guard.rot}. */
	public static FpPose guard(FpParams pr) {
		return FpPose.of(pr.vec("two.guard.pos", GUARD.pos()), pr.vec("two.guard.rot", GUARD.rot()));
	}

	/** Pivot offset along the blade (model units) for the stance weight {@code w}. */
	public static float pivotS(FpParams pr, float w) {
		return pr.num("two.pivot_s", PIVOT_S) * FpEase.smooth(w);
	}

	/** Slow sway of the held sword while idle in the stance (zero without it). */
	public static FpPose idle(FpParams pr, float timeSec, float w) {
		float amp = pr.num("two.breath", 0.003f) * FpEase.smooth(w);
		float s = (float) Math.sin(2.0 * Math.PI * pr.num("two.breath_hz", 0.4f) * timeSec);
		float c = (float) Math.cos(2.0 * Math.PI * pr.num("two.breath_hz", 0.4f) * timeSec * 0.5);
		return FpPose.of(amp * 0.5f * c, amp * s, 0f, amp * s * 60f, 0f, amp * c * 100f);
	}

	/** Weight of the left hand on the hilt: it arrives when the stance is a fifth up and eases in. */
	public static float leftWeight(float w) {
		return FpEase.smooth((w - 0.2f) / 0.8f);
	}

	/**
	 * Where the left fist goes, in the hand frame of the main hand: the point of the hilt {@code bladePoint} (hand frame at rest, the
	 * caller takes it {@code two.left.s} along the blade from the grip) carried by the same rigid motion as the sword
	 * ({@code pos + pivot + R (p - pivot)}), plus the small {@code two.left.offset}.
	 */
	public static float[] leftFist(FpParams pr, FpPose pose, float[] pivot, float[] bladePoint) {
		return leftFist(pr, pose, pivot, bladePoint, false);
	}

	/**
	 * Same with the sideways offset {@code two.left.offset} (default {@link #LEFT_OFFSET}, right hander layout; {@code mirrored} for a left
	 * hander) carried by the rotation of the sword: the left fist stays beside the hilt through strikes and the guard.
	 */
	public static float[] leftFist(FpParams pr, FpPose pose, float[] pivot, float[] bladePoint, boolean mirrored) {
		float[] p = pose.apply(pivot, bladePoint);
		float[] o = pr.vec("two.left.offset", LEFT_OFFSET);
		float[] r = pose.applyDir(new float[] {mirrored ? -o[0] : o[0], o[1], o[2]});
		return new float[] {p[0] + r[0], p[1] + r[1], p[2] + r[2]};
	}

	/** Strike keys of a profile for the two handed hold (absolute poses; {@code two.swing.scale} scales the deviation from the stance). */
	public static SwingMotion.Keys keys(FpParams pr, SwingMotion.Profile p) {
		FpPose[] d = defaults(p);
		FpPose st = stance(pr, 1f);
		float k = pr.num("two.swing.scale", 1f);
		String b = "two.swing." + p.key;
		return new SwingMotion.Keys(key(pr, b + ".wind", d[0], st, k), key(pr, b + ".hit", d[1], st, k), key(pr, b + ".follow", d[2], st, k),
				FpPose.of(pr.vec(b + ".arc.pos", d[3].pos()), pr.vec(b + ".arc.rot", d[3].rot())).scaled(k));
	}

	private static FpPose key(FpParams pr, String base, FpPose def, FpPose stance, float k) {
		FpPose v = FpPose.of(pr.vec(base + ".pos", def.pos()), pr.vec(base + ".rot", def.rot()));
		return k == 1f ? v : stance.lerp(v, k);
	}

	/** wind, hit, follow, arc of a profile (absolute poses authored in camera space, blender/fp2/author_poses.py; arcs: the slerp of the keys already curves). */
	private static FpPose[] defaults(SwingMotion.Profile p) {
		return switch (p) {
			// from the right to the left across the view: blade up on the right, sweeping horizontally, ends pointing to the left
			case H_R -> new FpPose[] {FpPose.of(0.047f, 0.353f, 0.138f, -6.77f, 63.7f, -9.51f), FpPose.of(-0.13f, 0.344f, -0.123f, -67.41f, -14.59f, 69.02f),
					FpPose.of(-0.873f, 0.444f, -0.175f, 2.47f, 14.23f, 93.11f), FpPose.ZERO};
			// backhand, from the left to the right
			case H_L -> new FpPose[] {FpPose.of(-1.377f, 0.353f, 0.138f, -7.5f, -62.56f, 22.96f), FpPose.of(-1.2f, 0.344f, -0.123f, -63.46f, 16.59f, -55.54f),
					FpPose.of(-0.457f, 0.444f, -0.175f, -4.28f, -14.52f, -81.78f), FpPose.ZERO};
			// overhead chop from the upper right, a diagonal down to the lower left
			case V_R -> new FpPose[] {FpPose.of(-0.327f, 0.561f, 0.148f, -20.93f, 63.19f, 3.81f), FpPose.of(-0.135f, 0.547f, -0.069f, -88.52f, -37f, 68.64f),
					FpPose.of(-0.519f, 0.448f, -0.113f, -12.22f, -6.81f, 122.04f), FpPose.ZERO};
			// overhead chop from the upper left
			case V_L -> new FpPose[] {FpPose.of(-1.002f, 0.561f, 0.148f, -21.1f, -61.72f, 10.18f), FpPose.of(-1.195f, 0.547f, -0.069f, -78.31f, 40.84f, -59.89f),
					FpPose.of(-0.811f, 0.448f, -0.113f, -17.6f, 9.31f, -108.19f), FpPose.ZERO};
			// thrust: hands pulled back, then pushed forward with the blade nearly level
			case STAB -> new FpPose[] {FpPose.of(-0.321f, 0.34f, 0.401f, 10.44f, 64.18f, -72.83f), FpPose.of(0.074f, 0.304f, -0.151f, -108.32f, 43.29f, 32.99f),
					FpPose.of(0.078f, 0.299f, -0.25f, -108.04f, 44.81f, 32.23f), FpPose.ZERO};
			// rising cut
			case UPPER -> new FpPose[] {FpPose.of(-0.535f, 0.296f, 0.191f, -73f, -37.59f, 72.54f), FpPose.of(-0.43f, 0.319f, 0.011f, -78.01f, 42.12f, 30.58f),
					FpPose.of(-0.338f, 0.536f, 0.026f, 3.16f, 58.22f, -19.41f), FpPose.ZERO};
		};
	}
}
