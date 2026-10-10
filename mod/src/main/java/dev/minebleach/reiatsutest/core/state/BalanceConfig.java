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

	/**
	 * Defaults. B4 step 4 (justification: LOG.md "B4 step 4"): bankai costs no reiatsu to enter (the bar must still be full),
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
		// ---- Rukia bankai: free, 20 s cooldown, radius 12 (the old 10), stronger and longer freeze, bigger shatter
		a.put(AbilityId.ABSOLUTE_ZERO, new AbilitySpec(AbilityId.ABSOLUTE_ZERO, 0, 400, 23, true, List.of(40, 66), p(
				AbilityParams.RADIUS, 12.0, AbilityParams.MAX_TARGETS, 48, AbilityParams.SLOW_AMP, 6, AbilityParams.STATUS_TICKS, 40, AbilityParams.DAMAGE, 18.0, AbilityParams.SPLIT_RADIUS, 16.0, AbilityParams.MAX_BLOCKS, 64)));
		// ---- Byakuya shikai
		a.put(AbilityId.MODE_ATTACK, new AbilitySpec(AbilityId.MODE_ATTACK, 120, 60, 30, true, List.of(10, 12, 14, 20), p(
				AbilityParams.RADIUS, 3.0, AbilityParams.MAX_TARGETS, 16, AbilityParams.DAMAGE, 2.0, AbilityParams.AIM_RANGE, 32.0)));
		a.put(AbilityId.MODE_BARRIER, new AbilitySpec(AbilityId.MODE_BARRIER, 180, 200, 31, true, List.of()));
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
				10, 500, 30, 100, 200, 80,
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
