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
		/**
		 * Attack only: path of the server's flight head (world positions). When it has at least two samples the ribbon follows it
		 * (steerable swarm); otherwise the fixed closed-form flight to {@code aim} is used.
		 */
		public HeadTrack track;
		/** Seconds the ribbon is drawn behind the newest sample (room to interpolate between 10 Hz packets), and the spread of the ribbon behind the head. */
		public double trackDelay = 0.12;
		public double trackLag = 0.30;
		/** Owner feet in world axes of the frame being drawn: turns the world head path into feet relative offsets. */
		public double fx;
		public double fy;
		public double fz;
		/**
		 * Third person (vfx2): the resting swarm is a few dense petal ribbons that wrap the body ({@link #ribbonIdle}) instead of the loose orbit. The renderer sets it
		 * per frame (false for the owner's first person, which keeps the classic orbit); {@code vx, vy, vz} = owner velocity in blocks per second (the ribbon ends trail behind a moving owner).
		 */
		public boolean ribbonIdle;
		public double vx;
		public double vy;
		public double vz;

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

		public void feet(double x, double y, double z) {
			fx = x;
			fy = y;
			fz = z;
		}

		public void velocity(double x, double y, double z) {
			vx = x;
			vy = y;
			vz = z;
		}

		/** True when the attack ribbon follows a recorded server head path. */
		public boolean tracked() {
			return kind == Kind.ATTACK && track != null && track.size() >= 2;
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
			case RELEASE -> release(s, i, t - m.t0, t, m, yaw, out);
			case ATTACK -> attack(s, i, t - m.t0, t, m, yaw, out);
			case BARRIER -> barrier(s, i, t - m.t0, t, m, out);
			default -> idle(s, i, t, m, out);
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

	// ------------------------------------------------------------------------------------------ third person loops (vfx2, R12: figure eight / Moebius)

	/** Numbers of the third person petal loops (the client tunes them from fx_override.json {@code swarm.rb*}). */
	public static final class RibbonParams {
		/** Number of loops that weave around the body. */
		public int streams = 3;
		/** Height of the centre of the loops, radius of a loop around the body, how far the radius differs between loops (fraction). */
		public double hMid = 1.1;
		public double radius = 0.95;
		public double radiusVar = 0.10;
		/** Vertical amplitude of the weave: the loop rises and falls twice per turn, which makes the figure eight seen from the side. */
		public double amp = 0.55;
		/** Tilt of the loop plane in radians (alternating sign between loops, so the loops cross each other) and the turn of the whole pattern in revolutions per second. */
		public double tilt = 0.40;
		public double spin = 0.10;
		/** Half width and thickness of the band of petals; the band turns by {@link #twist} turns per loop (0.5 = a Moebius strip). */
		public double band = 0.14;
		public double thick = 0.04;
		public double twist = 0.5;
		/** Flow along the loop in loops per second, and the spread of the speed between petals (0.45 = from 55 to 145 percent). */
		public double flow = 0.18;
		public double flowVar = 0.45;
		/** Seconds that the lobes lag behind a moving owner. */
		public double lag = 0.35;
		/** Size of a petal as a multiple of the base size, per petal from min to max. */
		public double sizeMin = 0.8;
		public double sizeMax = 1.7;
	}

	public static final RibbonParams RIBBON = new RibbonParams();

	private static double ribbonPhase(Slots s, int i, double t) {
		RibbonParams p = RIBBON;
		double f = 1.0 + p.flowVar * (2.0 * s.r2[i] - 1.0);
		double x = s.u[i] + t * p.flow * f;
		return x - Math.floor(x);
	}

	/**
	 * Resting swarm in the third person: petal i flows along one of {@code streams} closed loops that go around the body and rise and fall twice per turn
	 * (a figure eight seen from the side). The petals sit in a flat band whose width turns by half a turn per loop, so it reads as a Moebius ribbon. The loops are
	 * tilted against each other and the whole pattern turns slowly. Pure function of (seed, i, t), feet relative.
	 */
	public static void ribbonIdle(Slots s, int i, double t, double[] out) {
		RibbonParams p = RIBBON;
		int k = i % Math.max(1, p.streams);
		double s01 = ribbonPhase(s, i, t);
		long sd = s.seed;
		double u = 2 * Math.PI * (s01 + k / (double) Math.max(1, p.streams) * 0.5);
		double r = p.radius * (1.0 + p.radiusVar * FxHash.signed(sd, k, 95));
		// the loop in its own frame: a circle with a vertical weave of twice the frequency
		double cx = Math.cos(u) * r;
		double cz = Math.sin(u) * r;
		double cy = Math.sin(2 * u + k) * p.amp;
		// the band: across the loop in the plane of (outward, up), turned by the twist
		double psi = 2 * Math.PI * p.twist * (u / (2 * Math.PI)) + k * 1.3;
		double w = s.g1[i] * p.band * 0.5;
		double th = s.g2[i] * p.thick * 0.5;
		double ox = Math.cos(u) * (Math.cos(psi) * w) + Math.cos(u) * th;
		double oz = Math.sin(u) * (Math.cos(psi) * w) + Math.sin(u) * th;
		double oy = Math.sin(psi) * w;
		double x = cx + ox;
		double y = cy + oy;
		double z = cz + oz;
		// tilt the loop about the x axis (alternating sign), then turn the pattern about the vertical axis
		double a = ((k & 1) == 0 ? 1.0 : -1.0) * p.tilt * (1.0 + 0.25 * FxHash.signed(sd, k, 91));
		double ca = Math.cos(a);
		double sa = Math.sin(a);
		double y2 = y * ca - z * sa;
		double z2 = y * sa + z * ca;
		double ang = 2 * Math.PI * (p.spin * t) + k * 2 * Math.PI / Math.max(1, p.streams) + 0.4 * FxHash.signed(sd, k, 92);
		double cb = Math.cos(ang);
		double sb = Math.sin(ang);
		out[0] = x * cb - z2 * sb;
		out[1] = Math.max(0.03, p.hMid + y2);
		out[2] = x * sb + z2 * cb;
	}

	/** Weight 0..1 of the lag behind a moving owner: 1 on the two lobes of the figure eight, 0 where the loop crosses. */
	private static double ribbonTrail(Slots s, int i, double t) {
		return 0.5 + 0.5 * Math.cos(4 * Math.PI * ribbonPhase(s, i, t));
	}

	/** Size factor of petal i in the loops: a fixed size per petal (small and large ones). The loops are closed, so nothing shrinks at an end. */
	public static double ribbonScale(Slots s, int i, double t, Mode m) {
		RibbonParams p = RIBBON;
		return p.sizeMin + (p.sizeMax - p.sizeMin) * s.uh[i];
	}

	/** The resting position of petal i: the third person ribbons when {@code m.ribbonIdle}, else the classic orbit (first person of the owner). */
	public static void idle(Slots s, int i, double t, Mode m, double[] out) {
		idle(s, i, t, m.ribbonIdle, m.vx, m.vy, m.vz, out);
	}

	/** Same with the view given directly; {@code vx, vy, vz} = owner velocity (blocks per second) for the trailing ends. */
	public static void idle(Slots s, int i, double t, boolean ribbon, double vx, double vy, double vz, double[] out) {
		if (!ribbon) {
			orbit(s, i, t, out);
			return;
		}
		ribbonIdle(s, i, t, out);
		if (vx != 0 || vy != 0 || vz != 0) {
			double k = RIBBON.lag * ribbonTrail(s, i, t);
			out[0] -= vx * k;
			out[1] = Math.max(0.03, out[1] - vy * k);
			out[2] -= vz * k;
		}
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

	private static void release(Slots s, int i, double tm, double t, Mode m, double yaw, double[] out) {
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
			idle(s, i, t, m, out);
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
		idle(s, i, t, m, o);
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
		envelopAt(s, i, tm, m.ax, m.ay, m.az, out);
	}

	private static void envelopAt(Slots s, int i, double tm, double cx, double cy, double cz, double[] out) {
		int k = i % 3;
		double kx = k == 1 ? 0.7071 : 0;
		double ky = k == 0 ? 1 : 0.7071;
		double kz = k == 2 ? 0.7071 : 0;
		double sign = (i & 1) == 0 ? 1 : -1;
		double[] d = new double[3];
		rotate(kx, ky, kz, sign * 2 * Math.PI * 3.0 * tm, s.sx[i], s.sy[i], s.sz[i], d);
		double core = smooth(1.0, 1.25, tm);
		double rad = lerp(1.5, 0.9, core) * (0.72 + 0.28 * s.r2[i]);
		out[0] = cx + d[0] * rad;
		out[1] = cy + d[1] * rad;
		out[2] = cz + d[2] * rad;
	}

	/**
	 * Steerable attack: ribbon petal i sits on the recorded head path, {@code trackLag * rank} seconds behind the head (so the ribbon
	 * trails it), as a band across the travel direction while the head moves and as the cutting envelope while it hangs in place.
	 * Before the path starts the petal stays in orbit; after the head is back it falls into the orbit again, trailing petals last.
	 */
	private static void attackTracked(Slots s, int i, double t, Mode m, double[] out) {
		double[] o = new double[3];
		idle(s, i, t, m, o);
		HeadTrack tr = m.track;
		if (!s.ribbon(i)) {
			out[0] = o[0];
			out[1] = o[1];
			out[2] = o[2];
			return;
		}
		double ti = t - m.trackDelay - m.trackLag * s.ribbonRank(i);
		double since = ti - tr.startTime();
		if (since < 0) {
			out[0] = o[0];
			out[1] = o[1];
			out[2] = o[2];
			return;
		}
		double[] h = new double[3];
		tr.at(ti, h);
		double hx = h[0] - m.fx;
		double hy = h[1] - m.fy;
		double hz = h[2] - m.fz;
		double[] p = new double[3];
		envelopAt(s, i, since, hx, hy, hz, p);
		double move = smooth(3.0, 9.0, tr.speedAt(ti));
		if (move > 0.001) {
			double[] g = new double[3];
			if (!tr.dirAt(ti, g)) {
				g[0] = 1;
				g[1] = 0;
				g[2] = 0;
			}
			// e1 = g x up, e2 = g x e1 (the band across the travel direction, as in ribbonPoint)
			double e1x = -g[2];
			double e1z = g[0];
			double el = Math.sqrt(e1x * e1x + e1z * e1z);
			if (el < 1e-4) {
				e1x = 1;
				e1z = 0;
				el = 1;
			}
			e1x /= el;
			e1z /= el;
			double e2x = g[1] * e1z;
			double e2y = g[2] * e1x - g[0] * e1z;
			double e2z = -g[1] * e1x;
			double band = 0.4;
			double sx = hx + (e1x * s.g1[i] + e2x * s.g2[i]) * band;
			double sy = hy + (e2y * s.g2[i]) * band;
			double sz = hz + (e1z * s.g1[i] + e2z * s.g2[i]) * band;
			p[0] = lerp(p[0], sx, move);
			p[1] = lerp(p[1], sy, move);
			p[2] = lerp(p[2], sz, move);
		}
		double w = smooth(0, 0.12, since);
		p[0] = lerp(o[0], p[0], w);
		p[1] = lerp(o[1], p[1], w);
		p[2] = lerp(o[2], p[2], w);
		if (tr.closed()) {
			double q = smooth(tr.endTime(), tr.endTime() + 0.45, ti);
			if (q > 0) {
				p[0] = lerp(p[0], o[0], q);
				p[1] = lerp(p[1], o[1], q) + 1.2 * Math.sin(Math.PI * q) * (0.4 + 0.6 * s.u[i]);
				p[2] = lerp(p[2], o[2], q);
			}
		}
		out[0] = p[0];
		out[1] = p[1];
		out[2] = p[2];
	}

	private static void attack(Slots s, int i, double tm, double t, Mode m, double yaw, double[] out) {
		if (m.tracked()) {
			attackTracked(s, i, t, m, out);
			return;
		}
		double[] o = new double[3];
		idle(s, i, t, m, o);
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
		idle(s, i, t, m, o);
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
