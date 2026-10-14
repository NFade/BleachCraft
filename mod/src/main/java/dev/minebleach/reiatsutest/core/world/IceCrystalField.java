package dev.minebleach.reiatsutest.core.world;

import java.util.ArrayList;
import java.util.List;

/**
 * R11: the look of the Rukia ice wall, pure geometry (no Minecraft types, CorePurityTest). The wall is real blocks; the client draws every wall block as one cell
 * of a crystal column: a hexagonal shaft whose bottom and top rings are the same hexagon as the neighbour's, so the cells fuse into one spire, ended by a tip or a
 * crown at the top, with leaning satellite crystals and a few feet at the base. Every number here is a pure function of the block position and of "a wall block
 * stands under / over this one" (nothing looks further than one block, a chunk section rebuild does not reach more), so everybody sees the same wall, also those
 * who join later.
 *
 * <p>This is the Java port of {@code blender/scripts/ice_wall_field.py} (the Blender build script of the pieces and the check sheet); the integer hash and the
 * order of every step are the same, a golden file written by the Python module is compared in {@code IceCrystalFieldTest}. Change both or neither.
 *
 * <p>Frame: the pieces are authored in the Blender frame (Z up), cell centre (0, 0), z from 0 to 1. {@link Cell#column} / {@link Cell#rigid} give points in that
 * frame; the client maps them to the block: {@code mc = (x + 0.5, z, 0.5 - y)} added to the block corner ({@link #toBlockX} ...).
 */
public final class IceCrystalField {
	public static final String MODEL = "rukia_ice_wall";

	public static final double NOMINAL_R = 0.5;
	public static final double COL_R_MIN = 0.62;
	public static final double COL_R_MAX = 1.12;
	public static final double RING_JITTER = 0.08;
	public static final double FLARE = 1.18;
	public static final double FLARE_CAP = 0.56;
	public static final double LEAN_AMP = 0.15;
	public static final double LEAN_W = 0.55;
	public static final double JITTER_AMP = 0.02;
	public static final double PIVOT = 0.14;
	public static final double TIP_SCALE_MIN = 0.80;
	public static final double TIP_SCALE_MAX = 1.15;

	public static final List<String> SHAFTS = List.of("shaft_a", "shaft_b", "shaft_c");
	public static final List<String> TIPS = List.of("tip_a", "tip_b", "tip_c");
	public static final List<String> CROWNS = List.of("crown_a", "crown_b", "crown_c");
	public static final List<String> SATS = List.of("sat_a", "sat_b", "sat_c", "sat_d");
	public static final List<String> FEET = List.of("feet_a", "feet_b");
	/** Top piece weights in the order of the Python table: tip_a, tip_b, tip_c, crown_a, crown_b, crown_c. */
	private static final String[] TOP_NAMES = {"tip_a", "tip_b", "tip_c", "crown_a", "crown_b", "crown_c"};
	private static final double[] TOP_WEIGHTS = {0.22, 0.16, 0.12, 0.10, 0.08, 0.32};

	/** Every piece (OBJ object {@code rukia_ice_wall_<name>.obj}); the ones in {@link #CORE_PIECES} also have {@code ..._core.obj}. */
	public static final List<String> PIECES;
	public static final List<String> CORE_PIECES;

	static {
		List<String> all = new ArrayList<>();
		all.addAll(SHAFTS);
		all.addAll(TIPS);
		all.addAll(CROWNS);
		CORE_PIECES = List.copyOf(all);
		all.addAll(SATS);
		all.addAll(FEET);
		PIECES = List.copyOf(all);
	}

	/** Most satellites on one block (the budget test counts with it). */
	public static final int MAX_SATS = 2;

	private IceCrystalField() {
	}

	/** OBJ object name of a piece, or of its inner glow core. */
	public static String objectName(String piece, boolean core) {
		return MODEL + "_" + piece + (core ? "_core" : "");
	}

