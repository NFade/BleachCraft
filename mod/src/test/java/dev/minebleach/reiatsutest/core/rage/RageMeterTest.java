package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RageMeterTest {
	private final RageConfig cfg = RageConfig.defaults(); // max 1000, 5/hp, cap 100/hit, parry 150, delay 100, every 20 lose 10

	private RageMeter meter() {
		return new RageMeter(cfg);
	}

	@Test
	void startsEmpty() {
		RageMeter m = meter();
		assertEquals(0, m.value());
		assertEquals(1000, m.state().max());
	}

	@Test
	void damageGivesPercentOfDamage() {
		RageMeter m = meter();
		assertEquals(30, m.onDamageTaken(6.0F, 10)); // 6 hp * 0.5 point = 3.0 points = 30 tenths
		assertEquals(30, m.value());
		assertEquals(3, m.onDamageTaken(0.5F, 11)); // 2.5 rounds half up to 3
	}

	@Test
	void damageGainIsCappedPerHit() {
		RageMeter m = meter();
		assertEquals(100, m.onDamageTaken(40.0F, 0));
		assertEquals(100, m.onDamageTaken(20.0F, 1)); // exactly at the cap value
		assertEquals(200, m.value());
	}

	@Test
	void zeroNegativeAndNanDamageGiveNothing() {
		RageMeter m = meter();
		assertEquals(0, m.onDamageTaken(0.0F, 0));
		assertEquals(0, m.onDamageTaken(-3.0F, 0));
		assertEquals(0, m.onDamageTaken(Float.NaN, 0));
		assertEquals(0, m.value());
	}

	@Test
	void parryGivesFixedAmount() {
		RageMeter m = meter();
		assertEquals(150, m.onParry(5));
		assertEquals(150, m.onParry(6));
		assertEquals(300, m.value());
	}

	@Test
	void clampedAtMaxAndReportsActualGain() {
		RageMeter m = meter();
		for (int i = 0; i < 6; i++) {
			m.onParry(i);
		}
		assertEquals(900, m.value());
		assertEquals(100, m.onParry(7)); // only 100 fit
		assertTrue(m.state().isFull());
		assertEquals(0, m.onParry(8));
		assertEquals(1000, m.value());
	}

	@Test
	void noDrainInsideTheDelay() {
		RageMeter m = meter();
		m.onParry(0);
		for (int t = 0; t < 100; t++) {
			assertFalse(m.tick(t), "tick " + t);
		}
		assertEquals(150, m.value());
	}

	@Test
	void drainsStepwiseAfterTheDelay() {
		RageMeter m = meter();
		m.onParry(0);
		assertTrue(m.tick(100));
		assertEquals(140, m.value());
		for (int t = 101; t < 120; t++) {
			assertFalse(m.tick(t));
		}
		assertTrue(m.tick(120));
		assertEquals(130, m.value());
	}

	@Test
	void combatResetsTheDrainTimer() {
		RageMeter m = meter();
		m.onParry(0);
		m.tick(100);
		assertEquals(140, m.value());
		m.onDamageTaken(2.0F, 110); // +10, timer restarts at 110
		assertEquals(150, m.value());
		assertFalse(m.tick(209));
		assertTrue(m.tick(210));
		assertEquals(140, m.value());
	}

	@Test
	void hitAtFullBarStillCountsAsCombat() {
		RageMeter m = meter();
		for (int i = 0; i < 7; i++) {
			m.onParry(0);
		}
		assertTrue(m.state().isFull());
		m.onParry(90);
		assertFalse(m.tick(150)); // would have drained at 100
		assertTrue(m.tick(190));
	}

	@Test
	void largeTickJumpCatchesUpAndStopsAtZero() {
		RageMeter m = meter();
		m.onParry(0);
		assertTrue(m.tick(100 + 20 * 5)); // steps at 100,120,...,200 = 6 steps = 60
		assertEquals(90, m.value());
		assertTrue(m.tick(1_000_000));
		assertEquals(0, m.value());
		assertFalse(m.tick(1_000_001));
	}

	@Test
	void spendExtensionPoint() {
		RageMeter m = meter();
		m.onParry(0);
		assertTrue(m.canSpend(150));
		assertFalse(m.canSpend(151));
		assertFalse(m.spend(151));
		assertEquals(150, m.value());
		assertTrue(m.spend(150)); // may empty the bar, unlike reiatsu
		assertEquals(0, m.value());
		assertTrue(m.canSpend(0));
		assertTrue(m.spend(0));
		assertTrue(m.spend(-5));
		assertEquals(0, m.value());
	}

	@Test
	void resetEmptiesTheBar() {
		RageMeter m = meter();
		m.onParry(3);
		m.reset();
		assertEquals(0, m.value());
		assertFalse(m.tick(500));
	}

	@Test
	void zeroDecayConfigNeverDrains() {
		RageMeter m = new RageMeter(new RageConfig(1000, 5, 100, 150, 0, 20, 0));
		m.onParry(0);
		assertFalse(m.tick(10_000));
		assertEquals(150, m.value());
	}

	@Test
	void configRejectsBadValues() {
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(0, 5, 100, 150, 100, 20, 10));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, -1, 100, 150, 100, 20, 10));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 5, 100, 150, 100, 0, 10));
		assertThrows(IllegalArgumentException.class, () -> new RageConfig(1000, 5, 100, 150, -1, 20, 10));
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
