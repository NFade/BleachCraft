package dev.minebleach.reiatsutest.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.client.ClientConfigFile;
import dev.minebleach.reiatsutest.core.combat.TpKeyLimits;
import dev.minebleach.reiatsutest.core.config.ConfigFileIo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

	private static volatile JsonObject section = new JsonObject();
	private static long lastCheckNanos;
	private static long lastStamp = Long.MIN_VALUE;

	private TpConfig() {
	}

	private static Path file() {
		return ClientConfigFile.file();
	}

	/**
	 * Client start: adds the {@code tp} section with {@code swordScale} to a file that has none, then reads it. R11 review fix 9: the order of the client
	 * loaders does not matter any more: a file that does not exist yet is created here with the section (stamped with the config version), so the
	 * section is there from the first start; a file that exists keeps every key it has (nothing is replaced, the section is only added when missing); a file
	 * that does not parse is left alone. The read-add-write cycle holds the lock of the client file and the write is atomic (R11 review fix 8).
	 */
	public static void load() {
		Path f = file();
		synchronized (ClientConfigFile.lock()) {
			try {
				JsonObject root = null;
				if (Files.isRegularFile(f)) {
					JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
					root = el.isJsonObject() ? el.getAsJsonObject() : null;
				} else {
					root = new JsonObject();
					ClientConfigFile.stampFresh(root);
				}
				if (root != null && !root.has("tp")) {
					JsonObject tp = new JsonObject();
					tp.addProperty("swordScale", DEFAULT_SWORD_SCALE);
					root.add("tp", tp);
					ConfigFileIo.writeJson(f, root);
				}
			} catch (IOException | RuntimeException e) {
				ReiatsuTest.LOGGER.warn("[tp] cannot add the tp section to {}: {}", f, e.toString());
			}
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
			JsonElement el;
			synchronized (ClientConfigFile.lock()) {
				el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			}
			JsonObject tp = el.isJsonObject() && el.getAsJsonObject().get("tp") instanceof JsonObject o ? o : new JsonObject();
			section = tp;
			ReiatsuTest.LOGGER.info("[tp] config {}", tp);
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[tp] cannot read {}: {} (keeping the last values)", f, e.toString());
		}
	}

	/**
	 * Number {@code key} of the section, clamped into the range of the key ({@link TpKeyLimits}); {@code def} when missing, not a finite number or
	 * not a key the pose reads (R11 review fix 9).
	 */
	public static double num(String key, double def) {
		JsonElement e = section.get(key);
		if (e instanceof JsonPrimitive p && p.isNumber()) {
			return TpKeyLimits.num(key, p.getAsDouble(), def);
		}
		return def;
	}

	/**
	 * The vector {@code key} of the section with every component clamped ({@link TpKeyLimits}: {@code guard.shift} +-8 px, {@code hold.pos} +-16 px,
	 * {@code guard.arm_*} six numbers); {@code def} when missing, too short, not numbers or not a key the pose reads.
	 */
	public static double[] vec(String key, double[] def) {
		JsonElement e = section.get(key);
		int n = TpKeyLimits.vecSize(key);
		if (n > 0 && e instanceof JsonArray a && a.size() >= n) {
			double[] r = new double[n];
			for (int i = 0; i < n; i++) {
				if (!(a.get(i) instanceof JsonPrimitive p) || !p.isNumber()) {
					return def;
				}
				r[i] = p.getAsDouble();
			}
			return TpKeyLimits.vec(key, r, def);
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
