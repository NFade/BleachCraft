package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The vein overlay textures of the Blut Arterie (tools/gen_uryu_textures.py, block "U-FX 6"): the finished skin and the
 * {@link UryuBlutTiming#STAGES} draw-on versions in the player skin UV layout. Checks the layout (arms in the columns that exist on classic
 * AND slim arms, chest, neck; nothing on the head, the legs or the unused skin area), the density ("brighter and denser than Blut Vene"),
 * the draw-on order (fingertips first, neck last, every stage contains the one before) and the colours of the palette.
 */
class UryuVeinSkinTest {
	private static final String FX = "/assets/reiatsu_test/textures/fx/";
	private static final Set<Integer> R_ARM_COLS = Set.of(40, 41, 42, 43, 44, 45, 46, 48, 49, 50, 52, 53);
	private static final Set<Integer> L_ARM_COLS = Set.of(32, 33, 34, 35, 36, 37, 38, 40, 41, 42, 44, 45);

	private static BufferedImage png(String name) throws IOException {
		try (InputStream in = UryuVeinSkinTest.class.getResourceAsStream(FX + name)) {
			assertNotNull(in, name + " is not on the classpath (run python -I tools/gen_uryu_textures.py)");
			return ImageIO.read(in);
		}
	}

	private static int alpha(BufferedImage im, int x, int y) {
		return im.getRGB(x, y) >>> 24;
	}

	private static int rgb(BufferedImage im, int x, int y) {
		return im.getRGB(x, y) & 0xFFFFFF;
	}

	private static BufferedImage stage(int k) throws IOException {
		return png(String.format("uryu_vein/stage_%02d.png", k));
	}

	private static boolean inRArm(int x, int y) {
		return y >= 20 && y <= 31 && R_ARM_COLS.contains(x);
	}

	private static boolean inLArm(int x, int y) {
		return y >= 52 && y <= 63 && L_ARM_COLS.contains(x);
	}

	private static boolean inTorso(int x, int y) {
		return y >= 20 && y <= 31 && x >= 16 && x <= 39;
	}

	private static boolean inCap(int x, int y) {
		return (x >= 44 && x <= 46 && y >= 16 && y <= 19) || (x >= 36 && x <= 38 && y >= 48 && y <= 51);
	}

