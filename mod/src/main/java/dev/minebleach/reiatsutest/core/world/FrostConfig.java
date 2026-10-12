package dev.minebleach.reiatsutest.core.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The {@code "frost"} section of {@code config/reiatsu_test.json}: the territory freeze of the Rukia bankai (Hakka no Togame).
 * A ring of frost runs outward from her at {@link #waveSpeed()} blocks per second up to {@link #radius()} and replaces the
 * surface blocks with the mod's frost blocks; they are restored when the bankai ends (never later than {@link #maxTicks()}).
 * {@code orphanSweep}: when a chunk loads, frost blocks that no journal entry knows about (a crash lost the journal) are replaced by a plain
 * fallback block (dirt, stone, oak leaves, water, air) so frost can never stay in the world for good.
 * Every number has a default and is clamped when read, so a hand edited file cannot break the game. Pure Java.
 *
 * <pre>
 * "frost": { "enabled": true, "radius": 30, "waveSpeed": 12.0, "maxTicks": 900, "up": 10, "down": 14, "maxPerColumn": 5,
 *            "columnsPerTick": 220, "blocksPerTick": 450, "maxBlocks": 14000, "maxTotalBlocks": 40000,
 *            "restoreBlocksPerTick": 600, "fastRestoreBlocksPerTick": 3000, "chunkLoadCost": 200, "saveEveryTicks": 10, "thawSpeed": 20.0, "orphanSweep": true }
 * </pre>
 */
public record FrostConfig(boolean enabled, int radius, double waveSpeed, int maxTicks, int up, int down, int maxPerColumn,
		int columnsPerTick, int blocksPerTick, int maxBlocks, int maxTotalBlocks, int restoreBlocksPerTick,
		int fastRestoreBlocksPerTick, int chunkLoadCost, int saveEveryTicks, double thawSpeed, boolean orphanSweep) {
	public static final String SECTION = "frost";

	public FrostConfig {
		radius = clampI(radius, 4, 64);
		waveSpeed = Math.max(1.0, Math.min(60.0, waveSpeed));
		maxTicks = clampI(maxTicks, 20, 6000);
		up = clampI(up, 1, 40);
		down = clampI(down, 1, 40);
		maxPerColumn = clampI(maxPerColumn, 1, 12);
		columnsPerTick = clampI(columnsPerTick, 8, 2000);
		blocksPerTick = clampI(blocksPerTick, 16, 5000);
		maxBlocks = clampI(maxBlocks, 100, 200000);
		maxTotalBlocks = clampI(maxTotalBlocks, maxBlocks, 400000);
		restoreBlocksPerTick = clampI(restoreBlocksPerTick, 16, 20000);
		fastRestoreBlocksPerTick = clampI(fastRestoreBlocksPerTick, restoreBlocksPerTick, 50000);
		chunkLoadCost = clampI(chunkLoadCost, 1, 5000);
		saveEveryTicks = clampI(saveEveryTicks, 1, 1200);
		thawSpeed = Math.max(2.0, Math.min(120.0, thawSpeed));
	}

	/**
	 * Defaults. Radius 30 at 12 blocks per second: the ring reaches the rim after 2.5 s. About 2800 columns, a few thousand blocks
	 * at most per cast; the per tick budgets spread them over the wave so no tick places more than {@code blocksPerTick}
	 * (UNVERIFIED by play: the numbers are sized for 450 setBlockState calls per tick).
	 */
	public static FrostConfig defaults() {
		return new FrostConfig(true, 30, 12.0, 900, 10, 14, 5, 220, 450, 14000, 40000, 600, 3000, 200, 10, 20.0, true);
	}

	/** Wave radius (blocks) after {@code ageTicks} server ticks, capped at {@link #radius()}. */
	public double waveRadiusAt(int ageTicks) {
		return Math.min(radius, Math.max(0, ageTicks) * waveSpeed / 20.0);
	}

	/** Outer radius that may still be frozen {@code thawTicks} ticks into a normal thaw (the thaw ring runs inward at {@link #thawSpeed()}). */
	public double thawRadiusAt(int thawTicks) {
		return Math.max(0.0, radius - Math.max(0, thawTicks) * thawSpeed / 20.0);
	}

	/** Ticks until the ring has reached the rim. */
	public int waveTicks() {
		return (int) Math.ceil(radius / waveSpeed * 20.0);
	}

	// ------------------------------------------------------------------ json

	public static FrostConfig fromRoot(JsonObject root) {
		if (root == null || !root.has(SECTION) || !root.get(SECTION).isJsonObject()) {
			return defaults();
		}
		return fromJson(root.getAsJsonObject(SECTION));
	}

	public static FrostConfig fromJson(JsonObject o) {
		FrostConfig d = defaults();
		return new FrostConfig(
				bool(o, "enabled", d.enabled),
				(int) num(o, "radius", d.radius, 4, 64),
				num(o, "waveSpeed", d.waveSpeed, 1.0, 60.0),
				(int) num(o, "maxTicks", d.maxTicks, 20, 6000),
				(int) num(o, "up", d.up, 1, 40),
				(int) num(o, "down", d.down, 1, 40),
				(int) num(o, "maxPerColumn", d.maxPerColumn, 1, 12),
				(int) num(o, "columnsPerTick", d.columnsPerTick, 8, 2000),
				(int) num(o, "blocksPerTick", d.blocksPerTick, 16, 5000),
				(int) num(o, "maxBlocks", d.maxBlocks, 100, 200000),
				(int) num(o, "maxTotalBlocks", d.maxTotalBlocks, 100, 400000),
				(int) num(o, "restoreBlocksPerTick", d.restoreBlocksPerTick, 16, 20000),
				(int) num(o, "fastRestoreBlocksPerTick", d.fastRestoreBlocksPerTick, 16, 50000),
				(int) num(o, "chunkLoadCost", d.chunkLoadCost, 1, 5000),
				(int) num(o, "saveEveryTicks", d.saveEveryTicks, 1, 1200),
				num(o, "thawSpeed", d.thawSpeed, 2.0, 120.0),
				bool(o, "orphanSweep", d.orphanSweep));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("radius", radius);
		o.addProperty("waveSpeed", waveSpeed);
		o.addProperty("maxTicks", maxTicks);
		o.addProperty("up", up);
		o.addProperty("down", down);
		o.addProperty("maxPerColumn", maxPerColumn);
		o.addProperty("columnsPerTick", columnsPerTick);
		o.addProperty("blocksPerTick", blocksPerTick);
		o.addProperty("maxBlocks", maxBlocks);
		o.addProperty("maxTotalBlocks", maxTotalBlocks);
		o.addProperty("restoreBlocksPerTick", restoreBlocksPerTick);
		o.addProperty("fastRestoreBlocksPerTick", fastRestoreBlocksPerTick);
		o.addProperty("chunkLoadCost", chunkLoadCost);
		o.addProperty("saveEveryTicks", saveEveryTicks);
		o.addProperty("thawSpeed", thawSpeed);
		o.addProperty("orphanSweep", orphanSweep);
		return o;
	}

	public void writeInto(JsonObject root) {
		root.add(SECTION, toJson());
	}

	private static int clampI(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static boolean bool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		if (e instanceof JsonPrimitive p && p.isBoolean()) {
			return p.getAsBoolean();
		}
		return def;
	}

	private static double num(JsonObject o, String key, double def, double lo, double hi) {
		JsonElement e = o.get(key);
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			double v = p.getAsDouble();
			if (Double.isNaN(v) || Double.isInfinite(v)) {
				return def;
			}
			return Math.max(lo, Math.min(hi, v));
		}
		return def;
	}
}
