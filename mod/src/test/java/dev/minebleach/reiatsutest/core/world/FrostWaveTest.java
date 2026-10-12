package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FrostWaveTest {
	@Test
	void columnsFormAFullDiscSortedByDistanceWithoutDuplicates() {
		FrostWave w = new FrostWave(10, 12.0, 100);
		Set<Long> seen = new HashSet<>();
		double prev = -1;
		for (int i = 0; i < w.totalColumns(); i++) {
			assertTrue(seen.add(((long) w.dx(i) << 32) ^ (w.dz(i) & 0xFFFFFFFFL)), "unique column " + i);
			assertTrue(w.distance(i) <= 10.0 + 1e-9);
			assertTrue(w.distance(i) >= prev, "sorted by distance");
			prev = w.distance(i);
		}
		int expected = 0;
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				if (x * x + z * z <= 100) {
					expected++;
				}
			}
		}
		assertEquals(expected, w.totalColumns());
		assertEquals(0, w.dx(0));
		assertEquals(0, w.dz(0), "the first column is the centre");
	}

	@Test
	void theRingRunsOutwardAtTheConfiguredSpeedAndStopsAtTheRim() {
		FrostWave w = new FrostWave(30, 12.0, 10000);
		assertEquals(0.0, w.radiusAt(0), 1e-9);
		assertEquals(6.0, w.radiusAt(10), 1e-9, "12 blocks per second");
		assertEquals(12.0, w.radiusAt(20), 1e-9);
		assertEquals(30.0, w.radiusAt(1000), 1e-9);
		assertEquals(50, w.ticksToRim(), "30 blocks at 12 blocks per second is 2.5 s");
		int atHalf = w.reached(25);
		assertTrue(atHalf > 0 && atHalf < w.totalColumns());
		for (int i = 0; i < atHalf; i++) {
			assertTrue(w.distance(i) <= w.radiusAt(25) + 1e-9);
		}
		assertTrue(w.distance(atHalf) > w.radiusAt(25));
		assertEquals(w.totalColumns(), w.reached(50));
		assertTrue(w.reached(1) >= 1, "the centre column is reached on the first tick");
	}

	@Test
	void theColumnBudgetBoundsTheWorkOfOneTickAndNeverSkipsAColumn() {
		FrostWave w = new FrostWave(30, 12.0, 220);
		int cursor = 0;
		int ticks = 0;
		while (!w.done(cursor) && ticks < 1000) {
			int next = w.advance(cursor, ticks);
			assertTrue(next - cursor <= 220, "at most the budget per tick");
			assertTrue(next >= cursor);
			cursor = next;
			ticks++;
		}
		assertTrue(w.done(cursor));
		// a slow budget only delays the ring, it does not lose columns
		FrostWave slow = new FrostWave(30, 12.0, 20);
		int c = 0;
		int t = 0;
		while (!slow.done(c) && t < 10000) {
			c = slow.advance(c, t++);
		}
		assertTrue(slow.done(c));
		assertTrue(t > slow.ticksToRim());
		assertFalse(w.done(0));
	}

	@Test
	void deterministic() {
		FrostWave a = new FrostWave(16, 12.0, 50);
		FrostWave b = new FrostWave(16, 12.0, 50);
		for (int i = 0; i < a.totalColumns(); i++) {
			assertEquals(a.dx(i), b.dx(i));
			assertEquals(a.dz(i), b.dz(i));
		}
	}

	@Test
	void largestRadiusStillFitsTheSortKey() {
		FrostWave w = new FrostWave(64, 12.0, 100);
		assertTrue(w.totalColumns() > 12000);
		assertEquals(0, w.dx(0));
		assertEquals(64.0, Math.floor(w.distance(w.totalColumns() - 1)), 1.0);
	}
}
