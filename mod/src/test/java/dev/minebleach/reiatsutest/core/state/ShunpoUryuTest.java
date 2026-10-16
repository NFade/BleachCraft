package dev.minebleach.reiatsutest.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import org.junit.jupiter.api.Test;

/** Uryu's shunpo goes 2 blocks farther; the numbers of Rukia and Byakuya are untouched. */
class ShunpoUryuTest {
	private static final ShunpoSpec SP = BalanceConfig.defaults().shunpo();

	@Test
	void uryuGetsTwoMoreBlocksAndALongerCooldownAndNothingElseChanges() {
		ShunpoSpec u = SP.forCharacter(CharacterId.URYU);
		assertEquals(SP.distance() + 2.0, u.distance(), 1e-9);
		assertEquals(UryuBalance.SHUNPO_BONUS, 2.0, 0.0);
		assertEquals(SP.costTenths(), u.costTenths());
		assertEquals(UryuBalance.SHUNPO_COOLDOWN_BONUS, 20, "R14: 1.0 s longer, so a chaser can catch up");
		assertEquals(SP.cooldownTicks() + UryuBalance.SHUNPO_COOLDOWN_BONUS, u.cooldownTicks());
		assertEquals(SP.minDistance(), u.minDistance(), 1e-9);
		assertEquals(SP.step(), u.step(), 1e-9);
		assertEquals(SP.maxDrop(), u.maxDrop());
	}

	@Test
	void everyoneElseKeepsTheSameSpec() {
		for (CharacterId c : CharacterId.values()) {
			if (c != CharacterId.URYU) {
				assertSame(SP, SP.forCharacter(c), c.name());
			}
		}
	}
}
