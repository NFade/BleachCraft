package dev.minebleach.reiatsutest.core.gin;

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

/** The Gin item manifest points to the real exported models and textures (step 2; step 1 used the Rukia placeholder). */
class GinManifestTest {
	private static final String ASSETS = "/assets/reiatsu_test/";

	private static String text(String path) throws IOException {
		try (InputStream in = GinManifestTest.class.getResourceAsStream(ASSETS + path)) {
			assertNotNull(in, "resource missing: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ItemManifest manifest() throws IOException {
		return ItemManifest.parse(text("zanpakuto/gin_shinso.json"));
	}

	@Test
	void usesTheGinModelFolderAndTheDrawnBladeWithASeparateSaya() throws IOException {
		ItemManifest m = manifest();
		assertEquals("gin_shinso", m.model);
		assertEquals("gin_shinso_saya", m.draw.saya());
		assertEquals("gin_shinso", m.draw.blade());
		assertEquals(List.of("gin_shinso"), m.states.get("sealed").hand());
		assertEquals(List.of("gin_shinso_sheathed"), m.states.get("sealed").other());
		for (String state : List.of("base", "shikai", "bankai")) {
			assertEquals(List.of("gin_shinso"), m.states.get(state).hand(), state);
			assertEquals(List.of("gin_shinso"), m.states.get(state).other(), state);
		}
		assertTrue(m.states.get("bankai").dynamic().isEmpty(), "the bankai looks like the shikai (canon)");
	}

	@Test
	void everyObjectExistsParsesAndHasItsTexture() throws IOException {
		ItemManifest m = manifest();
		ObjMeta meta = ObjMeta.parse(text("models/obj/gin_shinso/gin_shinso_meta.json"));
		for (String object : m.objects.keySet()) {
			assertEquals("gin_shinso", m.modelOf(object));
			try (InputStream in = GinManifestTest.class.getResourceAsStream(ASSETS + "models/obj/gin_shinso/" + object + ".obj")) {
				assertNotNull(in, object + ".obj missing");
				ObjMesh mesh = ObjParser.parse(in, object);
				assertTrue(!ObjGeometry.toModelSpace(mesh, meta, object).isEmpty(), object);
			}
			ItemManifest.ObjectDef def = m.objects.get(object);
			for (String tex : new String[] {def.diffuse(), def.emissive()}) {
				if (tex != null) {
					try (InputStream in = GinManifestTest.class.getResourceAsStream(ASSETS + "textures/item/" + tex + ".png")) {
						assertNotNull(in, tex + ".png missing");
					}
				}
			}
		}
		assertTrue(m.objects.containsKey("gin_shinso_ray"), "the thrust mesh is declared");
		assertTrue(m.states.values().stream().noneMatch(st -> st.hand().contains("gin_shinso_ray") || st.other().contains("gin_shinso_ray")),
				"the ray is an effect mesh, never in the hand");
	}
}
