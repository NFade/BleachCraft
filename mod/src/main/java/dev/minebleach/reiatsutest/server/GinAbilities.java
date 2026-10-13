package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.gin.GinFxModes;
import dev.minebleach.reiatsutest.core.gin.GinGeometry;
import dev.minebleach.reiatsutest.core.gin.GinParams;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.server.gin.CastRoot;
import dev.minebleach.reiatsutest.server.gin.SelfPull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Server effects of Gin Ichimaru's abilities (design/GIN.md section 4). Called by {@link AbilityExecutor#run} once per phase, only while
 * the cast is still valid. Every length, damage, count and time comes from the ability's {@link AbilitySpec} (BalanceConfig, with the Gin
 * keys of {@link GinParams}). All of them are hitscan lines ({@link Targeting#firstOnRay}) or a swept sector: no projectile entity.
 * Damage is INDIRECT_MAGIC attributed to Gin (armor ignored like the other ability damage), so the one existing block rule of
 * {@link Targeting#hurt} applies to it (a sword block cuts it by {@code abilityBlockReduction}); the long-lived mechanisms (sliver,
 * recast, cover) are in {@link GinManager}.
 */
final class GinAbilities {
	private GinAbilities() {
	}

	static void run(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case IKOROSE -> ikorose(p, ctx);
			case HYAPPONZASHI -> hyapponzashi(p, phase, ctx);
			case SNAKE_HOOK -> snakeHook(p, ctx);
			case BUTOU_RENJIN -> renjin(p, phase, ctx);
			case KOROSE -> korose(p, ctx);
			case HAKUFUKU -> GinManager.hide(p, ctx.spec);
			default -> throw new IllegalArgumentException("not a Gin ability: " + ability);
		}
	}

	// ------------------------------------------------------------------ shikai

	/** Ikorose (Z): an instant line, the first target takes 5 HP and a push; Gin is rooted for 0.4 s after. */
	private static void ikorose(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		double length = sp.num(AbilityParams.LENGTH);
		boolean quiet = GinManager.takeNoFlash(p);
		LivingEntity target = Targeting.firstOnRay(p, ctx.eye, ctx.dir, length);
		if (target != null) {
			Targeting.hurt(p, target, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, false);
			Targeting.knockback(target, p.getPos(), sp.num(AbilityParams.KNOCKBACK));
		}
		GinManager.strikeFx(p, sp.effectId(), ctx.dir, lineEnd(p, ctx.eye, ctx.dir, length, target), target != null, quiet, 0);
		CastRoot.root(p, sp.intNum(GinParams.ROOT_TICKS), sp.intNum(GinParams.ROOT_AMP));
	}

	/**
	 * Hyapponzashi (H): phase 0 roots Gin for the whole cast (1.0 s wind-up, 0.8 s sweep, 0.5 s recovery); phases 1..n are the n equal slices
	 * of the arc, one every 2 ticks, from -arc/2 to +arc/2 around the look at the start of the sweep. Each target is hit once (3 HP), at
	 * most {@code maxTargets} in all.
	 */
	private static void hyapponzashi(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		if (phase == 0) {
			CastRoot.root(p, sp.intNum(GinParams.ROOT_TICKS), sp.intNum(GinParams.ROOT_AMP));
			return;
		}
		CastContext at = ctx.releaseAim(p); // the look when the sweep starts, shared by all slices
		int slices = sp.phaseOffsets().size() - 1;
		int i = phase - 1;
		double arc = sp.num(GinParams.ARC_DEGREES);
		double radius = sp.num(AbilityParams.RADIUS);
		if (i == 0) {
			int sweepTicks = sp.phaseOffsets().get(slices) - sp.phaseOffsets().get(1) + (slices > 1 ? sp.phaseOffsets().get(2) - sp.phaseOffsets().get(1) : 2);
			GinManager.fx(p, sp.effectId(), GinFxModes.SWEEP, at.dir, -1, false, (float) radius, (float) arc, sweepTicks);
		}
		double from = GinGeometry.sliceFrom(i, slices, arc);
		double to = GinGeometry.sliceTo(i, slices, arc);
		Vec3d feet = p.getPos();
		List<LivingEntity> near = Targeting.cylinder(p, feet.add(0, 1.0, 0), radius, sp.num(GinParams.ARC_HEIGHT), 64);
		int max = sp.intNum(AbilityParams.MAX_TARGETS);
		for (LivingEntity e : near) {
			if (at.hit.size() >= max) {
				break;
			}
			if (at.hit.contains(e.getId())) {
				continue;
			}
			double dx = e.getX() - feet.x;
			double dz = e.getZ() - feet.z;
			double dist = Math.hypot(dx, dz);
			double off = dist < 1.0E-4 ? 0.0 : GinGeometry.yawOffsetDeg(at.flatDir.x, at.flatDir.z, dx, dz);
			if (GinGeometry.sliceHit(off, from, to, GinGeometry.slackDeg(dist, e.getWidth() * 0.5))) {
				at.hit.add(e.getId());
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, false);
			}
		}
	}

	/**
	 * Snake Hook (B): a line up to 16 blocks. A block or a living target (mob or player) takes the hook: Gin is pulled toward it by server
	 * velocity and stops 1.5 blocks short (no teleport); a player is also hurt for 2 HP. A miss only shows the line.
	 */
	private static void snakeHook(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		double length = sp.num(AbilityParams.LENGTH);
		LivingEntity target = Targeting.firstOnRay(p, ctx.eye, ctx.dir, length);
		Vec3d blockEnd = Targeting.rayPoint(p, ctx.eye, ctx.dir, length);
		boolean block = ctx.eye.distanceTo(blockEnd) < length - 0.05;
		int pull = sp.intNum(GinParams.PULL_TICKS);
		if (target != null) {
			if (target instanceof PlayerEntity) {
				Targeting.hurt(p, target, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, false);
			}
			Vec3d end = lineEnd(p, ctx.eye, ctx.dir, length, target);
			SelfPull.start(p, target.getBoundingBox().getCenter(), target.getUuid(), sp.num(GinParams.STOP_SHORT), sp.num(GinParams.PULL_SPEED), pull);
			GinManager.fx(p, sp.effectId(), GinFxModes.HOOK, ctx.dir, target.getId(), false, (float) end.x, (float) end.y, (float) end.z, 1f, pull);
		} else if (block) {
			SelfPull.start(p, blockEnd, (UUID) null, sp.num(GinParams.STOP_SHORT), sp.num(GinParams.PULL_SPEED), pull);
			GinManager.fx(p, sp.effectId(), GinFxModes.HOOK, ctx.dir, -1, false, (float) blockEnd.x, (float) blockEnd.y, (float) blockEnd.z, 1f, pull);
		} else {
			GinManager.fx(p, sp.effectId(), GinFxModes.HOOK, ctx.dir, -1, false, (float) blockEnd.x, (float) blockEnd.y, (float) blockEnd.z, 0f, 0f);
		}
	}

	// ------------------------------------------------------------------ bankai

	/**
	 * Butou: Renjin (Z): every phase is one thrust (8 over 1.0 s) along the look of that tick, 24 blocks, 1.5 HP to the first target on the
	 * line, armor ignored, i-frames reset so every thrust lands. Phase 0 roots Gin for 1.2 s. A marked target hit by a thrust gets its
	 * sliver timer back to 12 s.
	 */
	private static void renjin(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		if (phase == 0) {
			CastRoot.root(p, sp.intNum(GinParams.ROOT_TICKS), sp.intNum(GinParams.ROOT_AMP));
		}
		CastContext now = ctx.resampled(p);
		double length = sp.num(AbilityParams.LENGTH);
		boolean quiet = phase == 0 && GinManager.takeNoFlash(p);
		LivingEntity target = Targeting.firstOnRay(p, now.eye, now.dir, length);
		if (target != null) {
			Targeting.hurt(p, target, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
			GinManager.onRenjinHit(p, target);
		}
		GinManager.strikeFx(p, sp.effectId(), now.dir, lineEnd(p, now.eye, now.dir, length, target), target != null, quiet, phase);
	}

	/**
	 * Korose, press 1 (H): a line of 48 blocks, 9 HP. When the thrust really cost the target at least {@code sliverMinHp} (so a Byakuya
	 * barrier that eats it leaves no sliver) and it is still alive, the sliver is placed and the recast becomes available for 12 s. Press 2
	 * is {@link GinManager#tryRecast}, called before the state machine ever sees the key.
	 */
	private static void korose(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		double length = sp.num(AbilityParams.LENGTH);
		boolean quiet = GinManager.takeNoFlash(p);
		LivingEntity target = Targeting.firstOnRay(p, ctx.eye, ctx.dir, length);
		if (target != null) {
			float before = target.getHealth() + target.getAbsorptionAmount();
			Targeting.hurt(p, target, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
			float lost = before - (target.getHealth() + target.getAbsorptionAmount());
			if (target.isAlive() && lost >= sp.floatNum(GinParams.SLIVER_MIN_HP)) {
				GinManager.place(p, target);
			} else {
				ReiatsuTest.LOGGER.info("[gin] Korose on {}: no sliver (lost {} HP, alive {})", target.getName().getString(), lost, target.isAlive());
			}
		}
		GinManager.strikeFx(p, sp.effectId(), ctx.dir, lineEnd(p, ctx.eye, ctx.dir, length, target), target != null, quiet, 0);
	}

	// ------------------------------------------------------------------ geometry

	/** Where a line from {@code eye} along {@code dir} ends: on the target's box when there is one, else on the first block or at {@code length}. */
	private static Vec3d lineEnd(ServerPlayerEntity p, Vec3d eye, Vec3d dir, double length, LivingEntity target) {
		Vec3d blockEnd = Targeting.rayPoint(p, eye, dir, length);
		if (target == null) {
			return blockEnd;
		}
		Optional<Vec3d> hit = target.getBoundingBox().expand(0.3).raycast(eye, blockEnd);
		return hit.orElse(target.getBoundingBox().getCenter());
	}
}
