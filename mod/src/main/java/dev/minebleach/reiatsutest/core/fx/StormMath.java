package dev.minebleach.reiatsutest.core.fx;

import java.util.Arrays;

/**
 * Closed-form petal storm of the Byakuya bankai scatter ability (VFX_STORYBOARD 6.2, id 32). Every petal is a pure function of
 * {@code (seed, petal index j, storm time t)}: no per-petal state, identical on every client, safe for late joiners.
 * <p>Layout: P petals per blade, petal {@code j} belongs to blade {@code b = j / P} and has the slot index {@code k = j % P}. The
 * petal is born where the burning cut of its blade passes the height {@code 8 - 8 k / P} ({@link RowsPhase.Timing#cutPassesAt}), flows
 * along a corridor curve into the helix around the owner, and then either stays in the tornado (one third) or, ranked by the angle to the
 * facing front, leaves in a stream to a sphere around the aim point (two thirds), contracts, bursts at the impact time, drifts, and
 * returns to its slot, where the regrowing blade consumes it ({@link RowsPhase.Timing#regrowReaches}).
 * <p>All lengths are metres, times seconds from the cast, positions world coordinates. Every number is in {@link Params} so the client can
 * tune it ({@code FxTune}).
 */
public final class StormMath {
	private StormMath() {
	}

	/** Tunable numbers (defaults = the storyboard). */
	public static final class Params {
		public RowsPhase.Timing rows = RowsPhase.Timing.scatter();
		/** Corridor flow from the birth slot to the helix: duration and the control point's lift. */
		public double corridorSpan = 0.9;
		public double corridorUp = 3.0;
		/** Helix: h = (h0 + rise t) mod height; r = rBase + rSpread (h/height)^rExp + rNoise n; angular speed (spinBase - spinTaper h/height) rev/s. */
		public double helixRise = 2.5;
		public double helixHeight = 8.0;
		public double rBase = 1.5;
		public double rSpread = 3.5;
		public double rExp = 0.8;
		public double rNoise = 0.4;
		public double spinBase = 1.2;
		public double spinTaper = 0.4;
		/** Multiplies every helix radius (the server tornado radius is wider than the drawn one). */
		public double radiusScale = 1.0;
		/** 30 percent loose debris at r 5 to 7. */
		public double looseFraction = 0.30;
		public double looseR0 = 5.0;
		public double looseR1 = 7.0;
		/** Stream: fraction of the petals, launch window, speed floor (b/s) and the longest flight. */
		public double streamFraction = 2.0 / 3.0;
		public double launchStart = 1.5;
		public double launchSpan = 0.3;
		public double speedMin = 40.0;
		public double maxFlight = 0.6;
		public double streamWobble = 0.6;
		/** Sphere around the aim: radius 5, then 2.2 over [shrinkStart, shrinkEnd], then 0.8 at the impact; 2 rev/s. */
		public double sphereR0 = 5.0;
		public double sphereR1 = 2.2;
		public double sphereR2 = 0.8;
		public double shrinkStart = 2.0;
		public double shrinkEnd = 2.44;
		public double impactTime = 2.5;
		public double sphereRev = 2.0;
		/** Burst: radius 0.8 + 9 oc((t - impact) / 0.5), falling with 0.5 (t - impact)^2. */
		public double burstR = 9.0;
		public double burstSpan = 0.5;
		public double burstFall = 0.5;
		/** Return: starts at {@code returnStart} (+ jitter for the stream), quadratic Bezier with the control 6 up, takes {@code returnSpan}. */
		public double returnStart = 3.0;
		public double returnJitter = 0.5;
		public double returnSpan = 1.0;
		public double returnUp = 6.0;
	}

	public static double smooth(double e0, double e1, double x) {
		double t = x <= e0 ? 0 : x >= e1 ? 1 : (x - e0) / (e1 - e0);
		return t * t * (3 - 2 * t);
	}

	private static double clamp(double x) {
		return x < 0 ? 0 : Math.min(x, 1);
	}

	private static double oc(double x) {
		double u = 1 - clamp(x);
		return 1 - u * u * u;
	}

	private static double ios(double x) {
		return -(Math.cos(Math.PI * clamp(x)) - 1) / 2;
	}

