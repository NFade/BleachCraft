package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoRequest;
import dev.minebleach.reiatsutest.core.rage.RageConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Round 5: shikai and bankai entry need (and pay) Rage; keys and voice share the machine, so one check covers both. Also the
 * "any moment" rule of kido and the rule that the sword leaving the hand ends nothing.
 */
class RageGateTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final RageConfig RC = RageConfig.defaults();

	/** A fixture whose Rage is the first element of the returned holder. */
	private static final class Rig {
		final Fx fx = new Fx();
		final int[] rage = new int[1];

		Rig(int rageTenths) {
			rage[0] = rageTenths;
			fx.sm.setRage(RC, () -> rage[0]);
		}
	}

	@Test
	void defaultCostsAre40And80OfTheBar() {
		assertEquals(400, RC.shikaiCostTenths());
		assertEquals(800, RC.bankaiCostTenths());
		assertEquals(1000, RC.maxTenths());
	}

	@Test
	void shikaiIsRefusedBelowTheThresholdWithNotEnoughRage() {
		Rig g = new Rig(399);
		g.fx.toBase(R);
		TransitionResult r = g.fx.tr(ZanpakutoState.SHIKAI, R);
		assertFalse(r.ok());
		assertEquals(RejectReason.NOT_ENOUGH_RAGE, r.reason());
		assertEquals(ResultCode.DENIED_RAGE, r.code());
		assertTrue(r.events().isEmpty(), "a refusal changes and costs nothing");
		assertEquals(ZanpakutoState.BASE, g.fx.sm.state());
		assertEquals(1000, g.fx.sm.reiatsu().value(), "no reiatsu was spent either");
	}

	@Test
	void shikaiAtTheThresholdPaysTheRage() {
		Rig g = new Rig(400);
		g.fx.toBase(R);
		TransitionResult r = g.fx.tr(ZanpakutoState.SHIKAI, R);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		List<StateEvent.RageSpent> paid = Fx.events(r.events(), StateEvent.RageSpent.class);
		assertEquals(1, paid.size());
		assertEquals(400, paid.get(0).tenths());
		assertEquals(ZanpakutoState.SHIKAI, g.fx.sm.state());
	}

	@Test
	void bankaiNeedsEightyAndPaysIt() {
		Rig g = new Rig(1000);
		g.fx.toShikai(R);
		g.rage[0] = 799;
		TransitionResult denied = g.fx.tr(ZanpakutoState.BANKAI, R);
		assertEquals(RejectReason.NOT_ENOUGH_RAGE, denied.reason());
		assertEquals(ResultCode.DENIED_RAGE, denied.code());
		assertEquals(ZanpakutoState.SHIKAI, g.fx.sm.state());
		g.rage[0] = 800;
		TransitionResult ok = g.fx.tr(ZanpakutoState.BANKAI, R);
		assertTrue(ok.ok(), String.valueOf(ok.reason()));
		assertEquals(800, Fx.events(ok.events(), StateEvent.RageSpent.class).get(0).tenths());
	}

	@Test
	void theRageCheckComesBeforeTheReiatsuCheck() {
		Rig g = new Rig(0);
		g.fx.toBase(R);
		g.fx.sm.devSetReiatsu(0);
		assertEquals(RejectReason.NOT_ENOUGH_RAGE, g.fx.tr(ZanpakutoState.SHIKAI, R).reason());
	}

	@Test
	void ragefreeSkipsTheRequirementAndThePayment() {
		Rig g = new Rig(0);
		g.fx.sm.setRageFree(true);
		g.fx.toBase(R);
		TransitionResult r = g.fx.tr(ZanpakutoState.SHIKAI, R);
		assertTrue(r.ok(), String.valueOf(r.reason()));
		assertTrue(Fx.events(r.events(), StateEvent.RageSpent.class).isEmpty());
		g.fx.sm.setRageFree(false);
		assertFalse(g.fx.sm.rageFree(), "default and switch-off: required again");
		assertEquals(400, g.fx.sm.rageNeed(ZanpakutoState.SHIKAI));
		assertEquals(0, g.fx.sm.rageNeed(ZanpakutoState.BASE));
		assertEquals(0, g.fx.sm.rageNeed(ZanpakutoState.SEALED));
	}

	@Test
	void withoutAGateTheMachineAloneDoesNotRequireRage() {
		Fx f = new Fx();
		f.toBase(R);
		assertTrue(f.tr(ZanpakutoState.SHIKAI, R).ok());
	}

	@Test
	void drawAndSealCostNoRage() {
		Rig g = new Rig(0);
		assertTrue(g.fx.tr(ZanpakutoState.BASE, R).ok());
		g.fx.adv(g.fx.cfg.transitionLockTicks() + 1);
		assertTrue(g.fx.tr(ZanpakutoState.SEALED, R).ok());
	}

	@Test
	void theRageIsReadLiveAtEveryRequest() {
		Rig g = new Rig(0);
		g.fx.toBase(R);
		assertEquals(RejectReason.NOT_ENOUGH_RAGE, g.fx.tr(ZanpakutoState.SHIKAI, R).reason());
		g.rage[0] = 500;
		g.fx.adv(3);
		assertTrue(g.fx.tr(ZanpakutoState.SHIKAI, R).ok());
	}

	// ------------------------------------------------------------------ kido and the sword leaving the hand

	@Test
	void kidoWorksInEveryStateRegardlessOfRageAndSword() {
		Rig g = new Rig(1000);
		assertTrue(g.fx.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.KEY, ++g.fx.seq)).ok(), "SEALED");
		g.fx.adv(300);
		g.fx.toBase(R);
		g.rage[0] = 0; // empty Rage: kido still fine
		assertTrue(g.fx.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.KEY, ++g.fx.seq)).ok(), "BASE");
		g.rage[0] = 1000;
		g.fx.adv(300);
		g.fx.tr(ZanpakutoState.SHIKAI, R);
		g.fx.adv(g.fx.cfg.transitionLockTicks() + 1);
		g.fx.sm.onHandChanged(CharacterId.NONE);
		assertTrue(g.fx.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.KEY, ++g.fx.seq)).ok(), "SHIKAI, sword away");
		g.fx.adv(300);
		g.fx.toBankai(R);
		g.fx.adv(g.fx.cfg.transitionLockTicks() + 1);
		assertEquals(ZanpakutoState.BANKAI, g.fx.sm.state());
		assertTrue(g.fx.sm.castKido(new KidoRequest(KidoId.SAI, RequestSource.KEY, ++g.fx.seq)).ok(), "BANKAI");
	}

	@Test
	void changingTheSlotKeepsTheStateAndTheRunningEffects() {
		Fx f = new Fx().toShikai(R);
		assertTrue(f.cast(AbilityId.SHIRAFUNE, R).ok());
		long serial = f.sm.castSerial();
		List<StateEvent> ev = f.sm.onHandChanged(CharacterId.NONE);
		ev = new java.util.ArrayList<>(ev);
		ev.addAll(f.adv(200));
		assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
		assertEquals(serial, f.sm.castSerial(), "the cast serial moves only when effects must be dropped");
		assertFalse(Fx.has(ev, StateEvent.CancelEffects.class));
		assertFalse(Fx.has(ev, StateEvent.RollbackTempBlocks.class));
		assertTrue(Fx.events(ev, StateEvent.StateChanged.class).isEmpty());
	}
}
