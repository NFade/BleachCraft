package dev.minebleach.reiatsutest.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.registry.data.CooldownData;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The shunpo cooldown travels in the cooldown sync under its own code, next to abilities and kido (side HUD cell). */
class CooldownDataShunpoTest {
	@Test
	void shunpoCodeCollidesWithNoAbilityOrKido() {
		for (AbilityId a : AbilityId.values()) {
			assertFalse(a.code == CooldownData.SHUNPO_CODE);
		}
		for (KidoId k : KidoId.values()) {
			assertFalse(k.code == CooldownData.SHUNPO_CODE);
		}
	}

	@Test
	void shunpoRemainingRoundTripsAndKeepsTheOthers() {
		CooldownData d = CooldownData.of(Map.of(AbilityId.TSUKISHIRO, 40), Map.of(KidoId.SAI, 70), 33);
		assertEquals(33, d.shunpoRemaining());
		assertEquals(40, d.remaining(AbilityId.TSUKISHIRO));
		assertEquals(70, d.remaining(KidoId.SAI));
	}

	@Test
	void readyShunpoAddsNoEntry() {
		CooldownData d = CooldownData.of(Map.of(), Map.of(), 0);
		assertEquals(0, d.shunpoRemaining());
		assertFalse(d.remainingTicks().containsKey(CooldownData.SHUNPO_CODE));
	}
}
