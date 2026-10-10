package dev.minebleach.reiatsutest.core.fx;

/**
 * Closed-form petal positions of the Byakuya shikai swarm (VFX_STORYBOARD 4.1 to 4.3). Everything is a pure function of
 * {@code (seed, petal index, time)}: no per-petal state, identical on every client, late-join safe. Positions are offsets from the
 * owner's FEET in world axes (the orbit is rotationally symmetric, only the release stream and the attack ribbon need the owner yaw).
 * Time {@code t} is seconds since the swarm epoch (the release); a {@link Mode} has its own start {@code t0}.
 */
public final class SwarmMath {
	public enum Kind { IDLE, RELEASE, ATTACK, BARRIER }

	/** The current behaviour of the swarm. {@code aim} is relative to the owner's feet. */
	public static final class Mode {
		public final Kind kind;
		public final double t0;
		public double ax;
		public double ay;
		public double az;
		/** Swarm time at which a barrier collapses (infinity while it stands). */
		public double collapseAt = Double.POSITIVE_INFINITY;

		public Mode(Kind kind, double t0) {
			this.kind = kind;
			this.t0 = t0;
		}

		public Mode aim(double x, double y, double z) {
			ax = x;
			ay = y;
			az = z;
			return this;
		}
	}

	/** Per petal seeded parameters, computed once per swarm. */
	public static final class Slots {
		public final long seed;
		public final int n;
		final float[] u;
		final float[] v;
		final float[] th;
		final float[] ph;
		final float[] key;
		final float[] uh;
		final float[] r2;
		final float[] g1;
		final float[] g2;
		final float[] sx;
		final float[] sy;
		final float[] sz;
		final float[] cone;
		final float[] coneAz;
		/** Tumble axis (unit), rate (rad/s) and phase of every petal, for the renderer. */
		public final float[] tx;
		public final float[] ty;
		public final float[] tz;
		public final float[] tumbleRate;
		public final float[] tumblePhase;

		public Slots(long seed, int n) {
			this.seed = seed;
			this.n = n;
			u = new float[n];
			v = new float[n];
			th = new float[n];
			ph = new float[n];
			key = new float[n];
			uh = new float[n];
			r2 = new float[n];
			g1 = new float[n];
			g2 = new float[n];
			sx = new float[n];
			sy = new float[n];
			sz = new float[n];
			cone = new float[n];
			coneAz = new float[n];
			tx = new float[n];
			ty = new float[n];
			tz = new float[n];
			tumbleRate = new float[n];
			tumblePhase = new float[n];
			for (int i = 0; i < n; i++) {
				u[i] = (float) FxHash.unit(seed, i, 1);
				v[i] = (float) FxHash.unit(seed, i, 2);
				th[i] = (float) (FxHash.unit(seed, i, 3) * Math.PI * 2);
				ph[i] = (float) (FxHash.unit(seed, i, 4) * Math.PI * 2);
				key[i] = (float) FxHash.unit(seed, i, 6);
				uh[i] = (float) FxHash.unit(seed, i, 7);
				r2[i] = (float) FxHash.unit(seed, i, 8);
				double a = Math.sqrt(-2 * Math.log(1 - FxHash.unit(seed, i, 10)));
				double b = FxHash.unit(seed, i, 11) * Math.PI * 2;
				g1[i] = (float) (a * Math.cos(b));
				g2[i] = (float) (a * Math.sin(b));
				double cz = FxHash.signed(seed, i, 12);
				double az = FxHash.unit(seed, i, 13) * Math.PI * 2;
				double rr = Math.sqrt(1 - cz * cz);
				sx[i] = (float) (rr * Math.cos(az));
				sy[i] = (float) cz;
				sz[i] = (float) (rr * Math.sin(az));
				cone[i] = (float) Math.sqrt(FxHash.unit(seed, i, 14));
				coneAz[i] = (float) (FxHash.unit(seed, i, 15) * Math.PI * 2);
				double ux = FxHash.signed(seed, i, 16);
				double uy = FxHash.signed(seed, i, 17);
				double uz = FxHash.signed(seed, i, 18);
				double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
				if (l < 1e-3) {
					ux = 0;
					uy = 1;
					uz = 0;
					l = 1;
				}
				tx[i] = (float) (ux / l);
				ty[i] = (float) (uy / l);
				tz[i] = (float) (uz / l);
				tumbleRate[i] = (float) (4 + 6 * FxHash.unit(seed, i, 19));
				tumblePhase[i] = (float) (FxHash.unit(seed, i, 20) * Math.PI * 2);
			}
		}

