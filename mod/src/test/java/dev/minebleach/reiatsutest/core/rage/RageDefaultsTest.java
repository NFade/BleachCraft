package dev.minebleach.reiatsutest.core.rage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The live Rage defaults after the gin-uryu override (user request 2026-10-14): 3.0 per hit, 5.0 per parry, drain after 30 s. */
class RageDefaultsTest {
	private final RageConfig cfg = RageConfig.defaults();

	private RageMeter meter() {
		return new RageMeter(cfg);
	}

	@Test
	void theNumbersOfTheRequest() {
		assertEquals(50, cfg.parryGainTenths(), "a parry gives 5.0");
		assertEquals(600, cfg.decayDelayTicks(), "the drain starts after 30 s out of combat");
	}

	@Test
	void everyMeleeHitGivesThreePointsWhateverTheDamage() {
		for (float hp : new float[] {0.5F, 1.0F, 6.0F, 40.0F}) {
			RageMeter m = meter();
			assertEquals(30, m.onDamageDealt(hp, 7, false, 100), hp + " hp on a player");
		}
	}

	@Test
	void mobsPayTheSameAndOneTargetNeverRunsDry() {
		RageMeter m = meter();
		int total = 0;
		for (int i = 0; i < 12; i++) {
			total += m.onDamageDealt(5.0F, 9, true, 100 + i * 12);
		}
		assertEquals(12 * 30, total, "twelve hits on one mob inside 10 s, all pay 3.0");
	}

	@Test
	void aParryGivesFivePoints() {
		assertEquals(50, meter().onParry(100));
	}

	@Test
	void nothingDrainsBeforeThirtySecondsOutOfCombat() {
		RageMeter m = meter();
		m.devSet(500, 100);
		m.onDamageDealt(5.0F, 3, false, 100);
		int v = m.value();
		m.tick(100 + cfg.decayDelayTicks() - 1);
		assertEquals(v, m.value(), "held one tick before 30 s");
		m.tick(100 + cfg.decayDelayTicks() + 2 * cfg.decayIntervalTicks());
		assertEquals(true, m.value() < v, "the bar drains after the delay");
	}
}
