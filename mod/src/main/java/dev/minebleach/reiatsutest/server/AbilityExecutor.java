package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Server effects of the abilities (STATE_MACHINE section 4). Called by the scheduler once per phase, only while the
 * cast is still valid. Damage is attributed to the caster. No visuals are produced here (effect events do that). Every
 * radius, length, damage and target cap comes from the ability's {@link AbilitySpec} (BalanceConfig), none is hard coded.
 */
final class AbilityExecutor {
	private AbilityExecutor() {
	}

	static void run(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case TSUKISHIRO -> tsukishiro(p, phase, ctx);
			case HAKUREN -> hakuren(p, phase, ctx);
			case SHIRAFUNE -> shirafune(p, ctx);
			case ABSOLUTE_ZERO -> absoluteZero(p, phase, ctx);
			case MODE_ATTACK -> modeAttack(p, ctx);
			case SCATTER -> scatter(p, phase, ctx);
			case HAKUTEIKEN -> hakuteiken(p, phase, ctx);
			case MODE_BARRIER, SENKEI -> { } // barrier lives in the damage hook; senkei is disabled
		}
	}

	private static int now(ServerPlayerEntity p) {
		return p.getServer().getTicks();
	}

	// ------------------------------------------------------------------ Rukia

	private static void tsukishiro(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		if (phase == 0) {
			List<LivingEntity> targets = Targeting.cylinder(p, ctx.origin, sp.num(AbilityParams.RADIUS), sp.num(AbilityParams.HEIGHT),
					sp.intNum(AbilityParams.MAX_TARGETS));
			for (LivingEntity e : targets) {
				Targeting.chill(p, e, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
				ctx.frozen.add(e.getId());
			}
			ServerFx.entities(p, EntityFxS2C.ENCASED, now(p) + sp.intNum(AbilityParams.STATUS_TICKS), targets);
		} else {
			for (LivingEntity e : frozenStillNear(p, ctx)) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
				e.setFrozenTicks(0);
			}
		}
	}

	private static void hakuren(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		ServerWorld world = p.getServerWorld();
		int phases = sp.phaseOffsets().size();
		double seg = sp.num(AbilityParams.LENGTH) / phases;
		double half = sp.num(AbilityParams.HALF_WIDTH);
		double d0 = phase * seg;
		double d1 = (phase + 1) * seg;
		Vec3d start = ctx.origin.add(0, 0.9, 0);
		int room = sp.intNum(AbilityParams.MAX_TARGETS) - ctx.hit.size();
		if (room > 0) {
			List<LivingEntity> targets = Targeting.segment(p, start, ctx.flatDir, d0, d1, half, room, ctx.hit);
			for (LivingEntity e : targets) {
				ctx.hit.add(e.getId());
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
				Targeting.chill(p, e, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
			}
			ServerFx.entities(p, EntityFxS2C.SLOWED, now(p) + sp.intNum(AbilityParams.STATUS_TICKS), targets);
		}
		// frost layer on the path, rolled back after 3 s; the per-cast budget is spread over the phases
		int maxBlocks = sp.intNum(AbilityParams.MAX_BLOCKS);
		int budget = Math.min(maxBlocks, (phase + 1) * Math.max(1, maxBlocks / phases));
		Vec3d side = new Vec3d(-ctx.flatDir.z, 0, ctx.flatDir.x);
		int yHint = (int) Math.floor(ctx.origin.y);
		for (double d = d0; d < d1 && ctx.tempBlocksPlaced < budget; d += 1.0) {
			for (double off = -half + 0.5; off <= half - 0.4 && ctx.tempBlocksPlaced < budget; off += 1.0) {
				double x = ctx.origin.x + ctx.flatDir.x * (d + 0.5) + side.x * off;
				double z = ctx.origin.z + ctx.flatDir.z * (d + 0.5) + side.z * off;
				BlockPos pos = TempBlocks.surface(world, x, yHint, z);
				if (pos != null && TempBlocks.place(world, p.getUuid(), pos, false, now(p) + 60L)) {
					ctx.tempBlocksPlaced++;
				}
			}
		}
	}

	private static void shirafune(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		LivingEntity target = Targeting.firstOnRay(p, ctx.eye, ctx.dir, sp.num(AbilityParams.LENGTH));
		if (target != null) {
			Targeting.hurt(p, target, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
			Targeting.chill(p, target, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
			ServerFx.entities(p, EntityFxS2C.FROZEN, now(p) + sp.intNum(AbilityParams.STATUS_TICKS), List.of(target));
		}
	}

	private static void absoluteZero(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		if (phase == 0) {
			double radius = sp.num(AbilityParams.RADIUS);
			Vec3d center = ctx.origin.add(0, 1.0, 0);
			List<LivingEntity> targets = Targeting.sphere(p, center, radius, sp.intNum(AbilityParams.MAX_TARGETS));
			for (LivingEntity e : targets) {
				Targeting.chill(p, e, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
				ctx.frozen.add(e.getId());
			}
			ServerFx.entities(p, EntityFxS2C.ENCASED, now(p) + sp.intNum(AbilityParams.STATUS_TICKS), targets);
			// temporary ice blocks around (per cast MAX_BLOCKS here, per player Tuning.MAX_TEMP_BLOCKS_PER_PLAYER in TempBlocks),
			// rolled back 2.0 to 5.0 s later (4.0 to 7.0 s after the cast)
			ServerWorld world = p.getServerWorld();
			Random rnd = new Random(ctx.seed);
			int yHint = (int) Math.floor(ctx.origin.y);
			int maxBlocks = sp.intNum(AbilityParams.MAX_BLOCKS);
			for (int attempt = 0; attempt < 256 && ctx.tempBlocksPlaced < maxBlocks; attempt++) {
				double ang = rnd.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(rnd.nextDouble()) * radius;
				BlockPos pos = TempBlocks.surface(world, ctx.origin.x + Math.cos(ang) * r, yHint, ctx.origin.z + Math.sin(ang) * r);
				if (pos != null && TempBlocks.place(world, p.getUuid(), pos, true, now(p) + 40L + rnd.nextInt(61))) {
					ctx.tempBlocksPlaced++;
				}
			}
		} else {
			for (LivingEntity e : frozenStillNear(p, ctx)) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
				e.setFrozenTicks(0);
			}
		}
	}

	/** Entities frozen in phase 0 that are still alive, valid and near the cast. */
	private static List<LivingEntity> frozenStillNear(ServerPlayerEntity p, CastContext ctx) {
		List<LivingEntity> out = new ArrayList<>();
		ServerWorld world = p.getServerWorld();
		double split = ctx.spec.num(AbilityParams.SPLIT_RADIUS);
		for (int id : ctx.frozen) {
			Entity e = world.getEntityById(id);
			if (e instanceof LivingEntity le && Targeting.valid(p, le) && le.squaredDistanceTo(ctx.origin) <= split * split) {
				out.add(le);
			}
		}
		return out;
	}

	/** Passive of the Rukia bankai: hostile mobs within 3 blocks are slowed (every 10 ticks, no damage, players excluded). */
	static void rukiaBankaiPassive(ServerPlayerEntity p) {
		ServerWorld world = p.getServerWorld();
		Box box = p.getBoundingBox().expand(3.0);
		for (HostileEntity e : world.getEntitiesByClass(HostileEntity.class, box, h -> Targeting.valid(p, h))) {
			if (e.squaredDistanceTo(p) <= 9.0) {
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 15, 0, false, false, true), p);
				if (e.getFrozenTicks() < 100) {
					e.setFrozenTicks(100); // below the 140 tick damage threshold
				}
			}
		}
	}

	// ------------------------------------------------------------------ Byakuya

	private static void modeAttack(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		for (LivingEntity e : Targeting.sphere(p, ctx.aim, sp.num(AbilityParams.RADIUS), sp.intNum(AbilityParams.MAX_TARGETS))) {
			Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
		}
	}

	private static void scatter(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		int max = sp.intNum(AbilityParams.MAX_TARGETS);
		if (phase < 2) {
			// tornado around the caster (caster safe by Targeting.valid)
			for (LivingEntity e : Targeting.sphere(p, p.getPos().add(0, 1.0, 0), sp.num(AbilityParams.TORNADO_RADIUS), max)) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE2), DamageTypes.INDIRECT_MAGIC, true);
			}
		} else {
			for (LivingEntity e : Targeting.sphere(p, ctx.aim, sp.num(AbilityParams.RADIUS), max)) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
				Targeting.knockback(e, ctx.aim, sp.num(AbilityParams.KNOCKBACK));
			}
		}
	}

	private static void hakuteiken(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		int max = sp.intNum(AbilityParams.MAX_TARGETS);
		if (phase == 0) {
			double length = ctx.eye.distanceTo(ctx.aim);
			for (LivingEntity e : Targeting.segment(p, ctx.eye, ctx.dir, 0.0, length, sp.num(AbilityParams.HALF_WIDTH), max, null)) {
				ctx.hit.add(e.getId());
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE2), DamageTypes.INDIRECT_MAGIC, true);
			}
		} else {
			for (LivingEntity e : Targeting.sphere(p, ctx.aim, sp.num(AbilityParams.RADIUS), max)) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
				Targeting.knockback(e, ctx.aim, sp.num(AbilityParams.KNOCKBACK));
			}
		}
	}
}