	private static double wrapPi(double a) {
		while (a > Math.PI) {
			a -= 2 * Math.PI;
		}
		while (a < -Math.PI) {
			a += 2 * Math.PI;
		}
		return a;
	}

	/** Number of petals per blade: {@code min(15, floor(maxPetals x Q / N))} (6.2). */
	public static int petalsPerBlade(int maxPetalsTimesQuality, int blades) {
		return Math.max(1, Math.min(15, maxPetalsTimesQuality / Math.max(1, blades)));
	}

	/** Petals per blade of the Hakuteiken condensation: {@code min(10, floor(2000 / N))} (6.3). */
	public static int condensePerBlade(int blades) {
		return Math.max(1, Math.min(10, 2000 / Math.max(1, blades)));
	}

	/** One storm: the per-petal seeded numbers and all curves. */
	public static final class Storm {
		public final Params p;
		public final long seed;
		public final int blades;
		public final int perBlade;
		public final int m;
		final float[] bladeDist;
		final float[] h0;
		final float[] th0;
		final float[] nz;
		final float[] lr;
		final float[] ret;
		final float[] qs;
		final float[] wob;
		final float[] ux;
		final float[] uy;
		final float[] uz;
		final float[] slotDx;
		final float[] slotDz;
		final byte[] axis;
		final byte[] dir;
		final boolean[] loose;
		final boolean[] stream;
		final float[] launch;
		final float[] flight;
		// scratch of position(): one render thread per storm, so no allocation per petal and frame
		private final double[] tSlot = new double[3];
		private final double[] tH = new double[3];
		private final double[] tC = new double[3];
		private final double[] tP0 = new double[3];
		private final double[] tP1 = new double[3];
		private final double[] tP2 = new double[3];
		private final double[] tP3 = new double[3];
		private final double[] tPs = new double[3];

		/**
		 * @param bladeDist distance of every blade from the release point (the rows' {@code dist}); for a stub ring any values in 0..50
		 * @param facingYaw owner body yaw in radians (Minecraft: 0 = +Z, so forward = (-sin, cos))
		 * @param flightDist distance owner to aim (the stream speed is max(40, L / 0.6))
		 */
		public Storm(Params p, long seed, int blades, int perBlade, float[] bladeDist, double facingYaw, double flightDist) {
			this.p = p;
			this.seed = seed;
			this.blades = blades;
			this.perBlade = perBlade;
			this.m = blades * perBlade;
			this.bladeDist = bladeDist;
			int n = m;
			h0 = new float[n];
			th0 = new float[n];
			nz = new float[n];
			lr = new float[n];
			ret = new float[n];
			qs = new float[n];
			wob = new float[n];
			ux = new float[n];
			uy = new float[n];
			uz = new float[n];
			slotDx = new float[n];
			slotDz = new float[n];
			axis = new byte[n];
			dir = new byte[n];
			loose = new boolean[n];
			stream = new boolean[n];
			launch = new float[n];
			flight = new float[n];
			double facingAz = Math.atan2(Math.cos(facingYaw), -Math.sin(facingYaw));
			int ms = 0;
			double[] key = new double[n];
			for (int j = 0; j < n; j++) {
				h0[j] = (float) (FxHash.unit(seed, j, 21) * p.helixHeight);
				th0[j] = (float) (FxHash.unit(seed, j, 22) * Math.PI * 2);
				nz[j] = (float) FxHash.signed(seed, j, 23);
				lr[j] = (float) FxHash.unit(seed, j, 24);
				ret[j] = (float) FxHash.unit(seed, j, 25);
				qs[j] = (float) FxHash.unit(seed, j, 26);
				wob[j] = (float) (FxHash.unit(seed, j, 27) * Math.PI * 2);
				double cz = FxHash.signed(seed, j, 28);
				double az = FxHash.unit(seed, j, 29) * Math.PI * 2;
				double rr = Math.sqrt(Math.max(0, 1 - cz * cz));
				ux[j] = (float) (rr * Math.cos(az));
				uy[j] = (float) cz;
				uz[j] = (float) (rr * Math.sin(az));
				axis[j] = (byte) Math.floorMod(FxHash.hash(seed, j, 30), 3L);
				dir[j] = (byte) (((FxHash.hash(seed, j, 31) & 1) == 0) ? 1 : -1);
				slotDx[j] = (float) (FxHash.signed(seed, j, 32) * 0.25);
				slotDz[j] = (float) (FxHash.signed(seed, j, 33) * 0.25);
				loose[j] = FxHash.unit(seed, j, 34) < p.looseFraction;
				stream[j] = FxHash.unit(seed, j, 35) < p.streamFraction;
				launch[j] = Float.NaN;
				if (stream[j]) {
					ms++;
					double[] c = new double[3];
					helix(j, p.launchStart, 0, 0, 0, c);
					key[j] = Math.abs(wrapPi(Math.atan2(c[2], c[0]) - facingAz));
				}
			}
			// rank the stream petals by the angle from the front: the nearest leave first
			Integer[] order = new Integer[ms];
			int k = 0;
			for (int j = 0; j < n; j++) {
				if (stream[j]) {
					order[k++] = j;
				}
			}
			Arrays.sort(order, (a, b) -> Double.compare(key[a], key[b]));
			for (int r = 0; r < ms; r++) {
				int j = order[r];
				launch[j] = (float) (p.launchStart + p.launchSpan * r / Math.max(1, ms - 1));
				double len = Math.max(4.0, flightDist + 3.0 * FxHash.signed(seed, j, 36));
				double speed = Math.max(p.speedMin, len / p.maxFlight);
				flight[j] = (float) Math.min(p.maxFlight, len / speed);
			}
		}

