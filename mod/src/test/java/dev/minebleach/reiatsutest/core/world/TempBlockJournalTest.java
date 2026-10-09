package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.world.TempBlockJournal.Entry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TempBlockJournalTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private static Entry e(UUID o, int x, String prev, String placed, long due) {
		return new Entry(o, "minecraft:overworld", x, 64, 0, prev, placed, due);
	}

	@Test
	void repeatedPlacementKeepsTheOriginalPreviousState() {
		TempBlockJournal j = new TempBlockJournal();
		j.add(e(A, 1, "air", "snow:1", 100));
		j.add(e(A, 1, "snow:1", "ice", 200)); // second placement on the same cell
		assertEquals(1, j.size());
		Entry only = j.snapshot().get(0);
		assertEquals("air", only.previous(), "rollback must restore the world as it was before the first placement");
		assertEquals("ice", only.placed());
		assertEquals(200, only.restoreAtTick());
	}

	@Test
	void dueEntriesAreTakenInOrderOfAge() {
		TempBlockJournal j = new TempBlockJournal();
		j.add(e(A, 1, "air", "ice", 100));
		j.add(e(A, 2, "air", "ice", 200));
		assertTrue(j.takeDue(99).isEmpty());
		List<Entry> due = j.takeDue(100);
		assertEquals(1, due.size());
		assertEquals(1, due.get(0).x());
		assertEquals(1, j.size());
		assertEquals(1, j.takeDue(1000).size());
		assertEquals(0, j.size());
	}

	@Test
	void perOwnerCapAndRollback() {
		TempBlockJournal j = new TempBlockJournal();
		for (int i = 0; i < 128; i++) {
			assertTrue(j.canAdd(A, "minecraft:overworld", i, 64, 0, 128));
			j.add(e(A, i, "air", "ice", 500));
		}
		assertFalse(j.canAdd(A, "minecraft:overworld", 500, 64, 0, 128));
		assertTrue(j.canAdd(A, "minecraft:overworld", 5, 64, 0, 128), "re-using a journaled cell is free");
		assertTrue(j.canAdd(B, "minecraft:overworld", 500, 64, 0, 128));
		j.add(e(B, 900, "air", "ice", 500));
		assertEquals(128, j.count(A));
		assertEquals(128, j.takeAll(A).size());
		assertEquals(1, j.size());
		assertEquals(1, j.takeAll().size());
		assertEquals(0, j.size());
	}

	@Test
	void putBackKeepsEntriesForRetry() {
		TempBlockJournal j = new TempBlockJournal();
		j.add(e(A, 1, "water", "ice", 10));
		List<Entry> due = j.takeDue(10);
		assertEquals(0, j.size());
		j.putBack(due);
		assertTrue(j.contains("minecraft:overworld", 1, 64, 0));
	}
}
