package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import dev.minebleach.reiatsutest.core.obj.ObjParseException;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjParserTest {
	private static final String BASE = "/assets/reiatsu_test/models/obj/spike/";

	private static ObjMesh load(String name) throws IOException {
		try (InputStream in = ObjParserTest.class.getResourceAsStream(BASE + name + ".obj")) {
			assertTrue(in != null, "resource missing: " + name);
			return ObjParser.parse(in, name + ".obj");
		}
	}

	private static ObjMeta meta() throws IOException {
		try (InputStream in = ObjParserTest.class.getResourceAsStream(BASE + "spike_meta.json")) {
			return ObjMeta.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	void spikeCubeCounts() throws IOException {
		ObjMesh m = load("spike_cube");
		assertEquals("spike_cube", m.name);
		assertEquals(16, m.vertexCount());
		assertEquals(24, m.uvCount());
		assertEquals(6, m.normalCount());
		assertEquals(12, m.faceCount());
		assertEquals(10, m.quadCount());
		assertEquals(2, m.triCount());
		assertTrue(m.warnings.isEmpty(), m.warnings.toString());
	}

	@Test
	void spikeCubeBoundsAfterAxisMapping() throws IOException {
		List<ObjGeometry.Quad> quads = ObjGeometry.toModelSpace(load("spike_cube"), meta(), "spike_cube");
		assertEquals(12, quads.size());
		float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (ObjGeometry.Quad q : quads) {
			for (float[] p : q.pos()) {
				for (int i = 0; i < 3; i++) {
					lo[i] = Math.min(lo[i], p[i]);
					hi[i] = Math.max(hi[i], p[i]);
				}
			}
		}
		// Blender cube +-0.1 in x/y, z 0..0.8; grip (0,0,0.19). model = (x, z - 0.19, -y) + 0.5
		assertArrayNear(new float[] {0.4f, 0.31f, 0.4f}, lo);
		assertArrayNear(new float[] {0.6f, 1.11f, 0.6f}, hi);
	}

	@Test
	void triangleBecomesDegenerateQuad() throws IOException {
		List<ObjGeometry.Quad> quads = ObjGeometry.toModelSpace(load("spike_cube"), meta(), "spike_cube");
		long tris = quads.stream().filter(ObjGeometry.Quad::triangle).count();
		assertEquals(2, tris);
		for (ObjGeometry.Quad q : quads) {
			if (q.triangle()) {
				assertArrayNear(q.pos()[2], q.pos()[3]);
			}
		}
	}

	@Test
	void uvIsFlipped() throws IOException {
		ObjMesh m = load("spike_cube");
		List<ObjGeometry.Quad> quads = ObjGeometry.toModelSpace(m, meta(), "spike_cube");
		ObjMesh.Face f0 = m.faces.get(0);
		float vObj = m.uvs[f0.vt()[0] * 2 + 1];
		assertEquals(1.0f - vObj, quads.get(0).uv()[0][1], 1e-6f);
	}

	@Test
	void stripOriginUsesMetaTable() throws IOException {
		// strip_01 hinge at Blender (0,0,-0.01) -> model (0.5, -0.01 - 0.19 + 0.5, 0.5)
		ObjMeta meta = meta();
		float[] h = meta.originModel("spike_strip_01");
		assertArrayNear(new float[] {0.5f, 0.30f, 0.5f}, h);
		List<ObjGeometry.Quad> quads = ObjGeometry.toModelSpace(load("spike_strip_01"), meta, "spike_strip_01");
		float maxY = -Float.MAX_VALUE;
		for (ObjGeometry.Quad q : quads) {
			for (float[] p : q.pos()) {
				maxY = Math.max(maxY, p[1]);
			}
		}
		assertEquals(0.30f, maxY, 1e-5f); // top edge of the strip sits on the hinge
	}

	@Test
	void negativeIndicesAndMissingNormal() {
		String obj = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nf -4/-4 -3/-3 -2/-2 -1/-1\n";
		ObjMesh m = ObjParser.parse(obj, "inline.obj");
		assertEquals(1, m.faceCount());
		assertEquals(-1, m.faces.get(0).vn()[0]);
		ObjMeta meta = ObjMeta.parse("{\"empties\":{\"grip_hand\":[0,0,0]},\"objects\":{}}");
		ObjGeometry.Quad q = ObjGeometry.toModelSpace(m, meta, "x").get(0);
		// face in the OBJ xy plane, CCW seen from +Z -> flat normal +Z
		assertArrayNear(new float[] {0, 0, 1}, q.nrm()[0]);
	}

	@Test
	void errorsCarryFileAndLine() {
		ObjParseException e = assertThrows(ObjParseException.class,
				() -> ObjParser.parse("v 0 0 0\nv 1 0 0\nv 1 1 0\nf 1 2 3\n", "bad.obj"));
		assertTrue(e.getMessage().startsWith("bad.obj:4:"), e.getMessage());
		e = assertThrows(ObjParseException.class, () -> ObjParser.parse(
				"v 0 0 0\nvt 0 0\nf 1/1 1/1 1/1 1/1 1/1\n", "ngon.obj"));
		assertTrue(e.getMessage().startsWith("ngon.obj:3:"), e.getMessage());
	}

	@Test
	void unknownKeywordWarnsOncePerFile() {
		ObjMesh m = ObjParser.parse("foo 1\nfoo 2\nusemtl a\ns off\n", "w.obj");
		assertEquals(1, m.warnings.size());
	}

	private static void assertArrayNear(float[] expected, float[] actual) {
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], actual[i], 1e-5f, "component " + i);
		}
	}
}
