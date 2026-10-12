package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * The frost blocks must never slide the player (user request, round 6): no friction override in the block settings and no vanilla ice
 * in the freeze. Minecraft classes cannot be loaded in unit tests, so this guard reads the sources: the default slipperiness of a block
 * is 0.6 (stone), vanilla ice, packed ice and blue ice are 0.98.
 */
class FrostBlocksSourceTest {
	private static String read(String rel) throws IOException {
		Path p = Paths.get("src", "main", "java", "dev", "minebleach", "reiatsutest").resolve(rel);
		assertTrue(Files.isRegularFile(p), p.toAbsolutePath().toString());
		return Files.readString(p, StandardCharsets.UTF_8);
	}

	private static String code(String src) {
		StringBuilder sb = new StringBuilder();
		for (String line : src.split("\\R")) {
			String t = line.trim();
			if (!t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	@Test
	void frostBlocksKeepTheDefaultFriction() throws IOException {
		String blocks = code(read("registry/ModBlocks.java"));
		assertFalse(blocks.contains("slipperiness("), "no slipperiness override: the default 0.6 is the stone value");
		assertFalse(blocks.contains("velocityMultiplier("), "no speed change on frost blocks");
		assertFalse(blocks.contains("jumpVelocityMultiplier("));
		assertFalse(blocks.contains("Blocks.ICE") || blocks.contains("PACKED_ICE") || blocks.contains("BLUE_ICE") || blocks.contains("FROSTED_ICE"));
		assertFalse(code(read("block/FrostSprigBlock.java")).contains("getSlipperiness"));
	}

	@Test
	void theFreezeNeverPlacesVanillaIce() throws IOException {
		String field = code(read("server/FrostField.java"));
		assertFalse(field.contains("Blocks.ICE"), "vanilla ice slides");
		assertFalse(field.contains("PACKED_ICE") || field.contains("BLUE_ICE") || field.contains("FROSTED_ICE") || field.contains("POWDER_SNOW"));
		assertTrue(field.contains("ModBlocks.FROST_CRUST") && field.contains("ModBlocks.FROST_FILM"));
	}

	@Test
	void theBankaiTerritoryAndTheIceSpikePlaceNoTemporaryIce() throws IOException {
		// Absolute Zero (removed in mech2 task 4) used to place random slippery ice; the passive territory only slows and hurts
		String exec = code(read("server/AbilityExecutor.java"));
		assertFalse(exec.contains("absoluteZero("), "the old ability code is gone");
		int b = exec.indexOf("private static void iceSpike(");
		assertTrue(b > 0);
		assertFalse(code(read("server/BankaiField.java")).contains("TempBlocks.place"));
	}
}
