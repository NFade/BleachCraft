package dev.minebleach.reiatsutest.core.combat;

/**
 * First person block stance, parry flash recoil and blocked-hit recoil of the zanpakuto (right mouse button, parry window in
 * {@link CombatConfig}). Pure maths; the client feeds in time and weights and draws the result.
 *
 * <ul>
 * <li>Stance: the right hand with the sword moves from rest to the GUARD pose (the user's cross-body diagonal guard: the blade enters from the bottom edge
 * at the middle and rises to the upper left, hand at the bottom edge) in {@code guard.in} seconds and back in {@code guard.out}; the weight is a smoothstep of a
 * linear ramp.</li>
 * <li>The free left hand supports the blade ({@link #leftFist}) and arrives slightly after the stance has started.</li>
 * <li>Parry: the sword jerks forward and up, overshoots back and settles (damped cosine envelope, {@link #envelope}).</li>
 * <li>Blocked hit (damage reduced): a short push of the sword back at the face.</li>
 * </ul>
 * Pose numbers are UNVERIFIED in game; every one can be replaced from {@code pose_override.json} (key paths {@code guard.*}).
 */
public final class GuardMotion {
	/**
	 * One handed block (fallback when the two handed hold is off), 2026-10-13: the user's cross-body diagonal guard as in {@link TwoHandMotion#GUARD} (same
	 * rotation, so the same blade direction and roll: R11 T1, the cutting edge faces forward) but about the grip instead of the point {@code two.pivot_s}; the
	 * shift was searched again for the grip pivot and the vanilla one handed arms (no {@code two.arm.shift} / {@code two.arm.rot}): the blade line on the screen is
	 * the old one within a degree (bottom edge 1.8 degrees right of the middle, horizon 20.4 degrees left, closest approach 17.8 degrees), the right fist at the
	 * bottom edge (ndc -0.01, -0.83). The left hand supports the blade 0.30 model units up the hilt ({@code guard.left.s}), so it ends at the upper left of the picture
	 * (ndc -0.49, 0.50); both arms clear the keep-out rectangle (smaller margin 0.29 / 0.23 / 0.14 at 4:3 / 16:9 / 21:9), nearest corner 0.17 blocks from the camera. During the
	 * blend from the rest pose the free left arm dips up to 0.07 ndc into the keep-out rectangle for about 0.03 s (the old pose stayed 0.14 clear; with the edge forward
	 * the vanilla arms cannot satisfy all rules at once, see design/results/r11_T1.md). Was (-0.802, 0.272, 0.418, -89.3, -112.5, -80.3); before that the Pose B pose
	 * (-0.317, -0.208, -0.337, -41.5, 0.5, -5.3) and the old pose (-0.22, 0.14, -0.04, -12, -22, 62). UNVERIFIED in game.
	 */
	public static final FpPose GUARD = FpPose.of(-0.815f, 0.154f, 0.204f, -8.8f, -5.1f, 37.1f);
	/**
	 * Block with the sheathed sword (SEALED, round 5): the scabbard with the sword in it is carried as one body in both hands across the view,
	 * the mouth near the right fist, the scabbard rising to the upper left at about 30 degrees on the screen (blade tilt 20 forward, 36 about the
	 * view axis); the pivot is the grip. Laid out in a python model of the projection (vertical FOV 70) against the stow pose, UNVERIFIED in game.
	 */
	public static final FpPose SEALED_GUARD = FpPose.of(-0.212f, -0.15f, 0f, -20f, 0f, -36f);
	/**
	 * r8: shift of the sheathed hold (rest, no block) in the hand frame, blocks, key {@code guard.sealed.rest}: the sword in its scabbard, the right fist and the hand
	 * that holds the scabbard sit this much lower (the play test: hands too high everywhere). Fades out while the sealed block comes up.
	 */
	public static final float[] SEALED_REST = {0f, -0.04f, 0f};
	/** Where the left hand holds the scabbard in the sealed block, model units (metres) from the grip along the scabbard. */
	public static final float SEALED_LEFT_S = 0.18f;
	/** Turn of the left forearm about its fist in the sealed block (degrees x y z, camera frame, right hander layout; a left hander mirrors y and z): the arm comes in from the lower left. */
	public static final float[] SEALED_LEFT_ARM_ROT = {0f, -75f, 0f};
	private static final FpPose PARRY_KICK = FpPose.of(0f, 0.04f, -0.14f, -16f, 0f, 5f);
	private static final FpPose HIT_KICK = FpPose.of(0f, -0.01f, 0.05f, 7f, 0f, -2f);
	private static final float[] LEFT_OFFSET = {0f, -0.02f, 0.06f};

	/** Which recoil. */
	public enum Kick {
		PARRY("parry", PARRY_KICK, 0.05f, 0.15f, 3.0f), HIT("hit", HIT_KICK, 0.03f, 0.10f, 3.5f);

		final String key;
		final FpPose pose;
		final float rise;
		final float decay;
		final float freq;

		Kick(String key, FpPose pose, float rise, float decay, float freq) {
			this.key = key;
			this.pose = pose;
			this.rise = rise;
			this.decay = decay;
			this.freq = freq;
		}
	}

	private GuardMotion() {
	}

	/** One frame of the stance weight: ramps to 1 while blocking (in {@code inSec}) and to 0 otherwise (out {@code outSec}). */
	public static float stepWeight(float w, boolean blocking, float dt, float inSec, float outSec) {
		float rate = blocking ? dt / Math.max(inSec, 1e-3f) : -dt / Math.max(outSec, 1e-3f);
		return FpEase.clamp01(w + rate);
	}

