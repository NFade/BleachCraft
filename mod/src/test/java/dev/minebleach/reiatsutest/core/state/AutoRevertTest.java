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
	void A1_bankaiCapAt900TicksExactly() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.adv(899);
		assertEquals(ZanpakutoState.BANKAI, f.sm.state());
		List<StateEvent> ev = f.adv(1);
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(Trigger.BANKAI_CAP, Fx.lastChange(ev).trigger());
		assertEquals(80, f.sm.reiatsu().value());
		assertEquals(11, Fx.events(ev, StateEvent.BroadcastEffect.class).get(0).effectId());
	}

	@Test
	void A2_earlierZeroWithAbilitiesSpent() {
		Fx f = new Fx().toShikai(B).toBankai(B);
		f.adv(44);
		assertTrue(f.cast(AbilityId.SCATTER, B).ok()); // -30.0
		long start = f.now();
		List<StateEvent> all = new java.util.ArrayList<>();
		while (f.sm.state() == ZanpakutoState.BANKAI && f.now() < start + 2000) {
			all.addAll(f.adv(1));
		}
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertTrue(f.sm.snapshot().bankaiEndTick() == 0);
		StateEvent.StateChanged c = Fx.lastChange(all);
		assertEquals(Trigger.REIATSU_ZERO, c.trigger());
		assertTrue(Fx.has(all, StateEvent.CancelEffects.class));
		assertTrue(Fx.has(all, StateEvent.RollbackTempBlocks.class));
		assertEquals(11, Fx.events(all, StateEvent.BroadcastEffect.class).get(0).effectId());
		assertTrue(f.now() < start + 900, "zero must come before the 45 s cap");
		assertEquals(0, f.sm.reiatsu().value(), "the bar was empty when the bankai ended");
	}

	@Test
	void A3_recoveryLockIsLongerThanTheSealLock() {
		Fx cap = new Fx().toShikai(R).toBankai(R);
		cap.adv(900);
		assertEquals(ZanpakutoState.SEALED, cap.sm.state());
		cap.adv(159);
		assertEquals(RejectReason.RELEASE_LOCK, cap.tr(ZanpakutoState.SHIKAI, R).reason());
		cap.adv(1);
		assertTrue(cap.tr(ZanpakutoState.SHIKAI, R).ok());

		Fx seal = new Fx().toShikai(R);
		assertTrue(seal.tr(ZanpakutoState.SEALED, R).ok());
		seal.adv(39);
		assertEquals(RejectReason.RELEASE_LOCK, seal.tr(ZanpakutoState.SHIKAI, R).reason());
		seal.adv(1);
		assertTrue(seal.tr(ZanpakutoState.SHIKAI, R).ok());
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
		assertTrue(gone.tr(ZanpakutoState.SHIKAI, R).ok());
	}

	@Test
	void A6_dropIsImmediate() {
		Fx f = new Fx().toShikai(R);
		List<StateEvent> ev = f.sm.onItemDropped();
		assertEquals(ZanpakutoState.SEALED, f.sm.state());
		assertEquals(Trigger.ITEM_DROPPED, Fx.lastChange(ev).trigger());
		assertTrue(Fx.has(ev, StateEvent.CancelEffects.class));
		assertTrue(f.sm.onItemDropped().isEmpty(), "dropping while sealed does nothing");
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
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
	}
}
