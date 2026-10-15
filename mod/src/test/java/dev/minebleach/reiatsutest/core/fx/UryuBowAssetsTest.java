package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjMesh;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import dev.minebleach.reiatsutest.core.obj.ObjParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The bow item of Uryu (manifest {@code zanpakuto/uryu_cross.json}) after the new Quincy bow and arrow of the {@code uryu_fx} set replaced the
 * old ones (URYU_VFX_STORYBOARD 4.1, U-FX 2): the manifest parses, every object it names exists in its model folder with a meta that lists it, the
 * textures exist, and the geometry follows the contracts the item model code and {@code UryuArrowRenderer} rely on (the arrow lies along +Y from its
 * tail at the origin and is 0.5 m long, the bow is 1.1 m along Y with its limbs bending to -Z, the side of the string).
 */
class UryuBowAssetsTest {
	private static final String ROOT = "/assets/reiatsu_test/";

	private static String text(String path) throws IOException {
		try (InputStream in = UryuBowAssetsTest.class.getResourceAsStream(ROOT + path)) {
			assertNotNull(in, path + " is not on the classpath");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static ObjMesh obj(String model, String object) throws IOException {
		return ObjParser.parse(text("models/obj/" + model + "/" + object + ".obj"), model + "/" + object);
	}

	private static float[] bounds(ObjMesh m) {
		float[] b = {1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f};
		for (int i = 0; i < m.vertexCount(); i++) {
			for (int a = 0; a < 3; a++) {
				float v = m.positions[i * 3 + a];
				b[a] = Math.min(b[a], v);
				b[a + 3] = Math.max(b[a + 3], v);
			}
		}
		return b;
	}

	@Test
	void theManifestPointsEveryObjectAtAnExistingMeshMetaAndTexture() throws IOException {
		ItemManifest man = ItemManifest.parse(text("zanpakuto/uryu_cross.json"));
		for (String object : man.objects.keySet()) {
			String model = man.modelOf(object);
			ObjMeta meta = ObjMeta.parse(text("models/obj/" + model + "/" + model + "_meta.json"));
			assertTrue(meta.objects.containsKey(object), model + "_meta.json lists " + object);
			assertTrue(obj(model, object).faceCount() > 0, object + " has faces");
			ItemManifest.ObjectDef def = man.objects.get(object);
			assertNotNull(UryuBowAssetsTest.class.getResourceAsStream(ROOT + "textures/item/" + def.diffuse() + ".png"), def.diffuse());
			if (def.emissive() != null) {
				assertNotNull(UryuBowAssetsTest.class.getResourceAsStream(ROOT + "textures/item/" + def.emissive() + ".png"), def.emissive());
			}
		}
		for (String state : new String[] {"base", "shikai", "bankai"}) {
			assertTrue(man.states.get(state).hand().contains("uryu_bow"), state + " holds the bow");
			assertTrue(man.states.get(state).hand().contains("uryu_bow_string"), state + " holds the string");
		}
		assertEquals("uryu_fx", man.modelOf("uryu_bow"));
		assertEquals("uryu_fx", man.modelOf("uryu_arrow"));
		assertEquals("uryu_fx", man.modelOf("uryu_bow_flex_1"));
		assertEquals("uryu", man.modelOf("uryu_cross"));
		assertEquals("uryu", man.modelOf("uryu_bow_string"), "the straight string keeps the old set (same tip positions)");
	}

	@Test
	void theQuincyAtlasHasTheContractSize() throws IOException {
		for (String name : new String[] {"uryu_fx_diffuse", "uryu_fx_emissive"}) {
			try (InputStream in = UryuBowAssetsTest.class.getResourceAsStream(ROOT + "textures/item/" + name + ".png")) {
				assertNotNull(in, name);
				BufferedImage im = ImageIO.read(in);
				assertEquals(256, im.getWidth(), name);
				assertEquals(256, im.getHeight(), name);
				assertTrue(im.getColorModel().hasAlpha(), name + " is RGBA (the emissive alpha is the intensity)");
			}
		}
	}

	@Test
	void theArrowLiesAlongPlusYFromItsTail() throws IOException {
		float[] b = bounds(obj("uryu_fx", "uryu_arrow"));
		assertEquals(0.5f, b[4], 0.005f, "the point is 0.5 m from the tail");
		assertTrue(b[1] >= -0.001f && b[1] < 0.05f, "the tail starts at the origin (min y " + b[1] + ")");
		assertTrue(b[3] - b[0] < 0.12f && b[5] - b[2] < 0.12f, "thin: width " + (b[3] - b[0]) + " x " + (b[5] - b[2]));
	}

	@Test
	void theBowIsOnePointOneMetresAlongYWithTheLimbsOnTheStringSide() throws IOException {
		for (String name : new String[] {"uryu_bow", "uryu_bow_flex_1", "uryu_bow_flex_2", "uryu_bow_flex_3"}) {
			float[] b = bounds(obj("uryu_fx", name));
			assertEquals(1.1f, b[4] - b[1], 0.06f, name + " height (the flex bows are a little shorter)");
			assertTrue(b[2] < -0.09f, name + " bends to -Z, the string side (min z " + b[2] + ")");
			assertTrue(b[5] < 0.2f, name + " the arrow side stays flat (max z " + b[5] + ")");
		}
		float rest = bounds(obj("uryu_fx", "uryu_bow"))[2];
		float flex3 = bounds(obj("uryu_fx", "uryu_bow_flex_3"))[2];
		assertTrue(flex3 < rest - 0.04f, "the flex bows bend further to the string side (" + rest + " then " + flex3 + ")");
	}

	@Test
	void theStringTipsOfTheNewBowAreWhereTheOldStringAttaches() throws IOException {
		ObjMeta fx = ObjMeta.parse(text("models/obj/uryu_fx/uryu_fx_meta.json"));
		ObjMeta old = ObjMeta.parse(text("models/obj/uryu/uryu_meta.json"));
		for (String e : new String[] {"bow_string_top", "bow_string_bottom", "bow_grip_hand"}) {
			float[] a = fx.empties.get(e);
			float[] b = old.empties.get(e);
			assertNotNull(a, e);
			assertNotNull(b, e);
			for (int i = 0; i < 3; i++) {
				assertEquals(b[i], a[i], 0.002f, e + " [" + i + "]");
			}
		}
		assertNotNull(fx.empties.get("arrow_rest"));
	}
}
