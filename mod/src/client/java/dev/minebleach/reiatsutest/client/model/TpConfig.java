package dev.minebleach.reiatsutest.client.model;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * R11 T10: the third person pose numbers a player may set without rebuilding, section {@code "tp"} of {@code config/reiatsu_test_client.json}:
 * <pre>
 * "tp": {
 *   "swordScale": 1.25,                        // size of the sword in the hand / block / hold and of the scabbard, times the item display scale (1.6)
 *   "swordScale.senbonzakura": 1.3,            // optional, per item (registry path), wins over swordScale
 *   "guard.shift": [1.45, -0.35, 0.55]         // optional, shift of the sword in the block (px, body space), replaces the shipped shift
 * }
 * </pre>
 * Flat dotted keys, the same names as the dev file {@code pose_override.json} section {@code tp} (which still wins in a development
 * environment). Only {@code swordScale} is written to a file that has no section; the others are optional so that the shipped values can change
 * without an old file hiding them (R11 T0). The file is re-read when it changes (checked once a second), a bad value keeps the default, a file
 * that does not parse is never overwritten. Third person only: the first person sword and the inventory icon keep their own display transforms.
 */
public final class TpConfig {
	/** Default of {@code tp.swordScale}: the sword and the scabbard of a player seen from outside are a quarter larger. */
	public static final double DEFAULT_SWORD_SCALE = 1.25;
	public static final double MIN_SWORD_SCALE = 0.5;
	public static final double MAX_SWORD_SCALE = 2.5;
	/** Largest shift (px) of a component of {@code guard.shift} that is accepted. */
	private static final double MAX_SHIFT_PX = 8.0;

	private static volatile JsonObject section = new JsonObject();
	private static long lastCheckNanos;
	private static long lastStamp = Long.MIN_VALUE;

	private TpConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("reiatsu_test_client.json");
	}

	/** Client start: adds the {@code tp} section with {@code swordScale} to an existing file that has none, then reads it. */
	public static void load() {
		Path f = file();
		try {
			if (Files.isRegularFile(f)) {
				JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				if (el.isJsonObject() && !el.getAsJsonObject().has("tp")) {
					JsonObject root = el.getAsJsonObject();
					JsonObject tp = new JsonObject();
					tp.addProperty("swordScale", DEFAULT_SWORD_SCALE);
					root.add("tp", tp);
					Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
				}
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[tp] cannot add the tp section to {}: {}", f, e.toString());
		}
		lastStamp = Long.MIN_VALUE;
		lastCheckNanos = 0L;
		poll();
	}

	/** Re-reads the section when the file changed (at most once a second). Render thread. */
	public static void poll() {
		long now = System.nanoTime();
		if (lastCheckNanos != 0L && now - lastCheckNanos < 1_000_000_000L) {
			return;
		}
		lastCheckNanos = now;
		Path f = file();
		try {
			if (!Files.isRegularFile(f)) {
				section = new JsonObject();
				lastStamp = Long.MIN_VALUE;
				return;
			}
			long stamp = Files.getLastModifiedTime(f).toMillis() ^ Files.size(f);
			if (stamp == lastStamp) {
				return;
			}
			lastStamp = stamp;
			JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			JsonObject tp = el.isJsonObject() && el.getAsJsonObject().get("tp") instanceof JsonObject o ? o : new JsonObject();
			section = tp;
			ReiatsuTest.LOGGER.info("[tp] config {}", tp);
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[tp] cannot read {}: {} (keeping the last values)", f, e.toString());
		}
	}

	/** Number {@code key} of the section; {@code def} when missing, not a finite number. */
	public static double num(String key, double def) {
		JsonElement e = section.get(key);
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			double v = p.getAsDouble();
			if (!Double.isNaN(v) && !Double.isInfinite(v)) {
				return v;
			}
		}
		return def;
	}

	/** Three numbers {@code key}; {@code def} when missing or not an array of three finite numbers (each within +-8 px). */
	public static double[] vec(String key, double[] def) {
		JsonElement e = section.get(key);
		if (e instanceof JsonArray a && a.size() >= 3) {
			double[] r = new double[3];
			for (int i = 0; i < 3; i++) {
				if (!(a.get(i) instanceof JsonPrimitive p) || !p.isNumber()) {
					return def;
				}
				double v = p.getAsDouble();
				if (Double.isNaN(v) || Double.isInfinite(v)) {
					return def;
				}
				r[i] = Math.max(-MAX_SHIFT_PX, Math.min(MAX_SHIFT_PX, v));
			}
			return r;
		}
		return def;
	}

	/** Size factor of the sword and scabbard of the item with registry path {@code itemId} ({@code swordScale.<id>}, else {@code swordScale}). */
	public static double swordScale(String itemId) {
		double general = clampScale(num("swordScale", DEFAULT_SWORD_SCALE));
		return itemId == null ? general : clampScale(num("swordScale." + itemId, general));
	}

	private static double clampScale(double v) {
		return Math.max(MIN_SWORD_SCALE, Math.min(MAX_SWORD_SCALE, v));
	}
}
