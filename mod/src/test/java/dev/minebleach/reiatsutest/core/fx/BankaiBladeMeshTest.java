package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * R11 T13: the new straight Byakuya bankai blade. The size the mod uses comes from the "blade" block of the meta ({@link BladeSpec}); this test reads the real
 * meta and the real OBJ files and checks that they agree (length, thickness, width, apex), that the UVs stay inside the four blade areas of the atlas
 * (blender/scripts/atlas_layouts.py BYAKUYA_BANKAI, 512 px), the triangle budget and that the mesh is closed.
 */
class BankaiBladeMeshTest {
	private static final String DIR = "/assets/reiatsu_test/models/obj/byakuya_bankai/";
	private static final double ATLAS = 512.0;
	/** blade_a, blade_b, blade_s, blade_e as {x, y, w, h} in atlas pixels (top left origin). */
	private static final Map<String, int[]> AREAS = new HashMap<>();

	static {
		AREAS.put("blade_a", new int[] {2, 2, 20, 252});
		AREAS.put("blade_b", new int[] {24, 2, 20, 252});
		AREAS.put("blade_s", new int[] {46, 2, 6, 252});
		AREAS.put("blade_e", new int[] {54, 2, 2, 252});
	}

	@AfterEach
	void reset() {
		BladeSpec.reset();
	}

