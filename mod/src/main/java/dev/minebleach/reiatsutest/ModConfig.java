package dev.minebleach.reiatsutest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.minebleach.reiatsutest.core.combat.FrostMomentum;
import dev.minebleach.reiatsutest.core.config.ConfigFileIo;
import dev.minebleach.reiatsutest.core.config.ConfigMigrations;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.movement.DoubleJumpConfig;
import dev.minebleach.reiatsutest.core.rage.RageConfig;
import dev.minebleach.reiatsutest.core.state.MechSpec;
import dev.minebleach.reiatsutest.core.swarm.FlightConfig;
import dev.minebleach.reiatsutest.core.voice.VoiceConfig;
import dev.minebleach.reiatsutest.core.world.FrostConfig;
import dev.minebleach.reiatsutest.core.world.IceWallConfig;
import dev.minebleach.reiatsutest.core.world.TerrainRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/reiatsu_test.json} (ADR section 2 "Config"): one JSON object, one section per feature. The
 * {@code "voice"}, {@code "kido"}, {@code "bladeFlight"} and {@code "frost"} sections exist so far. A missing file or section is written with defaults; a file that does not parse
 * is left untouched (defaults are used for the session) so a typo never destroys the user's settings.
 *
 * <p>R11 review fix 8: every writer goes through {@link ConfigFileIo#writeJson} (temporary file, then an atomic move: a crash during the write cannot leave an
 * empty file), and every loader and {@link #migrate} is {@code synchronized} on this class, so the read-modify-write cycles of the sections never interleave
 * (the server thread at {@code SERVER_STARTED}, the command thread of {@code /reiatsu reload}).
 */
public final class ModConfig {
	private ModConfig() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ReiatsuTest.MOD_ID + ".json");
	}

	/**
	 * R11 T0: stamps {@code configVersion} on a fresh file and, for an older one, replaces the values that still equal an old default (see
	 * {@link ConfigMigrations}), after copying the file to {@code reiatsu_test.json.bak_v<N>}. An unreadable file is left alone. Run before
	 * the section loaders (mod init and {@code /reiatsu reload}).
	 *
	 * @return the number of keys rewritten
	 */
	public static synchronized int migrate() {
		Path file = file();
		try {
			ConfigMigrations.Result r = ConfigFileIo.migrateFile(file, ConfigMigrations.SERVER_VERSION, ConfigMigrations::migrateServer);
			if (r == null) {
				return 0;
			}
			Path bak = file.resolveSibling(file.getFileName() + ".bak_v" + r.fromVersion());
			ReiatsuTest.LOGGER.info("[config] updated {} keys from v{} to v{} (backup {}): {}", r.changed().size(), r.fromVersion(), r.toVersion(),
					bak.getFileName(), r.changed());
			return r.changed().size();
		} catch (IOException | JsonParseException e) {
			ReiatsuTest.LOGGER.warn("[config] could not migrate {} ({}), leaving it alone", file, e.toString());
			return 0;
		}
	}

	/**
	 * R11: the generic section loader of the new sections ({@code mech}, {@code rage}, {@code terrain} ...). Same rules as {@link #loadKido}: a
	 * missing section is written with the defaults next to whatever else the file holds, an unreadable file is left alone and the defaults are used.
	 */
	static synchronized <T> T loadSection(String section, Supplier<T> defaults, Function<JsonObject, T> fromRoot, BiConsumer<T, JsonObject> writeInto) {
		return loadSection(section, defaults, fromRoot, writeInto, root -> root.has(section));
	}

	/** Same with an own test whether the file already holds the section (for a nested section that shares its parent object with other tasks). */
	static synchronized <T> T loadSection(String section, Supplier<T> defaults, Function<JsonObject, T> fromRoot, BiConsumer<T, JsonObject> writeInto,
			Predicate<JsonObject> present) {
		Path file = file();
		JsonObject root = new JsonObject();
		boolean write = true;
		if (Files.isRegularFile(file)) {
			try {
				var el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (el.isJsonObject()) {
					root = el.getAsJsonObject();
					write = !present.test(root);
				} else {
					write = false;
				}
			} catch (IOException | JsonParseException e) {
				ReiatsuTest.LOGGER.warn("[config] {} is not readable ({}), using {} defaults and leaving it alone", file, e.toString(), section);
				return defaults.get();
			}
		}
		T cfg = fromRoot.apply(root);
		if (write) {
			writeInto.accept(cfg, root);
			try {
				Files.createDirectories(file.getParent());
				ConfigFileIo.writeJson(file, root);
			} catch (IOException e) {
				ReiatsuTest.LOGGER.warn("[config] could not write {}: {}", file, e.toString());
			}
		}
		return cfg;
	}

	/** The {@code "mech"} section (R11 T2: melee and parry reiatsu, block, barrier push). */
	public static MechSpec loadMech() {
		return loadSection(MechSpec.SECTION, MechSpec::defaults, MechSpec::fromRoot, (m, root) -> root.add(MechSpec.SECTION, m.toJson()));
	}

	/** The {@code "rage"} section (R11 T3: rage gain, parry, ability reward, mob share, target cap, minimum shikai time before bankai). */
	public static RageConfig loadRage() {
		return loadSection(RageConfig.SECTION, RageConfig::defaults, RageConfig::fromRoot, (r, root) -> root.add(RageConfig.SECTION, r.toJson()));
	}

	/** The {@code "rukia.iceWall"} section (R11 T5): geometry, life and strength of the ice wall. Written into the {@code rukia} object next to {@code rukia.passive}. */
	public static IceWallConfig loadIceWall() {
		return loadSection(IceWallConfig.SECTION, IceWallConfig::defaults, IceWallConfig::fromRoot, (c, root) -> {
			JsonObject rukia = root.has(IceWallConfig.SECTION) && root.get(IceWallConfig.SECTION).isJsonObject()
					? root.getAsJsonObject(IceWallConfig.SECTION) : new JsonObject();
			rukia.add(IceWallConfig.SUB, c.toJson());
			root.add(IceWallConfig.SECTION, rukia);
		}, root -> root.has(IceWallConfig.SECTION) && root.get(IceWallConfig.SECTION).isJsonObject()
				&& root.getAsJsonObject(IceWallConfig.SECTION).has(IceWallConfig.SUB));
	}

	/** The {@code "doubleJump"} section (shikai / bankai air jump, read by the client): {@code enabled}, {@code strength}. */
	public static DoubleJumpConfig loadDoubleJump() {
		return loadSection(DoubleJumpConfig.SECTION, DoubleJumpConfig::defaults, DoubleJumpConfig::fromRoot,
				(c, root) -> root.add(DoubleJumpConfig.SECTION, c.toJson()));
	}

	/** The {@code "terrain"} section (R11 T9: abilities break blocks, profiles S / M / L, batches per tick, mob griefing). */
	public static TerrainRules.Config loadTerrain() {
		return loadSection(TerrainRules.Config.SECTION, TerrainRules.Config::defaults, TerrainRules.Config::fromRoot,
				(c, root) -> root.add(TerrainRules.Config.SECTION, c.toJson()));
	}

	/** The {@code "rukia.passive"} section (R11 T6): the frost momentum of Rukia. Written into an existing {@code rukia} object without touching its other keys. */
	public static FrostMomentum.Config loadRukiaPassive() {
		return loadSection(FrostMomentum.Config.SECTION, FrostMomentum.Config::defaults, FrostMomentum.Config::fromRoot, (c, root) -> {
			JsonObject rukia = root.has(FrostMomentum.Config.SECTION) && root.get(FrostMomentum.Config.SECTION).isJsonObject()
					? root.getAsJsonObject(FrostMomentum.Config.SECTION) : new JsonObject();
			rukia.add(FrostMomentum.Config.SUB, c.toJson());
			root.add(FrostMomentum.Config.SECTION, rukia);
		}, root -> root.has(FrostMomentum.Config.SECTION) && root.get(FrostMomentum.Config.SECTION).isJsonObject()
				&& root.getAsJsonObject(FrostMomentum.Config.SECTION).has(FrostMomentum.Config.SUB));
	}

	public static synchronized VoiceConfig loadVoice() {
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
				ConfigFileIo.writeJson(file, root);
			} catch (IOException e) {
				ReiatsuTest.LOGGER.warn("[config] could not write {}: {}", file, e.toString());
			}
		}
		return cfg;
	}

	/**
	 * The {@code "bladeFlight"} section (steerable Senbonzakura attack swarm). Same rules as {@link #loadKido}.
	 */
	public static synchronized FlightConfig loadFlight() {
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
				ConfigFileIo.writeJson(file, root);
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
	public static synchronized KidoConfig loadKido() {
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
				ConfigFileIo.writeJson(file, root);
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
	public static synchronized FrostConfig loadFrost() {
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
				ConfigFileIo.writeJson(file, root);
			} catch (IOException e) {
				ReiatsuTest.LOGGER.warn("[config] could not write {}: {}", file, e.toString());
			}
		}
		return cfg;
	}
}
