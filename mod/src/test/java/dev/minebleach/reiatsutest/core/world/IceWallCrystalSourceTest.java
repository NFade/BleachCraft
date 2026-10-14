package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * R11 source guards for the crystal look of the ice wall (Minecraft classes cannot be loaded in unit tests): the block keeps the behaviour of a full block,
 * the model is hooked to the block state with a safe fallback, and the model draws what the field says.
 */
class IceWallCrystalSourceTest {
	private static final Path SRC = Paths.get("src");
	private static final Path ASSETS = SRC.resolve(Paths.get("main", "resources", "assets", "reiatsu_test"));
	private static final Path CLIENT = SRC.resolve(Paths.get("client", "java", "dev", "minebleach", "reiatsutest", "client"));
	private static final Path MAIN = SRC.resolve(Paths.get("main", "java", "dev", "minebleach", "reiatsutest"));

	private static String read(Path p) throws IOException {
		assertTrue(Files.isRegularFile(p), p.toAbsolutePath().toString());
		return Files.readString(p, StandardCharsets.UTF_8);
	}

	@Test
	void theBlockStillBehavesLikeAFullBlock() throws IOException {
		String blocks = read(MAIN.resolve(Paths.get("registry", "ModBlocks.java")));
		int at = blocks.indexOf("ICE_WALL = register(\"ice_wall\"");
		assertTrue(at > 0);
		String decl = blocks.substring(at, blocks.indexOf(";", blocks.indexOf("blockVision", at)));
		assertTrue(decl.contains("ground(MapColor.LIGHT_BLUE, BlockSoundGroup.GLASS)") && decl.contains(".nonOpaque()"));
		assertFalse(decl.contains("noCollision"), "the wall keeps its collision");
		assertFalse(decl.contains("luminance"), "no block light from the crystals (it would change light updates on the server)");
		assertTrue(blocks.contains("strength(-1.0F, 3600000.0F).dropsNothing()") && blocks.contains("PistonBehavior.BLOCK"));
		String block = read(MAIN.resolve(Paths.get("block", "IceWallBlock.java")));
		assertFalse(block.contains("getCollisionShape") || block.contains("getOutlineShape"), "the model is a client matter, the shapes stay those of a full block");
	}

	@Test
	void theModelIsHookedToTheBlockStateWithAFallback() throws IOException {
		String plugin = read(CLIENT.resolve(Paths.get("model", "IceWallCrystalPlugin.java")));
		assertTrue(plugin.contains("ReiatsuTest.id(\"block/ice_wall\")"), "the resolver answers the model id of the block state");
		assertTrue(read(ASSETS.resolve(Paths.get("blockstates", "ice_wall.json"))).contains("\"reiatsu_test:block/ice_wall\""));
		assertTrue(read(ASSETS.resolve(Paths.get("models", "block", "ice_wall.json"))).contains("minecraft:block/cube_all"), "the plain cube stays as the fallback");
		assertTrue(plugin.contains("? new IceWallCrystalModel(data) : null"), "any other id is left to the game");
		assertTrue(plugin.contains("return null;") && plugin.contains("the plain cube is drawn instead"), "a failed load falls back to the cube");
		assertTrue(plugin.contains("PreparableModelLoadingPlugin.register"));
		String client = read(CLIENT.resolve("ReiatsuTestClient.java"));
		assertTrue(client.contains("IceWallCrystalPlugin.register()"));
		assertTrue(client.contains("ModBlocks.ICE_WALL") && client.contains("getTranslucent()"), "the translucent layer of the block stays");
		assertTrue(read(CLIENT.resolve(Paths.get("model", "IceWallCrystalPlugin.java"))).contains("TEXTURE = \"block/ice_wall_crystal\""));
		assertTrue(Files.isRegularFile(ASSETS.resolve(Paths.get("textures", "block", "ice_wall_crystal.png"))));
		assertTrue(Files.isRegularFile(ASSETS.resolve(Paths.get("textures", "block", "ice_wall_crystal_glow.png"))));
	}

	@Test
	void theModelDrawsWhatTheFieldSaysAndNeverCullsItself() throws IOException {
		String m = read(CLIENT.resolve(Paths.get("model", "IceWallCrystalModel.java")));
		assertTrue(m.contains("isVanillaAdapter()") && m.contains("return false;"), "a Fabric Renderer model");
		assertTrue(m.contains("emitBlockQuads(BlockRenderView blockView"));
		assertTrue(m.contains("blockView.getBlockState(pos.down())") && m.contains("blockView.getBlockState(pos.up())"), "only the two direct neighbours are looked at");
		assertEquals(2, count(m, "blockView.getBlockState("), "nothing is looked at beyond one block: a section rebuild would not follow");
		assertTrue(m.contains("IceCrystalField.cell(pos.getX(), pos.getY(), pos.getZ()"), "the variation comes from the block position only");
		assertTrue(m.contains("BlendMode.TRANSLUCENT") && m.contains(".emissive(true)"), "translucent shell, emissive glow pass");
		assertTrue(m.contains("em.cullFace(null)"), "no face is culled against a neighbour");
		assertTrue(m.contains("q.glow()"), "the glow pass only for the quads of the mask");
		assertFalse(m.contains("Random") && m.contains("randomSupplier.get()"), "no randomness: the look is the same for everybody");
		String field = read(MAIN.resolve(Paths.get("core", "world", "IceCrystalField.java")));
		assertFalse(field.contains("java.util.Random") || field.contains("Math.random"), "the field is a pure function of the position");
	}

	@Test
	void theBuildScriptsAreInTheRepository() throws IOException {
		Path root = Paths.get("..");
		for (String f : new String[] {"blender/scripts/build_rukia_ice_wall.py", "blender/scripts/ice_wall_field.py", "tools/gen_ice_crystal_textures.py",
				"tools/compose_ice_wall_sheet.py", "design/results/r11_ice_wall_sheet.png", "design/results/r11_ice_crystals.md"}) {
			assertTrue(Files.isRegularFile(root.resolve(f)), f);
		}
		String build = read(root.resolve("blender/scripts/build_rukia_ice_wall.py"));
		assertFalse(build.contains("clean_scene"), "the build script only touches its own objects (a live scene of somebody else must survive it)");
		assertFalse(build.contains("import random"), "deterministic: hash based, no random module");
		assertTrue(build.contains("export_obj") && build.contains("golden"));
	}

	private static int count(String s, String needle) {
		int n = 0;
		for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
			n++;
		}
		return n;
	}
}