		public int blade(int j) {
			return j / perBlade;
		}

		public int slot(int j) {
			return j % perBlade;
		}

		public boolean isStream(int j) {
			return stream[j];
		}

		public boolean isLoose(int j) {
			return loose[j];
		}

		public double launchTime(int j) {
			return launch[j];
		}

		public double flightTime(int j) {
			return flight[j];
		}

		public double arriveTime(int j) {
			return launch[j] + flight[j];
		}

		/** Height of the birth slot on the blade: {@code 8 - 8 k / P}. */
		public double slotHeight(int j) {
			return RowsPhase.BLADE_LENGTH * (1.0 - (double) slot(j) / perBlade);
		}

		/** Birth time: the burning cut of the blade passes the slot height. */
		public double birthTime(int j) {
			return p.rows.cutPassesAt(bladeDist[blade(j)], slotHeight(j));
		}

		/** The regrowing blade reaches the slot: the petal is consumed. */
		public double removeTime(int j) {
			return p.rows.regrowReaches(bladeDist[blade(j)], slotHeight(j));
		}

		public double returnTime(int j) {
			return p.returnStart + (stream[j] ? p.returnJitter * ret[j] : 0.0);
		}

		/** Time at which the petal is back at its slot. */
		public double settledTime(int j) {
			return returnTime(j) + p.returnSpan;
		}

		/** Helix position (world) of petal j at time t around the owner's feet {@code (ox, oy, oz)}; returns the height fraction h / height. */
		public double helix(int j, double t, double ox, double oy, double oz, double[] out) {
			double h = (h0[j] + p.helixRise * t) % p.helixHeight;
			double hf = h / p.helixHeight;
			double r = loose[j] ? p.looseR0 + (p.looseR1 - p.looseR0) * lr[j] : p.rBase + p.rSpread * Math.pow(hf, p.rExp) + p.rNoise * nz[j];
			r *= p.radiusScale;
			double th = th0[j] + 2 * Math.PI * (p.spinBase - p.spinTaper * hf) * t;
			out[0] = ox + r * Math.cos(th);
			out[1] = oy + h;
			out[2] = oz + r * Math.sin(th);
			return hf;
		}

		/** Radius of the sphere around the aim at time t (5, then 2.2 over [2.0, 2.44], then 0.8 at the impact). */
		public double sphereRadius(double t) {
			if (t <= p.shrinkStart) {
				return p.sphereR0;
			}
			if (t <= p.shrinkEnd) {
				return p.sphereR0 + (p.sphereR1 - p.sphereR0) * ios((t - p.shrinkStart) / (p.shrinkEnd - p.shrinkStart));
			}
			if (t <= p.impactTime) {
				return p.sphereR1 + (p.sphereR2 - p.sphereR1) * ios((t - p.shrinkEnd) / Math.max(1e-3, p.impactTime - p.shrinkEnd));
			}
			return p.sphereR2;
		}

