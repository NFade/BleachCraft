package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.fx.HakuteikenAim;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import java.util.ArrayList;
import java.util.List;
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
		DmgLog.begin(ability.commandId + "#" + phase);
		try {
			if (ctx.spec.aimsAtRelease() && phase == ctx.spec.releasePhase()) {
				// r8 item 3: the cast time is over and the thrust leaves NOW, along the look of this moment (position, eye, direction, aimed
				// point and ray length are read again); the client effect event is sent here too, so it carries the same new aim
				ctx = ctx.resampled(p);
				ServerFx.effect(p, ctx.spec.effectId(), ctx.seed, ctx);
			}
			dispatch(p, ability, phase, ctx);
		} finally {
			DmgLog.end();
		}
	}

	private static void dispatch(ServerPlayerEntity p, AbilityId ability, int phase, CastContext ctx) {
		switch (ability) {
			case TSUKISHIRO -> tsukishiro(p, phase, ctx);
			case HAKUREN -> hakuren(p, phase, ctx);
			case SHIRAFUNE -> shirafune(p, ctx);
			case ICE_WALL -> iceWall(p, phase, ctx);
			case CAGE -> cage(p, ctx);
			case MODE_ATTACK -> modeAttack(p, ctx);
			case SCATTER -> scatter(p, phase, ctx);
			case HAKUTEIKEN -> hakuteiken(p, phase, ctx);
			case MODE_BARRIER, SENKEI -> { } // barrier lives in the damage hook; senkei is disabled
			case IKOROSE, HYAPPONZASHI, SNAKE_HOOK, BUTOU_RENJIN, KOROSE, HAKUFUKU -> GinAbilities.run(p, ability, phase, ctx);
			case HEILIG_PFEIL, SPRENGER, HIRENKYAKU, LICHT_REGEN, HOMING_VOLLEY, STERNENSTAUB -> UryuAbilities.run(p, ability, phase, ctx);
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
			List<LivingEntity> hit = frozenStillNear(p, ctx);
			for (LivingEntity e : hit) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
				e.setFrozenTicks(0);
			}
			ServerFx.entities(p, EntityFxS2C.HIT, now(p), hit);
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
		// R11 T5: the wave stops at an ice wall (the first wall block on the ground line); the wall takes the hit of the phase that reaches it
		double wallAt = IceWall.clipDistance(world, start, ctx.flatDir, sp.num(AbilityParams.LENGTH));
		if (d0 >= wallAt) {
			return;
		}
		if (d1 > wallAt) {
			d1 = wallAt;
			IceWall.absorbNear(world, start.add(ctx.flatDir.multiply(wallAt + 0.05)), 0.9, sp.num(AbilityParams.DAMAGE), "hakuren");
		}
		int room = sp.intNum(AbilityParams.MAX_TARGETS) - ctx.hit.size();
		if (room > 0) {
			List<LivingEntity> targets = Targeting.segment(p, start, ctx.flatDir, d0, d1, half, room, ctx.hit);
			for (LivingEntity e : targets) {
				ctx.hit.add(e.getId());
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
				Targeting.chill(p, e, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
			}
			ServerFx.entities(p, EntityFxS2C.SLOWED, now(p) + sp.intNum(AbilityParams.STATUS_TICKS), targets);
			ServerFx.entities(p, EntityFxS2C.HIT, now(p), targets);
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
		if (target == null) {
			// R11 T5: the thrust ends on an ice wall: the wall takes the hit (the ray is cut by the wall block itself, Targeting.rayPoint)
			IceWall.absorbNear(p.getServerWorld(), Targeting.rayPoint(p, ctx.eye, ctx.dir, sp.num(AbilityParams.LENGTH)), 0.6, sp.num(AbilityParams.DAMAGE), "shirafune");
		}
		if (target != null) {
			Targeting.hurt(p, target, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.FREEZE, false);
			Targeting.chill(p, target, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
			ServerFx.entities(p, EntityFxS2C.FROZEN, now(p) + sp.intNum(AbilityParams.STATUS_TICKS), List.of(target));
			ServerFx.entities(p, EntityFxS2C.HIT, now(p), List.of(target));
		}
	}

	/**
	 * Ice wall (Rukia bankai, R11 T5, it was the ice spike): one phase at the end of the cast time. A wall of real ice blocks grows where the caster
	 * looks at that moment ({@link IceWall#cast}); no damage, no block of the world is lost (the journal puts everything back).
	 */
	private static void iceWall(ServerPlayerEntity p, int phase, CastContext ctx) {
		if (phase == 0) {
			IceWall.cast(p, ctx);
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

	// ------------------------------------------------------------------ Byakuya

	private static void modeAttack(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		// the landing burst sits at the cast's aimed point, or at the swarm head when the caster steered it (BladeFlights)
		Vec3d landing = BladeFlights.landingCenter(p, ctx);
		IceWall.absorbNear(p.getServerWorld(), landing, 1.3, sp.num(AbilityParams.DAMAGE), "mode_attack"); // R11 T5: a landing on an ice wall spends its strength
		List<LivingEntity> inReach = Targeting.sphere(p, landing, sp.num(AbilityParams.RADIUS), sp.intNum(AbilityParams.MAX_TARGETS));
		// r8 item 2: a victim that took a damage tick of this cast less than byakuyaAttackTickInterval ticks ago (landing or flight) is skipped
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : inReach) {
			if (!ctx.hitGate.tryHit(e.getId(), now(p))) {
				continue;
			}
			Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
			hit.add(e);
		}
		BladeFlights.markLanding(p, ctx, hit);
		ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now(p), hit); // S5: hit sparks of the swarm envelope
		ctx.terrainReserved += AbilityTerrain.modeAttack(p, ctx, landing); // R11 T9: profile S, cap 60 per cast
	}

	/**
	 * Petal cage (Byakuya shikai, round 6): every phase is one pulse of the sphere of petals around {@code ctx.center}. Each pulse hurts
	 * everything inside (INDIRECT_MAGIC like the other petal abilities, players included when {@link Tuning#affectPlayers}) and renews
	 * the Slowness, so a target that stays in the cage is slowed for the whole 5 s and 3 s after it leaves.
	 */
	private static void cage(ServerPlayerEntity p, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		List<LivingEntity> hit = Targeting.sphere(p, ctx.center, sp.num(AbilityParams.RADIUS), sp.intNum(AbilityParams.MAX_TARGETS));
		for (LivingEntity e : hit) {
			Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
			Targeting.slow(p, e, sp.intNum(AbilityParams.SLOW_AMP), sp.intNum(AbilityParams.STATUS_TICKS));
		}
		ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now(p), hit);
	}

	private static void scatter(ServerPlayerEntity p, int phase, CastContext ctx) {
		AbilitySpec sp = ctx.spec;
		int max = sp.intNum(AbilityParams.MAX_TARGETS);
		if (phase < 2) {
			// tornado around the caster (caster safe by Targeting.valid)
			List<LivingEntity> hit = Targeting.sphere(p, p.getPos().add(0, 1.0, 0), sp.num(AbilityParams.TORNADO_RADIUS), max);
			for (LivingEntity e : hit) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE2), DamageTypes.INDIRECT_MAGIC, true);
			}
			ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now(p), hit); // S5: hit sparks on the tornado ticks
		} else {
			IceWall.absorbNear(p.getServerWorld(), ctx.aim, 1.3, sp.num(AbilityParams.DAMAGE), "scatter"); // R11 T5
			List<LivingEntity> hit = Targeting.sphere(p, ctx.aim, sp.num(AbilityParams.RADIUS), max);
			for (LivingEntity e : hit) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
				Targeting.knockback(e, ctx.aim, sp.num(AbilityParams.KNOCKBACK));
			}
			ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now(p), hit); // S5: hit sparks at the impact
			ctx.terrainReserved += AbilityTerrain.scatter(p, ctx); // R11 T9: profile M, a crater at the impact, cap 400
		}
	}

	private static void hakuteiken(ServerPlayerEntity p, int phase, CastContext ctx) {
		// the effect event left at the key press (the condensing is the cast time); the line and the burst are aimed at the strike tick, along the
		// look of that moment, like the client white line (HakuteikenFx.refreshAim), so both end on the crosshair, not on where it was 1.5 s ago
		ctx = ctx.releaseAim(p);
		AbilitySpec sp = ctx.spec;
		int max = sp.intNum(AbilityParams.MAX_TARGETS);
		if (phase == 0) {
			double length = HakuteikenAim.reach(sp.num(AbilityParams.LENGTH), ctx.eye.distanceTo(ctx.aim));
			if (length < sp.num(AbilityParams.LENGTH) - 0.05) {
				IceWall.absorbNear(p.getServerWorld(), ctx.aim, 1.3, sp.num(AbilityParams.DAMAGE2), "hakuteiken line"); // R11 T5: the line ends on a block: an ice wall takes it
			}
			List<LivingEntity> hit = Targeting.segment(p, ctx.eye, ctx.dir, 0.0, length, sp.num(AbilityParams.HALF_WIDTH), max, null);
			for (LivingEntity e : hit) {
				ctx.hit.add(e.getId());
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE2), DamageTypes.INDIRECT_MAGIC, true);
			}
			ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now(p), hit); // S5: hit sparks along the line (t 1.5)
			ctx.terrainReserved += AbilityTerrain.hakuteikenLine(p, ctx, length); // R11 T9: profile L, the tunnel along the line
		} else {
			IceWall.absorbNear(p.getServerWorld(), ctx.aim, 1.3, sp.num(AbilityParams.DAMAGE), "hakuteiken burst"); // R11 T5
			List<LivingEntity> hit = Targeting.sphere(p, ctx.aim, sp.num(AbilityParams.RADIUS), max);
			for (LivingEntity e : hit) {
				Targeting.hurt(p, e, sp.floatNum(AbilityParams.DAMAGE), DamageTypes.INDIRECT_MAGIC, true);
				Targeting.knockback(e, ctx.aim, sp.num(AbilityParams.KNOCKBACK));
			}
			ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now(p), hit); // S5: hit sparks at the burst (t 1.8)
			ctx.terrainReserved += AbilityTerrain.hakuteikenBurst(p, ctx); // R11 T9: profile L, the crater at the end, cap 700 for line and crater together
		}
	}
}
