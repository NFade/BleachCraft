package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * R11 review fixes: guards that read the sources of the glue (Minecraft classes cannot be loaded in unit tests). The pure parts have their own tests
 * ({@code RageTuningTest}, {@code RageRewardTest}, {@code ConfigFileIoTest}, {@code TpKeyLimitsTest}, {@code IceWallLayoutTest}).
 */
class R11ReviewFixesSourceTest {
	private static final Path ROOT = Paths.get("src");
	private static final String PKG = "dev/minebleach/reiatsutest/";

	private static String main(String rel) throws IOException {
		return Files.readString(ROOT.resolve("main/java/" + PKG + rel), StandardCharsets.UTF_8);
	}

	private static String client(String rel) throws IOException {
		return Files.readString(ROOT.resolve("client/java/" + PKG + "client/" + rel), StandardCharsets.UTF_8);
	}

	@Test
	void fix3ReloadOfKidoReachesTheLiveStateMachines() throws IOException {
		String kido = main("server/KidoManager.java");
		assertTrue(kido.contains("ZanpakutoManager.refreshKido(c)"), "the reload callback hands the new config to the sessions");
		String zm = main("server/ZanpakutoManager.java");
		assertTrue(zm.contains("static void refreshKido(") && zm.contains("s.sm.setKidoConfig(cfg)"));
		assertTrue(zm.contains("s.sm.setKidoConfig(KidoManager.config())"), "new sessions still start with the current config");
	}

	@Test
	void fix4TheHudReadsTheSyncedTuningNotTheDefaults() throws IOException {
		String hud = client("hud/HudModel.java");
		assertFalse(hud.contains("RageConfig.defaults()"), "no copy of the code defaults left in the HUD");
		assertTrue(hud.contains("ClientState.rageTuning()"));
		assertTrue(client("ClientState.java").contains("ModAttachments.RAGE_TUNING"));
		String att = main("registry/ModAttachments.java");
		assertTrue(att.contains("RAGE_TUNING") && att.contains("AttachmentSyncPredicate.targetOnly()"));
		String rage = main("server/RageManager.java");
		assertTrue(rage.contains("ModAttachments.RAGE_TUNING") && rage.contains("RageTuning.of(config)"), "the server writes it when it differs, every tick check");
	}

	@Test
	void fix6TheIceWallHasAnOrphanSweepLikeTheFrostField() throws IOException {
		String temp = main("server/TempBlocks.java");
		assertTrue(temp.contains("ServerChunkEvents.CHUNK_LOAD.register(TempBlocks::sweepOrphanWalls)"));
		assertTrue(temp.contains("state.journal.contains(dim, x, y, z)"), "a wall block with a journal entry is left alone");
		assertTrue(temp.contains("sec.hasAny(st -> st.isOf(ModBlocks.ICE_WALL))"), "only sections that hold the block are scanned");
		assertTrue(temp.contains("restoring++") && temp.contains("restoring > 0"), "a restore that loads a chunk is not swept");
		assertTrue(temp.contains("sweepLoadedSpawnChunks(server)"), "the chunks loaded before the journal are swept at the start");
		assertTrue(main("server/ZanpakutoManager.java").contains("TempBlocks.init()"));
	}

	@Test
	void fix8TheConfigWritersAreAtomicAndTheClientFileIsMigrated() throws IOException {
		String cfg = main("ModConfig.java");
		assertFalse(cfg.contains("Files.writeString"), "no truncating write of the server file is left");
		assertTrue(cfg.contains("ConfigFileIo.writeJson(file, root)"));
		for (String loader : new String[] {"synchronized VoiceConfig loadVoice", "synchronized FlightConfig loadFlight", "synchronized KidoConfig loadKido",
				"synchronized FrostConfig loadFrost", "synchronized <T> T loadSection"}) {
			assertTrue(cfg.contains(loader), loader + " holds the file monitor");
		}
		assertTrue(cfg.contains("ConfigFileIo.migrateFile("));
		// the client file: migrate once at start, every loader locks and writes atomically
		String opts = client("ClientOptions.java");
		assertTrue(opts.contains("ClientConfigFile.migrate()") && opts.contains("ClientConfigFile.lock()") && opts.contains("ConfigFileIo.writeJson"));
		assertFalse(opts.contains("Files.writeString"));
		String fx = client("fx/FxConfig.java");
		assertTrue(fx.contains("ClientConfigFile.lock()") && fx.contains("ConfigFileIo.writeJson") && !fx.contains("Files.writeString"));
		String tp = client("model/TpConfig.java");
		assertTrue(tp.contains("ClientConfigFile.lock()") && tp.contains("ConfigFileIo.writeJson") && !tp.contains("Files.writeString"));
		assertTrue(client("ClientConfigFile.java").contains("ConfigFileIo.migrateFile(file, ConfigMigrations.CLIENT_VERSION, ConfigMigrations::migrateClient)"));
	}

	@Test
	void fix9ThePoseConfigClampsEveryKeyAndCreatesItsSectionOnAFirstRun() throws IOException {
		String tp = client("model/TpConfig.java");
		assertTrue(tp.contains("TpKeyLimits.num(key") && tp.contains("TpKeyLimits.vec(key"), "every key goes through the ranges");
		assertTrue(tp.contains("root = new JsonObject();") && tp.contains("ClientConfigFile.stampFresh(root)"),
				"a file that does not exist yet is created with the tp section, whichever loader runs first");
		assertTrue(tp.contains("!root.has(\"tp\")"), "an existing tp section and every other key are left as they are");
	}
}
