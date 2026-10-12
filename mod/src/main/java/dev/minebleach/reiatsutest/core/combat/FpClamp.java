package dev.minebleach.reiatsutest.core.combat;

/**
 * Reach limit of the ability gestures (fp_round5, item 6): the hands and the blade must not come closer to the camera than a margin, or the
 * near plane (0.05 blocks) cuts them off. A gesture is a delta on the held stance; the part of it that would bring a part of the hands or
 * of the sword closer than {@code near} blocks is taken off by one factor {@code k} (0..1) of the whole delta, found by bisection, so the
 * motion keeps its shape and only gets smaller where it would be cut. The right hand (with the sword and the left hand riding on the hilt)
 * and the free left hand have a factor each. {@link #release} makes the factor come back slowly, so the gesture does not jump.
 * Pure maths; the client feeds the pose of the frame ({@link FpGeometry} lays the hands and the blade against the camera).
 */
public final class FpClamp {
	/** Default minimal distance in front of the camera, blocks (the near plane is 0.05). */
	public static final float NEAR = 0.12f;

	private FpClamp() {
	}

	/** Margin (blocks, at least 0 is allowed) of a scale factor: at(1) is the unscaled gesture. */
	public interface Margin {
		float at(float k);
	}

	/**
	 * What the hands must keep: {@code near} blocks in front of the camera, and the centres of the fists inside the picture ({@code xMax} of the
	 * half width, between {@code yMin} and {@code yMax} of the half height; a fist at the lower edge is half out of the picture).
	 */
	public record Limits(float near, float aspect, float xMax, float yMin, float yMax) {
		public static Limits of(float aspect) {
			return new Limits(NEAR, aspect, 1.0f, -1.05f, 0.95f);
		}
	}

	/**
	 * Largest {@code k} in 0..1 with {@code at(k) >= 0}; 1 when the whole gesture is allowed. When even {@code k = 0} (the pose without the
	 * gesture) is over a limit, the gesture only may not make it worse (so a stance that was tuned off the screen does not silence every gesture).
	 */
	public static float solve(Margin m) {
		float floor = Math.min(0f, m.at(0f)) - 1e-5f;
		if (m.at(1f) >= floor) {
			return 1f;
		}
		float lo = 0f;
		float hi = 1f;
		for (int i = 0; i < 12; i++) {
			float mid = 0.5f * (lo + hi);
			if (m.at(mid) >= floor) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/** One frame of the smoothing: down at once (nothing may be cut), up over {@code releaseSec}. */
	public static float release(float prev, float target, float dt, float releaseSec) {
		if (target <= prev) {
			return target;
		}
		return Math.min(target, prev + dt / Math.max(1e-3f, releaseSec));
	}

	/** Result of {@link #factors}: factor of the right hand delta and of the left hand row. */
	public record Factors(float right, float left) {
		public static final Factors ONE = new Factors(1f, 1f);
	}

	/** Allowance (blocks) added to the picture frustum at the near plane for the thickness of the blade, the tsuba and the arms. */
	private static final float THICK = 0.09f;
	private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35.0));

	/**
	 * How safe the pose is from the near plane, in blocks (at least 0 is allowed): the right arm (all eight corners of its box), the left arm
	 * and the blade must stay {@code near} blocks in front of the camera. The blade is only a thin bar: it is cut visibly only where it crosses
	 * the plane {@code near} in front of the eye, so its margin is how far that crossing is outside the picture (a sword held up and back over
	 * the head crosses the plane high above the screen and is not limited). {@code l} is the row of the left hand ({@code px py pz arx ary arz free})
	 * or null for the hand riding on the hilt; the left hand is only considered when the hold has it.
	 */
	public static float margin(FpGeometry.Hold h, FpRig rig, FpPose pose, FpPose stanceBase, float[] l, Limits lim) {
		float min = Float.MAX_VALUE;
		for (float[] c : FpGeometry.rightArm(h, pose)) {
			min = Math.min(min, -c[2] - lim.near);
		}
		min = Math.min(min, bladeMargin(h, rig, pose, lim.near, lim.aspect));
		min = Math.min(min, screenMargin(FpGeometry.ndc(FpGeometry.cam(FpGeometry.rightFist(h, pose)), lim.aspect), lim));
		if (h.left()) {
			min = Math.min(min, leftMargin(h, pose, stanceBase, l, lim));
		}
		return min;
	}

