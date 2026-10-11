package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoMath;
import dev.minebleach.reiatsutest.core.kido.KidoSpec;
import dev.minebleach.reiatsutest.core.state.StateEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Server effects of the kido spells (B5). The cost, cooldown and gating are already decided by the state machine
 * ({@code StateMachine.castKido}); this class turns an accepted {@link StateEvent.KidoCast} into world effects and the
 * {@code effect_event} for the clients, and keeps the burst of a Shakkaho that is still in flight.
 *
 * <p>Damage is {@code INDIRECT_MAGIC} attributed to the caster, a damage type that the block and the parry never touch
 * ({@code BlockParry.kindOf} only counts a direct melee hit), so a kido hit cannot be blocked or parried by the target's
 * sword. Nothing here damages blocks. Targets are chosen by {@link Targeting#valid} (never the caster, never an immune type,
 * players only when {@link Tuning#affectPlayers} and PvP allow it).
 */
public final class KidoManager {
	/** Safety cap of one burst, like the ability target caps. */
	private static final int MAX_TARGETS = 24;
	/** Slowness amplifier 6 (level 7) takes the speed to zero: the binding of Sai. */
	private static final int BIND_AMPLIFIER = 6;
	/** Horizontal push of a burst. */
	private static final double BURST_KNOCKBACK = 0.5;

	private record Burst(long dueTick, UUID caster, ServerWorld world, KidoSpec spec, Vec3d center) {
	}

	/** Result of tracing a line of flight: where it ends, how long it is and what stopped it (a {@code KidoMath.HIT_*}). */
	record Trace(Vec3d end, double length, int hitKind, LivingEntity entity) {
	}

	private static volatile KidoConfig config = KidoConfig.defaults();
	private static final List<Burst> BURSTS = new ArrayList<>();

	private KidoManager() {
	}

	/** The spell numbers in use (defaults before the server starts, then the user config file). */
	public static KidoConfig config() {
		return config;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			config = ModConfig.loadKido();
			BURSTS.clear();
			ReiatsuTest.LOGGER.info("[kido] config: enabled={} gcd={} ticks; cost/cooldown byakurai {}/{}, shakkaho {}/{}, sai {}/{}",
					config.enabled(), config.gcdTicks(), config.spec(KidoId.BYAKURAI).costTenths(),
					config.spec(KidoId.BYAKURAI).cooldownTicks(), config.spec(KidoId.SHAKKAHO).costTenths(),
					config.spec(KidoId.SHAKKAHO).cooldownTicks(), config.spec(KidoId.SAI).costTenths(),
					config.spec(KidoId.SAI).cooldownTicks());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> BURSTS.clear());
		ServerTickEvents.END_SERVER_TICK.register(KidoManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> cancel(handler.player.getUuid()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity p) {
				cancel(p.getUuid());
			}
		});
	}

	private static void cancel(UUID caster) {
		BURSTS.removeIf(b -> b.caster().equals(caster));
	}

	/** Bursts in flight (tests and the harness). */
	public static int pending() {
		return BURSTS.size();
	}

	// ------------------------------------------------------------------ cast

	/** Runs an accepted cast: trace from the player's own eye along the look vector (the client sends no aim). */
	static void cast(ServerPlayerEntity p, StateEvent.KidoCast k) {
		KidoSpec spec = k.spec();
		Vec3d eye = p.getEyePos();
		Vec3d dir = p.getRotationVec(1.0F).normalize();
		Trace t = trace(p, eye, dir, spec.range(), spec.width());
		switch (k.kido()) {
			case BYAKURAI -> {
				if (t.entity() != null) {
					Targeting.hurt(p, t.entity(), (float) spec.damage(), DamageTypes.INDIRECT_MAGIC, true);
				}
			}
			case SHAKKAHO -> {
				Vec3d center = t.hitKind() == KidoMath.HIT_BLOCK ? t.end().subtract(dir.multiply(KidoMath.SURFACE_BACKOFF)) : t.end();
				int delay = KidoMath.travelTicks(t.length(), spec.speed());
				if (delay <= 0) {
					burst(p, spec, center);
				} else {
					BURSTS.add(new Burst(p.getServer().getTicks() + delay, p.getUuid(), p.getServerWorld(), spec, center));
				}
			}
			case SAI -> {
				if (t.entity() != null) {
					t.entity().addStatusEffect(
							new StatusEffectInstance(StatusEffects.SLOWNESS, spec.statusTicks(), BIND_AMPLIFIER, false, true, true), p);
				}
			}
		}
		ServerFx.kido(p, k.kido(), k.seed(), eye, dir, t, spec);
	}

	// ------------------------------------------------------------------ tracing

	/**
	 * Line from {@code start} along {@code dir}: stops at the first block, or at the first valid living entity whose box
	 * (grown by {@code width}) the line crosses before that; a miss ends at {@code range}. An entity hit ends the line on the
	 * target's centre line when that lies on the way, so the beam and the ball visibly reach the body.
	 */
	static Trace trace(ServerPlayerEntity caster, Vec3d start, Vec3d dir, double range, double width) {
		Vec3d blockEnd = Targeting.rayPoint(caster, start, dir, range);
		double blockDist = start.distanceTo(blockEnd);
		boolean blockHit = blockDist < range - 1.0E-6;
		LivingEntity best = null;
		Vec3d bestPoint = null;
		double bestDist = Double.MAX_VALUE;
		Box box = new Box(start, blockEnd).expand(width + 1.0);
		for (LivingEntity e : Targeting.inBox(caster, box, start, Integer.MAX_VALUE)) {
			var hit = e.getBoundingBox().expand(width).raycast(start, blockEnd);
			if (hit.isPresent()) {
				double d = start.squaredDistanceTo(hit.get());
				if (d < bestDist) {
					bestDist = d;
					best = e;
					bestPoint = hit.get();
				}
			}
		}
		if (best != null) {
			double along = best.getBoundingBox().getCenter().subtract(start).dotProduct(dir);
			Vec3d end = along > 0.0 && along <= blockDist ? start.add(dir.multiply(along)) : bestPoint;
			return new Trace(end, KidoMath.usedLength(range, start.distanceTo(end)), KidoMath.HIT_ENTITY, best);
		}
		return new Trace(blockEnd, blockDist, blockHit ? KidoMath.HIT_BLOCK : KidoMath.HIT_NONE, null);
	}

	// ------------------------------------------------------------------ burst of the Shakkaho

	private static void tick(MinecraftServer srv) {
		if (BURSTS.isEmpty()) {
			return;
		}
		long now = srv.getTicks();
		List<Burst> due = null;
		for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext();) {
			Burst b = it.next();
			if (b.dueTick() <= now) {
				it.remove();
				if (due == null) {
					due = new ArrayList<>();
				}
				due.add(b);
			}
		}
		if (due == null) {
			return;
		}
		for (Burst b : due) {
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(b.caster());
			if (p == null || p.isDead() || p.getServerWorld() != b.world()) {
				continue; // the caster died, left or changed dimension while the ball was in flight: it fizzles
			}
			try {
				burst(p, b.spec(), b.center());
			} catch (RuntimeException ex) {
				ReiatsuTest.LOGGER.error("[kido] burst failed", ex);
			}
		}
	}

	/** The red burst: every valid living entity within the radius takes damage with falloff and burns; no block is touched. */
	static void burst(ServerPlayerEntity p, KidoSpec spec, Vec3d center) {
		List<LivingEntity> targets = Targeting.sphere(p, center, spec.radius(), MAX_TARGETS);
		for (LivingEntity e : targets) {
			double dist = distanceToBox(center, e.getBoundingBox());
			float dmg = (float) KidoMath.burstDamage(spec, dist);
			if (dmg > 0f) {
				Targeting.hurt(p, e, dmg, DamageTypes.INDIRECT_MAGIC, true);
			}
			if (spec.fireTicks() > 0) {
				e.setOnFireForTicks(spec.fireTicks());
			}
			Targeting.knockback(e, center, BURST_KNOCKBACK);
		}
	}

	static double distanceToBox(Vec3d point, Box b) {
		double dx = Math.max(Math.max(b.minX - point.x, 0), point.x - b.maxX);
		double dy = Math.max(Math.max(b.minY - point.y, 0), point.y - b.maxY);
		double dz = Math.max(Math.max(b.minZ - point.z, 0), point.z - b.maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
