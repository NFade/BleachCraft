package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** R11 T2: the anti-spam of the melee hit reiatsu. */
class MeleeRewardLimiterTest {
	@Test
	void oneRewardPerTargetPerGap() {
		MeleeRewardLimiter l = new MeleeRewardLimiter(5, 150);
		assertEquals(50, l.grant(7, 100, 50));
		assertEquals(0, l.grant(7, 101, 50), "the same target inside the gap");
		assertEquals(0, l.grant(7, 104, 50));
		assertEquals(50, l.grant(7, 105, 50), "the gap is over");
	}

	@Test
	void anotherTargetIsNotBlockedByTheGap() {
		MeleeRewardLimiter l = new MeleeRewardLimiter(5, 150);
		assertEquals(50, l.grant(1, 100, 50));
		assertEquals(50, l.grant(2, 100, 50));
		assertEquals(50, l.grant(3, 100, 50));
		assertEquals(0, l.grant(4, 100, 50), "15 points a second is spent");
	}

	@Test
	void theWindowSlidesAndASmallRestIsPaid() {
		MeleeRewardLimiter l = new MeleeRewardLimiter(0, 120);
		assertEquals(50, l.grant(1, 100, 50));
		assertEquals(50, l.grant(1, 105, 50));
		assertEquals(20, l.grant(1, 110, 50), "only what is left of the window");
		assertEquals(0, l.grant(1, 115, 50));
		assertEquals(50, l.grant(1, 120, 50), "the first reward left the window");
	}

	@Test
	void fastAttacksOnOneTargetCannotPrintMana() {
		MeleeRewardLimiter l = new MeleeRewardLimiter(5, 150);
		int total = 0;
		for (int tick = 0; tick < 200; tick += 2) { // a swing every 2 ticks for 10 s
			total += l.grant(9, tick, 50);
		}
		assertTrue(total > 0 && total <= 15 * 10 * 10, "at most 15 points a second for 10 s, got " + total + " tenths");
	}

	@Test
	void zeroOrNegativeWantedPaysNothing() {
		MeleeRewardLimiter l = new MeleeRewardLimiter(5, 150);
		assertEquals(0, l.grant(1, 10, 0));
		assertEquals(0, l.grant(1, 10, -5));
	}

	@Test
	void aClockThatGoesBackDoesNotLockThePlayerOut() {
		MeleeRewardLimiter l = new MeleeRewardLimiter(5, 100);
		assertEquals(50, l.grant(1, 1000, 50));
		assertEquals(50, l.grant(2, 10, 50), "world reload: the tick counter restarted");
	}
}
