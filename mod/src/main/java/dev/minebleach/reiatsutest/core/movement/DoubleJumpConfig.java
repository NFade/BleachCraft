package dev.minebleach.reiatsutest.core.movement;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;

/**
 * The {@code "doubleJump"} section of {@code config/reiatsu_test.json}. Read by the client (the movement is client-authoritative).
 *
 * @param enabled false switches the double jump off for the local player (default true)
 * @param strength multiplier of the vanilla jump velocity (jump strength 0.42 plus Jump Boost) given by the air jump; 1.0 = a normal jump
 *                 (default 1.0, clamped to 0.1 .. 2.5)
 */
public record DoubleJumpConfig(boolean enabled, double strength) {
	public static final String SECTION = "doubleJump";
	public static final double MIN_STRENGTH = 0.1;
	public static final double MAX_STRENGTH = 2.5;

	public DoubleJumpConfig {
		if (Double.isNaN(strength) || Double.isInfinite(strength)) {
			strength = 1.0;
		}
		strength = Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength));
	}

	public static DoubleJumpConfig defaults() {
		return new DoubleJumpConfig(true, 1.0);
	}

	/** Missing or malformed keys give the defaults, the strength is clamped; never throws. */
	public static DoubleJumpConfig fromRoot(JsonObject root) {
		DoubleJumpConfig d = defaults();
		JsonObject o = Cfg.obj(root, SECTION);
		return new DoubleJumpConfig(Cfg.bool(o, "enabled", d.enabled), Cfg.num(o, "strength", d.strength, MIN_STRENGTH, MAX_STRENGTH));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("strength", strength);
		return o;
	}
}