	/** The guard pose of the right hand scaled by the weight {@code w} (0..1, smoothstep applied here). */
	public static FpPose stance(FpParams pr, float w) {
		FpPose g = FpPose.of(pr.vec("guard.pos", GUARD.pos()), pr.vec("guard.rot", GUARD.rot()));
		return FpPose.ZERO.lerp(g, FpEase.smooth(w));
	}

	/** The sealed block pose of the main hand and the scabbard (absolute), numbers from {@code guard.sealed.pos / guard.sealed.rot}. */
	public static FpPose sealedGuard(FpParams pr) {
		return FpPose.of(pr.vec("guard.sealed.pos", SEALED_GUARD.pos()), pr.vec("guard.sealed.rot", SEALED_GUARD.rot()));
	}

	/** The shift of the sheathed rest hold ({@code guard.sealed.rest}), hand frame, right hander layout. */
	public static float[] sealedRest(FpParams pr) {
		return pr.vec("guard.sealed.rest", SEALED_REST);
	}

	/** Place of the left hand on the scabbard (metres from the grip) while the sealed block is up, {@code guard.sealed.left_s}. */
	public static float sealedLeftS(FpParams pr) {
		return pr.num("guard.sealed.left_s", SEALED_LEFT_S);
	}

	/** Turn of the left forearm about its fist in the sealed block ({@code guard.sealed.left_arm_rot}), mirrored for a left hander. */
	public static float[] sealedLeftArmRot(FpParams pr, boolean mirrored) {
		float[] a = pr.vec("guard.sealed.left_arm_rot", SEALED_LEFT_ARM_ROT);
		return new float[] {a[0], mirrored ? -a[1] : a[1], mirrored ? -a[2] : a[2]};
	}

	/** Slow hold breathing of the sword while the guard is up (zero without the guard). */
	public static FpPose breath(FpParams pr, float timeSec, float w) {
		float amp = pr.num("guard.breath", 0.004f) * FpEase.smooth(w);
		float s = (float) Math.sin(2.0 * Math.PI * pr.num("guard.breath_hz", 0.5f) * timeSec);
		return FpPose.of(0f, amp * s, 0f, 0f, 0f, amp * s * 150f);
	}

	/**
	 * Pose of the right hand: the strike (or rest) pose blended into the stance by the weight {@code w}, plus the hold breathing and
	 * the two recoils (already scaled, see {@link #kick}).
	 */
	public static FpPose pose(FpParams pr, FpPose base, float w, float timeSec, FpPose parryKick, FpPose hitKick) {
		return pose(pr, base, stance(pr, 1f), w, timeSec, parryKick, hitKick);
	}

	/** Same with an explicit full guard pose (the two handed guard of {@link TwoHandMotion#guard}). */
	public static FpPose pose(FpParams pr, FpPose base, FpPose stance, float w, float timeSec, FpPose parryKick, FpPose hitKick) {
		FpPose p = base.lerp(stance, FpEase.smooth(w)).plus(breath(pr, timeSec, w));
		return p.plus(parryKick).plus(hitKick);
	}

	/**
	 * Damped recoil envelope {@code s} seconds after the impact: 0 before, rises to 1 over {@code rise}, then
	 * {@code exp(-(s-rise)/decay) * cos(2 pi freq (s-rise))}: one rebound past zero (about -0.3 for 3 Hz and 0.15 s), then still.
	 */
	public static float envelope(float s, float rise, float decay, float freq) {
		if (s <= 0f) {
			return 0f;
		}
		if (s < rise) {
			return FpEase.smooth(s / rise);
		}
		float u = s - rise;
		return (float) (Math.exp(-u / Math.max(decay, 1e-3f)) * Math.cos(2.0 * Math.PI * freq * u));
	}

	/**
	 * Offset pose of a recoil {@code since} seconds after the event. {@code side} in -1..1 (positive: the attacker stands to the right of
	 * the player) pushes the sword the other way by {@code <kick>.side_x} blocks; {@code amplitude} scales it (0.5 with reduced motion).
	 */
	public static FpPose kick(FpParams pr, Kick k, float since, float side, float amplitude) {
		String b = "guard." + k.key;
		float e = envelope(since, pr.num(b + ".rise", k.rise), pr.num(b + ".decay", k.decay), pr.num(b + ".freq", k.freq));
		if (e == 0f) {
			return FpPose.ZERO;
		}
		FpPose p = FpPose.of(pr.vec(b + ".pos", k.pose.pos()), pr.vec(b + ".rot", k.pose.rot()));
		float sideX = -side * pr.num(b + ".side_x", k == Kick.PARRY ? 0.04f : 0.03f);
		p = p.plus(FpPose.of(sideX, 0f, 0f, 0f, 0f, 0f));
		return p.scaled(e * amplitude);
	}

	/** Seconds after which the recoil is over (envelope below 3 percent). */
	public static float kickLength(FpParams pr, Kick k) {
		String b = "guard." + k.key;
		return pr.num(b + ".rise", k.rise) + 3.5f * pr.num(b + ".decay", k.decay);
	}

	/** Weight of the free left hand (0 until the stance is a quarter up, then eases in). */
	public static float leftWeight(float w) {
		return FpEase.smooth((w - 0.25f) / 0.75f);
	}

	/**
	 * Where the left fist goes, in the hand frame of the main hand: the point of the blade {@code bladePoint} (hand frame at rest, the
	 * caller takes it {@code guard.left.s} along the blade from the grip) moved by the pose, plus a small fixed {@code guard.left.offset}
	 * towards the camera so the fist does not sink into the blade.
	 */
	public static float[] leftFist(FpParams pr, FpPose pose, float[] pivot, float[] bladePoint) {
		float[] p = pose.apply(pivot, bladePoint);
		float[] o = pr.vec("guard.left.offset", LEFT_OFFSET);
		return new float[] {p[0] + o[0], p[1] + o[1], p[2] + o[2]};
	}
}
