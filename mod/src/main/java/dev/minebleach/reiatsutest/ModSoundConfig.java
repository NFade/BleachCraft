package dev.minebleach.reiatsutest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.sound.SoundMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * R11 T12: file access of {@code config/reiatsu_sounds.json} (the format and the tolerant reads are {@link SoundMap}). A missing file is written with the
 * defaults at the first start (server or client, whichever comes first); a file that does not parse is left untouched and the defaults are used, so a typo
 * never destroys the user's settings. The server part is re-read by {@code /reiatsu reload}, the client part is polled by {@code FxSound} (once a second).
 */
public final class ModSoundConfig {
	public static final String FILE_NAME = "reiatsu_sounds.json";

	private ModSoundConfig() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Modification stamp of the file (changes when it is edited), {@code Long.MIN_VALUE} when it does not exist. */
	public static long stamp() {
		Path f = file();
		try {
			return Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() ^ Files.size(f) : Long.MIN_VALUE;
		} catch (IOException e) {
			return Long.MIN_VALUE;
		}
	}

	/** Reads the file; writes it with the defaults when it is missing. Never throws: the defaults on any problem. */
	public static SoundMap load() {
		Path f = file();
		try {
			if (!Files.isRegularFile(f)) {
				SoundMap fresh = SoundMap.defaults();
				Files.createDirectories(f.getParent());
				Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(fresh.toJson()) + System.lineSeparator(), StandardCharsets.UTF_8);
				ReiatsuTest.LOGGER.info("[sound] wrote the default {}", f);
				return fresh;
			}
			JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				ReiatsuTest.LOGGER.warn("[sound] {} is not a JSON object, using the default sounds and leaving it alone", f);
				return SoundMap.defaults();
			}
			return SoundMap.fromJson(el.getAsJsonObject());
		} catch (IOException | JsonParseException | IllegalStateException e) {
			ReiatsuTest.LOGGER.warn("[sound] {} is not readable ({}), using the default sounds and leaving it alone", f, e.toString());
			return SoundMap.defaults();
		}
	}
}
