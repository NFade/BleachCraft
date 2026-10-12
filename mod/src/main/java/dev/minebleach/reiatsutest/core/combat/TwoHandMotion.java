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
 * <li>{@link #STANCE}: hands low and close to the player, the blade leaning about 27 degrees forward and a little to the right, the right fist
 * flush under the tsuba ({@link #ARM_SHIFT}, {@link #ARM_ROT}), the left fist right under it on the hilt.</li>
 * <li>{@link #keys}: wind, hit, follow and arc of the six strike profiles of {@link SwingMotion}; the sword returns to the stance.</li>
 * <li>{@link #guard}: the two handed block: the blade across the view, tip up to the left.</li>
 * </ul>
 * Numbers were laid out against a projection (FOV 70, 16:9) of the real sword model and are UNVERIFIED in game. Every one can be
 * replaced from {@code pose_override.json}, section {@code fp_two} (see design/results/fp_two_handed.md).
 */
public final class TwoHandMotion {
	/**
	 * Two handed hold (round 5): hands low and close to the player, the blade leaning about 27 degrees forward and 15 degrees to the right
	 * (the tip is in the upper third of the screen right of the middle), the right fist in the lower quarter near the middle (ndc 0.2, -0.56).
	 * Laid out in a python model of the projection (vertical FOV 70) against the real sword model, UNVERIFIED in game.
	 */
	public static final FpPose STANCE = FpPose.of(-0.47f, 0.105f, 0.06f, -36f, 0f, -8f);
	/** Two handed block pose. */
	public static final FpPose GUARD = FpPose.of(-0.32f, 0.22f, -0.06f, -14f, -16f, 54f);
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
	/**
	 * Turn of the free arm about its fist (degrees x y z, sword frame, right hander layout, a left hander mirrors y and z; the arm turns with the
	 * sword on top of it): the box of the fist lies square to the hilt and the forearm comes in from the lower left (a mirror of {@link #ARM_ROT}).
	 */
	public static final float[] LEFT_ARM_ROT = {-15f, -30f, -35f};
	/**
	 * Shift of the right arm against the sword (hand frame blocks, right hander layout, turns with the sword): the fist moves up the hilt until
	 * its box touches the underside of the tsuba (a gap of 0.004 blocks, nothing inside the tsuba).
	 */
	public static final float[] ARM_SHIFT = {0.0176f, 0.1436f, 0.0205f};
	/** Turn of the right arm about its fist (degrees x y z, sword frame): the top of the fist square to the hilt, the forearm comes in from the lower right. */
	public static final float[] ARM_ROT = {-15f, 30f, 35f};

	private static final FpPose ARC_H = FpPose.of(0f, -0.03f, -0.10f, -10f, 0f, 0f);
	private static final FpPose ARC_V = FpPose.of(0f, 0.08f, -0.12f, 0f, 0f, 0f);

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

	/** Extra turn of the free arm about its fist (degrees x y z, already mirrored for a left hander with {@code mirrored}). */
	public static float[] leftArmRot(FpParams pr, boolean mirrored) {
		float[] a = pr.vec("two.left.arm.rot", LEFT_ARM_ROT);
		return new float[] {a[0], mirrored ? -a[1] : a[1], mirrored ? -a[2] : a[2]};
	}

	/** Shift of the right arm against the sword (hand frame, mirrored x for a left hander), full weight. */
	public static float[] armShift(FpParams pr, boolean mirrored) {
		float[] a = pr.vec("two.arm.shift", ARM_SHIFT);
		return new float[] {mirrored ? -a[0] : a[0], a[1], a[2]};
	}

	/** Turn of the right arm about its fist (degrees, mirrored y and z for a left hander), full weight. */
	public static float[] armRot(FpParams pr, boolean mirrored) {
		float[] a = pr.vec("two.arm.rot", ARM_ROT);
		return new float[] {a[0], mirrored ? -a[1] : a[1], mirrored ? -a[2] : a[2]};
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
		return stance.plus(v.plus(stance.scaled(-1f)).scaled(k));
	}

	/** wind, hit, follow, arc of a profile. */
	private static FpPose[] defaults(SwingMotion.Profile p) {
		return switch (p) {
			// from the right to the left across the view: the sword sweeps like a wiper through the vertical, tip ends forward-left
			case H_R -> new FpPose[] {FpPose.of(0.00f, 0.14f, 0.10f, -4f, 6f, -40f), FpPose.of(-0.52f, 0.10f, -0.20f, -18f, -28f, 66f),
					FpPose.of(-0.64f, 0.12f, -0.16f, -18f, -34f, 82f), ARC_H};
			// backhand, from the left to the right
			case H_L -> new FpPose[] {FpPose.of(-0.52f, 0.14f, 0.10f, -4f, -6f, 50f), FpPose.of(0.12f, 0.10f, -0.20f, -18f, 28f, -60f),
					FpPose.of(0.22f, 0.12f, -0.16f, -18f, 34f, -76f), ARC_H};
			// overhead chop from the upper right, ends forward-left and low
			case V_R -> new FpPose[] {FpPose.of(0.04f, 0.40f, 0.10f, 10f, 0f, -18f), FpPose.of(-0.30f, 0.00f, -0.30f, -92f, 0f, 30f),
					FpPose.of(-0.38f, -0.08f, -0.34f, -104f, 0f, 42f), ARC_V};
			// overhead chop from the upper left, ends forward-right and low
			case V_L -> new FpPose[] {FpPose.of(-0.40f, 0.40f, 0.10f, 10f, 0f, 20f), FpPose.of(0.04f, 0.00f, -0.30f, -92f, 0f, -30f),
					FpPose.of(0.12f, -0.08f, -0.34f, -104f, 0f, -42f), ARC_V};
			// thrust: hands pulled back, then pushed forward with the blade nearly level
			case STAB -> new FpPose[] {FpPose.of(-0.20f, 0.02f, 0.22f, -34f, 0f, 10f), FpPose.of(-0.20f, 0.04f, -0.52f, -84f, 0f, 8f),
					FpPose.of(-0.20f, 0.04f, -0.58f, -88f, 0f, 8f), FpPose.ZERO};
			// rising cut
			case UPPER -> new FpPose[] {FpPose.of(-0.34f, -0.10f, 0.06f, 10f, 0f, -36f), FpPose.of(-0.06f, 0.28f, -0.22f, -34f, 0f, 40f),
					FpPose.of(-0.04f, 0.34f, -0.26f, -40f, 0f, 50f), FpPose.ZERO};
		};
	}
}
