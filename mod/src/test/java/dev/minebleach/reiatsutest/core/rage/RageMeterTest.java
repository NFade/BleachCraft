package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RageMeterTest {
	// round 5 numbers: max 1000; taken 15/hp cap 250; dealt 10/hp cap 150; parry 300; passive +5 every 10 ticks (1.0/s) for 120 ticks
	// after the last hit; drain after 160 ticks, every 20 ticks lose 5 (0.5/s); shikai costs 400, bankai 800
	// R11 T3: the defaults changed (see RageR11Test); this class pins the mechanics with the round 5 numbers, written out explicitly
	private final RageConfig cfg = new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 800);

	private RageMeter meter() {
		return new RageMeter(cfg);
	}

	@Test
	void theRound5NumbersAreStillExpressible() {
		assertEquals(1000, cfg.maxTenths());
		assertEquals(15, cfg.damageTenthsPerHp());
		assertEquals(250, cfg.maxGainPerHitTenths());
		assertEquals(10, cfg.dealtTenthsPerHp());
		assertEquals(150, cfg.maxDealtGainPerHitTenths());
		assertEquals(300, cfg.parryGainTenths());
		assertEquals(5, cfg.passiveTenths());
		assertEquals(10, cfg.passiveIntervalTicks());
		assertEquals(120, cfg.passiveWindowTicks());
		assertEquals(160, cfg.decayDelayTicks());
		assertEquals(20, cfg.decayIntervalTicks());
		assertEquals(5, cfg.decayTenths());
		assertEquals(400, cfg.shikaiCostTenths());
		assertEquals(800, cfg.bankaiCostTenths());
		assertEquals(0, cfg.releaseCostTenths(false), "R14: shikai is free whatever the field says");
		assertEquals(800, cfg.releaseCostTenths(true));
	}

	@Test
	void startsEmpty() {
		RageMeter m = meter();
		assertEquals(0, m.value());
		assertEquals(1000, m.state().max());
	}

	@Test
	void damageTakenGivesOnePointFiveOfTheDamage() {
		RageMeter m = meter();
		assertEquals(90, m.onDamageTaken(6.0F, 10)); // 6 hp * 1.5 points = 9.0 points = 90 tenths
		assertEquals(90, m.value());
		assertEquals(30, m.onDamageTaken(2.0F, 11));
	}

	@Test
	void damageTakenGainIsCappedPerHit() {
		RageMeter m = meter();
		assertEquals(250, m.onDamageTaken(40.0F, 0));
		assertEquals(250, m.onDamageTaken(20.0F, 1)); // 300 raw, capped at 25 points
		assertEquals(500, m.value());
	}

	@Test
	void damageDealtGivesOnePointPerHp() {
		RageMeter m = meter();
		assertEquals(60, m.onDamageDealt(6.0F, 10)); // 6 hp * 1.0 point
		assertEquals(10, m.onDamageDealt(1.0F, 11));
		assertEquals(70, m.value());
	}

	@Test
	void damageDealtGainIsCappedPerHit() {
		RageMeter m = meter();
		assertEquals(150, m.onDamageDealt(40.0F, 0)); // 15 points per hit at most
		assertEquals(150, m.onDamageDealt(15.0F, 1)); // exactly the cap value
		assertEquals(150, m.onDamageDealt(16.0F, 2));
		assertEquals(450, m.value());
	}

	@Test
	void sixHpSwordHitOnAMobGivesAVisibleAmount() {
		RageMeter m = meter();
		m.onDamageDealt(6.0F, 0);
		assertEquals(60, m.value());
		m.onDamageDealt(20.0F, 1);
		assertEquals(210, m.value());
	}

	@Test
	void aShikaiCostsAboutSevenSwordHitsAndABankaiAboutFourteen() {
		RageMeter m = meter();
		int hits = 0;
		while (!m.canSpend(cfg.shikaiCostTenths())) {
			m.onDamageDealt(6.0F, 0);
			hits++;
		}
		assertEquals(7, hits); // 7 * 6.0 = 42 >= 40
		while (!m.canSpend(cfg.bankaiCostTenths())) {
			m.onDamageDealt(6.0F, 0);
			hits++;
		}
		assertEquals(14, hits); // 14 * 6.0 = 84 >= 80
	}

	@Test
	void zeroNegativeAndNanDamageGiveNothing() {
		RageMeter m = meter();
		assertEquals(0, m.onDamageTaken(0.0F, 0));
		assertEquals(0, m.onDamageTaken(-3.0F, 0));
		assertEquals(0, m.onDamageTaken(Float.NaN, 0));
		assertEquals(0, m.onDamageDealt(0.0F, 0));
		assertEquals(0, m.onDamageDealt(-3.0F, 0));
		assertEquals(0, m.onDamageDealt(Float.NaN, 0));
		assertEquals(0, m.value());
	}

	@Test
	void parryGivesFixedAmount() {
		RageMeter m = meter();
		assertEquals(300, m.onParry(5));
		assertEquals(300, m.onParry(6));
		assertEquals(600, m.value());
	}

	@Test
	void clampedAtMaxAndReportsActualGain() {
		RageMeter m = meter();
		for (int i = 0; i < 3; i++) {
			m.onParry(i);
		}
		assertEquals(900, m.value());
		assertEquals(100, m.onDamageTaken(40.0F, 7)); // only 100 fit
		assertTrue(m.state().isFull());
		assertEquals(0, m.onParry(9));
		assertEquals(0, m.onDamageDealt(10.0F, 9));
		assertEquals(1000, m.value());
	}

	// ------------------------------------------------------------------ passive inflow

	@Test
	void passiveInflowIsOnePointPerSecondForSixSecondsAfterAHit() {
		RageMeter m = meter();
		m.onParry(0); // 300
		for (int t = 0; t < 10; t++) {
			assertFalse(m.tick(t), "tick " + t);
		}
		assertTrue(m.tick(10));
		assertEquals(305, m.value());
		for (int t = 11; t < 20; t++) {
			assertFalse(m.tick(t));
		}
		assertTrue(m.tick(20));
		assertEquals(310, m.value());
		for (int t = 21; t < 120; t++) {
			m.tick(t);
		}
		assertEquals(355, m.value()); // steps at 10, 20 ... 110 = 11 steps; the window of 6 s ends at tick 120
		assertFalse(m.tick(120));
		assertFalse(m.tick(150));
		assertEquals(355, m.value());
	}

	@Test
	void anotherHitExtendsTheWindowWithoutRestartingTheSteps() {
		RageMeter m = meter();
		m.onParry(0);
		m.tick(49);
		assertEquals(320, m.value()); // steps 10, 20, 30, 40
		m.onParry(50); // +300, window now until tick 170, the steps keep their rhythm
		assertEquals(620, m.value());
		m.tick(169);
		assertEquals(620 + 5 * 12, m.value()); // steps 50, 60 ... 160 = 12 steps
		assertFalse(m.tick(170));
	}

	@Test
	void passiveInflowNeverRunsWithoutAHitAndStopsAtAFullBar() {
		RageMeter m = meter();
		assertFalse(m.tick(500));
		assertEquals(0, m.value());
		for (int i = 0; i < 4; i++) {
			m.onParry(0);
		}
		assertEquals(1000, m.value());
		assertFalse(m.tick(100));
		assertEquals(1000, m.value());
	}

	@Test
	void aTakenHitStartsThePassiveInflowToo() {
		RageMeter m = meter();
		m.onDamageTaken(2.0F, 100); // +30
		assertTrue(m.tick(110));
		assertEquals(35, m.value());
	}

	// ------------------------------------------------------------------ drain

	@Test
	void noDrainInsideTheDelay() {
		RageMeter m = meter();
		m.onParry(0);
		int last = m.value();
		for (int t = 0; t < 160; t++) {
			m.tick(t);
			assertTrue(m.value() >= last, "tick " + t);
			last = m.value();
		}
		assertEquals(355, m.value());
	}

	@Test
	void drainsStepwiseAfterTheEightSecondDelay() {
		RageMeter m = meter();
		m.onParry(0);
		for (int t = 0; t < 160; t++) {
			m.tick(t);
		}
		assertTrue(m.tick(160));
		assertEquals(350, m.value());
		for (int t = 161; t < 180; t++) {
			assertFalse(m.tick(t));
		}
		assertTrue(m.tick(180));
		assertEquals(345, m.value()); // 0.5 per second
	}

	@Test
	void combatResetsTheDrainTimer() {
		RageMeter m = meter();
		m.onParry(0);
		m.tick(160);
		assertEquals(350, m.value()); // 300 + 11 passive steps - 5
		m.onDamageTaken(1.0F, 170); // +15, timer restarts at 170, a new fight starts the passive inflow at 180
		assertEquals(365, m.value());
		m.tick(329);
		assertEquals(365 + 55, m.value()); // passive steps 180 ... 280
		assertTrue(m.tick(330));
		assertEquals(365 + 55 - 5, m.value());
	}

	@Test
	void dealingDamageAlsoCountsAsCombat() {
		RageMeter m = meter();
		m.onParry(0);
		m.tick(160);
		assertEquals(350, m.value());
		m.onDamageDealt(1.5F, 170); // +15
		assertEquals(365, m.value());
		m.tick(329);
		assertEquals(420, m.value());
		assertTrue(m.tick(330));
		assertEquals(415, m.value());
	}

	@Test
	void hitAtFullBarStillCountsAsCombat() {
		RageMeter m = meter();
		for (int i = 0; i < 4; i++) {
			m.onParry(0);
		}
		assertTrue(m.state().isFull());
		m.onParry(90);
		assertFalse(m.tick(200)); // would have drained at 160
		assertFalse(m.tick(249));
		assertTrue(m.tick(250));
		assertEquals(995, m.value());
	}

	@Test
	void dealtHitAtFullBarStillCountsAsCombat() {
		RageMeter m = meter();
		for (int i = 0; i < 4; i++) {
			m.onParry(0);
		}
		m.onDamageDealt(5.0F, 90);
		assertFalse(m.tick(200));
		assertTrue(m.tick(250));
	}

	@Test
	void largeTickJumpCatchesUpAndStopsAtZero() {
		RageMeter m = meter();
		m.onParry(0);
		assertTrue(m.tick(160 + 20 * 5)); // passive 11 steps = +55, drain steps at 160, 180 ... 260 = 6 steps = -30
		assertEquals(300 + 55 - 30, m.value());
		assertTrue(m.tick(1_000_000));
		assertEquals(0, m.value());
		assertFalse(m.tick(1_000_001));
	}

	// ------------------------------------------------------------------ spend, dev, reset

	@Test
	void spendExtensionPoint() {
		RageMeter m = meter();
		m.onParry(0);
		assertTrue(m.canSpend(300));
		assertFalse(m.canSpend(301));
		assertFalse(m.spend(301));
		assertEquals(300, m.value());
		assertTrue(m.spend(300)); // may empty the bar, unlike reiatsu
		assertEquals(0, m.value());
		assertTrue(m.canSpend(0));
		assertTrue(m.spend(0));
		assertTrue(m.spend(-5));
		assertEquals(0, m.value());
	}

	@Test
	void theReleaseCostIsPaidFromTheBar() {
		RageMeter m = meter();
		m.onParry(0);
		m.onParry(0); // 600
		assertTrue(m.spend(cfg.shikaiCostTenths()));
		assertEquals(200, m.value());
		assertFalse(m.canSpend(cfg.shikaiCostTenths()));
	}

	@Test
	void devSetClampsHoldsTheDrainAndStartsNoPassiveInflow() {
		RageMeter m = meter();
		m.devSet(5000, 10);
		assertEquals(1000, m.value());
		m.devSet(-4, 10);
		assertEquals(0, m.value());
		m.devSet(500, 10);
		assertFalse(m.tick(100));
		assertFalse(m.tick(169));
		assertEquals(500, m.value(), "the value set by the command stays put");
		assertTrue(m.tick(170));
		assertEquals(495, m.value());
	}

	@Test
	void resetEmptiesTheBar() {
		RageMeter m = meter();
		m.onParry(3);
		m.reset();
		assertEquals(0, m.value());
		assertFalse(m.tick(500));
		assertEquals(0, m.value());
	}

	@Test
	void zeroDecayConfigNeverDrains() {
		RageMeter m = new RageMeter(new RageConfig(1000, 15, 250, 300, 10, 150, 0, 20, 0, 5, 10, 120, 400, 800));
		m.onParry(0);
		m.tick(10_000);
		assertEquals(355, m.value()); // only the passive steps
		assertFalse(m.tick(20_000));
		assertEquals(355, m.value());
	}

	@Test
	void configRejectsBadValues() {
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(0, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, -1, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, -1, 150, 160, 20, 5, 5, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, -1, 160, 20, 5, 5, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, 150, 160, 0, 5, 5, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, 150, -1, 20, 5, 5, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 0, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, -1, 10, 120, 400, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, -1, 800));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 1001));
	}

	@Test
	void stateClampsAndIsOverflowSafe() {
		assertEquals(0, new RageState(-5, 100).value());
		assertEquals(100, new RageState(500, 100).value());
		assertEquals(100, new RageState(90, 100).plus(Integer.MAX_VALUE).value());
		assertEquals(0, new RageState(90, 100).plus(Integer.MIN_VALUE).value());
		assertFalse(new RageState(0, 0).isFull());
	}
}