	private static String text(String file) throws IOException {
		try (InputStream in = BankaiBladeMeshTest.class.getResourceAsStream(DIR + file)) {
			assertNotNull(in, file);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ObjMesh mesh(String object) throws IOException {
		return ObjParser.parse(text(object + ".obj"), object);
	}

	private static double[] extent(ObjMesh m, int axis) {
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (int i = 0; i < m.vertexCount(); i++) {
			lo = Math.min(lo, m.positions[i * 3 + axis]);
			hi = Math.max(hi, m.positions[i * 3 + axis]);
		}
		return new double[] {lo, hi};
	}

	@Test
	void metaCarriesTheBladeSizeAndBladeSpecTakesIt() throws IOException {
		ObjMeta meta = ObjMeta.parse(text("byakuya_bankai_meta.json"));
		assertNotNull(meta.blade, "the meta has a blade block");
		assertEquals(5.5, meta.blade.length(), 1e-9);
		assertEquals(0.4026, meta.blade.width(), 1e-9);
		assertEquals(0.1477, meta.blade.thickness(), 1e-9);
		assertEquals(5.5, meta.empties.get("tip")[2], 1e-6, "the tip empty hangs at the length");
		BladeSpec.apply(new ObjMeta.Blade(3.0, 0.2, 0.1, new double[] {0, 0.5, 3.0}));
		assertEquals(3.0, BladeSpec.length(), 0);
		assertEquals(-0.5, BladeSpec.tipZ(), 1e-12, "Blender (x, y, z) to OBJ (x, z, -y): the OBJ z of the apex is -y");
		BladeSpec.apply(new ObjMeta.Blade(Double.NaN, -1, 0, null));
		assertEquals(3.0, BladeSpec.length(), 0, "a bad number keeps the current value");
		BladeSpec.apply(null);
		BladeSpec.apply(meta.blade);
		assertEquals(5.5, BladeSpec.length(), 1e-9);
		assertEquals(0.4026, BladeSpec.width(), 1e-9);
		assertEquals(0.1477, BladeSpec.thickness(), 1e-9);
		assertEquals(-0.200997, BladeSpec.tipZ(), 1e-9, "apex (0, 5.5, -0.201) in the OBJ frame");
	}

	@Test
	void defaultsAreTheShippedModel() throws IOException {
		ObjMeta meta = ObjMeta.parse(text("byakuya_bankai_meta.json"));
		assertEquals(BladeSpec.DEFAULT_LENGTH, meta.blade.length(), 1e-9);
		assertEquals(BladeSpec.DEFAULT_WIDTH, meta.blade.width(), 1e-9);
		assertEquals(BladeSpec.DEFAULT_THICKNESS, meta.blade.thickness(), 1e-9);
		assertEquals(BladeSpec.DEFAULT_TIP_Z, -meta.blade.tip()[1], 1e-9);
	}

	@Test
	void objDimensionsMatchTheMeta() throws IOException {
		ObjMeta meta = ObjMeta.parse(text("byakuya_bankai_meta.json"));
		for (String name : new String[] {"byakuya_bankai_blade", "byakuya_bankai_blade_lod"}) {
			ObjMesh m = mesh(name);
			double[] x = extent(m, 0);
			double[] y = extent(m, 1);
			double[] z = extent(m, 2);
			assertEquals(5.5, y[1] - y[0], 0.01, name + " length");
			assertEquals(0.0, y[0], 0.005, name + " base at 0");
			assertEquals(0.1477, x[1] - x[0], 0.002, name + " thickness");
			assertEquals(0.4026, z[1] - z[0], 0.01, name + " width");
			assertEquals(meta.blade.length(), y[1] - y[0], 0.01);
		}
		// the apex of the full mesh: (0, 5.5, -0.201) in OBJ axes = the meta tip (0, 0.201, 5.5) in Blender axes
		ObjMesh m = mesh("byakuya_bankai_blade");
		int apex = 0;
		for (int i = 0; i < m.vertexCount(); i++) {
			if (m.positions[i * 3 + 1] > m.positions[apex * 3 + 1]) {
				apex = i;
			}
		}
		assertEquals(0.0, m.positions[apex * 3], 0.002);
		assertEquals(meta.blade.tip()[2], m.positions[apex * 3 + 1], 0.001);
		assertEquals(-meta.blade.tip()[1], m.positions[apex * 3 + 2], 0.001);
	}

	@Test
	void meshIsStraight() throws IOException {
		// no bend: the middle of the width at every height stays within a few cm of the axis (the old mesh bent 0.83 m)
		ObjMesh m = mesh("byakuya_bankai_blade");
		double maxCentre = 0;
		for (double h = 0.5; h <= 4.6; h += 0.5) {
			double lo = Double.MAX_VALUE;
			double hi = -Double.MAX_VALUE;
			for (int i = 0; i < m.vertexCount(); i++) {
				if (Math.abs(m.positions[i * 3 + 1] - h) < 0.3) {
					lo = Math.min(lo, m.positions[i * 3 + 2]);
					hi = Math.max(hi, m.positions[i * 3 + 2]);
				}
			}
			if (lo <= hi) {
				maxCentre = Math.max(maxCentre, Math.abs((lo + hi) / 2));
			}
		}
		assertTrue(maxCentre < 0.05, "centre line within 5 cm of the axis: " + maxCentre);
	}

	@Test
	void triangleBudgetAndUvAreas() throws IOException {
		int[] budget = {300, 24};
		String[] names = {"byakuya_bankai_blade", "byakuya_bankai_blade_lod"};
		for (int k = 0; k < 2; k++) {
			ObjMesh m = mesh(names[k]);
			int tris = 0;
			for (ObjMesh.Face f : m.faces) {
				tris += f.corners() - 2;
			}
			assertTrue(tris <= budget[k], names[k] + " triangles " + tris + " <= " + budget[k]);
			assertTrue(tris > 0);
			for (ObjMesh.Face f : m.faces) {
				boolean inside = false;
				for (int[] a : AREAS.values()) {
					boolean all = true;
					for (int c = 0; c < f.corners(); c++) {
						double px = m.uvs[f.vt()[c] * 2] * ATLAS;
						double py = (1.0 - m.uvs[f.vt()[c] * 2 + 1]) * ATLAS; // (OBJ v runs up, the atlas rows run down)
						double eps = 0.02;
						if (px < a[0] - eps || px > a[0] + a[2] + eps || py < a[1] - eps || py > a[1] + a[3] + eps) {
							all = false;
							break;
						}
					}
					if (all) {
						inside = true;
						break;
					}
				}
				assertTrue(inside, names[k] + ": a face has UVs outside blade_a / blade_b / blade_s / blade_e");
			}
		}
	}

	@Test
	void meshesAreClosed() throws IOException {
		for (String name : new String[] {"byakuya_bankai_blade", "byakuya_bankai_blade_lod"}) {
			ObjMesh m = mesh(name);
			Map<Long, Integer> edges = new HashMap<>();
			for (ObjMesh.Face f : m.faces) {
				for (int c = 0; c < f.corners(); c++) {
					long a = f.v()[c];
					long b = f.v()[(c + 1) % f.corners()];
					edges.merge(Math.min(a, b) * 100_000L + Math.max(a, b), 1, Integer::sum);
				}
			}
			for (Map.Entry<Long, Integer> e : edges.entrySet()) {
				assertEquals(2, e.getValue(), name + ": every edge lies in exactly two faces (edge " + e.getKey() + ")");
			}
		}
	}
}
