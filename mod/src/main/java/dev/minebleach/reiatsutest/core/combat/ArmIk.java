package dev.minebleach.reiatsutest.core.combat;

/**
 * Placement of the vanilla player arm model in first person (fp2). Instead of rotating the arm about odd hand frame pivots (which let the arm
 * lie across the screen like a plank in the earlier rounds), the arm is put where its FIST has to be (a point of the hilt or of the scabbard) and
 * its long axis points to a shoulder point behind and below the camera, so every arm always comes in from the body. Pure maths in camera space
 * (x right, y up, -z forward; the camera is at the origin), right hander layout, a left arm is the mirror image.
 *
 * <p>The vanilla chain of {@code HeldItemRenderer.renderArmHoldingItem} (as {@code FirstPersonHand.renderVanillaArm} replays it) fixes the
 * natural look of the arm: its roll and its direction at rest. The solver keeps that roll (minimal rotation from the rest direction to the wanted
 * one), optionally squares the fist to the blade (the hilt goes through the middle of a face of the 4 x 4 pixel fist cube instead of a corner), and
 * stretches the arm along its axis when the fist is further from the shoulder than at rest, so the far end never hangs in the air inside the picture.
 *
 * <p>Arm model space is the model space of {@code PlayerEntityRenderer.renderRightArm / renderLeftArm}: x across, y along the arm to the HAND
 * (y down in entity model convention), blocks. The fist is the centre of the hand end ({@link #fistLocal}).
 */
public final class ArmIk {
	/** Distance from the rest fist to the shoulder point (blocks). */
	public static final float SHOULDER_DIST = 1.0f;
	/** Length of the arm from the fist centre to its far end (blocks): 12 pixel arm, the fist centre 2 pixels from the hand end. */
	public static final float ARM_LEN = 10.0f / 16.0f;
	/** The resting main hand frame in camera space (right hander). */
	public static final float[] HAND_BASE = {0.56f, -0.52f, -0.72f};

	/** Result: {@code translate(t) rotate(q) scale(1, stretch, 1) translate(-fistLocal)} puts the arm model where it belongs (camera space). */
	public record Placement(float[] t, float[] q, float stretch, float[] fistLocal) {
	}

	private ArmIk() {
	}

	// ------------------------------------------------------------------ vanilla arm

	/** The vanilla chain (hand frame of a held item of that side, arm model space to hand frame), row major 4x4. */
	public static double[] vanillaChain(boolean left) {
		double f = left ? -1.0 : 1.0;
		double[] m = identity();
		m = mul(m, translate(f * 0.08, -0.08, 0.0));
		m = mul(m, rotY(f * 45.0));
		m = mul(m, translate(f * -1.0, 3.6, 3.5));
		m = mul(m, rotZ(f * 120.0));
		m = mul(m, rotX(200.0));
		m = mul(m, rotY(f * -135.0));
		m = mul(m, translate(f * 5.6, 0.0, 0.0));
		return m;
	}

	/** Fist centre in arm model space (x across, y along, z) for the wide or the slim arm of that side. */
	public static float[] fistLocal(boolean left, boolean slim) {
		// right arm part: pivot (-5, 2 or 2.5, 0) px, box x (-3 | -2), width 4 | 3, height 12, depth 4; the left arm mirrors x (box x -1)
		double px = left ? 5.0 : -5.0;
		double py = slim ? 2.5 : 2.0;
		double bx = left ? -1.0 : (slim ? -2.0 : -3.0);
		double bw = slim ? 3.0 : 4.0;
		double cx = (px + bx + bw / 2.0) / 16.0;
		double cy = (py - 2.0 + 12.0 - 2.0) / 16.0;
		return new float[] {(float) cx, (float) cy, 0f};
	}

	/** Direction from the fist to the far end of the arm at rest, camera space (the rest direction of the vanilla arm). */
	public static float[] restDir(boolean left) {
		double[] m = vanillaChain(left);
		return norm(new float[] {(float) -m[1], (float) -m[5], (float) -m[9]}); // R * (0, -1, 0)
	}

	/** Fist of the vanilla arm in camera space at rest (equip finished): the main hand frame times the chain at the fist centre. */
	public static float[] restFist(boolean left, boolean slim) {
		double[] m = mul(translate((left ? -1 : 1) * HAND_BASE[0], HAND_BASE[1], HAND_BASE[2]), vanillaChain(left));
		float[] fl = fistLocal(left, slim);
		return transform(m, fl);
	}

	/** The shoulder point of that arm in camera space: {@link #SHOULDER_DIST} behind the rest fist (wide arm) along the rest direction of the arm. */
	public static float[] shoulder(boolean left) {
		float[] f = restFist(left, false);
		float[] d = restDir(left);
		return new float[] {f[0] + d[0] * SHOULDER_DIST, f[1] + d[1] * SHOULDER_DIST, f[2] + d[2] * SHOULDER_DIST};
	}

