package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server effects of Gin Ichimaru's abilities (design/GIN.md). Registration skeleton only: every ability is a stub that logs and does
 * nothing. Called by {@link AbilityExecutor#run} once per phase, only while the cast is still valid.
 */
final class GinAbilities {
	private GinAbilities() {
	}

	static void run(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case IKOROSE, HYAPPONZASHI, SNAKE_HOOK, BUTOU_RENJIN, KOROSE, HAKUFUKU -> notImplemented(ability);
			default -> throw new IllegalArgumentException("not a Gin ability: " + ability);
		}
	}

	private static void notImplemented(AbilityId ability) {
		ReiatsuTest.LOGGER.info("[gin] {} not implemented", ability.commandId);
	}
}