		/** True for the 60 percent of petals that form the attack ribbon (4.2). */
		public boolean ribbon(int i) {
			return key[i] < 0.6f;
		}

		/** 0..1 rank inside the ribbon group (the leading petals have a small rank). */
		public float ribbonRank(int i) {
			return key[i] / 0.6f;
		}
	}

	public static final double RIBBON_SPEED = 30.0;
	public static final double ATTACK_RETURN_END = 2.2;
	public static final double BARRIER_RADIUS = 3.0;

	private SwarmMath() {
	}

	private static double smooth(double e0, double e1, double x) {
		double t = x <= e0 ? 0 : x >= e1 ? 1 : (x - e0) / (e1 - e0);
		return t * t * (3 - 2 * t);
	}

	private static double oc(double x) {
		x = Math.max(0, Math.min(1, x));
		double u = 1 - x;
		return 1 - u * u * u;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** Position of petal i at swarm time t in the given mode; {@code yaw} is the owner body yaw in radians (0 = +Z). */
	public static void position(Slots s, int i, double t, Mode m, double yaw, double[] out) {
		switch (m.kind) {
			case RELEASE -> release(s, i, t - m.t0, t, yaw, out);
			case ATTACK -> attack(s, i, t - m.t0, t, m, yaw, out);
			case BARRIER -> barrier(s, i, t - m.t0, t, m, out);
			default -> orbit(s, i, t, out);
		}
	}

	/** Weight of the NEW mode for the first moments after a mode change: the previous mode is blended out over this long. */
	public static double blendWeight(double sinceModeStart) {
		return smooth(0, 0.25, sinceModeStart);
	}

	/** Instance scale 0..1 (release launch ramp). */
	public static double scale(Slots s, int i, double t, Mode m) {
		if (m.kind == Kind.RELEASE) {
			double ti = 0.4 * i / Math.max(1, s.n);
			return smooth(0, 0.08, t - m.t0 - ti);
		}
		return 1.0;
	}

	// ------------------------------------------------------------------------------------------ idle orbit (4.1, t 0.5)

	public static void orbit(Slots s, int i, double t, double[] out) {
		double u = s.u[i];
		double w = 0.6 * 2 * Math.PI * (1.0 + 0.25 * (1.0 - u));
		double ang = s.th[i] + w * t;
		double r = 1.4 + 1.0 * u;
		double h = 0.2 + 1.3 * s.v[i] + 0.18 * Math.sin(2 * Math.PI * 0.35 * t + s.ph[i]);
		double nx = 0.12 * Math.sin(2 * Math.PI * 0.8 * t + s.ph[i]);
		double nz = 0.12 * Math.sin(2 * Math.PI * 0.8 * t + s.ph[i] * 1.7 + 1.0);
		out[0] = Math.cos(ang) * r + nx;
		out[1] = h;
		out[2] = Math.sin(ang) * r + nz;
	}

	/** Feet relative position of the hand (right 0.35, up 1.15, forward 0.45 of the body yaw). */
	public static void hand(double yaw, double[] out) {
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		out[0] = rx * 0.35 + fx * 0.45;
		out[1] = 1.15;
		out[2] = rz * 0.35 + fz * 0.45;
	}

	// ------------------------------------------------------------------------------------------ release / Chire (4.1)

	private static void release(Slots s, int i, double tm, double t, double yaw, double[] out) {
		double ti = 0.4 * i / Math.max(1, s.n);
		double tau = tm - ti;
		double[] hand = new double[3];
		hand(yaw, hand);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		double tipX = hand[0] + fx * 0.25;
		double tipY = hand[1] + 0.1;
		double tipZ = hand[2] + fz * 0.25;
		if (tau < 0) {
			out[0] = tipX;
			out[1] = tipY;
			out[2] = tipZ;
			return;
		}
		if (tau >= 0.5) {
			orbit(s, i, t, out);
			return;
		}
		// 25 degree cone around forward (half angle 12.5), the stream
		double a = Math.toRadians(12.5) * s.cone[i];
		double ca = Math.cos(a);
		double sa = Math.sin(a);
		double cb = Math.cos(s.coneAz[i]);
		double sb = Math.sin(s.coneAz[i]);
		double dx = fx * ca + (rx * cb) * sa;
		double dy = 0.12 * ca + sb * sa;
		double dz = fz * ca + (rz * cb) * sa;
		double dist = 8.0 * (1 - Math.exp(-1.451 * tau)) / 1.451;
		double sx = tipX + dx * dist;
		double sy = tipY + dy * dist;
		double sz = tipZ + dz * dist;
		double w = smooth(0.2, 0.5, tau);
		double[] o = new double[3];
		orbit(s, i, t, o);
		out[0] = lerp(sx, o[0], w);
		out[1] = lerp(sy, o[1], w);
		out[2] = lerp(sz, o[2], w);
	}

	// ------------------------------------------------------------------------------------------ attack mode (4.2)

	private static void rotate(double kx, double ky, double kz, double ang, double vx, double vy, double vz, double[] out) {
		double c = Math.cos(ang);
		double sn = Math.sin(ang);
		double dot = kx * vx + ky * vy + kz * vz;
		out[0] = vx * c + (ky * vz - kz * vy) * sn + kx * dot * (1 - c);
		out[1] = vy * c + (kz * vx - kx * vz) * sn + ky * dot * (1 - c);
		out[2] = vz * c + (kx * vy - ky * vx) * sn + kz * dot * (1 - c);
	}

	/** Position on the envelope sphere around the aim (radius 1.5, 3 rev/s, then the dense core of radius 0.9). */
	private static void envelop(Slots s, int i, double tm, Mode m, double[] out) {
		int k = i % 3;
		double kx = k == 1 ? 0.7071 : 0;
		double ky = k == 0 ? 1 : 0.7071;
		double kz = k == 2 ? 0.7071 : 0;
		double sign = (i & 1) == 0 ? 1 : -1;
		double[] d = new double[3];
		rotate(kx, ky, kz, sign * 2 * Math.PI * 3.0 * tm, s.sx[i], s.sy[i], s.sz[i], d);
		double core = smooth(1.0, 1.25, tm);
		double rad = lerp(1.5, 0.9, core) * (0.72 + 0.28 * s.r2[i]);
		out[0] = m.ax + d[0] * rad;
		out[1] = m.ay + d[1] * rad;
		out[2] = m.az + d[2] * rad;
	}

	private static void attack(Slots s, int i, double tm, double t, Mode m, double yaw, double[] out) {
		double[] o = new double[3];
		orbit(s, i, t, o);
		if (!s.ribbon(i) || tm < 0) {
			out[0] = o[0];
			out[1] = o[1];
			out[2] = o[2];
			return;
		}
		double[] h = new double[3];
		hand(yaw, h);
		double ex = m.ax - h[0];
		double ey = m.ay - h[1];
		double ez = m.az - h[2];
		double len = Math.max(2.0, Math.sqrt(ex * ex + ey * ey + ez * ez));
		double r = s.ribbonRank(i);
		double dep = 0.2 + 0.15 * r;
		double flight = len / RIBBON_SPEED;
		double arrive = dep + flight;
		double ret = 1.5 + 0.15 * r;
		if (tm < dep) {
			out[0] = o[0];
			out[1] = o[1];
			out[2] = o[2];
			return;
		}
		double[] p = new double[3];
		if (tm < arrive) {
			ribbonPoint(s, i, h, m, len, (tm - dep) / flight, p);
			double w = smooth(0, 0.1, tm - dep);
			out[0] = lerp(o[0], p[0], w);
			out[1] = lerp(o[1], p[1], w);
			out[2] = lerp(o[2], p[2], w);
			return;
		}
		envelop(s, i, Math.min(tm, ret), m, p);
		if (tm < arrive + 0.15) {
			double[] b = new double[3];
			ribbonPoint(s, i, h, m, len, 1.0, b);
			double w = smooth(arrive, arrive + 0.15, tm);
			p[0] = lerp(b[0], p[0], w);
			p[1] = lerp(b[1], p[1], w);
			p[2] = lerp(b[2], p[2], w);
		}
		if (tm < ret) {
			out[0] = p[0];
			out[1] = p[1];
			out[2] = p[2];
			return;
		}
		double q = smooth(ret, ret + 0.55, tm);
		out[0] = lerp(p[0], o[0], q);
		out[1] = lerp(p[1], o[1], q) + 1.2 * Math.sin(Math.PI * q) * (0.4 + 0.6 * s.u[i]);
		out[2] = lerp(p[2], o[2], q);
	}

	private static void ribbonPoint(Slots s, int i, double[] h, Mode m, double len, double sParam, double[] out) {
		double cx = (h[0] + m.ax) * 0.5;
		double cy = (h[1] + m.ay) * 0.5 + 0.15 * len + 0.5;
		double cz = (h[2] + m.az) * 0.5;
		double a = 1 - sParam;
		double bx = a * a * h[0] + 2 * a * sParam * cx + sParam * sParam * m.ax;
		double by = a * a * h[1] + 2 * a * sParam * cy + sParam * sParam * m.ay;
		double bz = a * a * h[2] + 2 * a * sParam * cz + sParam * sParam * m.az;
		double tx = 2 * a * (cx - h[0]) + 2 * sParam * (m.ax - cx);
		double ty = 2 * a * (cy - h[1]) + 2 * sParam * (m.ay - cy);
		double tz = 2 * a * (cz - h[2]) + 2 * sParam * (m.az - cz);
		double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
		if (tl < 1e-6) {
			tx = 1;
			ty = 0;
			tz = 0;
			tl = 1;
		}
		tx /= tl;
		ty /= tl;
		tz /= tl;
		// e1 = t x up
		double e1x = -tz;
		double e1y = 0;
		double e1z = tx;
		double el = Math.sqrt(e1x * e1x + e1z * e1z);
		if (el < 1e-4) {
			e1x = 1;
			e1z = 0;
			el = 1;
		}
		e1x /= el;
		e1z /= el;
		double e2x = ty * e1z - tz * e1y;
		double e2y = tz * e1x - tx * e1z;
		double e2z = tx * e1y - ty * e1x;
		double band = 0.4 * (0.3 + 0.7 * Math.sin(Math.PI * sParam));
		out[0] = bx + (e1x * s.g1[i] + e2x * s.g2[i]) * band;
		out[1] = by + (e1y * s.g1[i] + e2y * s.g2[i]) * band;
		out[2] = bz + (e1z * s.g1[i] + e2z * s.g2[i]) * band;
	}

	// ------------------------------------------------------------------------------------------ barrier / dome (4.3)

	/** The dome position (hemisphere radius 3, 4 layers of 2.64 + 0.12 k, alternating 0.8 rev/s) of petal i at barrier time tm. */
	public static void dome(Slots s, int i, double tm, double[] out) {
		int k = i & 3;
		double rk = 2.64 + 0.12 * k;
		double dir = (k & 1) == 0 ? 1 : -1;
		double e = oc((tm - 0.3 - 0.3 * s.r2[i]) / 0.5);
		double uh = s.uh[i] * e;
		double rho = Math.sqrt(Math.max(0, 1 - uh * uh)) * rk;
		double ang = s.th[i] + dir * 0.8 * 2 * Math.PI * tm + (1 - e) * 2 * Math.PI * 1.2;
		double breathe = 1.0 + 0.02 * Math.sin(2 * Math.PI * 0.5 * tm + s.ph[i]);
		out[0] = Math.cos(ang) * rho * breathe;
		out[1] = rk * uh * breathe + 0.05;
		out[2] = Math.sin(ang) * rho * breathe;
	}

	private static void barrier(Slots s, int i, double tm, double t, Mode m, double[] out) {
		double[] o = new double[3];
		orbit(s, i, t, o);
		if (tm < 0) {
			out[0] = o[0];
			out[1] = o[1];
			out[2] = o[2];
			return;
		}
		double tc = m.collapseAt - m.t0; // collapse in barrier time
		double[] d = new double[3];
		double tmEff = Math.min(tm, tc);
		dome(s, i, tmEff, d);
		// contraction toward the player for the first 0.3 s, then out to the dome
		double[] c = {o[0] * 0.45, o[1] * 0.8 + 0.2, o[2] * 0.45};
		if (tmEff < 0.3) {
			double q = smooth(0, 0.3, tmEff);
			d[0] = lerp(o[0], c[0], q);
			d[1] = lerp(o[1], c[1], q);
			d[2] = lerp(o[2], c[2], q);
		} else {
			double q = smooth(0.3, 0.7, tmEff);
			double[] dd = {d[0], d[1], d[2]};
			d[0] = lerp(c[0], dd[0], q);
			d[1] = lerp(c[1], dd[1], q);
			d[2] = lerp(c[2], dd[2], q);
		}
		if (tm <= tc) {
			out[0] = d[0];
			out[1] = d[1];
			out[2] = d[2];
			return;
		}
		// collapse: drop with gravity, then rush back to the orbit
		double tau = tm - tc;
		double y = Math.max(0.05, d[1] - 0.5 * 10.0 * tau * tau);
		double w = smooth(0.25, 0.85, tau);
		out[0] = lerp(d[0], o[0], w);
		out[1] = lerp(y, o[1], w);
		out[2] = lerp(d[2], o[2], w);
	}
}