		private void rotated(int j, double t, double[] out) {
			double kx = 0;
			double ky = 1;
			double kz = 0;
			if (axis[j] == 1) {
				kx = 0.7071;
				ky = 0.7071;
			} else if (axis[j] == 2) {
				ky = 0.7071;
				kz = 0.7071;
			}
			double ang = dir[j] * 2 * Math.PI * p.sphereRev * t;
			double c = Math.cos(ang);
			double s = Math.sin(ang);
			double vx = ux[j];
			double vy = uy[j];
			double vz = uz[j];
			double dot = kx * vx + ky * vy + kz * vz;
			out[0] = vx * c + (ky * vz - kz * vy) * s + kx * dot * (1 - c);
			out[1] = vy * c + (kz * vx - kx * vz) * s + ky * dot * (1 - c);
			out[2] = vz * c + (kx * vy - ky * vx) * s + kz * dot * (1 - c);
		}

		/** Position on the sphere around the aim (world). */
		public void sphere(int j, double t, double[] aim, double[] out) {
			rotated(j, t, out);
			double r = sphereRadius(t) * (0.85 + 0.3 * qs[j]);
			out[0] = aim[0] + out[0] * r;
			out[1] = aim[1] + out[1] * r;
			out[2] = aim[2] + out[2] * r;
		}

		/** Burst after the impact: {@code aim + u (0.8 + 9 oc((t - impact) / 0.5)) - 0.5 (t - impact)^2 up}. */
		public void burst(int j, double t, double[] aim, double[] out) {
			double tb = Math.max(0, t - p.impactTime);
			rotated(j, p.impactTime, out);
			double r = (p.sphereR2 + p.burstR * oc(tb / p.burstSpan)) * (0.85 + 0.3 * qs[j]);
			out[0] = aim[0] + out[0] * r;
			out[1] = aim[1] + out[1] * r - p.burstFall * tb * tb;
			out[2] = aim[2] + out[2] * r;
		}

		private static void bez2(double[] a, double[] c, double[] b, double s, double[] out) {
			double u = 1 - s;
			for (int i = 0; i < 3; i++) {
				out[i] = u * u * a[i] + 2 * u * s * c[i] + s * s * b[i];
			}
		}

		private static void bez3(double[] a, double[] b, double[] c, double[] d, double s, double[] out) {
			double u = 1 - s;
			for (int i = 0; i < 3; i++) {
				out[i] = u * u * u * a[i] + 3 * u * u * s * b[i] + 3 * u * s * s * c[i] + s * s * s * d[i];
			}
		}

		/** A little noise while the petals fall after the burst; fades in from zero at the impact so the position has no jump. */
		private void drift(int j, double t, double[] out) {
			double k = smooth(p.impactTime, p.impactTime + 0.4, t) * 0.25;
			out[0] += k * Math.sin(5.0 * t + wob[j]);
			out[2] += k * Math.cos(4.3 * t + wob[j]);
		}

		/** The birth slot of petal j in the world (blade base at {@code (bx, by, bz)}, by = rest y of the base). */
		public void slotPoint(int j, double bx, double by, double bz, double[] out) {
			out[0] = bx + slotDx[j];
			out[1] = by + slotHeight(j);
			out[2] = bz + slotDz[j];
		}

