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
		assertEquals("rukia_bankai", m.modelOf("rukia_bankai_sword"));
		assertTrue(m.objects.get("rukia_bankai_sword").translucent());
		assertTrue(!m.objects.get("rukia_bankai_ribbon_seg").translucent());
	}

	@Test
	void everyStateObjectExistsAndParses() throws IOException {
		ItemManifest m = manifest();
		for (var e : m.states.entrySet()) {
			java.util.List<String> dyn = m.dynamicSegments(e.getValue()).stream().map(ItemManifest.DynSeg::object).toList();
			for (List<String> list : List.of(e.getValue().hand(), e.getValue().other(), dyn)) {
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
	void firstPersonArmPoseParses() throws IOException {
		ItemManifest m = manifest();
		assertNotNull(m.firstPersonArm);
		assertTrue(m.firstPersonArm.vanilla(), "the arm is drawn like the vanilla empty hand, not scaled with the item");
		assertEquals(null, ItemManifest.parse(text("zanpakuto/spike_item.json")).firstPersonArm); // spike item: no arm
		// per-state override still supported by the parser
		ItemManifest o = ItemManifest.parse("""
				{"model":"x","display_model":"a:b","first_person_arm":{"vanilla":true},"objects":{},"states":{"sealed":{"hand":[],"other":[],"arm":{"scale":0.5}}}}""");
		assertEquals(0.5f, o.armPose("sealed").scale(), 1e-6f);
		assertTrue(o.armPose("shikai").vanilla());
	}

	@Test
	void bankaiRibbonChainExpandsToSevenSegmentsAndATip() throws IOException {
		ItemManifest m = manifest();
		var segs = m.dynamicSegments(m.states.get("bankai"));
		assertEquals(8, segs.size());
		assertEquals("rukia_bankai_ribbon_seg", segs.get(0).object());
		assertEquals("rukia_bankai_ribbon_tip", segs.get(7).object());
		// hinges every 0.35 m down the -Z axis of Blender, starting at the root
		assertEquals(-0.012f, segs.get(0).hingeBlender()[2], 1e-6f);
		assertEquals(-0.012f - 0.35f * 3, segs.get(3).hingeBlender()[2], 1e-5f);
		assertEquals(-0.012f - 0.35f * 7, segs.get(7).hingeBlender()[2], 1e-5f);
		// effect meshes are declared (so they are loaded and addressable) but not part of any in-hand state
		for (String n : List.of("rukia_bankai_crystal_a", "rukia_bankai_crystal_d", "rukia_bankai_shard_a", "rukia_bankai_ice_shell")) {
			assertTrue(m.objects.containsKey(n), n);
			assertTrue(m.states.values().stream().noneMatch(st -> st.hand().contains(n) || st.other().contains(n)));
		}
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
