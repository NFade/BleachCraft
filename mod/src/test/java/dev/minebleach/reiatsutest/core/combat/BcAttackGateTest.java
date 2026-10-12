package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import org.junit.jupiter.api.Test;

/** BC5 decision 1: the Better Combat strike is off while the sword is sheathed or a block is held, on for a drawn sword. */
class BcAttackGateTest {
	@Test
	void sheathedSwordHasNoBetterCombatStrike() {
		assertTrue(BcAttackGate.attacksDisabled(true, ZanpakutoState.SEALED, false));
	}

	@Test
	void drawnSwordStrikesUnlessBlocking() {
		for (ZanpakutoState s : new ZanpakutoState[] {ZanpakutoState.BASE, ZanpakutoState.SHIKAI, ZanpakutoState.BANKAI}) {
			assertFalse(BcAttackGate.attacksDisabled(true, s, false), s + " must strike");
			assertTrue(BcAttackGate.attacksDisabled(true, s, true), s + " must not strike while blocking");
		}
	}

	@Test
	void flagIsNeverSetForAnythingThatIsNotAZanpakuto() {
		for (ZanpakutoState s : ZanpakutoState.values()) {
			assertFalse(BcAttackGate.attacksDisabled(false, s, false));
			assertFalse(BcAttackGate.attacksDisabled(false, s, true));
		}
	}

	/** tp_pose: the Better Combat katana stance is off for every zanpakuto state (the pose layer draws the stance), kept for other items. */
	@Test
	void katanaStanceIsOffForEveryZanpakutoState() {
		for (ZanpakutoState s : ZanpakutoState.values()) {
			assertTrue(BcAttackGate.poseSuppressed(true, s, false), s + " has no Better Combat stance");
			assertTrue(BcAttackGate.poseSuppressed(true, s, true), s + " has no Better Combat stance while blocking");
			assertFalse(BcAttackGate.poseSuppressed(false, s, false), "other items keep what Better Combat decides");
		}
	}
}
