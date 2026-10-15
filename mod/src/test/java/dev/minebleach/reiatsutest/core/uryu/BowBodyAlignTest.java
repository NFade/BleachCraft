package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R13: the body of an archer faces the look while the bow is drawn and for a short time after. */
class BowBodyAlignTest {
	@Test
	void drawingAlwaysAligns() {
		assertTrue(BowBodyAlign.active(true, 100, Long.MIN_VALUE));
		assertTrue(BowBodyAlign.active(true, 100, 99));
	}

	@Test
	void neverDrawnNeverAligns() {
		assertFalse(BowBodyAlign.active(false, 100, Long.MIN_VALUE));
	}

	@Test
	void holdsForTheFollowThroughOnly() {
		assertTrue(BowBodyAlign.active(false, 100, 100));
		assertTrue(BowBodyAlign.active(false, 100 + BowBodyAlign.HOLD_AFTER_TICKS, 100));
		assertFalse(BowBodyAlign.active(false, 101 + BowBodyAlign.HOLD_AFTER_TICKS, 100));
		assertFalse(BowBodyAlign.active(false, 99, 100), "a stamp from the future (new world) does not count");
	}
}
