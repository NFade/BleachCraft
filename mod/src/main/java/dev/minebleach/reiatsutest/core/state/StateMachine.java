package dev.minebleach.reiatsutest.core.state;

import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuMath;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player zanpakuto state machine (STATE_MACHINE.md). Pure Java, deterministic given the {@link Clock}. The server
 * glue owns one instance per player, feeds it requests, Minecraft events and ticks, and executes the returned
 * {@link StateEvent}s. Nothing here knows about Minecraft.
 *
 * <p>Order of checks for requests (first failure wins): alive, sequence, rate limit, state, item, locks (transition,
 * gcd, settle, release), cooldown, reiatsu.
 */
public final class StateMachine {
	/** Upper bound on how many ticks one {@link #tick(int)} call walks (catch-up stays exact within it). */
	public static final int MAX_CATCH_UP = 100;
	/**
	 * Sequence number of a request that the server makes on behalf of the player (the voice bridge, which has no client
	 * counter): the stale-sequence check is skipped and the stored sequence is left alone, so key presses keep their own
	 * ordering. Every other rule (rate limit, state, item, locks, cooldown, reiatsu) still applies.
	 */
	public static final int SERVER_SEQ = -1;

	private final Clock clock;
	private final BalanceConfig cfg;
	private final RateLimiter limiter;
	private final EnumMap<AbilityId, Long> cooldownEnd = new EnumMap<>(AbilityId.class);

	private ReiatsuState reiatsu;
	private CharacterId character = CharacterId.NONE;
	private ZanpakutoState state = ZanpakutoState.SEALED;
	private long stateSince;
	private ShikaiMode mode = ShikaiMode.IDLE;
	private long modeEnd;
	private int barrierPool;
	private long bankaiEnd;

	private long transitionLockEnd;
	private long gcdEnd;
	private long settleEnd;
	private long releaseLockEnd;
	private long lastCast;
	private long handLostSince = -1;

	private boolean dead;
	private boolean spectator;
	private boolean seqSeen;
	private int lastSeq;
	private long castSerial;
	private int effectCounter;

	public StateMachine(Clock clock, BalanceConfig cfg, ReiatsuState initial) {
		this.clock = clock;
		this.cfg = cfg;
		this.reiatsu = new ReiatsuState(initial.value(), cfg.maxTenths());
		this.limiter = new RateLimiter(clock, cfg.rateLimitPerSecond());
		this.stateSince = clock.nowTick();
	}

	// ------------------------------------------------------------------ requests

	public TransitionResult request(TransitionRequest r) {
		RejectReason pre = preCheck(r.clientSeq());
		if (pre != null) {
			return reject(pre);
		}
		long now = clock.nowTick();
		ZanpakutoState target = r.target();
		if (!legal(state, target)) {
			return reject(RejectReason.NOT_IN_STATE);
		}
		if (state == ZanpakutoState.SEALED) {
			if (r.held() == CharacterId.NONE) {
				return reject(RejectReason.WRONG_ITEM);
			}
		} else if (inHandGrace() || r.held() != character) {
			return reject(RejectReason.WRONG_ITEM);
		}
		if (now < transitionLockEnd) {
			return reject(RejectReason.TRANSITION_LOCK);
		}
		if (state == ZanpakutoState.SEALED && now < releaseLockEnd) {
			return reject(RejectReason.RELEASE_LOCK);
		}
		if (target == ZanpakutoState.SHIKAI && !reiatsu.canSpend(cfg.shikaiReleaseCost())) {
			return reject(RejectReason.NOT_ENOUGH_REIATSU);
		}
		if (target == ZanpakutoState.BANKAI && !reiatsu.isFull()) {
			return reject(RejectReason.BANKAI_NOT_FULL);
		}

		ZanpakutoState from = state;
		List<StateEvent> ev = new ArrayList<>();
		switch (target) {
			case SHIKAI -> {
				spend(cfg.shikaiReleaseCost(), ev);
				character = r.held();
				state = ZanpakutoState.SHIKAI;
				stateSince = now;
				mode = ShikaiMode.IDLE;
				bankaiEnd = 0;
				lastCast = now;
				handLostSince = -1;
				transitionLockEnd = now + cfg.transitionLockTicks();
				castSerial++;
				ev.add(new StateEvent.StateChanged(from, state, Trigger.REQUEST));
				ev.add(new StateEvent.MirrorComponent(state));
				ev.add(new StateEvent.BroadcastEffect(EffectIds.shikaiRelease(character), nextSeed(now, EffectIds.shikaiRelease(character))));
			}
			case BANKAI -> {
				spend(cfg.bankaiCost(), ev);
				state = ZanpakutoState.BANKAI;
				stateSince = now;
				bankaiEnd = now + cfg.bankaiCapTicks();
				settleEnd = now + cfg.settleTicks();
				transitionLockEnd = now + cfg.transitionLockTicks();
				lastCast = now;
				castSerial++;
				ev.add(new StateEvent.StateChanged(from, state, Trigger.REQUEST));
				ev.add(new StateEvent.MirrorComponent(state));
				ev.add(new StateEvent.BroadcastEffect(EffectIds.bankaiRelease(character), nextSeed(now, EffectIds.bankaiRelease(character))));
			}
			case SEALED -> revertToSealed(now, Trigger.REQUEST, EffectIds.SEAL, cfg.sealLockTicks(), true, ev);
		}
		return new TransitionResult(ResultCode.OK, null, from, state, ev);
	}

