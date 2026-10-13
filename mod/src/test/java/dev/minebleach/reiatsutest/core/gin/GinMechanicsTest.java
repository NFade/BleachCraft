package dev.minebleach.reiatsutest.core.gin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cast root, cover (Hakufuku) and the geometry helpers of the Gin abilities. */
class GinMechanicsTest {
	private static final UUID P = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

	// ---------------------------------------------------------------- RootBook

	@Test
	void rootEndsOnItsTick() {
		RootBook b = new RootBook();
		assertEquals(108, b.root(P, 100, 8));
		assertTrue(b.active(P, 107));
		assertFalse(b.active(P, 108));
		assertTrue(b.expired(107).isEmpty());
		assertEquals(List.of(P), b.expired(108));
		assertEquals(0, b.size());
	}

	@Test
	void aLaterShorterRootNeverShortensALongerOne() {
		RootBook b = new RootBook();
		b.root(P, 100, 46);
		assertEquals(146, b.root(P, 110, 8), "Ikorose during Hyapponzashi's recovery keeps the longer root");
		assertEquals(160, b.root(P, 120, 40));
	}

	@Test
	void releaseEndsTheRootAtOnce() {
		RootBook b = new RootBook();
		b.root(P, 0, 30);
		assertTrue(b.release(P));
		assertFalse(b.active(P, 1));
		assertFalse(b.release(P));
		assertTrue(b.expired(1000).isEmpty());
	}

	// ---------------------------------------------------------------- GinCover

	@Test
	void hakufukuHidesThreeSecondsAndDamageBreaksIt() {
		GinCover c = new GinCover();
		c.start(100, 60, 80);
		assertTrue(c.hidden(100));
		assertTrue(c.hidden(159));
		assertFalse(c.hidden(160));
		c.start(200, 60, 80);
		assertTrue(c.damaged(230), "was hidden");
		assertFalse(c.hidden(231));
		assertFalse(c.damaged(232), "already broken");
	}

	@Test
	void theNextThrustWithinFourSecondsHasNoFlashOnce() {
		GinCover c = new GinCover();
		assertFalse(c.takeNoFlash(0), "no Hakufuku, normal flash");
		c.start(100, 60, 80);
		assertTrue(c.takeNoFlash(179));
		assertFalse(c.takeNoFlash(179), "used up by that thrust");
		c.start(300, 60, 80);
		assertFalse(c.takeNoFlash(380), "after 4 s the window is gone");
	}

	@Test
	void breakingTheCoverKeepsTheFlashWindow() {
		GinCover c = new GinCover();
		c.start(0, 60, 80);
		c.damaged(10);
		assertTrue(c.flashWindow(20));
		assertTrue(c.takeNoFlash(20));
	}

	// ---------------------------------------------------------------- GinGeometry

	@Test
	void yawOffsetIsSignedAndWrapsAround() {
		assertEquals(0.0, GinGeometry.yawOffsetDeg(1, 0, 5, 0), 1e-9);
		assertEquals(90.0, GinGeometry.yawOffsetDeg(1, 0, 0, 3), 1e-9);
		assertEquals(-90.0, GinGeometry.yawOffsetDeg(1, 0, 0, -3), 1e-9);
		assertEquals(180.0, Math.abs(GinGeometry.yawOffsetDeg(1, 0, -2, 0)), 1e-9);
	}

	@Test
	void eightSlicesCoverTheArcAndEachAngleBelongsToExactlyOne() {
		int n = 8;
		double arc = 120;
		assertEquals(-60.0, GinGeometry.sliceFrom(0, n, arc), 1e-9);
		assertEquals(60.0, GinGeometry.sliceTo(n - 1, n, arc), 1e-9);
		assertEquals(15.0, GinGeometry.sliceTo(0, n, arc) - GinGeometry.sliceFrom(0, n, arc), 1e-9);
		for (double a = -60; a <= 60; a += 0.5) {
			int hits = 0;
			for (int i = 0; i < n; i++) {
				if (GinGeometry.inSlice(a, GinGeometry.sliceFrom(i, n, arc), GinGeometry.sliceTo(i, n, arc), i == n - 1)) {
					hits++;
				}
			}
			assertEquals(1, hits, "angle " + a);
		}
		assertFalse(GinGeometry.inSlice(60.5, 45, 60, true));
		assertFalse(GinGeometry.inSlice(-61, -60, -45, false));
	}

	@Test
	void sliceHitCountsTheBodyWidth() {
		double slack = GinGeometry.slackDeg(4.0, 0.3);
		assertTrue(slack > 4 && slack < 5, "0.3 at 4 blocks is about 4.3 degrees");
		assertTrue(GinGeometry.sliceHit(16.0, 0, 15, slack));
		assertFalse(GinGeometry.sliceHit(20.0, 0, 15, slack));
		assertTrue(GinGeometry.slackDeg(0.0, 0.3) < 40, "a target on top of Gin does not get an infinite slack");
	}

	@Test
	void recastRangeIsStrictlyBeyond64() {
		assertFalse(GinGeometry.outOfRange(64.0, 64.0));
		assertTrue(GinGeometry.outOfRange(64.001, 64.0));
	}

	@Test
	void pullStopsShortAndNeverOvershoots() {
		double x = 0;
		int steps = 0;
		GinGeometry.PullStep s;
		while (!(s = GinGeometry.pull(x, 0, 0, 16, 0, 0, 1.5, 0.9)).done()) {
			assertTrue(s.vx() <= 0.9 + 1e-9);
			x += s.vx();
			assertTrue(x <= 16 - 1.5 + 1e-9, "never closer than 1.5 blocks to the anchor");
			steps++;
			assertTrue(steps < 100);
		}
		assertEquals(14.5, x, 0.06);
		assertTrue(steps <= 17, "16 blocks at 0.9 per tick: about 17 ticks");
	}

	@Test
	void pullWorksInThreeDimensionsAndNotAtAll_whenAlreadyClose() {
		GinGeometry.PullStep s = GinGeometry.pull(0, 0, 0, 3, 4, 0, 1.5, 1.0);
		assertEquals(0.6, s.vx(), 1e-9);
		assertEquals(0.8, s.vy(), 1e-9);
		assertFalse(s.done());
		assertTrue(GinGeometry.pull(0, 0, 0, 1.0, 0, 0, 1.5, 1.0).done(), "inside the stop distance: nothing to do");
		assertTrue(GinGeometry.pull(0, 0, 0, 0, 0, 0, 1.5, 1.0).done());
	}
}
