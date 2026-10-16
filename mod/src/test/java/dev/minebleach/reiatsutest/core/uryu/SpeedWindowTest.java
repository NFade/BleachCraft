package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R14: Focus must survive a sneak walk whose movement packets arrive late or doubled. */
class SpeedWindowTest {
	private static final double SNEAK_WALK = 0.0655; // blocks per tick at the vanilla sneak speed

	@Test
	void meanOfTheLastSamples() {
		SpeedWindow w = new SpeedWindow();
		assertEquals(0.4, w.push(0.4), 1e-9);
		assertEquals(0.2, w.push(0.0), 1e-9);
		for (int i = 0; i < SpeedWindow.SIZE - 2; i++) {
			w.push(0.0);
		}
		assertEquals(0.0, w.push(0.0), 1e-9, "the first sample fell out of the window");
	}

	@Test
	void aClearedWindowForgets() {
		SpeedWindow w = new SpeedWindow();
		w.push(1.0);
		w.clear();
		assertEquals(0.05, w.push(0.05), 1e-9);
	}

	/** The old code fed the raw speed of one tick: a doubled step read 0.131 and reset the stacks on the next tick of the walk. */
	@Test
	void aSneakWalkWithJitterKeepsTheFocus() {
		double[] packets = {1, 1, 2, 0, 1, 1, 1, 2, 0, 1, 1, 1, 1, 1, 2, 0, 1, 1}; // steps seen per server tick (a late packet, then two at once)
		Focus raw = new Focus();
		Focus smooth = new Focus();
		SpeedWindow w = new SpeedWindow();
		boolean rawLost = false;
		for (double p : packets) {
			double v = p * SNEAK_WALK;
			raw.tick(true, false, true, v, false);
			smooth.tick(true, false, true, w.push(v), false);
			rawLost |= v >= UryuBalance.FOCUS_SNEAK_SPEED;
		}
		assertTrue(rawLost, "the jitter really is above the limit for one tick");
		assertTrue(smooth.stacks() >= 1, "the smoothed walk builds Focus: " + smooth.stacks());
		assertEquals(0, raw.stacks(), "the old raw speed never got through 14 quiet ticks");
	}

	@Test
	void aRealRunStillResets() {
		Focus f = new Focus();
		SpeedWindow w = new SpeedWindow();
		for (int i = 0; i < 30; i++) {
			f.tick(true, false, true, w.push(SNEAK_WALK), false);
		}
		assertTrue(f.stacks() >= 2);
		for (int i = 0; i < 4; i++) {
			f.tick(true, false, false, w.push(0.216), false); // a walk at normal speed
		}
		assertEquals(0, f.stacks());
	}
}
