package dev.minebleach.reiatsutest.core.combat;

/**
 * A rigid pose of the first person main hand together with the sword it holds, in the right-handed hand frame of the item
 * renderer (x to the right, y up, z towards the camera, so "forward" is negative z). {@code pos} is a shift in blocks, {@code rot}
 * a rotation in degrees (x, y, z, applied like the vanilla display transform: {@code Rx * Ry * Rz}) about a pivot that the caller
 * chooses (the grip). The left hand mirrors x of {@code pos} and y, z of {@code rot}; that is done by the caller.
 *
 * <p>Immutable by convention: the arrays are never modified after construction.
 */
public record FpPose(float[] pos, float[] rot) {
	public static final FpPose ZERO = new FpPose(new float[3], new float[3]);
	private static final float DEG = (float) (Math.PI / 180.0);

	public static FpPose of(float px, float py, float pz, float rx, float ry, float rz) {
		return new FpPose(new float[] {px, py, pz}, new float[] {rx, ry, rz});
	}

	public static FpPose of(float[] pos, float[] rot) {
		return new FpPose(pos.clone(), rot.clone());
	}

	public FpPose lerp(FpPose b, float t) {
		float[] p = new float[3];
		float[] r = new float[3];
		for (int i = 0; i < 3; i++) {
			p[i] = pos[i] + (b.pos[i] - pos[i]) * t;
			r[i] = rot[i] + (b.rot[i] - rot[i]) * t;
		}
		return new FpPose(p, r);
	}

	public FpPose plus(FpPose b) {
		float[] p = new float[3];
		float[] r = new float[3];
		for (int i = 0; i < 3; i++) {
			p[i] = pos[i] + b.pos[i];
			r[i] = rot[i] + b.rot[i];
		}
		return new FpPose(p, r);
	}

	public FpPose scaled(float k) {
		return new FpPose(new float[] {pos[0] * k, pos[1] * k, pos[2] * k}, new float[] {rot[0] * k, rot[1] * k, rot[2] * k});
	}

	/** Mirror for the left hand: x of the shift, y and z of the rotation change sign. */
	public FpPose mirrored() {
		return new FpPose(new float[] {-pos[0], pos[1], pos[2]}, new float[] {rot[0], -rot[1], -rot[2]});
	}

	public boolean isNearZero() {
		for (int i = 0; i < 3; i++) {
			if (Math.abs(pos[i]) > 1e-4f || Math.abs(rot[i]) > 1e-3f) {
				return false;
			}
		}
		return true;
	}

	/** Rotation quaternion (x, y, z, w) of {@link #rot}, same convention as JOML {@code rotationXYZ} (vanilla display transform). */
	public float[] quaternion() {
		float[] qx = axisAngle(1, 0, 0, rot[0] * DEG);
		float[] qy = axisAngle(0, 1, 0, rot[1] * DEG);
		float[] qz = axisAngle(0, 0, 1, rot[2] * DEG);
		return mul(mul(qx, qy), qz);
	}

	/** The point {@code p} of the held sword (hand frame at rest) after the pose, rotating about {@code pivot}: {@code pos + pivot + R (p - pivot)}. */
	public float[] apply(float[] pivot, float[] p) {
		float[] r = rotate(quaternion(), new float[] {p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]});
		return new float[] {pos[0] + pivot[0] + r[0], pos[1] + pivot[1] + r[1], pos[2] + pivot[2] + r[2]};
	}

	/** Direction {@code d} (not a point) after the rotation of the pose. */
	public float[] applyDir(float[] d) {
		return rotate(quaternion(), d);
	}

	static float[] axisAngle(float x, float y, float z, float a) {
		float s = (float) Math.sin(a * 0.5f);
		return new float[] {x * s, y * s, z * s, (float) Math.cos(a * 0.5f)};
	}

	static float[] mul(float[] a, float[] b) {
		return new float[] {
			a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
			a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
			a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
			a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	static float[] rotate(float[] q, float[] v) {
		float x = q[0];
		float y = q[1];
		float z = q[2];
		float w = q[3];
		// v' = v + 2 w (u x v) + 2 u x (u x v), u = (x, y, z)
		float cx = y * v[2] - z * v[1];
		float cy = z * v[0] - x * v[2];
		float cz = x * v[1] - y * v[0];
		float dx = y * cz - z * cy;
		float dy = z * cx - x * cz;
		float dz = x * cy - y * cx;
		return new float[] {v[0] + 2f * (w * cx + dx), v[1] + 2f * (w * cy + dy), v[2] + 2f * (w * cz + dz)};
	}
}
