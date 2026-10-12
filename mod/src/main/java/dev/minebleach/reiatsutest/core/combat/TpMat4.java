package dev.minebleach.reiatsutest.core.combat;

/**
 * Tiny affine 4x4 matrix (row major, double) for the third person pose maths ({@link TpPose}). Pure Java: the pose is solved in
 * the same model space the player model uses (x toward the left of the character, y down, z toward the back, blocks), and the
 * chain of matrices is the one vanilla builds for a held item, so the maths can be unit tested without Minecraft.
 */
public final class TpMat4 {
	final double[] m;

	private TpMat4(double[] m) {
		this.m = m;
	}

	public static TpMat4 identity() {
		return new TpMat4(new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
	}

	public static TpMat4 translation(double x, double y, double z) {
		TpMat4 r = identity();
		r.m[3] = x;
		r.m[7] = y;
		r.m[11] = z;
		return r;
	}

	public static TpMat4 scale(double s) {
		TpMat4 r = identity();
		r.m[0] = s;
		r.m[5] = s;
		r.m[10] = s;
		return r;
	}

	public static TpMat4 rotX(double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new TpMat4(new double[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1});
	}

	public static TpMat4 rotY(double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new TpMat4(new double[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1});
	}

	public static TpMat4 rotZ(double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new TpMat4(new double[] {c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
	}

	/** {@code Rz(roll) * Ry(yaw) * Rx(pitch)}, the order of {@code ModelPart#rotate} (the pitch turns first). */
	public static TpMat4 rotZYX(double roll, double yaw, double pitch) {
		return rotZ(roll).mul(rotY(yaw)).mul(rotX(pitch));
	}

	/** {@code Rx * Ry * Rz}, the order of JOML {@code rotationXYZ} used by the item display transform (Z turns first). */
	public static TpMat4 rotXYZ(double rx, double ry, double rz) {
		return rotX(rx).mul(rotY(ry)).mul(rotZ(rz));
	}

	/** Rotation from a unit quaternion (x, y, z, w). */
	public static TpMat4 fromQuat(double[] q) {
		double x = q[0];
		double y = q[1];
		double z = q[2];
		double w = q[3];
		return new TpMat4(new double[] {
			1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0,
			2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0,
			2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0,
			0, 0, 0, 1});
	}

	/** Rotation with the given columns (the images of the x, y and z axes). */
	public static TpMat4 fromAxes(double[] x, double[] y, double[] z) {
		return new TpMat4(new double[] {x[0], y[0], z[0], 0, x[1], y[1], z[1], 0, x[2], y[2], z[2], 0, 0, 0, 0, 1});
	}

	public TpMat4 mul(TpMat4 o) {
		double[] r = new double[16];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double s = 0;
				for (int k = 0; k < 4; k++) {
					s += m[i * 4 + k] * o.m[k * 4 + j];
				}
				r[i * 4 + j] = s;
			}
		}
		return new TpMat4(r);
	}

	public TpMat4 copy() {
		return new TpMat4(m.clone());
	}

	public double[] point(double x, double y, double z) {
		return new double[] {
			m[0] * x + m[1] * y + m[2] * z + m[3],
			m[4] * x + m[5] * y + m[6] * z + m[7],
			m[8] * x + m[9] * y + m[10] * z + m[11]};
	}

	public double[] point(double[] v) {
		return point(v[0], v[1], v[2]);
	}

	public double[] dir(double x, double y, double z) {
		return new double[] {
			m[0] * x + m[1] * y + m[2] * z,
			m[4] * x + m[5] * y + m[6] * z,
			m[8] * x + m[9] * y + m[10] * z};
	}

	public double[] translationPart() {
		return new double[] {m[3], m[7], m[11]};
	}

	/** Uniform scale of the upper 3x3 (length of the first column). */
	public double scaleFactor() {
		return Math.sqrt(m[0] * m[0] + m[4] * m[4] + m[8] * m[8]);
	}

	/** Inverse of an affine matrix (rotation times uniform or non uniform scale, plus translation). */
	public TpMat4 inverse() {
		double a = m[0];
		double b = m[1];
		double c = m[2];
		double d = m[4];
		double e = m[5];
		double f = m[6];
		double g = m[8];
		double h = m[9];
		double i = m[10];
		double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
		double id = 1.0 / det;
		double[] r = new double[16];
		r[0] = (e * i - f * h) * id;
		r[1] = (c * h - b * i) * id;
		r[2] = (b * f - c * e) * id;
		r[4] = (f * g - d * i) * id;
		r[5] = (a * i - c * g) * id;
		r[6] = (c * d - a * f) * id;
		r[8] = (d * h - e * g) * id;
		r[9] = (b * g - a * h) * id;
		r[10] = (a * e - b * d) * id;
		r[3] = -(r[0] * m[3] + r[1] * m[7] + r[2] * m[11]);
		r[7] = -(r[4] * m[3] + r[5] * m[7] + r[6] * m[11]);
		r[11] = -(r[8] * m[3] + r[9] * m[7] + r[10] * m[11]);
		r[15] = 1;
		return new TpMat4(r);
	}

	/** Unit quaternion (x, y, z, w) of the rotation part (the columns are normalised first, so a uniform scale is harmless). */
	public double[] quat() {
		double s = scaleFactor();
		double m00 = m[0] / s;
		double m01 = m[1] / s;
		double m02 = m[2] / s;
		double m10 = m[4] / s;
		double m11 = m[5] / s;
		double m12 = m[6] / s;
		double m20 = m[8] / s;
		double m21 = m[9] / s;
		double m22 = m[10] / s;
		double tr = m00 + m11 + m22;
		double x;
		double y;
		double z;
		double w;
		if (tr > 0) {
			double k = Math.sqrt(tr + 1.0) * 2;
			w = 0.25 * k;
			x = (m21 - m12) / k;
			y = (m02 - m20) / k;
			z = (m10 - m01) / k;
		} else if (m00 > m11 && m00 > m22) {
			double k = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
			w = (m21 - m12) / k;
			x = 0.25 * k;
			y = (m01 + m10) / k;
			z = (m02 + m20) / k;
		} else if (m11 > m22) {
			double k = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
			w = (m02 - m20) / k;
			x = (m01 + m10) / k;
			y = 0.25 * k;
			z = (m12 + m21) / k;
		} else {
			double k = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
			w = (m10 - m01) / k;
			x = (m02 + m20) / k;
			y = (m12 + m21) / k;
			z = 0.25 * k;
		}
		double n = Math.sqrt(x * x + y * y + z * z + w * w);
		return new double[] {x / n, y / n, z / n, w / n};
	}

	public static double[] slerp(double[] a, double[] b, double t) {
		double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
		double[] bb = b;
		if (dot < 0) {
			bb = new double[] {-b[0], -b[1], -b[2], -b[3]};
			dot = -dot;
		}
		double ka;
		double kb;
		if (dot > 0.9995) {
			ka = 1 - t;
			kb = t;
		} else {
			double th = Math.acos(dot);
			double sn = Math.sin(th);
			ka = Math.sin((1 - t) * th) / sn;
			kb = Math.sin(t * th) / sn;
		}
		double[] r = {ka * a[0] + kb * bb[0], ka * a[1] + kb * bb[1], ka * a[2] + kb * bb[2], ka * a[3] + kb * bb[3]};
		double n = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + r[3] * r[3]);
		return new double[] {r[0] / n, r[1] / n, r[2] / n, r[3] / n};
	}

	/** The mirror {@code Mx * this * Mx} across the plane x = 0 (left and right handed poses are mirror images). */
	public TpMat4 mirrorX() {
		TpMat4 r = copy();
		// conjugation by diag(-1, 1, 1): entries with exactly one of the indices 0 (x) flip, translation x flips
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				if ((i == 0) != (j == 0)) {
					r.m[i * 4 + j] = -r.m[i * 4 + j];
				}
			}
		}
		r.m[3] = -r.m[3];
		return r;
	}
}
