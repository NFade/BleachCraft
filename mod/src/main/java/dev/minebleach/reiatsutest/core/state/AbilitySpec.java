package dev.minebleach.reiatsutest.core.state;

import java.util.List;
import java.util.Map;

/**
 * Numbers of one ability. {@code phaseOffsets} are the ticks after the cast at which server glue executes a phase
 * (index = phase id); the tables of STATE_MACHINE section 4 are the source. {@code params} are the area / damage numbers
 * (keys in {@link AbilityParams}), all balance values live in {@link BalanceConfig#defaults()}.
 */
public record AbilitySpec(AbilityId id, int costTenths, int cooldownTicks, int effectId, boolean enabled,
		List<Integer> phaseOffsets, Map<String, Double> params) {
	public AbilitySpec {
		phaseOffsets = List.copyOf(phaseOffsets);
		params = Map.copyOf(params);
	}

	public AbilitySpec(AbilityId id, int costTenths, int cooldownTicks, int effectId, boolean enabled, List<Integer> phaseOffsets) {
		this(id, costTenths, cooldownTicks, effectId, enabled, phaseOffsets, Map.of());
	}

	/** The number {@code key}, or 0 when the ability has none. */
	public double num(String key) {
		Double v = params.get(key);
		return v == null ? 0.0 : v;
	}

	public int intNum(String key) {
		return (int) Math.round(num(key));
	}

	public float floatNum(String key) {
		return (float) num(key);
	}
}
