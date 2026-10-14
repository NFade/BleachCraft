package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** R11 T9 guards that read the sources: where block destruction starts, which abilities never start it, and the safety rules of the glue. */
class TerrainSourceTest {
	private static final Path MAIN = Paths.get("src", "main", "java", "dev", "minebleach", "reiatsutest");

	private static String read(String rel) throws IOException {
		return Files.readString(MAIN.resolve(rel), StandardCharsets.UTF_8);
	}

	/** The text of one method: from its signature to the next method of the file (good enough for these small private methods). */
	private static String method(String src, String signature) {
		int i = src.indexOf(signature);
		assertTrue(i >= 0, signature);
		int next = src.indexOf("\n\tprivate static ", i + signature.length());
		return src.substring(i, next < 0 ? src.length() : next);
	}

	@Test
	void destructionStartsOnlyFromTheListedAbilities() throws IOException {
		String exec = read("server/AbilityExecutor.java");
		assertTrue(method(exec, "private static void modeAttack(").contains("AbilityTerrain.modeAttack("));
		assertTrue(method(exec, "private static void scatter(").contains("AbilityTerrain.scatter("));
		String hak = method(exec, "private static void hakuteiken(");
		assertTrue(hak.contains("AbilityTerrain.hakuteikenLine(") && hak.contains("AbilityTerrain.hakuteikenBurst("));
		assertTrue(read("server/KidoManager.java").contains("AbilityTerrain.shakkaho("));
		// controls and the Rukia abilities never break blocks
		for (String none : new String[] {"private static void cage(", "private static void tsukishiro(", "private static void hakuren(", "private static void shirafune(",
				"private static void iceWall("}) {
			assertFalse(method(exec, none).contains("AbilityTerrain"), none);
		}
		assertFalse(read("server/BankaiField.java").contains("AbilityTerrain"));
		// the call count: five call points, nothing else in the server package starts a destruction job
		int calls = 0;
		try (var files = Files.walk(MAIN.resolve("server"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				if (p.getFileName().toString().equals("AbilityTerrain.java")) {
					continue;
				}
				String s = Files.readString(p, StandardCharsets.UTF_8);
				for (String m : new String[] {"AbilityTerrain.modeAttack(", "AbilityTerrain.scatter(", "AbilityTerrain.hakuteikenLine(", "AbilityTerrain.hakuteikenBurst(",
						"AbilityTerrain.shakkaho("}) {
					int i = s.indexOf(m);
					while (i >= 0) {
						calls++;
						i = s.indexOf(m, i + 1);
					}
				}
			}
		}
		assertTrue(calls == 5, "call points: " + calls);
	}

	@Test
	void theGlueKeepsItsSafetyRules() throws IOException {
		String s = read("server/AbilityTerrain.java");
		assertTrue(s.contains("DO_MOB_GRIEFING") && s.contains("TerrainRules.worldAllows("), "mobGriefing and terrain.ignoreMobGriefing");
		assertTrue(s.contains("getGameMode()") && s.contains("TerrainRules.gameModeMayBreak("), "the game mode of the owner");
		assertTrue(s.contains("canPlayerModifyAt("), "spawn protection and the like");
		assertTrue(s.contains("NOTIFY_LISTENERS | Block.FORCE_STATE"), "no neighbour update cascade");
		assertTrue(s.contains("ModBlocks.isModTerrain("), "frost blocks and the ice wall");
		assertTrue(s.contains("hasBlockEntity()"), "block entities");
		for (String never : new String[] {"BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "JIGSAW", "NETHER_PORTAL",
				"END_PORTAL", "END_PORTAL_FRAME", "END_GATEWAY"}) {
			assertTrue(s.contains("Blocks." + never), never);
		}
		assertTrue(s.contains("FluidBlock"), "fluids are left alone");
		assertTrue(s.contains("cfg.drops()"), "drops are off unless terrain.drops");
		assertTrue(s.contains("[terrain] {} broke {} blocks (cap {}, budget ticks {})"), "the agreed log line");
		assertTrue(s.contains("cfg.blocksPerTick()"), "batches per tick");
		assertTrue(s.contains("effectAt("), "one sound and particle group per several blocks");
	}

	@Test
	void theCapIsCountedAcrossThePhasesOfACast() throws IOException {
		assertTrue(read("server/CastContext.java").contains("int terrainReserved;"));
		assertTrue(read("server/AbilityTerrain.java").contains("TerrainRules.remaining(site.cap(), ctx == null ? 0 : ctx.terrainReserved)"));
	}
}
