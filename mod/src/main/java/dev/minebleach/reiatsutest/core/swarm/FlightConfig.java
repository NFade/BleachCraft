package dev.minebleach.reiatsutest.core.swarm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The {@code "bladeFlight"} section of {@code config/reiatsu_test.json}: numbers of the Senbonzakura attack swarm flight
 * (steering with the held key, damage along the flight). Speeds are blocks per second, times are ticks (20 per second), angles
 * are degrees, damage is HP (1 HP = half a heart). Everything has a default; {@link #fromRoot} falls back to the default for a
 * missing, malformed or non-finite value and clamps the rest, so a hand-edited file cannot break the game. Pure Java (Gson only).
 *
 * <pre>
 * "bladeFlight": { "enabled": true, "steerEnabled": true, "turnDegPerSec": 200.0, "speed": 30.0, "hitDamage": 1.0, ... }
 * </pre>
 *
 * @param enabled              false = the swarm has no server flight at all (the old fixed aim and the landing damage only)
 * @param steerEnabled         false = the flight and its damage exist but holding the key does not steer
 * @param startDelayTicks      ticks after the cast before the blades leave the hand
 * @param speed                outward speed, also the largest sideways speed while steering (blocks per second)
 * @param backSpeed            speed of the return to the caster
 * @param turnDegPerSec        largest turn of the swarm direction around the caster (degrees per second), at close range
 * @param minRange             closest distance to the caster's eye while out
 * @param returnTick           tick after the cast at which the swarm turns back (the old fixed envelope ends at 30)
 * @param maxTicks             longest time out while the key is held (hard limit of the flight, then it returns)
 * @param hitRadius            radius of the damage sphere around the swarm head
 * @param hitDamage            damage of one hit in flight (less than the landing hit)
 * @param hitCooldownTicks     ticks before the same target can be hit again in the same leg
 * @param maxHitsPerTarget     most flight hits (landing hits included) on one target per cast
 * @param maxTargetsPerTick    most targets hurt in one tick
 * @param maxSweepPerTick      a head step longer than this (caster teleport) is not swept
 * @param steerTimeoutTicks    steering stops when no steer packet arrived for this long
 * @param steerRatePerSecond   packets per second a client may send (excess is dropped)
 * @param steerLookToleranceDeg a sent direction further than this from the server's own look vector is replaced by it
 * @param sendEveryTicks       ticks between head position packets to the clients
 */
public record FlightConfig(boolean enabled, boolean steerEnabled, int startDelayTicks, double speed, double backSpeed, double turnDegPerSec,
		double minRange, int returnTick, int maxTicks, double hitRadius, double hitDamage, int hitCooldownTicks, int maxHitsPerTarget,
		int maxTargetsPerTick, double maxSweepPerTick, int steerTimeoutTicks, int steerRatePerSecond, double steerLookToleranceDeg,
		int sendEveryTicks) {
	public static final String SECTION = "bladeFlight";

	/**
	 * Round 5 timing (range 19 from BalanceConfig): blades leave after 3 ticks at 40 b/s (about 0.45 s to the far point), return at tick 18 when the key is
	 * not held (whole cycle about 1.4 s), at tick 32 in any case (held: about 2.0 s with the return). UNVERIFIED by play: damage 1.0 per hit against 2.0 per landing hit.
	 */
	public static FlightConfig defaults() {
		return new FlightConfig(true, true, 3, 40.0, 40.0, 240.0, 1.5, 18, 32, 1.6, 0.5, 8, 6, 8, 6.0, 6, 20, 35.0, 2);
	}

	public double speedPerTick() {
		return speed / 20.0;
	}

	public double backSpeedPerTick() {
		return backSpeed / 20.0;
	}

	public double turnRadPerTick() {
		return Math.toRadians(turnDegPerSec) / 20.0;
	}

	// ------------------------------------------------------------------ json

	public static FlightConfig fromRoot(JsonObject root) {
		if (root == null || !root.has(SECTION) || !root.get(SECTION).isJsonObject()) {
			return defaults();
		}
		return fromJson(root.getAsJsonObject(SECTION));
	}

	public static FlightConfig fromJson(JsonObject o) {
		FlightConfig d = defaults();
		return new FlightConfig(
				bool(o, "enabled", d.enabled),
				bool(o, "steerEnabled", d.steerEnabled),
				(int) num(o, "startDelayTicks", d.startDelayTicks, 0, 40),
				num(o, "speed", d.speed, 2.0, 80.0),
				num(o, "backSpeed", d.backSpeed, 2.0, 80.0),
				num(o, "turnDegPerSec", d.turnDegPerSec, 10.0, 1080.0),
				num(o, "minRange", d.minRange, 0.5, 8.0),
				(int) num(o, "returnTick", d.returnTick, 5, 200),
				(int) num(o, "maxTicks", d.maxTicks, 5, 400),
				num(o, "hitRadius", d.hitRadius, 0.3, 6.0),
				num(o, "hitDamage", d.hitDamage, 0.0, 40.0),
				(int) num(o, "hitCooldownTicks", d.hitCooldownTicks, 1, 100),
				(int) num(o, "maxHitsPerTarget", d.maxHitsPerTarget, 1, 100),
				(int) num(o, "maxTargetsPerTick", d.maxTargetsPerTick, 1, 64),
				num(o, "maxSweepPerTick", d.maxSweepPerTick, 1.0, 64.0),
				(int) num(o, "steerTimeoutTicks", d.steerTimeoutTicks, 2, 40),
				(int) num(o, "steerRatePerSecond", d.steerRatePerSecond, 1, 100),
				num(o, "steerLookToleranceDeg", d.steerLookToleranceDeg, 1.0, 180.0),
				(int) num(o, "sendEveryTicks", d.sendEveryTicks, 1, 10));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("steerEnabled", steerEnabled);
		o.addProperty("startDelayTicks", startDelayTicks);
		o.addProperty("speed", speed);
		o.addProperty("backSpeed", backSpeed);
		o.addProperty("turnDegPerSec", turnDegPerSec);
		o.addProperty("minRange", minRange);
		o.addProperty("returnTick", returnTick);
		o.addProperty("maxTicks", maxTicks);
		o.addProperty("hitRadius", hitRadius);
		o.addProperty("hitDamage", hitDamage);
		o.addProperty("hitCooldownTicks", hitCooldownTicks);
		o.addProperty("maxHitsPerTarget", maxHitsPerTarget);
		o.addProperty("maxTargetsPerTick", maxTargetsPerTick);
		o.addProperty("maxSweepPerTick", maxSweepPerTick);
		o.addProperty("steerTimeoutTicks", steerTimeoutTicks);
		o.addProperty("steerRatePerSecond", steerRatePerSecond);
		o.addProperty("steerLookToleranceDeg", steerLookToleranceDeg);
		o.addProperty("sendEveryTicks", sendEveryTicks);
		return o;
	}

	public void writeInto(JsonObject root) {
		root.add(SECTION, toJson());
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
