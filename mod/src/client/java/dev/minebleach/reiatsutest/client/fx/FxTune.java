package dev.minebleach.reiatsutest.client.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.ReiatsuTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Dev hot reload: numeric tuning values of the effects (and the FxConfig keys) are read from {@code run/fx_override.json}
 * and re-read whenever the file changes (checked every 30 frames), so colours, sizes and timings can be tuned without
 * restarting the game. Effects ask {@code FxTune.d("release.burstSpeed", 6.0)} (the default is the shipped value); a missing
 * file or key means the default. Keys of {@link FxConfig} (e.g. {@code glowIntensity}, {@code reduceMotion}) are applied too.
 * Strings starting with '#' are colours: {@code FxTune.s("release.rukiaFlash", "#DDF3FF")}.
 */
public final class FxTune {
	private static final Map<String, JsonElement> VALUES = new HashMap<>();
	private static long lastModified = -1;
	private static int counter;

	private FxTune() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("fx_override.json");
	}

	/** Called once per frame. */
	public static void poll() {
		if (++counter % 30 != 0) {
			return;
		}
		Path f = file();
		try {
			long m = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
			if (m == lastModified) {
				return;
			}
			lastModified = m;
			VALUES.clear();
			if (m == 0) {
				return;
			}
			JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (el.isJsonObject()) {
				JsonObject o = el.getAsJsonObject();
				for (Map.Entry<String, JsonElement> e : o.entrySet()) {
					VALUES.put(e.getKey(), e.getValue());
				}
				FxConfig.applyDev(o);
				ReiatsuTest.LOGGER.info("[fxtune] reloaded {} values from {}", VALUES.size(), f);
			}
		} catch (IOException | RuntimeException e) {
			ReiatsuTest.LOGGER.warn("[fxtune] cannot read {}: {}", f, e.toString());
		}
	}

	public static double d(String key, double def) {
		JsonElement e = VALUES.get(key);
		try {
			return e == null ? def : e.getAsDouble();
		} catch (RuntimeException ex) {
			return def;
		}
	}

	public static String s(String key, String def) {
		JsonElement e = VALUES.get(key);
		try {
			return e == null ? def : e.getAsString();
		} catch (RuntimeException ex) {
			return def;
		}
	}
}
