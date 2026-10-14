package dev.minebleach.reiatsutest.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	// ------------------------------------------------------------------ R11 T5: the ice wall uses the same journal

	@Test
	void aSingleCellCanBeTakenOutAndLookedUp() {
		TempBlockJournal j = new TempBlockJournal();
		j.add(e(A, 1, "air", "ice_wall", 100));
		j.add(e(A, 2, "air", "ice_wall", 100));
		assertEquals(2, j.get("minecraft:overworld", 2, 64, 0).x());
		assertNull(j.get("minecraft:overworld", 3, 64, 0));
		assertNull(j.get("minecraft:nether", 2, 64, 0), "other dimension, other cell");
		Entry taken = j.take("minecraft:overworld", 1, 64, 0);
		assertEquals("air", taken.previous());
		assertEquals(1, j.size());
		assertNull(j.take("minecraft:overworld", 1, 64, 0), "taken once");
	}

	@Test
	void serializedPlantStatesSurviveAsOpaqueTokensEvenWhenAWallIsPlacedTwice() {
		TempBlockJournal j = new TempBlockJournal();
		String grass = "state:minecraft:short_grass";
		String snow = "state:minecraft:snow[layers=3]";
		j.add(e(A, 1, grass, "ice_wall", 200));
		j.add(e(A, 2, snow, "ice_wall", 200));
		j.add(e(A, 1, "air", "ice_wall", 300)); // the same cell again (the wall was replaced at once): the first previous state is kept
		assertEquals(grass, j.get("minecraft:overworld", 1, 64, 0).previous());
		assertEquals(snow, j.get("minecraft:overworld", 2, 64, 0).previous());
		assertEquals(300, j.get("minecraft:overworld", 1, 64, 0).restoreAtTick());
	}

	@Test
	void aJournalRebuiltAfterARestartRollsEverythingBack() {
		// the server persists snapshot() (TempBlockState) and on the next start takes everything out and restores it
		TempBlockJournal before = new TempBlockJournal();
		for (int x = 0; x < 90; x++) {
			before.add(e(A, x, x % 3 == 0 ? "state:minecraft:short_grass" : "air", "ice_wall", 4000));
		}
		before.add(e(B, 500, "water", "ice", 100));
		TempBlockJournal after = new TempBlockJournal();
		for (Entry entry : before.snapshot()) {
			after.add(entry);
		}
		assertEquals(91, after.size());
		List<Entry> leftovers = after.takeAll();
		assertEquals(91, leftovers.size());
		int plants = 0;
		for (Entry entry : leftovers) {
			if (entry.previous().startsWith("state:")) {
				plants++;
			}
		}
		assertEquals(30, plants);
		assertEquals(0, after.size());
	}

	@Test
	void theWallCapOfOnePlayerIsRespected() {
		TempBlockJournal j = new TempBlockJournal();
		for (int x = 0; x < 160; x++) {
			assertTrue(j.canAdd(A, "minecraft:overworld", x, 64, 0, 160));
			j.add(e(A, x, "air", "ice_wall", 100));
		}
		assertFalse(j.canAdd(A, "minecraft:overworld", 999, 64, 0, 160));
		assertTrue(j.canAdd(A, "minecraft:overworld", 5, 64, 0, 160), "a known cell is free");
		assertTrue(j.canAdd(B, "minecraft:overworld", 999, 64, 0, 160), "the cap is per player");
	}
}
