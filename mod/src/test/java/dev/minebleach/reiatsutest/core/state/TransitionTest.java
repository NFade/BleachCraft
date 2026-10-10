package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.reiatsu.Rate;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6: legal (L) and illegal (I) transitions. */
class TransitionTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;
	private static final CharacterId NONE = CharacterId.NONE;

	// ------------------------------------------------------------------ legal

	@Test
	void L0_drawIsFreeAndLocksTheCharacter() {
		for (CharacterId c : new CharacterId[] {R, B}) {
			Fx f = new Fx();
			TransitionResult r = f.tr(ZanpakutoState.BASE, c);
			assertTrue(r.ok(), c + " " + r.reason());
			assertEquals(ZanpakutoState.SEALED, r.from());
			assertEquals(ZanpakutoState.BASE, r.to());
			assertEquals(ZanpakutoState.BASE, f.sm.state());
			assertEquals(c, f.sm.snapshot().character());
			assertEquals(f.now(), f.sm.snapshot().stateSinceTick());
			assertEquals(1000, f.sm.reiatsu().value(), "the draw costs nothing");
			assertTrue(Fx.events(r.events(), StateEvent.ReiatsuSpent.class).isEmpty());
			assertEquals(ZanpakutoState.BASE, Fx.events(r.events(), StateEvent.MirrorComponent.class).get(0).state());
			assertTrue(Fx.events(r.events(), StateEvent.BroadcastEffect.class).isEmpty(), "no release effect before shikai");
			assertEquals(ZanpakutoState.BASE, Fx.lastChange(r.events()).to());
		}
	}

	@Test
	void L0b_drawWorksWithAnAlmostEmptyBar() {
		Fx f = new Fx();
		f.sm.devSetReiatsu(1);
		assertTrue(f.tr(ZanpakutoState.BASE, R).ok());
		assertEquals(1, f.sm.reiatsu().value());
	}

	@Test
	void L0c_sheatheFromBaseIsAllowedAndHasNoReleaseLock() {
		Fx f = new Fx().toBase(R);
		TransitionResult r = f.tr(ZanpakutoState.SEALED, R);
		assertTrue(r.ok());
		assertEquals(ZanpakutoState.BASE, r.from());
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(CharacterId.NONE, f.sm.character());
		assertEquals(ZanpakutoState.SEALED, Fx.events(r.events(), StateEvent.MirrorComponent.class).get(0).state());
		assertTrue(Fx.has(r.events(), StateEvent.CancelEffects.class));
		assertEquals(f.now(), f.sm.releaseLockEndTick(), "no release lock after sheathing the base form");
		f.adv(f.cfg.transitionLockTicks());
		assertTrue(f.tr(ZanpakutoState.BASE, R).ok(), "drawing again right after the transition lock");
	}

	@Test
	void L0d_baseToShikaiWorksAndSealedToShikaiIsRejectedWithItsOwnFeedback() {
		Fx f = new Fx();
		TransitionResult no = f.tr(ZanpakutoState.SHIKAI, R);
		assertDenied(no, RejectReason.NOT_DRAWN);
		assertEquals(ResultCode.DENIED_NOT_DRAWN, no.code());
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(1000, f.sm.reiatsu().value());
		f.toBase(R);
		TransitionResult ok = f.tr(ZanpakutoState.SHIKAI, R);
		assertTrue(ok.ok());
		assertEquals(ZanpakutoState.BASE, ok.from());
		assertEquals(ZanpakutoState.SHIKAI, ok.to());
	}

	@Test
	void L0e_bankaiOnlyThroughBaseAndShikai() {
		Fx f = new Fx();
		assertDenied(f.tr(ZanpakutoState.BANKAI, R), RejectReason.NOT_IN_STATE);
		f.toBase(R);
		assertDenied(f.tr(ZanpakutoState.BANKAI, R), RejectReason.NOT_IN_STATE);
		assertEquals(ZanpakutoState.BASE, f.sm.state());
		assertDenied(f.tr(ZanpakutoState.BASE, R), RejectReason.NOT_IN_STATE); // duplicate
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		f.adv(160);
		assertDenied(f.tr(ZanpakutoState.BASE, R), RejectReason.NOT_IN_STATE); // shikai does not go back to base
		assertTrue(f.tr(ZanpakutoState.BANKAI, R).ok());
	}

	@Test
	void L0f_baseHasNoAbilitiesAndRegeneratesLikeSealed() {
		Fx f = new Fx().toBase(R);
		for (AbilityId a : AbilityId.values()) {
			assertDenied(f.cast(a, a.character), RejectReason.NOT_IN_STATE);
		}
		f.sm.devSetReiatsu(500);
		f.adv(50);
		assertEquals(500 + 10 * 10, f.sm.reiatsu().value(), "+4.0 per second like SEALED");
		f.adv(1000);
		assertEquals(ZanpakutoState.BASE, f.sm.state(), "no idle timeout, no cap in the base form");
	}

	@Test
	void L1_rukiaShikaiRelease() {
		Fx f = new Fx().toBase(R);
		TransitionResult r = f.tr(ZanpakutoState.SHIKAI, R);
		assertTrue(r.ok());
		assertEquals(ZanpakutoState.BASE, r.from());
		assertEquals(ZanpakutoState.SHIKAI, r.to());
		assertEquals(850, f.sm.reiatsu().value());
		assertEquals(R, f.sm.snapshot().character());
		assertEquals(f.now(), f.sm.snapshot().stateSinceTick());
		assertEquals(150, Fx.events(r.events(), StateEvent.ReiatsuSpent.class).get(0).tenths());
		assertEquals(ZanpakutoState.SHIKAI, Fx.events(r.events(), StateEvent.MirrorComponent.class).get(0).state());
		assertEquals(1, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
	}

	@Test
	void L2_byakuyaShikaiRelease() {
		Fx f = new Fx().toBase(B);
		TransitionResult r = f.tr(ZanpakutoState.SHIKAI, B);
		assertTrue(r.ok());
		assertEquals(850, f.sm.reiatsu().value());
		assertEquals(B, f.sm.snapshot().character());
		assertEquals(3, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
	}

	@Test
	void L3_bankaiAtExactlyFullBothCharacters() {
		for (CharacterId c : new CharacterId[] {R, B}) {
			Fx f = new Fx().toShikai(c);
			f.adv(150);
			assertEquals(1000, f.sm.reiatsu().value());
			long t = f.now();
			TransitionResult r = f.tr(ZanpakutoState.BANKAI, c);
			assertTrue(r.ok(), c + " " + r.reason());
			assertEquals(800, f.sm.reiatsu().value());
			assertEquals(t + 900, f.sm.snapshot().bankaiEndTick());
			assertEquals(ZanpakutoState.BANKAI, f.sm.state());
			int expected = c == R ? 2 : 4;
			assertEquals(expected, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
		}
	}

	@Test
	void L4_sealFromShikai() {
		Fx f = new Fx().toShikai(R);
		TransitionResult r = f.tr(ZanpakutoState.SEALED, R);
		assertTrue(r.ok());
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(CharacterId.NONE, f.sm.character());
		assertTrue(Fx.has(r.events(), StateEvent.CancelEffects.class));
		assertTrue(Fx.has(r.events(), StateEvent.RollbackTempBlocks.class));
		assertEquals(10, Fx.events(r.events(), StateEvent.BroadcastEffect.class).get(0).effectId());
		assertEquals(0, Fx.events(r.events(), StateEvent.ReiatsuSpent.class).size());
	}

	@Test
	void L5_sealFromBankaiKeepsCooldownsAndSetsReleaseLock() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.adv(44);
		assertTrue(f.cast(AbilityId.HAKUTEIKEN, B).ok());
		int cd = f.sm.cooldownRemaining(AbilityId.HAKUTEIKEN);
		assertTrue(cd > 0);
		TransitionResult r = f.tr(ZanpakutoState.SEALED, B);
		assertTrue(r.ok());
		assertEquals(f.now() + 40, f.sm.releaseLockEndTick());
		assertEquals(cd, f.sm.cooldownRemaining(AbilityId.HAKUTEIKEN));
	}

	@Test
	void L6_L7_L8_L9_L10_autoRevertsLiveInAutoRevertTest() {
		// L6 (cap) L7 (zero) L9 (idle) L10 (grace) are covered tick-exactly in AutoRevertTest A1, A2, A4, A5.
		// L8 (T6, zero in shikai) is a safety net that default rates cannot reach: use rates that drain shikai.
		EnumMap<ZanpakutoState, Rate> rates = new EnumMap<>(ZanpakutoState.class);
		BalanceConfig d = BalanceConfig.defaults();
		rates.put(ZanpakutoState.SEALED, d.rate(ZanpakutoState.SEALED));
		rates.put(ZanpakutoState.BASE, d.rate(ZanpakutoState.BASE));
		rates.put(ZanpakutoState.SHIKAI, new Rate(0, 100));
		rates.put(ZanpakutoState.BANKAI, d.rate(ZanpakutoState.BANKAI));
		BalanceConfig drain = new BalanceConfig(d.maxTenths(), d.regenBatchTicks(), rates, d.shikaiReleaseCost(),
				d.bankaiCost(), d.bankaiCapTicks(), d.shikaiIdleTicks(), d.handGraceTicks(), d.transitionLockTicks(),
				d.gcdTicks(), d.settleTicks(), d.sealLockTicks(), d.sheatheLockTicks(), d.recoveryLockTicks(), d.rateLimitPerSecond(),
				d.respawnTenths(), d.attackModeTicks(), d.barrierTicks(), d.barrierPoolTenths(),
				d.barrierReductionPercent(), d.abilities());
		Fx f = new Fx(drain, 400);
		f.toBase(R);
		f.sm.devSetReiatsu(400);
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok()); // 400 - 150 = 250, drains 100 per batch
		List<StateEvent> ev = f.adv(15);
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(Trigger.REIATSU_ZERO, Fx.lastChange(ev).trigger());
		assertEquals(10, Fx.events(ev, StateEvent.BroadcastEffect.class).get(0).effectId());
	}

	@Test
	void L11_keyAndVoiceGiveIdenticalResults() {
		Fx a = new Fx();
		Fx b = new Fx();
		assertEquals(a.tr(ZanpakutoState.BASE, RequestSource.KEY, R), b.tr(ZanpakutoState.BASE, RequestSource.VOICE, R));
		a.adv(11);
		b.adv(11);
		TransitionResult ra = a.tr(ZanpakutoState.SHIKAI, RequestSource.KEY, R);
		TransitionResult rb = b.tr(ZanpakutoState.SHIKAI, RequestSource.VOICE, R);
		assertEquals(ra, rb);
		assertEquals(a.sm.snapshot(), b.sm.snapshot());
		assertEquals(a.sm.reiatsu(), b.sm.reiatsu());
		a.adv(11);
		b.adv(11);
		assertEquals(a.tr(ZanpakutoState.SEALED, RequestSource.KEY, R), b.tr(ZanpakutoState.SEALED, RequestSource.VOICE, R));
	}

	@Test
	void L12_fullCycleIsReEnterableAfterTheLock() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		f.adv(11);
		assertTrue(f.tr(ZanpakutoState.SEALED, R).ok());
		f.adv(39);
		assertDenied(f.tr(ZanpakutoState.BASE, R), RejectReason.RELEASE_LOCK);
		f.adv(1);
		assertTrue(f.tr(ZanpakutoState.BASE, R).ok());
		f.adv(11);
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
	}

	// ------------------------------------------------------------------ illegal

	private static void assertDenied(TransitionResult r, RejectReason reason) {
		assertFalse(r.ok());
		assertEquals(reason, r.reason());
		assertEquals(reason.toWire(), r.code());
		assertTrue(r.events().isEmpty());
	}

	@Test
	void I1_sealedToBankai() {
		Fx f = new Fx();
		assertDenied(f.tr(ZanpakutoState.BANKAI, R), RejectReason.NOT_IN_STATE);
		assertEquals(1000, f.sm.reiatsu().value());
	}

	@Test
	void I2_bankaiToShikai() {
		Fx f = new Fx().toShikai(R).toBankai(R);
		f.adv(11);
		assertDenied(f.tr(ZanpakutoState.SHIKAI, R), RejectReason.NOT_IN_STATE);
	}

	@Test
	void I3_duplicateTargetIsRejectedWithoutCost() {
		Fx f = new Fx().toShikai(R);
		int before = f.sm.reiatsu().value();
		assertDenied(f.tr(ZanpakutoState.SHIKAI, R), RejectReason.NOT_IN_STATE);
		assertEquals(before, f.sm.reiatsu().value());
		f.toBankai(R);
		f.adv(11);
		int b2 = f.sm.reiatsu().value();
		assertDenied(f.tr(ZanpakutoState.BANKAI, R), RejectReason.NOT_IN_STATE);
		assertEquals(b2, f.sm.reiatsu().value());
		assertDenied(new Fx().tr(ZanpakutoState.SEALED, R), RejectReason.NOT_IN_STATE);
	}

	@Test
	void I4_bankaiBelowFull() {
		Fx f = new Fx().toShikai(R);
		f.sm.devSetReiatsu(999);
		TransitionResult r = f.tr(ZanpakutoState.BANKAI, R);
		assertDenied(r, RejectReason.BANKAI_NOT_FULL);
		assertEquals(ResultCode.DENIED_REIATSU, r.code());
		assertEquals(999, f.sm.reiatsu().value());
	}

	@Test
	void I5_releaseIsStrictlyAboveFifteen() {
		Fx poor = new Fx().toBase(R);
		poor.sm.devSetReiatsu(150);
		assertDenied(poor.tr(ZanpakutoState.SHIKAI, R), RejectReason.NOT_ENOUGH_REIATSU);
		Fx f = new Fx().toBase(R);
		f.sm.devSetReiatsu(151);
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		assertEquals(1, f.sm.reiatsu().value());
	}

	@Test
	void I6_noItemOrWrongCharacterItem() {
		assertDenied(new Fx().tr(ZanpakutoState.BASE, NONE), RejectReason.WRONG_ITEM);
		Fx base = new Fx().toBase(R);
		assertDenied(base.tr(ZanpakutoState.SHIKAI, B), RejectReason.WRONG_ITEM);
		assertDenied(base.tr(ZanpakutoState.SHIKAI, NONE), RejectReason.WRONG_ITEM);
		assertDenied(base.tr(ZanpakutoState.SEALED, B), RejectReason.WRONG_ITEM);
		Fx r = new Fx().toShikai(R);
		assertDenied(r.tr(ZanpakutoState.SEALED, B), RejectReason.WRONG_ITEM);
		assertDenied(r.tr(ZanpakutoState.SEALED, NONE), RejectReason.WRONG_ITEM);
		Fx b = new Fx().toShikai(B);
		assertDenied(b.tr(ZanpakutoState.SEALED, R), RejectReason.WRONG_ITEM);
	}

	@Test
	void I7_characterMismatchInShikai() {
		Fx f = new Fx().toShikai(R);
		f.adv(150);
		assertDenied(f.tr(ZanpakutoState.BANKAI, B), RejectReason.WRONG_ITEM);
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
	}

	@Test
	void I8_duringHandGrace() {
		Fx f = new Fx().toShikai(R);
		f.adv(150);
		f.sm.onHandChanged(NONE);
		f.adv(5);
		assertTrue(f.sm.inHandGrace());
		assertDenied(f.tr(ZanpakutoState.BANKAI, R), RejectReason.WRONG_ITEM);
		assertDenied(f.tr(ZanpakutoState.SEALED, R), RejectReason.WRONG_ITEM);
		assertDenied(f.cast(AbilityId.SHIRAFUNE, R), RejectReason.WRONG_ITEM);
		f.sm.onHandChanged(R);
		assertFalse(f.sm.inHandGrace());
		assertTrue(f.tr(ZanpakutoState.BANKAI, R).ok());
	}

	@Test
	void I9_deadPlayer() {
		Fx f = new Fx();
		f.sm.onDeath();
		TransitionResult r = f.tr(ZanpakutoState.BASE, R);
		assertDenied(r, RejectReason.DEAD_OR_SPECTATOR);
		assertEquals(ResultCode.DENIED_STATE, r.code());
		f.sm.onRespawn();
		assertTrue(f.tr(ZanpakutoState.BASE, R).ok());
	}

	@Test
	void I10_everyRejectionLeavesEverythingUnchanged() {
		for (ZanpakutoState from : ZanpakutoState.values()) {
			for (ZanpakutoState target : ZanpakutoState.values()) {
				for (CharacterId held : CharacterId.values()) {
					Fx f = new Fx();
					setUp(f, from);
					ReiatsuState rei = f.sm.reiatsu();
					ZanpakutoSnapshot snap = f.sm.snapshot();
					Map<AbilityId, Integer> cds = f.sm.cooldownRemainingAll();
					TransitionResult r = f.tr(target, held);
					if (!r.ok()) {
						String ctx = from + "->" + target + " held " + held;
						assertEquals(rei, f.sm.reiatsu(), ctx);
						assertEquals(snap, f.sm.snapshot(), ctx);
						assertEquals(cds, f.sm.cooldownRemainingAll(), ctx);
						assertTrue(r.events().isEmpty(), ctx);
						assertNotNull(r.reason(), ctx);
					}
				}
			}
		}
		// the same for abilities: every ability against every state and held value
		for (ZanpakutoState from : ZanpakutoState.values()) {
			for (AbilityId a : AbilityId.values()) {
				for (CharacterId held : CharacterId.values()) {
					Fx f = new Fx();
					setUp(f, from);
					ReiatsuState rei = f.sm.reiatsu();
					ZanpakutoSnapshot snap = f.sm.snapshot();
					Map<AbilityId, Integer> cds = f.sm.cooldownRemainingAll();
					TransitionResult r = f.cast(a, held);
					if (!r.ok()) {
						String ctx = from + " cast " + a + " held " + held;
						assertEquals(rei, f.sm.reiatsu(), ctx);
						assertEquals(snap, f.sm.snapshot(), ctx);
						assertEquals(cds, f.sm.cooldownRemainingAll(), ctx);
					}
				}
			}
		}
	}

	private static void setUp(Fx f, ZanpakutoState s) {
		switch (s) {
			case SEALED -> { }
			case BASE -> f.toBase(R);
			case SHIKAI -> f.toShikai(R);
			case BANKAI -> {
				f.toShikai(R).toBankai(R);
				f.adv(11);
			}
		}
	}

	@Test
	void I11_wireMappingIsTotal() {
		for (RejectReason r : RejectReason.values()) {
			assertNotNull(r.toWire(), r.name());
			assertNotEquals(ResultCode.OK, r.toWire(), r.name());
		}
		assertEquals(ResultCode.DENIED_STATE, RejectReason.NOT_IN_STATE.toWire());
		assertEquals(ResultCode.DENIED_ITEM, RejectReason.WRONG_ITEM.toWire());
		assertEquals(ResultCode.DENIED_REIATSU, RejectReason.NOT_ENOUGH_REIATSU.toWire());
		assertEquals(ResultCode.DENIED_REIATSU, RejectReason.BANKAI_NOT_FULL.toWire());
		for (RejectReason r : new RejectReason[] {RejectReason.ON_COOLDOWN, RejectReason.RELEASE_LOCK,
				RejectReason.TRANSITION_LOCK, RejectReason.GCD, RejectReason.SETTLE_LOCK}) {
			assertEquals(ResultCode.COOLDOWN, r.toWire(), r.name());
		}
		assertEquals(ResultCode.DENIED_NOT_DRAWN, RejectReason.NOT_DRAWN.toWire());
		assertEquals(ResultCode.RATE_LIMIT, RejectReason.RATE_LIMITED.toWire());
		assertEquals(ResultCode.DENIED_STATE, RejectReason.DEAD_OR_SPECTATOR.toWire());
		for (ResultCode c : ResultCode.values()) {
			assertEquals(c, ResultCode.fromCode(c.code()));
		}
	}

	@Test
	void I12_checkOrder() {
		// state beats item
		assertDenied(new Fx().tr(ZanpakutoState.BANKAI, NONE), RejectReason.NOT_IN_STATE);
		// item beats lock: just released (transition lock active) and wrong item
		assertDenied(new Fx().tr(ZanpakutoState.SHIKAI, NONE), RejectReason.NOT_DRAWN); // state beats item
		Fx f = new Fx().toBase(R);
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
		f.adv(2);
		assertDenied(f.tr(ZanpakutoState.SEALED, B), RejectReason.WRONG_ITEM);
		assertDenied(f.tr(ZanpakutoState.SEALED, R), RejectReason.TRANSITION_LOCK);
		// lock beats cooldown: same ability twice inside the gcd
		Fx g = new Fx().toShikai(R);
		assertTrue(g.cast(AbilityId.SHIRAFUNE, R).ok());
		g.adv(1);
		assertDenied(g.cast(AbilityId.SHIRAFUNE, R), RejectReason.GCD);
		// cooldown beats reiatsu: after the gcd, on cooldown and also too poor
		g.adv(12);
		g.sm.devSetReiatsu(1);
		assertDenied(g.cast(AbilityId.SHIRAFUNE, R), RejectReason.ON_COOLDOWN);
		// transition lock beats release lock, release lock beats reiatsu
		Fx h = new Fx().toBase(R); // too poor for shikai
		h.sm.devSetReiatsu(100);
		assertDenied(h.tr(ZanpakutoState.SHIKAI, R), RejectReason.NOT_ENOUGH_REIATSU);
		Fx k = new Fx().toShikai(R);
		assertTrue(k.tr(ZanpakutoState.SEALED, R).ok());
		k.sm.devSetReiatsu(1);
		k.adv(11);
		assertDenied(k.tr(ZanpakutoState.BASE, R), RejectReason.RELEASE_LOCK);
		k.adv(30);
		assertTrue(k.tr(ZanpakutoState.BASE, R).ok(), "the draw needs no reiatsu");
		k.adv(11);
		k.sm.devSetReiatsu(1);
		assertDenied(k.tr(ZanpakutoState.SHIKAI, R), RejectReason.NOT_ENOUGH_REIATSU);
	}

	@Test
	void unknownCodesDecodeSafely() {
		assertNull(AbilityId.fromCode((byte) 99));
		assertEquals(ZanpakutoState.SEALED, ZanpakutoState.fromCode((byte) 9));
		assertEquals(ZanpakutoState.BASE, ZanpakutoState.fromCode((byte) 1));
		assertEquals(ZanpakutoState.SHIKAI, ZanpakutoState.fromCode((byte) 2));
		assertEquals(ZanpakutoState.BANKAI, ZanpakutoState.fromCode((byte) 3));
		assertEquals(CharacterId.NONE, CharacterId.fromCode((byte) 9));
		assertEquals(ShikaiMode.IDLE, ShikaiMode.fromCode((byte) -1));
		assertEquals(AbilityId.HAKUREN, AbilityId.fromCommandId("rukia.shikai.hakuren"));
		assertEquals(AbilityId.ABSOLUTE_ZERO, AbilityId.forSlot(R, ZanpakutoState.BANKAI, 0));
		assertNull(AbilityId.forSlot(R, ZanpakutoState.BANKAI, 2));
	}
}
