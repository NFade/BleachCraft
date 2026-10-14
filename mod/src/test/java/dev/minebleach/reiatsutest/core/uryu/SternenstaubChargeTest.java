package dev.minebleach.reiatsutest.core.uryu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * S-U1: the Sternenstaub got a 0.8 s charge. The cast is aimed (and its Focus read) at the RELEASE, 16 ticks after the key press, exactly like
 * the Licht Regen; damage, cost and cooldown did not change. A charge whose Focus was lost fizzles with half the cooldown. S-U4: the cast
 * start has its own effect id.
 */
class SternenstaubChargeTest {
	private static final BalanceConfig CFG = BalanceConfig.defaults();

	private static AbilitySpec star() {
		return CFG.spec(AbilityId.STERNENSTAUB);
	}

	@Test
	void theCastResolvesAtTheReleaseSixteenTicksAfterTheKeyPress() {
		AbilitySpec s = star();
		assertEquals(List.of(16), s.phaseOffsets(), "one phase at 0.8 s");
		assertEquals(16, UryuBalance.STAR_CHARGE_TICKS);
		assertEquals(0.8, UryuBalance.STAR_CHARGE_TICKS / 20.0, 1e-9);
		assertTrue(s.aimsAtRelease(), "aim and Focus are taken at the release, like Licht Regen");
		assertEquals(0, s.releasePhase());
		assertEquals(1.0, s.num(AbilityParams.AIM_AT_RELEASE));
	}

	@Test
	void costCooldownEffectAndDamageAreUnchanged() {
		AbilitySpec s = star();
		assertEquals(500, s.costTenths(), "50 reiatsu");
		assertEquals(800, s.cooldownTicks(), "40 s");
		assertEquals(77, s.effectId());
		assertTrue(s.enabled());
		assertEquals(20.0f, Sternenstaub.damage(0));
		assertEquals(12.0f, Sternenstaub.damage(1));
		assertEquals(40.0, UryuBalance.STAR_LENGTH);
		assertEquals(2.0, UryuBalance.STAR_HALF_WIDTH * 2.0);
		assertEquals(3, UryuBalance.STAR_FOCUS);
	}

	@Test
	void theOtherUryuAbilitiesKeepTheirTimings() {
		assertEquals(List.of(0), CFG.spec(AbilityId.HEILIG_PFEIL).phaseOffsets());
		assertEquals(List.of(0), CFG.spec(AbilityId.SPRENGER).phaseOffsets());
		assertEquals(List.of(0), CFG.spec(AbilityId.SEELE_SCHNEIDER).phaseOffsets());
		assertEquals(List.of(0), CFG.spec(AbilityId.WING_BARRAGE).phaseOffsets());
		AbilitySpec rain = CFG.spec(AbilityId.LICHT_REGEN);
		assertEquals(List.of(12), rain.phaseOffsets(), "Licht Regen: 0.6 s");
		assertTrue(rain.aimsAtRelease());
		for (AbilityId a : new AbilityId[] {AbilityId.HEILIG_PFEIL, AbilityId.SPRENGER, AbilityId.SEELE_SCHNEIDER, AbilityId.WING_BARRAGE}) {
			assertFalse(CFG.spec(a).aimsAtRelease(), a + " is aimed at the key press");
		}
	}

	@Test
	void theReleaseNeedsTheFullFocusAgain() {
		assertFalse(Sternenstaub.releases(0));
		assertFalse(Sternenstaub.releases(1));
		assertFalse(Sternenstaub.releases(2), "moved during the charge: Focus dropped");
		assertTrue(Sternenstaub.releases(3));
		assertTrue(Sternenstaub.releases(4), "more than the cap never hurts");
	}

	@Test
	void aFizzledChargeKeepsHalfTheCooldown() {
		assertEquals(0.5, UryuBalance.STAR_FIZZLE_COOLDOWN_SHARE);
		assertEquals(400, Sternenstaub.fizzleCooldown(star().cooldownTicks()), "half of 40 s = 20 s");
		assertEquals(0, Sternenstaub.fizzleCooldown(0));
		assertEquals(0, Sternenstaub.fizzleCooldown(-5));
		assertEquals(50, Sternenstaub.fizzleCooldown(100));
		assertTrue(Sternenstaub.fizzleCooldown(star().cooldownTicks()) < star().cooldownTicks() - UryuBalance.STAR_CHARGE_TICKS,
				"the fizzle must really be shorter than the full cooldown that is still running at the release");
	}

	@Test
	void theCastStartHasItsOwnEffectId() {
		assertEquals(78, EffectIds.URYU_CAST_START);
		for (AbilityId a : AbilityId.values()) {
			assertTrue(CFG.spec(a).effectId() != EffectIds.URYU_CAST_START, a + " must not use the cast start id");
		}
		for (int id : new int[] {EffectIds.URYU_SHIKAI_RELEASE, EffectIds.URYU_BANKAI_RELEASE, EffectIds.GIN_SHIKAI_RELEASE, EffectIds.GIN_BANKAI_RELEASE}) {
			assertTrue(id != EffectIds.URYU_CAST_START);
		}
		assertTrue(EffectIds.URYU_CAST_START > CFG.spec(AbilityId.STERNENSTAUB).effectId(), "the Uryu range 70 to 79 is kept");
		assertTrue(EffectIds.URYU_CAST_START < 80);
	}
}
