package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.combat.UryuBowFlow.Phase;
import org.junit.jupiter.api.Test;

/** The string hand of the first person bow ({@link UryuStringHand}), the row helpers of {@link UryuGestures} and the breathing of {@link UryuPoseMath}. */
class UryuStringHandTest {
	private static final float[] ZERO_ROW = new float[7];

	@Test
	void theHandIsWantedWhileDrawingAndInTheFirstTicksOfTheRelease() {
		assertTrue(UryuStringHand.wanted(true, true, Phase.DRAW, 10, -1, false));
		assertTrue(UryuStringHand.wanted(true, true, Phase.RELEASE, 5, -1, false));
		assertFalse(UryuStringHand.wanted(true, true, Phase.RELEASE, UryuStringHand.RELEASE_TICKS, -1, false));
		assertFalse(UryuStringHand.wanted(true, true, Phase.IDLE, 0, -1, false));
		assertFalse(UryuStringHand.wanted(true, true, Phase.OFF, 0, -1, false));
	}

	@Test
	void theRaiseBeforeAnEventAndAGestureWithAStringHandWantTheHand() {
		assertTrue(UryuStringHand.wanted(true, true, Phase.ABILITY, 3, 0.0, false));
		assertFalse(UryuStringHand.wanted(true, true, Phase.ABILITY, 3, -1.0, false));
		assertTrue(UryuStringHand.wanted(true, true, Phase.IDLE, 0, -1.0, true));
	}

	@Test
	void noCandidateOrNoSampleMeansNoHand() {
		assertFalse(UryuStringHand.wanted(false, true, Phase.DRAW, 10, 0.5, true));
		assertFalse(UryuStringHand.wanted(true, false, Phase.DRAW, 10, 0.5, true));
	}

	@Test
	void presenceComesInFasterThanItLeavesAndStaysInRange() {
		float p = 0f;
		float dt = 0.01f;
		int in = 0;
		while (p < 1f) {
			p = UryuStringHand.presence(p, true, dt);
			in++;
			assertTrue(in < 1000);
		}
		assertEquals(UryuStringHand.IN_SECONDS, in * dt, 0.02);
		assertEquals(1f, UryuStringHand.presence(1f, true, dt));
		int out = 0;
		while (p > 0f) {
			p = UryuStringHand.presence(p, false, dt);
			out++;
			assertTrue(out < 1000);
		}
		assertEquals(UryuStringHand.OUT_SECONDS, out * dt, 0.02);
		assertTrue(out > in);
	}

	@Test
	void atFullPresenceTheFistSitsAtTheRowPlaceAndComesFromBelowBefore() {
		float[] row = {0.05f, -0.04f, 0.12f, 0f, 0f, 0f, 1f};
		UryuStringHand.Placement full = UryuStringHand.placement(1, row, 1f, 1f);
		// target (0.56 + 0.05, -0.52 - 0.04, -0.72 + 0.12); rest fist of the other hand (-(0.56 + FIST_X), -0.52 + FIST_Y, -0.72 + FIST_Z)
		assertEquals(0.61 + 0.56 + UryuStringHand.FIST_X, full.dx(), 1e-6);
		assertEquals(-0.04 - UryuStringHand.FIST_Y, full.dy(), 1e-6);
		assertEquals(0.12 - UryuStringHand.FIST_Z, full.dz(), 1e-6);
		assertEquals(-0.56, full.baseX(), 1e-9);
		UryuStringHand.Placement none = UryuStringHand.placement(1, row, 1f, 0f);
		assertEquals(full.dy() - 0.9, none.dy(), 1e-6);
		assertEquals(full.dz() + 0.1, none.dz(), 1e-6);
		assertEquals(full.dx(), none.dx(), 1e-9);
	}

	@Test
	void theLeftHanderIsTheMirrorImage() {
		float[] row = {0.05f, -0.04f, 0.12f, 3f, 4f, 5f, 1f};
		UryuStringHand.Placement r = UryuStringHand.placement(1, row, 1f, 1f);
		UryuStringHand.Placement l = UryuStringHand.placement(-1, row, 1f, 1f);
		assertEquals(-r.baseX(), l.baseX(), 1e-9);
		assertEquals(-r.dx(), l.dx(), 1e-9);
		assertEquals(r.dy(), l.dy(), 1e-9);
		assertEquals(r.dz(), l.dz(), 1e-9);
		assertEquals(r.rotDeg()[0], l.rotDeg()[0], 1e-9);
		assertEquals(-r.rotDeg()[1], l.rotDeg()[1], 1e-9);
		assertEquals(-r.rotDeg()[2], l.rotDeg()[2], 1e-9);
	}

	@Test
	void theHandRisesWithTheEquipAnimation() {
		UryuStringHand.Placement up = UryuStringHand.placement(1, ZERO_ROW, 1f, 1f);
		UryuStringHand.Placement low = UryuStringHand.placement(1, ZERO_ROW, 0f, 1f);
		assertEquals(up.baseY() - 0.6, low.baseY(), 1e-9);
	}

	@Test
	void theDrawAndReleaseRowsAreTheOnesTheStateMachineSamples() {
		assertEquals(UryuGestures.DRAW, UryuGestures.rowId(UryuAnims.DRAW));
		assertEquals(UryuGestures.RELEASE, UryuGestures.rowId(UryuAnims.RELEASE));
		assertNull(UryuGestures.rowId(UryuAnims.IDLE));
		assertNull(UryuGestures.rowId("seele"));
		// the draw holds at its last row, the release runs on
		assertEquals(1.0f, UryuGestures.rowTime(UryuAnims.DRAW, 20.0, 1.0), 1e-6);
		assertEquals(1.0f, UryuGestures.rowTime(UryuAnims.DRAW, 40.0, 1.0), 1e-6);
		assertEquals(0.25f, UryuGestures.rowTime(UryuAnims.DRAW, 5.0, 1.0), 1e-6);
		assertEquals(0.4f, UryuGestures.rowTime(UryuAnims.RELEASE, 8.0, 0.5), 1e-6);
		assertEquals(1.0f, UryuGestures.rowTime(UryuAnims.RELEASE, 20.0, 0.5), 1e-6);
	}

	@Test
	void theBreathIsASlowSineAtTheStoryboardAmplitude() {
		double quarter = 0.25 / UryuPoseMath.BREATH_HZ;
		float[] up = UryuPoseMath.breathRow(quarter, 1.0);
		assertEquals(UryuPoseMath.BREATH_RISE, up[1], 1e-6);
		assertEquals(UryuPoseMath.BREATH_ROLL, up[5], 1e-6);
		assertEquals(0f, up[0]);
		assertEquals(0f, up[3]);
		float[] zero = UryuPoseMath.breathRow(0.0, 1.0);
		assertEquals(0f, zero[1], 1e-9);
		float[] half = UryuPoseMath.breathRow(quarter, 0.5);
		assertEquals(0.5f * up[1], half[1], 1e-7);
		float[] down = UryuPoseMath.breathRow(3 * quarter, 1.0);
		assertEquals(-up[1], down[1], 1e-6);
	}

	@Test
	void reduceMotionHalvesTheBreath() {
		assertEquals(1.0, UryuPoseMath.breathScale(false, 1.0));
		assertEquals(0.5, UryuPoseMath.breathScale(true, 1.0));
		assertEquals(0.0, UryuPoseMath.breathScale(true, 0.0));
		assertEquals(1.5, UryuPoseMath.breathScale(false, 1.5));
	}
}
