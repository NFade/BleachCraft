package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * R11 T5 guards that read the sources (Minecraft classes cannot be loaded in unit tests): the old ice spike is gone from the code, every ray and area
 * implementation knows the ice wall, and the block has all its resources.
 */
class IceWallSourceTest {
	private static final Path SRC = Paths.get("src");
	private static final Path MAIN = SRC.resolve(Paths.get("main", "java", "dev", "minebleach", "reiatsutest"));

	private static String read(String rel) throws IOException {
		Path p = MAIN.resolve(rel);
		assertTrue(Files.isRegularFile(p), p.toAbsolutePath().toString());
		return Files.readString(p, StandardCharsets.UTF_8);
	}

	@Test
	void theIceSpikeIsGoneFromCodeResourcesAndTests() throws IOException {
		List<String> hits = new ArrayList<>();
		String[] banned = {"ICE_SPIKE", "ice_spike", "iceSpike", "IceSpike"};
		try (Stream<Path> files = Files.walk(SRC)) {
			for (Path p : files.filter(Files::isRegularFile).toList()) {
				String n = p.getFileName().toString();
				if (n.equals("IceWallSourceTest.java") || !(n.endsWith(".java") || n.endsWith(".json") || n.endsWith(".mcmeta"))) {
					continue;
				}
				String s = Files.readString(p, StandardCharsets.UTF_8);
				for (String b : banned) {
					if (s.contains(b)) {
						hits.add(p + " has " + b);
					}
				}
			}
		}
		assertEquals(List.of(), hits, "ICE_SPIKE / ice_spike only survive in history documents (LOG.md, design/results)");
	}

	@Test
	void theByteCodeOfTheOldSlotIsKept() throws IOException {
		assertTrue(read("core/state/AbilityId.java").contains("ICE_WALL(10, \"rukia.bankai.ice_wall\""));
	}

	@Test
	void everyRayAndAreaImplementationKnowsTheIceWall() throws IOException {
		// rays and block aimed points: Targeting.rayPoint is a COLLIDER raycast, the wall is a full collider block
		String targeting = read("server/Targeting.java");
		assertTrue(targeting.contains("RaycastContext.ShapeType.COLLIDER"), "the block ray sees every collider, the wall included");
		// area targets: spheres, cylinders and segments ask whether a wall stands between the centre and the target
		assertEquals(3, count(targeting, "IceWall.blocked("), "sphere, cylinder and segment");
		String exec = read("server/AbilityExecutor.java");
		for (String by : new String[] {"\"hakuren\"", "\"shirafune\"", "\"mode_attack\"", "\"scatter\"", "\"hakuteiken line\"", "\"hakuteiken burst\""}) {
			assertTrue(exec.contains("IceWall.absorbNear(") && exec.contains(by), "the wall spends the strength of " + by);
		}
		assertTrue(exec.contains("IceWall.clipDistance("), "the Hakuren wave stops at the wall");
		assertTrue(read("server/KidoManager.java").contains("\"byakurai\"") && read("server/KidoManager.java").contains("\"shakkaho\""));
		assertTrue(read("server/BladeFlights.java").contains("IceWall.absorbNear("), "the flight head stops at the wall (COLLIDER raycast) and spends its strength");
		assertTrue(read("server/BladeFlights.java").contains("RaycastContext.ShapeType.COLLIDER"));
		assertTrue(read("server/BladeFlights.java").contains("IceWall.blocked("), "the sweep of the head does not hit targets behind the wall");
		assertTrue(read("server/AbilityTerrain.java").contains("IceWall.blocked("), "a crater does not reach behind the wall");
		// the shunpo path checks the player's own box against block collisions: the wall blocks it
		assertTrue(read("server/ShunpoWorld.java").contains("isSpaceEmpty("));
	}

	@Test
	void theWallRollsBackThroughTheJournalOnEveryEnd() throws IOException {
		String temp = read("server/TempBlocks.java");
		assertTrue(temp.contains("IceWall.forget(owner)") && temp.contains("IceWall.forgetAll()"));
		String zm = read("server/ZanpakutoManager.java");
		assertTrue(zm.contains("TempBlocks.rollback(srv, p.getUuid())"), "state changes (death, dimension change, bankai end) roll the journal back");
		assertTrue(zm.contains("TempBlocks.rollback(p.getServer(), p.getUuid())"), "logout");
		assertTrue(zm.contains("TempBlocks.onServerStopping(srv)") && zm.contains("TempBlocks.onServerStarted(srv)"), "server stop and the replay after a crash");
		assertTrue(zm.contains("IceWall.init()"));
		String wall = read("server/IceWall.java");
		assertTrue(wall.contains("TempBlocks.placeWall(") && wall.contains("TempBlocks.releaseCell("));
		assertTrue(wall.contains("blocksCell") && wall.contains("LivingEntity") && wall.contains("VehicleEntity"), "cells with entities are skipped");
		assertTrue(temp.contains("getBlockEntity(pos) != null"), "cells with block entities are skipped");
	}

	@Test
	void theBlockHasItsResources() throws IOException {
		Path assets = SRC.resolve(Paths.get("main", "resources", "assets", "reiatsu_test"));
		assertTrue(Files.isRegularFile(assets.resolve("blockstates/ice_wall.json")));
		assertTrue(Files.isRegularFile(assets.resolve("models/block/ice_wall.json")));
		assertTrue(Files.isRegularFile(assets.resolve("textures/block/ice_wall.png")));
		assertTrue(Files.readString(assets.resolve("lang/en_us.json"), StandardCharsets.UTF_8).contains("\"block.reiatsu_test.ice_wall\""));
		assertTrue(Files.readString(assets.resolve("lang/ru_ru.json"), StandardCharsets.UTF_8).contains("\"block.reiatsu_test.ice_wall\""));
		String client = Files.readString(SRC.resolve(Paths.get("client", "java", "dev", "minebleach", "reiatsutest", "client", "ReiatsuTestClient.java")),
				StandardCharsets.UTF_8);
		assertTrue(client.contains("ModBlocks.ICE_WALL") && client.contains("getTranslucent()"));
		assertFalse(Files.exists(assets.resolve("textures/block/ice_spike.png")));
	}

	private static int count(String s, String needle) {
		int n = 0;
		for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
			n++;
		}
		return n;
	}
}
