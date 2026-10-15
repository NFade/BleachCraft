package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R12 task 1: the row layout presets follow the ground plane fit of refs/byakuya_bankai_3.png (pixels from tools/measure_bankai_rows.py). */
class BladeRowFitTest {
	// {x, baseY} of the nearest blades of the frame (1249 x 937), nearest first; vanishing point from the least squares of the base/top lines
	private static final double[][] LEFT = {{129.0, 758}, {203.5, 716}, {259.0, 690}, {307.5, 666}, {348.0, 644}, {381.5, 628}, {411.0, 612}, {436.5, 600}, {466.0, 588}};
	private static final double[][] RIGHT = {{1098.0, 753}, {1024.5, 715}, {969.0, 685}, {920.0, 663}, {878.0, 643}, {846.0, 627}, {816.0, 608}};
	private static final double VP_X = 639.7;
	private static final double VP_Y = 499.2;
	private static final double FEET_Y = 785;
	private static final double BODY_PX = 298; // hair top 487 .. feet 785

	private static BladeRowFit.Result at(double fov) {
		return BladeRowFit.fit(LEFT, RIGHT, 16, VP_X, VP_Y, FEET_Y, BODY_PX, 1.8, 1249, fov);
	}

	@Test
	void eyeHeightIsTheEyeOfAStandingByakuya() {
		BladeRowFit.Result r = at(50);
		assertEquals(1.73, r.eye(), 0.05);
	}

	@Test
	void corridorMatchesThePresetsHalfWidthAndStart() {
		BladeRowFit.Result r = at(50);
		assertEquals(BladeRowPresets.MEASURED_HALF_WIDTH, r.halfWidth(), 0.25, "half width of the lane");
		assertEquals(-BladeRowPresets.MEASURED_START_AHEAD, r.startBehind(), 0.2, "nearest blade behind the feet");
		assertEquals(4.95, r.bladeHeight(), 0.25, "blade height in blocks, about 2.75 heights of Byakuya");
	}

	@Test
	void animePresetPitchIsTheFiftyDegreeFit() {
		assertEquals(BladeRowPresets.ANIME.pitch(), at(50).pitch(), 0.15);
	}

	@Test
	void pitchScalesWithTheFocalLength() {
		double p50 = at(50).pitch();
		assertEquals(at(35).pitch(), BladeRowFit.pitchForFov(p50, 50, 35), 0.02);
		assertEquals(at(70).pitch(), BladeRowFit.pitchForFov(p50, 50, 70), 0.02);
		assertTrue(at(70).pitch() < BladeRowPresets.BOOK.pitch() && BladeRowPresets.BOOK.pitch() < at(35).pitch(), "the default sits inside the 35..70 degree range");
		assertEquals(BladeRowPresets.DENSE.pitch(), 1.0, 0);
	}

	@Test
	void presetsAreOrderedAndHaveGaps() {
		assertTrue(BladeRowPresets.DENSE.pitch() < BladeRowPresets.BOOK.pitch() && BladeRowPresets.BOOK.pitch() < BladeRowPresets.ANIME.pitch());
		for (BladeRowPresets.Preset p : BladeRowPresets.all()) {
			assertTrue(BladeRowLayout.gap(p.pitch(), 1.0) > 0.5, p.key() + " leaves a gap between slabs");
			assertTrue(p.halfWidth() > 2.0, p.key() + " keeps the caster inside the lane");
			assertTrue(p.startAhead() <= 0, p.key() + " starts at or behind the feet");
			assertTrue(p.length() < 40, p.key());
		}
	}

	@Test
	void keysResolveAndFallBackToTheDefault() {
		assertSame(BladeRowPresets.DENSE, BladeRowPresets.byKey("Dense"));
		assertSame(BladeRowPresets.ANIME, BladeRowPresets.byKey(" anime "));
		assertSame(BladeRowPresets.R9, BladeRowPresets.byKey("r9"));
		assertSame(BladeRowPresets.R7, BladeRowPresets.byKey("r7"));
		assertSame(BladeRowPresets.DEFAULT, BladeRowPresets.byKey(""));
		assertSame(BladeRowPresets.DEFAULT, BladeRowPresets.byKey(null));
		assertSame(BladeRowPresets.DEFAULT, BladeRowPresets.byKey("nonsense"));
		assertSame(BladeRowPresets.BOOK, BladeRowPresets.DEFAULT);
	}

	@Test
	void presetBuildsALaneWithItsNumbers() {
		BladeRowPresets.Preset p = BladeRowPresets.BOOK;
		BladeRowLayout l = BladeRowLayout.lane(p.perSide(), 1, p.halfWidth(), p.pitch(), p.startAhead());
		assertEquals(2 * p.perSide(), l.n);
		assertEquals(p.startAhead(), l.along[0], 1e-5);
		assertEquals(-p.halfWidth(), l.across[0], 1e-5);
		assertEquals(p.length() - p.startAhead(), l.length(), 1e-4, "length() counts from the feet (origin included)");
	}

	@Test
	void defaultIsSevenBladesPerSideAndBroaderBlades() {
		assertEquals(7, BladeRowPresets.DEFAULT.perSide());
		assertEquals(1.35, BladeRowLayout.DEFAULT_WIDTH_FACTOR, 0);
		// the broadened slab still leaves a gap to its neighbour along the row (thickness is not stretched)
		assertTrue(BladeRowLayout.gap(BladeRowPresets.DEFAULT.pitch(), 1.0) > 1.0);
	}

	@Test
	void framePinsOfPass2() {
		// R12 pass 2 (frame ep52): vertical shafts. After the in-game test the user asked for 7 blades per side near the caster: the default (book) has 7,
		// the presets are short rows of 5..12 blades (the frame shows 18..28 but the game camera is much wider)
		assertEquals(0.0, BladeRowLayout.LEAN_DEG, 0, "shafts are vertical in the frame");
		for (BladeRowPresets.Preset p : new BladeRowPresets.Preset[] {BladeRowPresets.DENSE, BladeRowPresets.BOOK, BladeRowPresets.ANIME}) {
			assertTrue(p.perSide() >= 5 && p.perSide() <= 12, p.key() + " blades per side " + p.perSide());
			assertTrue(p.length() >= 8 && p.length() <= 14, p.key() + " length " + p.length());
		}
	}
}
