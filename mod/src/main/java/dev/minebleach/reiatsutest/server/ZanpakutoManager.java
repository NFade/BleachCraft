package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.combat.BlockOutcome;
import dev.minebleach.reiatsutest.core.combat.BlockRules;
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
import dev.minebleach.reiatsutest.core.state.Trigger;
import dev.minebleach.reiatsutest.core.state.EffectIds;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.net.ActionResultS2C;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoRequest;
import dev.minebleach.reiatsutest.net.CastAbilityC2S;
import dev.minebleach.reiatsutest.net.CastKidoC2S;
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
		ServerPlayNetworking.registerGlobalReceiver(CastKidoC2S.ID, (payload, ctx) -> onKido(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ShunpoC2S.ID, (payload, ctx) ->
				performShunpo(ctx.player(), RequestSource.fromCode(payload.source()), Math.max(0, payload.clientSeq())));
		ServerTickEvents.END_SERVER_TICK.register(ZanpakutoManager::tick);
		BcCombatGate.init(); // BC5: no Better Combat strike while sheathed or blocking
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
			BankaiField.clearAll();
			SESSIONS.clear();
			server = null;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ZanpakutoManager::allowDamage);
		BlockParry.init();
		BladeFlights.init(); // after the tick registration above: the flight ticks after the scheduled landing phases
		MeleeReiatsu.init();
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
		java.util.UUID id = p.getUuid();
		s.sm.setRage(RageManager.CONFIG, () -> RageManager.valueOf(id)); // shikai / bankai entry needs and pays Rage (round 5)
		s.sm.setKidoConfig(KidoManager.config());
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
			// the machine's own events are not applied (the player is leaving), but the bankai cleanup must run: frost, anchors, clients
			for (StateEvent e : s.sm.onLogout()) {
				if (e instanceof StateEvent.BankaiEnded b) {
					endBankaiEffects(p, b.who(), b.trigger());
				}
			}
		}
		SCHEDULER.cancel(p.getUuid());
		AnchorManager.reset(p);
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

	private static void onKido(ServerPlayerEntity p, CastKidoC2S pl) {
		KidoId kido = KidoId.fromCode(pl.kidoId());
		if (kido == null) {
			reply(p, pl.clientSeq(), ResultCode.DENIED_STATE);
			return;
		}
		performKido(p, kido, RequestSource.fromCode(pl.source()), Math.max(0, pl.clientSeq()));
	}

	/**
	 * The one server path of a kido cast (B5), shared by the key ({@code cast_kido}) and the voice bridge. Needs no state and no
	 * sword. Like every ability it is refused while the block is held (design/PARRY_PLAN.md section 5), before the machine
	 * charges reiatsu, cooldown or the gap.
	 */
	public static TransitionResult performKido(ServerPlayerEntity p, KidoId kido, RequestSource source, int seq) {
		Session s = session(p);
		if (!BlockRules.allowsAbility(ZanpakutoItem.isBlocking(p))) {
			ZanpakutoState st = s.sm.state();
			ReiatsuTest.LOGGER.info("[kido] {} cast {} ({}): denied, blocking", p.getName().getString(), kido, source);
			reply(p, seq, ResultCode.DENIED_STATE);
			return new TransitionResult(ResultCode.DENIED_STATE, dev.minebleach.reiatsutest.core.state.RejectReason.NOT_IN_STATE, st, st,
					List.of());
		}
		TransitionResult r = s.sm.castKido(new KidoRequest(kido, source, seq));
		ReiatsuTest.LOGGER.info("[kido] {} cast {} ({}, state {}): {} {}", p.getName().getString(), kido, source, s.sm.state(),
				r.code(), r.ok() ? "ok" : r.reason());
		apply(p, s, r.events(), null);
		reply(p, seq, r.code());
		return r;
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
		if (!BlockRules.allowsAbility(ZanpakutoItem.isBlocking(p))) {
			// abilities cannot be cast while the block is held (design/PARRY_PLAN.md section 5); checked here so the key and the
			// voice path both obey it, before the machine charges a cooldown, a GCD or reiatsu
			ZanpakutoState st = s.sm.state();
			ReiatsuTest.LOGGER.info("[reiatsu] {} cast {} ({}): denied, blocking", p.getName().getString(), ability, source);
			reply(p, seq, ResultCode.DENIED_STATE);
			return new TransitionResult(ResultCode.DENIED_STATE, dev.minebleach.reiatsutest.core.state.RejectReason.NOT_IN_STATE, st, st,
					List.of());
		}
		if (!dev.minebleach.reiatsutest.server.uryu.UryuManager.allowsCast(p, ability)) {
			// Uryu: Sternenstaub needs a full Focus; refused before the machine charges anything
			ZanpakutoState st = s.sm.state();
			reply(p, seq, ResultCode.DENIED_STATE);
			return new TransitionResult(ResultCode.DENIED_STATE, dev.minebleach.reiatsutest.core.state.RejectReason.NOT_IN_STATE, st, st, List.of());
		}
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
			ctx = CastContext.capture(p, ability, s.sm.config().spec(ability), seed, s.sm.config().byakuyaAttackTickInterval());
		}
		apply(p, s, r.events(), ctx);
		if (ctx != null && ability == AbilityId.MODE_ATTACK) {
			BladeFlights.start(p, ctx, s.sm.castSerial()); // steerable swarm flight + damage along it (core/swarm)
		}
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
				case StateEvent.StateChanged c -> {
						ReiatsuTest.LOGGER.info("[reiatsu] {} state {} -> {} ({})", p.getName().getString(), c.from(), c.to(), c.trigger());
						if (c.to() == ZanpakutoState.SEALED || c.to() == ZanpakutoState.BASE) {
							AnchorManager.reset(p); // swarm and rows end with every return to the sheathed or base form (the seal event repeats it)
						}
					}
				case StateEvent.ReiatsuSpent r -> { }
					case StateEvent.RageSpent r -> RageManager.spend(p, r.tenths());
				case StateEvent.CooldownStarted c -> { }
				case StateEvent.BroadcastEffect b -> {
						// r8 item 3: an ability that is aimed at its release (Shirafune) sends its effect event from the release phase
						// (AbilityExecutor.run), with the position and look of that moment; sending it now would show the press-time aim
						if (ctx == null || !ctx.spec.aimsAtRelease()) {
							ServerFx.effect(p, b.effectId(), b.seed(), ctx);
						}
						AnchorManager.onEffect(p, b.effectId(), b.seed());
					}
				case StateEvent.CancelEffects c -> SCHEDULER.cancel(p.getUuid());
				case StateEvent.RollbackTempBlocks r -> TempBlocks.rollback(srv, p.getUuid());
				case StateEvent.MirrorComponent m -> applyMirror(p, s.sm);
				case StateEvent.ScheduledPhase ph -> {
					if (ctx != null) {
						SCHEDULER.add(srv.getTicks() + ph.offsetTicks(), p.getUuid(), ph.serial(), ph.ability(), ph.phaseId(), ctx);
					}
				}
				case StateEvent.ShikaiModeChanged m -> { }
				case StateEvent.BankaiEnded b -> endBankaiEffects(p, b.who(), b.trigger());
				case StateEvent.KidoCast k -> KidoManager.cast(p, k);
				case StateEvent.ShunpoMove m -> {
					p.networkHandler.requestTeleport(m.toX(), m.toY(), m.toZ(), p.getYaw(), p.getPitch());
					// shunpo works in any state: tint by the released character, else by the sword in hand, else the default
						CharacterId who = s.sm.character() != CharacterId.NONE ? s.sm.character() : ModItems.characterOf(p.getMainHandStack());
						ServerFx.shunpo(p, m, who);
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
		CooldownData c = CooldownData.of(s.sm.cooldownRemainingAll(), s.sm.kidoCooldownRemainingAll());
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
			// round 5: no check of the main hand here on purpose. A slot change, a drop or the off hand never ends the released form
			// or its effects (only seal, death, logout, dimension, the bankai cap and the idle timer do); the render mirror below
			// simply follows the stack that is in the main hand.
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

			if (sm.barrierActive()) {
				BarrierShove.tick(p, sm.config(), now); // r8 item 6: nobody comes close to the dome, they are shoved out, no damage
			}
			if (sm.state() == ZanpakutoState.BANKAI && sm.character() == CharacterId.RUKIA) {
				BankaiField.tick(p, sm.config().bankaiField(), now); // the passive frost territory (mech2 task 4)
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
		if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		// block / parry first (design/PARRY_PLAN.md section 3): a parry cancels the hit, a block lowers it; the lowered
		// amount then goes through the barrier below
		float eff = amount;
		boolean modified = false;
		BlockOutcome block = BlockParry.onHit(p, source, amount);
		if (block.parried()) {
			DmgLog.noteVeto(p, dev.minebleach.reiatsutest.core.combat.HitRules.Outcome.PARRY);
			return false;
		}
		if (block.blocked()) {
			eff = block.damage();
			modified = true;
			DmgLog.noteVeto(p, dev.minebleach.reiatsutest.core.combat.HitRules.Outcome.BLOCKED);
		}
		Session s = SESSIONS.get(p.getUuid());
		if (s != null && s.sm.shikaiMode() == dev.minebleach.reiatsutest.core.state.ShikaiMode.BARRIER) {
			int total = Math.round(eff * 10.0F);
			int remaining = s.sm.absorbBarrier(total);
			if (remaining < total) {
				flush(p, s);
				if (remaining <= 0) {
					DmgLog.noteVeto(p, dev.minebleach.reiatsutest.core.combat.HitRules.Outcome.BARRIER);
					return false;
				}
				eff = remaining / 10.0F;
				modified = true;
			}
		}
		if (!modified) {
			return true;
		}
		reentrantDamage = true;
		try {
			p.damage(source, eff);
		} finally {
			reentrantDamage = false;
		}
		return false;
	}

	// ------------------------------------------------------------------ the one bankai cleanup (mech2 task 2)

	/** How many bankai cleanups ran since the server started (dev audit and tests). */
	private static int bankaiCleanups;

	/**
	 * THE cleanup of a bankai. Every end reason (45 s timer, seal, death, logout, dimension change, spectator, dev reset) reaches it
	 * through {@link StateEvent.BankaiEnded}, which the machine emits exactly once per bankai: the rows anchor (and the shikai swarm anchor unless the
	 * bankai only falls back to SHIKAI, r7: then the petal swarm around the owner goes on) and the Rukia
	 * field anchor are discarded now, the frost territory starts to thaw now (fast when the owner is dead, gone or elsewhere), scheduled
	 * phases of bankai abilities are dropped, and the clients get {@link EffectIds#BANKAI_CLEANUP} so every bankai effect of that owner
	 * ends there too. Idempotent: running it twice finds nothing left.
	 */
	static void endBankaiEffects(ServerPlayerEntity p, CharacterId who, Trigger why) {
		bankaiCleanups++;
		UUID id = p.getUuid();
		boolean fast = why == Trigger.DEATH || why == Trigger.LOGOUT || why == Trigger.DIMENSION_CHANGE;
		int anchors = AnchorManager.count(p);
		// r7: back to SHIKAI (the timer) the petal swarm around the owner goes on, only the big blades end; every other end resets both
		AnchorManager.onBankaiEnd(p, why == Trigger.BANKAI_CAP, who == CharacterId.BYAKUYA);
		int fieldAnchors = RukiaServerFx.endOf(id);
		boolean territory = BankaiField.end(id);
		int fields = p.getServer() == null ? 0 : FrostField.endOf(p.getServer(), id, fast);
		int phases = SCHEDULER.cancelIf(id, a -> a.requiredState == ZanpakutoState.BANKAI);
		dev.minebleach.reiatsutest.server.uryu.UryuManager.onBankaiEnd(id); // Uryu: the barrage and the bankai arrows in the air stop
		ServerFx.bankaiEnd(p, who, why);
		ReiatsuTest.LOGGER.info("[bankai-end] {} {} ended ({}): {} anchor(s), {} field anchor(s), territory {}, {} frost field(s) thawing{}, {} scheduled phase(s) dropped",
				p.getName().getString(), who, why, anchors, fieldAnchors, territory ? "ended" : "-", fields, fast ? " fast" : "", phases);
	}

	/** Slowness and damage pulses of the Rukia bankai territory since the server started: {slow, damage} (dev audit and the harness). */
	public static int[] fieldPulses() {
		return new int[] {BankaiField.slowPulses(), BankaiField.damagePulses()};
	}

	public static int bankaiCleanups() {
		return bankaiCleanups;
	}

	/**
	 * Server side leftovers of a player's bankai, for the audit after an end: anchors alive, Rukia field anchors, frost fields (not
	 * the manual dev ones), scheduled bankai phases and the passive territory (index 4). Everything must be 0 once the bankai is over.
	 */
	public static int[] bankaiLeftovers(ServerPlayerEntity p) {
		int phases = SCHEDULER.countFor(p.getUuid(), a -> a.requiredState == ZanpakutoState.BANKAI);
		return new int[] {AnchorManager.countRows(p), RukiaServerFx.liveOf(p.getUuid()), FrostField.liveFields(p.getUuid()), phases, BankaiField.live(p.getUuid())};
	}

	// ------------------------------------------------------------------ reiatsu from melee hits (r8 item 5)

	/**
	 * Gives the player reiatsu for a landed melee hit ({@link MeleeReiatsu}): {@code tenths} of a point ({@link BalanceConfig#meleeHitTenths()},
	 * 10 = 1.0 point by default), clamped to the maximum, synced at once. Returns the tenths really added.
	 */
	static int grantMeleeReiatsu(ServerPlayerEntity p, int tenths) {
		Session s = SESSIONS.get(p.getUuid());
		if (s == null || p.isDead()) {
			return 0;
		}
		int added = s.sm.grantReiatsu(tenths);
		if (added > 0) {
			flush(p, s);
		}
		ReiatsuTest.LOGGER.info("[reiatsu] {} melee hit: +{} (reiatsu {}/{} tenths)", p.getName().getString(), added, s.sm.reiatsu().value(),
				s.sm.reiatsu().max());
		return added;
	}

	/** The balance numbers of a player's machine (the glue classes of r8 read their knobs here), or the defaults without a session. */
	static BalanceConfig configOf(ServerPlayerEntity p) {
		Session s = SESSIONS.get(p.getUuid());
		return s == null ? FALLBACK_CONFIG : s.sm.config();
	}

	private static final BalanceConfig FALLBACK_CONFIG = BalanceConfig.defaults();

	// ------------------------------------------------------------------ barrier: control immunity (mech2 task 1)

	/** How many control effects the barrier has refused since the server started (dev check and tests). */
	private static int barrierRefusals;

	/**
	 * Server side, called by the mixins: does a Byakuya barrier with control immunity stand on this entity right now? False for
	 * everything that is not an online player with a session, on the client and for the dead.
	 */
	public static boolean barrierControlImmune(Entity e) {
		if (!(e instanceof ServerPlayerEntity p) || p.isDead()) {
			return false;
		}
		Session s = SESSIONS.get(p.getUuid());
		return s != null && s.sm.barrierControlImmune();
	}

	/** Bookkeeping of one refused control effect ({@code [barrier]} server log, counter). */
	public static void noteBarrierRefused(Entity e, String what) {
		barrierRefusals++;
		ReiatsuTest.LOGGER.info("[barrier] {} refused {}", e.getName().getString(), what);
	}

	public static int barrierRefusals() {
		return barrierRefusals;
	}

	// ------------------------------------------------------------------ info for commands and the harness

	/** Effect anchors (swarm, rows) alive for a player: harness and tests. */
	public static int anchorCount(ServerPlayerEntity p) {
		return AnchorManager.count(p);
	}

	/** Phase kind (S3: 0 standing, 32 scattered, 33 consumed) of the rows anchor of a player, -1 without rows: harness and tests. */
	public static int rowsPhase(ServerPlayerEntity p) {
		return AnchorManager.rowsPhase(p);
	}

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
