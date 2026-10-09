package dev.minebleach.reiatsutest.core.state;

import java.util.List;

/**
 * Numbers of one ability. {@code phaseOffsets} are the ticks after the cast at which server glue executes a phase
 * (index = phase id); the tables of STATE_MACHINE section 4 are the source.
 */
public record AbilitySpec(AbilityId id, int costTenths, int cooldownTicks, int effectId, boolean enabled,
		List<Integer> phaseOffsets) {
	public AbilitySpec {
		phaseOffsets = List.copyOf(phaseOffsets);
	}
}
