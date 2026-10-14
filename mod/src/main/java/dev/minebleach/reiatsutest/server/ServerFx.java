package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.FeedbackLimiter;
import dev.minebleach.reiatsutest.core.fx.BlockFxMath;
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

	/**
	 * Uryu, S-U4: the key press of Licht Regen or Sternenstaub (effect 78): x y z = the caster's feet, d = his look, params = {pre-aim x, y, z,
	 * effect id of the ability that follows at the release, seconds until the release}.
	 */
	static void uryuCastStart(ServerPlayerEntity caster, int seed, Vec3d pre, int abilityEffectId, float seconds) {
		Vec3d look = caster.getRotationVec(1.0F);
		EffectEventS2C payload = new EffectEventS2C(EffectIds.URYU_CAST_START, caster.getId(), seed, caster.getX(), caster.getY(), caster.getZ(),
				(float) look.x, (float) look.y, (float) look.z, -1, caster.getServer().getTicks(),
				new float[] {(float) pre.x, (float) pre.y, (float) pre.z, abilityEffectId, seconds});
		sendToViewers(caster, payload);
	}

	/**
	 * The bankai cleanup message (id 44): everybody who sees the caster drops every bankai effect of him. params = {trigger ordinal,
	 * character code}. Sent from the one server cleanup, whatever ended the bankai.
	 */
	static void bankaiEnd(ServerPlayerEntity caster, CharacterId who, dev.minebleach.reiatsutest.core.state.Trigger trigger) {
		int tick = caster.getServer().getTicks();
		EffectEventS2C payload = new EffectEventS2C(EffectIds.BANKAI_CLEANUP, caster.getId(), tick, caster.getX(), caster.getY(), caster.getZ(),
				0f, 0f, 0f, -1, tick, new float[] {trigger.ordinal(), who.code});
		sendToViewers(caster, payload);
	}

	/** Shunpo effect (id 40): x y z = start, d = vector to the end, params = {distance, character code, stopped by wall (0/1)}. */
	static void shunpo(ServerPlayerEntity caster, StateEvent.ShunpoMove m, CharacterId who) {
		int tick = caster.getServer().getTicks();
		EffectEventS2C payload = new EffectEventS2C(EffectIds.SHUNPO, caster.getId(), tick, m.fromX(), m.fromY(), m.fromZ(),
				(float) (m.toX() - m.fromX()), (float) (m.toY() - m.fromY()), (float) (m.toZ() - m.fromZ()), -1, tick,
				new float[] {(float) m.distance(), who.code, m.stoppedByWall() ? 1f : 0f});
		sendToViewers(caster, payload);
	}

	/** Rate limit of the block feedback (r8 fx2): 4 events per second per defender, a parry may bring it to 6. */
	private static final FeedbackLimiter BLOCK_LIMIT = FeedbackLimiter.standard();

	/**
	 * Block feedback (id 41) of a melee hit that the zanpakuto block took: everybody who sees the defender gets the sparks and the sound at
	 * the contact point, the defender's own client also gets the recoil, the screen kick and (parry) the flash. x y z = the CONTACT POINT
	 * (chest height, 0.6 blocks from the defender towards the attacker), d = horizontal vector from the defender to the attacker,
	 * target = the attacker, params = {variant ({@link BlockFxMath#BLOCK} / {@link BlockFxMath#PARRY}), damage of the hit, blade source (1)}.
	 * Returns false when the rate limit refused the event (the caller then plays its plain server sound).
	 */
	static boolean blockFeedback(ServerPlayerEntity defender, LivingEntity attacker, boolean parried, float damage) {
		return blockEvent(defender, attacker.getPos(), attacker.getId(), parried ? BlockFxMath.PARRY : BlockFxMath.BLOCK, damage, true);
	}

	/**
	 * The same event for an ABILITY or kido hit that the block cut by 30 percent (variant {@link BlockFxMath#ABILITY}): a lighter picture
	 * and sound; {@code bladeSource} is false for petals, ice, kido (no scrape), true when the hit came from a sword. The damage numbers
	 * are not touched here.
	 */
	static boolean blockFeedbackAbility(ServerPlayerEntity defender, LivingEntity caster, float damageCut, boolean bladeSource) {
		return blockEvent(defender, caster.getPos(), caster.getId(), BlockFxMath.ABILITY, damageCut, bladeSource);
	}

	private static boolean blockEvent(ServerPlayerEntity defender, Vec3d from, int sourceId, int variant, float damage, boolean blade) {
		int tick = defender.getServer().getTicks();
		if (tick % 200 == 0) {
			BLOCK_LIMIT.purge(tick);
		}
		if (!BLOCK_LIMIT.tryAcquire(defender.getUuid(), tick, variant == BlockFxMath.PARRY)) {
			return false;
		}
		Vec3d to = from.subtract(defender.getPos());
		double nx = to.x;
		double nz = to.z;
		if (Math.hypot(nx, nz) < 1e-4) { // attacker inside the defender: use the side he looks at
			Vec3d look = defender.getRotationVec(1.0F);
			nx = look.x;
			nz = look.z;
		}
		double[] c = BlockFxMath.contactPoint(defender.getX(), defender.getY(), defender.getZ(), defender.getHeight(), nx, nz, 0.6, 0.62);
		EffectEventS2C payload = new EffectEventS2C(EffectIds.BLOCK_FEEDBACK, defender.getId(), tick, c[0], c[1], c[2], (float) nx, 0f, (float) nz, sourceId,
				tick, new float[] {variant, damage, blade ? 1f : 0f});
		sendToViewers(defender, payload);
		return true;
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
