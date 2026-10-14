package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.server.uryu.UryuManager;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server effects of Uryu Ishida's abilities (design/URYU.md). Called by {@link AbilityExecutor#run} once per phase, only while the
 * cast is still valid; every ability has one phase at tick 0 and the timed parts (the pentagon, the barrage, the dash, the arrows)
 * live in {@link UryuManager}, which also owns the Focus counter. Numbers: {@code core.uryu.UryuBalance}.
 */
final class UryuAbilities {
	private UryuAbilities() {
	}

	static void run(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case HEILIG_PFEIL -> UryuManager.focusShot(p);
			case SPRENGER -> UryuManager.sprenger(p, ctx.aim);
			case SEELE_SCHNEIDER -> UryuManager.seeleSchneider(p);
			case WING_BARRAGE -> UryuManager.wingBarrage(p);
			case HOMING_VOLLEY -> UryuManager.homingVolley(p);
			case STERNENSTAUB -> UryuManager.sternenstaub(p);
			default -> throw new IllegalArgumentException("not an Uryu ability: " + ability);
		}
	}
}
