package dev.minebleach.reiatsutest.core.combat;

/**
 * Tunable numbers of the block and parry (design/PARRY_PLAN.md sections 3 to 5). Pure data, no Minecraft types.
 *
 * @param parryWindowTicks       a hit that lands this many ticks (inclusive) after the block began is a parry; 4 ticks = 0.2 s
 * @param blockedDamageMultiplier factor applied to melee damage taken while blocking outside the parry window (0.5 = minus 50 percent)
 * @param parryKnockback         horizontal knockback strength given to the attacker of a parried hit (vanilla scale, 0.4 is a normal hit)
 * @param staggerTicks           duration of the short slowdown the attacker gets after a parry (0 = none)
 * @param staggerAmplifier       Slowness amplifier of the stagger (0 = Slowness I)
 */
public record CombatConfig(int parryWindowTicks, float blockedDamageMultiplier, double parryKnockback, int staggerTicks,
		int staggerAmplifier) {
	public CombatConfig {
		if (parryWindowTicks < 0) {
			throw new IllegalArgumentException("parryWindowTicks < 0: " + parryWindowTicks);
		}
		if (!(blockedDamageMultiplier >= 0.0F && blockedDamageMultiplier <= 1.0F)) {
			throw new IllegalArgumentException("blockedDamageMultiplier outside 0..1: " + blockedDamageMultiplier);
		}
		if (parryKnockback < 0.0 || staggerTicks < 0 || staggerAmplifier < 0) {
			throw new IllegalArgumentException("negative combat value");
		}
	}

	/** User decisions of 2026-10-11: window 0.2 s from the start of the block, minus 50 percent outside it. */
	public static CombatConfig defaults() {
		return new CombatConfig(4, 0.5F, 0.6, 12, 2);
	}
}
