package dev.minebleach.reiatsutest.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * R11 T0: the tolerant reads every config section uses. A missing, malformed or non-finite value gives the default, a number is clamped
 * to its range, so a hand-edited file cannot break the game (same rules as {@code KidoConfig}). Pure Java: Gson only.
 */
public final class Cfg {
	private Cfg() {
	}

	/** The child object {@code key} of {@code o}, or an empty object when missing / not an object. */
	public static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e instanceof JsonObject j ? j : new JsonObject();
	}

	public static boolean bool(JsonObject o, String key, boolean def) {
		JsonElement e = o == null ? null : o.get(key);
		if (e instanceof JsonPrimitive p && p.isBoolean()) {
			return p.getAsBoolean();
		}
		return def;
	}

	public static double num(JsonObject o, String key, double def, double lo, double hi) {
		JsonElement e = o == null ? null : o.get(key);
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			double v = p.getAsDouble();
			if (Double.isNaN(v) || Double.isInfinite(v)) {
				return def;
			}
			return Math.max(lo, Math.min(hi, v));
		}
		return def;
	}

	public static int integer(JsonObject o, String key, int def, int lo, int hi) {
		return (int) Math.round(num(o, key, def, lo, hi));
	}
}
