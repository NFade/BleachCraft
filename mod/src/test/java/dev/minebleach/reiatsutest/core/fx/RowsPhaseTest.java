package dev.minebleach.reiatsutest.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Dissolve and regrowth of the standing rows (6.2, 6.3, S3). */
class RowsPhaseTest {
	private static final RowsPhase.Timing S = RowsPhase.Timing.scatter();

	@Test
	void scatterCutRunsFromTheTipDownInFortyHundredthsAtTheStoryboardTimes() {
		// blade at the release point (d = 0): standing until 0.3, gone at 0.7
		assertEquals(1.0, RowsPhase.fraction(S, 0.29, 0), 1e-9);
		assertEquals(0.5, RowsPhase.fraction(S, 0.50, 0), 1e-9);
		assertEquals(0.0, RowsPhase.fraction(S, 0.70, 0), 1e-9);
		assertEquals(0.0, RowsPhase.fraction(S, 3.0, 0), 1e-9);
		// the cut is 0.008 s per block later at a blade 50 m away
		assertEquals(1.0, RowsPhase.fraction(S, 0.3 + 0.008 * 50 - 1e-6, 50), 1e-3);
		assertEquals(0.0, RowsPhase.fraction(S, 0.3 + 0.008 * 50 + 0.4, 50), 1e-9);
	}

	@Test
	void regrowthIsBottomUpFromFourPointFiveAndFinishesBy5Point7ForTheNearBlade() {
		assertEquals(0.0, RowsPhase.fraction(S, 4.49, 0), 1e-9);
		assertEquals(0.5, RowsPhase.fraction(S, 4.8, 0), 1e-9);
		assertEquals(1.0, RowsPhase.fraction(S, 5.1, 0), 1e-9);
		assertEquals(5.1, S.end(0), 1e-9);
		// the farthest blade of 200 (about 55 m) finishes at 4.5 + 0.004 d + 0.6, storyboard "4.5-5.7"
		assertEquals(5.32, S.end(55), 1e-9);
		assertTrue(S.end(110) < 5.7 + 1e-9);
	}

	@Test
	void stagesFollowInOrder() {
		assertEquals(RowsPhase.Stage.STANDING, RowsPhase.stage(S, 0.1, 10));
		assertEquals(RowsPhase.Stage.DISSOLVING, RowsPhase.stage(S, 0.45, 10));
		assertEquals(RowsPhase.Stage.GONE, RowsPhase.stage(S, 2.0, 10));
		assertEquals(RowsPhase.Stage.REGROWING, RowsPhase.stage(S, 4.8, 10));
		assertEquals(RowsPhase.Stage.STANDING, RowsPhase.stage(S, 5.3, 10));
		assertTrue(RowsPhase.animating(S, 2.0, 10));
		assertFalse(RowsPhase.animating(S, 0.1, 10));
		assertFalse(RowsPhase.animating(S, 6.0, 10));
	}

	@Test
	void petalBirthMatchesTheCutAndConsumptionMatchesTheRegrowth() {
		for (double d : new double[] {0, 12.5, 40}) {
			for (double y : new double[] {0, 2, 5.3, 8}) {
				double born = S.cutPassesAt(d, y);
				// at the moment the cut passes y the blade fraction x 8 equals y
				assertEquals(y, RowsPhase.fraction(S, born, d) * 8.0, 1e-9);
				double eaten = S.regrowReaches(d, y);
				assertEquals(y, RowsPhase.fraction(S, eaten, d) * 8.0, 1e-9);
				assertTrue(eaten > born);
			}
		}
	}

	@Test
	void hakuteikenDissolvesFastAndRegrowsFromThree() {
		RowsPhase.Timing c = RowsPhase.Timing.consumed();
		assertEquals(0.0, RowsPhase.fraction(c, 0.31, 0), 1e-9);
		assertEquals(0.0, RowsPhase.fraction(c, 2.9, 20), 1e-9);
		assertEquals(0.0, RowsPhase.fraction(c, 3.0, 0), 1e-9);
		assertEquals(1.0, RowsPhase.fraction(c, 3.7, 0), 1e-9);
		assertTrue(c.end(55) < 4.2 + 1e-9, "re-form is over by the end of the 4.2 s effect: " + c.end(55));
		assertEquals(0.002, RowsPhase.Timing.of(RowsPhase.CONSUMED).cutPerBlock, 1e-12);
		assertEquals(0.008, RowsPhase.Timing.of(RowsPhase.SCATTERED).cutPerBlock, 1e-12);
	}

	@Test
	void edgeGlowOnlyWhileTheBladeChanges() {
		assertEquals(0.0, RowsPhase.edgeGlow(S, 0.1, 0), 1e-9);
		assertTrue(RowsPhase.edgeGlow(S, 0.5, 0) > 0.5);
		assertEquals(0.0, RowsPhase.edgeGlow(S, 2.0, 0), 1e-9);
		assertTrue(RowsPhase.edgeGlow(S, 4.8, 0) > 0.5);
		assertEquals(0.0, RowsPhase.edgeGlow(S, 5.5, 0), 1e-9);
	}

	@Test
	void anchorPhaseAgeIsClampedAtZero() {
		assertEquals(0.0, RowsPhase.ageSeconds(100, 105, 0.5), 1e-9);
		assertEquals(2.0, RowsPhase.ageSeconds(140, 100, 0.0), 1e-9);
		assertEquals(2.025, RowsPhase.ageSeconds(140, 100, 0.5), 1e-9);
	}
}
