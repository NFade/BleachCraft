package dev.minebleach.reiatsutest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/reiatsu_test.json} (ADR section 2 "Config"): one JSON object, one section per feature. Only the
 * {@code "voice"} section exists so far. A missing file or section is written with defaults; a file that does not parse
 * is left untouched (defaults are used for the session) so a typo never destroys the user's settings.
 */
public final class ModConfig {
	private ModConfig() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ReiatsuTest.MOD_ID + ".json");
	}

	public static VoiceConfig loadVoice() {
		Path file = file();
		JsonObject root = new JsonObject();
		boolean write = true;
		if (Files.isRegularFile(file)) {
			try {
				var el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (el.isJsonObject()) {
					root = el.getAsJsonObject();
					write = !root.has("voice");
				} else {
					write = false;
				}
			} catch (IOException | JsonParseException e) {
				ReiatsuTest.LOGGER.warn("[config] {} is not readable ({}), using defaults and leaving it alone", file, e.toString());
				return VoiceConfig.defaults();
			}
		}
		VoiceConfig cfg = VoiceConfig.fromRoot(root);
		if (write) {
			cfg.writeInto(root);
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator(),
						StandardCharsets.UTF_8);
			} catch (IOException e) {
				ReiatsuTest.LOGGER.warn("[config] could not write {}: {}", file, e.toString());
			}
		}
		return cfg;
	}
}
