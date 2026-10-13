package dev.minebleach.reiatsutest.core.gin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.gin.SliverBook.Reason;
import dev.minebleach.reiatsutest.core.gin.SliverBook.Removal;
import dev.minebleach.reiatsutest.core.gin.SliverBook.Sliver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The sliver registry (design/GIN.md sections 4 and 5): lifetime, the Renjin refresh, and every cleanse reason. */
class SliverBookTest {
	private static final UUID GIN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID GIN2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	@Test
	void aSliverLivesTwelveSecondsAndThenExpires() {
		SliverBook book = new SliverBook();
		assertNull(book.place(A, GIN, 1000 + 240));
		assertTrue(book.liveOf(A, GIN, 1000));
		assertTrue(book.liveOf(A, GIN, 1239));
		assertFalse(book.liveOf(A, GIN, 1240));
		assertTrue(book.expire(1239).isEmpty());
		List<Removal> gone = book.expire(1240);
		assertEquals(1, gone.size());
		assertEquals(Reason.TIMER, gone.get(0).reason());
		assertEquals(A, gone.get(0).sliver().target());
		assertEquals(0, book.size());
	}

	@Test
	void everyCleanseReasonRemovesTheSliverAndReportsItsReason() {
		for (Reason why : new Reason[] {Reason.MILK, Reason.TARGET_DEATH, Reason.OUT_OF_RANGE, Reason.DISSOLVED, Reason.INTERRUPTED}) {
			SliverBook book = new SliverBook();
			book.place(A, GIN, 500);
			Removal r = book.cleanse(A, why);
			assertNotNull(r, why.name());
			assertEquals(why, r.reason());
			assertEquals(GIN, r.sliver().owner());
			assertFalse(book.has(A), why.name());
			assertNull(book.cleanse(A, why), "a second cleanse finds nothing");
		}
	}

	@Test
	void theOwnersEndCleansesAllHisSlivers() {
		for (Reason why : new Reason[] {Reason.OWNER_DEATH, Reason.OWNER_LEFT_BANKAI}) {
			SliverBook book = new SliverBook();
			book.place(A, GIN, 500);
			book.place(B, GIN2, 500);
			List<Removal> r = book.cleanseOwner(GIN, why);
			assertEquals(1, r.size());
			assertEquals(why, r.get(0).reason());
			assertFalse(book.has(A));
			assertTrue(book.has(B), "another Gin's sliver stays");
			assertNull(book.ofOwner(GIN));
			assertNotNull(book.ofOwner(GIN2));
		}
	}

	@Test
	void renjinResetsTheTimerToTwelveSeconds() {
		SliverBook book = new SliverBook();
		book.place(A, GIN, 240);
		assertTrue(book.refresh(A, GIN, 200, 240));
		assertEquals(440, book.get(A).expiry());
		assertTrue(book.liveOf(A, GIN, 439));
		assertTrue(book.expire(239).isEmpty(), "the old end of the timer passes without effect");
		assertFalse(book.expire(440).isEmpty());
	}

	@Test
	void refreshOnlyWorksForTheOwnerOnALiveSliver() {
		SliverBook book = new SliverBook();
		book.place(A, GIN, 240);
		assertFalse(book.refresh(A, GIN2, 10, 240), "another Gin's Renjin does not touch it");
		assertFalse(book.refresh(B, GIN, 10, 240), "no sliver on B");
		assertFalse(book.refresh(A, GIN, 240, 240), "already over");
		assertEquals(240, book.get(A).expiry());
	}

	@Test
	void extendKeepsTheSliverAliveDuringTheChannelButNeverShortensIt() {
		SliverBook book = new SliverBook();
		book.place(A, GIN, 240);
		book.extendTo(A, 100);
		assertEquals(240, book.get(A).expiry());
		book.extendTo(A, 262);
		assertEquals(262, book.get(A).expiry());
	}

	@Test
	void aNewerSliverOnTheSameTargetReplacesTheOldOne() {
		SliverBook book = new SliverBook();
		book.place(A, GIN, 240);
		Sliver old = book.place(A, GIN2, 300);
		assertEquals(GIN, old.owner());
		assertEquals(GIN2, book.get(A).owner());
		assertEquals(1, book.size());
	}
}
