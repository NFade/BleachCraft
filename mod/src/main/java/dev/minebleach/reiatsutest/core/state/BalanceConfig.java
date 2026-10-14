package dev.minebleach.reiatsutest.core.state;

import dev.minebleach.reiatsutest.core.gin.GinParams;
import dev.minebleach.reiatsutest.core.reiatsu.Rate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every balance number in one place (STATE_MACHINE header). Units: ticks (20/s) and tenths of a reiatsu point.
 */
public record BalanceConfig(
		int maxTenths,
		int regenBatchTicks,
		Map<ZanpakutoState, Rate> rates,
		int shikaiReleaseCost,
		int bankaiCost,
		int bankaiCapTicks,
		int shikaiIdleTicks,
		int handGraceTicks,
		int transitionLockTicks,
		int gcdTicks,
		int settleTicks,
		int sealLockTicks,
		int sheatheLockTicks,
		int bankaiReentryTicks,
		int rateLimitPerSecond,
		int respawnTenths,
		int attackModeTicks,
		int barrierTicks,
		int barrierPoolTenths,
		int barrierReductionPercent,
		boolean barrierControlImmune,
		BankaiFieldSpec bankaiField,
		ShunpoSpec shunpo,
		Map<AbilityId, AbilitySpec> abilities,
		MechSpec mech) {

	public BalanceConfig {
		rates = Map.copyOf(rates);
		abilities = Map.copyOf(abilities);
		mech = mech == null ? MechSpec.defaults() : mech;
	}

	/** Block against abilities (r8 item 1): the share of ability damage a holder of a zanpakuto block does not take (0.30 = 30 percent less). */
	public double abilityBlockReduction() {
		return mech.abilityBlockReduction();
	}

	/**
	 * Melee hit reiatsu (r8 item 5), in reiatsu POINTS as the HUD shows them (the bar is 0..100.0 points; {@link #maxTenths} 1000 = 100.0).
	 * Every landed zanpakuto melee hit restores this much; {@link #meleeHitTenths} is the same in the machine's unit.
	 */
	public double meleeHitReiatsu() {
		return mech.meleeHitReiatsu();
	}

	/** {@link #meleeHitReiatsu} in tenths of a point (the unit of every cost and of the bar), never negative. */
	public int meleeHitTenths() {
		return (int) Math.max(0L, Math.round(mech.meleeHitReiatsu() * 10.0));
	}

	/** Reiatsu a successful parry restores (R11 T2), in points. */
	public double parryReiatsu() {
		return mech.parryReiatsu();
	}

	/** {@link #parryReiatsu} in tenths of a point, never negative. */
	public int parryTenths() {
		return (int) Math.max(0L, Math.round(mech.parryReiatsu() * 10.0));
	}

	/** Most tenths of a point the melee hits of one player may restore per second (R11 T2). */
	public int meleeHitMaxPerSecondTenths() {
		return (int) Math.max(0L, Math.round(mech.meleeHitMaxPerSecond() * 10.0));
	}

	public int meleeHitTargetGapTicks() {
		return mech.meleeHitTargetGapTicks();
	}

	public int parryGapTicks() {
		return mech.parryGapTicks();
	}

	/** Ticks a victim is immune to the Byakuya attack mode after one of its damage ticks, per victim and caster (r8 item 2). */
	public int byakuyaAttackTickInterval() {
		return mech.byakuyaAttackTickInterval();
	}

	public double barrierPushRadius() {
		return mech.barrierPushRadius();
	}

	public double barrierPushStrength() {
		return mech.barrierPushStrength();
	}

	public double barrierPushUp() {
		return mech.barrierPushUp();
	}

	public int barrierPushIntervalTicks() {
		return mech.barrierPushIntervalTicks();
	}

	/** A copy with other r8 numbers (tests). */
	public BalanceConfig withMech(MechSpec newMech) {
		return new BalanceConfig(maxTenths, regenBatchTicks, rates, shikaiReleaseCost, bankaiCost, bankaiCapTicks, shikaiIdleTicks,
				handGraceTicks, transitionLockTicks, gcdTicks, settleTicks, sealLockTicks, sheatheLockTicks, bankaiReentryTicks,
				rateLimitPerSecond, respawnTenths, attackModeTicks, barrierTicks, barrierPoolTenths, barrierReductionPercent,
				barrierControlImmune, bankaiField, shunpo, abilities, newMech);
	}

	public Rate rate(ZanpakutoState s) {
		Rate r = rates.get(s);
		return r != null ? r : rates.get(ZanpakutoState.SEALED); // a custom config without a BASE entry regenerates like SEALED
	}

	public AbilitySpec spec(AbilityId id) {
		return abilities.get(id);
	}

	/** A copy with another rate table (drain scenarios in tests). */
	public BalanceConfig withRates(Map<ZanpakutoState, Rate> newRates) {
		return new BalanceConfig(maxTenths, regenBatchTicks, newRates, shikaiReleaseCost, bankaiCost, bankaiCapTicks, shikaiIdleTicks,
				handGraceTicks, transitionLockTicks, gcdTicks, settleTicks, sealLockTicks, sheatheLockTicks, bankaiReentryTicks,
				rateLimitPerSecond, respawnTenths, attackModeTicks, barrierTicks, barrierPoolTenths, barrierReductionPercent,
				barrierControlImmune, bankaiField, shunpo, abilities, mech);
	}

	/** A copy with another territory spec (tests). */
	public BalanceConfig withBankaiField(BankaiFieldSpec field) {
		return new BalanceConfig(maxTenths, regenBatchTicks, rates, shikaiReleaseCost, bankaiCost, bankaiCapTicks, shikaiIdleTicks,
				handGraceTicks, transitionLockTicks, gcdTicks, settleTicks, sealLockTicks, sheatheLockTicks, bankaiReentryTicks,
				rateLimitPerSecond, respawnTenths, attackModeTicks, barrierTicks, barrierPoolTenths, barrierReductionPercent,
				barrierControlImmune, field, shunpo, abilities, mech);
	}

	/** A copy with other barrier numbers: pool in tenths of a HP (0 = unlimited), reduction percent, control immunity. */
	public BalanceConfig withBarrier(int poolTenths, int reductionPercent, boolean controlImmune) {
		return new BalanceConfig(maxTenths, regenBatchTicks, rates, shikaiReleaseCost, bankaiCost, bankaiCapTicks, shikaiIdleTicks,
				handGraceTicks, transitionLockTicks, gcdTicks, settleTicks, sealLockTicks, sheatheLockTicks, bankaiReentryTicks,
				rateLimitPerSecond, respawnTenths, attackModeTicks, barrierTicks, poolTenths, reductionPercent,
				controlImmune, bankaiField, shunpo, abilities, mech);
	}

	/**
	 * Defaults. B4 step 4 (justification: LOG.md "B4 step 4"): bankai costs no reiatsu to enter (and needs no full bar, mech2 task 3; the Rage gate stays),
	 * lasts {@code bankaiCapTicks} as a plain timer and then falls back to SHIKAI; its abilities cost nothing but have
	 * cooldowns; shikai abilities stay usable inside bankai with their normal costs. Areas of every ability are 1.5 to 2 times
	 * the old values (shikai) or 10 to 16 blocks (bankai).
	 */
	public static BalanceConfig defaults() {
		EnumMap<ZanpakutoState, Rate> rates = new EnumMap<>(ZanpakutoState.class);
		rates.put(ZanpakutoState.SEALED, new Rate(10, 0)); // +4.0 / s
		rates.put(ZanpakutoState.BASE, new Rate(10, 0)); // drawn base form: same regeneration as sealed, no upkeep
		rates.put(ZanpakutoState.SHIKAI, new Rate(8, 3)); // +3.2 gross, -1.2 upkeep
		rates.put(ZanpakutoState.BANKAI, new Rate(5, 0)); // no upkeep drain (the timer ends bankai); +2.0 / s, the same net as shikai, so shikai abilities can be afforded

		EnumMap<AbilityId, AbilitySpec> a = new EnumMap<>(AbilityId.class);
		// ---- Rukia shikai (areas x1.75, wave and hitscan reach x1.7)
		a.put(AbilityId.TSUKISHIRO, new AbilitySpec(AbilityId.TSUKISHIRO, 250, 240, 20, true, List.of(20, 40), p(
				AbilityParams.RADIUS, 7.0, AbilityParams.HEIGHT, 4.0, AbilityParams.MAX_TARGETS, 24, AbilityParams.SLOW_AMP, 3, AbilityParams.STATUS_TICKS, 60, AbilityParams.DAMAGE, 3.0, AbilityParams.SPLIT_RADIUS, 16.0)));
		a.put(AbilityId.HAKUREN, new AbilitySpec(AbilityId.HAKUREN, 200, 160, 21, true, List.of(20, 22, 24, 26, 28, 30, 32, 34), p(
				AbilityParams.LENGTH, 20.0, AbilityParams.HALF_WIDTH, 3.5, AbilityParams.MAX_TARGETS, 20, AbilityParams.DAMAGE, 2.0, AbilityParams.SLOW_AMP, 1, AbilityParams.STATUS_TICKS, 60, AbilityParams.MAX_BLOCKS, 64)));
		// Shirafune (r8 item 3): 12 ticks of cast time, then the thrust leaves in the direction the caster looks AT THAT MOMENT (aimAtRelease),
		// not where he looked when he pressed the key; the effect event is sent at the release too
		a.put(AbilityId.SHIRAFUNE, new AbilitySpec(AbilityId.SHIRAFUNE, 150, 100, 22, true, List.of(12), p(
				AbilityParams.LENGTH, 14.0, AbilityParams.DAMAGE, 3.5, AbilityParams.SLOW_AMP, 2, AbilityParams.STATUS_TICKS, 80, AbilityParams.AIM_AT_RELEASE, 1.0)));
		// ---- Rukia bankai: the ice wall is the one ability; the second slot became the passive frost territory (BankaiFieldSpec, mech2 task 4)
		// Ice wall (U, R11 T5 variant W): 14 ticks of cast time, then a wall of real ice blocks grows where she looks (ray up to 10 blocks,
		// the wall stands one block in front of an obstacle). 18 reiatsu, 14 s cooldown, no damage. The geometry (9 x 5 x 2, 10 s life,
		// growth 0.5 s, strength 60) lives in the config section rukia.iceWall (core/world/IceWallConfig), not here.
		a.put(AbilityId.ICE_WALL, new AbilitySpec(AbilityId.ICE_WALL, 180, 280, 24, true, List.of(14), p(
				AbilityParams.LENGTH, 10.0)));
		// ---- Byakuya shikai
		a.put(AbilityId.MODE_ATTACK, new AbilitySpec(AbilityId.MODE_ATTACK, 120, 60, 30, true, List.of(10, 12, 14, 20), p(
				AbilityParams.RADIUS, 3.0, AbilityParams.MAX_TARGETS, 16, AbilityParams.DAMAGE, 1.0, AbilityParams.AIM_RANGE, 19.0)));
		a.put(AbilityId.MODE_BARRIER, new AbilitySpec(AbilityId.MODE_BARRIER, 180, 200, 31, true, List.of()));
		// Petal cage (B, round 6): a sphere of petals of radius 5 around the aimed target or point (aim range 22); 6 pulses, one every
		// 10 ticks from tick 12, each 1.5 HP (9 HP in all if the target stays inside) and Slowness III for 3 s; 20 reiatsu, 15 s cooldown.
		// 0.6 HP per second, below the attack mode (2 HP per 3 s) because the cage also controls.
		a.put(AbilityId.CAGE, new AbilitySpec(AbilityId.CAGE, 200, 300, 35, true, List.of(12, 22, 32, 42, 52, 62), p(
				AbilityParams.RADIUS, 5.0, AbilityParams.MAX_TARGETS, 16, AbilityParams.DAMAGE, 0.8, AbilityParams.AIM_RANGE, 22.0, AbilityParams.SLOW_AMP, 2, AbilityParams.STATUS_TICKS, 60)));
		// ---- Byakuya bankai: free, storm radius 12 around the caster, impact radius 14 at the aim point, group damage
		a.put(AbilityId.SCATTER, new AbilitySpec(AbilityId.SCATTER, 0, 300, 32, true, List.of(16, 26, 50), p(
				AbilityParams.TORNADO_RADIUS, 12.0, AbilityParams.DAMAGE2, 1.0, AbilityParams.RADIUS, 14.0, AbilityParams.DAMAGE, 6.0, AbilityParams.KNOCKBACK, 1.5, AbilityParams.MAX_TARGETS, 48, AbilityParams.AIM_RANGE, 40.0)));
		a.put(AbilityId.HAKUTEIKEN, new AbilitySpec(AbilityId.HAKUTEIKEN, 0, 600, 33, true, List.of(30, 36), p(
				AbilityParams.LENGTH, 28.0, AbilityParams.HALF_WIDTH, 2.0, AbilityParams.DAMAGE2, 5.0, AbilityParams.RADIUS, 12.0, AbilityParams.DAMAGE, 9.0, AbilityParams.KNOCKBACK, 1.5, AbilityParams.MAX_TARGETS, 48)));
		a.put(AbilityId.SENKEI, new AbilitySpec(AbilityId.SENKEI, 0, 500, 34, false, List.of()));
		// ---- Gin and Uryu (design/GIN.md, design/URYU.md): registration skeleton, first-guess cost and cooldown only, one phase at tick 0 (stub executors);
		// area, damage and timing numbers come with the gameplay steps
		// Gin shikai Z: 10 reiatsu, 4 s
		a.put(AbilityId.IKOROSE, new AbilitySpec(AbilityId.IKOROSE, 100, 80, 62, true, List.of(0), p(
				AbilityParams.LENGTH, 14.0, AbilityParams.HALF_WIDTH, 0.3, AbilityParams.DAMAGE, 5.0, AbilityParams.KNOCKBACK, 1.5,
				GinParams.ROOT_TICKS, 8, GinParams.ROOT_AMP, 3)));
		// Gin shikai H: 18 reiatsu, 9 s
		// phase 0 = the root starts (1.0 s wind-up), phases 1..8 = the eight 15 degree slices of the sweep, one every 2 ticks (0.8 s), then 0.5 s recovery
		a.put(AbilityId.HYAPPONZASHI, new AbilitySpec(AbilityId.HYAPPONZASHI, 180, 180, 63, true, List.of(0, 20, 22, 24, 26, 28, 30, 32, 34), p(
				AbilityParams.RADIUS, 8.0, GinParams.ARC_DEGREES, 120.0, GinParams.ARC_HEIGHT, 2.5, AbilityParams.DAMAGE, 3.0, AbilityParams.MAX_TARGETS, 8,
				GinParams.ROOT_TICKS, 46, GinParams.ROOT_AMP, 3)));
		// Gin shikai B: 12 reiatsu, 8 s
		a.put(AbilityId.SNAKE_HOOK, new AbilitySpec(AbilityId.SNAKE_HOOK, 120, 160, 64, true, List.of(0), p(
				AbilityParams.LENGTH, 16.0, AbilityParams.HALF_WIDTH, 0.3, AbilityParams.DAMAGE, 2.0, GinParams.STOP_SHORT, 1.5,
				GinParams.PULL_SPEED, 0.9, GinParams.PULL_TICKS, 30)));
		// Gin bankai Z: 28 reiatsu, 14 s
		// 8 thrusts over 1.0 s (phase = thrust), rooted 1.2 s from the first one
		a.put(AbilityId.BUTOU_RENJIN, new AbilitySpec(AbilityId.BUTOU_RENJIN, 280, 280, 65, true, List.of(0, 3, 5, 8, 10, 13, 15, 18), p(
				AbilityParams.LENGTH, 24.0, AbilityParams.HALF_WIDTH, 0.3, AbilityParams.DAMAGE, 1.5, GinParams.ROOT_TICKS, 24, GinParams.ROOT_AMP, 3)));
		// Gin bankai H, press 1 (the recast is free; the 30 s cooldown after the dissolve comes in step 2): 25 reiatsu, 12 s
		// press 1 = this row (line 48, 9 HP, sliver 12 s, only if the thrust really deals sliverMinHp). Press 2 = the free recast: 1.5 s channel, 18 HP
		// dissolve; the cooldown is 12 s after press 1 (cooldown field), 30 s after the dissolve, 15 s after an interrupted channel
		a.put(AbilityId.KOROSE, new AbilitySpec(AbilityId.KOROSE, 250, 240, 66, true, List.of(0), p(
				AbilityParams.LENGTH, 48.0, AbilityParams.HALF_WIDTH, 0.3, AbilityParams.DAMAGE, 9.0,
				GinParams.SLIVER_TICKS, 240, GinParams.SLIVER_MIN_HP, 1.0, GinParams.RECAST_RANGE, 64.0, GinParams.CHANNEL_TICKS, 30,
				GinParams.DISSOLVE_DAMAGE, 18.0, GinParams.COOLDOWN_DISSOLVE, 600, GinParams.COOLDOWN_INTERRUPT, 300,
				GinParams.INTERRUPT_HP, 4.0, GinParams.PUSH_LIMIT, 0.9, GinParams.INTERRUPT_KEEPS_MARK, 0, GinParams.ROOT_AMP, 3)));
		// Gin bankai B: 15 reiatsu, 18 s
		a.put(AbilityId.HAKUFUKU, new AbilitySpec(AbilityId.HAKUFUKU, 150, 360, 67, true, List.of(0), p(
				AbilityParams.DAMAGE, 0.0, GinParams.HIDE_TICKS, 60, GinParams.FLASH_TICKS, 80))); // no damage and no damage bonus (open question 1)
		// Uryu shikai Z: 12 reiatsu, 3 s
		a.put(AbilityId.HEILIG_PFEIL, new AbilitySpec(AbilityId.HEILIG_PFEIL, 120, 60, 72, true, List.of(0)));
		// Uryu shikai H: 25 reiatsu, 14 s
		a.put(AbilityId.SPRENGER, new AbilitySpec(AbilityId.SPRENGER, 250, 280, 73, true, List.of(0)));
		// Uryu shikai B: 10 reiatsu, 6 s
		a.put(AbilityId.HIRENKYAKU, new AbilitySpec(AbilityId.HIRENKYAKU, 100, 120, 74, true, List.of(0)));
		// Uryu bankai Z: 28 reiatsu, 16 s
		a.put(AbilityId.LICHT_REGEN, new AbilitySpec(AbilityId.LICHT_REGEN, 280, 320, 75, true, List.of(0)));
		// Uryu bankai H: 30 reiatsu, 16 s
		a.put(AbilityId.HOMING_VOLLEY, new AbilitySpec(AbilityId.HOMING_VOLLEY, 300, 320, 76, true, List.of(0)));
		// Uryu bankai B: 50 reiatsu, 40 s
		a.put(AbilityId.STERNENSTAUB, new AbilitySpec(AbilityId.STERNENSTAUB, 500, 800, 77, true, List.of(0)));

		return new BalanceConfig(
				1000, 5, rates,
				150, 0, 900, 2400, 20,
				10, 12, 44, 40, 0, 1200,
				// barrier (H): 5 s, 80 percent less damage for the whole time (pool 0 = no cap, the dome never collapses early), control immune
				10, 500, 30, 100, 0, 80, true,
				// passive frost territory of the Rukia bankai (mech2 task 4)
				BankaiFieldSpec.defaults(),
				new ShunpoSpec(50, 50, 9.0, 1.5, 0.25, 3), a, MechSpec.defaults());
	}

	private static Map<String, Double> p(Object... kv) {
		java.util.HashMap<String, Double> m = new java.util.HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
		}
		return m;
	}
}
