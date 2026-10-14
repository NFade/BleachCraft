package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Sprenger root and the shunpo ban: the window, the early end on damage, the own explosion. */
class BindBookTest {
	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();

	@Test
	void theBindLastsFromTheBindMomentToTheExplosion() {
		// the pentagon is cast at tick 100: bind at 116, explosion at 140
		SprengerTrap t = new SprengerTrap(UUID.randomUUID(), new V3(0, 64, 0), 100);
		BindBook book = new BindBook();
		int bindTick = -1;
		int explodeTick = -1;
		for (int now = 100; now < 150; now++) {
			SprengerTrap.Step st = t.advance(now);
			if (st == SprengerTrap.Step.BIND) {
				bindTick = now;
				book.bind(A, now, UryuBalance.SPRENGER_BIND_TICKS);
			} else if (st == SprengerTrap.Step.EXPLODE) {
				explodeTick = now;
			}
		}
		assertEquals(116, bindTick);
		assertEquals(140, explodeTick);
		assertEquals(140, book.endOf(A), "the bind runs out exactly at the explosion");
		assertEquals(24, UryuBalance.SPRENGER_BIND_TICKS, "1.2 s");
		assertTrue(book.has(A), "one bind, ending at 140");
		assertTrue(book.active(A, 116));
		assertTrue(book.active(A, 139));
		assertFalse(book.active(A, 140));
	}

	@Test
	void anyDamageEndsTheBindAtOnce() {
		BindBook book = new BindBook();
		book.bind(A, 116, 24);
		assertTrue(book.active(A, 120));
		assertTrue(book.onDamage(A), "the damage broke the bind");
		assertFalse(book.active(A, 120), "no root and no shunpo ban any more");
		assertFalse(book.onDamage(A), "a second hit has nothing to break");
	}

	@Test
	void damageToSomeoneNotBoundChangesNothing() {
		BindBook book = new BindBook();
		book.bind(A, 116, 24);
		assertFalse(book.onDamage(B));
		assertTrue(book.active(A, 120));
	}

	@Test
	void theOwnExplosionIsReleasedFirstSoItIsNotAnEarlyBreak() {
		BindBook book = new BindBook();
		book.bind(A, 116, 24);
		// the glue frees the bound ones before the explosion hurts them (also for a focused arrow that detonates the pentagon early)
		assertTrue(book.release(A));
		assertFalse(book.onDamage(A), "the explosion damage finds no bind: it is not counted as a break");
		assertFalse(book.active(A, 130));
		assertFalse(book.has(A));
	}

	@Test
	void anEarlyDetonationEndsTheBindAtTheDetonation() {
		SprengerTrap t = new SprengerTrap(UUID.randomUUID(), new V3(0, 64, 0), 100);
		BindBook book = new BindBook();
		book.bind(A, 116, 24);
		assertTrue(t.detonateEarly()); // tick 125
		book.release(A);
		assertFalse(book.active(A, 125));
	}

	@Test
	void aLongerBindIsKeptAndTheBookReportsTheExpired() {
		BindBook book = new BindBook();
		book.bind(A, 100, 24);
		book.bind(A, 110, 5);
		assertEquals(124, book.endOf(A), "the shorter second bind does not shorten the first");
		book.bind(B, 120, 10);
		assertEquals(List.of(), book.expired(123));
		assertEquals(List.of(A), book.expired(124));
		assertEquals(2, book.size());
	}

	@Test
	void deathLogoutAndStopClearTheBook() {
		BindBook book = new BindBook();
		book.bind(A, 0, 24);
		book.bind(B, 0, 24);
		assertTrue(book.release(A));
		assertFalse(book.release(A));
		book.clear();
		assertEquals(0, book.size());
		assertFalse(book.active(B, 5));
	}
}
