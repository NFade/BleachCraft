package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The clock of the draw hand of the first person bow. */
class BowHandMotionTest {
	private static final float IN = 0.10f;
	private static final float OUT = 0.20f;
	private static final float SNAP = 0.09f;
	private static final float DT = 0.01f;

	private static void run(BowHandMotion h, float seconds, boolean using, double pull) {
		for (float t = 0f; t < seconds - 1e-6f; t += DT) {
			h.step(DT, using, pull, IN, OUT, SNAP);
		}
	}

	@Test
	void restsBeforeTheDraw() {
		BowHandMotion h = new BowHandMotion();
		run(h, 1f, false, 0.0);
		assertEquals(0f, h.blend());
		assertEquals(0.0, h.pull());
		assertFalse(h.snapping());
	}

	@Test
	void theHandComesToTheStringAndFollowsTheDraw() {
		BowHandMotion h = new BowHandMotion();
		run(h, 0.05f, true, 0.3);
		assertTrue(h.blend() > 0.3f && h.blend() < 0.7f, "half way after half the time");
		run(h, 0.2f, true, 0.6);
		assertEquals(1f, h.blend(), 1e-6f);
		assertEquals(0.6, h.pull(), 1e-9);
		run(h, 1f, true, 1.0);
		assertEquals(1.0, h.pull(), 1e-9, "holds at full draw");
		assertEquals(1f, h.blend(), 1e-6f);
	}

	@Test
	void theReleaseSnapsForwardThenTheHandRelaxes() {
		BowHandMotion h = new BowHandMotion();
		run(h, 0.5f, true, 1.0);
		h.step(DT, false, 0.0, IN, OUT, SNAP);
		assertTrue(h.snapping());
		assertTrue(h.pull() < 1.0 && h.pull() > 0.0, "already moving, not yet at rest");
		assertEquals(1f, h.blend(), 1e-6f, "the hand stays on the string during the snap");
		double last = h.pull();
		for (int i = 0; i < 20 && h.snapping(); i++) {
			h.step(DT, false, 0.0, IN, OUT, SNAP);
			assertTrue(h.pull() <= last + 1e-12, "the snap only moves forward");
			last = h.pull();
		}
		assertFalse(h.snapping());
		assertEquals(0.0, h.pull(), 1e-12);
		run(h, 0.05f, false, 0.0);
		assertTrue(h.blend() < 1f && h.blend() > 0f, "relaxing");
		run(h, 0.5f, false, 0.0);
		assertEquals(0f, h.blend(), 1e-6f);
	}

	@Test
	void snapIsAFastEaseOut() {
		BowHandMotion h = new BowHandMotion();
		run(h, 0.5f, true, 1.0);
		float t = 0f;
		while (t < SNAP / 3f) {
			h.step(DT, false, 0.0, IN, OUT, SNAP);
			t += DT;
		}
		assertTrue(h.pull() < 0.6, "more than 40 percent of the way in the first third of the time");
	}

	@Test
	void aNewDrawDuringTheSnapTakesOver() {
		BowHandMotion h = new BowHandMotion();
		run(h, 0.5f, true, 1.0);
		h.step(DT, false, 0.0, IN, OUT, SNAP);
		h.step(DT, true, 0.2, IN, OUT, SNAP);
		assertFalse(h.snapping());
		assertEquals(0.2, h.pull(), 1e-12);
	}

	@Test
	void resetForgetsEverything() {
		BowHandMotion h = new BowHandMotion();
		run(h, 0.5f, true, 1.0);
		h.reset();
		assertEquals(0f, h.blend());
		assertEquals(0.0, h.pull());
		assertFalse(h.snapping());
	}
}
