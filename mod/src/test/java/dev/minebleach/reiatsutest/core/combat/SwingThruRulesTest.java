package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** R11 T11, narrowed by review fix 7 to soft vegetation. */
class SwingThruRulesTest {
	@Test
	void grassWithAZanpakutoSwingsInsteadOfBreaking() {
		assertTrue(SwingThruRules.shouldSwing(true, true, true, 0.0F));
	}

	@Test
	void otherItemsAreUntouched() {
		assertFalse(SwingThruRules.shouldSwing(false, true, true, 0.0F));
	}

	@Test
	void solidOrHarderBlocksCanStillBeDug() {
		assertFalse(SwingThruRules.shouldSwing(true, true, false, 0.0F), "a block you collide with");
		assertFalse(SwingThruRules.shouldSwing(true, true, true, 0.2F), "vines, snow layers and the like");
		assertFalse(SwingThruRules.shouldSwing(true, true, false, 1.5F), "stone");
	}

	@Test
	void unbreakableIsNotAGrassCase() {
		assertFalse(SwingThruRules.shouldSwing(true, true, true, -1.0F));
		assertFalse(SwingThruRules.shouldSwing(true, true, true, Float.NaN));
	}

	/** Torches, wheat, sugar cane, redstone dust: no collision, hardness 0, but not soft vegetation: the sword breaks them like any tool. */
	@Test
	void aBlockWithoutCollisionAndHardnessButNotAPlantIsStillBreakable() {
		assertFalse(SwingThruRules.shouldSwing(true, false, true, 0.0F));
	}

	@Test
	void theSoftPlantListHasTheGrassFamilyAndNotTheOthers() throws IOException {
		Path main = Paths.get("src", "main", "java", "dev", "minebleach", "reiatsutest", "server");
		String plants = Files.readString(main.resolve("SoftPlants.java"), StandardCharsets.UTF_8);
		for (String yes : new String[] {"FROST_SPRIG", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH", "SMALL_FLOWERS", "TALL_FLOWERS"}) {
			assertTrue(plants.contains(yes), yes);
		}
		String code = plants.substring(plants.indexOf("public static boolean isSoftPlant"));
		for (String no : new String[] {"TORCH", "WHEAT", "CARROTS", "POTATOES", "SUGAR_CANE", "REDSTONE_WIRE", "SAPLING", "TRIPWIRE", "NETHER_WART", "CROPS", "FLOWERS_ALL"}) {
			assertFalse(code.contains(no), no + " must not be protected");
		}
		String guard = Files.readString(main.resolve("GrassGuard.java"), StandardCharsets.UTF_8);
		assertTrue(guard.contains("SoftPlants.isSoftPlant(state)"));
	}
}
