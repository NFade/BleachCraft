package dev.minebleach.reiatsutest.core.state;

/**
 * The numbers of the r8 mechanics round (design/results/r8_mech.md), kept together so {@link BalanceConfig} stays one record:
 * <ul>
 * <li>{@code abilityBlockReduction}: a holder of the zanpakuto block (right mouse) takes this much less damage from abilities and
 * kido (fraction 0..1, 0.30 = 30 percent less). Melee is handled by {@code CombatConfig.blockedDamageMultiplier} (50 percent).</li>
 * <li>{@code meleeHitReiatsu}: reiatsu POINTS (HUD scale, the bar is 0..100.0, internally tenths) restored by every landed melee hit
 * with a zanpakuto.</li>
 * <li>{@code byakuyaAttackTickInterval}: ticks a victim is immune to the Byakuya attack mode (Z) after one damage tick of it, per
 * victim and caster (20 ticks = 1 s; 4 = 0.2 s).</li>
 * <li>{@code barrierPush*}: the barrier mode pushes everybody but the owner out of {@code barrierPushRadius} blocks (strength in
 * blocks per tick of outward speed, a small upward component, repeated every {@code barrierPushIntervalTicks} ticks; no damage).</li>
 * </ul>
 * Every value is clamped to a sane range by the constructor, so a hand-built config cannot break the rules.
 */
public record MechSpec(double abilityBlockReduction, double meleeHitReiatsu, int byakuyaAttackTickInterval, double barrierPushRadius,
		double barrierPushStrength, double barrierPushUp, int barrierPushIntervalTicks) {
	public MechSpec {
		abilityBlockReduction = clamp(abilityBlockReduction, 0.0, 1.0);
		meleeHitReiatsu = clamp(meleeHitReiatsu, 0.0, 100.0);
		byakuyaAttackTickInterval = Math.max(1, Math.min(200, byakuyaAttackTickInterval));
		barrierPushRadius = clamp(barrierPushRadius, 0.0, 16.0);
		barrierPushStrength = clamp(barrierPushStrength, 0.0, 4.0);
		barrierPushUp = clamp(barrierPushUp, 0.0, 1.0);
		barrierPushIntervalTicks = Math.max(1, Math.min(40, barrierPushIntervalTicks));
	}

	/**
	 * Defaults (UNVERIFIED in play). Block: 30 percent less ability damage. Melee: +1.0 reiatsu point per hit (10 tenths; the bar is
	 * 100.0 points, an ability costs 12 to 25, so 12 to 25 hits pay for one). Byakuya attack: 4 ticks (0.2 s). Barrier push:
	 * radius 3.5, outward speed 0.6 blocks per tick every 2 ticks (about 0.55 blocks per tick on average) (a sprinting player moves 0.28 blocks per tick), 0.18 up.
	 */
	public static MechSpec defaults() {
		return new MechSpec(0.30, 1.0, 4, 3.5, 0.6, 0.18, 2);
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
