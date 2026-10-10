package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.AxisMapper;
import dev.minebleach.reiatsutest.core.obj.DrawAnimation;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import dev.minebleach.reiatsutest.core.obj.ObjMeta;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Step B3: the draw-from-scabbard animation maths against the shipped manifests and metas of both sealed models. */
class DrawAnimationTest {
	private static final String ASSETS = "/assets/reiatsu_test/";

	private static String text(String path) throws IOException {
		try (InputStream in = DrawAnimationTest.class.getResourceAsStream(ASSETS + path)) {
			assertNotNull(in, "resource missing: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private record Case(String item, String model) {
	}

	private static final Case[] CASES = {new Case("sode_no_shirayuki", "rukia_sealed"), new Case("senbonzakura", "byakuya_sealed")};

	@Test
	void manifestsDeclareTheSayaAndTheDrawEntry() throws IOException {
		for (Case c : CASES) {
			ItemManifest m = ItemManifest.parse(text("zanpakuto/" + c.item() + ".json"));
			assertNotNull(m.draw, c.item());
			// B4 step 2: the scabbard is drawn client side by ScabbardRenderer, so it is NOT in the item's sealed hand list
			assertFalse(m.states.get("sealed").hand().contains(m.draw.saya()), "saya is no longer baked into the sealed hand mesh");
			assertTrue(m.states.get("sealed").hand().contains(m.draw.blade()), "the bare sword is the sealed hand mesh");
			assertNotNull(m.stow, c.item() + ": stow pose");
			assertNotNull(m.hip, c.item() + ": hip pose");
			assertTrue(m.stow.slideEnd() > 0.2f && m.stow.slideEnd() < 0.9f);
			assertEquals(3, m.hip.dir().length);
			assertTrue(m.objects.containsKey(m.draw.saya()));
			ObjMeta meta = ObjMeta.parse(text("models/obj/" + c.model() + "/" + c.model() + "_meta.json"));
			assertNotNull(meta.bladeAxis, "blade_axis in " + c.model());
			assertTrue(meta.objects.containsKey(m.draw.saya()));
			assertEquals(1f, meta.bladeAxis.arcAxis()[0], 1e-6f);
			assertTrue(m.draw.hold()[2] > meta.empties.get("saya_mouth")[2], "hold point is on the saya, above the mouth");
		}
	}

	@Test
	void progressZeroIsTheSheathedSwordAndOneIsTheHandOnTheHilt() throws IOException {
		for (Case c : CASES) {
			ItemManifest m = ItemManifest.parse(text("zanpakuto/" + c.item() + ".json"));
			ObjMeta meta = ObjMeta.parse(text("models/obj/" + c.model() + "/" + c.model() + "_meta.json"));
			DrawAnimation d = new DrawAnimation(m.draw, meta);
			float[] v = {0.5f, 0.8f, 0.45f};
			float[] w = v.clone();
			d.sword(w, 0f);
			assertArrayEquals(v, w);
			d.saya(w, 0f);
			assertArrayEquals(v, w);
			assertEquals(1f, d.sayaScale(0f), 0f);
			// p = 1: hand factor 1 (the baked frame shifted back by holdOff = pivot on the hilt grip), saya gone
			assertEquals(1f, d.handFactor(1f), 1e-6f);
			assertEquals(0f, d.sayaScale(1f), 1e-6f);
			float[] g = {0.5f, 0.5f, 0.5f}; // a point at the hold in the baked frame is the origin (0.5): after p=1 it is holdOff above
			float[] gh = {g[0] - d.holdOff[0], g[1] - d.holdOff[1], g[2] - d.holdOff[2]}; // the hilt grip in the baked frame
			d.sword(gh, 1f);
			assertArrayEquals(g, gh); // the grip sits at the item origin again
			assertTrue(d.holdOff[1] > 0.1f && d.holdOff[1] < 0.2f, "hold is about 14 cm above the grip, along the blade (model +Y): " + d.holdOff[1]);
		}
	}

	@Test
	void theSayaSwingsOffAlongTheSoriArcSoTheBladeClearsTheMouth() throws IOException {
		for (Case c : CASES) {
			ItemManifest m = ItemManifest.parse(text("zanpakuto/" + c.item() + ".json"));
			ObjMeta meta = ObjMeta.parse(text("models/obj/" + c.model() + "/" + c.model() + "_meta.json"));
			DrawAnimation d = new DrawAnimation(m.draw, meta);
			float[] grip = meta.empties.get("grip_hand");
			float[] mouth = AxisMapper.blenderToModel(meta.empties.get("saya_mouth"), grip);
			float[] tip = AxisMapper.blenderToModel(meta.empties.get("tip"), grip);
			for (int i = 0; i < 3; i++) {
				mouth[i] -= d.holdOff[i]; // baked frame
				tip[i] -= d.holdOff[i];
			}
			// angle at travel = clearTravel is clearTravel / R; the mouth, carried along the arc relative to the sword, meets the tip
			float p = 1f;
			float full = d.angle(p) / d.overshoot; // angle for travel = clearTravel
			assertEquals(d.clearTravel / d.radius, full, 1e-5f);
			float[] mv = mouth.clone();
			float save = d.overshoot;
			assertTrue(save >= 1f);
			// emulate p with overshoot 1: rotate the mouth by -full about the arc centre
			float cos = (float) Math.cos(-full);
			float sin = (float) Math.sin(-full);
			float y = mv[1] - d.centre[1];
			float z = mv[2] - d.centre[2];
			mv[1] = d.centre[1] + y * cos - z * sin;
			mv[2] = d.centre[2] + y * sin + z * cos;
			float dist = (float) Math.sqrt(Math.pow(mv[1] - tip[1], 2) + Math.pow(mv[2] - tip[2], 2));
			assertTrue(dist < 0.012f, c.model() + ": the mouth reaches the blade tip after clearTravel, off by " + dist);
			// monotonic travel, no motion at the start
			assertEquals(0f, d.travel(0f), 0f);
			assertTrue(d.travel(0.5f) > d.travel(0.25f) && d.travel(1f) > d.clearTravel);
		}
	}

	private static void assertArrayEquals(float[] a, float[] b) {
		for (int i = 0; i < a.length; i++) {
			assertEquals(a[i], b[i], 1e-5f);
		}
	}
}
