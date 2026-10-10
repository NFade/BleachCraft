package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** STATE_MACHINE section 6, auto-revert A1..A8. */
class AutoRevertTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;

	@Test
	void A1_bankaiTimerEndsAt900TicksExactlyAndFallsBackToShikai() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.adv(899);
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		List<StateEvent> ev = f.adv(1);
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state(), "B4 step 4: the timer ends bankai into shikai, not into the scabbard");
		assertEquals(B, f.sm.character(), "the zanpakuto stays released and locked");
		assertEquals(Trigger.BANKAI_CAP, Fx.lastChange(ev).trigger());
		assertEquals(ZanpakutoState.BANKAI, Fx.lastChange(ev).from());
		assertEquals(ZanpakutoState.SHIKAI, Fx.lastChange(ev).to());
		assertEquals(1000, f.sm.reiatsu().value(), "no upkeep drain: the bar is still full");
		assertEquals(11, Fx.events(ev, StateEvent.BroadcastEffect.class).get(0).effectId());
		assertTrue(Fx.has(ev, StateEvent.MirrorComponent.class));
		assertEquals(ZanpakutoState.SHIKAI, Fx.events(ev, StateEvent.MirrorComponent.class).get(0).state());
		assertFalse(Fx.has(ev, StateEvent.CancelEffects.class), "casts in flight keep running");
		assertEquals(0, f.sm.snapshot().bankaiEndTick());
	}

	@Test
	void A2_spendingNeverEndsBankaiEarlyAndAZeroBarIsOnlyASafetyNet() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.adv(44);
		assertTrue(f.cast(AbilityId.SCATTER, B).ok()); // free
		assertTrue(f.cast(AbilityId.MODE_ATTACK, B).reason() == RejectReason.GCD);
		f.adv(12);
		assertTrue(f.cast(AbilityId.MODE_ATTACK, B).ok()); // shikai ability inside bankai: -12.0
		assertEquals(880, f.sm.reiatsu().value());
		long start = f.now();
		f.adv(800);
		assertEquals(ZanpakutoState.BANKAI, f.sm.state(), "abilities never end bankai early");
		// a (custom) bankai drain that empties the bar does not end bankai either: only the timer does
		Fx d = new Fx(Fx.withRate(ZanpakutoState.BANKAI, new dev.minebleach.reiatsutest.core.reiatsu.Rate(0, 4)), 1000);
		d.toShikai(B);
		d.adv(150);
		assertTrue(d.tr(ZanpakutoState.BANKAI, B).ok());
		d.sm.devSetReiatsu(8);
		d.adv(100);
		assertEquals(0, d.sm.reiatsu().value());
		assertEquals(ZanpakutoState.BANKAI, d.sm.state());
		assertTrue(start > 0);
	}

	@Test
	void A3_bankaiReentryLockAfterTheTimerAndAfterASeal() {
		Fx cap = new Fx().toShikai(R).toBankai(R);
		cap.adv(900);
		assertEquals(ZanpakutoState.SHIKAI, cap.sm.state());
		assertEquals(1000, cap.sm.reiatsu().value());
		cap.adv(1199);
		assertEquals(RejectReason.RELEASE_LOCK, cap.tr(ZanpakutoState.BANKAI, R).reason(), "full bar is not enough during the lock");
		assertEquals(ResultCode.COOLDOWN, cap.tr(ZanpakutoState.BANKAI, R).code());
		cap.adv(1);
		assertTrue(cap.tr(ZanpakutoState.BANKAI, R).ok(), "the lock lasts bankaiReentryTicks (1200) from the end of the timer");

		Fx byHand = new Fx().toShikai(R).toBankai(R);
		byHand.adv(60);
		assertTrue(byHand.tr(ZanpakutoState.SEALED, R).ok());
		byHand.adv(40);
		assertTrue(byHand.tr(ZanpakutoState.BASE, R).ok());
		byHand.adv(11);
		assertTrue(byHand.tr(ZanpakutoState.SHIKAI, R).ok());
		byHand.adv(300);
		assertEquals(RejectReason.RELEASE_LOCK, byHand.tr(ZanpakutoState.BANKAI, R).reason(), "sealing in bankai starts the lock too");

		Fx seal = new Fx().toShikai(R);
		assertTrue(seal.tr(ZanpakutoState.SEALED, R).ok());
		seal.adv(39);
		assertEquals(RejectReason.RELEASE_LOCK, seal.tr(ZanpakutoState.BASE, R).reason());
		seal.adv(1);
		assertTrue(seal.tr(ZanpakutoState.BASE, R).ok());
	}

	@Test
	void A4_shikaiIdleTimeoutResetByEachCast() {
		Fx f = new Fx().toShikai(R);
		long t0 = f.now() - 11;
		f.adv(2000 - 11);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok());
		long castAt = f.now();
		f.adv(2399);
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
		List<StateEvent> ev = f.adv(1);
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(Trigger.SHIKAI_IDLE, Fx.lastChange(ev).trigger());
		assertEquals(castAt + 2400, f.now());
		assertTrue(f.now() - t0 > 2400);

		Fx g = new Fx().toShikai(R);
		g.adv(2400 - 11 - 1);
		assertEquals(ZanpakutoState.SHIKAI, g.sm.state());
		g.adv(1);
		assertEquals(ZanpakutoState.SEALED, g.sm.state());
	}

	@Test
	void A5_handGrace20Ticks() {
		Fx back = new Fx().toShikai(R);
		back.sm.onHandChanged(CharacterId.NONE);
		back.adv(19);
		assertEquals(ZanpakutoState.SHIKAI, back.sm.state());
		back.sm.onHandChanged(R);
		back.adv(5);
		assertEquals(ZanpakutoState.SHIKAI, back.sm.state());

		Fx gone = new Fx().toShikai(R);
		gone.sm.onHandChanged(B); // another zanpakuto in hand also counts as lost
		gone.adv(19);
		assertEquals(ZanpakutoState.SHIKAI, gone.sm.state());
		List<StateEvent> ev = gone.adv(1);
		assertEquals(ZanpakutoState.SEALED, gone.sm.state());
		assertEquals(Trigger.HAND_LOST, Fx.lastChange(ev).trigger());
		// release lock is 0 after a hand loss
		assertTrue(gone.tr(ZanpakutoState.BASE, R).ok());
	}

	@Test
	void A5b_handGraceAlsoAppliesToTheBaseForm() {
		Fx back = new Fx().toBase(R);
		back.sm.onHandChanged(CharacterId.NONE);
		back.adv(19);
		assertEquals(ZanpakutoState.BASE, back.sm.state());
		assertEquals(RejectReason.WRONG_ITEM, back.tr(ZanpakutoState.SHIKAI, R).reason(), "no release during the grace");
		back.sm.onHandChanged(R);
		back.adv(5);
		assertEquals(ZanpakutoState.BASE, back.sm.state());
		assertTrue(back.tr(ZanpakutoState.SHIKAI, R).ok());

		Fx gone = new Fx().toBase(R);
		gone.sm.onHandChanged(CharacterId.NONE);
		gone.adv(19);
		assertEquals(ZanpakutoState.BASE, gone.sm.state());
		List<StateEvent> ev = gone.adv(1);
		assertEquals(ZanpakutoState.SEALED, gone.sm.state());
		assertEquals(Trigger.HAND_LOST, Fx.lastChange(ev).trigger());
		assertEquals(ZanpakutoState.SEALED, Fx.events(ev, StateEvent.MirrorComponent.class).get(0).state());
		assertEquals(CharacterId.NONE, gone.sm.character());
	}

	@Test
	void A5c_swappingTheHandToAnotherZanpakutoLosesTheBaseForm() {
		Fx f = new Fx().toBase(R);
		f.sm.onHandChanged(B);
		f.adv(20);
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
	}

	@Test
	void A6_dropIsImmediate() {
		Fx f = new Fx().toShikai(R);
		List<StateEvent> ev = f.sm.onItemDropped();
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(Trigger.ITEM_DROPPED, Fx.lastChange(ev).trigger());
		assertTrue(Fx.has(ev, StateEvent.CancelEffects.class));
		assertTrue(f.sm.onItemDropped().isEmpty(), "dropping while sealed does nothing");

		Fx base = new Fx().toBase(B);
		List<StateEvent> ev2 = base.sm.onItemDropped();
		assertEquals(ZanpakutoState.SEALED, base.sm.state());
		assertEquals(Trigger.ITEM_DROPPED, Fx.lastChange(ev2).trigger());
	}

	@Test
	void A7_deathLogoutDimensionResetEverything() {
		for (Trigger t : new Trigger[] {Trigger.DEATH, Trigger.LOGOUT, Trigger.DIMENSION_CHANGE}) {
			Fx f = new Fx().toShikai(B).toBankai(B);
			f.adv(44);
			assertTrue(f.cast(AbilityId.SCATTER, B).ok());
			List<StateEvent> ev = switch (t) {
				case DEATH -> f.sm.onDeath();
				case LOGOUT -> f.sm.onLogout();
				default -> f.sm.onDimensionChange();
			};
			assertEquals(ZanpakutoState.SEALED, f.sm.state(), t.name());
			assertEquals(CharacterId.NONE, f.sm.character(), t.name());
			assertEquals(ShikaiMode.IDLE, f.sm.shikaiMode(), t.name());
			assertTrue(Fx.has(ev, StateEvent.CancelEffects.class), t.name());
			assertTrue(Fx.has(ev, StateEvent.RollbackTempBlocks.class), t.name());
			assertEquals(t, Fx.lastChange(ev).trigger());
			assertEquals(0, f.sm.releaseLockEndTick(), t.name());
			boolean cooldownsKept = t == Trigger.DIMENSION_CHANGE;
			assertEquals(cooldownsKept, f.sm.cooldownRemaining(AbilityId.SCATTER) > 0, t.name());
		}
	}

	@Test
	void A7b_deathLogoutDimensionAlsoResetTheBaseForm() {
		for (Trigger t : new Trigger[] {Trigger.DEATH, Trigger.LOGOUT, Trigger.DIMENSION_CHANGE}) {
			Fx f = new Fx().toBase(R);
			List<StateEvent> ev = switch (t) {
				case DEATH -> f.sm.onDeath();
				case LOGOUT -> f.sm.onLogout();
				default -> f.sm.onDimensionChange();
			};
			assertEquals(ZanpakutoState.SEALED, f.sm.state(), t.name());
			assertEquals(CharacterId.NONE, f.sm.character(), t.name());
			assertEquals(t, Fx.lastChange(ev).trigger());
			assertEquals(ZanpakutoState.BASE, Fx.lastChange(ev).from());
			assertEquals(ZanpakutoState.SEALED, Fx.events(ev, StateEvent.MirrorComponent.class).get(0).state(), t.name());
		}
	}

	@Test
	void A7c_spectatorModeResetsTheBaseForm() {
		Fx f = new Fx().toBase(B);
		f.sm.setSpectator(true);
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(RejectReason.DEAD_OR_SPECTATOR, f.tr(ZanpakutoState.BASE, B).reason());
	}

	@Test
	void A8_respawnGivesHalfABar() {
		Fx f = new Fx();
		f.sm.onDeath();
		assertTrue(f.sm.isDead());
		f.sm.onRespawn();
		assertFalse(f.sm.isDead());
		assertEquals(500, f.sm.reiatsu().value());
	}

	@Test
	void spectatorBlocksAndResets() {
		Fx f = new Fx().toShikai(R);
		List<StateEvent> ev = f.sm.setSpectator(true);
		assertTrue(Fx.has(ev, StateEvent.CancelEffects.class));
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(RejectReason.DEAD_OR_SPECTATOR, f.tr(ZanpakutoState.SHIKAI, R).reason());
		f.sm.setSpectator(false);
		f.adv(60);
		assertTrue(f.tr(ZanpakutoState.BASE, R).ok());
	}
}