	/** Lower corner of the arm box in arm model space (blocks). */
	public static float[] boxMin(boolean left, boolean slim) {
		double px = left ? 5.0 : -5.0;
		double py = slim ? 2.5 : 2.0;
		double bx = left ? -1.0 : (slim ? -2.0 : -3.0);
		return new float[] {(float) ((px + bx) / 16.0), (float) ((py - 2.0) / 16.0), -2f / 16f};
	}

	/** Upper corner of the arm box in arm model space (blocks). */
	public static float[] boxMax(boolean left, boolean slim) {
		float[] lo = boxMin(left, slim);
		double bw = slim ? 3.0 : 4.0;
		return new float[] {(float) (lo[0] + bw / 16.0), lo[1] + 12f / 16f, 2f / 16f};
	}

	/** A point of the arm model space (blocks) in camera space for a placement. */
	public static float[] place(Placement p, float[] local) {
		double x = p.q()[0];
		double y = p.q()[1];
		double z = p.q()[2];
		double w = p.q()[3];
		double lx = local[0] - p.fistLocal()[0];
		double ly = (local[1] - p.fistLocal()[1]) * p.stretch();
		double lz = local[2] - p.fistLocal()[2];
		double rx = (1 - 2 * (y * y + z * z)) * lx + 2 * (x * y - z * w) * ly + 2 * (x * z + y * w) * lz;
		double ry = 2 * (x * y + z * w) * lx + (1 - 2 * (x * x + z * z)) * ly + 2 * (y * z - x * w) * lz;
		double rz = 2 * (x * z - y * w) * lx + 2 * (y * z + x * w) * ly + (1 - 2 * (x * x + y * y)) * lz;
		return new float[] {(float) (p.t()[0] + rx), (float) (p.t()[1] + ry), (float) (p.t()[2] + rz)};
	}

	// ------------------------------------------------------------------ the solver

	/**
	 * @param fist camera space point where the fist centre has to be
	 * @param shoulder camera space point the arm points to (see {@link #shoulder})
	 * @param blade unit direction of the held blade in camera space to square the fist to, or null
	 * @param rollDeg extra roll about the arm axis, degrees
	 * @param maxStretch upper limit of the stretch factor (1 = never stretch)
	 */
	public static Placement place(boolean left, boolean slim, float[] fist, float[] shoulder, float[] blade, float rollDeg, float maxStretch) {
		double[] rv = vanillaChain(left);
		double[] r = {rv[0], rv[1], rv[2], rv[4], rv[5], rv[6], rv[8], rv[9], rv[10]};
		double[] av = {-r[1], -r[4], -r[7]};
		double[] d = {shoulder[0] - fist[0], shoulder[1] - fist[1], shoulder[2] - fist[2]};
		double dist = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		d = unit3(d);
		double[] q = mul3(rotTo(av, d), r);
		if (blade != null) {
			double[] b = {blade[0], blade[1], blade[2]};
			double dot = b[0] * d[0] + b[1] * d[1] + b[2] * d[2];
			double[] bp = {b[0] - d[0] * dot, b[1] - d[1] * dot, b[2] - d[2] * dot};
			double l = Math.sqrt(bp[0] * bp[0] + bp[1] * bp[1] + bp[2] * bp[2]);
			if (l > 1e-3) {
				bp = new double[] {bp[0] / l, bp[1] / l, bp[2] / l};
				double best = Double.NaN;
				double[][] axes = {{1, 0, 0}, {0, 0, 1}};
				for (double[] ax : axes) {
					for (int sg = -1; sg <= 1; sg += 2) {
						double[] v = mulv3(q, new double[] {ax[0] * sg, ax[1] * sg, ax[2] * sg});
						double[] c = cross(v, bp);
						double ang = Math.toDegrees(Math.atan2(c[0] * d[0] + c[1] * d[1] + c[2] * d[2], v[0] * bp[0] + v[1] * bp[1] + v[2] * bp[2]));
						if (Double.isNaN(best) || Math.abs(ang) < Math.abs(best)) {
							best = ang;
						}
					}
				}
				q = mul3(axisAngle(d, best), q);
			}
		}
		if (rollDeg != 0f) {
			q = mul3(axisAngle(d, rollDeg), q);
		}
		double rest = SHOULDER_DIST;
		double k = Math.max(1.0, Math.min(maxStretch, 1.0 + Math.max(0.0, dist - rest) / ARM_LEN));
		return new Placement(fist.clone(), quat(q), (float) k, fistLocal(left, slim));
	}

