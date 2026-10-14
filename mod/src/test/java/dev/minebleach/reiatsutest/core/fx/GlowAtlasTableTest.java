package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The two glow atlases: the Java table ({@link GlowAtlasTable}, which the client enum {@code GlowSprite} reads), the rect tables of the two
 * generator scripts, the {@code *_atlas.json} the generators wrote and the PNGs must all say the same thing. A change in one place only fails
 * here. The generators are read as text from {@code ../tools} (the tests run in {@code mod/}); the assets come from the classpath.
 */
class GlowAtlasTableTest {
	private static final String FX = "/assets/reiatsu_test/textures/fx/";

	private static String tool(String name) throws IOException {
		Path p = Path.of("..", "tools", name);
		assertTrue(Files.isRegularFile(p), "generator script not found at " + p.toAbsolutePath());
		return Files.readString(p, StandardCharsets.UTF_8);
	}

	/** The text of a Python dict literal {@code NAME = { ... }}. */
	private static String block(String src, String name) {
		int i = src.indexOf(name + " = {");
		assertTrue(i >= 0, name + " not found in the script");
		int j = src.indexOf("\n}", i);
		assertTrue(j > i, name + " block does not end");
		return src.substring(i, j);
	}

	private static Map<String, GlowAtlasTable.Rect> parsePython(String blockText) {
		Map<String, GlowAtlasTable.Rect> out = new LinkedHashMap<>();
		Matcher m = Pattern.compile("\"(\\w+)\":\\s*\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\)").matcher(blockText);
		while (m.find()) {
			out.put(m.group(1), new GlowAtlasTable.Rect(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
					Integer.parseInt(m.group(5))));
		}
		return out;
	}

