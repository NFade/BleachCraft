package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BarrierRulesTest {
	@Test
	void damageIsCutByTheConfiguredPercent() {
		assertEquals(2.0F, BarrierRules.reduce(10.0F, 80), 1.0E-5F);
		assertEquals(10.0F, BarrierRules.reduce(10.0F, 0), 1.0E-5F);
		assertEquals(0.0F, BarrierRules.reduce(10.0F, 100), 1.0E-5F);
		assertEquals(10.0F, BarrierRules.reduce(10.0F, -5), 1.0E-5F, "a negative knob does nothing");
		assertEquals(0.0F, BarrierRules.reduce(10.0F, 250), 1.0E-5F, "more than 100 percent is clamped");
	}

	@Test
	void everyControlEffectIsRefusedWhileImmune() {
		for (String id : new String[] {"slowness", "mining_fatigue", "weakness", "nausea", "blindness", "darkness", "levitation"}) {
			assertTrue(BarrierRules.blocksEffect(true, id), id);
			assertFalse(BarrierRules.blocksEffect(false, id), id + " lands without the barrier");
		}
	}

	@Test
	void damageAndBuffEffectsAreNotControl() {
		for (String id : new String[] {"poison", "wither", "hunger", "speed", "strength", "regeneration", "resistance", "fire_resistance",
				"instant_damage", "slow_falling", "night_vision", "absorption", "glowing"}) {
			assertFalse(BarrierRules.blocksEffect(true, id), id);
		}
		assertFalse(BarrierRules.blocksEffect(true, null));
	}

	@Test
	void freezingTicksCanOnlyDrainUnderTheBarrier() {
		assertTrue(BarrierRules.blocksFrozenTicks(true, 0, 140), "ice cannot be raised");
		assertTrue(BarrierRules.blocksFrozenTicks(true, 30, 31));
		assertFalse(BarrierRules.blocksFrozenTicks(true, 30, 30));
		assertFalse(BarrierRules.blocksFrozenTicks(true, 30, 28), "the old ice melts away as usual");
		assertFalse(BarrierRules.blocksFrozenTicks(true, 30, 0));
		assertFalse(BarrierRules.blocksFrozenTicks(false, 0, 140), "no barrier: powder snow and Rukia ice work");
	}

	@Test
	void knockbackFollowsTheImmunity() {
		assertTrue(BarrierRules.blocksKnockback(true));
		assertFalse(BarrierRules.blocksKnockback(false));
	}
}
