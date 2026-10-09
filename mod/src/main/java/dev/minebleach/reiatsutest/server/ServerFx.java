package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Sends effect events and entity fx to the caster and the players tracking the caster. No VFX is rendered here. */
final class ServerFx {
	private ServerFx() {
	}

	static void effect(ServerPlayerEntity caster, int effectId, int seed, CastContext ctx) {
		Vec3d look = caster.getRotationVec(1.0F);
		float[] params = ctx != null ? ctx.effectParams() : new float[0];
		EffectEventS2C payload = new EffectEventS2C(effectId, caster.getId(), seed, caster.getX(), caster.getY(), caster.getZ(),
				(float) look.x, (float) look.y, (float) look.z, -1, caster.getServer().getTicks(), params);
		sendToViewers(caster, payload);
	}

	static void entities(ServerPlayerEntity caster, byte kind, int untilTick, List<? extends LivingEntity> targets) {
		if (targets.isEmpty()) {
			return;
		}
		int[] ids = new int[Math.min(targets.size(), EntityFxS2C.MAX_ENTITIES)];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = targets.get(i).getId();
		}
		sendToViewers(caster, new EntityFxS2C(kind, untilTick, ids));
	}

	private static void sendToViewers(ServerPlayerEntity caster, CustomPayload payload) {
		Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(caster));
		viewers.add(caster);
		for (ServerPlayerEntity v : viewers) {
			if (ServerPlayNetworking.canSend(v, payload.getId())) {
				ServerPlayNetworking.send(v, payload);
			}
		}
	}
}
