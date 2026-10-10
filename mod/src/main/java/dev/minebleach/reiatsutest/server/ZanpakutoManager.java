package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilityRequest;
import dev.minebleach.reiatsutest.core.state.BalanceConfig;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RequestSource;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.StateEvent;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.TransitionRequest;
import dev.minebleach.reiatsutest.core.state.TransitionResult;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.net.ActionResultS2C;
import dev.minebleach.reiatsutest.net.CastAbilityC2S;
import dev.minebleach.reiatsutest.net.RequestTransitionC2S;
import dev.minebleach.reiatsutest.net.ShunpoC2S;
import dev.minebleach.reiatsutest.core.state.ShunpoPath;
import dev.minebleach.reiatsutest.registry.ModAttachments;
import dev.minebleach.reiatsutest.registry.ModComponents;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.registry.ReleaseState;
import dev.minebleach.reiatsutest.registry.ZanpakutoItem;
import dev.minebleach.reiatsutest.registry.data.CooldownData;
import dev.minebleach.reiatsutest.registry.data.ReiatsuData;
import dev.minebleach.reiatsutest.registry.data.ZanpakutoData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server glue between Minecraft and the pure {@link StateMachine}: one machine per online player, fed with requests,
 * Minecraft events and ticks; its {@link StateEvent}s are executed here (attachments, component mirror, effect events,
 * scheduled phases, temporary blocks).
 */
public final class ZanpakutoManager {
	/** Per player state kept next to the machine; dropped on logout. */
	static final class Session {
		final StateMachine sm;
		ZanpakutoData lastZanpakuto;
		ReiatsuData lastReiatsu;
		java.util.Set<Byte> lastCooldownKeys = Set.of();
		boolean spectator;
		ItemStack lastMainStack = ItemStack.EMPTY;
		int lastSlot = -1;

