package dev.minebleach.reiatsutest.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * R14: the key list behind {@code /reiatsu dev get|set}: the number and switch leaves of a config object as dotted paths, a typed parse of the
 * text a person typed, and the write of one leaf. Pure Java: Gson only.
 */
public final class ConfigKeys {
	/** The sections that can be changed on a running server (voice and frost own sockets / world state and need a restart). */
	public static final List<String> SECTIONS = List.of("balance", "rage", "mech", "uryu", "kido", "terrain", "rukia", "bladeFlight", "doubleJump");

	private ConfigKeys() {
	}

	/** True when the first part of {@code key} is a section {@link #SECTIONS} lists. */
	public static boolean allowed(String key) {
		int dot = key == null ? -1 : key.indexOf('.');
		return dot > 0 && SECTIONS.contains(key.substring(0, dot));
	}

	/** Adds every number and boolean leaf of {@code o} to {@code out} as {@code prefix + path}. Arrays and strings are skipped. */
	public static void flatten(String prefix, JsonObject o, Map<String, JsonPrimitive> out) {
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
			JsonElement v = e.getValue();
			if (v.isJsonObject()) {
				flatten(path, v.getAsJsonObject(), out);
			} else if (v.isJsonPrimitive() && (v.getAsJsonPrimitive().isNumber() || v.getAsJsonPrimitive().isBoolean())) {
				out.put(path, v.getAsJsonPrimitive());
			}
		}
	}

	/** The leaves of the sections of {@code root} that can be changed, sorted by path. */
	public static Map<String, JsonPrimitive> leaves(JsonObject root) {
		Map<String, JsonPrimitive> out = new TreeMap<>();
		for (String s : SECTIONS) {
			JsonElement e = root.get(s);
			if (e != null && e.isJsonObject()) {
				flatten(s, e.getAsJsonObject(), out);
			}
		}
		return out;
	}

	/**
	 * What the text means for a key whose current value is {@code like}: a switch takes true / false / on / off / 1 / 0, a number takes a decimal.
	 *
	 * @throws IllegalArgumentException with a message fit for the chat
	 */
	public static JsonPrimitive parse(String text, JsonPrimitive like) {
		String t = text.trim().toLowerCase(Locale.ROOT);
		if (like.isBoolean()) {
			return switch (t) {
				case "true", "on", "1", "yes" -> new JsonPrimitive(true);
				case "false", "off", "0", "no" -> new JsonPrimitive(false);
				default -> throw new IllegalArgumentException("a switch: use true or false, not '" + text + "'");
			};
		}
		double d;
		try {
			d = Double.parseDouble(t);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("a number is needed, not '" + text + "'");
		}
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			throw new IllegalArgumentException("a finite number is needed");
		}
		double cur = like.getAsDouble();
		boolean whole = cur == Math.rint(cur) && d == Math.rint(d) && Math.abs(d) < 1e9;
		return whole ? new JsonPrimitive((long) d) : new JsonPrimitive(d);
	}

	/** Writes {@code value} at the dotted {@code path} of {@code root}, creating the objects on the way. */
	public static void setPath(JsonObject root, String path, JsonPrimitive value) {
		String[] parts = path.split("\\.");
		JsonObject o = root;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonObject next = o.has(parts[i]) && o.get(parts[i]).isJsonObject() ? o.getAsJsonObject(parts[i]) : new JsonObject();
			o.add(parts[i], next);
			o = next;
		}
		o.add(parts[parts.length - 1], value);
	}

	/** A short text of a leaf: a switch as true / false, a whole number without a point, a fraction with at most three decimals. */
	public static String show(JsonPrimitive p) {
		if (p.isBoolean()) {
			return Boolean.toString(p.getAsBoolean());
		}
		double d = p.getAsDouble();
		if (d == Math.rint(d) && Math.abs(d) < 1e9) {
			return Long.toString((long) d);
		}
		String s = String.format(Locale.ROOT, "%.3f", d);
		return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
	}
}
