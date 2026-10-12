package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.world.FrostJournal.Entry;
import dev.minebleach.reiatsutest.core.world.FrostJournal.Snapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrostJournalTest {
	private static final String OW = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	@Test
	void packingRoundTripsNegativeAndExtremeCoordinates() {
		int[][] cases = {{0, 0, 0}, {-1, -1, -1}, {12345, 70, -98765}, {-30000000, -64, 29999999}, {29999999, 319, -30000000}, {5, -2048, 5}, {5, 2047, 5}};
		for (int[] c : cases) {
			long p = FrostJournal.pack(c[0], c[1], c[2]);
			assertEquals(c[0], FrostJournal.unpackX(p));
			assertEquals(c[1], FrostJournal.unpackY(p));
			assertEquals(c[2], FrostJournal.unpackZ(p));
		}
	}

	@Test
	void aCellIsJournaledOnceSoTheOriginalStateIsNeverLost() {
		FrostJournal j = new FrostJournal();
		assertTrue(j.add(OW, 1, 64, 1, "minecraft:grass_block", 1));
		assertFalse(j.add(OW, 1, 64, 1, "reiatsu_test:frost_crust", 2), "a second field must not journal the frost block as the original");
		assertEquals(1, j.size());
		assertEquals("minecraft:grass_block", j.get(OW, 1, 64, 1).previous());
		assertEquals(1, j.get(OW, 1, 64, 1).field());
		assertTrue(j.add(NETHER, 1, 64, 1, "minecraft:netherrack", 1), "same coordinates in another dimension are another cell");
		assertEquals(2, j.size());
	}

	@Test
	void batchesGoOutsideInAndLeaveNothingBehind() {
		FrostJournal j = new FrostJournal();
		j.setCenter(7, 0.5, 0.5);
		for (int x = 0; x < 20; x++) {
			j.add(OW, x, 64, 0, "minecraft:dirt", 7);
		}
		assertEquals(20, j.size(7));
		List<Entry> first = j.takeBatch(7, 5, true);
		assertEquals(5, first.size());
		for (Entry e : first) {
			assertTrue(e.x() >= 15, "the outermost cells thaw first: " + e.x());
		}
		assertEquals(15, j.size());
		List<Entry> inner = j.takeBatch(7, 3, false);
		for (Entry e : inner) {
			assertTrue(e.x() <= 2, "innermost first when asked: " + e.x());
		}
		int guard = 0;
		while (j.size() > 0 && guard++ < 100) {
			j.takeBatch(7, 4, true);
		}
		assertEquals(0, j.size());
		assertEquals(0, j.size(7));
		assertTrue(j.fieldIds().isEmpty());
		assertFalse(j.contains(OW, 3, 64, 0));
		assertTrue(j.add(OW, 3, 64, 0, "minecraft:stone", 9), "a restored cell can be frozen again");
	}

	@Test
	void theThawRingTakesOnlyCellsBeyondItsRadiusAndNeverSkipsAnyOnTheWayIn() {
		FrostJournal j = new FrostJournal();
		j.setCenter(4, 0.5, 0.5);
		for (int x = 0; x < 30; x++) {
			j.add(OW, x, 64, 0, "minecraft:dirt", 4);
		}
		// ring radius 20 around x = 0.5: only cells with centre at least 20 blocks away
		List<Entry> out = j.takeBatch(4, 100, true, 20.0);
		assertEquals(10, out.size());
		for (Entry e : out) {
			assertTrue(e.x() >= 20);
		}
		assertTrue(j.takeBatch(4, 100, true, 20.0).isEmpty(), "nothing more beyond the same radius");
		assertEquals(20, j.size());
		// the ring moves inward: every cell comes out exactly once, in order
		int total = out.size();
		for (double r = 19.0; r > -1; r -= 1.0) {
			total += j.takeBatch(4, 3, true, Math.max(0.0, r)).size();
		}
		int guard = 0;
		while (j.size() > 0 && guard++ < 100) {
			total += j.takeBatch(4, 5, true, 0.0).size();
		}
		assertEquals(30, total);
		assertEquals(0, j.size());
	}

	@Test
	void batchOfAnUnknownFieldIsEmpty() {
		assertTrue(new FrostJournal().takeBatch(5, 10, true).isEmpty());
		FrostJournal j = new FrostJournal();
		j.add(OW, 0, 0, 0, "minecraft:dirt", 1);
		assertTrue(j.takeBatch(1, 0, true).isEmpty());
	}

	@Test
	void snapshotRoundTripKeepsEveryCellAndItsOriginalState() {
		FrostJournal j = new FrostJournal();
		String[] states = {"minecraft:grass_block", "minecraft:stone", "minecraft:oak_leaves[distance=2,persistent=false]", "minecraft:water[level=0]"};
		for (int i = 0; i < 400; i++) {
			j.add(i % 3 == 0 ? NETHER : OW, i - 200, -64 + i % 200, 1000 - i * 7, states[i % states.length], 1 + i % 4);
		}
		Snapshot s = j.snapshot();
		assertEquals(400, s.size());
		assertEquals(states.length, s.palette().length, "the palette holds each token once");
		assertEquals(2, s.dimensions().length);
		FrostJournal back = FrostJournal.fromSnapshot(s);
		assertEquals(400, back.size());
		for (int i = 0; i < 400; i++) {
			Entry e = back.get(i % 3 == 0 ? NETHER : OW, i - 200, -64 + i % 200, 1000 - i * 7);
			assertEquals(states[i % states.length], e.previous(), "cell " + i);
		}
		// after a restart nothing owns the cells: everything can be taken at once
		assertEquals(400, back.takeAll().size());
		assertEquals(0, back.size());
	}

	@Test
	void aDamagedSnapshotNeverThrows() {
		Snapshot bad = new Snapshot(new String[] {OW}, new String[] {"minecraft:dirt"},
				new long[] {FrostJournal.pack(1, 2, 3), FrostJournal.pack(4, 5, 6), FrostJournal.pack(7, 8, 9)},
				new int[] {0, 5, 0}, new int[] {0, 0, 9});
		FrostJournal j = FrostJournal.fromSnapshot(bad);
		assertEquals(1, j.size(), "entries with a bad index are dropped");
		assertTrue(j.contains(OW, 1, 2, 3));
		Snapshot shortArrays = new Snapshot(new String[] {OW}, new String[] {"minecraft:dirt"}, new long[] {1, 2, 3}, new int[] {0}, new int[] {0});
		assertEquals(1, FrostJournal.fromSnapshot(shortArrays).size());
	}

	@Test
	void putBackKeepsWaitingEntries() {
		FrostJournal j = new FrostJournal();
		j.add(OW, 1, 1, 1, "minecraft:dirt", 3);
		List<Entry> out = j.takeBatch(3, 10, true);
		assertEquals(0, j.size());
		j.putBack(out);
		assertEquals(1, j.size());
		assertEquals(1, j.size(3));
	}
}
