package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
