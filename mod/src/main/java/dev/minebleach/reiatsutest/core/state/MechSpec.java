package dev.minebleach.reiatsutest.core.state;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;

/**
 * The numbers of the r8 mechanics round (design/results/r8_mech.md), kept together so {@link BalanceConfig} stays one record:
 * <ul>
 * <li>{@code abilityBlockReduction}: a holder of the zanpakuto block (right mouse) takes this much less damage from abilities and
 * kido (fraction 0..1, 0.30 = 30 percent less). Melee is handled by {@code CombatConfig.blockedDamageMultiplier} (50 percent).</li>
 * <li>{@code meleeHitReiatsu}: reiatsu POINTS (HUD scale, the bar is 0..100.0, internally tenths) restored by every landed melee hit
 * with a zanpakuto.</li>
 * <li>{@code parryReiatsu} (R11 T2): points restored by a successful parry, at most once per {@code parryGapTicks} ticks.
 * {@code meleeHitMaxPerSecond}: cap of the points melee hits may restore per second (fast attacks of Better Combat would
 * otherwise print mana), {@code meleeHitTargetGapTicks}: one reward per target per this many ticks.</li>
 * <li>{@code byakuyaAttackTickInterval}: ticks a victim is immune to the Byakuya attack mode (Z) after one damage tick of it, per
 * victim and caster (20 ticks = 1 s; 4 = 0.2 s).</li>
 * <li>{@code barrierPush*}: the barrier mode pushes everybody but the owner out of {@code barrierPushRadius} blocks (strength in
 * blocks per tick of outward speed, a small upward component, repeated every {@code barrierPushIntervalTicks} ticks; no damage).</li>
 * </ul>
 * Every value is clamped to a sane range by the constructor, so a hand-built config cannot break the rules.
 */
public record MechSpec(double abilityBlockReduction, double meleeHitReiatsu, int byakuyaAttackTickInterval, double barrierPushRadius,
		double barrierPushStrength, double barrierPushUp, int barrierPushIntervalTicks, double parryReiatsu, double meleeHitMaxPerSecond,
		int meleeHitTargetGapTicks, int parryGapTicks) {
	public static final String SECTION = "mech";

	/** The pre-R11 seven numbers; the R11 ones take their defaults. */
	public MechSpec(double abilityBlockReduction, double meleeHitReiatsu, int byakuyaAttackTickInterval, double barrierPushRadius,
			double barrierPushStrength, double barrierPushUp, int barrierPushIntervalTicks) {
		this(abilityBlockReduction, meleeHitReiatsu, byakuyaAttackTickInterval, barrierPushRadius, barrierPushStrength, barrierPushUp,
				barrierPushIntervalTicks, 10.0, 15.0, 5, 10);
	}

	public MechSpec {
		abilityBlockReduction = clamp(abilityBlockReduction, 0.0, 1.0);
		meleeHitReiatsu = clamp(meleeHitReiatsu, 0.0, 100.0);
		byakuyaAttackTickInterval = Math.max(1, Math.min(200, byakuyaAttackTickInterval));
		barrierPushRadius = clamp(barrierPushRadius, 0.0, 16.0);
		barrierPushStrength = clamp(barrierPushStrength, 0.0, 4.0);
		barrierPushUp = clamp(barrierPushUp, 0.0, 1.0);
		barrierPushIntervalTicks = Math.max(1, Math.min(40, barrierPushIntervalTicks));
		parryReiatsu = clamp(parryReiatsu, 0.0, 100.0);
		meleeHitMaxPerSecond = clamp(meleeHitMaxPerSecond, 0.0, 200.0);
		meleeHitTargetGapTicks = Math.max(0, Math.min(200, meleeHitTargetGapTicks));
		parryGapTicks = Math.max(0, Math.min(200, parryGapTicks));
	}

	/**
	 * Defaults (UNVERIFIED in play). Block: 30 percent less ability damage. Melee: +5.0 reiatsu points per hit (R11 T2, was 1.0; the bar is
	 * 100.0 points, an ability costs 12 to 25, so 3 to 5 hits pay for one), at most 15 points a second from hits and one reward per target
	 * per 5 ticks. Parry: +10.0 points, at most once per 10 ticks. Byakuya attack: 4 ticks (0.2 s). Barrier push:
	 * radius 3.5, outward speed 0.6 blocks per tick every 2 ticks (about 0.55 blocks per tick on average) (a sprinting player moves 0.28 blocks per tick), 0.18 up.
	 */
	public static MechSpec defaults() {
		return new MechSpec(0.30, 5.0, 4, 3.5, 0.6, 0.18, 2, 10.0, 15.0, 5, 10);
	}

	/** The {@code "mech"} section of the root config (missing keys = defaults, bad values = defaults, numbers clamped). */
	public static MechSpec fromRoot(JsonObject root) {
		MechSpec d = defaults();
		JsonObject o = Cfg.obj(root, SECTION);
		return new MechSpec(
				Cfg.num(o, "abilityBlockReduction", d.abilityBlockReduction, 0.0, 1.0),
				Cfg.num(o, "meleeHitReiatsu", d.meleeHitReiatsu, 0.0, 100.0),
				Cfg.integer(o, "byakuyaAttackTickInterval", d.byakuyaAttackTickInterval, 1, 200),
				Cfg.num(o, "barrierPushRadius", d.barrierPushRadius, 0.0, 16.0),
				Cfg.num(o, "barrierPushStrength", d.barrierPushStrength, 0.0, 4.0),
				Cfg.num(o, "barrierPushUp", d.barrierPushUp, 0.0, 1.0),
				Cfg.integer(o, "barrierPushIntervalTicks", d.barrierPushIntervalTicks, 1, 40),
				Cfg.num(o, "parryReiatsu", d.parryReiatsu, 0.0, 100.0),
				Cfg.num(o, "meleeHitMaxPerSecond", d.meleeHitMaxPerSecond, 0.0, 200.0),
				Cfg.integer(o, "meleeHitTargetGapTicks", d.meleeHitTargetGapTicks, 0, 200),
				Cfg.integer(o, "parryGapTicks", d.parryGapTicks, 0, 200));
	}

	/** The {@code "mech"} object, for writing the numbers in force into a fresh file. */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("abilityBlockReduction", abilityBlockReduction);
		o.addProperty("meleeHitReiatsu", meleeHitReiatsu);
		o.addProperty("parryReiatsu", parryReiatsu);
		o.addProperty("meleeHitMaxPerSecond", meleeHitMaxPerSecond);
		o.addProperty("meleeHitTargetGapTicks", meleeHitTargetGapTicks);
		o.addProperty("parryGapTicks", parryGapTicks);
		o.addProperty("byakuyaAttackTickInterval", byakuyaAttackTickInterval);
		o.addProperty("barrierPushRadius", barrierPushRadius);
		o.addProperty("barrierPushStrength", barrierPushStrength);
		o.addProperty("barrierPushUp", barrierPushUp);
		o.addProperty("barrierPushIntervalTicks", barrierPushIntervalTicks);
		return o;
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
