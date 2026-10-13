package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server effects of Uryu Ishida's abilities (design/URYU.md). Registration skeleton only: every ability is a stub that logs and does
 * nothing. Called by {@link AbilityExecutor#run} once per phase, only while the cast is still valid.
 */
final class UryuAbilities {
	private UryuAbilities() {
	}

	static void run(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case HEILIG_PFEIL, SPRENGER, HIRENKYAKU, LICHT_REGEN, HOMING_VOLLEY, STERNENSTAUB -> notImplemented(ability);
			default -> throw new IllegalArgumentException("not an Uryu ability: " + ability);
		}
	}

	private static void notImplemented(AbilityId ability) {
		ReiatsuTest.LOGGER.info("[uryu] {} not implemented", ability.commandId);
	}
}
