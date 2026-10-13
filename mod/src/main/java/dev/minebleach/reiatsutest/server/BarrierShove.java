package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.BarrierPush;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Server side of the Byakuya barrier push (r8 item 6, {@code byakuya.shikai.mode_barrier}): while the owner's barrier stands, every
 * living entity that comes within {@code barrierPushRadius} of him is shoved outward (horizontal) with a small upward part, again
 * every {@code barrierPushIntervalTicks} ticks while it stays inside. Nobody is hurt: only the velocity changes (no damage source is
 * created, so the barrier, Rage and the {@code [dmg]} log never see it). The math is {@link BarrierPush} (pure, tested).
 *
 * <p>Left out: the owner, his own pets (tamed, owner uuid equal) and the vehicle or passengers he is attached to, Creative and
 * Spectator players, dead entities, entity types of the {@code reiatsu_test:immune} tag, and other barrier owners (their control
 * immunity forbids any push or knockback). It runs from the per-player tick of {@code ZanpakutoManager}, which is the existing tick
 * path of the barrier mode (the mode ends there by its timer), and ignores {@code Tuning.affectPlayers} and {@code pvp}: a push is not
 * damage. UNVERIFIED in play.
 */
final class BarrierShove {
	/** Half height of the pushed cylinder: an entity from 2.5 blocks below to 2.5 above the owner's feet. */
	static final double HALF_HEIGHT = 2.5;

	private BarrierShove() {
	}

	static void tick(ServerPlayerEntity owner, BalanceConfig cfg, int now) {
		double radius = cfg.barrierPushRadius();
		double strength = cfg.barrierPushStrength();
		if (radius <= 0.0 || strength <= 0.0 || (now + owner.getId()) % cfg.barrierPushIntervalTicks() != 0) {
			return;
		}
		double reach = radius + 2.0; // room for wide mobs: BarrierPush.inside adds the half width exactly
		Box box = new Box(owner.getX() - reach, owner.getY() - HALF_HEIGHT - 0.5, owner.getZ() - reach,
				owner.getX() + reach, owner.getY() + HALF_HEIGHT + 2.5, owner.getZ() + reach);
		List<LivingEntity> found = owner.getServerWorld().getEntitiesByClass(LivingEntity.class, box, e -> eligible(owner, e));
		for (LivingEntity e : found) {
			double dx = e.getX() - owner.getX();
			double dz = e.getZ() - owner.getZ();
			double dy = e.getY() - owner.getY();
			if (!BarrierPush.inside(dx, dy, dz, e.getWidth() * 0.5, radius, HALF_HEIGHT)) {
				continue;
			}
			double resistance = e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
			var v = e.getVelocity();
			double[] nv = BarrierPush.velocity(v.x, v.y, v.z, dx, dz, strength, cfg.barrierPushUp(), resistance, e.isOnGround(),
					e.getId() * 2.399963);
			e.setVelocity(nv[0], nv[1], nv[2]);
			e.velocityModified = true; // players and mobs: the tracker sends the new velocity at the end of the tick
		}
	}

	private static boolean eligible(ServerPlayerEntity owner, LivingEntity e) {
		if (e == owner || !e.isAlive() || e.isSpectator() || e.getType().isIn(Tuning.IMMUNE)) {
			return false;
		}
		if (e instanceof PlayerEntity p && (p.isCreative() || p.getAbilities().invulnerable)) {
			return false;
		}
		if (e instanceof Tameable t && owner.getUuid().equals(t.getOwnerUuid())) {
			return false; // the owner's own pets
		}
		if (owner.getVehicle() == e || e.getVehicle() == owner) {
			return false;
		}
		return !ZanpakutoManager.barrierControlImmune(e); // another barrier owner takes no push, as he takes no knockback
	}
}
