package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6, debounce interplay D1..D7. */
class DebounceTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;

	@Test
	void D1_duplicateReleaseOneTickApart() {
		Fx f = new Fx();
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		f.adv(1);
		TransitionResult second = f.tr(ZanpakutoState.SHIKAI, R);
		assertEquals(RejectReason.NOT_IN_STATE, second.reason());
		assertEquals(ResultCode.DENIED_STATE, second.code());
		assertTrue(f.sm.reiatsu().value() >= 850 && f.sm.reiatsu().value() < 1000, "charged once");
	}

	@Test
	void D2_releaseThenSealWithinTenTicks() {
		Fx f = new Fx();
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		f.adv(9);
		assertEquals(RejectReason.TRANSITION_LOCK, f.tr(ZanpakutoState.SEALED, R).reason());
		f.adv(1);
		assertTrue(f.tr(ZanpakutoState.SEALED, R).ok());
	}

	@Test
	void D3_bankaiThenChireFourTicksLater() {
		Fx f = new Fx().toShikai(B);
		f.adv(150);
		assertTrue(f.tr(ZanpakutoState.BANKAI, B).ok());
		f.adv(4);
		TransitionResult r = f.cast(AbilityId.SCATTER, B);
		assertFalse(r.ok());
		assertEquals(RejectReason.TRANSITION_LOCK, r.reason());
		assertEquals(ResultCode.COOLDOWN, r.code());
		f.adv(10); // 14 ticks: past the transition lock, inside the 44 tick settle lock
		assertEquals(RejectReason.SETTLE_LOCK, f.cast(AbilityId.SCATTER, B).reason());
	}

	@Test
	void D4_replayedOrLowerSequenceIsIgnored() {
		Fx f = new Fx();
		assertTrue(f.sm.request(new TransitionRequest(ZanpakutoState.SHIKAI, RequestSource.VOICE, 5, R)).ok());
		f.adv(11);
		TransitionResult same = f.sm.request(new TransitionRequest(ZanpakutoState.SEALED, RequestSource.VOICE, 5, R));
		assertEquals(RejectReason.STALE_SEQ, same.reason());
		TransitionResult lower = f.sm.request(new TransitionRequest(ZanpakutoState.SEALED, RequestSource.VOICE, 3, R));
		assertEquals(RejectReason.STALE_SEQ, lower.reason());
		assertEquals(ResultCode.RATE_LIMIT, lower.code());
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
		assertTrue(f.sm.request(new TransitionRequest(ZanpakutoState.SEALED, RequestSource.VOICE, 6, R)).ok());
	}

	@Test
	void D5_rateLimiterTenPerSecond() {
		FakeClock clock = new FakeClock(100);
		RateLimiter rl = new RateLimiter(clock, 10);
		for (int i = 0; i < 10; i++) {
			assertTrue(rl.tryAcquire(), "request " + i);
		}
		assertFalse(rl.tryAcquire(), "11th request in the same tick");
		clock.advance(1);
		assertFalse(rl.tryAcquire(), "half a token after one tick");
		clock.advance(1);
		assertTrue(rl.tryAcquire(), "one token after two ticks");
		assertFalse(rl.tryAcquire());
		clock.advance(200);
		for (int i = 0; i < 10; i++) {
			assertTrue(rl.tryAcquire());
		}
		assertFalse(rl.tryAcquire(), "burst is capped at one second worth");
	}

	@Test
	void D5b_machineAnswersRateLimit() {
		Fx f = new Fx(1000);
		int ok = 0;
		TransitionResult last = null;
		for (int i = 0; i < 12; i++) {
			last = f.tr(ZanpakutoState.BANKAI, R); // rejected requests still use tokens
			if (last.reason() != RejectReason.RATE_LIMITED) {
				ok++;
			}
		}
		assertEquals(10, ok);
		assertEquals(RejectReason.RATE_LIMITED, last.reason());
		assertEquals(ResultCode.RATE_LIMIT, last.code());
	}

	@Test
	void D6_rejectionTakesOneTokenAndStartsNoCooldown() {
		Fx f = new Fx();
		f.sm.devSetState(ZanpakutoState.SHIKAI, R);
		f.sm.devSetReiatsu(100);
		for (int i = 0; i < 5; i++) {
			assertEquals(RejectReason.NOT_ENOUGH_REIATSU, f.cast(AbilityId.TSUKISHIRO, R).reason());
		}
		assertTrue(f.sm.cooldownRemainingAll().isEmpty());
		// five tokens are left: five more requests pass the limiter
		for (int i = 0; i < 5; i++) {
			assertEquals(RejectReason.NOT_ENOUGH_REIATSU, f.cast(AbilityId.TSUKISHIRO, R).reason());
		}
		assertEquals(RejectReason.RATE_LIMITED, f.cast(AbilityId.TSUKISHIRO, R).reason());
	}

	@Test
	void D7_keyAndVoiceAreIdenticalForAbilities() {
		Fx a = new Fx();
		Fx b = new Fx();
		a.sm.devSetState(ZanpakutoState.SHIKAI, R);
		b.sm.devSetState(ZanpakutoState.SHIKAI, R);
		TransitionResult ra = a.sm.cast(new AbilityRequest(AbilityId.HAKUREN, RequestSource.KEY, 1, R));
		TransitionResult rb = b.sm.cast(new AbilityRequest(AbilityId.HAKUREN, RequestSource.VOICE, 1, R));
		assertEquals(ra, rb);
		assertEquals(a.sm.cooldownRemainingAll(), b.sm.cooldownRemainingAll());
	}
}
