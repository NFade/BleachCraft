package dev.minebleach.reiatsutest.core.world;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;

/**
 * The {@code "rukia.iceWall"} section of {@code config/reiatsu_test.json} (R11 T5, variant W "ice wall"): the geometry and the life of the
 * wall. Cost, cooldown and cast time are in {@code BalanceConfig} (ability {@code rukia.bankai.ice_wall}: 18 reiatsu, 14 s, 14 ticks).
 * Every number has a default and is clamped when read. Pure Java.
 *
 * <pre>
 * "rukia": { "iceWall": { "enabled": true, "width": 9, "height": 5, "thickness": 2, "lifeTicks": 200, "growTicks": 10, "fadeTicks": 10,
 *            "hp": 60.0, "maxBlocks": 160, "searchDown": 6, "fillDown": 2 } }
 * </pre>
 *
 * <ul>
 * <li>{@code width x height x thickness}: blocks across, up and deep (thickness 2 so nobody climbs past it diagonally or jumps over one layer);</li>
 * <li>{@code lifeTicks}: how long it stands (200 = 10 s), {@code growTicks}: bottom to top growth (10 = 0.5 s), {@code fadeTicks}: the top layers
 * vanish one after the other during the last ticks;</li>
 * <li>{@code hp}: strength of the whole wall. An ability that hits it spends its damage; at 0 the wall breaks. Vanilla explosions and tools do not
 * hurt it (the block has strength -1);</li>
 * <li>{@code maxBlocks}: wall blocks one player may have in the temporary block journal; {@code searchDown}: how far below the aimed point the
 * ground is looked for; {@code fillDown}: how far a column may reach down into a dip so no gap is left under the wall.</li>
 * </ul>
 */
public record IceWallConfig(boolean enabled, int width, int height, int thickness, int lifeTicks, int growTicks, int fadeTicks, double hp,
		int maxBlocks, int searchDown, int fillDown) {
	public static final String SECTION = "rukia";
	public static final String SUB = "iceWall";

	public IceWallConfig {
		width = clampI(width, 1, 25);
		height = clampI(height, 1, 12);
		thickness = clampI(thickness, 1, 4);
		lifeTicks = clampI(lifeTicks, 20, 6000);
		growTicks = clampI(growTicks, 1, 100);
		fadeTicks = clampI(fadeTicks, 0, 100);
		hp = Math.max(1.0, Math.min(100000.0, hp));
		maxBlocks = clampI(maxBlocks, 8, 2000);
		searchDown = clampI(searchDown, 0, 16);
		fillDown = clampI(fillDown, 0, 6);
	}

	public static IceWallConfig defaults() {
		return new IceWallConfig(true, 9, 5, 2, 200, 10, 10, 60.0, 160, 6, 2);
	}

	public static IceWallConfig fromRoot(JsonObject root) {
		IceWallConfig d = defaults();
		JsonObject o = Cfg.obj(Cfg.obj(root, SECTION), SUB);
		return new IceWallConfig(
				Cfg.bool(o, "enabled", d.enabled),
				Cfg.integer(o, "width", d.width, 1, 25),
				Cfg.integer(o, "height", d.height, 1, 12),
				Cfg.integer(o, "thickness", d.thickness, 1, 4),
				Cfg.integer(o, "lifeTicks", d.lifeTicks, 20, 6000),
				Cfg.integer(o, "growTicks", d.growTicks, 1, 100),
				Cfg.integer(o, "fadeTicks", d.fadeTicks, 0, 100),
				Cfg.num(o, "hp", d.hp, 1.0, 100000.0),
				Cfg.integer(o, "maxBlocks", d.maxBlocks, 8, 2000),
				Cfg.integer(o, "searchDown", d.searchDown, 0, 16),
				Cfg.integer(o, "fillDown", d.fillDown, 0, 6));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("width", width);
		o.addProperty("height", height);
		o.addProperty("thickness", thickness);
		o.addProperty("lifeTicks", lifeTicks);
		o.addProperty("growTicks", growTicks);
		o.addProperty("fadeTicks", fadeTicks);
		o.addProperty("hp", hp);
		o.addProperty("maxBlocks", maxBlocks);
		o.addProperty("searchDown", searchDown);
		o.addProperty("fillDown", fillDown);
		return o;
	}

	private static int clampI(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
