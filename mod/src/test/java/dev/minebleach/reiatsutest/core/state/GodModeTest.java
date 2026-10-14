package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.rage.RageConfig;

/** {@code /reiatsu dev god on|off}: infinite reiatsu, no Rage needed. */
class GodModeTest {
	private static final CharacterId R = CharacterId.RUKIA;

	@org.junit.jupiter.api.Test
	void godModeFillsTheBarAndSkipsTheRageGate() {
		Fx fx = new Fx(100); // 10 of 100 points
		fx.sm.setRage(RageConfig.defaults(), () -> 0); // no Rage at all
		fx.sm.setGodMode(true);
		assertTrue(fx.sm.godMode());
		assertTrue(fx.sm.rageFree());
		assertEquals(fx.cfg.maxTenths(), fx.sm.reiatsu().value());
		fx.toBase(R);
		assertTrue(fx.tr(ZanpakutoState.SHIKAI, R).ok(), "shikai without Rage and with a spent bar");
		assertEquals(fx.cfg.maxTenths(), fx.sm.reiatsu().value(), "release cost was not paid");
	}

	@org.junit.jupiter.api.Test
	void godModeRefillsEveryTickAndSurvivesTheDrain() {
		Fx fx = new Fx();
		fx.sm.setGodMode(true);
		fx.toShikai(R);
		fx.adv(200);
		assertEquals(ZanpakutoState.SHIKAI, fx.sm.state(), "the bar never reaches zero, so no REIATSU_ZERO seal");
		assertEquals(fx.cfg.maxTenths(), fx.sm.reiatsu().value());
	}

	@org.junit.jupiter.api.Test
	void switchingOffPaysAgain() {
		Fx fx = new Fx();
		fx.sm.setGodMode(true);
		fx.sm.setGodMode(false);
		assertFalse(fx.sm.godMode());
		assertFalse(fx.sm.rageFree());
		fx.toBase(R);
		assertTrue(fx.tr(ZanpakutoState.SHIKAI, R).ok());
		assertTrue(fx.sm.reiatsu().value() < fx.cfg.maxTenths(), "the release cost is paid again");
	}
}
