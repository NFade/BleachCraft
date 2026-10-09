package dev.minebleach.reiatsutest.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Area queries, hit tests and effect helpers for abilities. Everything is server-side and loaded-chunks only. */
final class Targeting {
	private Targeting() {
	}

	/** Never the caster, a spectator, a dead entity, a pet of the caster, an immune type, or (by default) a player. */
	static boolean valid(ServerPlayerEntity caster, LivingEntity e) {
		if (e == caster || !e.isAlive() || e.isSpectator()) {
			return false;
		}
		if (e.getType().isIn(Tuning.IMMUNE)) {
			return false;
		}
		if (e instanceof Tameable t && caster.getUuid().equals(t.getOwnerUuid())) {
			return false;
		}
		if (e instanceof PlayerEntity) {
			return Tuning.affectPlayers && caster.getServer() != null && caster.getServer().isPvpEnabled();
		}
		return true;
	}

	/** Valid living entities whose bounding box touches {@code box}, nearest to {@code near} first, at most {@code max}. */
	static List<LivingEntity> inBox(ServerPlayerEntity caster, Box box, Vec3d near, int max) {
		ServerWorld world = caster.getServerWorld();
		List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, box, e -> valid(caster, e));
		found.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(near)));
		return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
	}

	static List<LivingEntity> sphere(ServerPlayerEntity caster, Vec3d center, double radius, int max) {
		Box box = Box.of(center, radius * 2, radius * 2, radius * 2);
		List<LivingEntity> in = new ArrayList<>();
		for (LivingEntity e : inBox(caster, box.expand(1.0), center, Integer.MAX_VALUE)) {
			// distance to the closest point of the bounding box, so big mobs on the edge count
			Box b = e.getBoundingBox();
			double dx = Math.max(Math.max(b.minX - center.x, 0), center.x - b.maxX);
			double dy = Math.max(Math.max(b.minY - center.y, 0), center.y - b.maxY);
			double dz = Math.max(Math.max(b.minZ - center.z, 0), center.z - b.maxZ);
			if (dx * dx + dy * dy + dz * dz <= radius * radius) {
				in.add(e);
				if (in.size() >= max) {
					break;
				}
			}
		}
		return in;
	}

	/** Horizontal circle of {@code radius} around {@code center}, within {@code height} blocks above and below. */
	static List<LivingEntity> cylinder(ServerPlayerEntity caster, Vec3d center, double radius, double height, int max) {
		Box box = new Box(center.x - radius, center.y - height, center.z - radius,
				center.x + radius, center.y + height, center.z + radius);
		List<LivingEntity> in = new ArrayList<>();
		for (LivingEntity e : inBox(caster, box, center, Integer.MAX_VALUE)) {
			double dx = e.getX() - center.x;
			double dz = e.getZ() - center.z;
			double r = radius + e.getWidth() * 0.5;
			if (dx * dx + dz * dz <= r * r) {
				in.add(e);
				if (in.size() >= max) {
					break;
				}
			}
		}
		return in;
	}

	/**
	 * Entities within {@code halfWidth} of the segment origin + dir * [from, to] (dir normalised). Entities in {@code skip} are left out.
	 */
	static List<LivingEntity> segment(ServerPlayerEntity caster, Vec3d origin, Vec3d dir, double from, double to,
			double halfWidth, int max, Set<Integer> skip) {
		Vec3d a = origin.add(dir.multiply(from));
		Vec3d b = origin.add(dir.multiply(to));
		Box box = new Box(a, b).expand(halfWidth + 1.0);
		List<LivingEntity> in = new ArrayList<>();
		for (LivingEntity e : inBox(caster, box, origin, Integer.MAX_VALUE)) {
			if (skip != null && skip.contains(e.getId())) {
				continue;
			}
			Vec3d c = e.getBoundingBox().getCenter().subtract(origin);
			double along = c.dotProduct(dir);
			double lateral = c.subtract(dir.multiply(along)).length();
			double slack = e.getWidth() * 0.5;
			if (along >= from - slack && along <= to + slack && lateral <= halfWidth + slack) {
				in.add(e);
				if (in.size() >= max) {
					break;
				}
			}
		}
		return in;
	}

	/** Block-only ray from {@code start} to {@code start + dir * range}: the hit point, or the far end on a miss. */
	static Vec3d rayPoint(ServerPlayerEntity caster, Vec3d start, Vec3d dir, double range) {
		Vec3d end = start.add(dir.multiply(range));
		BlockHitResult hit = caster.getServerWorld()
				.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
	}

	/** First valid living entity along the ray before any block, or null. */
	static LivingEntity firstOnRay(ServerPlayerEntity caster, Vec3d start, Vec3d dir, double range) {
		Vec3d end = rayPoint(caster, start, dir, range);
		Box box = new Box(start, end).expand(1.5);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : inBox(caster, box, start, Integer.MAX_VALUE)) {
			Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(start, end);
			if (hit.isPresent()) {
				double d = start.squaredDistanceTo(hit.get());
				if (d < bestDist) {
					bestDist = d;
					best = e;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ effects

	static void hurt(ServerPlayerEntity caster, LivingEntity target, float hp, RegistryKey<DamageType> type, boolean resetInvulnerability) {
		DamageSource src = caster.getServerWorld().getDamageSources().create(type, caster);
		if (resetInvulnerability) {
			target.timeUntilRegen = 0; // multi-hit abilities are meant to land every hit
		}
		target.damage(src, hp);
	}

	/** Slowness plus vanilla freezing ticks (frozen ticks decay by 2 per tick, so 2 * duration keeps them above the visual threshold). */
	static void chill(ServerPlayerEntity caster, LivingEntity target, int slownessAmplifier, int durationTicks) {
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, slownessAmplifier, false, true, true), caster);
		int want = target.getMinFreezeDamageTicks() + 2 * durationTicks;
		if (target.getFrozenTicks() < want) {
			target.setFrozenTicks(want);
		}
	}

	static void knockback(LivingEntity target, Vec3d from, double strength) {
		target.takeKnockback(strength, from.x - target.getX(), from.z - target.getZ());
		target.velocityModified = true;
	}
}
