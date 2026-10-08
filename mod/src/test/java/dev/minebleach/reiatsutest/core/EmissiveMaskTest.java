package dev.minebleach.reiatsutest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.obj.EmissiveMask;
import dev.minebleach.reiatsutest.core.obj.ItemManifest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EmissiveMaskTest {
	@Test
	void quadOverGlowTexelsIsKeptOthersDropped() {
		byte[] a = new byte[64 * 64];
		// glow only in the 4x6 strip at x 32..36, y 16..22 (top 0.10 m of the bar strip)
		for (int y = 16; y < 22; y++) {
			for (int x = 32; x < 36; x++) {
				a[y * 64 + x] = (byte) 255;
			}
		}
		EmissiveMask m = new EmissiveMask(64, 64, a);
		float[][] glowQuad = {{32 / 64f, 52 / 64f}, {36 / 64f, 52 / 64f}, {36 / 64f, 16 / 64f}, {32 / 64f, 16 / 64f}};
		float[][] otherQuad = {{0, 0}, {16 / 64f, 0}, {16 / 64f, 16 / 64f}, {0, 16 / 64f}};
		float[][] belowGlow = {{32 / 64f, 52 / 64f}, {36 / 64f, 52 / 64f}, {36 / 64f, 22 / 64f}, {32 / 64f, 22 / 64f}};
		assertTrue(m.anyEmissive(glowQuad));
		assertFalse(m.anyEmissive(otherQuad));
		assertFalse(m.anyEmissive(belowGlow));
	}

	@Test
	void spikeEmissiveTextureHasGlowOnlyOnBarTop() throws IOException {
		// the shipped texture is RGBA with A = intensity: decode PNG alpha through ImageIO
		try (InputStream in = EmissiveMaskTest.class.getResourceAsStream("/assets/reiatsu_test/textures/item/spike_emissive.png")) {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
			assertTrue(img.getColorModel().hasAlpha());
			int opaque = 0;
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					if ((img.getRGB(x, y) >>> 24) > 0) {
						opaque++;
						assertEquals(0xFFFFFF, img.getRGB(x, y) & 0xFFFFFF, "glow RGB must be white");
					}
				}
			}
			assertEquals(112, opaque); // 4 bar sides x 4x6 + 4x4 cap
		}
	}

	@Test
	void manifestParses() throws IOException {
		try (InputStream in = EmissiveMaskTest.class.getResourceAsStream("/assets/reiatsu_test/zanpakuto/spike_item.json")) {
			ItemManifest m = ItemManifest.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			assertEquals("spike", m.model);
			assertEquals(0xFFFFAA33, m.emissiveTint);
			assertEquals(4, m.objects.size());
			assertEquals(java.util.List.of("spike_strip_01", "spike_strip_02"), m.states.get("sealed").dynamic());
			assertTrue(m.states.get("bankai").hand().isEmpty());
		}
	}
}
