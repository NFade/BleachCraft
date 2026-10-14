package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.AxisMapper;
import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * R11: the Blender made OBJ pieces and the atlas of the ice wall crystals: files present, every piece parses, the triangle budget (design/ART_BIBLE.md 0.6),
 * the size of every piece, the UV cells, the glow mask and the worst case of a whole 9 x 5 x 2 wall counted with the Java field.
 */
class IceWallCrystalResourcesTest {
	private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "reiatsu_test");
	private static final Path OBJ_DIR = ASSETS.resolve(Paths.get("models", "obj", "rukia_ice_wall"));
	/** Budgets of ART_BIBLE 0.6 (rukia_ice_wall): per OBJ object, shell + core of one piece, one block, the 9 x 5 x 2 wall. */
	static final int BUDGET_OBJECT = 96;
	static final int BUDGET_PIECE = 120;
	static final int BUDGET_CELL = 280;
	static final int BUDGET_WALL = 12000;
	/** UV cells of the atlas, pixels (x, y, w, h) on 64 x 64, the same as blender/scripts/ice_wall_field.py CELLS. */
	private static final Map<String, int[]> CELLS = Map.of("side", new int[] {0, 0, 30, 30}, "tip", new int[] {32, 0, 30, 30}, "cap", new int[] {0, 32, 14, 14},
			"core", new int[] {16, 32, 14, 30});

	private static ObjMesh mesh(String object) throws IOException {
		Path p = OBJ_DIR.resolve(object + ".obj");
		assertTrue(Files.isRegularFile(p), "missing " + p);
		return ObjParser.parse(Files.readString(p, StandardCharsets.UTF_8), p.toString());
	}

	private static int tris(ObjMesh m) {
		return m.triCount() + 2 * m.quadCount();
	}

	private static List<String> objects() {
		List<String> out = new ArrayList<>();
		for (String piece : IceCrystalField.PIECES) {
			out.add(IceCrystalField.objectName(piece, false));
			if (IceCrystalField.CORE_PIECES.contains(piece)) {
				out.add(IceCrystalField.objectName(piece, true));
			}
		}
		return out;
	}

	private static BufferedImage png(String name) throws IOException {
		Path p = ASSETS.resolve(Paths.get("textures", "block", name));
		assertTrue(Files.isRegularFile(p), "missing " + p);
		BufferedImage img = ImageIO.read(p.toFile());
		assertTrue(img != null && img.getColorModel().hasAlpha(), name + " must be RGBA");
		return img;
	}

	private static EmissiveMask mask(BufferedImage img) {
		byte[] a = new byte[img.getWidth() * img.getHeight()];
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				a[y * img.getWidth() + x] = (byte) (img.getRGB(x, y) >>> 24);
			}
		}
		return new EmissiveMask(img.getWidth(), img.getHeight(), a);
	}

	@Test
	void everyPieceAndItsMetaEntryExists() throws IOException {
		ObjMeta meta = ObjMeta.parse(Files.readString(OBJ_DIR.resolve("rukia_ice_wall_meta.json"), StandardCharsets.UTF_8));
		for (String o : objects()) {
			assertTrue(meta.objects.containsKey(o), "meta lists " + o);
			assertTrue(mesh(o).faceCount() > 0, o + " is not empty");
		}
		assertEquals(objects().size(), meta.objects.size(), "no object in the meta that the field does not know");
	}

	@Test
	void everyObjectKeepsTheTriangleBudget() throws IOException {
		for (String piece : IceCrystalField.PIECES) {
			int shell = tris(mesh(IceCrystalField.objectName(piece, false)));
			int core = IceCrystalField.CORE_PIECES.contains(piece) ? tris(mesh(IceCrystalField.objectName(piece, true))) : 0;
			assertTrue(shell <= BUDGET_OBJECT, piece + " has " + shell + " tris");
			assertTrue(core <= BUDGET_OBJECT, piece + " core has " + core + " tris");
			assertTrue(shell + core <= BUDGET_PIECE, piece + " with core has " + (shell + core) + " tris");
		}
	}

	@Test
	void everyPieceHasTheSizeOfACell() throws IOException {
		for (String o : objects()) {
			ObjMesh m = mesh(o);
			double[] lo = {1e9, 1e9, 1e9};
			double[] hi = {-1e9, -1e9, -1e9};
			for (int i = 0; i < m.vertexCount(); i++) {
				float[] b = AxisMapper.objToBlender(m.positions[i * 3], m.positions[i * 3 + 1], m.positions[i * 3 + 2]);
				for (int k = 0; k < 3; k++) {
					lo[k] = Math.min(lo[k], b[k]);
					hi[k] = Math.max(hi[k], b[k]);
				}
			}
			String at = o + " bbox " + java.util.Arrays.toString(lo) + java.util.Arrays.toString(hi);
			boolean sat = o.contains("_sat_");
			boolean feet = o.contains("_feet_");
			boolean top = o.contains("_tip_") || o.contains("_crown_");
			double reach = feet ? 0.9 : sat ? 0.7 : 0.56;
			assertTrue(lo[0] >= -reach && hi[0] <= reach && lo[1] >= -reach && hi[1] <= reach, at);
			assertTrue(lo[2] >= (sat || feet ? -0.2 : -1e-6), at);
			assertTrue(hi[2] <= (sat ? 1.5 : top ? 1.4 : feet ? 0.6 : 1.0 + 1e-6), at);
			if (o.contains("_shaft_")) {
				assertEquals(1.0, hi[2], 1e-5, at + " a shaft fills the cell height");
			}
		}
	}

	@Test
	void everyFaceMapsIntoOneUvCell() throws IOException {
		for (String o : objects()) {
			ObjMesh m = mesh(o);
			boolean core = o.endsWith("_core");
			for (ObjMesh.Face f : m.faces) {
				float minU = 1e9f, maxU = -1e9f, minV = 1e9f, maxV = -1e9f;
				for (int k = 0; k < f.corners(); k++) {
					float u = m.uvs[f.vt()[k] * 2] * 64f;
					float v = (1f - m.uvs[f.vt()[k] * 2 + 1]) * 64f;
					minU = Math.min(minU, u);
					maxU = Math.max(maxU, u);
					minV = Math.min(minV, v);
					maxV = Math.max(maxV, v);
				}
				boolean inside = false;
				for (Map.Entry<String, int[]> e : CELLS.entrySet()) {
					int[] c = e.getValue();
					if (minU >= c[0] && maxU <= c[0] + c[2] && minV >= c[1] && maxV <= c[1] + c[3]) {
						inside = true;
						assertEquals(core, e.getKey().equals("core"), o + ": the inner cores use the core cell, the shell never");
					}
				}
				assertTrue(inside, o + " has a face outside the UV cells: u " + minU + ".." + maxU + " v " + minV + ".." + maxV);
			}
		}
	}

	@Test
	void theAtlasAndTheGlowMaskAreThere() throws IOException {
		BufferedImage d = png("ice_wall_crystal.png");
		BufferedImage g = png("ice_wall_crystal_glow.png");
		assertEquals(64, d.getWidth());
		assertEquals(64, d.getHeight());
		assertEquals(64, g.getWidth());
		assertEquals(64, g.getHeight());
		// translucent crystal: no fully transparent texel inside the cells that are drawn
		for (int[] c : CELLS.values()) {
			for (int y = c[1]; y < c[1] + c[3]; y++) {
				for (int x = c[0]; x < c[0] + c[2]; x++) {
					assertTrue((d.getRGB(x, y) >>> 24) > 60, "texel " + x + "," + y + " of the atlas is nearly transparent");
				}
			}
		}
		EmissiveMask m = mask(g);
		int cores = 0;
		int shellSides = 0;
		int tipGlow = 0;
		for (String o : objects()) {
			ObjMesh mesh = mesh(o);
			for (ObjMesh.Face f : mesh.faces) {
				float[][] uv = new float[4][];
				for (int c = 0; c < 4; c++) {
					int k = Math.min(c, f.corners() - 1);
					uv[c] = new float[] {mesh.uvs[f.vt()[k] * 2], 1f - mesh.uvs[f.vt()[k] * 2 + 1]};
				}
				boolean glow = m.anyEmissive(uv);
				if (o.endsWith("_core")) {
					assertTrue(glow, o + ": every face of the inner core glows");
					cores++;
				} else if (uv[0][0] * 64f < 30.5f && uv[0][1] * 64f < 30.5f) {
					assertFalse(glow, o + ": the side facets of the shell do not glow (only the core and the tips)");
					shellSides++;
				} else if (glow) {
					tipGlow++;
				}
			}
		}
		assertTrue(cores > 60 && shellSides > 200 && tipGlow > 20, "cores " + cores + ", shell sides " + shellSides + ", glowing tips " + tipGlow);
	}

	@Test
	void theWholeWallKeepsTheBudgetAndMatchesTheBlenderCheck() throws IOException {
		Map<String, Integer> shell = new HashMap<>();
		Map<String, Integer> core = new HashMap<>();
		for (String piece : IceCrystalField.PIECES) {
			shell.put(piece, tris(mesh(IceCrystalField.objectName(piece, false))));
			core.put(piece, IceCrystalField.CORE_PIECES.contains(piece) ? tris(mesh(IceCrystalField.objectName(piece, true))) : 0);
		}
		int worst = 0;
		int wall = 0;
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 2; z++) {
				for (int y = 0; y < 5; y++) {
					IceCrystalField.Cell c = IceCrystalField.cell(x, y, z, y > 0, y < 4);
					int t = shell.get(c.piece) + core.get(c.piece);
					for (IceCrystalField.Sat s : c.sats) {
						t += shell.get(s.kind());
					}
					if (c.feet != null) {
						t += shell.get(c.feet);
					}
					worst = Math.max(worst, t);
					wall += t;
				}
			}
		}
		assertTrue(worst <= BUDGET_CELL, "worst block " + worst);
		assertTrue(wall <= BUDGET_WALL, "wall " + wall);
		assertEquals(6742, wall, "the Blender check (build_rukia_ice_wall.py verify) counted the same wall: " + wall);
		// the absolute worst: the biggest top piece with its core, 2 of the biggest satellites and the biggest feet
		int maxTop = 0;
		for (String p : IceCrystalField.TIPS) {
			maxTop = Math.max(maxTop, shell.get(p) + core.get(p));
		}
		for (String p : IceCrystalField.CROWNS) {
			maxTop = Math.max(maxTop, shell.get(p) + core.get(p));
		}
		int maxSat = IceCrystalField.SATS.stream().mapToInt(shell::get).max().orElse(0);
		int maxFeet = IceCrystalField.FEET.stream().mapToInt(shell::get).max().orElse(0);
		assertTrue(maxTop + IceCrystalField.MAX_SATS * maxSat + maxFeet <= BUDGET_CELL, "the theoretical worst block is inside the cell budget");
	}
}