	public TransitionResult cast(AbilityRequest r) {
		RejectReason pre = preCheck(r.clientSeq());
		if (pre != null) {
			return reject(pre);
		}
		long now = clock.nowTick();
		AbilityId a = r.ability();
		AbilitySpec spec = a == null ? null : cfg.spec(a);
		if (spec == null || !spec.enabled() || state == ZanpakutoState.SEALED
				|| a.character != character || a.requiredState != state) {
			return reject(RejectReason.NOT_IN_STATE);
		}
		if (inHandGrace() || r.held() != character) {
			return reject(RejectReason.WRONG_ITEM);
		}
		if (now < transitionLockEnd) {
			return reject(RejectReason.TRANSITION_LOCK);
		}
		if (now < gcdEnd) {
			return reject(RejectReason.GCD);
		}
		if (now < settleEnd) {
			return reject(RejectReason.SETTLE_LOCK);
		}
		Long cd = cooldownEnd.get(a);
		if (cd != null && now < cd) {
			return reject(RejectReason.ON_COOLDOWN);
		}
		if (!reiatsu.canSpend(spec.costTenths())) {
			return reject(RejectReason.NOT_ENOUGH_REIATSU);
		}

		List<StateEvent> ev = new ArrayList<>();
		spend(spec.costTenths(), ev);
		long end = now + spec.cooldownTicks();
		cooldownEnd.put(a, end);
		gcdEnd = now + cfg.gcdTicks();
		lastCast = now;
		ev.add(new StateEvent.CooldownStarted(a, end));
		if (a == AbilityId.MODE_ATTACK) {
			mode = ShikaiMode.ATTACK;
			modeEnd = now + cfg.attackModeTicks();
			barrierPool = 0;
			ev.add(new StateEvent.ShikaiModeChanged(mode));
		} else if (a == AbilityId.MODE_BARRIER) {
			mode = ShikaiMode.BARRIER;
			modeEnd = now + cfg.barrierTicks();
			barrierPool = cfg.barrierPoolTenths();
			ev.add(new StateEvent.ShikaiModeChanged(mode));
		}
		ev.add(new StateEvent.BroadcastEffect(spec.effectId(), nextSeed(now, spec.effectId())));
		List<Integer> phases = spec.phaseOffsets();
		for (int i = 0; i < phases.size(); i++) {
			ev.add(new StateEvent.ScheduledPhase(a, phases.get(i), i, castSerial));
		}
		return new TransitionResult(ResultCode.OK, null, state, state, ev);
	}

	// ------------------------------------------------------------------ time

