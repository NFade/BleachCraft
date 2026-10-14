package dev.minebleach.reiatsutest.core.kido;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.EnumMap;
import java.util.Map;

/**
 * The {@code "kido"} section of {@code config/reiatsu_test.json} (B5): one switch, the gap between two casts and one object
 * per spell with its {@link KidoSpec} numbers. Everything has a default; {@link #fromRoot} reads what the file has, falls back
 * to the default for a missing, malformed or non-finite value and clamps the rest, so a hand-edited file cannot break the
 * game. Pure Java (Gson is the only dependency, like {@code VoiceConfig}).
 *
 * <pre>
 * "kido": { "enabled": true, "gcdTicks": 10,
 *           "byakurai": { "enabled": true, "costTenths": 100, "cooldownTicks": 60, "range": 30.0, "damage": 1.25, "width": 0.35 }, ... }
 * </pre>
 */
public final class KidoConfig {
	public static final String SECTION = "kido";

	private final boolean enabled;
	private final int gcdTicks;
	private final Map<KidoId, KidoSpec> specs;

	/** {@code specs} must hold every spell. */
	public KidoConfig(boolean enabled, int gcdTicks, Map<KidoId, KidoSpec> specs) {
		this.enabled = enabled;
		this.gcdTicks = Math.max(0, Math.min(200, gcdTicks));
		EnumMap<KidoId, KidoSpec> m = new EnumMap<>(KidoId.class);
		for (KidoId id : KidoId.values()) {
			KidoSpec s = specs.get(id);
			if (s == null) {
				throw new IllegalArgumentException("no spec for " + id);
			}
			m.put(id, s);
		}
		this.specs = m;
	}

	/**
	 * Default balance (UNVERIFIED by play, see design/results/kido.md). Reiatsu is 100.0 and regenerates 4.0 per second when
	 * sealed or drawn, so Byakurai costs 2.5 s of regeneration, Shakkaho 5 s and Sai 1.5 s.
	 */
	public static KidoConfig defaults() {
		EnumMap<KidoId, KidoSpec> m = new EnumMap<>(KidoId.class);
		// Hado 4: instant thin beam, 1.25 hp (0.6 hearts; r8 item 4 halved the 2.5 of the 2026-10-12 rebalance), 30 blocks, 5 s cooldown
		m.put(KidoId.BYAKURAI, new KidoSpec(KidoId.BYAKURAI, true, 100, 100, 30.0, 1.25, 0.0, 0, 0, 0.0, 0.35));
		// Hado 31: a ball at 1.2 blocks/tick (24 blocks/s) to the first block or target within 24 blocks, burst radius 3.5,
		// 1.0 hp in the centre (half a heart, R11 T4; r8 item 4 had 2.0) falling to 40 percent at the edge, targets burn 15 ticks (0.75 s, short of the 20-tick fire damage step), no block damage, 10 s cooldown
		m.put(KidoId.SHAKKAHO, new KidoSpec(KidoId.SHAKKAHO, true, 200, 200, 24.0, 1.0, 3.5, 0, 15, 1.2, 0.5));
		// Bakudo 1: first target within 8 blocks cannot move for 3 s (slowness to a standstill), no damage, 5 s cooldown
		m.put(KidoId.SAI, new KidoSpec(KidoId.SAI, true, 60, 100, 8.0, 0.0, 0.0, 60, 0, 0.0, 0.4));
		return new KidoConfig(true, 10, m);
	}

	public boolean enabled() {
		return enabled;
	}

	/** Minimum gap in ticks between two kido casts (any spells). */
	public int gcdTicks() {
		return gcdTicks;
	}

	public KidoSpec spec(KidoId id) {
		return specs.get(id);
	}

	// ------------------------------------------------------------------ json

	/** Reads the "kido" object of the root config (missing = defaults). */
	public static KidoConfig fromRoot(JsonObject root) {
		if (root == null || !root.has(SECTION) || !root.get(SECTION).isJsonObject()) {
			return defaults();
		}
		return fromJson(root.getAsJsonObject(SECTION));
	}

	public static KidoConfig fromJson(JsonObject o) {
		KidoConfig d = defaults();
		boolean on = bool(o, "enabled", d.enabled);
		int gcd = (int) num(o, "gcdTicks", d.gcdTicks, 0, 200);
		EnumMap<KidoId, KidoSpec> m = new EnumMap<>(KidoId.class);
		for (KidoId id : KidoId.values()) {
			KidoSpec def = d.spec(id);
			JsonObject s = o.has(id.key) && o.get(id.key).isJsonObject() ? o.getAsJsonObject(id.key) : new JsonObject();
			m.put(id, new KidoSpec(id,
					bool(s, "enabled", def.enabled()),
					(int) num(s, "costTenths", def.costTenths(), 0, 1000),
					(int) num(s, "cooldownTicks", def.cooldownTicks(), 0, 12000),
					num(s, "range", def.range(), 1.0, 64.0),
					num(s, "damage", def.damage(), 0.0, 100.0),
					num(s, "radius", def.radius(), 0.0, 16.0),
					(int) num(s, "statusTicks", def.statusTicks(), 0, 1200),
					(int) num(s, "fireTicks", def.fireTicks(), 0, 600),
					num(s, "speed", def.speed(), 0.0, 4.0),
					num(s, "width", def.width(), 0.05, 3.0)));
		}
		return new KidoConfig(on, gcd, m);
	}

	/** The "kido" object of this config, for writing the defaults into a fresh file. */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("gcdTicks", gcdTicks);
		for (KidoId id : KidoId.values()) {
			KidoSpec s = specs.get(id);
			JsonObject j = new JsonObject();
			j.addProperty("enabled", s.enabled());
			j.addProperty("costTenths", s.costTenths());
			j.addProperty("cooldownTicks", s.cooldownTicks());
			j.addProperty("range", s.range());
			j.addProperty("damage", s.damage());
			j.addProperty("radius", s.radius());
			j.addProperty("statusTicks", s.statusTicks());
			j.addProperty("fireTicks", s.fireTicks());
			j.addProperty("speed", s.speed());
			j.addProperty("width", s.width());
			o.add(id.key, j);
		}
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
