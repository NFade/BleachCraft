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

/** Step B2: the Byakuya manifest (sealed / shikai hilt / empty bankai hand, effect meshes loadable). */
class SenbonzakuraManifestTest {
	private static final String ASSETS = "/assets/reiatsu_test/";

	private static String text(String path) throws IOException {
		try (InputStream in = SenbonzakuraManifestTest.class.getResourceAsStream(ASSETS + path)) {
			assertNotNull(in, "resource missing: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ItemManifest manifest() throws IOException {
		return ItemManifest.parse(text("zanpakuto/senbonzakura.json"));
	}

	@Test
	void statesFollowTheAdrTable() throws IOException {
		ItemManifest m = manifest();
		assertEquals(List.of("byakuya_sealed_drawn"), m.states.get("sealed").hand()); // B4 step 2: the bare sword; the saya is drawn by ScabbardRenderer
		assertEquals("byakuya_sealed_saya", m.draw.saya());
		assertNotNull(m.stow);
		assertNotNull(m.hip);
		assertEquals(List.of("byakuya_sealed_sheathed"), m.states.get("sealed").other());
		assertEquals(List.of("byakuya_shikai_hilt"), m.states.get("shikai").hand());
		assertEquals(List.of("byakuya_shikai_hilt"), m.states.get("shikai").other());
		assertTrue(m.states.get("bankai").hand().isEmpty(), "bankai: empty hand");
		assertEquals(List.of("byakuya_sealed_sheathed"), m.states.get("bankai").other());
		assertNotNull(m.firstPersonArm);
	}

	@Test
	void everyDeclaredObjectParses() throws IOException {
		ItemManifest m = manifest();
		for (String object : m.objects.keySet()) {
			String model = m.modelOf(object);
			ObjMeta meta = ObjMeta.parse(text("models/obj/" + model + "/" + model + "_meta.json"));
			try (InputStream in = SenbonzakuraManifestTest.class.getResourceAsStream(ASSETS + "models/obj/" + model + "/" + object + ".obj")) {
				assertNotNull(in, object + ".obj missing");
				ObjMesh mesh = ObjParser.parse(in, object);
				assertTrue(!ObjGeometry.toModelSpace(mesh, meta, object).isEmpty(), object);
			}
		}
	}

	@Test
	void petalAndShardAreDeclaredButNotInAnyState() throws IOException {
		ItemManifest m = manifest();
		for (String n : List.of("byakuya_shikai_petal", "byakuya_shikai_shard", "byakuya_bankai_hilt_ground", "hakuteiken_wing_l")) {
			assertTrue(m.objects.containsKey(n), n);
			assertTrue(m.states.values().stream().noneMatch(st -> st.hand().contains(n) || st.other().contains(n)), n);
		}
		assertEquals("byakuya_shikai", m.modelOf("byakuya_shikai_petal"));
		assertTrue(m.objects.get("byakuya_shikai_petal").emissive() != null);
	}

	@Test
	void texturesAndIconsExist() throws IOException {
		for (String f : List.of("textures/item/byakuya_sealed_diffuse.png", "textures/item/byakuya_shikai_diffuse.png",
				"textures/item/byakuya_shikai_emissive.png", "textures/item/byakuya_bankai_diffuse.png",
				"textures/item/senbonzakura_sealed_icon.png", "textures/item/senbonzakura_shikai_icon.png",
				"textures/item/senbonzakura_bankai_icon.png")) {
			try (InputStream in = SenbonzakuraManifestTest.class.getResourceAsStream(ASSETS + f)) {
				assertNotNull(in, f + " missing");
			}
		}
	}
}