	/**
	 * Advance the machine after {@code clock} moved forward by {@code dtTicks}. Walks tick by tick (at most
	 * {@link #MAX_CATCH_UP}, the latest ticks) so catching up is exactly equal to stepping.
	 */
	public List<StateEvent> tick(int dtTicks) {
		List<StateEvent> ev = new ArrayList<>();
		long now = clock.nowTick();
		int steps = Math.min(Math.max(dtTicks, 0), MAX_CATCH_UP);
		for (int i = steps - 1; i >= 0; i--) {
			step(now - i, ev);
		}
		return ev;
	}

	/** Fixed order inside one tick: mode expiry, hand grace, reiatsu batch, zero check, bankai cap, shikai idle. */
	private void step(long t, List<StateEvent> ev) {
		if (mode != ShikaiMode.IDLE && t >= modeEnd) {
			setMode(ShikaiMode.IDLE, ev);
		}
		if (state != ZanpakutoState.SEALED && handLostSince >= 0 && t - handLostSince >= cfg.handGraceTicks()) {
			revertToSealed(t, Trigger.HAND_LOST, EffectIds.SEAL, 0, false, ev);
		}
		if (t % cfg.regenBatchTicks() == 0) {
			reiatsu = ReiatsuMath.applyBatch(reiatsu, cfg.rate(state));
		}
		if (reiatsu.value() == 0) {
			if (state == ZanpakutoState.BANKAI) {
				revertToSealed(t, Trigger.REIATSU_ZERO, EffectIds.BANKAI_END, cfg.recoveryLockTicks(), true, ev);
			} else if (state == ZanpakutoState.SHIKAI) {
				revertToSealed(t, Trigger.REIATSU_ZERO, EffectIds.SEAL, cfg.sealLockTicks(), true, ev);
			}
		}
		if (state == ZanpakutoState.BANKAI && t >= bankaiEnd) {
			revertToSealed(t, Trigger.BANKAI_CAP, EffectIds.BANKAI_END, cfg.recoveryLockTicks(), true, ev);
		}
		if (state == ZanpakutoState.SHIKAI && t - lastCast >= cfg.shikaiIdleTicks()) {
			revertToSealed(t, Trigger.SHIKAI_IDLE, EffectIds.SEAL, cfg.sealLockTicks(), true, ev);
		}
	}

	// ------------------------------------------------------------------ Minecraft events

	/** The character of the zanpakuto in the main hand changed (NONE = none). Starts or cancels the hand grace. */
	public List<StateEvent> onHandChanged(CharacterId held) {
		if (state == ZanpakutoState.SEALED || held == character) {
			handLostSince = -1;
		} else if (handLostSince < 0) {
			handLostSince = clock.nowTick();
		}
		return List.of();
	}

	/** The released stack left the inventory: immediate SEALED. */
	public List<StateEvent> onItemDropped() {
		List<StateEvent> ev = new ArrayList<>();
		if (state != ZanpakutoState.SEALED) {
			revertToSealed(clock.nowTick(), Trigger.ITEM_DROPPED, EffectIds.SEAL, 0, false, ev);
		}
		return ev;
	}

	public List<StateEvent> onDeath() {
		dead = true;
		return reset(Trigger.DEATH, true);
	}

	/** Respawn after death: alive again with half a bar. */
	public List<StateEvent> onRespawn() {
		dead = false;
		reiatsu = new ReiatsuState(cfg.respawnTenths(), cfg.maxTenths());
		return List.of();
	}

	public List<StateEvent> onLogout() {
		return reset(Trigger.LOGOUT, true);
	}

	/** Cooldowns and reiatsu are kept. */
	public List<StateEvent> onDimensionChange() {
		return reset(Trigger.DIMENSION_CHANGE, false);
	}

	/** Spectator mode (or any other "cannot act" condition decided by the glue). */
	public List<StateEvent> setSpectator(boolean value) {
		List<StateEvent> ev = new ArrayList<>();
		if (value && !spectator) {
			ev.addAll(reset(Trigger.DIMENSION_CHANGE, false));
		}
		spectator = value;
		return ev;
	}

