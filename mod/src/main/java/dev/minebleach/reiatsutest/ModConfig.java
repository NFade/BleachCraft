package dev.minebleach.reiatsutest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.swarm.FlightConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.world.FrostConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/reiatsu_test.json} (ADR section 2 "Config"): one JSON object, one section per feature. The
 * {@code "voice"}, {@code "kido"}, {@code "bladeFlight"} and {@code "frost"} sections exist so far. A missing file or section is written with defaults; a file that does not parse
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

	/**
	 * The {@code "bladeFlight"} section (steerable Senbonzakura attack swarm). Same rules as {@link #loadKido}.
	 */
	public static FlightConfig loadFlight() {
		Path file = file();
		JsonObject root = new JsonObject();
		boolean write = true;
		if (Files.isRegularFile(file)) {
			try {
				var el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (el.isJsonObject()) {
					root = el.getAsJsonObject();
					write = !root.has(FlightConfig.SECTION);
				} else {
					write = false;
				}
			} catch (IOException | JsonParseException e) {
				ReiatsuTest.LOGGER.warn("[config] {} is not readable ({}), using bladeFlight defaults and leaving it alone", file, e.toString());
				return FlightConfig.defaults();
			}
		}
		FlightConfig cfg = FlightConfig.fromRoot(root);
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

	/**
	 * The {@code "kido"} section (B5). Same rules as {@link #loadVoice}: a missing section is written with the defaults next
	 * to whatever else the file holds, an unreadable file is left alone and the defaults are used for the session.
	 */
	public static KidoConfig loadKido() {
		Path file = file();
		JsonObject root = new JsonObject();
		boolean write = true;
		if (Files.isRegularFile(file)) {
			try {
				var el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (el.isJsonObject()) {
					root = el.getAsJsonObject();
					write = !root.has(KidoConfig.SECTION);
				} else {
					write = false;
				}
			} catch (IOException | JsonParseException e) {
				ReiatsuTest.LOGGER.warn("[config] {} is not readable ({}), using kido defaults and leaving it alone", file, e.toString());
				return KidoConfig.defaults();
			}
		}
		KidoConfig cfg = KidoConfig.fromRoot(root);
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

	/**
	 * The {@code "frost"} section (territory freeze of the Rukia bankai, round 6). Same rules as {@link #loadKido}: a missing section is
	 * written with the defaults next to whatever else the file holds, an unreadable file is left alone and the defaults are used.
	 */
	public static FrostConfig loadFrost() {
		Path file = file();
		JsonObject root = new JsonObject();
		boolean write = true;
		if (Files.isRegularFile(file)) {
			try {
				var el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (el.isJsonObject()) {
					root = el.getAsJsonObject();
					write = !root.has(FrostConfig.SECTION);
				} else {
					write = false;
				}
			} catch (IOException | JsonParseException e) {
				ReiatsuTest.LOGGER.warn("[config] {} is not readable ({}), using frost defaults and leaving it alone", file, e.toString());
				return FrostConfig.defaults();
			}
		}
		FrostConfig cfg = FrostConfig.fromRoot(root);
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
