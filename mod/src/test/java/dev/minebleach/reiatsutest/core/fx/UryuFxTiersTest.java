package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The per tier values of the Uryu effects, URYU_VFX_STORYBOARD 5.1 (High / Medium / Low). */
class UryuFxTiersTest {
	@Test
	void highMediumLowAreTheRowsOfTheStoryboard() {
		var h = UryuFxTiers.HIGH;
		assertEquals(160, h.maxFlechettes());
		assertEquals(600, h.maxStardust());
		assertEquals(120, h.starFlakes());
		assertTrue(h.wingOverglow());
		assertTrue(h.bands());
		assertEquals(UryuFxTiers.VeinOverlay.FULL, h.veinOverlay());
		assertEquals(1.0, h.rainSplashShare());
		assertEquals(3, h.afterimages());

		var m = UryuFxTiers.MEDIUM;
		assertEquals(80, m.maxFlechettes());
		assertEquals(300, m.maxStardust());
		assertEquals(60, m.starFlakes());
		assertTrue(m.wingOverglow());
		assertTrue(m.bands());
		assertEquals(UryuFxTiers.VeinOverlay.FULL, m.veinOverlay());
		assertEquals(0.5, m.rainSplashShare());
		assertEquals(2, m.afterimages());

		var l = UryuFxTiers.LOW;
		assertEquals(40, l.maxFlechettes());
		assertEquals(120, l.maxStardust());
		assertEquals(30, l.starFlakes());
		assertFalse(l.wingOverglow());
		assertFalse(l.bands());
		assertEquals(UryuFxTiers.VeinOverlay.ARMS, l.veinOverlay());
		assertEquals(0.25, l.rainSplashShare());
		assertEquals(0, l.afterimages());
	}

	@Test
	void everyCapNeverGrowsTowardsTheLowerTier() {
		var tiers = new UryuFxTiers.Values[] {UryuFxTiers.HIGH, UryuFxTiers.MEDIUM, UryuFxTiers.LOW};
		for (int i = 0; i + 1 < tiers.length; i++) {
			var a = tiers[i];
			var b = tiers[i + 1];
			assertTrue(a.maxFlechettes() >= b.maxFlechettes());
			assertTrue(a.maxStardust() >= b.maxStardust());
			assertTrue(a.starFlakes() >= b.starFlakes());
			assertTrue(a.rainSplashShare() >= b.rainSplashShare());
			assertTrue(a.afterimages() >= b.afterimages());
			assertTrue(!b.wingOverglow() || a.wingOverglow());
			assertTrue(!b.bands() || a.bands());
		}
	}

	@Test
	void tierNamesAreParsedLikeFxConfigDoes() {
		assertSame(UryuFxTiers.LOW, UryuFxTiers.of("LOW"));
		assertSame(UryuFxTiers.LOW, UryuFxTiers.of(" low "));
		assertSame(UryuFxTiers.MEDIUM, UryuFxTiers.of("Medium"));
		assertSame(UryuFxTiers.HIGH, UryuFxTiers.of("HIGH"));
		assertSame(UryuFxTiers.HIGH, UryuFxTiers.of("CUSTOM"), "CUSTOM reads the HIGH row (FxConfig.tier())");
		assertSame(UryuFxTiers.HIGH, UryuFxTiers.of(null));
		assertSame(UryuFxTiers.HIGH, UryuFxTiers.of("nonsense"));
	}

	@Test
	void veinOverlayAndWingScaleAreClamped() {
		assertEquals(UryuFxTiers.VeinOverlay.ARMS, UryuFxTiers.parseVein("arms"));
		assertEquals(UryuFxTiers.VeinOverlay.ARMS, UryuFxTiers.parseVein("ARMS"));
		assertEquals(UryuFxTiers.VeinOverlay.FULL, UryuFxTiers.parseVein("full"));
		assertEquals(UryuFxTiers.VeinOverlay.FULL, UryuFxTiers.parseVein(null));
		assertEquals(UryuFxTiers.VeinOverlay.FULL, UryuFxTiers.parseVein("anything"));
		assertEquals(1.5, UryuFxTiers.clampWingScale(3.0), "the wing scale is 1.5 at most");
		assertEquals(0.5, UryuFxTiers.clampWingScale(0.0));
		assertEquals(1.0, UryuFxTiers.clampWingScale(1.0));
		assertEquals(1.25, UryuFxTiers.clampWingScale(1.25));
	}
}
