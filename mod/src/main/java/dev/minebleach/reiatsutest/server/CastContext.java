package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.AbilityId;
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

	private CastContext(AbilityId ability, int seed, Vec3d origin, Vec3d eye, Vec3d dir, Vec3d flatDir, Vec3d aim) {
		this.ability = ability;
		this.seed = seed;
		this.origin = origin;
		this.eye = eye;
		this.dir = dir;
		this.flatDir = flatDir;
		this.aim = aim;
	}

	static CastContext capture(ServerPlayerEntity p, AbilityId ability, int seed) {
		Vec3d eye = p.getEyePos();
		Vec3d dir = p.getRotationVec(1.0F).normalize();
		Vec3d flat = new Vec3d(dir.x, 0, dir.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		double range = switch (ability) {
			case SHIRAFUNE -> 8.0;
			case MODE_ATTACK -> 24.0;
			case SCATTER -> 40.0;
			case HAKUTEIKEN -> 20.0;
			default -> 0.0;
		};
		Vec3d aim = range > 0 ? Targeting.rayPoint(p, eye, dir, range) : p.getPos();
		return new CastContext(ability, seed, p.getPos(), eye, dir, flat, aim);
	}

	/** Parameters for the effect_event payload (aim point and a size). */
	float[] effectParams() {
		float size = switch (ability) {
			case TSUKISHIRO -> 4f;
			case HAKUREN -> 12f;
			case SHIRAFUNE -> 8f;
			case ABSOLUTE_ZERO -> 10f;
			case MODE_ATTACK -> 1.5f;
			case SCATTER -> 5f;
			case HAKUTEIKEN -> 20f;
			default -> 0f;
		};
		if (ability == AbilityId.HAKUTEIKEN) {
			// S4: the length of the server ray really used (the white line ends exactly at the server burst)
			return new float[] {(float) aim.x, (float) aim.y, (float) aim.z, size, (float) Math.min(20.0, eye.distanceTo(aim))};
		}
		return new float[] {(float) aim.x, (float) aim.y, (float) aim.z, size};
	}
}
