package dev.minebleach.reiatsutest.core.fx;

/**
 * Layout of the Byakuya bankai blade rows (ART_BIBLE 2.5, VFX_STORYBOARD 6.1). Two rows parallel to the flat look direction
 * at the release, 6 blocks either side of the axis. Each row has R ranks (rank 1 innermost, each further rank 1.2 further out,
 * staggered 0.55 along the row, scale +10 percent), blade spacing 1.1, rows start 3 blocks ahead of the player and run behind.
 * Blade count N = {@code bankaiBladeCount} (already quality scaled), R = max(2, ceil(N / 200)) at most 5, N / (2R) blades per rank.
 * Positions are in the ROW frame: {@code along} (forward = flat look at release, from the feet) and {@code across} (right
 * hand side positive). The world transform needs only the release yaw and the feet, so the rows are static (S2).
 */
public final class BladeRowLayout {
	public static final double SPACING = 1.1;
	public static final double RANK_BASE = 6.0;
	public static final double RANK_STEP = 1.2;
	public static final double STAGGER = 0.55;
	public static final double START_AHEAD = 3.0;
	/** The ground hilt falls 0.6 ahead of the feet along the flat look; row distances are measured from it. */
	public static final double HILT_AHEAD = 0.6;
	public static final double BLADE_LENGTH = 8.0;
	/** Start of the eruption of the first blade and the delay per block of distance (6.1). */
	public static final double ERUPT_START = 1.0;
	public static final double ERUPT_PER_BLOCK = 0.012;
	public static final double RISE_TIME = 0.45;
	public static final double SINK_DEPTH = 8.5;
	public static final double LEAN_DEG = 3.0;

	public final int n;
	public final int ranks;
	public final int perRank;
	/** Position of the blade base in the row frame, blocks. */
	public final float[] along;
	public final float[] across;
	/** Row side: +1 right of the axis, -1 left. */
	public final byte[] side;
	public final byte[] rank;
	public final float[] scale;
	/** Distance from the release point (hilt) along the row, blocks. */
	public final float[] dist;
	/** Eruption start time (seconds from the release event). */
	public final float[] start;
	/** Small seeded deviation of the lean in degrees. */
	public final float[] leanJitter;

	private BladeRowLayout(int ranks, int perRank, long seed) {
		this.ranks = ranks;
		this.perRank = perRank;
		this.n = 2 * ranks * perRank;
		along = new float[n];
		across = new float[n];
		side = new byte[n];
		rank = new byte[n];
		scale = new float[n];
		dist = new float[n];
		start = new float[n];
		leanJitter = new float[n];
		int i = 0;
		for (int s = -1; s <= 1; s += 2) {
			for (int k = 0; k < ranks; k++) {
				for (int j = 0; j < perRank; j++) {
					double a = START_AHEAD - SPACING * j - STAGGER * k;
					double off = RANK_BASE + RANK_STEP * k;
					along[i] = (float) a;
					across[i] = (float) (s * off);
					side[i] = (byte) s;
					rank[i] = (byte) k;
					scale[i] = (float) (1.0 + 0.1 * k);
					double d = Math.abs(a - HILT_AHEAD);
					dist[i] = (float) d;
					start[i] = (float) (ERUPT_START + ERUPT_PER_BLOCK * d);
					leanJitter[i] = (float) (FxHash.signed(seed, i, 31) * 0.5);
					i++;
				}
			}
		}
	}

	public static BladeRowLayout of(int requested, long seed) {
		int req = Math.max(4, Math.min(1000, requested));
		int r = Math.max(2, Math.min(5, (req + 199) / 200));
		int per = Math.max(1, req / (2 * r));
		return new BladeRowLayout(r, per, seed);
	}

	/** Time at which the last blade has settled (2.2 s for 200 blades, 2.8 s for 1000). */
	public double completionTime() {
		double max = 0;
		for (int i = 0; i < n; i++) {
			max = Math.max(max, start[i]);
		}
		return max + RISE_TIME;
	}

	/** Length of the rows along the axis (about 55 blocks for 200 blades, 110 for 1000). */
	public double length() {
		double min = 0;
		double max = 0;
		for (int i = 0; i < n; i++) {
			min = Math.min(min, along[i]);
			max = Math.max(max, along[i]);
		}
		return max - min;
	}

	/** Vertical offset of a blade base from its rest height {@code tau} seconds after its eruption start: -8.5, then up with a 0.3 overshoot. */
	public static double riseOffset(double tau) {
		if (tau <= 0) {
			return -SINK_DEPTH;
		}
		double x = Math.min(1.0, tau / RISE_TIME);
		double u = x - 1;
		double c1 = 1.0;
		double ob = 1 + (c1 + 1) * u * u * u + c1 * u * u;
		return -SINK_DEPTH + SINK_DEPTH * ob;
	}

	/** The lean toward the axis in degrees at time t (starts when the rows are complete, ease in-out over 0.6 s). */
	public double leanDeg(double t, int i) {
		double x = Math.max(0, Math.min(1, (t - completionTime() - 0.6) / 0.6));
		double e = -(Math.cos(Math.PI * x) - 1) / 2;
		return e * (LEAN_DEG + leanJitter[i]);
	}

	/** Time after which every blade is in its final pose (the rows can go to the static buffer). */
	public double settledTime() {
		return completionTime() + 1.25;
	}

	/** World position of the base of blade i: rows frame to world for a release yaw in degrees (Minecraft convention) and feet. */
	public void world(int i, double yawDeg, double feetX, double feetZ, double[] out) {
		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		out[0] = feetX + fx * along[i] + rx * across[i];
		out[1] = 0;
		out[2] = feetZ + fz * along[i] + rz * across[i];
	}
}
