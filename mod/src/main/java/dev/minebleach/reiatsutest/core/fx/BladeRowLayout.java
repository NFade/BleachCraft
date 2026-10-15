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
	/** Start of the eruption of the first blade and the delay per block of distance (6.1). */
	public static final double ERUPT_START = 1.0;
	public static final double ERUPT_PER_BLOCK = 0.012;
	public static final double RISE_TIME = 0.45;
	/** The sunk blade is hidden this far under its rest height, mesh metres, beyond the length of the blade ({@link #sinkDepth()}). */
	public static final double SINK_MARGIN = 0.5;
	/** R12 pass 2: 0. The shafts of the blades in the anime frame (ep52) are vertical: the left edge of the nearest blade drifts 4 px over 440 px, 0.5 degrees; only the point of the blade is bent. The lean machinery stays (a seeded jitter of up to 0.5 degrees remains). */
	public static final double LEAN_DEG = 0.0;

	// ---- r7: the wall of slab blades (references ep52 / ep58 of the anime: pages of a book). R11 T13: the mesh is the user's straight blade, its size
	// (length 5.5, width 0.4026, thickness 0.1477) comes from the meta through BladeSpec, not from constants of the old 8 m scimitar.
	/** High tier: blades per side. */
	public static final int DEFAULT_PER_SIDE = 10;
	/** Uniform scale of the blades (bankai.bladeScale): 1.0 = the mesh as it is, 5.5 blocks tall (3 heights of Byakuya, measured on the anime). */
	public static final double DEFAULT_BLADE_SCALE = 1.0;
	/** Width of the blades across the lane relative to the mesh (bankai.bladeWidth): the frame ep52 has a length : width ratio of about 10, the mesh 13.7, and the user asked for broader blades seen along the lane. Thickness and length stay. */
	public static final double DEFAULT_WIDTH_FACTOR = 1.35;
	/** Distance from the axis to the blade bases (rows.halfWidth): lane wide open around the caster. */
	public static final double DEFAULT_HALF_WIDTH = 7.0;
	/** Distance between neighbours along the row (rows.spacing): with the 0.1477 thick slab the gap is 0.85 blocks. */
	public static final double DEFAULT_PITCH = 1.0;
	/** First blade this many blocks ahead of the feet (rows.startAhead). r7 user request: the big blades appear BEHIND the character, so it is negative: the first blade 2 blocks behind the feet, the rest run further back. */
	public static final double DEFAULT_START_AHEAD = -2.0;

	// ---- R9 measurement of the anime (design/results/bankai_blade_r9.md), switched on by bankai.layout = r9 only; the default layout above is unchanged.
	/** bankai.layout value that selects the R9 numbers below. */
	public static final String LAYOUT_R9 = "r9";
	public static final double R9_START_AHEAD = -1.0;
	public static final double R9_PITCH = 2.0;
	public static final int R9_PER_SIDE = 12;
	public static final double R9_HALF_WIDTH = 3.7;

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

	/** Layout scale (rows.scale): spacing, corridor half width, rank step and stagger are multiplied by it. 1.0 = the original numbers. */
	public final double space;
	/** Distance from the axis to the innermost rank, blocks (the open lane around the caster is twice this wide). */
	public final double halfWidth;

	private BladeRowLayout(int ranks, int perRank, long seed, double space) {
		this(ranks, perRank, seed, SPACING * space, RANK_BASE * space, RANK_STEP * space, STAGGER * space, START_AHEAD, space);
	}

	private BladeRowLayout(int ranks, int perRank, long seed, double spacing, double rankBase, double rankStep, double stagger, double startAhead, double space) {
		this.space = space;
		this.halfWidth = rankBase;
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
					double a = startAhead - (spacing * j + stagger * k);
					double off = rankBase + rankStep * k;
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
		return of(requested, seed, 1.0);
	}

	/** {@code space} = rows.scale: 1.0 is the original layout (6 blocks to the first rank, spacing 1.1), clamped to 0.2..3. */
	public static BladeRowLayout of(int requested, long seed, double space) {
		double sp = Math.max(0.2, Math.min(3.0, space));
		int req = Math.max(4, Math.min(1000, requested));
		int r = Math.max(2, Math.min(5, (req + 199) / 200));
		int per = Math.max(1, req / (2 * r));
		return new BladeRowLayout(r, per, seed, sp);
	}

	/**
	 * The open lane of the Byakuya bankai (vfx2): ONE rank per side, {@code perSide} blades on each, {@code halfWidth} blocks either side of the axis
	 * (a clear lane around the caster), {@code spacing} blocks between neighbours, the first blade {@code startAhead} blocks ahead of the feet and the
	 * rest running behind. About 10 blades in all by default (5 per side), not the 200 of the old two rank corridor.
	 */
	public static BladeRowLayout lane(int perSide, long seed, double halfWidth, double spacing, double startAhead) {
		int per = Math.max(1, Math.min(250, perSide));
		return new BladeRowLayout(1, per, seed, Math.max(0.5, spacing), Math.max(1.0, halfWidth), RANK_STEP, 0.0, startAhead, 1.0);
	}

	/** The lane with the r7 defaults for {@code perSide} blades per side, spacing widened for the lower tiers (see {@link #tierPitch}). */
	public static BladeRowLayout laneDefault(int perSide, long seed) {
		return lane(perSide, seed, DEFAULT_HALF_WIDTH, tierPitch(perSide, DEFAULT_PER_SIDE, DEFAULT_PITCH), DEFAULT_START_AHEAD);
	}

	/**
	 * Pitch for a tier with fewer blades: the lane keeps up to twice its High length (neighbours then stand twice as far apart at most),
	 * so Medium (5 per side) still reads as a corridor and not as a short stub. {@code perSide >= high} keeps {@code pitch}.
	 */
	public static double tierPitch(int perSide, int high, double pitch) {
		if (perSide >= high || perSide < 2) {
			return pitch;
		}
		return pitch * Math.min(2.0, (high - 1.0) / (perSide - 1.0));
	}

	/** Height of a blade in blocks at the uniform scale {@code bladeScale}. */
	public static double height(double bladeScale) {
		return BladeSpec.length() * bladeScale;
	}

	/** Gap between two neighbouring blades along the row, blocks: pitch minus the thickness of a slab. */
	public static double gap(double pitch, double bladeScale) {
		return pitch - BladeSpec.thickness() * bladeScale;
	}

	/** How deep a blade sinks under its rest height (mesh metres): the blade length plus a margin, so that a risen blade starts completely buried. */
	public static double sinkDepth() {
		return BladeSpec.length() + SINK_MARGIN;
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

	/** Vertical offset of a blade base from its rest height {@code tau} seconds after its eruption start: {@code -sinkDepth()} (6.0), then up with a 0.3 overshoot. */
	public static double riseOffset(double tau) {
		double depth = sinkDepth();
		if (tau <= 0) {
			return -depth;
		}
		double x = Math.min(1.0, tau / RISE_TIME);
		double u = x - 1;
		double c1 = 1.0;
		double ob = 1 + (c1 + 1) * u * u * u + c1 * u * u;
		return -depth + depth * ob;
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

	/** Half width of the corridor up to the innermost rank, blocks. */
	public double corridorHalfWidth() {
		return halfWidth;
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
