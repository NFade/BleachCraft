package dev.minebleach.reiatsutest.core.reiatsu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.Fx;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6, reiatsu tests R1..R9. */
class ReiatsuTest {
	private static final CharacterId R = CharacterId.RUKIA;

	@Test
	void R1_batchValuesPerState() {
		Fx sealed = new Fx(0);
		sealed.adv(5);
		assertEquals(10, sealed.sm.reiatsu().value());

		Fx shikai = new Fx();
		shikai.sm.devSetState(ZanpakutoState.SHIKAI, R);
		shikai.sm.devSetReiatsu(500);
		shikai.adv(5);
		assertEquals(505, shikai.sm.reiatsu().value());

		Fx bankai = new Fx();
		bankai.sm.devSetState(ZanpakutoState.BANKAI, R);
		bankai.sm.devSetReiatsu(500);
		bankai.adv(5);
		assertEquals(496, bankai.sm.reiatsu().value());
	}

	@Test
	void R2_sealedFillsIn25SecondsAndShikaiRefillsFifteenIn150Ticks() {
		Fx f = new Fx(0);
		f.adv(495);
		assertEquals(990, f.sm.reiatsu().value());
		f.adv(5);
		assertTrue(f.sm.reiatsu().isFull());

		Fx s = new Fx().toShikai(R); // 850 + 11 ticks of regen
		s.sm.devSetReiatsu(850);
		s.adv(145);
		assertEquals(995, s.sm.reiatsu().value());
		s.adv(5);
		assertTrue(s.sm.reiatsu().isFull());
	}

	@Test
	void R3_clampAtMaxAndZeroEvenForHugeDt() {
		Fx f = new Fx(990);
		f.clock.advance(5_000_000L);
		f.sm.tick(5_000_000);
		assertEquals(1000, f.sm.reiatsu().value());

		Fx b = new Fx();
		b.sm.devSetState(ZanpakutoState.BANKAI, R);
		b.sm.devSetReiatsu(4);
		b.adv(10);
		// 4 - 4 = 0 at the first batch: bankai ends, and the bar never goes below zero
		assertEquals(ZanpakutoState.SEALED, b.sm.state());
		assertTrue(b.sm.reiatsu().value() >= 0 && b.sm.reiatsu().value() <= 1000);
	}

	@Test
	void R4_nonBatchTicksChangeNothing() {
		Fx f = new Fx(100);
		for (int i = 1; i <= 4; i++) {
			f.adv(1);
			assertEquals(100, f.sm.reiatsu().value(), "tick offset " + i);
		}
		f.adv(1);
		assertEquals(110, f.sm.reiatsu().value());
	}

	@Test
	void R5_spendingNeverReachesZero() {
		ReiatsuState s = new ReiatsuState(250, 1000);
		assertFalse(s.canSpend(250));
		assertTrue(s.canSpend(249));
		assertEquals(1, s.plus(-249).value());

		Fx f = new Fx();
		f.sm.devSetState(ZanpakutoState.SHIKAI, R);
		f.sm.devSetReiatsu(250);
		assertFalse(f.cast(AbilityId.TSUKISHIRO, R).ok());
		f.sm.devSetReiatsu(251);
		assertTrue(f.cast(AbilityId.TSUKISHIRO, R).ok());
		assertEquals(1, f.sm.reiatsu().value());
	}

	@Test
	void R6_idleBankaiAfter900TicksHasEightPoints() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		long start = f.now();
		assertEquals(800, f.sm.reiatsu().value());
		f.adv(899);
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		f.adv(1);
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(start + 900, f.now());
		// 180 drain batches of -4 happened, then the revert stopped the drain: 800 - 720 = 80, plus 0 sealed batches
		assertEquals(80, f.sm.reiatsu().value());
	}

	@Test
	void R7_catchUpEqualsStepping() {
		Fx a = new Fx().toShikai(R);
		Fx b = new Fx().toShikai(R);
		a.adv(100);
		b.clock.advance(100);
		b.sm.tick(100);
		assertEquals(a.sm.reiatsu(), b.sm.reiatsu());
		assertEquals(a.sm.snapshot(), b.sm.snapshot());
	}

	@Test
	void R8_invalidConstructionIsClamped() {
		assertEquals(new ReiatsuState(0, 1000), new ReiatsuState(-5, 1000));
		assertEquals(new ReiatsuState(1000, 1000), new ReiatsuState(5000, 1000));
		assertEquals(new ReiatsuState(0, 0), new ReiatsuState(7, -3));
		assertEquals(1000, new ReiatsuState(990, 1000).plus(Integer.MAX_VALUE).value());
		assertEquals(0, new ReiatsuState(10, 1000).plus(Integer.MIN_VALUE).value());
	}

	@Test
	void R9_ticksToFull() {
		Rate sealed = new Rate(10, 0);
		assertEquals(500, ReiatsuMath.ticksToFull(new ReiatsuState(0, 1000), sealed));
		assertEquals(0, ReiatsuMath.ticksToFull(new ReiatsuState(1000, 1000), sealed));
		assertEquals(150, ReiatsuMath.ticksToFull(new ReiatsuState(850, 1000), new Rate(8, 3)));
		assertEquals(-1, ReiatsuMath.ticksToFull(new ReiatsuState(0, 1000), new Rate(2, 6)));
		assertEquals(5, ReiatsuMath.ticksToFull(new ReiatsuState(999, 1000), sealed));
		assertEquals(1000, ReiatsuMath.ticksToZero(new ReiatsuState(800, 1000), new Rate(2, 6), 5));
		assertEquals(-1, ReiatsuMath.ticksToZero(new ReiatsuState(800, 1000), sealed, 5));
	}
}