	/** Margin of the blade (see {@link #margin}): negative when the bar crosses the near plane inside the picture. */
	public static float bladeMargin(FpGeometry.Hold h, FpRig rig, FpPose pose, float near, float aspect) {
		float[] a = FpGeometry.cam(pose.apply(h.pivot(), rig.hand(0.5f, FpRig.POMMEL_Y, 0.5f)));
		float[] b = FpGeometry.cam(pose.apply(h.pivot(), rig.hand(0.5f, FpRig.TIP_Y, 0.5f)));
		float da = -a[2];
		float db = -b[2];
		if (da >= near && db >= near) {
			return 1f;
		}
		if (da < near && db < near) {
			return Math.max(da, db) - near;
		}
		float t = (da - near) / (da - db);
		float x = a[0] + (b[0] - a[0]) * t;
		float y = a[1] + (b[1] - a[1]) * t;
		float lx = near * TAN_HALF_FOV * aspect * 1.2f + THICK;
		float ly = near * TAN_HALF_FOV * 1.2f + THICK;
		return Math.max(Math.abs(x) - lx, Math.abs(y) - ly);
	}

	/** Soft limit of a forward (negative z) displacement: about {@code z} for small ones, never beyond {@code -max}; backwards is untouched. */
	public static float softForward(float z, float max) {
		if (z >= 0f || max <= 1e-4f) {
			return z;
		}
		return (float) (-max * Math.tanh(-z / max));
	}

	/** How far inside the picture a fist centre is (negative: outside or behind the camera), scaled like blocks at one block distance. */
	static float screenMargin(float[] ndc, Limits lim) {
		if (ndc == null) {
			return -1f;
		}
		return 0.5f * Math.min(lim.xMax - Math.abs(ndc[0]), Math.min(ndc[1] - lim.yMin, lim.yMax - ndc[1]));
	}

	/** Margin of the left arm: nearest depth in front of the camera minus {@code near}, and the fist inside the picture. */
	static float leftMargin(FpGeometry.Hold h, FpPose pose, FpPose stanceBase, float[] l, Limits lim) {
		float free = l == null ? 0f : l[6];
		float[] shift = l == null ? null : new float[] {l[0], l[1], l[2]};
		float[] fist = FpGeometry.leftFist(h, pose, stanceBase, free, shift);
		float min = Float.MAX_VALUE;
		for (float[] c : FpGeometry.leftArm(h, fist, pose, stanceBase, free)) {
			min = Math.min(min, -c[2] - lim.near);
		}
		return Math.min(min, screenMargin(FpGeometry.ndc(FpGeometry.cam(fist), lim.aspect), lim));
	}

	/**
	 * The factors of a gesture frame: {@code base} is the pose without the gesture (stance, strike, guard), {@code r} the six numbers of
	 * the right hand row, {@code l} the seven of the left one (or null).
	 */
	public static Factors factors(FpGeometry.Hold h, FpRig rig, FpPose base, float[] r, float[] l, Limits lim) {
		FpPose delta = FpPose.of(r[0], r[1], r[2], r[3], r[4], r[5]);
		float[] lRide = l == null ? null : new float[] {0f, 0f, 0f, 0f, 0f, 0f, l[6]};
		float kr = solve(k -> margin(h, rig, base.plus(delta.scaled(k)), base, lRide, lim));
		FpPose pose = base.plus(delta.scaled(kr));
		float kl = 1f;
		if (l != null && h.left()) {
			kl = solve(k -> {
				float[] s = {l[0] * k, l[1] * k, l[2] * k, l[3] * k, l[4] * k, l[5] * k, l[6]};
				return leftMargin(h, pose, base, s, lim);
			});
		}
		return new Factors(kr, kl);
	}
}