	// ------------------------------------------------------------------ barrier

	/**
	 * Byakuya barrier: reduces incoming damage (tenths of a HP) by the configured percentage while the dome lasts, until
	 * its pool is exhausted, then the dome collapses. Returns the damage that still applies to the caster.
	 */
	public int absorbBarrier(int damageTenths) {
		if (mode != ShikaiMode.BARRIER || barrierPool <= 0 || damageTenths <= 0) {
			return damageTenths;
		}
		int wanted = damageTenths * cfg.barrierReductionPercent() / 100;
		int absorbed = Math.min(wanted, barrierPool);
		barrierPool -= absorbed;
		if (barrierPool <= 0) {
			mode = ShikaiMode.IDLE;
		}
		return damageTenths - absorbed;
	}

	// ------------------------------------------------------------------ dev / test hooks

	public List<StateEvent> devSetReiatsu(int tenths) {
		reiatsu = new ReiatsuState(tenths, cfg.maxTenths());
		return List.of();
	}

	/** Forces a state without guards (the /reiatsu command). SEALED is a plain reset keeping cooldowns. */
	public List<StateEvent> devSetState(ZanpakutoState target, CharacterId who) {
		long now = clock.nowTick();
		List<StateEvent> ev = new ArrayList<>();
		if (target == ZanpakutoState.SEALED || who == CharacterId.NONE) {
			return reset(Trigger.DEV_COMMAND, false);
		}
		ZanpakutoState from = state;
		castSerial++;
		ev.add(new StateEvent.CancelEffects());
		ev.add(new StateEvent.RollbackTempBlocks());
		character = who;
		state = target;
		stateSince = now;
		mode = ShikaiMode.IDLE;
		barrierPool = 0;
		bankaiEnd = target == ZanpakutoState.BANKAI ? now + cfg.bankaiCapTicks() : 0;
		lastCast = now;
		handLostSince = -1;
		transitionLockEnd = 0;
		gcdEnd = 0;
		settleEnd = 0;
		releaseLockEnd = 0;
		ev.add(new StateEvent.StateChanged(from, state, Trigger.DEV_COMMAND));
		ev.add(new StateEvent.MirrorComponent(state));
		return ev;
	}

	public void devClearCooldowns() {
		cooldownEnd.clear();
		gcdEnd = 0;
	}

	// ------------------------------------------------------------------ queries

	public ZanpakutoSnapshot snapshot() {
		return new ZanpakutoSnapshot(character, state, stateSince, mode, bankaiEnd);
	}

	public ReiatsuState reiatsu() {
		return reiatsu;
	}

	public ZanpakutoState state() {
		return state;
	}

	public CharacterId character() {
		return character;
	}

	public ShikaiMode shikaiMode() {
		return mode;
	}

	public int barrierPoolTenths() {
		return barrierPool;
	}

	public boolean isDead() {
		return dead;
	}

	public boolean inHandGrace() {
		return handLostSince >= 0 && state != ZanpakutoState.SEALED;
	}

	/** Increases whenever running effects must be dropped (seal, auto-revert, reset). */
	public long castSerial() {
		return castSerial;
	}

	public long releaseLockEndTick() {
		return releaseLockEnd;
	}

	public int cooldownRemaining(AbilityId id) {
		Long end = cooldownEnd.get(id);
		if (end == null) {
			return 0;
		}
		return (int) Math.max(0L, end - clock.nowTick());
	}

	/** Remaining ticks of every ability that is on cooldown, for the owner-only cooldown sync. */
	public Map<AbilityId, Integer> cooldownRemainingAll() {
		EnumMap<AbilityId, Integer> out = new EnumMap<>(AbilityId.class);
		long now = clock.nowTick();
		for (Map.Entry<AbilityId, Long> e : cooldownEnd.entrySet()) {
			if (e.getValue() > now) {
				out.put(e.getKey(), (int) (e.getValue() - now));
			}
		}
		return out;
	}

	public BalanceConfig config() {
		return cfg;
	}

	// ------------------------------------------------------------------ internals

