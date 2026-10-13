package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rate limit of the block feedback events (r8 fx2 item 1). */
class FeedbackLimiterTest {
	@Test
	void normalEventsAreCappedPerWindow() {
		FeedbackLimiter l = FeedbackLimiter.standard();
		UUID p = UUID.randomUUID();
		for (int i = 0; i < 4; i++) {
			assertTrue(l.tryAcquire(p, 100 + i, false), "event " + i);
		}
		assertFalse(l.tryAcquire(p, 105, false), "the fifth inside one second is refused");
		assertTrue(l.tryAcquire(p, 120, false), "the first one left the window after 20 ticks");
	}

	@Test
	void aParryStillGetsThroughAFloodOfWeakHits() {
		FeedbackLimiter l = FeedbackLimiter.standard();
		UUID p = UUID.randomUUID();
		for (int i = 0; i < 4; i++) {
			l.tryAcquire(p, 100, false);
		}
		assertFalse(l.tryAcquire(p, 101, false));
		assertTrue(l.tryAcquire(p, 101, true), "priority passes while under the hard cap of 6");
		assertTrue(l.tryAcquire(p, 101, true));
		assertFalse(l.tryAcquire(p, 101, true), "but even a parry cannot pass 6 per second");
	}

	@Test
	void playersAreCountedApartAndRefusedEventsAreNotCounted() {
		FeedbackLimiter l = new FeedbackLimiter(20, 2, 3);
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		assertTrue(l.tryAcquire(a, 0, false));
		assertTrue(l.tryAcquire(a, 1, false));
		assertFalse(l.tryAcquire(a, 2, false));
		assertFalse(l.tryAcquire(a, 3, false));
		assertTrue(l.tryAcquire(b, 3, false), "another defender has his own budget");
		// the refused ones did not extend the window: 20 ticks after the first accepted event a new one fits
		assertTrue(l.tryAcquire(a, 20, false));
	}

	@Test
	void purgeDropsOldKeys() {
		FeedbackLimiter l = FeedbackLimiter.standard();
		for (int i = 0; i < 10; i++) {
			l.tryAcquire(UUID.randomUUID(), 5, false);
		}
		assertEquals(10, l.keys());
		l.purge(10);
		assertEquals(10, l.keys(), "still inside the window");
		l.purge(100);
		assertEquals(0, l.keys());
	}

	@Test
	void caps_are_sane_for_nonsense_arguments() {
		FeedbackLimiter l = new FeedbackLimiter(0, -1, -5);
		assertFalse(l.tryAcquire("k", 0, false), "a zero cap refuses everything");
		assertFalse(l.tryAcquire("k", 0, true));
	}
}
