package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.Fx;
import org.junit.jupiter.api.Test;

class BlockRulesTest {
	private static final CombatConfig CFG = CombatConfig.defaults();

	@Test
	void defaultsAreTheUserDecisions() {
		assertEquals(4, CFG.parryWindowTicks()); // 0.2 s at 20 tick/s
		assertEquals(0.5F, CFG.blockedDamageMultiplier());
	}

	@Test
	void windowIsInclusiveAtTheEndAndNeverNegative() {
		assertTrue(BlockRules.inParryWindow(0, CFG));
		assertTrue(BlockRules.inParryWindow(4, CFG));
		assertFalse(BlockRules.inParryWindow(5, CFG));
		assertFalse(BlockRules.inParryWindow(-1, CFG));
		assertFalse(BlockRules.inParryWindow(72000, CFG));
	}

	@Test
	void meleeInsideTheWindowIsParried() {
		BlockOutcome o = BlockRules.resolve(true, 2, DamageKind.MELEE, 7.0F, CFG);
		assertTrue(o.parried());
		assertEquals(0.0F, o.damage());
	}

	@Test
	void meleeAfterTheWindowIsHalved() {
		BlockOutcome o = BlockRules.resolve(true, 5, DamageKind.MELEE, 7.0F, CFG);
		assertTrue(o.blocked());
		assertEquals(3.5F, o.damage());
	}

	@Test
	void abilityDamageIsNeverParriedNorReduced() {
		for (int elapsed : new int[] {0, 3, 4, 10, 100}) {
			BlockOutcome o = BlockRules.resolve(true, elapsed, DamageKind.OTHER, 6.0F, CFG);
			assertEquals(BlockOutcome.Kind.NONE, o.kind(), "elapsed " + elapsed);
			assertEquals(6.0F, o.damage());
		}
	}

	@Test
	void notBlockingChangesNothing() {
		BlockOutcome o = BlockRules.resolve(false, 1, DamageKind.MELEE, 5.0F, CFG);
		assertEquals(BlockOutcome.Kind.NONE, o.kind());
		assertEquals(5.0F, o.damage());
	}

	@Test
	void zeroDamageIsNotAParry() {
		assertEquals(BlockOutcome.Kind.NONE, BlockRules.resolve(true, 0, DamageKind.MELEE, 0.0F, CFG).kind());
	}

	@Test
	void customWindowAndMultiplier() {
		CombatConfig c = new CombatConfig(6, 0.25F, 0.4, 0, 0);
		assertTrue(BlockRules.resolve(true, 6, DamageKind.MELEE, 8.0F, c).parried());
		BlockOutcome o = BlockRules.resolve(true, 7, DamageKind.MELEE, 8.0F, c);
		assertEquals(2.0F, o.damage());
		BlockOutcome none = BlockRules.resolve(true, 0, DamageKind.MELEE, 8.0F, new CombatConfig(0, 1.0F, 0, 0, 0));
		assertTrue(none.parried()); // window 0 still parries the very first tick
	}

	@Test
	void blockingForbidsAttackAndAbilitiesButNotShunpo() {
		assertFalse(BlockRules.allowsAttack(true));
		assertTrue(BlockRules.allowsAttack(false));
		assertFalse(BlockRules.allowsAbility(true));
		assertTrue(BlockRules.allowsAbility(false));
		assertTrue(BlockRules.allowsShunpo(true));
		assertTrue(BlockRules.allowsShunpo(false));
	}

	@Test
	void invalidConfigIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new CombatConfig(-1, 0.5F, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new CombatConfig(4, 1.5F, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new CombatConfig(4, Float.NaN, 0, 0, 0));
	}

	// ------------------------------------------------------------------ r8 item 1: block against abilities

	@Test
	void blockCutsAbilityDamageByTheReductionAndNothingElse() {
		assertEquals(7.0F, BlockRules.abilityDamage(true, 10.0F, 0.30), 1e-5F, "30 percent less");
		assertEquals(10.0F, BlockRules.abilityDamage(false, 10.0F, 0.30), "not blocking: untouched");
		assertEquals(0.0F, BlockRules.abilityDamage(true, 0.0F, 0.30));
		assertEquals(-3.0F, BlockRules.abilityDamage(true, -3.0F, 0.30), "damage <= 0 is not touched");
		assertTrue(Float.isNaN(BlockRules.abilityDamage(true, Float.NaN, 0.30)));
		assertEquals(10.0F, BlockRules.abilityDamage(true, 10.0F, 0.0), "reduction 0 changes nothing");
		assertEquals(0.0F, BlockRules.abilityDamage(true, 10.0F, 1.0), "reduction 1 stops all of it");
	}

	@Test
	void abilityBlockReductionIsClampedSoTheCutNeverPassesOneHundredPercent() {
		assertEquals(0.0F, BlockRules.abilityDamage(true, 10.0F, 5.0), "above 1 acts as 1");
		assertEquals(10.0F, BlockRules.abilityDamage(true, 10.0F, -2.0), "negative acts as 0 (never heals, never amplifies)");
		assertEquals(10.0F, BlockRules.abilityDamage(true, 10.0F, Double.NaN));
		for (double r = -1.0; r <= 2.0; r += 0.1) {
			float out = BlockRules.abilityDamage(true, 8.0F, r);
			assertTrue(out >= 0.0F && out <= 8.0F, "r=" + r + " gave " + out);
		}
	}

	@Test
	void abilityBlockDefaultIsThirtyPercentAndMeleeStaysFiftyWithAParryWindow() {
		assertEquals(0.30, BalanceConfig.defaults().abilityBlockReduction(), 1e-9);
		// melee is unchanged by the new rule: 50 percent after the window, a parry inside it
		assertEquals(3.5F, BlockRules.resolve(true, 5, DamageKind.MELEE, 7.0F, CFG).damage());
		// abilities (DamageKind.OTHER) are still untouched by resolve(): the ability cut is the separate abilityDamage rule
		assertEquals(BlockOutcome.Kind.NONE, BlockRules.resolve(true, 2, DamageKind.OTHER, 7.0F, CFG).kind());
	}

	@Test
	void blockAndBarrierMultiplyInsteadOfAdding() {
		// 10 HP ability on a blocking Byakuya inside his barrier: block keeps 70 percent, then the barrier keeps 20 percent of that
		float afterBlock = BlockRules.abilityDamage(true, 10.0F, BalanceConfig.defaults().abilityBlockReduction());
		Fx f = new Fx().toShikai(CharacterId.BYAKUYA);
		assertTrue(f.cast(AbilityId.MODE_BARRIER, CharacterId.BYAKUYA).ok());
		int tenths = f.sm.absorbBarrier(Math.round(afterBlock * 10.0F));
		assertEquals(14, tenths, "10 HP * 0.7 * 0.2 = 1.4 HP, i.e. 86 percent cut in all, not 110");
		// the barrier alone, for comparison: 2.0 HP
		Fx g = new Fx().toShikai(CharacterId.BYAKUYA);
		assertTrue(g.cast(AbilityId.MODE_BARRIER, CharacterId.BYAKUYA).ok());
		assertEquals(20, g.sm.absorbBarrier(100));
		assertTrue(tenths < 20, "blocking inside the barrier is strictly better, never worse");
	}
}
