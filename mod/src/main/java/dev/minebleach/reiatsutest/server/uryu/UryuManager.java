package dev.minebleach.reiatsutest.server.uryu;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import dev.minebleach.reiatsutest.core.uryu.Focus;
import dev.minebleach.reiatsutest.core.uryu.SeeleCombo;
import dev.minebleach.reiatsutest.core.uryu.WingBarrage;
import dev.minebleach.reiatsutest.core.uryu.Shots;
import dev.minebleach.reiatsutest.core.uryu.SprengerTrap;
import dev.minebleach.reiatsutest.core.uryu.Sternenstaub;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import dev.minebleach.reiatsutest.core.uryu.Volley;
import dev.minebleach.reiatsutest.net.UryuArrowsS2C;
import dev.minebleach.reiatsutest.server.UryuTargets;
import dev.minebleach.reiatsutest.server.ZanpakutoManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Server side of Uryu Ishida (design/URYU.md, core rules in {@code core.uryu}): the Focus counter of every Uryu, one {@link ArrowSim}
 * per world stepped once per server tick, the Sprenger pentagons, the wing barrage, the Seele Schneider combo and the six ability
 * bodies called by {@code UryuAbilities}. Damage goes through the one damage function ({@link UryuTargets#hurt}), so the caster is
 * safe, the usual target rules apply and the Byakuya barrier / block rules cut it like any other ability.
 *
 * <p>Cleanup: death, logout, a state that is no longer Uryu's (sealed, BASE for the ability effects), the end of the bankai
 * ({@link #onBankaiEnd}), the server stopping. All state is in plain statics and touched from the server thread only.
 */
public final class UryuManager {
	private static final class Data {
		final Focus focus = new Focus();
		Vec3d lastPos;
		int lastShot = Integer.MIN_VALUE / 2;
		int shown;
		ZanpakutoState lastState = ZanpakutoState.SEALED;
	}

	private record Trap(SprengerTrap trap, RegistryKey<World> world) {
	}

	/** The barrage of one player: pending shots of the current cycle. */
	private static final class Wing {
		final UUID owner;
		final RegistryKey<World> world;
		final int start;
		List<WingBarrage.Shot> pending = new ArrayList<>();

		Wing(UUID owner, RegistryKey<World> world, int start) {
			this.owner = owner;
			this.world = world;
			this.start = start;
		}
	}

	/** The Seele Schneider combo of one player: start tick and the horizontal lunge direction. */
	private record Seele(int start, V3 dir) {
	}

	private static final Map<UUID, Data> PLAYERS = new HashMap<>();
	private static final Map<RegistryKey<World>, ArrowSim> SIMS = new HashMap<>();
	private static final List<Trap> TRAPS = new ArrayList<>();
	private static final List<Wing> WINGS = new ArrayList<>();
	private static final Map<UUID, Seele> SEELES = new HashMap<>();

	private UryuManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(UryuManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> forget(handler.player.getUuid()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity p) {
				cancelAll(p.getUuid());
				Data d = PLAYERS.get(p.getUuid());
				if (d != null) {
					d.focus.reset();
					d.shown = 0;
					d.lastState = ZanpakutoState.SEALED;
				}
			}
		});
		// being pushed takes the Focus: the knockback hook ({@link #onPushed}, called from LivingEntityMixin) replaces the old damage hook
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> clearAll());
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> clearAll());
	}

	// ------------------------------------------------------------------ gates

	private static boolean isUryu(StateMachine m) {
		return m != null && m.character() == CharacterId.URYU && m.state() != ZanpakutoState.SEALED;
	}

	private static boolean arterie(StateMachine m) {
		return m.state() == ZanpakutoState.SHIKAI || m.state() == ZanpakutoState.BANKAI;
	}

	private static Data data(ServerPlayerEntity p) {
		return PLAYERS.computeIfAbsent(p.getUuid(), k -> new Data());
	}

	/** Stacks of Focus the player has right now (0 for everybody who is not Uryu). */
	public static int focusOf(ServerPlayerEntity p) {
		Data d = PLAYERS.get(p.getUuid());
		return d == null || !isUryu(ZanpakutoManager.machine(p)) ? 0 : d.focus.stacks();
	}

	/**
	 * Checked by {@code ZanpakutoManager.performCast} BEFORE the state machine charges anything: Sternenstaub needs all 3 stacks of Focus,
	 * so a cast without them is refused (no cost, no cooldown) and the player is told. True for every other ability.
	 */
	public static boolean allowsCast(ServerPlayerEntity p, AbilityId ability) {
		if (ability != AbilityId.STERNENSTAUB) {
			return true;
		}
		if (Sternenstaub.canCast(focusOf(p))) {
			return true;
		}
		p.sendMessage(Text.literal("Sternenstaub needs Focus 3 (stand still)"), true);
		ReiatsuTest.LOGGER.info("[uryu] {} sternenstaub refused: focus {}/{}", p.getName().getString(), focusOf(p), UryuBalance.STAR_FOCUS);
		return false;
	}

	// ------------------------------------------------------------------ cleanup

	private static void forget(UUID id) {
		cancelAll(id);
		PLAYERS.remove(id);
		UryuNet.forget(id);
	}

	/** Everything of one Uryu goes: pentagons, barrage, dash, arrows in flight. */
	static void cancelAll(UUID id) {
		TRAPS.removeIf(t -> t.trap().owner().equals(id));
		WINGS.removeIf(w -> w.owner.equals(id));
		SEELES.remove(id);
		for (ArrowSim sim : SIMS.values()) {
			sim.removeOwned(id);
		}
	}

	/** The bankai of this player is over (any reason): the barrage and the bankai arrows in the air stop. */
	public static void onBankaiEnd(UUID id) {
		WINGS.removeIf(w -> w.owner.equals(id));
		for (ArrowSim sim : SIMS.values()) {
			sim.removeOwned(id, ArrowSim.Kind.WING);
			sim.removeOwned(id, ArrowSim.Kind.VOLLEY);
		}
	}

	private static void clearAll() {
		PLAYERS.clear();
		SIMS.clear();
		TRAPS.clear();
		WINGS.clear();
		SEELES.clear();
		UryuNet.clear();
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer srv) {
		int now = srv.getTicks();
		for (ServerPlayerEntity p : srv.getPlayerManager().getPlayerList()) {
			tickPlayer(p, now);
		}
		if (!WINGS.isEmpty()) {
			tickWings(srv, now);
		}
		if (!SEELES.isEmpty()) {
			tickSeele(srv, now);
		}
		if (!TRAPS.isEmpty()) {
			tickTraps(srv, now);
		}
		stepArrows(srv, now);
	}

	private static void tickPlayer(ServerPlayerEntity p, int now) {
		Data d = PLAYERS.get(p.getUuid());
		StateMachine m = ZanpakutoManager.machine(p);
		boolean active = isUryu(m) && !p.isDead() && !p.isSpectator();
		if (!active) {
			if (d != null && d.lastState != ZanpakutoState.SEALED) {
				cancelAll(p.getUuid()); // sealed again (or another character): nothing of Uryu stays
				d.focus.reset();
				d.lastState = ZanpakutoState.SEALED;
				show(p, d);
			}
			return;
		}
		if (d == null) {
			d = data(p);
		}
		ZanpakutoState st = m.state();
		if (st != d.lastState) {
			if (st == ZanpakutoState.BASE) {
				cancelAll(p.getUuid()); // BASE has no abilities: the pentagons and the barrage of the shikai are gone
			} else if (d.lastState == ZanpakutoState.BANKAI) {
				onBankaiEnd(p.getUuid());
			}
			d.lastState = st;
		}
		Vec3d pos = p.getPos();
		// horizontal speed only: a step up or a fall is judged by the ground flag, not by the speed
		double speed = d.lastPos == null ? 1.0 : Math.hypot(pos.x - d.lastPos.x, pos.z - d.lastPos.z);
		d.lastPos = pos;
		d.focus.tick(p.isOnGround(), p.isSprinting(), p.isSneaking(), speed, arterie(m));
		show(p, d);
	}

	/** Syncs the stack count to the owner (HUD pips), only when it changed. */
	private static void show(ServerPlayerEntity p, Data d) {
		int s = d.focus.stacks();
		if (s == d.shown) {
			return;
		}
		d.shown = s;
		UryuNet.sendFocus(p, s, p.getServer() == null ? 0 : p.getServer().getTicks());
	}

	/**
	 * Called by {@code LivingEntityMixin.takeKnockback} (server): a hit or any ability of another character pushes a player. Takes the
	 * Focus of an Uryu. Nothing for anybody else.
	 */
	public static void onPushed(LivingEntity e) {
		if (e instanceof ServerPlayerEntity p) {
			Data d = PLAYERS.get(p.getUuid());
			if (d != null && d.focus.reset()) {
				show(p, d);
			}
		}
	}

	/**
	 * Called by {@code ZanpakutoManager} right after a shunpo teleported the player: the jump in position is not walking, so Focus is
	 * neither reset nor built for the next ticks ("shunpo does not reset Focus").
	 */
	public static void onShunpo(ServerPlayerEntity p, double toX, double toY, double toZ) {
		Data d = PLAYERS.get(p.getUuid());
		if (d != null) {
			d.lastPos = new Vec3d(toX, toY, toZ);
			d.focus.grace();
		}
	}

	// ------------------------------------------------------------------ arrows

	private static ArrowSim sim(ServerWorld w) {
		return SIMS.computeIfAbsent(w.getRegistryKey(), k -> new ArrowSim());
	}

	private static void stepArrows(MinecraftServer srv, int now) {
		if (SIMS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<RegistryKey<World>, ArrowSim>> it = SIMS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<RegistryKey<World>, ArrowSim> en = it.next();
			ServerWorld w = srv.getWorld(en.getKey());
			ArrowSim sim = en.getValue();
			if (w == null) {
				it.remove();
				continue;
			}
			if (sim.isEmpty()) {
				UryuNet.sendEmpty(w, now);
				it.remove();
				continue;
			}
			List<ArrowSim.Event> events = sim.step(new UryuHitWorld(srv, w));
			for (ArrowSim.Event e : events) {
				try {
					onEvent(srv, w, e, now);
				} catch (RuntimeException ex) {
					ReiatsuTest.LOGGER.error("[uryu] arrow event {} failed", e.type(), ex);
				}
			}
			UryuNet.sendArrows(w, sim, now);
		}
	}

	private static void onEvent(MinecraftServer srv, ServerWorld w, ArrowSim.Event e, int now) {
		switch (e.type()) {
			case HIT -> {
				ServerPlayerEntity caster = srv.getPlayerManager().getPlayer(e.owner());
				Entity target = w.getEntityById(e.targetId());
				if (caster == null || !(target instanceof LivingEntity le)) {
					return;
				}
				UryuNet.sendImpact(w, e.to(), UryuArrowsS2C.IMPACT_ENTITY, e.kind().ordinal(), now);
				boolean reset = e.kind() == ArrowSim.Kind.WING || e.kind() == ArrowSim.Kind.VOLLEY; // many small arrows: each lands through the i-frames
				RegistryKey<DamageType> type = DamageTypes.ARROW;
				UryuTargets.logged("uryu.arrow." + e.kind().name().toLowerCase(java.util.Locale.ROOT),
						() -> UryuTargets.hurt(caster, le, e.damage(), type, reset));
			}
			case MOVED -> {
				if (!TRAPS.isEmpty() && SprengerTrap.detonates(e.focus())) {
					detonateTouched(srv, w, e);
				}
			}
			case BLOCKED -> UryuNet.sendImpact(w, e.to(), UryuArrowsS2C.IMPACT_BLOCK, e.kind().ordinal(), now);
			default -> { }
		}
	}

	private static void spawn(ServerWorld w, ArrowSim.Spec spec) {
		if (sim(w).spawn(spec) == null) {
			ReiatsuTest.LOGGER.warn("[uryu] arrow cap reached, shot dropped");
		}
	}

	private static V3 v3(Vec3d v) {
		return new V3(v.x, v.y, v.z);
	}

	private static Vec3d vec(V3 v) {
		return new Vec3d(v.x(), v.y(), v.z());
	}

	// ------------------------------------------------------------------ bow shot and Z

	/**
	 * The release of the drawn Heilig Bogen (BASE, SHIKAI and BANKAI), called on the SERVER by {@code UryuCrossItem.onStoppedUsing}
	 * (the server sees the release of the use key): the vanilla power curve of the draw time, nothing under 0.1 power, the damage and
	 * the speed scale with the power, uses and clears the Focus, no reiatsu, no ammo. False when nothing was fired.
	 */
	public static boolean releaseBow(ServerPlayerEntity p, int drawTicks) {
		StateMachine m = ZanpakutoManager.machine(p);
		if (!isUryu(m) || p.isDead() || p.isSpectator()) {
			return false;
		}
		double power = BowDraw.power(drawTicks);
		if (!BowDraw.fires(power)) {
			return false;
		}
		Data d = data(p);
		int now = p.getServer().getTicks();
		if (now - d.lastShot < UryuBalance.BASIC_COOLDOWN) {
			return false;
		}
		d.lastShot = now;
		int stacks = d.focus.consume();
		show(p, d);
		Shots.Params sp = Shots.basic(stacks, arterie(m), power);
		shoot(p, sp, ArrowSim.Kind.BASIC);
		p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F,
				1.0F / (p.getRandom().nextFloat() * 0.4F + 1.2F) + (float) power * 0.5F);
		ReiatsuTest.LOGGER.info("[uryu] {} bow release ticks={} power={} focus={} damage={} speed={} homing={}deg", p.getName().getString(), drawTicks,
				String.format(java.util.Locale.ROOT, "%.2f", power), stacks, sp.damage(), String.format(java.util.Locale.ROOT, "%.2f", sp.speed()),
				String.format(java.util.Locale.ROOT, "%.1f", Math.toDegrees(sp.homing().turnRad())));
		return true;
	}

	/** Heilig Pfeil: the homing arrow (Z, SHIKAI): 7 HP base, strong homing, all Focus stacks used. */
	public static void focusShot(ServerPlayerEntity p) {
		Data d = data(p);
		int stacks = d.focus.consume();
		show(p, d);
		Shots.Params sp = Shots.focusShot(stacks);
		shoot(p, sp, ArrowSim.Kind.FOCUS);
		p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.2F, 1.6F);
		ReiatsuTest.LOGGER.info("[uryu] {} homing arrow (Z) focus={} damage={} speed={} pierce={} homing={}deg radius={}", p.getName().getString(), stacks, sp.damage(),
				String.format(java.util.Locale.ROOT, "%.2f", sp.speed()), sp.pierce(), String.format(java.util.Locale.ROOT, "%.1f", Math.toDegrees(sp.homing().turnRad())),
				sp.homing().radius());
	}

	private static void shoot(ServerPlayerEntity p, Shots.Params sp, ArrowSim.Kind kind) {
		Vec3d eye = p.getEyePos();
		Vec3d look = p.getRotationVec(1.0F).normalize();
		V3 pos = new V3(eye.x + look.x * 0.3, eye.y - 0.1 + look.y * 0.3, eye.z + look.z * 0.3);
		V3 vel = new V3(look.x * sp.speed(), look.y * sp.speed(), look.z * sp.speed());
		spawn(p.getServerWorld(), new ArrowSim.Spec(p.getUuid(), kind, pos, vel, sp.gravity(), sp.damage(), sp.life(), sp.pierce(),
				UryuBalance.ARROW_RADIUS, -1, 0.0, sp.focus(), sp.homing()));
	}

	// ------------------------------------------------------------------ Sprenger

	/** Sprenger (H, SHIKAI): five Seele Schneider land in a pentagon at the aimed ground point. */
	public static void sprenger(ServerPlayerEntity p, Vec3d aim) {
		ServerWorld w = p.getServerWorld();
		Vec3d ground = groundPoint(w, aim);
		int now = p.getServer().getTicks();
		UUID id = p.getUuid();
		int mine = 0;
		for (Trap t : TRAPS) {
			if (t.trap().owner().equals(id)) {
				mine++;
			}
		}
		if (mine >= UryuBalance.SPRENGER_MAX_PER_PLAYER) {
			for (Iterator<Trap> it = TRAPS.iterator(); it.hasNext();) {
				if (it.next().trap().owner().equals(id)) {
					it.remove(); // the oldest goes
					break;
				}
			}
		}
		TRAPS.add(new Trap(new SprengerTrap(id, v3(ground), now), w.getRegistryKey()));
		UryuNet.sendEffect(w, ground.x, ground.y, ground.z, UryuArrowsS2C.TRAP, now,
				new float[] {(float) ground.x, (float) ground.y, (float) ground.z, (float) UryuBalance.SPRENGER_RADIUS, UryuBalance.SPRENGER_EXPLODE_AT});
		ReiatsuTest.LOGGER.info("[uryu] {} sprenger at ({}, {}, {})", p.getName().getString(), String.format(java.util.Locale.ROOT, "%.1f", ground.x),
				String.format(java.util.Locale.ROOT, "%.1f", ground.y), String.format(java.util.Locale.ROOT, "%.1f", ground.z));
	}

	/** The floor under (or at) the aimed point, searching 1 block up and 6 down; the point itself when there is none. */
	static Vec3d groundPoint(ServerWorld w, Vec3d aim) {
		BlockHitResult r = w.raycast(new RaycastContext(aim.add(0, 1.0, 0), aim.add(0, -6.0, 0), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
		return r.getType() == HitResult.Type.MISS ? aim : r.getPos();
	}

	private static void tickTraps(MinecraftServer srv, int now) {
		List<Trap> due = new ArrayList<>();
		for (Iterator<Trap> it = TRAPS.iterator(); it.hasNext();) {
			Trap t = it.next();
			ServerWorld w = srv.getWorld(t.world());
			ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(t.trap().owner());
			if (w == null || owner == null || owner.isDead() || owner.getServerWorld() != w) {
				it.remove(); // the caster died, left or went to another dimension: the pentagon fizzles
				continue;
			}
			SprengerTrap.Step st = t.trap().advance(now);
			if (st == SprengerTrap.Step.BIND) {
				bind(owner, t.trap());
			} else if (st == SprengerTrap.Step.EXPLODE) {
				due.add(t);
				it.remove();
			}
		}
		for (Trap t : due) {
			ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(t.trap().owner());
			if (owner != null) {
				explode(owner, srv.getWorld(t.world()), t.trap(), now);
			}
		}
	}

	private static List<LivingEntity> inside(ServerPlayerEntity owner, SprengerTrap t) {
		Vec3d c = vec(t.center());
		// the band from SPRENGER_BELOW under the floor to SPRENGER_HEIGHT above it, as one cylinder around its middle
		double mid = (UryuBalance.SPRENGER_HEIGHT - UryuBalance.SPRENGER_BELOW) / 2.0;
		return UryuTargets.cylinder(owner, c.add(0, mid, 0), UryuBalance.SPRENGER_RADIUS, (UryuBalance.SPRENGER_HEIGHT + UryuBalance.SPRENGER_BELOW) / 2.0, 24);
	}

	/** 0.8 s: the legs of whoever is still inside are bound (Slowness V and no jump). */
	private static void bind(ServerPlayerEntity owner, SprengerTrap t) {
		int n = 0;
		for (LivingEntity e : inside(owner, t)) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, UryuBalance.SPRENGER_BIND_TICKS, UryuBalance.SPRENGER_SLOW_AMP, false, true,
					true), owner);
			// a negative Jump Boost level takes the jump away (vanilla ignores a jump with no upward speed)
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, UryuBalance.SPRENGER_BIND_TICKS, -10, false, false, false), owner);
			n++;
		}
		ReiatsuTest.LOGGER.info("[uryu] sprenger bind: {} bound", n);
	}

	/** 2.0 s (or at once for a focused arrow): the pentagon explodes for 9 HP (13.5 after an early detonation) in its whole area. */
	private static void explode(ServerPlayerEntity owner, ServerWorld w, SprengerTrap t, int now) {
		if (w == null) {
			return;
		}
		float hp = t.damage();
		int n = 0;
		List<LivingEntity> hit = inside(owner, t);
		UryuTargets.logged("uryu.shikai.sprenger#explode", () -> {
			for (LivingEntity e : hit) {
				UryuTargets.hurt(owner, e, hp, DamageTypes.PLAYER_EXPLOSION, true);
			}
		});
		n = hit.size();
		Vec3d c = vec(t.center());
		UryuNet.sendEffect(w, c.x, c.y, c.z, UryuArrowsS2C.BURST, now,
				new float[] {(float) c.x, (float) c.y, (float) c.z, (float) UryuBalance.SPRENGER_RADIUS, t.early() ? 1f : 0f});
		ReiatsuTest.LOGGER.info("[uryu] sprenger explodes{}: {} in the area, {} HP", t.early() ? " early" : "", n, hp);
	}

	/** A fully focused arrow of the owner touched a pentagon of the owner: it explodes now. */
	private static void detonateTouched(MinecraftServer srv, ServerWorld w, ArrowSim.Event e) {
		List<Trap> hit = new ArrayList<>();
		for (Trap t : TRAPS) {
			if (t.world().equals(w.getRegistryKey()) && t.trap().owner().equals(e.owner()) && t.trap().touchedBy(e.from(), e.to())) {
				hit.add(t);
			}
		}
		if (hit.isEmpty()) {
			return;
		}
		ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(e.owner());
		for (Trap t : hit) {
			TRAPS.remove(t);
			if (owner != null && t.trap().detonateEarly()) {
				explode(owner, w, t.trap(), srv.getTicks());
			}
		}
	}

	// ------------------------------------------------------------------ Seele Schneider

	/**
	 * Seele Schneider (B, SHIKAI): a 1.5 block lunge by velocity (no teleport, three packets of 0.36 blocks per tick, then the ground
	 * friction), then three slashes of a Reishi blade 0.25 s apart ({@link #tickSeele}). Focus is neither consumed nor reset: the stacks
	 * wait through the lunge ({@code Focus.grace}).
	 */
	public static void seeleSchneider(ServerPlayerEntity p) {
		Data d = data(p);
		d.focus.grace(UryuBalance.SEELE_FOCUS_GRACE);
		V3 look = v3(p.getRotationVec(1.0F));
		V3 fallback = new V3(-Math.sin(Math.toRadians(p.getYaw())), 0.0, Math.cos(Math.toRadians(p.getYaw())));
		SEELES.put(p.getUuid(), new Seele(p.getServer().getTicks(), SeeleCombo.lungeDirection(look, fallback)));
		ReiatsuTest.LOGGER.info("[uryu] {} seele schneider", p.getName().getString());
	}

	private static void tickSeele(MinecraftServer srv, int now) {
		for (Iterator<Map.Entry<UUID, Seele>> it = SEELES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Seele> en = it.next();
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(en.getKey());
			Seele s = en.getValue();
			if (p == null || p.isDead() || p.isSpectator()) {
				it.remove();
				continue;
			}
			int k = now - s.start;
			if (SeeleCombo.lungeAt(k)) {
				V3 v = SeeleCombo.lungeVelocity(s.dir);
				push(p, new Vec3d(v.x(), p.getVelocity().y, v.z()));
			}
			int slash = SeeleCombo.slashAt(k);
			if (slash >= 0) {
				slash(p, slash, now);
			}
			if (SeeleCombo.over(k)) {
				it.remove();
			}
		}
	}

	/** One slash: everything valid in the cone in front of the player takes 2.5 HP; the third also gets knocked back 1.0. */
	private static void slash(ServerPlayerEntity p, int index, int now) {
		Vec3d eye = p.getEyePos();
		Vec3d look = p.getRotationVec(1.0F).normalize();
		V3 o = v3(eye);
		V3 l = v3(look);
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : UryuTargets.sphere(p, eye, UryuBalance.SEELE_REACH + 1.0, 32)) {
			Vec3d c = e.getBoundingBox().getCenter();
			Vec3d seen = UryuTargets.rayPoint(p, eye, c.subtract(eye).normalize(), eye.distanceTo(c));
			if (eye.distanceTo(seen) < eye.distanceTo(c) - 0.6) {
				continue; // a block between
			}
			// the centre or the nearest point of the box counts (a big mob on the edge of the cone is still hit)
			net.minecraft.util.math.Box b = e.getBoundingBox();
			Vec3d near = new Vec3d(Math.max(b.minX, Math.min(eye.x, b.maxX)), Math.max(b.minY, Math.min(eye.y, b.maxY)), Math.max(b.minZ, Math.min(eye.z, b.maxZ)));
			if (SeeleCombo.inCone(o, l, v3(c)) || SeeleCombo.inCone(o, l, v3(near))) {
				hit.add(e);
			}
		}
		boolean knock = SeeleCombo.knocksBack(index);
		UryuTargets.logged("uryu.shikai.seele_schneider#" + (index + 1), () -> {
			for (LivingEntity e : hit) {
				if (UryuTargets.hurt(p, e, UryuBalance.SEELE_DAMAGE, DamageTypes.PLAYER_ATTACK, true) && knock) {
					UryuTargets.knockback(e, p.getPos(), UryuBalance.SEELE_KNOCKBACK);
				}
			}
		});
		UryuNet.sendEffect(p.getServerWorld(), eye.x, eye.y, eye.z, UryuArrowsS2C.SLASH, now,
				new float[] {(float) eye.x, (float) eye.y, (float) eye.z, (float) look.x, (float) look.y, (float) look.z, index, (float) UryuBalance.SEELE_REACH,
						(float) UryuBalance.SEELE_CONE_DEG, hit.size()});
		for (LivingEntity e : hit) {
			Vec3d c = e.getBoundingBox().getCenter();
			UryuNet.sendImpact(p.getServerWorld(), new V3(c.x, c.y, c.z), UryuArrowsS2C.IMPACT_ENTITY, UryuArrowsS2C.KIND_SLASH, now);
		}
		p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9F, 1.5F + index * 0.15F);
		ReiatsuTest.LOGGER.info("[uryu] {} seele slash {} hits={} knockback={}", p.getName().getString(), index + 1, hit.size(), knock);
	}

	private static void push(ServerPlayerEntity p, Vec3d v) {
		p.setVelocity(v);
		p.velocityModified = false;
		p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
	}

	// ------------------------------------------------------------------ Wing barrage

	/** Wing barrage (Z, BANKAI): for 6 s the wings fire homing arrows at the nearest enemies ({@link #tickWings}). */
	public static void wingBarrage(ServerPlayerEntity p) {
		WINGS.removeIf(w -> w.owner.equals(p.getUuid())); // one barrage per player
		WINGS.add(new Wing(p.getUuid(), p.getServerWorld().getRegistryKey(), p.getServer().getTicks()));
		ReiatsuTest.LOGGER.info("[uryu] {} wing barrage", p.getName().getString());
	}

	private static void tickWings(MinecraftServer srv, int now) {
		for (Iterator<Wing> it = WINGS.iterator(); it.hasNext();) {
			Wing w = it.next();
			ServerWorld world = srv.getWorld(w.world);
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(w.owner);
			if (world == null || p == null || p.isDead() || p.getServerWorld() != world) {
				it.remove();
				continue;
			}
			int k = now - w.start;
			if (!WingBarrage.active(k)) {
				it.remove();
				continue;
			}
			if (WingBarrage.isCycleStart(k)) {
				w.pending = WingBarrage.cycle(k, wingTargets(p));
			}
			for (Iterator<WingBarrage.Shot> si = w.pending.iterator(); si.hasNext();) {
				WingBarrage.Shot s = si.next();
				if (s.fireTick() <= k) {
					si.remove();
					fireWing(p, world, s, now);
				}
			}
		}
	}

	/** Valid enemies within 30 blocks with a free line from the chest, nearest first, as the pure rule wants them. */
	private static List<WingBarrage.Target> wingTargets(ServerPlayerEntity p) {
		Vec3d eye = p.getEyePos();
		List<WingBarrage.Target> out = new ArrayList<>();
		for (LivingEntity e : UryuTargets.sphere(p, p.getPos().add(0, 1.0, 0), UryuBalance.WING_RANGE, 24)) {
			if (e instanceof ServerPlayerEntity sp && sp.isCreative()) {
				continue;
			}
			Vec3d c = e.getBoundingBox().getCenter();
			Vec3d seen = UryuTargets.rayPoint(p, eye, c.subtract(eye).normalize(), eye.distanceTo(c));
			if (eye.distanceTo(seen) >= eye.distanceTo(c) - 0.6) {
				out.add(new WingBarrage.Target(e.getId(), v3(c)));
			}
		}
		out.sort(java.util.Comparator.comparingDouble((WingBarrage.Target t) -> t.pos().distanceTo(v3(p.getPos()))).thenComparingInt(WingBarrage.Target::id));
		return out;
	}

	private static void fireWing(ServerPlayerEntity p, ServerWorld world, WingBarrage.Shot s, int now) {
		V3 tip = WingBarrage.tip(s.slot(), p.getBodyYaw(), v3(p.getPos()));
		V3 dir = s.target().pos().sub(tip).normalizedOr(v3(p.getRotationVec(1.0F)));
		spawn(world, new ArrowSim.Spec(p.getUuid(), ArrowSim.Kind.WING, tip, dir.scale(UryuBalance.WING_SPEED), 0.0, UryuBalance.WING_DAMAGE, UryuBalance.WING_LIFE, 0,
				UryuBalance.ARROW_RADIUS, s.target().id(), Math.toRadians(UryuBalance.WING_TURN_DEG), 0));
	}
	// ------------------------------------------------------------------ Homing Volley

	/** Homing Volley (H, BANKAI): locks the nearest living target in a 15 degree cone, fires 3 + Focus arrows that curve to it. */
	public static void homingVolley(ServerPlayerEntity p) {
		Data d = data(p);
		int stacks = d.focus.consume();
		show(p, d);
		int n = Volley.arrowCount(stacks);
		Vec3d eye = p.getEyePos();
		Vec3d look = p.getRotationVec(1.0F).normalize();
		List<Volley.Candidate> cands = new ArrayList<>();
		for (LivingEntity e : UryuTargets.sphere(p, eye, UryuBalance.VOLLEY_LOCK_RANGE, 48)) {
			Vec3d c = e.getBoundingBox().getCenter();
			Vec3d seen = UryuTargets.rayPoint(p, eye, c.subtract(eye).normalize(), eye.distanceTo(c));
			if (eye.distanceTo(seen) >= eye.distanceTo(c) - 0.6) { // no block on the way
				cands.add(new Volley.Candidate(e.getId(), v3(c)));
			}
		}
		int target = Volley.pickTarget(v3(eye), v3(look), cands, UryuBalance.VOLLEY_CONE_DEG, UryuBalance.VOLLEY_LOCK_RANGE);
		V3[] dirs = Volley.directions(v3(look), n, UryuBalance.VOLLEY_SPREAD_DEG);
		V3 origin = new V3(eye.x + look.x * 0.4, eye.y - 0.1 + look.y * 0.4, eye.z + look.z * 0.4);
		for (V3 dir : dirs) {
			spawn(p.getServerWorld(), new ArrowSim.Spec(p.getUuid(), ArrowSim.Kind.VOLLEY, origin, dir.scale(UryuBalance.VOLLEY_SPEED), 0.0,
					UryuBalance.VOLLEY_DAMAGE, UryuBalance.VOLLEY_LIFE, 0, UryuBalance.ARROW_RADIUS, target, Math.toRadians(UryuBalance.VOLLEY_TURN_DEG), 0));
		}
		ReiatsuTest.LOGGER.info("[uryu] {} homing volley focus={} arrows={} target={}", p.getName().getString(), stacks, n, target);
	}

	// ------------------------------------------------------------------ Sternenstaub

	/** Sternenstaub (B, BANKAI): a line 40 long and 2 wide, 20 HP to the first target and 12 to each following one; uses the 3 stacks. */
	public static void sternenstaub(ServerPlayerEntity p) {
		Data d = data(p);
		if (!Sternenstaub.canCast(d.focus.stacks())) {
			ReiatsuTest.LOGGER.info("[uryu] {} sternenstaub fizzled: focus {}", p.getName().getString(), d.focus.stacks());
			return; // the cast was let through before the Focus ran out (moved in the same tick); the gate in allowsCast normally stops it
		}
		d.focus.consume();
		show(p, d);
		Vec3d eye = p.getEyePos();
		Vec3d dir = p.getRotationVec(1.0F).normalize();
		Vec3d end = UryuTargets.rayPoint(p, eye, dir, UryuBalance.STAR_LENGTH); // the giant arrow stops at the first block
		double length = eye.distanceTo(end);
		List<LivingEntity> targets = UryuTargets.segment(p, eye, dir, 0.0, length, UryuBalance.STAR_HALF_WIDTH, UryuBalance.STAR_MAX_TARGETS);
		UryuTargets.logged("uryu.bankai.sternenstaub", () -> {
			for (int i = 0; i < targets.size(); i++) {
				UryuTargets.hurt(p, targets.get(i), Sternenstaub.damage(i), DamageTypes.ARROW, true);
			}
		});
		Vec3d from = eye.add(0, -0.2, 0);
		UryuNet.sendEffect(p.getServerWorld(), eye.x, eye.y, eye.z, UryuArrowsS2C.LINE, p.getServer().getTicks(),
				new float[] {(float) from.x, (float) from.y, (float) from.z, (float) end.x, (float) end.y, (float) end.z, (float) UryuBalance.STAR_HALF_WIDTH});
		ReiatsuTest.LOGGER.info("[uryu] {} sternenstaub length={} targets={}", p.getName().getString(), String.format(java.util.Locale.ROOT, "%.1f", length),
				targets.size());
	}
}
