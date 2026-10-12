package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mech2 task 2: every way a bankai can end produces exactly one {@link StateEvent.BankaiEnded} (right after the StateChanged), which is
 * what the server glue turns into the ONE cleanup (anchors, frost field, scheduled phases, client message). Ends of other states do not.
 */
class BankaiEndTest {
	private static final CharacterId R = CharacterId.RUKIA;
	private static final CharacterId B = CharacterId.BYAKUYA;

	private static Fx inBankai(CharacterId c) {
		Fx f = new Fx().toShikai(c).toBankai(c);
		f.adv(44);
		return f;
	}

	private static void assertOneEnd(List<StateEvent> ev, CharacterId who, Trigger why) {
		List<StateEvent.BankaiEnded> ends = Fx.events(ev, StateEvent.BankaiEnded.class);
		assertEquals(1, ends.size(), "exactly one BankaiEnded in " + ev);
		assertEquals(who, ends.get(0).who());
		assertEquals(why, ends.get(0).trigger());
		int change = -1;
		int end = -1;
		for (int i = 0; i < ev.size(); i++) {
			if (ev.get(i) instanceof StateEvent.StateChanged) {
				change = i;
			}
			if (ev.get(i) instanceof StateEvent.BankaiEnded) {
				end = i;
			}
		}
		assertEquals(change + 1, end, "BankaiEnded follows the StateChanged directly");
	}

	@Test
	void theTimerEndsItIntoShikai() {
		for (CharacterId c : new CharacterId[] {R, B}) {
			Fx f = inBankai(c);
			f.adv(899 - 44);
			List<StateEvent> ev = f.adv(1);
			assertEquals(ZanpakutoState.SHIKAI, f.sm.state());
			assertOneEnd(ev, c, Trigger.BANKAI_CAP);
		}
	}

	@Test
	void sealEndsIt() {
		for (CharacterId c : new CharacterId[] {R, B}) {
			Fx f = inBankai(c);
			TransitionResult r = f.tr(ZanpakutoState.SEALED, c);
			assertTrue(r.ok());
			assertOneEnd(r.events(), c, Trigger.REQUEST);
		}
	}

	@Test
	void sealWorksWithoutTheSwordAndStillEndsIt() {
		Fx f = inBankai(R);
		TransitionResult r = f.tr(ZanpakutoState.SEALED, CharacterId.NONE);
		assertTrue(r.ok());
		assertOneEnd(r.events(), R, Trigger.REQUEST);
	}

	@Test
	void deathEndsIt() {
		Fx f = inBankai(R);
		assertOneEnd(f.sm.onDeath(), R, Trigger.DEATH);
	}

	@Test
	void logoutEndsIt() {
		Fx f = inBankai(B);
		assertOneEnd(f.sm.onLogout(), B, Trigger.LOGOUT);
	}

	@Test
	void dimensionChangeEndsIt() {
		Fx f = inBankai(R);
		assertOneEnd(f.sm.onDimensionChange(), R, Trigger.DIMENSION_CHANGE);
	}

	@Test
	void spectatorEndsIt() {
		Fx f = inBankai(R);
		assertOneEnd(f.sm.setSpectator(true), R, Trigger.DIMENSION_CHANGE);
	}

	@Test
	void devResetAndDevStateEndIt() {
		Fx f = inBankai(R);
		assertOneEnd(f.sm.devSetState(ZanpakutoState.SEALED, CharacterId.NONE), R, Trigger.DEV_COMMAND);
		Fx g = inBankai(B);
		assertOneEnd(g.sm.devSetState(ZanpakutoState.SHIKAI, B), B, Trigger.DEV_COMMAND);
	}

	@Test
	void endingTwiceNeverProducesASecondEnd() {
		Fx f = inBankai(R);
		assertOneEnd(f.sm.onDeath(), R, Trigger.DEATH);
		assertFalse(Fx.has(f.sm.onDeath(), StateEvent.BankaiEnded.class));
		assertFalse(Fx.has(f.sm.onLogout(), StateEvent.BankaiEnded.class));
		assertFalse(Fx.has(f.sm.onDimensionChange(), StateEvent.BankaiEnded.class));
	}

	@Test
	void shikaiAndBaseEndsAreNotBankaiEnds() {
		Fx f = new Fx().toShikai(R);
		assertFalse(Fx.has(f.sm.onDeath(), StateEvent.BankaiEnded.class));
		Fx g = new Fx().toShikai(B);
		TransitionResult r = g.tr(ZanpakutoState.SEALED, B);
		assertTrue(r.ok());
		assertFalse(Fx.has(r.events(), StateEvent.BankaiEnded.class));
		Fx h = new Fx().toBase(R);
		assertFalse(Fx.has(h.sm.onLogout(), StateEvent.BankaiEnded.class));
	}

	@Test
	void theTimerEndKeepsTheAnchorlessCleanupRulesTheOtherEndsHave() {
		// the timer path used to emit no CancelEffects (it keeps the cast serial so a running ability lands); the cleanup event is what
		// the glue uses to drop the BANKAI ability phases, so it must be there even though the state stays released
		Fx f = inBankai(R);
		f.adv(899 - 44);
		List<StateEvent> ev = f.adv(1);
		assertFalse(Fx.has(ev, StateEvent.CancelEffects.class), "the timer end does not bump the cast serial");
		assertTrue(Fx.has(ev, StateEvent.BankaiEnded.class));
	}
}
