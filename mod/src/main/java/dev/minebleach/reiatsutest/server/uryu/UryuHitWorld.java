package dev.minebleach.reiatsutest.server.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import dev.minebleach.reiatsutest.server.UryuTargets;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * The arrows' view of one world: block ray with the collision shapes, entities through the usual target rules of the owner (never the
 * owner, no immune types, players only as {@code Tuning.affectPlayers} and PvP allow), and the aim point of a homing target.
 */
final class UryuHitWorld implements ArrowSim.HitWorld {
	private final MinecraftServer server;
	private final ServerWorld world;

	UryuHitWorld(MinecraftServer server, ServerWorld world) {
		this.server = server;
		this.world = world;
	}

	@Override
	public double raycastBlock(V3 from, V3 to) {
		Vec3d a = new Vec3d(from.x(), from.y(), from.z());
		Vec3d b = new Vec3d(to.x(), to.y(), to.z());
		double len = a.distanceTo(b);
		if (len < 1.0E-9) {
			return -1.0;
		}
		BlockHitResult r = world.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
		if (r.getType() == HitResult.Type.MISS) {
			return -1.0;
		}
		return Math.min(1.0, a.distanceTo(r.getPos()) / len);
	}

	@Override
	public List<ArrowSim.EntityHit> entitiesAlong(UUID owner, V3 from, V3 to, double radius) {
		ServerPlayerEntity caster = server.getPlayerManager().getPlayer(owner);
		if (caster == null || caster.isDead()) {
			return List.of();
		}
		return UryuTargets.arrowHits(world, caster, from, to, radius);
	}

	@Override
	public V3 targetPosition(int entityId) {
		Entity e = world.getEntityById(entityId);
		if (e instanceof LivingEntity le && le.isAlive()) {
			Box b = le.getBoundingBox();
			Vec3d c = b.getCenter();
			return new V3(c.x, c.y, c.z);
		}
		return null;
	}
}
