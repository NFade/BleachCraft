package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6, cooldowns and abilities C1..C11. */
class AbilityTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;

	@Test
	void C1_everyAbilityChargesItsCostAndStartsItsCooldown() {
		BalanceConfig cfg = BalanceConfig.defaults();
		for (AbilityId a : AbilityId.values()) {
			AbilitySpec spec = cfg.spec(a);
			if (!spec.enabled()) {
				continue;
			}
			Fx f = new Fx();
			f.sm.devSetState(a.requiredState, a.character);
			int before = f.sm.reiatsu().value();
			TransitionResult r = f.cast(a, a.character);
			assertTrue(r.ok(), a + " " + r.reason());
			assertEquals(before - spec.costTenths(), f.sm.reiatsu().value(), a.name());
			assertEquals(spec.cooldownTicks(), f.sm.cooldownRemaining(a), a.name());
			assertEquals(spec.costTenths(), Fx.events(r.events(), StateEvent.ReiatsuSpent.class).get(0).tenths());
			assertEquals(spec.effectId(), Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
			assertEquals(f.now() + spec.cooldownTicks(), Fx.events(r.events(), StateEvent.CooldownStarted.class).get(0).endTick());
		}
	}

	@Test
	void C2_cooldownExpiresExactly() {
		Fx f = new Fx().toShikai(R);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok()); // cooldown 100 ticks
		f.adv(99);
		f.sm.devSetReiatsu(1000);
		TransitionResult early = f.cast(AbilityId.SHIRAFUNE, R);
		assertEquals(RejectReason.ON_COOLDOWN, early.reason());
		f.adv(1);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok());
	}

	@Test
	void C3_gcdAcrossDifferentAbilities() {
		Fx f = new Fx().toShikai(R);
		assertTrue(f.cast(AbilityId.TSUKISHIRO, R).ok());
		f.adv(11);
		assertEquals(RejectReason.GCD, f.cast(AbilityId.HAKUREN, R).reason());
		f.adv(1);
		assertTrue(f.cast(AbilityId.HAKUREN, R).ok());
	}

	@Test
	void C4_settleLockAfterBankai() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.adv(43);
		TransitionResult denied = f.cast(AbilityId.SCATTER, B);
		assertEquals(RejectReason.SETTLE_LOCK, denied.reason());
		assertEquals(ResultCode.COOLDOWN, denied.code());
		f.adv(1);
		assertTrue(f.cast(AbilityId.SCATTER, B).ok());
	}

	@Test
	void C5_wrongStateOrCharacterDenied() {
		Fx f = new Fx();
		assertEquals(RejectReason.NOT_IN_STATE, f.cast(AbilityId.TSUKISHIRO, R).reason()); // sealed
		f.toShikai(R);
		assertEquals(RejectReason.NOT_IN_STATE, f.cast(AbilityId.MODE_ATTACK, R).reason()); // other character
		assertEquals(RejectReason.NOT_IN_STATE, f.cast(AbilityId.ABSOLUTE_ZERO, R).reason()); // other state
		assertEquals(RejectReason.WRONG_ITEM, f.cast(AbilityId.TSUKISHIRO, B).reason());
		assertEquals(RejectReason.NOT_IN_STATE, f.sm.cast(new AbilityRequest(null, RequestSource.KEY, 999, R)).reason());
	}

	@Test
	void C6_cooldownsSurviveSealAndDieWithDeath() {
		Fx f = new Fx().toShikai(R);
		assertTrue(f.cast(AbilityId.TSUKISHIRO, R).ok());
		f.adv(11);
		assertTrue(f.tr(ZanpakutoState.SEALED, R).ok());
		assertTrue(f.sm.cooldownRemaining(AbilityId.TSUKISHIRO) > 0);
		f.sm.onDeath();
		assertEquals(0, f.sm.cooldownRemaining(AbilityId.TSUKISHIRO));
		assertTrue(f.sm.cooldownRemainingAll().isEmpty());
	}

	@Test
	void C7_insufficientReiatsuDeniedWithoutCooldown() {
		Fx f = new Fx();
		f.sm.devSetState(ZanpakutoState.SHIKAI, R);
		f.sm.devSetReiatsu(250);
		TransitionResult r = f.cast(AbilityId.TSUKISHIRO, R);
		assertEquals(RejectReason.NOT_ENOUGH_REIATSU, r.reason());
		assertEquals(0, f.sm.cooldownRemaining(AbilityId.TSUKISHIRO));
		assertEquals(250, f.sm.reiatsu().value());
		// and a rejected cast must not start the global cooldown either
		f.sm.devSetReiatsu(1000);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok());
	}

	@Test
	void C8_modeAttackEndsActiveBarrier() {
		Fx f = new Fx().toShikai(B);
		assertTrue(f.cast(AbilityId.MODE_BARRIER, B).ok());
		assertEquals(ShikaiMode.BARRIER, f.sm.shikaiMode());
		assertEquals(200, f.sm.barrierPoolTenths());
		f.adv(12);
		TransitionResult r = f.cast(AbilityId.MODE_ATTACK, B);
		assertTrue(r.ok());
		assertEquals(ShikaiMode.ATTACK, f.sm.shikaiMode());
		assertEquals(0, f.sm.barrierPoolTenths());
		assertEquals(ShikaiMode.ATTACK, f.sm.snapshot().shikaiMode());
		f.adv(29);
		assertEquals(ShikaiMode.ATTACK, f.sm.shikaiMode());
		f.adv(1);
		assertEquals(ShikaiMode.IDLE, f.sm.shikaiMode());
	}

	@Test
	void C9_barrierReturnsToIdleAt100Ticks() {
		Fx f = new Fx().toShikai(B);
		assertTrue(f.cast(AbilityId.MODE_BARRIER, B).ok());
		f.adv(99);
		assertEquals(ShikaiMode.BARRIER, f.sm.shikaiMode());
		f.adv(1);
		assertEquals(ShikaiMode.IDLE, f.sm.shikaiMode());
	}

	@Test
	void barrierAbsorbsEightyPercentUntilThePoolIsGone() {
		Fx f = new Fx().toShikai(B);
		assertEquals(100, f.sm.absorbBarrier(100)); // no barrier yet
		assertTrue(f.cast(AbilityId.MODE_BARRIER, B).ok());
		assertEquals(20, f.sm.absorbBarrier(100)); // 80 absorbed, pool 120
		assertEquals(20, f.sm.absorbBarrier(100)); // pool 40
		assertEquals(60, f.sm.absorbBarrier(100)); // only 40 left: 100 - 40, dome collapses
		assertEquals(ShikaiMode.IDLE, f.sm.shikaiMode());
		assertEquals(100, f.sm.absorbBarrier(100));
	}

	@Test
	void C10_disabledSenkeiIsRejected() {
		Fx f = new Fx();
		f.sm.devSetState(ZanpakutoState.BANKAI, B);
		TransitionResult r = f.cast(AbilityId.SENKEI, B);
		assertEquals(RejectReason.NOT_IN_STATE, r.reason());
		assertEquals(ResultCode.DENIED_STATE, r.code());
		assertFalse(BalanceConfig.defaults().spec(AbilityId.SENKEI).enabled());
	}

	@Test
	void C11_scheduledPhaseOffsets() {
		assertEquals(List.of(20, 40), phases(AbilityId.TSUKISHIRO));
		assertEquals(List.of(40, 66), phases(AbilityId.ABSOLUTE_ZERO));
		assertEquals(List.of(12), phases(AbilityId.SHIRAFUNE));
		assertEquals(List.of(10, 12, 14, 20), phases(AbilityId.MODE_ATTACK));
		assertEquals(List.of(30, 36), phases(AbilityId.HAKUTEIKEN));
		assertEquals(List.of(16, 26, 50), phases(AbilityId.SCATTER));
		assertEquals(List.of(), phases(AbilityId.MODE_BARRIER));
	}

	private static List<Integer> phases(AbilityId a) {
		Fx f = new Fx();
		f.sm.devSetState(a.requiredState, a.character);
		TransitionResult r = f.cast(a, a.character);
		assertTrue(r.ok(), a.name());
		long serial = f.sm.castSerial();
		List<Integer> offsets = new java.util.ArrayList<>();
		for (StateEvent.ScheduledPhase p : Fx.events(r.events(), StateEvent.ScheduledPhase.class)) {
			assertEquals(a, p.ability());
			assertEquals(offsets.size(), p.phaseId());
			assertEquals(serial, p.serial());
			offsets.add(p.offsetTicks());
		}
		return offsets;
	}

	@Test
	void sealBumpsTheCastSerialSoScheduledPhasesDie() {
		Fx f = new Fx().toShikai(R);
		TransitionResult r = f.cast(AbilityId.TSUKISHIRO, R);
		long serial = Fx.events(r.events(), StateEvent.ScheduledPhase.class).get(0).serial();
		assertEquals(serial, f.sm.castSerial());
		f.adv(11);
		f.tr(ZanpakutoState.SEALED, R);
		assertTrue(f.sm.castSerial() > serial);
	}
}
