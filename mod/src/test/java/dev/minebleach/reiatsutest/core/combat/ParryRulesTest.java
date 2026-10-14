package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** R11 T2: the parry reiatsu. */
class ParryRulesTest {
	@Test
	void theFirstParryPays() {
		assertEquals(100, ParryRules.reiatsuTenths(100, 500, ParryRules.NEVER, 10));
	}

	@Test
	void notTwiceInsideTheGap() {
		assertEquals(0, ParryRules.reiatsuTenths(100, 505, 500, 10));
		assertEquals(0, ParryRules.reiatsuTenths(100, 509, 500, 10));
		assertEquals(100, ParryRules.reiatsuTenths(100, 510, 500, 10));
	}

	@Test
	void noRewardMeansNothing() {
		assertEquals(0, ParryRules.reiatsuTenths(0, 500, ParryRules.NEVER, 10));
		assertEquals(0, ParryRules.reiatsuTenths(-10, 500, ParryRules.NEVER, 10));
	}

	@Test
	void aTickCounterThatRestartedPays() {
		assertEquals(100, ParryRules.reiatsuTenths(100, 5, 90000, 10));
	}
}
