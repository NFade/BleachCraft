package dev.minebleach.reiatsutest.core.fx;

/**
 * The condensation of the Byakuya bankai rows into the hand at the start of Shukei: Hakuteiken (VFX_STORYBOARD 6.3, t 0.0 to about 1.4).
 * The blades dissolve fast from the tip down ({@link RowsPhase.Timing#consumed()}), the petals are born at the cut like the storm petals,
 * spiral two turns into the hand point and are absorbed there; within 1.5 blocks of the hand they turn white. Pure closed form of
 * {@code (seed, j, t)} like {@link StormMath}.
 */
public final class CondenseMath {
	private CondenseMath() {
	}

	/** Tunable numbers (defaults = the storyboard). */
	public static final class Params {
		public RowsPhase.Timing rows = RowsPhase.Timing.consumed();
		/** Flight time to the hand: base + jitter x u. */
		public double flightBase = 0.55;
		public double flightJitter = 0.35;
		public double turns = 2.0;
		public double spiralRadius = 1.7;
		/** Petals within this distance of the hand are white. */
		public double whiteRadius = 1.5;
		/** The petal shrinks into the hand over the last fraction of its flight. */
		public double absorb = 0.88;
		/** Exponent of the pull (1 = uniform, larger = accelerating into the hand). */
		public double pull = 1.5;
	}

	public static final class Cloud {
		public final Params p;
		public final int blades;
		public final int perBlade;
		public final int m;
		final float[] bladeDist;
		final float[] th0;
		final float[] u;
		final float[] rad;
		final byte[] dir;

		public Cloud(Params p, long seed, int blades, int perBlade, float[] bladeDist) {
			this.p = p;
			this.blades = blades;
			this.perBlade = perBlade;
			this.m = blades * perBlade;
			this.bladeDist = bladeDist;
			th0 = new float[m];
			u = new float[m];
			rad = new float[m];
			dir = new byte[m];
			for (int j = 0; j < m; j++) {
				th0[j] = (float) (FxHash.unit(seed, j, 41) * Math.PI * 2);
				u[j] = (float) FxHash.unit(seed, j, 42);
				rad[j] = (float) (0.4 + 0.6 * FxHash.unit(seed, j, 43));
				dir[j] = (byte) ((FxHash.hash(seed, j, 44) & 1) == 0 ? 1 : -1);
			}
		}

		public int blade(int j) {
			return j / perBlade;
		}

		public double slotHeight(int j) {
			return RowsPhase.BLADE_LENGTH * (1.0 - (double) (j % perBlade) / perBlade);
		}

		public double birthTime(int j) {
			return p.rows.cutPassesAt(bladeDist[blade(j)], slotHeight(j));
		}

		public double flight(int j) {
			return p.flightBase + p.flightJitter * u[j];
		}

		/** Time at which the petal reaches the hand. */
		public double arriveTime(int j) {
			return birthTime(j) + flight(j);
		}

		/** The last petal is absorbed at this time. */
		public double endTime() {
			double e = 0;
			for (int j = 0; j < m; j++) {
				e = Math.max(e, arriveTime(j));
			}
			return e;
		}

		/** Whiteness 0..1 of a petal at distance {@code d} from the hand (0 beyond 1.5 blocks, 1 at the hand). */
		public double whiteness(double d) {
			return 1.0 - Math.min(1.0, Math.max(0.0, (d - 0.25) / Math.max(1e-3, p.whiteRadius - 0.25)));
		}

		/** World position of petal j; the scale 0..1 (0 = not born or absorbed). {@code hand} is the hand point in the world. */
		public double position(int j, double t, double bx, double by, double bz, double[] hand, double[] out) {
			double ts = birthTime(j);
			if (t < ts) {
				return 0;
			}
			double fl = flight(j);
			double s = (t - ts) / fl;
			if (s >= 1.0) {
				return 0;
			}
			double sx = bx;
			double sy = by + slotHeight(j);
			double sz = bz;
			double ax = hand[0] - sx;
			double ay = hand[1] - sy;
			double az = hand[2] - sz;
			double al = Math.max(1e-3, Math.sqrt(ax * ax + ay * ay + az * az));
			double nx = ax / al;
			double ny = ay / al;
			double nzz = az / al;
			// e1 = axis x up (fallback x), e2 = axis x e1
			double e1x = ny * 0 - nzz * 1;
			double e1y = 0;
			double e1z = nx * 1 - ny * 0;
			double el = Math.sqrt(e1x * e1x + e1z * e1z);
			if (el < 1e-4) {
				e1x = 1;
				e1z = 0;
				el = 1;
			}
			e1x /= el;
			e1z /= el;
			double e2x = ny * e1z - nzz * e1y;
			double e2y = nzz * e1x - nx * e1z;
			double e2z = nx * e1y - ny * e1x;
			double e = Math.pow(Math.max(0, s), p.pull);
			double ang = th0[j] + dir[j] * 2 * Math.PI * p.turns * s;
			double rho = p.spiralRadius * rad[j] * Math.pow(Math.sin(Math.PI * Math.min(1, s)), 0.7) * (1.0 - 0.35 * s);
			double c = Math.cos(ang) * rho;
			double sn = Math.sin(ang) * rho;
			out[0] = sx + ax * e + e1x * c + e2x * sn;
			out[1] = sy + ay * e + e1y * c + e2y * sn;
			out[2] = sz + az * e + e1z * c + e2z * sn;
			double scale = StormMath.smooth(ts, ts + 0.08, t) * (1.0 - StormMath.smooth(p.absorb, 1.0, s));
			return scale;
		}
	}
}
