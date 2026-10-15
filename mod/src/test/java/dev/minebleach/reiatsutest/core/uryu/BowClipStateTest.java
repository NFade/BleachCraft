package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The playback state of the bow draw clip: follows the draw, freezes at full draw, fast forwards on an early release, ends by itself. */
class BowClipStateTest {
	@Test
	void idleStaysInactive() {
		BowClipState s = new BowClipState();
		for (int i = 0; i < 5; i++) {
			BowClipState.Step st = s.step(false);
			assertFalse(st.active());
			assertEquals(0, st.advance());
		}
		assertEquals(BowClipState.Phase.IDLE, s.phase());
	}

	@Test
	void drawStartsOnceThenAdvancesOneTickPerGameTick() {
		BowClipState s = new BowClipState();
		BowClipState.Step first = s.step(true);
		assertTrue(first.restart() && first.active());
		assertEquals(0, first.advance());
		for (int i = 1; i <= BowClipState.FULL_DRAW_TICK; i++) {
			BowClipState.Step st = s.step(true);
			assertFalse(st.restart());
			assertEquals(1, st.advance());
			assertEquals(i, s.tick());
		}
		assertTrue(s.holding(), "frozen on the hold pose at full draw");
	}

	@Test
	void holdKeepsTheClipFrozenWhileDrawing() {
		BowClipState s = new BowClipState();
		for (int i = 0; i < BowClipState.FULL_DRAW_TICK + 1; i++) {
			s.step(true);
		}
		for (int i = 0; i < 100; i++) {
			BowClipState.Step st = s.step(true);
			assertEquals(0, st.advance());
			assertTrue(st.active());
			assertEquals(BowClipState.FULL_DRAW_TICK, s.tick());
		}
	}

	@Test
	void releaseFromFullDrawFastForwardsThenPlaysAtNormalSpeedToTheEnd() {
		BowClipState s = new BowClipState();
		for (int i = 0; i < BowClipState.FULL_DRAW_TICK + 1; i++) {
			s.step(true);
		}
		int games = 0;
		int advanced = 0;
		BowClipState.Step st;
		do {
			st = s.step(false);
			advanced += st.advance();
			games++;
			assertTrue(games < 100, "the clip must end");
		} while (st.active());
		assertEquals(BowClipState.END_TICK - BowClipState.FULL_DRAW_TICK, advanced);
		assertEquals(BowClipState.Phase.IDLE, s.phase());
		assertTrue(games <= BowClipState.END_TICK - BowClipState.FULL_DRAW_TICK + 1);
	}

	@Test
	void earlyReleaseSkipsAheadAtTwoTicksPerGameTick() {
		BowClipState s = new BowClipState();
		for (int i = 0; i < 6; i++) {
			s.step(true); // tick 5
		}
		assertEquals(5, s.tick());
		BowClipState.Step st = s.step(false);
		assertEquals(2, st.advance());
		assertEquals(7, s.tick());
		while (s.tick() < BowClipState.RELEASE_START_TICK) {
			st = s.step(false);
			assertTrue(st.advance() <= 2 && st.active());
		}
		assertEquals(BowClipState.RELEASE_START_TICK, s.tick());
		assertEquals(1, s.step(false).advance());
	}

	@Test
	void drawingAgainDuringReleaseRestartsTheClip() {
		BowClipState s = new BowClipState();
		for (int i = 0; i < 10; i++) {
			s.step(true);
		}
		s.step(false);
		BowClipState.Step st = s.step(true);
		assertTrue(st.restart());
		assertEquals(0, s.tick());
		assertEquals(BowClipState.Phase.DRAW, s.phase());
	}

	@Test
	void stanceWeightIsOneStandingZeroMovingAndMonotone() {
		assertEquals(1.0, BowClipState.stanceWeight(0.0), 1e-9);
		assertEquals(0.0, BowClipState.stanceWeight(BowClipState.MOVING_LIMB_SPEED), 1e-9);
		assertEquals(0.0, BowClipState.stanceWeight(1.0), 1e-9);
		assertEquals(0.5, BowClipState.stanceWeight(BowClipState.MOVING_LIMB_SPEED / 2), 1e-9);
		double prev = 1.0;
		for (double v = 0.0; v <= 0.4; v += 0.02) {
			double w = BowClipState.stanceWeight(v);
			assertTrue(w <= prev + 1e-12 && w >= 0.0);
			prev = w;
		}
	}

	@Test
	void tickNeverExceedsTheEnd() {
		BowClipState s = new BowClipState();
		for (int i = 0; i < 30; i++) {
			s.step(true);
		}
		for (int i = 0; i < 80; i++) {
			s.step(false);
			assertTrue(s.tick() <= BowClipState.END_TICK);
		}
	}
}