	private static boolean legal(ZanpakutoState from, ZanpakutoState to) {
		return switch (from) {
			case SEALED -> to == ZanpakutoState.SHIKAI;
			case SHIKAI -> to == ZanpakutoState.BANKAI || to == ZanpakutoState.SEALED;
			case BANKAI -> to == ZanpakutoState.SEALED;
		};
	}

	private RejectReason preCheck(int seq) {
		if (dead || spectator) {
			return RejectReason.DEAD_OR_SPECTATOR;
		}
		if (seq != SERVER_SEQ) {
			if (seqSeen && seq <= lastSeq) {
				return RejectReason.STALE_SEQ;
			}
			seqSeen = true;
			lastSeq = seq;
		}
		if (!limiter.tryAcquire()) {
			return RejectReason.RATE_LIMITED;
		}
		return null;
	}

	private TransitionResult reject(RejectReason reason) {
		return new TransitionResult(reason.toWire(), reason, state, state, List.of());
	}

	private void spend(int cost, List<StateEvent> ev) {
		reiatsu = reiatsu.plus(-cost);
		ev.add(new StateEvent.ReiatsuSpent(cost));
	}

	private void setMode(ShikaiMode m, List<StateEvent> ev) {
		mode = m;
		if (m == ShikaiMode.IDLE) {
			barrierPool = 0;
		}
		ev.add(new StateEvent.ShikaiModeChanged(m));
	}

	/** T3..T8: any released state back to SEALED. Ability cooldowns are kept. */
	private void revertToSealed(long t, Trigger trigger, int effectId, int releaseLock, boolean transitionLock,
			List<StateEvent> ev) {
		ZanpakutoState from = state;
		state = ZanpakutoState.SEALED;
		character = CharacterId.NONE;
		mode = ShikaiMode.IDLE;
		barrierPool = 0;
		bankaiEnd = 0;
		stateSince = t;
		settleEnd = 0;
		handLostSince = -1;
		castSerial++;
		releaseLockEnd = t + releaseLock;
		transitionLockEnd = transitionLock ? t + cfg.transitionLockTicks() : 0;
		ev.add(new StateEvent.CancelEffects());
		ev.add(new StateEvent.RollbackTempBlocks());
		ev.add(new StateEvent.StateChanged(from, state, trigger));
		ev.add(new StateEvent.MirrorComponent(state));
		if (effectId > 0) {
			ev.add(new StateEvent.BroadcastEffect(effectId, nextSeed(t, effectId)));
		}
	}

	/** T9: death, logout, dimension change. Everything stops; cooldowns only on death and logout. */
	private List<StateEvent> reset(Trigger trigger, boolean clearCooldowns) {
		long now = clock.nowTick();
		List<StateEvent> ev = new ArrayList<>();
		ZanpakutoState from = state;
		state = ZanpakutoState.SEALED;
		character = CharacterId.NONE;
		mode = ShikaiMode.IDLE;
		barrierPool = 0;
		bankaiEnd = 0;
		stateSince = now;
		handLostSince = -1;
		transitionLockEnd = 0;
		gcdEnd = 0;
		settleEnd = 0;
		releaseLockEnd = 0;
		castSerial++;
		if (clearCooldowns) {
			cooldownEnd.clear();
		}
		ev.add(new StateEvent.CancelEffects());
		ev.add(new StateEvent.RollbackTempBlocks());
		if (from != ZanpakutoState.SEALED) {
			ev.add(new StateEvent.StateChanged(from, state, trigger));
			ev.add(new StateEvent.MirrorComponent(state));
		}
		return ev;
	}

	/** Deterministic per (tick, counter, effect) seed so replays and tests are reproducible. */
	private int nextSeed(long t, int effectId) {
		long x = t * 0x9E3779B97F4A7C15L + (long) (++effectCounter) * 0xBF58476D1CE4E5B9L + effectId * 0x94D049BB133111EBL;
		x ^= x >>> 30;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 27;
		x *= 0x94D049BB133111EBL;
		x ^= x >>> 31;
		return (int) (x ^ (x >>> 32));
	}
}