	private static JsonObject json(String name) throws IOException {
		try (InputStream in = GlowAtlasTableTest.class.getResourceAsStream(FX + name)) {
			assertNotNull(in, name + " is not on the classpath");
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static Map<String, GlowAtlasTable.Rect> parseJson(JsonObject sprites) {
		Map<String, GlowAtlasTable.Rect> out = new LinkedHashMap<>();
		for (var e : sprites.entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			out.put(e.getKey(), new GlowAtlasTable.Rect(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("w").getAsInt(), o.get("h").getAsInt()));
		}
		return out;
	}

	private static BufferedImage png(String name) throws IOException {
		try (InputStream in = GlowAtlasTableTest.class.getResourceAsStream(FX + name)) {
			assertNotNull(in, name + " is not on the classpath");
			return ImageIO.read(in);
		}
	}

	@Test
	void theTablesHaveNoOverlapAndStayInsideTheirAtlas() {
		assertTrue(GlowAtlasTable.consistent(GlowAtlasTable.FX, GlowAtlasTable.FX_SIZE, GlowAtlasTable.FX_SIZE), "fx_glow");
		assertTrue(GlowAtlasTable.consistent(GlowAtlasTable.URYU, GlowAtlasTable.URYU_WIDTH, GlowAtlasTable.URYU_HEIGHT), "uryu_glow");
		assertEquals(17, GlowAtlasTable.FX.size());
		assertEquals(24, GlowAtlasTable.URYU.size(), "23 sprites and the reserved block");
		assertTrue(GlowAtlasTable.URYU.containsKey("spare"));
	}

	@Test
	void theConsistencyCheckSeesOverlapAndOverflow() {
		Map<String, GlowAtlasTable.Rect> t = new LinkedHashMap<>();
		t.put("a", new GlowAtlasTable.Rect(0, 0, 8, 8));
		t.put("b", new GlowAtlasTable.Rect(7, 7, 8, 8));
		assertFalse(GlowAtlasTable.consistent(t, 32, 32), "overlap");
		t.put("b", new GlowAtlasTable.Rect(8, 8, 32, 8));
		assertFalse(GlowAtlasTable.consistent(t, 32, 32), "outside");
		t.put("b", new GlowAtlasTable.Rect(8, 8, 8, 8));
		assertTrue(GlowAtlasTable.consistent(t, 32, 32));
	}

	@Test
	void theQuincyTableMatchesTheGeneratorScript() throws IOException {
		Map<String, GlowAtlasTable.Rect> py = parsePython(block(tool("gen_uryu_textures.py"), "URYU_RECTS"));
		assertEquals(GlowAtlasTable.URYU, py, "core/fx/GlowAtlasTable.URYU and URYU_RECTS of tools/gen_uryu_textures.py differ");
	}

	@Test
	void theRukiaByakuyaTableMatchesItsGeneratorScript() throws IOException {
		Map<String, GlowAtlasTable.Rect> py = parsePython(block(tool("gen_fx_textures.py"), "GLOW_RECTS"));
		assertEquals(GlowAtlasTable.FX, py, "core/fx/GlowAtlasTable.FX and GLOW_RECTS of tools/gen_fx_textures.py differ");
	}

	@Test
	void theWrittenAtlasJsonFilesMatchTheTables() throws IOException {
		JsonObject u = json("uryu_glow_atlas.json");
		assertEquals(GlowAtlasTable.URYU_WIDTH, u.get("width").getAsInt());
		assertEquals(GlowAtlasTable.URYU_HEIGHT, u.get("height").getAsInt());
		assertEquals(GlowAtlasTable.URYU, parseJson(u.getAsJsonObject("sprites")), "uryu_glow_atlas.json");
		JsonObject f = json("fx_glow_atlas.json");
		assertEquals(GlowAtlasTable.FX_SIZE, f.get("size").getAsInt());
		assertEquals(GlowAtlasTable.FX, parseJson(f.getAsJsonObject("sprites")), "fx_glow_atlas.json");
	}

	@Test
	void thePngsHaveTheAtlasSizes() throws IOException {
		BufferedImage u = png("uryu_glow.png");
		assertEquals(GlowAtlasTable.URYU_WIDTH, u.getWidth());
		assertEquals(GlowAtlasTable.URYU_HEIGHT, u.getHeight());
		BufferedImage f = png("fx_glow.png");
		assertEquals(GlowAtlasTable.FX_SIZE, f.getWidth());
		assertEquals(GlowAtlasTable.FX_SIZE, f.getHeight());
	}

	@Test
	void everyQuincySpriteIsPaintedWhereTheTableSaysAndTheReservedBlockIsBlack() throws IOException {
		BufferedImage img = png("uryu_glow.png");
		for (var e : GlowAtlasTable.URYU.entrySet()) {
			GlowAtlasTable.Rect r = e.getValue();
			int max = 0;
			for (int y = r.y(); y < r.y() + r.h(); y++) {
				for (int x = r.x(); x < r.x() + r.w(); x++) {
					max = Math.max(max, img.getRGB(x, y) & 0xFF);
				}
			}
			if (e.getKey().equals("spare")) {
				assertEquals(0, max, "the reserved block must stay empty");
			} else {
				assertTrue(max > 128, e.getKey() + " is (almost) empty in the PNG: brightest texel " + max);
			}
		}
	}

	@Test
	void roundSpritesEndAtZeroOnTheirCellBorder() throws IOException {
		// an additive cell that does not fall to zero at its border draws a visible box: the round sprites must be black on the whole border
		BufferedImage img = png("uryu_glow.png");
		for (String name : new String[] {"zeichen_star", "web_wheel", "burst_rays", "zeichen_cross", "pent_line", "diamond_star", "platform_disc"}) {
			GlowAtlasTable.Rect r = GlowAtlasTable.uryu(name);
			int max = 0;
			for (int x = r.x(); x < r.x() + r.w(); x++) {
				max = Math.max(max, Math.max(img.getRGB(x, r.y()) & 0xFF, img.getRGB(x, r.y() + r.h() - 1) & 0xFF));
			}
			for (int y = r.y(); y < r.y() + r.h(); y++) {
				max = Math.max(max, Math.max(img.getRGB(r.x(), y) & 0xFF, img.getRGB(r.x() + r.w() - 1, y) & 0xFF));
			}
			assertTrue(max <= 6, name + " has a lit cell border (" + max + "/255): a box edge would show");
		}
	}
}
