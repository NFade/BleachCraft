package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The emissive tint hook of the bow in the item model. */
class UryuBowGlowTest {
	private static final double EPS = 1e-9;

	@Test
	void anIdleBowWithoutFocusIsNeutral() {
		assertTrue(UryuBowGlow.neutral(UryuBowGlow.bow(0, 0.0, -1.0)));
		assertEquals(1.0, UryuBowGlow.bow(0, 0.0, 99.0), EPS);
	}

	@Test
	void focusDrawAndTheMuzzleFlashAddUp() {
		assertEquals(1.3, UryuBowGlow.bow(1, 0.0, -1.0), EPS);
		assertEquals(1.0 + 0.25, UryuBowGlow.bow(0, 1.0, -1.0), EPS);
		assertEquals(1.0 + 1.0, UryuBowGlow.bow(0, 0.0, 0.0), EPS, "the muzzle flash doubles the glow");
		assertEquals(1.5, UryuBowGlow.bow(0, 0.0, 0.04), EPS, "and fades over 0.08 s");
		assertEquals(1.0, UryuBowGlow.bow(0, 0.0, 0.08), EPS);
		assertTrue(UryuBowGlow.bow(3, 1.0, 0.0) <= UryuBowGlow.MAX + EPS, "capped");
		assertTrue(UryuBowGlow.bow(3, 1.0, 0.0) > 2.0);
	}

	@Test
	void belowOneTheAlphaFallsAboveOneTheColourGoesWhite() {
		int tint = 0xFFBFD8FF;
		assertEquals(tint, UryuBowGlow.argb(tint, 1.0));
		int dim = UryuBowGlow.argb(tint, 0.5);
		assertEquals(0x80, dim >>> 24, "half the alpha");
		assertEquals(tint & 0xFFFFFF, dim & 0xFFFFFF, "colour kept");
		assertEquals(0, UryuBowGlow.argb(tint, 0.0) >>> 24);
		assertEquals(0, UryuBowGlow.argb(tint, -3.0) >>> 24);
		int hot = UryuBowGlow.argb(tint, 2.0);
		assertEquals(0xFF, hot >>> 24, "alpha kept");
		assertTrue(((hot >> 16) & 255) > 0xBF, "red moved towards white");
		assertTrue(((hot >> 8) & 255) > 0xD8, "green moved towards white");
		assertEquals(0xFF, hot & 255, "blue was already full");
		int white = UryuBowGlow.argb(tint, 99.0);
		assertEquals(0xFFFFFFFF, white, "the mix is capped at white");
	}

	@Test
	void theExtraPassAddsTheBrightnessAboveOne() {
		assertEquals(0.0, UryuBowGlow.extraPass(0.5), EPS);
		assertEquals(0.0, UryuBowGlow.extraPass(1.0), EPS);
		assertEquals(0.3, UryuBowGlow.extraPass(1.3), 1e-9);
		assertEquals(1.0, UryuBowGlow.extraPass(2.6), EPS);
		assertFalse(UryuBowGlow.neutral(1.3));
	}

	@Test
	void scaleAlphaKeepsTheColour() {
		assertEquals(0x80123456, UryuBowGlow.scaleAlpha(0xFF123456, 0.5));
		assertEquals(0xFF123456, UryuBowGlow.scaleAlpha(0xFF123456, 3.0));
		assertEquals(0x00123456, UryuBowGlow.scaleAlpha(0xFF123456, -1.0));
	}
}