	// ------------------------------------------------------------------------------------------------------------------------------------- hash
	/** Integer hash of a block position (the same 32 bit arithmetic as the Python module). */
	public static int mix(int a, int b, int c, int salt) {
		int h = salt * 0x9E3779B1;
		h ^= a * 0x85EBCA6B;
		h ^= b * 0xC2B2AE35;
		h ^= c * 0x27D4EB2F;
		h ^= h >>> 16;
		h *= 0x7FEB352D;
		h ^= h >>> 15;
		h *= 0x846CA68B;
		h ^= h >>> 16;
		return h;
	}

	/** 0 &lt;= value &lt; 1 from the top 24 bits. */
	public static double unit(int h) {
		return (h >>> 8) / 16777216.0;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double clamp01(double t) {
		return Math.max(0.0, Math.min(1.0, t));
	}

	private static String pick(double u, List<String> names) {
		return names.get(Math.min(names.size() - 1, (int) (u * names.size())));
	}

	private static String pickWeighted(double u) {
		double acc = 0.0;
		for (int i = 0; i < TOP_NAMES.length; i++) {
			acc += TOP_WEIGHTS[i];
			if (u < acc) {
				return TOP_NAMES[i];
			}
		}
		return TOP_NAMES[TOP_NAMES.length - 1];
	}

	private static double columnScale(int x, int z) {
		return COL_R_MIN + (COL_R_MAX - COL_R_MIN) * unit(mix(x, 0, z, 4));
	}

	// ------------------------------------------------------------------------------------------------------------------------------------- cell
	/** One satellite crystal: piece kind, azimuth in degrees round the shaft, height in the cell. */
	public record Sat(String kind, double az, double z) {
	}

	/** Everything the client needs to draw the block at ({@link #x}, {@link #y}, {@link #z}). */
	public static final class Cell {
		public final int x;
		public final int y;
		public final int z;
		public final boolean below;
		public final boolean above;
		public final double yaw;
		public final double colR;
		public final double colOffX;
		public final double colOffY;
		public final double leanAmp;
		public final double leanPhase1;
		public final double leanPhase2;
		public final double fBottom;
		public final double fTop;
		public final double offBottomX;
		public final double offBottomY;
		public final double offTopX;
		public final double offTopY;
		/** shaft_*, tip_* or crown_*. */
		public final String piece;
		/** No wall block above: this cell ends the column. */
		public final boolean top;
		public final double tipScale;
		public final List<Sat> sats;
		/** feet_* of the lowest cell of a column, else null. */
		public final String feet;
		public final double feetYaw;

		Cell(int x, int y, int z, boolean below, boolean above) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.below = below;
			this.above = above;
			this.yaw = unit(mix(x, 0, z, 1)) * 360.0;
			this.colR = columnScale(x, z);
			double amp = 0.04 + 0.20 * (COL_R_MAX - colR);
			this.colOffX = (unit(mix(x, 0, z, 2)) - 0.5) * 2.0 * amp;
			this.colOffY = (unit(mix(x, 0, z, 3)) - 0.5) * 2.0 * amp;
			this.leanPhase1 = unit(mix(x, 0, z, 16)) * 2.0 * Math.PI;
			this.leanPhase2 = unit(mix(x, 0, z, 17)) * 2.0 * Math.PI;
			this.leanAmp = LEAN_AMP * (0.4 + 0.6 * unit(mix(x, 0, z, 18)));
			double fb = colR * (1.0 + (unit(mix(x, y, z, 5)) - 0.5) * RING_JITTER);
			this.offBottomX = (unit(mix(x, y, z, 6)) - 0.5) * 2.0 * JITTER_AMP;
			this.offBottomY = (unit(mix(x, y, z, 7)) - 0.5) * 2.0 * JITTER_AMP;
			this.fTop = colR * (1.0 + (unit(mix(x, y + 1, z, 5)) - 0.5) * RING_JITTER);
			this.offTopX = (unit(mix(x, y + 1, z, 6)) - 0.5) * 2.0 * JITTER_AMP;
			this.offTopY = (unit(mix(x, y + 1, z, 7)) - 0.5) * 2.0 * JITTER_AMP;
			boolean flare = !below;
			if (flare) {
				fb = Math.min(fb * FLARE, FLARE_CAP / NOMINAL_R);
			}
			this.fBottom = fb;
			this.top = !above;
			if (top) {
				this.piece = pickWeighted(unit(mix(x, y, z, 8)));
				this.tipScale = TIP_SCALE_MIN + (TIP_SCALE_MAX - TIP_SCALE_MIN) * unit(mix(x, y, z, 9));
			} else {
				this.piece = pick(unit(mix(x, y, z, 13)), SHAFTS);
				this.tipScale = 1.0;
			}
			double nu = unit(mix(x, y, z, 10));
			int n = nu < 0.20 ? 0 : (nu < 0.65 ? 1 : 2);
			List<Sat> list = new ArrayList<>(n);
			for (int s = 0; s < n; s++) {
				String kind = pick(unit(mix(x, y, z, 20 + s)), SATS);
				double az = unit(mix(x, y, z, 30 + s)) * 360.0;
				double zz = 0.08 + (top ? 0.45 : 0.65) * unit(mix(x, y, z, 40 + s));
				list.add(new Sat(kind, az, zz));
			}
			this.sats = List.copyOf(list);
			this.feet = flare ? pick(unit(mix(x, y, z, 14)), FEET) : null;
			this.feetYaw = unit(mix(x, y, z, 15)) * 360.0;
		}

		/** Axis offset of the column at cell height t (0..1) as {x, y}. */
		public double[] axisAt(double t) {
			t = clamp01(t);
			double ya = y + t;
			return new double[] {
				lerp(offBottomX, offTopX, t) + colOffX + leanAmp * Math.sin(LEAN_W * ya + leanPhase1),
				lerp(offBottomY, offTopY, t) + colOffY + leanAmp * Math.sin(LEAN_W * ya + leanPhase2)};
		}

		public double ringFactorAt(double t) {
			return lerp(fBottom, fTop, clamp01(t));
		}

		/** A vertex of the shaft / tip / crown piece (or of its core) after the variation, Blender frame of the cell. */
		public double[] column(double vx, double vy, double vz) {
			double t = vz;
			double zz = vz;
			if (top && vz > PIVOT) {
				zz = PIVOT + (vz - PIVOT) * tipScale;
			}
			double f = ringFactorAt(t);
			double px = vx * f;
			double py = vy * f;
			double a = Math.toRadians(yaw);
			double ca = Math.cos(a);
			double sa = Math.sin(a);
			double[] ax = axisAt(t);
			return new double[] {px * ca - py * sa + ax[0], px * sa + py * ca + ax[1], zz};
		}

		/** Origin {x, y, z, yaw degrees} of satellite s. */
		public double[] satOrigin(int s) {
			Sat sat = sats.get(s);
			double[] ax = axisAt(sat.z());
			double f = ringFactorAt(sat.z());
			double a = Math.toRadians(sat.az());
			double rad = 0.34 * f;
			return new double[] {ax[0] + rad * Math.cos(a), ax[1] + rad * Math.sin(a), sat.z(), sat.az()};
		}

		/** Origin {x, y, z, yaw degrees} of the feet piece. */
		public double[] feetOrigin() {
			double[] ax = axisAt(0.0);
			return new double[] {ax[0], ax[1], 0.0, feetYaw};
		}
	}

	/** A point of a rigid piece (satellite, feet) placed at {@code origin} ({x, y, z, yaw degrees}). */
	public static double[] rigid(double[] origin, double vx, double vy, double vz) {
		double a = Math.toRadians(origin[3]);
		return new double[] {origin[0] + vx * Math.cos(a) - vy * Math.sin(a), origin[1] + vx * Math.sin(a) + vy * Math.cos(a), origin[2] + vz};
	}

	/** The cell of the wall block at ({@code x}, {@code y}, {@code z}); {@code below} / {@code above}: a wall block stands under / over it. */
	public static Cell cell(int x, int y, int z, boolean below, boolean above) {
		return new Cell(x, y, z, below, above);
	}

	// ------------------------------------------------------------------------------------------------------------------------------------- frame
	/** Blender frame point (x, y, z) of a cell to Minecraft block local coordinates: X = x + 0.5, Y = z, Z = 0.5 - y. */
	public static double toBlockX(double bx) {
		return bx + 0.5;
	}

	public static double toBlockY(double bz) {
		return bz;
	}

	public static double toBlockZ(double by) {
		return 0.5 - by;
	}
}
