package dev.minebleach.reiatsutest.core.combat;

/**
 * The rules of the block and the parry as pure functions (design/PARRY_PLAN.md sections 3 to 5). The server glue feeds in what it
 * reads from the game ({@code blocking}, ticks since the block began) and applies the outcome.
 *
 * <p>Decisions of the user (2026-10-11): a block forbids attacking; abilities cannot be cast while blocking; shunpo can; melee
 * damage is reduced by 50 percent outside the parry window; the parry window is 0.2 s from the start of the block.
 */
public final class BlockRules {
	private BlockRules() {
	}

	/** Blocking forbids attacking (the player must release the block to hit). */
	public static boolean allowsAttack(boolean blocking) {
		return !blocking;
	}

	/** Shikai / bankai abilities (Z H B, U I O, voice casts) cannot be cast while blocking. */
	public static boolean allowsAbility(boolean blocking) {
		return !blocking;
	}

	/** Shunpo works during a block: it is the dodge. */
	public static boolean allowsShunpo(boolean blocking) {
		return true;
	}

	/** True when a hit {@code elapsedTicks} after the block began falls inside the parry window (inclusive at the end). */
	public static boolean inParryWindow(int elapsedTicks, CombatConfig cfg) {
		return elapsedTicks >= 0 && elapsedTicks <= cfg.parryWindowTicks();
	}

	/**
	 * What the block does to a hit. Only {@link DamageKind#MELEE} is affected: inside the parry window it is cancelled
	 * (PARRIED), after it reduced by the configured multiplier (BLOCKED). Not blocking, or any other damage: untouched.
	 */
	public static BlockOutcome resolve(boolean blocking, int elapsedTicks, DamageKind kind, float damage, CombatConfig cfg) {
		if (!blocking || kind != DamageKind.MELEE || damage <= 0.0F) {
			return new BlockOutcome(BlockOutcome.Kind.NONE, damage);
		}
		if (inParryWindow(elapsedTicks, cfg)) {
			return new BlockOutcome(BlockOutcome.Kind.PARRIED, 0.0F);
		}
		return new BlockOutcome(BlockOutcome.Kind.BLOCKED, damage * cfg.blockedDamageMultiplier());
	}

	/**
	 * What the block does to damage from ABILITIES and kido (r8 item 1): the zanpakuto block cuts it by {@code reduction}
	 * (a fraction, 0.30 = 30 percent), with no parry window (a parry is for melee only). Not blocking, no damage or a non-finite
	 * damage: untouched. The result is never below 0 and never above the input, whatever {@code reduction} is.
	 *
	 * <p>It composes by multiplication with the other cuts the hit meets afterwards (the Byakuya barrier keeps 20 percent, armor and
	 * resistance are vanilla), so two cuts never add up past 100 percent: 30 percent block and 80 percent barrier leave
	 * 0.7 * 0.2 = 14 percent of the hit.
	 */
	public static float abilityDamage(boolean blocking, float damage, double reduction) {
		if (!blocking || !(damage > 0.0F) || Float.isInfinite(damage)) {
			return damage;
		}
		double r = Double.isNaN(reduction) ? 0.0 : Math.max(0.0, Math.min(1.0, reduction));
		return (float) (damage * (1.0 - r));
	}
}
