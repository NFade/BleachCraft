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

	/**
	 * Interpolation of two poses: the shift linearly, the rotation by a spherical interpolation of the two orientations (the shortest great arc;
	 * a straight line between Euler angles bends and turns the blade through odd attitudes when the poses are far apart, fp2).
	 */
	public FpPose lerp(FpPose b, float t) {
		float[] p = new float[3];
		for (int i = 0; i < 3; i++) {
			p[i] = pos[i] + (b.pos[i] - pos[i]) * t;
		}
		return new FpPose(p, eulerOf(FpGeometry.slerp(quaternion(), b.quaternion(), t)));
	}

	/**
	 * Adds a delta: the shifts add, the delta rotation is applied after this rotation about the camera axes (the same as adding the Euler angles
	 * while the delta is small, and well behaved when the pose already is turned a lot).
	 */
	public FpPose plus(FpPose b) {
		float[] p = new float[3];
		for (int i = 0; i < 3; i++) {
			p[i] = pos[i] + b.pos[i];
		}
		boolean zero = Math.abs(b.rot[0]) < 1e-6f && Math.abs(b.rot[1]) < 1e-6f && Math.abs(b.rot[2]) < 1e-6f;
		if (zero) {
			return new FpPose(p, rot.clone());
		}
		return new FpPose(p, eulerOf(mul(b.quaternion(), quaternion())));
	}

	/** Euler angles (degrees x y z, {@code Rx * Ry * Rz}) of a unit quaternion (x, y, z, w). */
	public static float[] eulerOf(float[] q) {
		double x = q[0];
		double y = q[1];
		double z = q[2];
		double w = q[3];
		double r02 = 2 * (x * z + y * w);
		double a;
		double b;
		double c;
		if (Math.abs(r02) > 0.999999) {
			b = r02 > 0 ? Math.PI / 2 : -Math.PI / 2;
			// gimbal: only a + c (or a - c) is defined, put it all in a
			double r10 = 2 * (x * y + z * w);
			double r11 = 1 - 2 * (x * x + z * z);
			a = r02 > 0 ? Math.atan2(r10, r11) : Math.atan2(-r10, r11);
			c = 0;
		} else {
			b = Math.asin(r02);
			a = Math.atan2(-2 * (y * z - x * w), 1 - 2 * (x * x + y * y));
			c = Math.atan2(-2 * (x * y - z * w), 1 - 2 * (y * y + z * z));
		}
		return new float[] {(float) Math.toDegrees(a), (float) Math.toDegrees(b), (float) Math.toDegrees(c)};
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
