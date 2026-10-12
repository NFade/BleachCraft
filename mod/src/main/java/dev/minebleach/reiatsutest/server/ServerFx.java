package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoMath;
import dev.minebleach.reiatsutest.core.kido.KidoSpec;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.core.state.StateEvent;
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
		params = RukiaServerFx.params(effectId, params, ctx);
		EffectEventS2C payload = new EffectEventS2C(effectId, caster.getId(), seed, caster.getX(), caster.getY(), caster.getZ(),
				(float) look.x, (float) look.y, (float) look.z, -1, caster.getServer().getTicks(), params);
		sendToViewers(caster, payload);
		RukiaServerFx.onEffect(caster, effectId, seed);
	}

	/** Shunpo effect (id 40): x y z = start, d = vector to the end, params = {distance, character code, stopped by wall (0/1)}. */
	static void shunpo(ServerPlayerEntity caster, StateEvent.ShunpoMove m, CharacterId who) {
		int tick = caster.getServer().getTicks();
		EffectEventS2C payload = new EffectEventS2C(EffectIds.SHUNPO, caster.getId(), tick, m.fromX(), m.fromY(), m.fromZ(),
				(float) (m.toX() - m.fromX()), (float) (m.toY() - m.fromY()), (float) (m.toZ() - m.fromZ()), -1, tick,
				new float[] {(float) m.distance(), who.code, m.stoppedByWall() ? 1f : 0f});
		sendToViewers(caster, payload);
	}

	/**
	 * Block feedback (id 41, BC5b): the defender's client plays the first person recoil, screen kick and sparks; others ignore it.
	 * x y z = the defender, d = horizontal vector to the attacker, params = {parried (1/0), damage of the hit}.
	 */
	static void blockFeedback(ServerPlayerEntity defender, LivingEntity attacker, boolean parried, float damage) {
		int tick = defender.getServer().getTicks();
		Vec3d to = attacker.getPos().subtract(defender.getPos());
		EffectEventS2C payload = new EffectEventS2C(EffectIds.BLOCK_FEEDBACK, defender.getId(), tick, defender.getX(), defender.getY(),
				defender.getZ(), (float) to.x, 0f, (float) to.z, attacker.getId(), tick, new float[] {parried ? 1f : 0f, damage});
		sendToViewers(defender, payload);
	}

	/**
	 * Kido effect (ids 50 to 52): x y z = the caster's eye, d = unit look direction, target = id of the entity hit or -1, params =
	 * {@link KidoMath#effectParams} (line length, travel ticks, burst radius, hit kind, speed, binding ticks).
	 */
	static void kido(ServerPlayerEntity caster, KidoId kido, int seed, Vec3d eye, Vec3d dir, KidoManager.Trace trace, KidoSpec spec) {
		float[] params = KidoMath.effectParams(spec, trace.length(), trace.hitKind());
		EffectEventS2C payload = new EffectEventS2C(kido.effectId, caster.getId(), seed, eye.x, eye.y, eye.z, (float) dir.x, (float) dir.y,
				(float) dir.z, trace.entity() == null ? -1 : trace.entity().getId(), caster.getServer().getTicks(), params);
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
