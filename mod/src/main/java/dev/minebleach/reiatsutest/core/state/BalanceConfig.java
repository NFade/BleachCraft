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
		int recoveryLockTicks,
		int rateLimitPerSecond,
		int respawnTenths,
		int attackModeTicks,
		int barrierTicks,
		int barrierPoolTenths,
		int barrierReductionPercent,
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

	public static BalanceConfig defaults() {
		EnumMap<ZanpakutoState, Rate> rates = new EnumMap<>(ZanpakutoState.class);
		rates.put(ZanpakutoState.SEALED, new Rate(10, 0)); // +4.0 / s
		rates.put(ZanpakutoState.BASE, new Rate(10, 0)); // drawn base form: same regeneration as sealed, no upkeep
		rates.put(ZanpakutoState.SHIKAI, new Rate(8, 3)); // +3.2 gross, -1.2 upkeep
		rates.put(ZanpakutoState.BANKAI, new Rate(2, 6)); // +0.8 gross, -2.4 upkeep

		EnumMap<AbilityId, AbilitySpec> a = new EnumMap<>(AbilityId.class);
		a.put(AbilityId.TSUKISHIRO, new AbilitySpec(AbilityId.TSUKISHIRO, 250, 240, 20, true, List.of(20, 40)));
		a.put(AbilityId.HAKUREN, new AbilitySpec(AbilityId.HAKUREN, 200, 160, 21, true, List.of(20, 22, 24, 26, 28)));
		a.put(AbilityId.SHIRAFUNE, new AbilitySpec(AbilityId.SHIRAFUNE, 150, 100, 22, true, List.of(12)));
		a.put(AbilityId.ABSOLUTE_ZERO, new AbilitySpec(AbilityId.ABSOLUTE_ZERO, 400, 500, 23, true, List.of(40, 66)));
		a.put(AbilityId.MODE_ATTACK, new AbilitySpec(AbilityId.MODE_ATTACK, 120, 60, 30, true, List.of(10, 12, 14, 20)));
		a.put(AbilityId.MODE_BARRIER, new AbilitySpec(AbilityId.MODE_BARRIER, 180, 200, 31, true, List.of()));
		a.put(AbilityId.SCATTER, new AbilitySpec(AbilityId.SCATTER, 300, 400, 32, true, List.of(16, 26, 50)));
		a.put(AbilityId.HAKUTEIKEN, new AbilitySpec(AbilityId.HAKUTEIKEN, 500, 800, 33, true, List.of(30, 36)));
		a.put(AbilityId.SENKEI, new AbilitySpec(AbilityId.SENKEI, 350, 500, 34, false, List.of()));

		return new BalanceConfig(
				1000, 5, rates,
				150, 200, 900, 2400, 20,
				10, 12, 44, 40, 0, 160,
				10, 500, 30, 100, 200, 80, a);
	}
}
