package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Everything a scheduled phase needs, captured from the server's own view of the caster when the cast was accepted
 * (the client never sends aim).
 */
final class CastContext {
	final AbilityId ability;
	/** The balance numbers of this ability (radii, damage, counts), captured with the cast. */
	final AbilitySpec spec;
	final int seed;
	/** Feet position at cast time. */
	final Vec3d origin;
	/** Eye position and unit look vector at cast time. */
	final Vec3d eye;
	final Vec3d dir;
	/** Unit horizontal direction (for ground waves). */
	final Vec3d flatDir;
	/** Aimed point (block ray) or the origin for abilities without aim. */
	final Vec3d aim;
	/** Entity ids already hit (single-hit abilities) and the ids frozen in phase 0 (shatter in a later phase). */
	final Set<Integer> hit = new HashSet<>();
	final Set<Integer> frozen = new LinkedHashSet<>();
	int tempBlocksPlaced;

	private CastContext(AbilityId ability, AbilitySpec spec, int seed, Vec3d origin, Vec3d eye, Vec3d dir, Vec3d flatDir, Vec3d aim) {
		this.ability = ability;
		this.spec = spec;
		this.seed = seed;
		this.origin = origin;
		this.eye = eye;
		this.dir = dir;
		this.flatDir = flatDir;
		this.aim = aim;
	}

	static CastContext capture(ServerPlayerEntity p, AbilityId ability, AbilitySpec spec, int seed) {
		Vec3d eye = p.getEyePos();
		Vec3d dir = p.getRotationVec(1.0F).normalize();
		Vec3d flat = new Vec3d(dir.x, 0, dir.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		double range = switch (ability) {
			case SHIRAFUNE, HAKUTEIKEN -> spec.num(AbilityParams.LENGTH);
			case MODE_ATTACK, SCATTER -> spec.num(AbilityParams.AIM_RANGE);
			default -> 0.0;
		};
		Vec3d aim = range > 0 ? Targeting.rayPoint(p, eye, dir, range) : p.getPos();
		return new CastContext(ability, spec, seed, p.getPos(), eye, dir, flat, aim);
	}

	/**
	 * Parameters for the effect_event payload: aim point, then size = the main radius / length, size2 = the secondary one
	 * (scatter: tornado radius, lines: half width).
	 */
	float[] effectParams() {
		float size;
		float size2 = 0f;
		switch (ability) {
			case TSUKISHIRO, ABSOLUTE_ZERO, MODE_ATTACK -> size = spec.floatNum(AbilityParams.RADIUS);
			case HAKUREN -> {
				size = spec.floatNum(AbilityParams.LENGTH);
				size2 = spec.floatNum(AbilityParams.HALF_WIDTH);
			}
			case SHIRAFUNE -> size = spec.floatNum(AbilityParams.LENGTH);
			case SCATTER -> {
				size = spec.floatNum(AbilityParams.RADIUS);
				size2 = spec.floatNum(AbilityParams.TORNADO_RADIUS);
			}
			case HAKUTEIKEN -> {
				size = spec.floatNum(AbilityParams.LENGTH);
				size2 = spec.floatNum(AbilityParams.RADIUS);
			}
			default -> size = 0f;
		}
		return new float[] {(float) aim.x, (float) aim.y, (float) aim.z, size, size2};
	}
}