	// ------------------------------------------------------------------ small matrix helpers (row major)

	static double[] identity() {
		return new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	static double[] translate(double x, double y, double z) {
		return new double[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
	}

	static double[] rotX(double deg) {
		double a = Math.toRadians(deg);
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new double[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
	}

	static double[] rotY(double deg) {
		double a = Math.toRadians(deg);
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new double[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
	}

	static double[] rotZ(double deg) {
		double a = Math.toRadians(deg);
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new double[] {c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	static double[] mul(double[] a, double[] b) {
		double[] r = new double[16];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double s = 0;
				for (int k = 0; k < 4; k++) {
					s += a[i * 4 + k] * b[k * 4 + j];
				}
				r[i * 4 + j] = s;
			}
		}
		return r;
	}

	static float[] transform(double[] m, float[] p) {
		return new float[] {
			(float) (m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3]),
			(float) (m[4] * p[0] + m[5] * p[1] + m[6] * p[2] + m[7]),
			(float) (m[8] * p[0] + m[9] * p[1] + m[10] * p[2] + m[11])};
	}

	private static double[] mul3(double[] a, double[] b) {
		double[] r = new double[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				double s = 0;
				for (int k = 0; k < 3; k++) {
					s += a[i * 3 + k] * b[k * 3 + j];
				}
				r[i * 3 + j] = s;
			}
		}
		return r;
	}

	private static double[] mulv3(double[] a, double[] v) {
		return new double[] {a[0] * v[0] + a[1] * v[1] + a[2] * v[2], a[3] * v[0] + a[4] * v[1] + a[5] * v[2], a[6] * v[0] + a[7] * v[1] + a[8] * v[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double[] unit3(double[] v) {
		double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new double[] {v[0] / l, v[1] / l, v[2] / l};
	}

	private static float[] norm(float[] v) {
		float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new float[] {v[0] / l, v[1] / l, v[2] / l};
	}

	/** Rotation about a unit axis by degrees (3x3, row major). */
	static double[] axisAngle(double[] u, double deg) {
		double a = Math.toRadians(deg);
		double c = Math.cos(a);
		double s = Math.sin(a);
		double C = 1 - c;
		double x = u[0];
		double y = u[1];
		double z = u[2];
		return new double[] {c + x * x * C, x * y * C - z * s, x * z * C + y * s, y * x * C + z * s, c + y * y * C, y * z * C - x * s,
			z * x * C - y * s, z * y * C + x * s, c + z * z * C};
	}

	/** Minimal rotation taking unit vector a to unit vector b (3x3). */
	static double[] rotTo(double[] a, double[] b) {
		double[] v = cross(a, b);
		double c = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
		if (c < -0.999999) {
			double[] p = cross(a, new double[] {1, 0, 0});
			if (p[0] * p[0] + p[1] * p[1] + p[2] * p[2] < 1e-12) {
				p = cross(a, new double[] {0, 1, 0});
			}
			return axisAngle(unit3(p), 180);
		}
		double k = 1.0 / (1.0 + c);
		double[] vx = {0, -v[2], v[1], v[2], 0, -v[0], -v[1], v[0], 0};
		double[] vx2 = mul3(vx, vx);
		double[] r = new double[9];
		for (int i = 0; i < 9; i++) {
			r[i] = (i % 4 == 0 ? 1.0 : 0.0) + vx[i] + vx2[i] * k;
		}
		return r;
	}

	/** Rotation matrix (3x3 row major) to a quaternion x, y, z, w. */
	static float[] quat(double[] m) {
		double tr = m[0] + m[4] + m[8];
		double x;
		double y;
		double z;
		double w;
		if (tr > 0) {
			double s = Math.sqrt(tr + 1.0) * 2;
			w = 0.25 * s;
			x = (m[7] - m[5]) / s;
			y = (m[2] - m[6]) / s;
			z = (m[3] - m[1]) / s;
		} else if (m[0] > m[4] && m[0] > m[8]) {
			double s = Math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2;
			w = (m[7] - m[5]) / s;
			x = 0.25 * s;
			y = (m[1] + m[3]) / s;
			z = (m[2] + m[6]) / s;
		} else if (m[4] > m[8]) {
			double s = Math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2;
			w = (m[2] - m[6]) / s;
			x = (m[1] + m[3]) / s;
			y = 0.25 * s;
			z = (m[5] + m[7]) / s;
		} else {
			double s = Math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2;
			w = (m[3] - m[1]) / s;
			x = (m[2] + m[6]) / s;
			y = (m[5] + m[7]) / s;
			z = 0.25 * s;
		}
		return new float[] {(float) x, (float) y, (float) z, (float) w};
	}
}
