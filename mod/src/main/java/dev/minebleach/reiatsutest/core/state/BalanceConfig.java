package dev.minebleach.reiatsutest.core.state;

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
		Map<AbilityId, AbilitySpec> abilities) {

	public BalanceConfig {
		rates = Map.copyOf(rates);
		abilities = Map.copyOf(abilities);
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
				barrierControlImmune, bankaiField, shunpo, abilities);
	}

	/** A copy with another territory spec (tests). */
	public BalanceConfig withBankaiField(BankaiFieldSpec field) {
		return new BalanceConfig(maxTenths, regenBatchTicks, rates, shikaiReleaseCost, bankaiCost, bankaiCapTicks, shikaiIdleTicks,
				handGraceTicks, transitionLockTicks, gcdTicks, settleTicks, sealLockTicks, sheatheLockTicks, bankaiReentryTicks,
				rateLimitPerSecond, respawnTenths, attackModeTicks, barrierTicks, barrierPoolTenths, barrierReductionPercent,
				barrierControlImmune, field, shunpo, abilities);
	}

	/** A copy with other barrier numbers: pool in tenths of a HP (0 = unlimited), reduction percent, control immunity. */
	public BalanceConfig withBarrier(int poolTenths, int reductionPercent, boolean controlImmune) {
		return new BalanceConfig(maxTenths, regenBatchTicks, rates, shikaiReleaseCost, bankaiCost, bankaiCapTicks, shikaiIdleTicks,
				handGraceTicks, transitionLockTicks, gcdTicks, settleTicks, sealLockTicks, sheatheLockTicks, bankaiReentryTicks,
				rateLimitPerSecond, respawnTenths, attackModeTicks, barrierTicks, poolTenths, reductionPercent,
				controlImmune, bankaiField, shunpo, abilities);
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
				AbilityParams.RADIUS, 7.0, AbilityParams.HEIGHT, 4.0, AbilityParams.MAX_TARGETS, 24, AbilityParams.SLOW_AMP, 3, AbilityParams.STATUS_TICKS, 60, AbilityParams.DAMAGE, 6.0, AbilityParams.SPLIT_RADIUS, 16.0)));
		a.put(AbilityId.HAKUREN, new AbilitySpec(AbilityId.HAKUREN, 200, 160, 21, true, List.of(20, 22, 24, 26, 28, 30, 32, 34), p(
				AbilityParams.LENGTH, 20.0, AbilityParams.HALF_WIDTH, 3.5, AbilityParams.MAX_TARGETS, 20, AbilityParams.DAMAGE, 5.0, AbilityParams.SLOW_AMP, 1, AbilityParams.STATUS_TICKS, 60, AbilityParams.MAX_BLOCKS, 64)));
		a.put(AbilityId.SHIRAFUNE, new AbilitySpec(AbilityId.SHIRAFUNE, 150, 100, 22, true, List.of(12), p(
				AbilityParams.LENGTH, 14.0, AbilityParams.DAMAGE, 8.0, AbilityParams.SLOW_AMP, 2, AbilityParams.STATUS_TICKS, 80)));
		// ---- Rukia bankai: the ice spike is the one ability; the second slot became the passive frost territory (BankaiFieldSpec, mech2 task 4)
		// Ice spike (U): a 36 block thrust of ice spikes along the look ray (the wave runs 6 blocks per 2 ticks), one hit per target,
		// 9 HP and Slowness III for 4 s, 15 reiatsu, 8 s cooldown. About 1.1 HP per second when spammed.
		a.put(AbilityId.ICE_SPIKE, new AbilitySpec(AbilityId.ICE_SPIKE, 150, 160, 24, true, List.of(14, 16, 18, 20, 22, 24), p(
				AbilityParams.LENGTH, 36.0, AbilityParams.HALF_WIDTH, 1.5, AbilityParams.MAX_TARGETS, 12, AbilityParams.DAMAGE, 9.0, AbilityParams.SLOW_AMP, 2, AbilityParams.STATUS_TICKS, 80)));
		// ---- Byakuya shikai
		a.put(AbilityId.MODE_ATTACK, new AbilitySpec(AbilityId.MODE_ATTACK, 120, 60, 30, true, List.of(10, 12, 14, 20), p(
				AbilityParams.RADIUS, 3.0, AbilityParams.MAX_TARGETS, 16, AbilityParams.DAMAGE, 2.0, AbilityParams.AIM_RANGE, 19.0)));
		a.put(AbilityId.MODE_BARRIER, new AbilitySpec(AbilityId.MODE_BARRIER, 180, 200, 31, true, List.of()));
		// Petal cage (B, round 6): a sphere of petals of radius 5 around the aimed target or point (aim range 22); 6 pulses, one every
		// 10 ticks from tick 12, each 1.5 HP (9 HP in all if the target stays inside) and Slowness III for 3 s; 20 reiatsu, 15 s cooldown.
		// 0.6 HP per second, below the attack mode (2 HP per 3 s) because the cage also controls.
		a.put(AbilityId.CAGE, new AbilitySpec(AbilityId.CAGE, 200, 300, 35, true, List.of(12, 22, 32, 42, 52, 62), p(
				AbilityParams.RADIUS, 5.0, AbilityParams.MAX_TARGETS, 16, AbilityParams.DAMAGE, 1.5, AbilityParams.AIM_RANGE, 22.0, AbilityParams.SLOW_AMP, 2, AbilityParams.STATUS_TICKS, 60)));
		// ---- Byakuya bankai: free, storm radius 12 around the caster, impact radius 14 at the aim point, group damage
		a.put(AbilityId.SCATTER, new AbilitySpec(AbilityId.SCATTER, 0, 300, 32, true, List.of(16, 26, 50), p(
				AbilityParams.TORNADO_RADIUS, 12.0, AbilityParams.DAMAGE2, 2.0, AbilityParams.RADIUS, 14.0, AbilityParams.DAMAGE, 16.0, AbilityParams.KNOCKBACK, 1.5, AbilityParams.MAX_TARGETS, 48, AbilityParams.AIM_RANGE, 40.0)));
		a.put(AbilityId.HAKUTEIKEN, new AbilitySpec(AbilityId.HAKUTEIKEN, 0, 600, 33, true, List.of(30, 36), p(
				AbilityParams.LENGTH, 28.0, AbilityParams.HALF_WIDTH, 2.0, AbilityParams.DAMAGE2, 14.0, AbilityParams.RADIUS, 12.0, AbilityParams.DAMAGE, 24.0, AbilityParams.KNOCKBACK, 1.5, AbilityParams.MAX_TARGETS, 48)));
		a.put(AbilityId.SENKEI, new AbilitySpec(AbilityId.SENKEI, 0, 500, 34, false, List.of()));

		return new BalanceConfig(
				1000, 5, rates,
				150, 0, 900, 2400, 20,
				10, 12, 44, 40, 0, 1200,
				// barrier (H): 5 s, 80 percent less damage for the whole time (pool 0 = no cap, the dome never collapses early), control immune
				10, 500, 30, 100, 0, 80, true,
				// passive frost territory of the Rukia bankai (mech2 task 4)
				BankaiFieldSpec.defaults(),
				new ShunpoSpec(50, 50, 9.0, 1.5, 0.25, 3), a);
	}

	private static Map<String, Double> p(Object... kv) {
		java.util.HashMap<String, Double> m = new java.util.HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
		}
		return m;
	}
}