	private static int count(BufferedImage im, int x0, int y0, int x1, int y1, boolean veinOnly) {
		int n = 0;
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				int a = alpha(im, x, y);
				if (veinOnly ? a == 255 : a > 0) {
					n++;
				}
			}
		}
		return n;
	}

	@Test
	void allVersionsExistAtSkinSizeAndTheLastOneIsTheFinishedSkin() throws IOException {
		BufferedImage skin = png("uryu_vein_skin.png");
		assertEquals(64, skin.getWidth());
		assertEquals(64, skin.getHeight());
		BufferedImage last = stage(UryuBlutTiming.STAGES - 1);
		for (int k = 0; k < UryuBlutTiming.STAGES; k++) {
			BufferedImage s = stage(k);
			assertEquals(64, s.getWidth(), "stage " + k);
			assertEquals(64, s.getHeight(), "stage " + k);
		}
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				assertEquals(skin.getRGB(x, y), last.getRGB(x, y), "the last stage is the finished skin at " + x + "," + y);
			}
		}
	}

	@Test
	void onlyArmsChestAndNeckArePaintedInTheColumnsOfBothArmLayouts() throws IOException {
		BufferedImage skin = png("uryu_vein_skin.png");
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (alpha(skin, x, y) > 0) {
					assertTrue(inRArm(x, y) || inLArm(x, y) || inTorso(x, y) || inCap(x, y), "a vein pixel outside the painted faces at " + x + "," + y);
				}
			}
		}
		// nothing on the head (0..31 x 0..15), the legs (0..15 x 16..31, 16..31 x 48..63) or the second layer (v 32..47)
		assertEquals(0, count(skin, 0, 0, 31, 15, false), "head");
		assertEquals(0, count(skin, 32, 0, 63, 15, false), "hat");
		assertEquals(0, count(skin, 0, 16, 15, 31, false), "right leg");
		assertEquals(0, count(skin, 16, 48, 31, 63, false), "left leg");
		assertEquals(0, count(skin, 0, 32, 63, 47, false), "second layer rows");
		// the columns that differ between the classic and slim arms stay empty: 47 and 51 of the right arm, 39 and 43 of the left arm
		for (int y = 20; y <= 31; y++) {
			assertEquals(0, alpha(skin, 47, y), "right arm column 47 exists on classic arms only");
			assertEquals(0, alpha(skin, 51, y), "right arm column 51 exists on classic arms only");
			assertEquals(0, alpha(skin, 54, y));
			assertEquals(0, alpha(skin, 55, y));
		}
		for (int y = 52; y <= 63; y++) {
			assertEquals(0, alpha(skin, 39, y), "left arm column 39 exists on classic arms only");
			assertEquals(0, alpha(skin, 43, y), "left arm column 43 exists on classic arms only");
			assertEquals(0, alpha(skin, 46, y));
			assertEquals(0, alpha(skin, 47, y));
		}
	}

	@Test
	void theVeinsAreDenseOnBothArmsAndTheChest() throws IOException {
		BufferedImage skin = png("uryu_vein_skin.png");
		int rArm = count(skin, 40, 20, 53, 31, true);
		int lArm = count(skin, 32, 52, 45, 63, true);
		int torso = count(skin, 16, 20, 39, 31, true);
		assertTrue(rArm >= 35, "right arm vein pixels " + rArm);
		assertTrue(lArm >= 35, "left arm vein pixels " + lArm);
		assertTrue(torso >= 55, "chest vein pixels " + torso);
		// every one of the four faces of an arm carries a vein (a face of the 12 px belt: 4 + 3 + 3 + 2 columns)
		int[][] faces = {{40, 43}, {44, 46}, {48, 50}, {52, 53}};
		for (int[] f : faces) {
			assertTrue(count(skin, f[0], 20, f[1], 31, true) >= 3, "right arm face " + f[0] + ".." + f[1]);
		}
		int[][] lfaces = {{32, 35}, {36, 38}, {40, 42}, {44, 45}};
		for (int[] f : lfaces) {
			assertTrue(count(skin, f[0], 52, f[1], 63, true) >= 3, "left arm face " + f[0] + ".." + f[1]);
		}
		assertTrue(count(skin, 20, 20, 27, 31, true) >= 15, "chest front");
		assertTrue(count(skin, 32, 20, 39, 31, true) >= 15, "chest back");
		// the neck: the collar rows at the middle of the chest front and back
		assertTrue(count(skin, 22, 20, 25, 23, true) >= 5, "neck front");
		assertTrue(count(skin, 34, 20, 37, 23, true) >= 5, "neck back");
		// it must not become a blue sheet: arms and chest at most 60 percent covered by vein and halo together (the halo is a faint alpha 110)
		assertTrue(count(skin, 40, 20, 53, 31, false) <= 14 * 12 * 6 / 10, "right arm coverage");
		assertTrue(count(skin, 16, 20, 39, 31, false) <= 24 * 12 * 6 / 10, "chest coverage");
	}

	@Test
	void theColoursAreThePaletteOfTheQuincy() throws IOException {
		BufferedImage skin = png("uryu_vein_skin.png");
		boolean core = false;
		boolean halo = false;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int a = alpha(skin, x, y);
				if (a == 0) {
					continue;
				}
				int c = rgb(skin, x, y);
				if (a == 255) {
					assertTrue(c == 0x4FC8FF || c == 0xE4FAFF, String.format("vein colour %06X at %d,%d", c, x, y));
					core |= c == 0xE4FAFF;
				} else {
					assertEquals(110, a, "halo alpha at " + x + "," + y);
					assertEquals(0x1F8FE0, c, "halo colour");
					halo = true;
				}
			}
		}
		assertTrue(core, "a Q1 core pixel on some segments");
		assertTrue(halo, "the Q4 halo that makes the lines read at noon");
	}

	@Test
	void everyStageContainsTheOneBeforeAndTheFingertipsLightFirst() throws IOException {
		BufferedImage prev = stage(0);
		for (int k = 1; k < UryuBlutTiming.STAGES; k++) {
			BufferedImage cur = stage(k);
			int grown = 0;
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					if (alpha(prev, x, y) > 0) {
						assertTrue(alpha(cur, x, y) > 0, "stage " + k + " lost the pixel " + x + "," + y);
					}
					if (alpha(cur, x, y) > 0 && alpha(prev, x, y) == 0) {
						grown++;
					}
				}
			}
			assertTrue(grown > 0, "stage " + k + " adds pixels");
			prev = cur;
		}
		// stage 0: only the last three rows of the hands (rows 29..31 of the right arm, 61..63 of the left arm)
		BufferedImage s0 = stage(0);
		int n0 = 0;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (alpha(s0, x, y) > 0) {
					n0++;
					assertTrue((inRArm(x, y) && y >= 29) || (inLArm(x, y) && y >= 61), "stage 0 shows only the fingertips, found " + x + "," + y);
				}
			}
		}
		assertTrue(n0 >= 10, "the fingertips are visible in the first stage: " + n0);
		// the arms (g up to 0.45) are lit before the chest (g from 0.5)
		BufferedImage s4 = stage(4);
		assertEquals(0, count(s4, 16, 20, 39, 31, false), "no chest pixel before the arms are lit (the chest gradient starts at 0.5)");
		assertTrue(count(stage(5), 16, 20, 39, 31, false) > 0, "the chest lights from stage 5 (its lowest row, g 0.5)");
		// the neck last: the collar rows are empty until the last 3 stages
		assertEquals(0, count(stage(8), 22, 20, 25, 21, false) + count(stage(8), 34, 20, 37, 21, false), "the very collar is not lit at stage 8");
		assertTrue(count(stage(11), 22, 20, 25, 23, false) > 0);
	}

	@Test
	void theBrightHeadOfTheSweepIsTheRimColourAndTheTailTheCyan() throws IOException {
		BufferedImage s = stage(2);
		int head = 0;
		int tail = 0;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (alpha(s, x, y) == 255) {
					int c = rgb(s, x, y);
					if (c == 0xE4FAFF) {
						head++;
					} else if (c == 0x4FC8FF) {
						tail++;
					}
				}
			}
		}
		assertTrue(head >= 3, "bright head pixels " + head);
		assertTrue(tail >= 3, "cyan trail pixels " + tail);
	}

	@Test
	void theGeneratorBlockIsSeparatedAndNeverTouchesTheBlenderAtlas() throws IOException {
		Path py = Path.of("..", "tools", "gen_uryu_textures.py");
		if (!Files.exists(py)) {
			py = Path.of("tools", "gen_uryu_textures.py");
		}
		String src = Files.readString(py, StandardCharsets.UTF_8);
		assertTrue(src.contains("U-FX 6: the vein skin"), "a clearly separated vein block");
		assertTrue(src.contains("VEIN_SEED = 4711"));
		assertTrue(src.contains("def gen_vein_skin"));
		assertFalse(src.contains("\"uryu_fx_diffuse.png\"") || src.contains("\"uryu_fx_emissive.png\""));
		// the stage count of the generator is the one of the Java tables
		int i = src.indexOf("VEIN_STAGES = ");
		assertTrue(i > 0);
		assertEquals(UryuBlutTiming.STAGES, Integer.parseInt(src.substring(i + "VEIN_STAGES = ".length()).split("\\s", 2)[0].trim()));
	}
}