		/**
		 * World position of petal j at storm time t.
		 *
		 * @param owner feet of the owner now (the tornado follows him)
		 * @param bx blade base x, by rest y of the base, bz base z of the blade of this petal
		 * @param aim aimed point, {@code look} unit look vector of the cast
		 * @return the scale 0..1 (0 = not born, or consumed)
		 */
		public double position(int j, double t, double[] owner, double bx, double by, double bz, double[] aim, double[] look, double[] out) {
			double ts = birthTime(j);
			if (t < ts) {
				return 0;
			}
			double scale = smooth(ts, ts + 0.08, t);
			double trm = removeTime(j);
			scale *= 1.0 - smooth(trm - 0.10, trm, t);
			if (scale <= 0) {
				return 0;
			}
			double[] slot = tSlot;
			slotPoint(j, bx, by, bz, slot);
			double tr = returnTime(j);
			double tEnd = tr + p.returnSpan;
			if (t >= tEnd) {
				double sway = 0.06 * Math.sin(2 * Math.PI * 0.7 * t + wob[j]);
				out[0] = slot[0] + sway;
				out[1] = slot[1] + 0.5 * sway;
				out[2] = slot[2] - sway;
				return scale;
			}
			double[] h = tH;
			if (t < tr && !(stream[j] && t >= launch[j])) {
				// born, corridor flow into the helix, then the helix itself
				double hf = helix(j, t, owner[0], owner[1], owner[2], h);
				double s = smooth(ts, ts + p.corridorSpan, t);
				double[] c = tC;
				c[0] = (slot[0] + h[0]) * 0.5;
				c[1] = (slot[1] + h[1]) * 0.5 + p.corridorUp;
				c[2] = (slot[2] + h[2]) * 0.5;
				bez2(slot, c, h, s, out);
				double fade = smooth(0.0, 0.06, hf) * (1.0 - smooth(0.94, 1.0, hf));
				scale *= s * fade + (1 - s);
				return scale;
			}
			if (stream[j] && t < p.impactTime) {
				double tl = launch[j];
				double ta = tl + flight[j];
				if (t < ta) {
					double[] p0 = tP0;
					double hf0 = helix(j, tl, owner[0], owner[1], owner[2], p0);
					double[] p3 = tP3;
					sphere(j, ta, aim, p3);
					double dx = aim[0] - p0[0];
					double dy = aim[1] - p0[1];
					double dz = aim[2] - p0[2];
					double dl = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
					dx /= dl;
					dy /= dl;
					dz /= dl;
					double[] p1 = tP1;
					p1[0] = p0[0] + look[0] * 4;
					p1[1] = p0[1] + look[1] * 4 + 2;
					p1[2] = p0[2] + look[2] * 4;
					double[] p2 = tP2;
					p2[0] = aim[0] - dx * 6;
					p2[1] = aim[1] - dy * 6 + 3;
					p2[2] = aim[2] - dz * 6;
					double s = clamp((t - tl) / Math.max(1e-3, flight[j]));
					bez3(p0, p1, p2, p3, s, out);
					// lateral wobble across the flight direction
					double nx = -dz;
					double nzz = dx;
					double nl = Math.max(1e-3, Math.sqrt(nx * nx + nzz * nzz));
					double w = p.streamWobble * Math.sin(2 * Math.PI * 1.5 * s + wob[j]) * Math.sin(Math.PI * s);
					out[0] += nx / nl * w;
					out[2] += nzz / nl * w;
					double fade0 = smooth(0.0, 0.06, hf0) * (1.0 - smooth(0.94, 1.0, hf0));
					return scale * Math.max(fade0, smooth(tl, tl + 0.08, t));
				}
				sphere(j, t, aim, out);
				return scale;
			}
			if (stream[j] && t < tr) {
				burst(j, t, aim, out);
				drift(j, t, out);
				return scale;
			}
			// return: quadratic Bezier from where the petal is at tr to its slot, control 6 up
			double[] ps = tPs;
			if (stream[j]) {
				burst(j, tr, aim, ps);
				drift(j, tr, ps);
			} else {
				helix(j, tr, owner[0], owner[1], owner[2], ps);
			}
			double s = ios((t - tr) / p.returnSpan);
			double[] c = tC;
			c[0] = (ps[0] + slot[0]) * 0.5;
			c[1] = (ps[1] + slot[1]) * 0.5 + p.returnUp;
			c[2] = (ps[2] + slot[2]) * 0.5;
			bez2(ps, c, slot, s, out);
			double w = 0.5 * Math.sin(Math.PI * s) * Math.sin(2 * Math.PI * 1.3 * s + wob[j]);
			out[0] += w;
			out[2] -= w;
			return scale;
		}
	}
}
