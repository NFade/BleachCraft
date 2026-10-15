package dev.minebleach.reiatsutest.client;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.config.ConfigFileIo;
import dev.minebleach.reiatsutest.core.config.ConfigMigrations;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * R11 review fix 8: the one place that knows the client config file {@code config/reiatsu_test_client.json}. Its loaders ({@link ClientOptions},
 * {@code FxConfig}, {@code TpConfig}) each read, add their own section and write the file back; they all hold {@link #lock()} for the whole cycle and
 * write through {@link ConfigFileIo#writeJson} (temporary file and atomic move). {@link #migrate()} runs the client migration of {@link ConfigMigrations}
 * (it was defined but never called): a file without a stamp gets {@code configVersion}, an older one is migrated after a one time backup
 * {@code reiatsu_test_client.json.bak_v<N>}; every key of the file stays.
 */
public final class ClientConfigFile {
	private ClientConfigFile() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("reiatsu_test_client.json");
	}

	/** The monitor every read-modify-write of the file holds. */
	public static Object lock() {
		return ConfigFileIo.lockFor(file());
	}

	/** A file that a loader creates from nothing is stamped with the current version, so the next start does not treat it as an old one. */
	public static void stampFresh(JsonObject root) {
		if (!root.has(ConfigMigrations.VERSION_KEY)) {
			root.addProperty(ConfigMigrations.VERSION_KEY, ConfigMigrations.CLIENT_VERSION);
		}
	}

	/** Client start, before the loaders. A missing file is left to them (they create it, stamped); an unreadable one is left alone. */
	public static void migrate() {
		Path file = file();
		synchronized (lock()) {
			if (!java.nio.file.Files.isRegularFile(file)) {
				return;
			}
			try {
				ConfigMigrations.Result r = ConfigFileIo.migrateFile(file, ConfigMigrations.CLIENT_VERSION, ConfigMigrations::migrateClient);
				if (r != null) {
					ReiatsuTest.LOGGER.info("[config] client file stamped/updated v{} -> v{} ({} keys changed, backup {}.bak_v{}): {}", r.fromVersion(), r.toVersion(),
							r.changed().size(), file.getFileName(), r.fromVersion(), r.changed());
				}
			} catch (IOException | RuntimeException e) {
				ReiatsuTest.LOGGER.warn("[config] could not migrate {} ({}), leaving it alone", file, e.toString());
			}
		}
	}
}
