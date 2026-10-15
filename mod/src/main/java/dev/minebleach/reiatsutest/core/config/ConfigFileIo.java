package dev.minebleach.reiatsutest.core.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * R11 review fix 8: file access of the two config files, pure Java (no Minecraft types).
 * <ul>
 * <li>{@link #writeAtomic}: the new text goes to {@code <file>.tmp} first and replaces the file with one move ({@code ATOMIC_MOVE}, with a plain
 * replacing move where the file system cannot do it), so a crash or kill during the write leaves the old complete file, never an empty one (the
 * old {@code Files.writeString} truncated first and a killed process lost every number of the user);</li>
 * <li>{@link #lockFor}: one monitor per file; every read-modify-write of a config file holds it, so two loaders (or the server thread and the render
 * thread) cannot interleave and lose each other's section;</li>
 * <li>{@link #migrateFile}: the versioned migration of {@link ConfigMigrations} applied to a file under the lock: stamps a missing file, backs up an
 * older one once as {@code <file>.bak_v<N>}, rewrites it atomically, leaves an unreadable one alone.</li>
 * </ul>
 */
public final class ConfigFileIo {
	private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

	private ConfigFileIo() {
	}

	/** The monitor of a config file (the same object for the same path, however it is spelled). */
	public static Object lockFor(Path file) {
		return LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), k -> new Object());
	}

	/** Pretty printed JSON with a final line break (the format of both config files). */
	public static String pretty(JsonObject root) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator();
	}

	/** Writes {@code text} to {@code file} through a temporary file in the same folder; the old file stays complete until the move. */
	public static void writeAtomic(Path file, String text) throws IOException {
		Path abs = file.toAbsolutePath();
		Path dir = abs.getParent();
		if (dir != null) {
			Files.createDirectories(dir);
		}
		Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
		try {
			Files.writeString(tmp, text, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp); // only left behind when something above failed
		}
	}

	/** {@link #writeAtomic} of a JSON object. */
	public static void writeJson(Path file, JsonObject root) throws IOException {
		writeAtomic(file, pretty(root));
	}

	/**
	 * Applies a versioned migration to {@code file} under its lock.
	 *
	 * @param freshVersion   the version stamped into a file that does not exist yet (the file is created holding only the stamp)
	 * @param migrator       e.g. {@code ConfigMigrations::migrateClient}
	 * @return the migration result when the file changed (stamp or keys), or null when nothing was done (fresh file written, already current,
	 *         not a JSON object)
	 * @throws IOException                         the file could not be read or written
	 * @throws com.google.gson.JsonParseException  the file is not JSON; it is left untouched
	 */
	public static ConfigMigrations.Result migrateFile(Path file, int freshVersion, Function<JsonObject, ConfigMigrations.Result> migrator) throws IOException {
		synchronized (lockFor(file)) {
			if (!Files.isRegularFile(file)) {
				JsonObject fresh = new JsonObject();
				fresh.addProperty(ConfigMigrations.VERSION_KEY, freshVersion);
				writeJson(file, fresh);
				return null;
			}
			JsonElement el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!el.isJsonObject()) {
				return null;
			}
			JsonObject root = el.getAsJsonObject();
			ConfigMigrations.Result r = migrator.apply(root);
			if (!r.touched()) {
				return null;
			}
			Path bak = file.resolveSibling(file.getFileName() + ".bak_v" + r.fromVersion());
			if (!Files.exists(bak)) {
				Files.copy(file, bak);
			}
			writeJson(file, root);
			return r;
		}
	}

	/** The rules list helper for tests and callers that migrate with an explicit table. */
	public static Function<JsonObject, ConfigMigrations.Result> with(List<ConfigMigrations.Rule> rules, int current) {
		return root -> ConfigMigrations.migrate(root, rules, current);
	}
}
