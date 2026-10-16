package dev.minebleach.reiatsutest.core.world;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * R11 T9: the pure rules of "abilities break blocks" (no Minecraft types, CorePurityTest). The server glue ({@code server/AbilityTerrain})
 * reads the facts of a block and the world and asks this class; this class decides, computes the cells of a crater or a tunnel and splits
 * the work into per tick batches.
 *
 * <ul>
 * <li>{@link Profile}: S "soft" (hardness up to 0.8), M "medium" (2.0), L "strong" (5.0); the limits are in {@link Config}.</li>
 * <li>{@link #verdict}: the one function that says whether a block may be broken. Never broken, whatever the profile: air, fluids,
 * unbreakable blocks (hardness below 0), blocks with a block entity, special blocks (bedrock, barrier, command and structure blocks,
 * portals and portal frames, the frost blocks and the ice wall of the mod) and protected cells (spawn protection and the like).</li>
 * <li>{@link #sphere} and {@link #tunnel}: the cells of a crater and of a tunnel along a segment, nearest to the centre first.</li>
 * <li>{@link #batches}: how many blocks to break per tick.</li>
 * </ul>
 */
public final class TerrainRules {
	private TerrainRules() {
	}

	/** Strength of a destruction profile. */
	public enum Profile {
		NONE, S, M, L;

		public static Profile parse(String s, Profile def) {
			if (s == null) {
				return def;
			}
			for (Profile p : values()) {
				if (p.name().equalsIgnoreCase(s.trim())) {
					return p;
				}
			}
			return def;
		}
	}

	/** Why a block may not be broken ({@link #OK}: it may). */
	public enum Verdict {
		OK, PROFILE_OFF, AIR, FLUID, UNBREAKABLE, BLOCK_ENTITY, SPECIAL, PROTECTED, TOO_HARD
	}

	/**
	 * What the server knows about one block.
	 *
	 * @param hardness the block hardness (negative: unbreakable)
	 * @param special bedrock, barrier, command / structure blocks, portals and frames, frost blocks, the ice wall
	 * @param protectedCell the world forbids the owner to change the cell (spawn protection)
	 */
	public record Facts(boolean air, boolean fluid, double hardness, boolean blockEntity, boolean special, boolean protectedCell) {
	}

	/** One destruction site of an ability (centre, radius or radii and the cap per cast). */
	public record Site(boolean enabled, Profile profile, double radius, double radius2, int cap) {
		public Site {
			radius = Math.max(0.0, Math.min(16.0, radius));
			radius2 = Math.max(0.0, Math.min(16.0, radius2));
			cap = Math.max(0, Math.min(5000, cap));
		}
	}

	/**
	 * The {@code "terrain"} section of {@code config/reiatsu_test.json}.
	 *
	 * <pre>
	 * "terrain": { "enabled": true, "ignoreMobGriefing": false, "drops": false, "blocksPerTick": 40, "effectEveryBlocks": 6,
	 *   "profiles": { "S": {"maxHardness": 0.8}, "M": {"maxHardness": 2.0}, "L": {"maxHardness": 5.0} },
	 *   "modeAttack":  {"enabled": true,  "profile": "S", "radius": 3.0, "cap": 60},
	 *   "scatter":     {"enabled": true,  "profile": "M", "craterRadius": 5.0, "cap": 400},
	 *   "hakuteiken":  {"enabled": true,  "profile": "L", "tunnelRadius": 1.5, "craterRadius": 5.0, "cap": 700},
	 *   "shakkaho":    {"enabled": false, "profile": "S", "radius": 2.5, "cap": 40} }
	 * </pre>
	 */
	public record Config(boolean enabled, boolean ignoreMobGriefing, boolean drops, int blocksPerTick, int effectEveryBlocks, double hardnessS,
			double hardnessM, double hardnessL, Site modeAttack, Site scatter, Site hakuteiken, Site shakkaho, Site uryuWings, Site uryuRain, Site uryuStar) {
		public static final String SECTION = "terrain";

		/** The shape before R15 (no Uryu sites): the three Uryu sites take their defaults. */
		public Config(boolean enabled, boolean ignoreMobGriefing, boolean drops, int blocksPerTick, int effectEveryBlocks, double hardnessS,
				double hardnessM, double hardnessL, Site modeAttack, Site scatter, Site hakuteiken, Site shakkaho) {
			this(enabled, ignoreMobGriefing, drops, blocksPerTick, effectEveryBlocks, hardnessS, hardnessM, hardnessL, modeAttack, scatter, hakuteiken, shakkaho,
					defaults().uryuWings, defaults().uryuRain, defaults().uryuStar);
		}

		public Config {
			blocksPerTick = Math.max(1, Math.min(2000, blocksPerTick));
			effectEveryBlocks = Math.max(1, Math.min(1000, effectEveryBlocks));
			hardnessS = Math.max(0.0, Math.min(100.0, hardnessS));
			hardnessM = Math.max(0.0, Math.min(100.0, hardnessM));
			hardnessL = Math.max(0.0, Math.min(100.0, hardnessL));
		}

		public static Config defaults() {
			return new Config(true, false, false, 40, 6, 0.8, 2.0, 5.0,
					new Site(true, Profile.S, 3.0, 0.0, 60),
					new Site(true, Profile.M, 5.0, 0.0, 400),
					new Site(true, Profile.L, 1.5, 5.0, 700), // radius = tunnel, radius2 = crater at the end
					new Site(false, Profile.S, 2.5, 0.0, 40),
					// R15, Uryu's bankai (the second phase) only: a wing arrow that hits a block scratches soft blocks; Licht Regen crumbles soft blocks in its square;
					// Sternenstaub bores a tunnel (radius) past the block it stops at and leaves a crater (radius2) there; medium profile: wood, stone, dirt, glass, no obsidian
					new Site(true, Profile.S, 1.3, 0.0, 10),
					new Site(true, Profile.S, 3.5, 0.0, 140),
					new Site(true, Profile.M, 1.0, 3.0, 450));
		}

		/** Hardness limit of a profile (the strongest block it may break); NONE breaks nothing. */
		public double maxHardness(Profile p) {
			return switch (p) {
				case S -> hardnessS;
				case M -> hardnessM;
				case L -> hardnessL;
				case NONE -> -1.0;
			};
		}

		public static Config fromRoot(JsonObject root) {
			Config d = defaults();
			JsonObject o = Cfg.obj(root, SECTION);
			JsonObject pr = Cfg.obj(o, "profiles");
			return new Config(
					Cfg.bool(o, "enabled", d.enabled),
					Cfg.bool(o, "ignoreMobGriefing", d.ignoreMobGriefing),
					Cfg.bool(o, "drops", d.drops),
					Cfg.integer(o, "blocksPerTick", d.blocksPerTick, 1, 2000),
					Cfg.integer(o, "effectEveryBlocks", d.effectEveryBlocks, 1, 1000),
					Cfg.num(Cfg.obj(pr, "S"), "maxHardness", d.hardnessS, 0.0, 100.0),
					Cfg.num(Cfg.obj(pr, "M"), "maxHardness", d.hardnessM, 0.0, 100.0),
					Cfg.num(Cfg.obj(pr, "L"), "maxHardness", d.hardnessL, 0.0, 100.0),
					site(Cfg.obj(o, "modeAttack"), d.modeAttack, "radius", null),
					site(Cfg.obj(o, "scatter"), d.scatter, "craterRadius", null),
					site(Cfg.obj(o, "hakuteiken"), d.hakuteiken, "tunnelRadius", "craterRadius"),
					site(Cfg.obj(o, "shakkaho"), d.shakkaho, "radius", null),
					site(Cfg.obj(o, "uryuWings"), d.uryuWings, "radius", null),
					site(Cfg.obj(o, "uryuRain"), d.uryuRain, "radius", null),
					site(Cfg.obj(o, "uryuStar"), d.uryuStar, "tunnelRadius", "craterRadius"));
		}

		private static Site site(JsonObject o, Site d, String radiusKey, String radius2Key) {
			return new Site(Cfg.bool(o, "enabled", d.enabled),
					Profile.parse(o.has("profile") && o.get("profile").isJsonPrimitive() ? o.get("profile").getAsString() : null, d.profile),
					Cfg.num(o, radiusKey, d.radius, 0.0, 16.0),
					radius2Key == null ? d.radius2 : Cfg.num(o, radius2Key, d.radius2, 0.0, 16.0),
					Cfg.integer(o, "cap", d.cap, 0, 5000));
		}

		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", enabled);
			o.addProperty("ignoreMobGriefing", ignoreMobGriefing);
			o.addProperty("drops", drops);
			o.addProperty("blocksPerTick", blocksPerTick);
			o.addProperty("effectEveryBlocks", effectEveryBlocks);
			JsonObject pr = new JsonObject();
			pr.add("S", hardness(hardnessS));
			pr.add("M", hardness(hardnessM));
			pr.add("L", hardness(hardnessL));
			o.add("profiles", pr);
			o.add("modeAttack", siteJson(modeAttack, "radius", null));
			o.add("scatter", siteJson(scatter, "craterRadius", null));
			o.add("hakuteiken", siteJson(hakuteiken, "tunnelRadius", "craterRadius"));
			o.add("shakkaho", siteJson(shakkaho, "radius", null));
			o.add("uryuWings", siteJson(uryuWings, "radius", null));
			o.add("uryuRain", siteJson(uryuRain, "radius", null));
			o.add("uryuStar", siteJson(uryuStar, "tunnelRadius", "craterRadius"));
			return o;
		}

		private static JsonObject hardness(double v) {
			JsonObject h = new JsonObject();
			h.addProperty("maxHardness", v);
			return h;
		}

		private static JsonObject siteJson(Site s, String radiusKey, String radius2Key) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", s.enabled());
			o.addProperty("profile", s.profile().name());
			o.addProperty(radiusKey, s.radius());
			if (radius2Key != null) {
				o.addProperty(radius2Key, s.radius2());
			}
			o.addProperty("cap", s.cap());
			return o;
		}
	}

	// ------------------------------------------------------------------ the rules

	/** The verdict for one block. The order is the order of the reasons in the plan (T9): what is never broken first, then the profile limit. */
	public static Verdict verdict(Config cfg, Profile profile, Facts f) {
		if (profile == Profile.NONE) {
			return Verdict.PROFILE_OFF;
		}
		if (f.air()) {
			return Verdict.AIR;
		}
		if (f.fluid()) {
			return Verdict.FLUID;
		}
		if (f.special()) {
			return Verdict.SPECIAL;
		}
		if (f.blockEntity()) {
			return Verdict.BLOCK_ENTITY;
		}
		if (f.hardness() < 0.0) {
			return Verdict.UNBREAKABLE;
		}
		if (f.protectedCell()) {
			return Verdict.PROTECTED;
		}
		if (f.hardness() > cfg.maxHardness(profile) + 1.0E-9) {
			return Verdict.TOO_HARD;
		}
		return Verdict.OK;
	}

	/**
	 * The world switches: the mod flag {@code terrain.enabled}, the vanilla {@code mobGriefing} game rule (ignored when the server says so) and the
	 * game mode of the owner (adventure and spectator do not break blocks).
	 */
	public static boolean worldAllows(Config cfg, boolean mobGriefing, boolean ownerMayBreak) {
		return cfg.enabled() && ownerMayBreak && (mobGriefing || cfg.ignoreMobGriefing());
	}

	/** Game mode of the owner: survival and creative break, adventure and spectator do not. Names are the vanilla ones ({@code survival} ...). */
	public static boolean gameModeMayBreak(String mode) {
		return mode != null && (mode.equalsIgnoreCase("survival") || mode.equalsIgnoreCase("creative"));
	}

	// ------------------------------------------------------------------ geometry

	/** A block cell. */
	public record Cell(int x, int y, int z) {
	}

	/** Cells whose centre lies within {@code radius} of (cx, cy, cz), nearest to the centre first (ties by y, x, z so the order is stable). */
	public static List<Cell> sphere(double cx, double cy, double cz, double radius) {
		return around(cx, cy, cz, cx, cy, cz, radius);
	}

	/**
	 * Cells whose centre lies within {@code radius} of the segment a-b, ordered by distance along the segment first, then from the axis: the
	 * tunnel opens from its start to its end.
	 */
	public static List<Cell> tunnel(double ax, double ay, double az, double bx, double by, double bz, double radius) {
		return around(ax, ay, az, bx, by, bz, radius);
	}

	private static List<Cell> around(double ax, double ay, double az, double bx, double by, double bz, double radius) {
		List<Cell> out = new ArrayList<>();
		if (radius <= 0.0) {
			return out;
		}
		int x0 = (int) Math.floor(Math.min(ax, bx) - radius);
		int x1 = (int) Math.floor(Math.max(ax, bx) + radius);
		int y0 = (int) Math.floor(Math.min(ay, by) - radius);
		int y1 = (int) Math.floor(Math.max(ay, by) + radius);
		int z0 = (int) Math.floor(Math.min(az, bz) - radius);
		int z1 = (int) Math.floor(Math.max(az, bz) + radius);
		double dx = bx - ax;
		double dy = by - ay;
		double dz = bz - az;
		double len2 = dx * dx + dy * dy + dz * dz;
		double r2 = radius * radius;
		List<double[]> keyed = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					double px = x + 0.5 - ax;
					double py = y + 0.5 - ay;
					double pz = z + 0.5 - az;
					double t = len2 < 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, (px * dx + py * dy + pz * dz) / len2));
					double qx = px - dx * t;
					double qy = py - dy * t;
					double qz = pz - dz * t;
					double d2 = qx * qx + qy * qy + qz * qz;
					if (d2 <= r2) {
						keyed.add(new double[] {t, d2, x, y, z});
					}
				}
			}
		}
		keyed.sort(Comparator.<double[]>comparingDouble(k -> k[0]).thenComparingDouble(k -> k[1]).thenComparingDouble(k -> k[3])
				.thenComparingDouble(k -> k[2]).thenComparingDouble(k -> k[4]));
		for (double[] k : keyed) {
			out.add(new Cell((int) k[2], (int) k[3], (int) k[4]));
		}
		return out;
	}

	/** Keeps the first {@code limit} cells (limit below 1: none). */
	public static <T> List<T> capped(List<T> cells, int limit) {
		if (limit <= 0) {
			return new ArrayList<>();
		}
		return cells.size() <= limit ? cells : new ArrayList<>(cells.subList(0, limit));
	}

	/** The part of a cap that is still free after {@code already} blocks of the same cast were broken. */
	public static int remaining(int cap, int already) {
		return Math.max(0, cap - Math.max(0, already));
	}

	/** Ticks a job of {@code total} blocks needs at {@code perTick} blocks per tick. */
	public static int budgetTicks(int total, int perTick) {
		return total <= 0 ? 0 : (total + Math.max(1, perTick) - 1) / Math.max(1, perTick);
	}

	/** Whether the block number {@code index} (0-based) of a job plays the break sound and particles: the first and then every {@code every}th. */
	public static boolean effectAt(int index, int every) {
		return index % Math.max(1, every) == 0;
	}
}
