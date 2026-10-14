package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.HitRules;
import dev.minebleach.reiatsutest.core.swarm.SweepMath;
import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import dev.minebleach.reiatsutest.core.uryu.Homing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Public door of the package-private {@link Targeting} and {@link DmgLog} for the Uryu glue in {@code server.uryu}. Nothing here has
 * logic of its own: the same target rules, the same one damage function, the same {@code [dmg]} log as every other ability.
 */
public final class UryuTargets {
	private UryuTargets() {
	}

	public static boolean valid(ServerPlayerEntity caster, LivingEntity e) {
		return Targeting.valid(caster, e);
	}

	/** Damages through the one damage function; true when health was really lost. */
	public static boolean hurt(ServerPlayerEntity caster, LivingEntity target, float hp, RegistryKey<DamageType> type, boolean resetInvulnerability) {
		return Targeting.hurt(caster, target, hp, type, resetInvulnerability) == HitRules.Outcome.DAMAGED;
	}

	public static List<LivingEntity> cylinder(ServerPlayerEntity caster, Vec3d center, double radius, double halfHeight, int max) {
		return Targeting.cylinder(caster, center, radius, halfHeight, max);
	}

	public static List<LivingEntity> sphere(ServerPlayerEntity caster, Vec3d center, double radius, int max) {
		return Targeting.sphere(caster, center, radius, max);
	}

	/** Valid entities near the segment origin + dir * [from, to], nearest to the origin first. */
	public static List<LivingEntity> segment(ServerPlayerEntity caster, Vec3d origin, Vec3d dir, double from, double to, double halfWidth, int max) {
		return Targeting.segment(caster, origin, dir, from, to, halfWidth, max, null);
	}

	/** Block-only ray: the hit point, or the far end on a miss. */
	public static Vec3d rayPoint(ServerPlayerEntity caster, Vec3d start, Vec3d dir, double range) {
		return Targeting.rayPoint(caster, start, dir, range);
	}

	/** Labels the {@code [dmg]} lines written by {@code body}. */
	public static void logged(String label, Runnable body) {
		DmgLog.begin(label);
		try {
			body.run();
		} finally {
			DmgLog.end();
		}
	}

	/**
	 * Targets of the passive homing: every valid living entity (the usual rules: not the caster, no spectator, no immune type, players
	 * as {@code affectPlayers} and PvP allow) whose box centre lies within {@code radius} of {@code pos}, without creative players. The
	 * aim point is the centre of the box. No log lines.
	 */
	public static List<Homing.Candidate> homingTargets(ServerWorld world, ServerPlayerEntity caster, V3 pos, double radius) {
		Box box = new Box(pos.x() - radius, pos.y() - radius, pos.z() - radius, pos.x() + radius, pos.y() + radius, pos.z() + radius);
		List<LivingEntity> near = world.getEntitiesByClass(LivingEntity.class, box,
				e -> Targeting.valid(caster, e) && !(e instanceof ServerPlayerEntity sp && sp.isCreative()));
		List<Homing.Candidate> out = new ArrayList<>(near.size());
		for (LivingEntity e : near) {
			Vec3d c = e.getBoundingBox().getCenter();
			if (c.squaredDistanceTo(pos.x(), pos.y(), pos.z()) <= radius * radius) {
				out.add(new Homing.Candidate(e.getId(), new V3(c.x, c.y, c.z), e instanceof net.minecraft.entity.player.PlayerEntity));
			}
		}
		out.sort(java.util.Comparator.comparingInt(Homing.Candidate::id)); // a fixed order, so the flight is deterministic
		return out;
	}

	/**
	 * Valid targets (the same rules as every ability, no per tick log line) whose hit box, grown by {@code radius}, the segment
	 * {@code from -> to} crosses in the given world, with the fraction of the segment where it first touches.
	 */
	public static List<ArrowSim.EntityHit> arrowHits(ServerWorld world, ServerPlayerEntity caster, V3 from, V3 to, double radius) {
		Box box = new Box(from.x(), from.y(), from.z(), to.x(), to.y(), to.z()).expand(radius + 0.5);
		List<LivingEntity> near = world.getEntitiesByClass(LivingEntity.class, box, e -> Targeting.valid(caster, e));
		List<ArrowSim.EntityHit> out = new ArrayList<>();
		for (LivingEntity e : near) {
			Box b = e.getBoundingBox();
			double t = SweepMath.sweepSphereVsBox(from, to, radius, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
			if (t >= 0.0) {
				out.add(new ArrowSim.EntityHit(e.getId(), t));
			}
		}
		return out;
	}
}
