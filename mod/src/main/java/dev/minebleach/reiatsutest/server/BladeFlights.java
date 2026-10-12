package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ModConfig;
import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.HitRules;
import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.swarm.BladeFlight;
import dev.minebleach.reiatsutest.core.swarm.FlightConfig;
import dev.minebleach.reiatsutest.core.swarm.HitCooldown;
import dev.minebleach.reiatsutest.core.swarm.SteerGate;
import dev.minebleach.reiatsutest.core.swarm.SweepMath;
import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.net.EntityFxS2C;
import dev.minebleach.reiatsutest.net.SteerC2S;
import dev.minebleach.reiatsutest.net.SwarmHeadS2C;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Server authority of the Senbonzakura attack swarm flight (Byakuya shikai, ability mode_attack). One {@link BladeFlight} per
 * caster, started when the cast is accepted and ticked once per server tick:
 * <ul>
 * <li>steering: while the client holds the cast key it sends {@link SteerC2S} (about 10 Hz); {@link SteerGate} validates it, the
 * look point becomes the flight target and {@link BladeFlight} moves the head toward it within its turn and speed limits;</li>
 * <li>damage in flight: every tick the segment the head covered is swept (sphere against the bounding box of every valid living
 * entity, so fast blades cannot pass through a target) and each target is hurt through {@link Targeting#hurt} (same log, barrier,
 * creative, team and pvp rules as every ability), limited per target by {@link HitCooldown};</li>
 * <li>visual: {@link SwarmHeadS2C} with the head position every {@code sendEveryTicks} to the caster and the players tracking them.</li>
 * </ul>
 * The landing burst of {@link AbilityExecutor} stays as it was; it only follows the head when the swarm was steered.
 * The log lines are prefixed {@code [steer]}; hits are the usual {@code [dmg] byakuya.shikai.mode_attack#flight ...}.
 */
final class BladeFlights {
	static final String DMG_LABEL = "byakuya.shikai.mode_attack#flight";

	private static final class Active {
		final UUID caster;
		final long serial;
		final CastContext ctx;
		final ServerWorld world;
		final FlightConfig cfg;
		final double maxRange;
		final BladeFlight flight;
		final HitCooldown cooldown;
		final SteerGate gate;
		final Set<Integer> refusedLogged = new HashSet<>();
		final String name;
		int lastSent = Integer.MIN_VALUE / 2;
		int lastPhase = -1;
		int flightHits;
		int landingTargets;
		boolean wasHeld;
		boolean loggedClamp;
		boolean loggedBad;
		boolean loggedRate;
		boolean steerSeen;

		Active(ServerPlayerEntity p, CastContext ctx, long serial, FlightConfig cfg, int now) {
			this.caster = p.getUuid();
			this.serial = serial;
			this.ctx = ctx;
			this.world = p.getServerWorld();
			this.cfg = cfg;
			this.name = p.getName().getString();
			this.maxRange = ctx.spec.num(AbilityParams.AIM_RANGE) > 0 ? ctx.spec.num(AbilityParams.AIM_RANGE) : 19.0;
			this.flight = new BladeFlight(cfg, maxRange, v(ctx.eye), v(ctx.dir), v(ctx.aim));
			this.cooldown = new HitCooldown(cfg.hitCooldownTicks(), cfg.maxHitsPerTarget());
			this.gate = new SteerGate(cfg, now);
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static volatile FlightConfig config = FlightConfig.defaults();

	private BladeFlights() {
	}

	static FlightConfig config() {
		return config;
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			config = ModConfig.loadFlight();
			ACTIVE.clear();
			ReiatsuTest.LOGGER.info("[steer] config: enabled={} steer={} speed={} b/s turn={} deg/s hit={} hp r={} cd={} ticks maxHits={} return={} max={} ticks",
					config.enabled(), config.steerEnabled(), config.speed(), config.turnDegPerSec(), config.hitDamage(), config.hitRadius(),
					config.hitCooldownTicks(), config.maxHitsPerTarget(), config.returnTick(), config.maxTicks());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> ACTIVE.clear());
		ServerTickEvents.END_SERVER_TICK.register(BladeFlights::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> ACTIVE.remove(handler.player.getUuid()));
		ServerPlayNetworking.registerGlobalReceiver(SteerC2S.ID, (payload, ctx) -> onSteer(ctx.player(), payload));
	}

	// ------------------------------------------------------------------ start

	/** Called when a mode_attack cast was accepted ({@code serial} = the machine's cast serial, the flight ends when it changes). */
	static void start(ServerPlayerEntity p, CastContext ctx, long serial) {
		FlightConfig cfg = config;
		if (!cfg.enabled()) {
			return;
		}
		Active old = ACTIVE.remove(p.getUuid());
		if (old != null) {
			summary(old, "replaced");
		}
		Active a = new Active(p, ctx, serial, cfg, p.getServer().getTicks());
		ACTIVE.put(p.getUuid(), a);
		sendHead(p, a, p.getServer().getTicks());
		ReiatsuTest.LOGGER.info("[steer] {} flight start target=({}, {}, {}) range={}", a.name, f(ctx.aim.x), f(ctx.aim.y), f(ctx.aim.z), f(a.maxRange));
	}

	// ------------------------------------------------------------------ landing burst hooks (AbilityExecutor.modeAttack)

	/** Centre of the landing burst: the cast's aimed point, or the swarm head when the caster steered it. */
	static Vec3d landingCenter(ServerPlayerEntity p, CastContext ctx) {
		Active a = ACTIVE.get(p.getUuid());
		if (a != null && a.ctx == ctx && a.flight.steered()) {
			V3 h = a.flight.head();
			return new Vec3d(h.x(), h.y(), h.z());
		}
		return ctx.aim;
	}

	/** The landing burst hurt these targets: they start the flight hit cooldown (no double hit one tick later). */
	static void markLanding(ServerPlayerEntity p, CastContext ctx, List<LivingEntity> hit) {
		Active a = ACTIVE.get(p.getUuid());
		if (a == null || a.ctx != ctx) {
			return;
		}
		int now = p.getServer().getTicks();
		for (LivingEntity e : hit) {
			a.cooldown.markOther(e.getId(), now);
			a.landingTargets++;
		}
	}

	// ------------------------------------------------------------------ steer packets

	private static void onSteer(ServerPlayerEntity p, SteerC2S pl) {
		Active a = ACTIVE.get(p.getUuid());
		if (a == null || !a.cfg.steerEnabled()) {
			return; // nothing to steer (cast refused, flight over, disabled): silently ignored
		}
		int now = p.getServer().getTicks();
		Vec3d look = p.getRotationVec(1.0F);
		SteerGate.Result r = a.gate.accept(now, pl.holding(), new V3(pl.dx(), pl.dy(), pl.dz()), new V3(look.x, look.y, look.z));
		switch (r.verdict()) {
			case OK -> {
				if (!a.wasHeld) {
					a.wasHeld = true;
					a.steerSeen = true;
					ReiatsuTest.LOGGER.info("[steer] {} hold start tick={} dir=({}, {}, {})", a.name, a.flight.ticks(), f(r.dir().x()), f(r.dir().y()), f(r.dir().z()));
				}
				if (r.clamped() && !a.loggedClamp) {
					a.loggedClamp = true;
					ReiatsuTest.LOGGER.info("[steer] {} sent direction is more than {} deg from the server look, using the server look", a.name, f(a.cfg.steerLookToleranceDeg()));
				}
			}
			case RELEASE -> {
				if (a.wasHeld) {
					a.wasHeld = false;
					ReiatsuTest.LOGGER.info("[steer] {} hold end tick={} phase={}", a.name, a.flight.ticks(), a.flight.phase());
				}
			}
			case BAD_VECTOR -> {
				if (!a.loggedBad) {
					a.loggedBad = true;
					ReiatsuTest.LOGGER.warn("[steer] {} sent an invalid direction vector, packet dropped", a.name);
				}
			}
			case RATE_LIMITED -> {
				if (!a.loggedRate) {
					a.loggedRate = true;
					ReiatsuTest.LOGGER.warn("[steer] {} exceeds {} steer packets per second, extra packets dropped", a.name, a.cfg.steerRatePerSecond());
				}
			}
		}
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer srv) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		int now = srv.getTicks();
		for (Active a : new ArrayList<>(ACTIVE.values())) {
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(a.caster);
			StateMachine m = p == null ? null : ZanpakutoManager.machine(p);
			if (p == null || p.isDead() || m == null || m.castSerial() != a.serial || p.getServerWorld() != a.world) {
				ACTIVE.remove(a.caster);
				if (p != null) {
					sendEnd(p, a, now);
				}
				summary(a, "cancelled");
				continue;
			}
			try {
				run(a, p, now);
			} catch (RuntimeException ex) {
				ReiatsuTest.LOGGER.error("[steer] flight of {} failed", a.name, ex);
				ACTIVE.remove(a.caster);
				sendEnd(p, a, now); // (the clients close the head path and the swarm goes back to its orbit)
			}
		}
	}

	private static void run(Active a, ServerPlayerEntity p, int now) {
		Vec3d eyeV = p.getEyePos();
		V3 eye = v(eyeV);
		boolean held = a.cfg.steerEnabled() && a.gate.holding(now);
		a.flight.setHeld(held);
		if (held) {
			Vec3d d = new Vec3d(a.gate.dir().x(), a.gate.dir().y(), a.gate.dir().z());
			a.flight.steerTo(v(steerTarget(p, eyeV, d, a.maxRange)));
		} else if (a.wasHeld && !a.gate.holding(now)) {
			a.wasHeld = false; // packets stopped without a release (lost, client left): steering lapses by itself
			ReiatsuTest.LOGGER.info("[steer] {} hold lapsed (no steer packet for {} ticks)", a.name, a.cfg.steerTimeoutTicks());
		}
		BladeFlight.Step st = a.flight.step(eye);
		V3 from = st.from();
		V3 to = st.to();
		if (st.sweep()) {
			// outward the head cannot pass through blocks: stop it at the first one on this step. On the return (BACK) the head ignores blocks
			// (round 5: it used to stick in a wall and never come home); only the damage sweep of the return still stops at the block
			Vec3d fv = new Vec3d(from.x(), from.y(), from.z());
			Vec3d tv = new Vec3d(to.x(), to.y(), to.z());
			BlockHitResult br = a.world.raycast(new RaycastContext(fv, tv, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
			if (br.getType() == HitResult.Type.BLOCK) {
				if (st.leg() == HitCooldown.LEG_BACK) {
					Vec3d hit = br.getPos();
					to = new V3(hit.x, hit.y, hit.z);
				} else {
					Vec3d back = br.getPos().subtract(tv.subtract(fv).normalize().multiply(0.05));
					a.flight.clipHead(eye, v(back));
					to = a.flight.head();
				}
			}
			if (from.distanceTo(to) > 0.05) {
				sweepHits(a, p, now, from, to, st.leg());
			}
		}
		boolean changed = st.phase().code != a.lastPhase;
		if (changed || now - a.lastSent >= a.cfg.sendEveryTicks()) {
			sendHead(p, a, now);
		}
		if (a.flight.done()) {
			ACTIVE.remove(a.caster);
			summary(a, "returned");
		}
	}

	// ------------------------------------------------------------------ damage along the path

	private record Cand(LivingEntity e, double t) {
	}

	private static void sweepHits(Active a, ServerPlayerEntity p, int now, V3 from, V3 to, int leg) {
		double r = a.cfg.hitRadius();
		Vec3d fv = new Vec3d(from.x(), from.y(), from.z());
		Vec3d tv = new Vec3d(to.x(), to.y(), to.z());
		Box box = new Box(fv, tv).expand(r + 0.5);
		List<LivingEntity> near = a.world.getEntitiesByClass(LivingEntity.class, box, e -> e != p && e.isAlive());
		if (near.isEmpty()) {
			return;
		}
		List<Cand> cands = new ArrayList<>();
		for (LivingEntity e : near) {
			HitRules.Refusal why = Targeting.refusal(p, e);
			if (why != HitRules.Refusal.OK) {
				if (e instanceof net.minecraft.entity.player.PlayerEntity && why != HitRules.Refusal.DEAD && why != HitRules.Refusal.SELF
						&& why != HitRules.Refusal.OWN_PET && a.refusedLogged.add(e.getId())) {
					DmgLog.begin(DMG_LABEL);
					try {
						DmgLog.refused(p, e, why); // once per player and flight, not every tick
					} finally {
						DmgLog.end();
					}
				}
				continue;
			}
			Box b = e.getBoundingBox();
			double t = SweepMath.sweepSphereVsBox(from, to, r, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
			if (t >= 0.0) {
				cands.add(new Cand(e, t));
			}
		}
		if (cands.isEmpty()) {
			return;
		}
		cands.sort((x, y) -> Double.compare(x.t, y.t));
		List<LivingEntity> hurt = new ArrayList<>();
		int budget = a.cfg.maxTargetsPerTick();
		DmgLog.begin(DMG_LABEL);
		try {
			for (Cand c : cands) {
				if (budget <= 0) {
					break;
				}
				if (!a.cooldown.tryHit(c.e.getId(), now, leg)) {
					continue;
				}
				budget--;
				a.flightHits++;
				if (Targeting.hurt(p, c.e, (float) a.cfg.hitDamage(), DamageTypes.INDIRECT_MAGIC, true) == HitRules.Outcome.DAMAGED) {
					hurt.add(c.e);
				}
			}
		} finally {
			DmgLog.end();
		}
		ServerFx.entities(p, EntityFxS2C.HIT_PETAL, now, hurt);
	}

	/**
	 * The point the caster aims at: first block on the look ray, or the first valid living entity in front of it (so looking at a
	 * mob in front of a far wall steers the swarm onto the mob, not through it to the wall).
	 */
	private static Vec3d steerTarget(ServerPlayerEntity p, Vec3d eye, Vec3d dir, double range) {
		Vec3d end = Targeting.rayPoint(p, eye, dir, range);
		Box box = new Box(eye, end).expand(1.5);
		double best = eye.squaredDistanceTo(end);
		Vec3d point = end;
		for (LivingEntity e : p.getServerWorld().getEntitiesByClass(LivingEntity.class, box, x -> Targeting.valid(p, x))) {
			var hit = e.getBoundingBox().expand(0.3).raycast(eye, end);
			if (hit.isPresent()) {
				double d = eye.squaredDistanceTo(hit.get());
				if (d < best) {
					best = d;
					point = hit.get();
				}
			}
		}
		return point;
	}

	// ------------------------------------------------------------------ packets

	private static void sendHead(ServerPlayerEntity p, Active a, int now) {
		V3 h = a.flight.head();
		a.lastSent = now;
		a.lastPhase = a.flight.phase().code;
		send(p, new SwarmHeadS2C(p.getId(), (byte) a.flight.phase().code, now, (float) h.x(), (float) h.y(), (float) h.z()));
	}

	private static void sendEnd(ServerPlayerEntity p, Active a, int now) {
		V3 h = a.flight.head();
		send(p, new SwarmHeadS2C(p.getId(), (byte) BladeFlight.Phase.DONE.code, now, (float) h.x(), (float) h.y(), (float) h.z()));
	}

	private static void send(ServerPlayerEntity caster, SwarmHeadS2C payload) {
		Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(caster));
		viewers.add(caster);
		for (ServerPlayerEntity v : viewers) {
			if (ServerPlayNetworking.canSend(v, payload.getId())) {
				ServerPlayNetworking.send(v, payload);
			}
		}
	}

	private static void summary(Active a, String why) {
		ReiatsuTest.LOGGER.info("[steer] {} flight end ({}) ticks={} steered={} packets ok={} dropped={} clamped={} flightHits={} landingTargets={} lastPhase={}",
				a.name, why, a.flight.ticks(), a.flight.steered(), a.gate.accepted(), a.gate.dropped(), a.gate.clamped(), a.flightHits,
				a.landingTargets, a.flight.phase());
	}

	private static V3 v(Vec3d d) {
		return new V3(d.x, d.y, d.z);
	}

	private static String f(double d) {
		return String.format(Locale.ROOT, "%.2f", d);
	}
}
