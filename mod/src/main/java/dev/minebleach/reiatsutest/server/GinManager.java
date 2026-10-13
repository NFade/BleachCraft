package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.ReiatsuTest;
import dev.minebleach.reiatsutest.core.gin.GinFxModes;
import dev.minebleach.reiatsutest.core.gin.GinGeometry;
import dev.minebleach.reiatsutest.core.gin.GinCover;
import dev.minebleach.reiatsutest.core.gin.GinParams;
import dev.minebleach.reiatsutest.core.gin.GinRecast;
import dev.minebleach.reiatsutest.core.gin.SliverBook;
import dev.minebleach.reiatsutest.core.state.AbilityId;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import dev.minebleach.reiatsutest.core.state.CharacterId;
import dev.minebleach.reiatsutest.core.state.RejectReason;
import dev.minebleach.reiatsutest.core.state.ResultCode;
import dev.minebleach.reiatsutest.core.state.StateMachine;
import dev.minebleach.reiatsutest.core.state.TransitionResult;
import dev.minebleach.reiatsutest.core.state.Trigger;
import dev.minebleach.reiatsutest.core.state.ZanpakutoState;
import dev.minebleach.reiatsutest.net.EffectEventS2C;
import dev.minebleach.reiatsutest.registry.ModItems;
import dev.minebleach.reiatsutest.server.gin.CastRoot;
import dev.minebleach.reiatsutest.server.gin.SelfPull;
import dev.minebleach.reiatsutest.server.gin.SliverEffect;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Server glue of the Gin mechanisms that outlive one ability phase (design/GIN.md sections 4 to 6): the sliver registry and its cleanse
 * rules, the Korose recast (press 2 of the same key), the Hakufuku cover and the cleanup hooks. The rules are pure and tested
 * ({@code core/gin}); this class reads and changes the game. Abilities themselves are in {@link GinAbilities}.
 *
 * <p>Cleanse reasons (all end in {@link #onSliverRemoved}): the carrier lost its marker effect (milk, totem, /effect clear), died, is
 * farther than the recast range, the 12 s timer, Gin died / logged out / left BANKAI ({@link #onBankaiEnded}, called by the one bankai
 * cleanup of {@link ZanpakutoManager}).
 */
final class GinManager {
	private static final SliverBook BOOK = new SliverBook();
	private static final Map<UUID, GinRecast> RECASTS = new HashMap<>();
	private static final Map<UUID, GinCover> COVERS = new HashMap<>();
	/** Where the channelling Gin stood when the channel began (x, z): a push farther than the push limit is knockback. */
	private static final Map<UUID, double[]> ANCHORS = new HashMap<>();

	private GinManager() {
	}

	static void init() {
		SliverEffect.register();
		ServerTickEvents.END_SERVER_TICK.register(GinManager::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> onDamaged(entity, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> onLeave(handler.player, srv));
		ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
			CastRoot.clearAll(srv);
			SelfPull.clearAll();
			BOOK.clear();
			RECASTS.clear();
			COVERS.clear();
			ANCHORS.clear();
		});
	}

	// ------------------------------------------------------------------ small helpers

	private static int now(MinecraftServer srv) {
		return srv.getTicks();
	}

	static GinRecast recastOf(ServerPlayerEntity p) {
		return RECASTS.computeIfAbsent(p.getUuid(), id -> new GinRecast(GinRecast.Config.of(ZanpakutoManager.configOf(p).spec(AbilityId.KOROSE))));
	}

	private static GinCover coverOf(UUID id) {
		return COVERS.computeIfAbsent(id, k -> new GinCover());
	}

	static Entity find(MinecraftServer srv, UUID id) {
		for (ServerWorld w : srv.getWorlds()) {
			Entity e = w.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/** Slivers alive (tests, the harness and the status log). */
	static int sliverCount() {
		return BOOK.size();
	}

	static boolean hasSliver(UUID target) {
		return BOOK.has(target);
	}

	// ------------------------------------------------------------------ effect events

	private static void send(ServerPlayerEntity p, boolean ownerOnly, CustomPayload payload) {
		Set<ServerPlayerEntity> to = new HashSet<>();
		to.add(p);
		if (!ownerOnly) {
			to.addAll(PlayerLookup.tracking(p));
		}
		for (ServerPlayerEntity v : to) {
			if (ServerPlayNetworking.canSend(v, payload.getId())) {
				ServerPlayNetworking.send(v, payload);
			}
		}
	}

	/** One Gin effect event ({@link GinFxModes}): x y z = the caster's eye, d = the unit look direction. */
	static void fx(ServerPlayerEntity p, int effectId, int mode, Vec3d dir, int targetId, boolean ownerOnly, float... rest) {
		float[] params = new float[Math.max(GinFxModes.MIN_LEN, rest.length + 1)];
		params[0] = mode;
		System.arraycopy(rest, 0, params, 1, rest.length);
		Vec3d eye = p.getEyePos();
		int tick = now(p.getServer());
		send(p, ownerOnly, new EffectEventS2C(effectId, p.getId(), tick * 31 + effectId, eye.x, eye.y, eye.z, (float) dir.x, (float) dir.y, (float) dir.z,
				targetId, tick, params));
	}

	static void strikeFx(ServerPlayerEntity p, int effectId, Vec3d dir, Vec3d end, boolean hit, boolean quiet, int index) {
		fx(p, effectId, GinFxModes.STRIKE, dir, -1, false, (float) end.x, (float) end.y, (float) end.z, hit ? 1f : 0f, quiet ? 1f : 0f, index);
	}

	// ------------------------------------------------------------------ cooldown seam

	/**
	 * Sets the Korose cooldown to exactly {@code ticks} from now and pushes it to the owner's client (the cooldown attachment only syncs when
	 * the set of cooldowns changes, so the sync is forced). No session (logout) = nothing to do.
	 */
	static void applyCooldown(ServerPlayerEntity p, int ticks) {
		StateMachine sm = ZanpakutoManager.machine(p);
		if (sm == null) {
			return;
		}
		ZanpakutoManager.Session s = ZanpakutoManager.session(p);
		s.sm.setCooldown(AbilityId.KOROSE, ticks);
		s.lastCooldownKeys = null;
		ZanpakutoManager.apply(p, s, List.of(), null);
	}

	// ------------------------------------------------------------------ Korose press 1: the sliver

	/** Press 1 hit {@code target} for real: leave the sliver (the owner has at most one) and tell the owner's client. */
	static void place(ServerPlayerEntity p, LivingEntity target) {
		MinecraftServer srv = p.getServer();
		int now = now(srv);
		AbilitySpec sp = ZanpakutoManager.configOf(p).spec(AbilityId.KOROSE);
		int ticks = sp.intNum(GinParams.SLIVER_TICKS);
		for (SliverBook.Removal r : BOOK.cleanseOwner(p.getUuid(), SliverBook.Reason.REPLACED)) {
			onSliverRemoved(srv, r);
		}
		SliverBook.Sliver old = BOOK.place(target.getUuid(), p.getUuid(), now + ticks);
		if (old != null) {
			onSliverRemoved(srv, new SliverBook.Removal(old, SliverBook.Reason.REPLACED));
		}
		SliverEffect.apply(target, ticks + 20);
		recastOf(p).marked(target.getUuid(), now);
		fx(p, 66, GinFxModes.SLIVER, p.getRotationVec(1.0F), target.getId(), true, ticks);
		ReiatsuTest.LOGGER.info("[gin] {} left a sliver on {} for {} ticks", p.getName().getString(), target.getName().getString(), ticks);
	}

	/** Renjin hit {@code target}: a sliver of this Gin on it starts its 12 s again. */
	static void onRenjinHit(ServerPlayerEntity p, LivingEntity target) {
		int now = now(p.getServer());
		int ticks = ZanpakutoManager.configOf(p).spec(AbilityId.KOROSE).intNum(GinParams.SLIVER_TICKS);
		if (BOOK.refresh(target.getUuid(), p.getUuid(), now, ticks)) {
			recastOf(p).refresh(target.getUuid(), now);
			SliverEffect.apply(target, ticks + 20);
			fx(p, 66, GinFxModes.SLIVER, p.getRotationVec(1.0F), target.getId(), true, ticks);
		}
	}

	// ------------------------------------------------------------------ Korose press 2: the recast

	/**
	 * Called by {@code ZanpakutoManager.performCast} for a Korose key press BEFORE the machine sees it. Returns null when the press is a
	 * normal first cast (no live mark: the machine decides, cost and cooldown as for any ability); otherwise the answer to the player.
	 * The recast never reaches the machine, so it is free, has no gap and charges nothing; its cooldowns are set by {@link #applyCooldown}.
	 */
	static TransitionResult tryRecast(ServerPlayerEntity p, ZanpakutoManager.Session s) {
		StateMachine sm = s.sm;
		if (sm.isDead() || sm.state() != ZanpakutoState.BANKAI || sm.character() != CharacterId.GIN
				|| ModItems.characterOf(p.getMainHandStack()) != CharacterId.GIN) {
			return null;
		}
		GinRecast rc = RECASTS.get(p.getUuid());
		if (rc == null) {
			return null;
		}
		MinecraftServer srv = p.getServer();
		int now = now(srv);
		ZanpakutoState st = sm.state();
		switch (rc.decide(now)) {
			case MARK -> {
				return null;
			}
			case BUSY -> {
				return new TransitionResult(ResultCode.DENIED_STATE, RejectReason.NOT_IN_STATE, st, st, List.of());
			}
			case RECAST -> {
			}
		}
		UUID id = rc.target();
		Entity e = id == null ? null : find(srv, id);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.getWorld() != p.getWorld()) {
			SliverBook.Removal r = id == null ? null : BOOK.cleanse(id, e == null || !(e instanceof LivingEntity le && le.isAlive())
					? SliverBook.Reason.TARGET_DEATH : SliverBook.Reason.OUT_OF_RANGE);
			if (r != null) {
				onSliverRemoved(srv, r);
			}
			return null;
		}
		if (!Targeting.valid(p, target)) {
			return new TransitionResult(ResultCode.DENIED_STATE, RejectReason.NOT_IN_STATE, st, st, List.of());
		}
		double dist = p.getEyePos().distanceTo(target.getBoundingBox().getCenter());
		GinRecast.Outcome o = rc.beginChannel(now, dist);
		if (o.kind() == GinRecast.Kind.FIZZLED) {
			SliverBook.Removal r = BOOK.cleanse(target.getUuid(), SliverBook.Reason.OUT_OF_RANGE);
			if (r != null) {
				onSliverRemoved(srv, r);
			}
			return null;
		}
		if (o.kind() != GinRecast.Kind.STARTED) {
			return null;
		}
		AbilitySpec sp = sm.config().spec(AbilityId.KOROSE);
		BOOK.extendTo(target.getUuid(), rc.channelEnd() + 2);
		CastRoot.root(p, sp.intNum(GinParams.CHANNEL_TICKS), sp.intNum(GinParams.ROOT_AMP));
		ANCHORS.put(p.getUuid(), new double[] {p.getX(), p.getZ()});
		fx(p, 66, GinFxModes.CHANNEL, p.getRotationVec(1.0F), target.getId(), false, sp.intNum(GinParams.CHANNEL_TICKS));
		ReiatsuTest.LOGGER.info("[gin] {} recast Korose on {} ({} blocks), channel {} ticks", p.getName().getString(), target.getName().getString(),
				String.format(java.util.Locale.ROOT, "%.1f", dist), sp.intNum(GinParams.CHANNEL_TICKS));
		return new TransitionResult(ResultCode.OK, null, st, st, List.of());
	}

	/** The channel ended without a dissolve (interrupt on Gin's side, or the sliver was cleansed during it). */
	private static void endChannel(ServerPlayerEntity p, GinRecast.Outcome o, UUID target, SliverBook.Reason spent) {
		ANCHORS.remove(p.getUuid());
		CastRoot.release(p);
		if (o.changesCooldown()) {
			applyCooldown(p, o.cooldownTicks());
		}
		if (spent != null && target != null) {
			SliverBook.Removal r = BOOK.cleanse(target, spent);
			if (r != null) {
				onSliverRemoved(p.getServer(), r);
			}
		}
		fx(p, 66, GinFxModes.INTERRUPT, p.getRotationVec(1.0F), -1, false);
		ReiatsuTest.LOGGER.info("[gin] {} Korose channel ended: {}, cooldown {}", p.getName().getString(), o.kind(), o.cooldownTicks());
	}

	/** The channel ran out: the target dissolves (18 HP that skip the block, the barrier and the armor; a totem still pops). */
	private static void dissolve(ServerPlayerEntity p, GinRecast rc, UUID targetId, GinRecast.Outcome o) {
		MinecraftServer srv = p.getServer();
		ANCHORS.remove(p.getUuid());
		CastRoot.release(p);
		Entity e = find(srv, targetId);
		SliverBook.Removal r = BOOK.cleanse(targetId, SliverBook.Reason.DISSOLVED);
		if (r != null) {
			onSliverRemoved(srv, r);
		}
		AbilitySpec sp = ZanpakutoManager.configOf(p).spec(AbilityId.KOROSE);
		if (e instanceof LivingEntity target && target.isAlive() && target.getWorld() == p.getWorld() && Targeting.valid(p, target)) {
			float hp = sp.floatNum(GinParams.DISSOLVE_DAMAGE);
			DmgLog.begin("gin.korose.dissolve");
			try {
				DamageSource src = p.getServerWorld().getDamageSources().create(DamageTypes.INDIRECT_MAGIC, p);
				float before = target.getHealth() + target.getAbsorptionAmount();
				target.timeUntilRegen = 0;
				ZanpakutoManager.bypassMitigation = true; // poison, not a hit: no sword block, no Byakuya barrier
				try {
					target.damage(src, hp);
				} finally {
					ZanpakutoManager.bypassMitigation = false;
				}
				float lost = Math.max(0f, before - (target.getHealth() + target.getAbsorptionAmount()));
				DmgLog.hit(p, target, hp, lost, lost > 0f ? dev.minebleach.reiatsutest.core.combat.HitRules.Outcome.DAMAGED
						: dev.minebleach.reiatsutest.core.combat.HitRules.Outcome.NO_HEALTH_LOST);
			} finally {
				DmgLog.end();
			}
			Vec3d c = target.getBoundingBox().getCenter();
			fx(p, 66, GinFxModes.DISSOLVE, p.getRotationVec(1.0F), target.getId(), false, (float) c.x, (float) c.y, (float) c.z);
		}
		applyCooldown(p, o.cooldownTicks());
		ReiatsuTest.LOGGER.info("[gin] {} Korose dissolved {}", p.getName().getString(), e == null ? "(gone)" : e.getName().getString());
	}

	// ------------------------------------------------------------------ cleanse

	/** Everything that follows a sliver leaving the book (marker, the owner's recast and client). */
	private static void onSliverRemoved(MinecraftServer srv, SliverBook.Removal r) {
		SliverBook.Sliver s = r.sliver();
		Entity e = find(srv, s.target());
		if (e instanceof LivingEntity le && r.reason() != SliverBook.Reason.MILK) {
			SliverEffect.remove(le);
		}
		ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(s.owner());
		GinRecast rc = RECASTS.get(s.owner());
		if (rc != null && r.reason() != SliverBook.Reason.DISSOLVED && r.reason() != SliverBook.Reason.INTERRUPTED) {
			GinRecast.Outcome o = rc.sliverGone(r.reason());
			if (o.kind() == GinRecast.Kind.FIZZLED && owner != null) {
				endChannel(owner, o, null, null);
			}
		}
		if (owner != null) {
			fx(owner, 66, GinFxModes.GONE, owner.getRotationVec(1.0F), e == null ? -1 : e.getId(), true);
		}
		ReiatsuTest.LOGGER.info("[gin] sliver of {} on {} ended: {}", owner == null ? s.owner() : owner.getName().getString(),
				e == null ? s.target() : e.getName().getString(), r.reason());
	}

	/** Gin's bankai is over for any reason (death, logout, seal, dimension, the 45 s cap): called by ZanpakutoManager.endBankaiEffects. */
	static void onBankaiEnded(ServerPlayerEntity p, CharacterId who, Trigger why) {
		if (who != CharacterId.GIN) {
			return;
		}
		UUID id = p.getUuid();
		MinecraftServer srv = p.getServer();
		boolean gone = why == Trigger.DEATH || why == Trigger.LOGOUT;
		SliverBook.Reason reason = gone ? SliverBook.Reason.OWNER_DEATH : SliverBook.Reason.OWNER_LEFT_BANKAI;
		GinRecast rc = RECASTS.get(id);
		GinRecast.Outcome o = rc == null ? null : rc.onStateChange();
		CastRoot.release(p);
		SelfPull.cancel(id);
		ANCHORS.remove(id);
		resetCover(p);
		for (SliverBook.Removal r : BOOK.cleanseOwner(id, reason)) {
			onSliverRemoved(srv, r);
		}
		if (rc != null) {
			rc.reset();
		}
		if (o != null && o.kind() == GinRecast.Kind.INTERRUPTED && !gone) {
			applyCooldown(p, o.cooldownTicks());
			fx(p, 66, GinFxModes.INTERRUPT, p.getRotationVec(1.0F), -1, false);
		}
	}

	// ------------------------------------------------------------------ events

	private static void onDamaged(LivingEntity entity, float damageTaken) {
		if (!(entity instanceof ServerPlayerEntity p) || damageTaken <= 0f) {
			return;
		}
		GinRecast rc = RECASTS.get(p.getUuid());
		if (rc != null && rc.phase() == GinRecast.Phase.CHANNEL) {
			UUID target = rc.target();
			GinRecast.Outcome o = rc.onHit(damageTaken);
			if (o.kind() == GinRecast.Kind.INTERRUPTED) {
				endChannel(p, o, target, interruptSpends(rc) ? SliverBook.Reason.INTERRUPTED : null);
			}
		}
		GinCover cover = COVERS.get(p.getUuid());
		if (cover != null && cover.damaged(now(p.getServer()))) {
			p.removeStatusEffect(StatusEffects.INVISIBILITY);
			ReiatsuTest.LOGGER.info("[gin] {} Hakufuku broken by damage", p.getName().getString());
		}
	}

	private static boolean interruptSpends(GinRecast rc) {
		return !rc.config().keepsMark();
	}

	private static void onDeath(LivingEntity entity) {
		MinecraftServer srv = entity.getServer();
		if (srv == null) {
			return;
		}
		SliverBook.Removal r = BOOK.cleanse(entity.getUuid(), SliverBook.Reason.TARGET_DEATH);
		if (r != null) {
			onSliverRemoved(srv, r);
		}
		if (entity instanceof ServerPlayerEntity p) {
			CastRoot.release(p);
			SelfPull.cancel(p.getUuid());
			ANCHORS.remove(p.getUuid());
			resetCover(p);
			GinRecast rc = RECASTS.get(p.getUuid());
			if (rc != null) {
				rc.reset();
			}
		}
	}

	private static void onLeave(ServerPlayerEntity p, MinecraftServer srv) {
		UUID id = p.getUuid();
		CastRoot.release(p);
		CastRoot.forget(id);
		SelfPull.cancel(id);
		ANCHORS.remove(id);
		COVERS.remove(id);
		GinRecast rc = RECASTS.remove(id);
		if (rc != null) {
			rc.reset();
		}
		// a logged-out carrier is out of reach: its sliver ends; a logged-out owner's slivers end with his bankai (endBankaiEffects)
		SliverBook.Removal r = BOOK.cleanse(id, SliverBook.Reason.OUT_OF_RANGE);
		if (r != null) {
			onSliverRemoved(srv, r);
		}
		for (SliverBook.Removal o : BOOK.cleanseOwner(id, SliverBook.Reason.OWNER_DEATH)) {
			onSliverRemoved(srv, o);
		}
	}

	private static void resetCover(ServerPlayerEntity p) {
		GinCover c = COVERS.get(p.getUuid());
		if (c != null) {
			if (c.hidden(now(p.getServer()))) {
				p.removeStatusEffect(StatusEffects.INVISIBILITY);
			}
			c.reset();
		}
	}

	// ------------------------------------------------------------------ Hakufuku

	/** Hakufuku: Invisibility without particles or icon, glow removed, flash window for the next thrust. */
	static void hide(ServerPlayerEntity p, AbilitySpec sp) {
		int now = now(p.getServer());
		int hide = sp.intNum(GinParams.HIDE_TICKS);
		coverOf(p.getUuid()).start(now, hide, sp.intNum(GinParams.FLASH_TICKS));
		p.removeStatusEffect(StatusEffects.GLOWING);
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, hide, 0, false, false, false));
		ReiatsuTest.LOGGER.info("[gin] {} hidden for {} ticks", p.getName().getString(), hide);
	}

	/** A thrust leaves now: true when it must show no white flash (the window of Hakufuku, used up by this thrust). */
	static boolean takeNoFlash(ServerPlayerEntity p) {
		GinCover c = COVERS.get(p.getUuid());
		return c != null && c.takeNoFlash(now(p.getServer()));
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer srv) {
		int now = now(srv);
		CastRoot.tick(srv, now);
		SelfPull.tick(srv, now);
		// a root only lives while Gin is still released (seal, death and the like end it at once)
		if (CastRoot.active() > 0) {
			for (UUID id : CastRoot.all()) {
				ServerPlayerEntity p = srv.getPlayerManager().getPlayer(id);
				StateMachine sm = p == null ? null : ZanpakutoManager.machine(p);
				if (p != null && (p.isDead() || sm == null || sm.character() != CharacterId.GIN
						|| sm.state() == ZanpakutoState.SEALED || sm.state() == ZanpakutoState.BASE)) {
					CastRoot.release(p);
				}
			}
		}
		if (BOOK.size() > 0) {
			checkSlivers(srv, now);
		}
		for (Map.Entry<UUID, GinRecast> en : new HashMap<>(RECASTS).entrySet()) {
			ServerPlayerEntity p = srv.getPlayerManager().getPlayer(en.getKey());
			GinRecast rc = en.getValue();
			if (p == null || rc.phase() == GinRecast.Phase.NONE) {
				continue;
			}
			if (rc.phase() == GinRecast.Phase.CHANNEL) {
				Cause cause = channelCause(p, rc, now);
				if (cause != null) {
					UUID target = rc.target();
					GinRecast.Outcome o = switch (cause) {
						case CONTROL -> rc.onControl();
						case KNOCKBACK -> rc.onKnockback();
						case STATE -> rc.onStateChange();
					};
					if (o.kind() == GinRecast.Kind.INTERRUPTED) {
						endChannel(p, o, target, interruptSpends(rc) ? SliverBook.Reason.INTERRUPTED : null);
					}
					continue;
				}
			}
			UUID target = rc.target();
			GinRecast.Outcome o = rc.tick(now);
			if (o.kind() == GinRecast.Kind.DISSOLVE) {
				dissolve(p, rc, target, o);
			}
		}
	}

	private static void checkSlivers(MinecraftServer srv, int now) {
		for (SliverBook.Sliver s : BOOK.all()) {
			Entity e = find(srv, s.target());
			ServerPlayerEntity owner = srv.getPlayerManager().getPlayer(s.owner());
			SliverBook.Reason why = null;
			if (e == null) {
				why = SliverBook.Reason.OUT_OF_RANGE;
			} else if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				why = SliverBook.Reason.TARGET_DEATH;
			} else if (owner == null) {
				why = SliverBook.Reason.OWNER_DEATH;
			} else if (owner.getWorld() != le.getWorld()
					|| GinGeometry.outOfRange(owner.distanceTo(le), recastOf(owner).config().range())) {
				why = SliverBook.Reason.OUT_OF_RANGE;
			} else if (now < s.expiry() && !SliverEffect.present(le)) {
				why = SliverBook.Reason.MILK; // the marker was cleared: milk bucket, totem pop or /effect clear
			}
			if (why != null) {
				SliverBook.Removal r = BOOK.cleanse(s.target(), why);
				if (r != null) {
					onSliverRemoved(srv, r);
				}
			}
		}
		for (SliverBook.Removal r : BOOK.expire(now)) {
			onSliverRemoved(srv, r);
		}
	}

	private enum Cause {
		CONTROL, KNOCKBACK, STATE
	}

	/** What breaks a running channel on Gin's side this tick (hits arrive through {@link #onDamaged}), or null. */
	private static Cause channelCause(ServerPlayerEntity p, GinRecast rc, int now) {
		StateMachine sm = ZanpakutoManager.machine(p);
		if (p.isDead() || sm == null || sm.state() != ZanpakutoState.BANKAI || sm.character() != CharacterId.GIN) {
			return Cause.STATE;
		}
		AbilitySpec sp = sm.config().spec(AbilityId.KOROSE);
		double[] a = ANCHORS.get(p.getUuid());
		if (a != null) {
			double limit = sp.num(GinParams.PUSH_LIMIT);
			double dx = p.getX() - a[0];
			double dz = p.getZ() - a[1];
			if (dx * dx + dz * dz > limit * limit) {
				return Cause.KNOCKBACK; // a rooted player does not move by himself: this was a push (or a teleport)
			}
		}
		if (controlled(p, (int) (rc.channelEnd() - now), sp.intNum(GinParams.ROOT_AMP))) {
			return Cause.CONTROL;
		}
		return null;
	}

	/**
	 * Any control effect on Gin that is not his own root: frozen, a Slowness that is not the hidden one of the root (another amplifier or a
	 * longer time than the channel has left), nausea, levitation or blindness (kido bind, frost, Tsukishiro, parry stagger ...).
	 */
	private static boolean controlled(ServerPlayerEntity p, int channelLeft, int rootAmp) {
		if (p.isFrozen()) {
			return true;
		}
		for (StatusEffectInstance e : p.getActiveStatusEffects().values()) {
			if (e.getEffectType().equals(StatusEffects.SLOWNESS)) {
				if (e.getAmplifier() != rootAmp || e.getDuration() > channelLeft + 2) {
					return true;
				}
			} else if (e.getEffectType().equals(StatusEffects.NAUSEA) || e.getEffectType().equals(StatusEffects.LEVITATION)
					|| e.getEffectType().equals(StatusEffects.BLINDNESS)) {
				return true;
			}
		}
		return false;
	}
}
