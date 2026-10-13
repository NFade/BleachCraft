package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.combat.AbilityHitGate;
import dev.minebleach.reiatsutest.core.fx.HakuteikenAim;
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
	/** Centre of the petal cage (the aimed target's centre, else the aimed point lifted a little), the aim point for other abilities. */
	final Vec3d center;
	/** Length of the free ray of the ice spike (the look ray up to the first block, at most LENGTH). */
	final double rayLength;
	/**
	 * Per victim immunity window between two damage ticks of this cast (r8 item 2). Shared by every phase of the cast, so the landing
	 * burst and the flight of the Byakuya attack mode cannot stack ticks inside the window. Unused (a 1 tick window) by other abilities.
	 */
	final AbilityHitGate hitGate;
	/** The same cast read again at the strike tick (Hakuteiken), made once on first use and shared by its phases. */
	private CastContext atRelease;

	private CastContext(AbilityId ability, AbilitySpec spec, int seed, Vec3d origin, Vec3d eye, Vec3d dir, Vec3d flatDir, Vec3d aim, Vec3d center,
			AbilityHitGate hitGate) {
		this.ability = ability;
		this.spec = spec;
		this.seed = seed;
		this.origin = origin;
		this.eye = eye;
		this.dir = dir;
		this.flatDir = flatDir;
		this.aim = aim;
		this.center = center;
		this.hitGate = hitGate;
		this.rayLength = ability == AbilityId.ICE_SPIKE ? Math.min(spec.num(AbilityParams.LENGTH), eye.distanceTo(aim)) : 0.0;
	}

	static CastContext capture(ServerPlayerEntity p, AbilityId ability, AbilitySpec spec, int seed, int attackTickInterval) {
		return capture(p, ability, spec, seed, new AbilityHitGate(attackTickInterval));
	}

	/**
	 * The same cast looked at again NOW (r8 item 3, Shirafune): position, eye, look direction, aimed point and the ray length are read
	 * from the caster as he is at this moment, the spec, the seed and the hit gate stay those of the cast.
	 */
	CastContext resampled(ServerPlayerEntity p) {
		return capture(p, ability, spec, seed, hitGate);
	}

	/**
	 * The look at the strike of an ability whose effect event is already out (Hakuteiken: the condensing starts at the key press, the line hits
	 * 1.5 s later). Read ONCE, at the first phase that asks, then every later phase (the burst) gets the same object, so the line and the burst
	 * agree with the crosshair of the strike moment and with each other.
	 */
	CastContext releaseAim(ServerPlayerEntity p) {
		if (atRelease == null) {
			atRelease = resampled(p);
		}
		return atRelease;
	}

	private static CastContext capture(ServerPlayerEntity p, AbilityId ability, AbilitySpec spec, int seed, AbilityHitGate gate) {
		Vec3d eye = p.getEyePos();
		Vec3d dir = p.getRotationVec(1.0F).normalize();
		Vec3d flat = new Vec3d(dir.x, 0, dir.z);
		flat = flat.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : flat.normalize();
		double range = switch (ability) {
			case SHIRAFUNE, HAKUTEIKEN, ICE_SPIKE -> spec.num(AbilityParams.LENGTH);
			case MODE_ATTACK, SCATTER, CAGE -> spec.num(AbilityParams.AIM_RANGE);
			case IKOROSE, SNAKE_HOOK, BUTOU_RENJIN, KOROSE -> spec.num(AbilityParams.LENGTH); // Gin hitscan lines, like Shirafune
			case SPRENGER -> dev.minebleach.reiatsutest.core.uryu.UryuBalance.SPRENGER_RANGE; // Uryu: aimed ground point
			case LICHT_REGEN -> dev.minebleach.reiatsutest.core.uryu.UryuBalance.RAIN_RANGE;
			default -> 0.0;
		};
		Vec3d aim = range > 0 ? Targeting.rayPoint(p, eye, dir, range) : p.getPos();
		Vec3d center = aim;
		if (ability == AbilityId.CAGE) {
			// the cage closes around the first valid target on the ray; with none, around the aimed point (lifted when it is a block)
			net.minecraft.entity.LivingEntity target = Targeting.firstOnRay(p, eye, dir, range);
			if (target != null) {
				center = target.getBoundingBox().getCenter();
			} else if (eye.distanceTo(aim) < range - 0.05) {
				center = aim.add(0, 1.6, 0);
			}
		}
		return new CastContext(ability, spec, seed, p.getPos(), eye, dir, flat, aim, center, gate);
	}

	/**
	 * Parameters for the effect_event payload: aim point, then size = the main radius / length, size2 = the secondary one
	 * (scatter: tornado radius, lines: half width).
	 */
	float[] effectParams() {
		float size;
		float size2 = 0f;
		if (ability == AbilityId.CAGE) {
			// {centre, radius, first pulse (s), pulse count, spacing (s)}: the client draws the shell and one flash per pulse
			java.util.List<Integer> offs = spec.phaseOffsets();
			float first = offs.isEmpty() ? 0f : offs.get(0) / 20f;
			float spacing = offs.size() < 2 ? 0.5f : (offs.get(1) - offs.get(0)) / 20f;
			return new float[] {(float) center.x, (float) center.y, (float) center.z, spec.floatNum(AbilityParams.RADIUS), first, offs.size(), spacing};
		}
		switch (ability) {
			case TSUKISHIRO, MODE_ATTACK -> size = spec.floatNum(AbilityParams.RADIUS);
			case HAKUREN -> {
				size = spec.floatNum(AbilityParams.LENGTH);
				size2 = spec.floatNum(AbilityParams.HALF_WIDTH);
			}
			case SHIRAFUNE -> size = spec.floatNum(AbilityParams.LENGTH);
			case IKOROSE, SNAKE_HOOK, BUTOU_RENJIN, KOROSE -> size = spec.floatNum(AbilityParams.LENGTH); // Gin: the strike has its own event
			case HYAPPONZASHI -> size = spec.floatNum(AbilityParams.RADIUS);
			case ICE_SPIKE -> size = spec.floatNum(AbilityParams.LENGTH);
			case SCATTER -> {
				size = spec.floatNum(AbilityParams.RADIUS);
				size2 = spec.floatNum(AbilityParams.TORNADO_RADIUS);
			}
			case HAKUTEIKEN -> {
				size = spec.floatNum(AbilityParams.LENGTH);
				// S4: the length of the server ray really used (the white line ends exactly at the server burst)
				size2 = (float) HakuteikenAim.reach(size, eye.distanceTo(aim));
			}
			case SPRENGER -> { // Uryu: radius, seconds to the explosion
				size = (float) dev.minebleach.reiatsutest.core.uryu.UryuBalance.SPRENGER_RADIUS;
				size2 = dev.minebleach.reiatsutest.core.uryu.UryuBalance.SPRENGER_EXPLODE_AT / 20f;
			}
			case LICHT_REGEN -> { // radius, seconds of the barrage
				size = (float) dev.minebleach.reiatsutest.core.uryu.UryuBalance.RAIN_RADIUS;
				size2 = dev.minebleach.reiatsutest.core.uryu.UryuBalance.RAIN_TICKS / 20f;
			}
			case HIRENKYAKU -> size = (float) dev.minebleach.reiatsutest.core.uryu.UryuBalance.DASH_BLOCKS;
			case STERNENSTAUB -> {
				size = (float) dev.minebleach.reiatsutest.core.uryu.UryuBalance.STAR_LENGTH;
				size2 = (float) (dev.minebleach.reiatsutest.core.uryu.UryuBalance.STAR_HALF_WIDTH * 2.0);
			}
			default -> size = 0f;
		}
		return new float[] {(float) aim.x, (float) aim.y, (float) aim.z, size, size2};
	}
}
