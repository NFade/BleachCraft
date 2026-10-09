package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjGeometry;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Step B: the real Rukia manifest (objects from several Blender models) resolves against the shipped assets. */
class RukiaManifestTest {
	private static final String ASSETS = "/assets/reiatsu_test/";

	private static String text(String path) throws IOException {
		try (InputStream in = RukiaManifestTest.class.getResourceAsStream(ASSETS + path)) {
			assertNotNull(in, "resource missing: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ItemManifest manifest() throws IOException {
		return ItemManifest.parse(text("zanpakuto/sode_no_shirayuki.json"));
	}

	@Test
	void objectsResolveToTheirOwnModelFolder() throws IOException {
		ItemManifest m = manifest();
		assertEquals("rukia_sealed", m.model);
		assertEquals("rukia_sealed", m.modelOf("rukia_sealed_drawn"));
		assertEquals("rukia_shikai", m.modelOf("rukia_shikai_blade"));
		assertEquals("rukia_shikai", m.modelOf("rukia_shikai_ribbon_10"));
		assertEquals("spike", m.modelOf("spike_cube")); // bankai placeholder until its model exists
	}

	@Test
	void everyStateObjectExistsAndParses() throws IOException {
		ItemManifest m = manifest();
		for (var e : m.states.entrySet()) {
			for (List<String> list : List.of(e.getValue().hand(), e.getValue().other(), e.getValue().dynamic())) {
				for (String object : list) {
					assertTrue(m.objects.containsKey(object), e.getKey() + " references unknown object " + object);
					String model = m.modelOf(object);
					ObjMeta meta = ObjMeta.parse(text("models/obj/" + model + "/" + model + "_meta.json"));
					try (InputStream in = RukiaManifestTest.class.getResourceAsStream(ASSETS + "models/obj/" + model + "/" + object + ".obj")) {
						assertNotNull(in, object + ".obj missing");
						ObjMesh mesh = ObjParser.parse(in, object);
						assertTrue(!ObjGeometry.toModelSpace(mesh, meta, object).isEmpty());
					}
				}
			}
		}
	}

	@Test
	void shikaiChainIsTenSegmentsWithHingesDownTheBlade() throws IOException {
		ItemManifest m = manifest();
		List<String> chain = m.states.get("shikai").dynamic();
		assertEquals(10, chain.size());
		ObjMeta meta = ObjMeta.parse(text("models/obj/rukia_shikai/rukia_shikai_meta.json"));
		float prevY = Float.MAX_VALUE;
		for (String seg : chain) {
			float y = meta.originModel(seg)[1];
			assertTrue(y < prevY, seg + " hinge must lie below the previous one");
			prevY = y;
		}
		// segment 01 hinges at ribbon_root (z = -0.010 in Blender), grip at z 0.19: 0.20 m below the block centre
		assertEquals(0.5f - 0.20f, meta.originModel(chain.get(0))[1], 1e-5f);
	}

	@Test
	void atlasesAndIconsExist() throws IOException {
		for (String f : List.of("textures/item/rukia_sealed_diffuse.png", "textures/item/rukia_shikai_diffuse.png",
				"textures/item/rukia_shikai_emissive.png", "textures/item/sode_no_shirayuki_sealed_icon.png",
				"textures/item/sode_no_shirayuki_shikai_icon.png", "textures/item/sode_no_shirayuki_bankai_icon.png")) {
			try (InputStream in = RukiaManifestTest.class.getResourceAsStream(ASSETS + f)) {
				assertNotNull(in, f + " missing");
			}
		}
	}
}