		Session(StateMachine sm) {
			this.sm = sm;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final PhaseScheduler SCHEDULER = new PhaseScheduler();
	private static MinecraftServer server;
	private static boolean reentrantDamage;

	private ZanpakutoManager() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(RequestTransitionC2S.ID, (payload, ctx) -> onTransition(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(CastAbilityC2S.ID, (payload, ctx) -> onCast(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ShunpoC2S.ID, (payload, ctx) ->
				performShunpo(ctx.player(), RequestSource.fromCode(payload.source()), Math.max(0, payload.clientSeq())));
		ServerTickEvents.END_SERVER_TICK.register(ZanpakutoManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> onLeave(handler.player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity p) {
				Session s = SESSIONS.get(p.getUuid());
				if (s != null) {
					apply(p, s, s.sm.onDeath(), null);
				}
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				onRespawn(newPlayer);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			Session s = SESSIONS.get(player.getUuid());
			if (s != null) {
				apply(player, s, s.sm.onDimensionChange(), null);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			server = srv;
			TempBlocks.onServerStarted(srv);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			TempBlocks.onServerStopping(srv);
			SCHEDULER.clear();
			SESSIONS.clear();
			server = null;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ZanpakutoManager::allowDamage);
	}

	// ------------------------------------------------------------------ sessions

	static Session session(ServerPlayerEntity p) {
		Session s = SESSIONS.get(p.getUuid());
		if (s == null) {
			s = createSession(p);
		}
		return s;
	}

	/** The machine of an online player, or null (commands, harness). */
	public static StateMachine machine(ServerPlayerEntity p) {
		Session s = SESSIONS.get(p.getUuid());
		return s == null ? null : s.sm;
	}

	private static Session createSession(ServerPlayerEntity p) {
		MinecraftServer srv = p.getServer();
		BalanceConfig cfg = BalanceConfig.defaults();
		ReiatsuData stored = p.getAttached(ModAttachments.REIATSU);
		ReiatsuState initial = stored != null ? stored.toState() : ReiatsuState.full(cfg.maxTenths());
		Session s = new Session(new StateMachine(srv::getTicks, cfg, initial));
		SESSIONS.put(p.getUuid(), s);
		return s;
	}

	private static void onJoin(ServerPlayerEntity p) {
		Session s = createSession(p);
		// join always starts SEALED (the attachment is not persistent): reset every released stack right away
		applyMirror(p, s.sm);
		s.lastZanpakuto = null;
		s.lastReiatsu = null;
		s.lastCooldownKeys = null;
		flush(p, s);
		ReiatsuTest.LOGGER.info("[reiatsu] session for {} started, reiatsu {}", p.getName().getString(), s.sm.reiatsu().value());
	}

	private static void onLeave(ServerPlayerEntity p) {
		Session s = SESSIONS.remove(p.getUuid());
		if (s != null) {
			s.sm.onLogout();
		}
		SCHEDULER.cancel(p.getUuid());
		if (p.getServer() != null) {
			TempBlocks.rollback(p.getServer(), p.getUuid());
		}
	}

	private static void onRespawn(ServerPlayerEntity p) {
		Session s = SESSIONS.get(p.getUuid());
		if (s == null) {
			s = createSession(p);
		}
		s.sm.onRespawn();
		s.lastZanpakuto = null;
		s.lastReiatsu = null;
		s.lastCooldownKeys = null;
		s.lastMainStack = ItemStack.EMPTY;
		flush(p, s); // new player entity: write all three attachments again (reiatsu is back to 50%)
	}

	// ------------------------------------------------------------------ requests

	private static void onTransition(ServerPlayerEntity p, RequestTransitionC2S pl) {
		if (pl.targetState() < 0 || pl.targetState() >= ZanpakutoState.values().length) {
			reply(p, pl.clientSeq(), ResultCode.DENIED_STATE);
			return;
		}
		// a client may not use the server-side sequence marker to skip the stale check
		performTransition(p, ZanpakutoState.fromCode(pl.targetState()), RequestSource.fromCode(pl.source()),
				Math.max(0, pl.clientSeq()));
	}

	private static void onCast(ServerPlayerEntity p, CastAbilityC2S pl) {
		AbilityId ability = AbilityId.fromCode(pl.abilityId());
		if (ability == null) {
			reply(p, pl.clientSeq(), ResultCode.DENIED_STATE);
			return;
		}
		performCast(p, ability, RequestSource.fromCode(pl.source()), Math.max(0, pl.clientSeq()));
	}

	/**
	 * The one server path of a state transition request: used by the {@code request_transition} receiver (keys) and by
	 * the voice bridge ({@link StateMachine#SERVER_SEQ}). Same machine, same checks, same events and the same
	 * {@code action_result} answer to the player; voice never bypasses cooldown, reiatsu or state rules.
	 */
	public static TransitionResult performTransition(ServerPlayerEntity p, ZanpakutoState target, RequestSource source, int seq) {
		Session s = session(p);
		CharacterId held = ModItems.characterOf(p.getMainHandStack());
		TransitionResult r = s.sm.request(new TransitionRequest(target, source, seq, held));
		ReiatsuTest.LOGGER.info("[reiatsu] {} request {} (held {}, {}): {} {}", p.getName().getString(), target, held, source,
				r.code(), r.ok() ? r.from() + "->" + r.to() : r.reason());
		apply(p, s, r.events(), null);
		reply(p, seq, r.code());
		return r;
	}

	/** The one server path of an ability cast; see {@link #performTransition}. */
	public static TransitionResult performCast(ServerPlayerEntity p, AbilityId ability, RequestSource source, int seq) {
		Session s = session(p);
		CharacterId held = ModItems.characterOf(p.getMainHandStack());
		TransitionResult r = s.sm.cast(new AbilityRequest(ability, source, seq, held));
		ReiatsuTest.LOGGER.info("[reiatsu] {} cast {} (held {}, {}): {} {}", p.getName().getString(), ability, held, source,
				r.code(), r.ok() ? "ok" : r.reason());
		CastContext ctx = null;
		if (r.ok()) {
			int seed = 0;
			for (StateEvent e : r.events()) {
				if (e instanceof StateEvent.BroadcastEffect b) {
					seed = b.seed();
				}
			}
			ctx = CastContext.capture(p, ability, s.sm.config().spec(ability), seed);
		}
		apply(p, s, r.events(), ctx);
		reply(p, seq, r.code());
		return r;
	}

	/**
	 * The one server path of a shunpo (B4 step 5): the machine checks state, item, locks, cooldown and reiatsu, the path
	 * is walked over the real block collisions ({@link ShunpoWorld}); the client never sends a position.
	 */
	public static TransitionResult performShunpo(ServerPlayerEntity p, RequestSource source, int seq) {
		Session s = session(p);
		CharacterId held = ModItems.characterOf(p.getMainHandStack());
		boolean ground = p.isOnGround();
		double[] dir = ShunpoPath.direction(p.getYaw(), p.getPitch(), ground);
		TransitionResult r = s.sm.shunpo(seq, held, new ShunpoWorld(p), p.getX(), p.getY(), p.getZ(), dir, ground);
		ReiatsuTest.LOGGER.info("[reiatsu] {} shunpo (held {}, {}): {} {}", p.getName().getString(), held, source, r.code(),
				r.ok() ? "ok" : r.reason());
		apply(p, s, r.events(), null);
		reply(p, seq, r.code());
		return r;
	}

	private static void reply(ServerPlayerEntity p, int clientSeq, ResultCode code) {
		if (ServerPlayNetworking.canSend(p, ActionResultS2C.ID)) {
			ServerPlayNetworking.send(p, new ActionResultS2C(clientSeq, code.code()));
		}
	}

	// ------------------------------------------------------------------ events

	/** Executes machine events for one player. {@code ctx} is the cast context for events of an accepted ability cast. */
	static void apply(ServerPlayerEntity p, Session s, List<StateEvent> events, CastContext ctx) {
		MinecraftServer srv = p.getServer();
		for (StateEvent e : events) {
			switch (e) {
				case StateEvent.StateChanged c -> ReiatsuTest.LOGGER.info("[reiatsu] {} state {} -> {} ({})",
						p.getName().getString(), c.from(), c.to(), c.trigger());
				case StateEvent.ReiatsuSpent r -> { }
				case StateEvent.CooldownStarted c -> { }
				case StateEvent.BroadcastEffect b -> ServerFx.effect(p, b.effectId(), b.seed(), ctx);
				case StateEvent.CancelEffects c -> SCHEDULER.cancel(p.getUuid());
				case StateEvent.RollbackTempBlocks r -> TempBlocks.rollback(srv, p.getUuid());
				case StateEvent.MirrorComponent m -> applyMirror(p, s.sm);
				case StateEvent.ScheduledPhase ph -> {
					if (ctx != null) {
						SCHEDULER.add(srv.getTicks() + ph.offsetTicks(), p.getUuid(), ph.serial(), ph.ability(), ph.phaseId(), ctx);
					}
				}
				case StateEvent.ShikaiModeChanged m -> { }
				case StateEvent.ShunpoMove m -> {
					p.networkHandler.requestTeleport(m.toX(), m.toY(), m.toZ(), p.getYaw(), p.getPitch());
					ServerFx.shunpo(p, m, s.sm.character());
				}
			}
		}
		flush(p, s);
	}

	/** Writes the attachments that changed. Reiatsu changes at most once per batch, so syncs stay under 4 per second. */
	private static void flush(ServerPlayerEntity p, Session s) {
		ZanpakutoData z = ZanpakutoData.of(s.sm.snapshot());
		if (!z.equals(s.lastZanpakuto)) {
			p.setAttached(ModAttachments.ZANPAKUTO, z);
			s.lastZanpakuto = z;
		}
		ReiatsuData r = ReiatsuData.of(s.sm.reiatsu());
		if (!r.equals(s.lastReiatsu)) {
			p.setAttached(ModAttachments.REIATSU, r);
			s.lastReiatsu = r;
		}
		CooldownData c = CooldownData.of(s.sm.cooldownRemainingAll());
		if (!c.remainingTicks().keySet().equals(s.lastCooldownKeys)) {
			p.setAttached(ModAttachments.COOLDOWNS, c);
			s.lastCooldownKeys = Set.copyOf(c.remainingTicks().keySet());
		}
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer srv) {
		int now = srv.getTicks();
		for (ServerPlayerEntity p : new ArrayList<>(srv.getPlayerManager().getPlayerList())) {
			Session s = SESSIONS.get(p.getUuid());
			if (s == null) {
				onJoin(p);
				continue;
			}
			if (p.isDead()) {
				continue;
			}
			StateMachine sm = s.sm;
			List<StateEvent> ev = new ArrayList<>();
			if (p.isSpectator() != s.spectator) {
				s.spectator = p.isSpectator();
				ev.addAll(sm.setSpectator(s.spectator));
			}
			if (sm.state() != ZanpakutoState.SEALED) {
				if (!carries(p, sm.character())) {
					ev.addAll(sm.onItemDropped());
				} else {
					ev.addAll(sm.onHandChanged(ModItems.characterOf(p.getMainHandStack())));
				}
			}
			ev.addAll(sm.tick(1));

			// keep the render mirror on the main-hand stack in step with the state when the hand changes
			ItemStack main = p.getMainHandStack();
			int slot = p.getInventory().selectedSlot;
			if (main != s.lastMainStack || slot != s.lastSlot) {
				s.lastMainStack = main;
				s.lastSlot = slot;
				applyMirror(p, sm);
			}
			apply(p, s, ev, null);

			if (sm.state() == ZanpakutoState.BANKAI && sm.character() == CharacterId.RUKIA && now % 10 == 0) {
				AbilityExecutor.rukiaBankaiPassive(p);
			}
			if ((now + p.getId()) % 20 == 0) {
				applyMirror(p, sm); // invariant check (ADR section 3)
			}
		}
		SCHEDULER.runDue(now, task -> {
			Session s = SESSIONS.get(task.caster());
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(task.caster());
			if (s == null || p == null || p.isDead() || s.sm.castSerial() != task.serial()) {
				return;
			}
			try {
				AbilityExecutor.run(p, task.ability(), task.phase(), task.ctx());
			} catch (RuntimeException ex) {
				ReiatsuTest.LOGGER.error("[reiatsu] ability {} phase {} failed", task.ability(), task.phase(), ex);
			}
		});
		if (now % 5 == 0) {
			TempBlocks.tick(srv, now);
		}
	}

	private static boolean carries(ServerPlayerEntity p, CharacterId character) {
		PlayerInventory inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (ModItems.characterOf(inv.getStack(i)) == character) {
				return true;
			}
		}
		return ModItems.characterOf(p.currentScreenHandler.getCursorStack()) == character;
	}

	/**
	 * The component is a render mirror of the attachment: the main-hand stack of the released character shows the state,
	 * every other zanpakuto stack is SEALED.
	 */
	static void applyMirror(ServerPlayerEntity p, StateMachine sm) {
		ZanpakutoState st = sm.state();
		CharacterId ch = sm.character();
		PlayerInventory inv = p.getInventory();
		ItemStack main = p.getMainHandStack();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.getItem() instanceof ZanpakutoItem z) {
				ReleaseState want = st != ZanpakutoState.SEALED && z.character() == ch && stack == main
						? ReleaseState.of(st) : ReleaseState.SEALED;
				if (stack.get(ModComponents.RELEASE_STATE) != want) {
					stack.set(ModComponents.RELEASE_STATE, want);
				}
			}
		}
	}

	// ------------------------------------------------------------------ barrier

	private static boolean allowDamage(net.minecraft.entity.LivingEntity entity, DamageSource source, float amount) {
		if (reentrantDamage || !(entity instanceof ServerPlayerEntity p)) {
			return true;
		}
		Session s = SESSIONS.get(p.getUuid());
		if (s == null || s.sm.shikaiMode() != dev.minebleach.reiatsutest.core.state.ShikaiMode.BARRIER
				|| source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		int total = Math.round(amount * 10.0F);
		int remaining = s.sm.absorbBarrier(total);
		if (remaining >= total) {
			return true;
		}
		flush(p, s);
		if (remaining <= 0) {
			return false;
		}
		reentrantDamage = true;
		try {
			p.damage(source, remaining / 10.0F);
		} finally {
			reentrantDamage = false;
		}
		return false;
	}

	// ------------------------------------------------------------------ info for commands and the harness

	public static int pendingPhases() {
		return SCHEDULER.pending();
	}

	/** Applies events produced outside the manager (the /reiatsu command). */
	public static void applyExternal(ServerPlayerEntity p, List<StateEvent> events) {
		Session s = session(p);
		apply(p, s, events, null);
	}

	/** Plain access for dev code that needs the entity of a tracked id. */
	public static Entity entity(ServerPlayerEntity p, int id) {
		return p.getServerWorld().getEntityById(id);
	}
}
