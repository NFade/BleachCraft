package dev.minebleach.reiatsutest.core.state;

import dev.minebleach.reiatsutest.core.kido.KidoConfig;
import dev.minebleach.reiatsutest.core.kido.KidoId;
import dev.minebleach.reiatsutest.core.kido.KidoRequest;
import dev.minebleach.reiatsutest.core.kido.KidoSpec;
import dev.minebleach.reiatsutest.core.rage.RageConfig;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuMath;
import dev.minebleach.reiatsutest.core.reiatsu.ReiatsuState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Per-player zanpakuto state machine (STATE_MACHINE.md). Pure Java, deterministic given the {@link Clock}. The server
 * glue owns one instance per player, feeds it requests, Minecraft events and ticks, and executes the returned
 * {@link StateEvent}s. Nothing here knows about Minecraft.
 *
 * <p>Order of checks for requests (first failure wins): alive, sequence, rate limit, state, item, locks (transition,
 * gcd, settle, release), cooldown, rage (shikai and bankai entry only), reiatsu.
 *
 * <p>Round 5: the sword leaving the main hand (slot change, drop, off hand) no longer ends anything. SHIKAI / BANKAI end only by
 * seal, death, logout, dimension change, the bankai cap and the shikai idle timer; casting a NEW shikai/bankai ability still needs
 * the matching sword in the main hand. Kido and shunpo need neither a state nor a sword.
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
	private final EnumMap<KidoId, Long> kidoCooldownEnd = new EnumMap<>(KidoId.class);
	private KidoConfig kidoCfg = KidoConfig.defaults();
	private long kidoGcdEnd;

	private ReiatsuState reiatsu;
	private CharacterId character = CharacterId.NONE;
	private ZanpakutoState state = ZanpakutoState.SEALED;
	private long stateSince;
	private ShikaiMode mode = ShikaiMode.IDLE;
	private long modeEnd;
	/** {@code barrierPool} value of a barrier without a cap. */
	public static final int POOL_UNLIMITED = -1;
	private int barrierPool;
	private long bankaiEnd;
	private long bankaiLockEnd;
	private long shunpoEnd;

	private long transitionLockEnd;
	private long gcdEnd;
	private long settleEnd;
	private long releaseLockEnd;
	private long lastCast;
	/** Rage seam (round 5): the cost config and the live value; null = Rage does not gate the release (unit tests, harness). */
	private RageConfig rageCfg;
	private IntSupplier rageSource;
	private boolean rageFree;

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

	// ------------------------------------------------------------------ rage seam

	/**
	 * Round 5: shikai and bankai entry need (and pay) Rage. {@code available} is read live at the request (the server's Rage meter);
	 * a null config switches the requirement off (the machine alone, as in unit tests). The payment itself is the
	 * {@link StateEvent.RageSpent} event the glue executes.
	 */
	public void setRage(RageConfig config, IntSupplier available) {
		this.rageCfg = config;
		this.rageSource = available;
	}

	/** Dev switch ({@code /reiatsu dev ragefree on}): no Rage is required or spent for shikai and bankai. Default off. */
	public void setRageFree(boolean free) {
		this.rageFree = free;
	}

	public boolean rageFree() {
		return rageFree;
	}

	/** Rage a request to {@code target} needs and pays in tenths (0 = none: not shikai/bankai, requirement off or ragefree). */
	public int rageNeed(ZanpakutoState target) {
		if (rageCfg == null || rageSource == null || rageFree) {
			return 0;
		}
		return switch (target) {
			case SHIKAI -> rageCfg.shikaiCostTenths();
			case BANKAI -> rageCfg.bankaiCostTenths();
			default -> 0;
		};
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
			// the one rejection with its own message: a release while the sword is still in the scabbard
			return reject(state == ZanpakutoState.SEALED && target == ZanpakutoState.SHIKAI
					? RejectReason.NOT_DRAWN : RejectReason.NOT_IN_STATE);
		}
		if (state == ZanpakutoState.SEALED) {
			if (r.held() == CharacterId.NONE) {
				return reject(RejectReason.WRONG_ITEM);
			}
		} else if (target != ZanpakutoState.SEALED && r.held() != character) {
			return reject(RejectReason.WRONG_ITEM); // sealing needs no sword: V works with the sword dropped or in the off hand
		}
		if (now < transitionLockEnd) {
			return reject(RejectReason.TRANSITION_LOCK);
		}
		if (state == ZanpakutoState.SEALED && now < releaseLockEnd) {
			return reject(RejectReason.RELEASE_LOCK);
		}
		if (target == ZanpakutoState.BANKAI && now < bankaiLockEnd) {
			return reject(RejectReason.RELEASE_LOCK); // bankai recovery after the last bankai (timer end, seal)
		}
		int rageNeed = rageNeed(target);
		if (rageNeed > 0 && rageSource.getAsInt() < rageNeed) {
			return reject(RejectReason.NOT_ENOUGH_RAGE);
		}
		if (target == ZanpakutoState.SHIKAI && !reiatsu.canSpend(cfg.shikaiReleaseCost())) {
			return reject(RejectReason.NOT_ENOUGH_REIATSU);
		}

		ZanpakutoState from = state;
		List<StateEvent> ev = new ArrayList<>();
		switch (target) {
			case BASE -> {
				// T0: draw. Free; the character is locked here, the release effect waits for the shikai request
				character = r.held();
				state = ZanpakutoState.BASE;
				stateSince = now;
				mode = ShikaiMode.IDLE;
				bankaiEnd = 0;
				lastCast = now;
				transitionLockEnd = now + cfg.transitionLockTicks();
				castSerial++;
				ev.add(new StateEvent.StateChanged(from, state, Trigger.REQUEST));
				ev.add(new StateEvent.MirrorComponent(state));
			}
			case SHIKAI -> {
				spend(cfg.shikaiReleaseCost(), ev);
				if (rageNeed > 0) {
					ev.add(new StateEvent.RageSpent(rageNeed));
				}
				state = ZanpakutoState.SHIKAI;
				stateSince = now;
				mode = ShikaiMode.IDLE;
				bankaiEnd = 0;
				lastCast = now;
				transitionLockEnd = now + cfg.transitionLockTicks();
				castSerial++;
				ev.add(new StateEvent.StateChanged(from, state, Trigger.REQUEST));
				ev.add(new StateEvent.MirrorComponent(state));
				ev.add(new StateEvent.BroadcastEffect(EffectIds.shikaiRelease(character), nextSeed(now, EffectIds.shikaiRelease(character))));
			}
			case BANKAI -> {
				if (cfg.bankaiCost() > 0) {
					spend(cfg.bankaiCost(), ev);
				}
				if (rageNeed > 0) {
					ev.add(new StateEvent.RageSpent(rageNeed));
				}
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
			case SEALED -> {
				// T3: seal from SHIKAI / BANKAI keeps the 2 s release lock; sheathing the base form is free of it
				int lock = from == ZanpakutoState.BASE ? cfg.sheatheLockTicks() : cfg.sealLockTicks();
				int effect = from == ZanpakutoState.BASE ? 0 : EffectIds.SEAL;
				revertToSealed(now, Trigger.REQUEST, effect, lock, true, ev);
			}
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
		if (spec == null || !spec.enabled() || state == ZanpakutoState.SEALED || a.character != character
				|| !(a.requiredState == state || (state == ZanpakutoState.BANKAI && a.requiredState == ZanpakutoState.SHIKAI))) {
			return reject(RejectReason.NOT_IN_STATE);
		}
		if (r.held() != character) {
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
		if (spec.costTenths() > 0) {
			spend(spec.costTenths(), ev); // bankai abilities are free (cooldown only), shikai ones cost as usual in bankai too
		}
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
			barrierPool = cfg.barrierPoolTenths() > 0 ? cfg.barrierPoolTenths() : POOL_UNLIMITED;
			ev.add(new StateEvent.ShikaiModeChanged(mode));
		}
		ev.add(new StateEvent.BroadcastEffect(spec.effectId(), nextSeed(now, spec.effectId())));
		List<Integer> phases = spec.phaseOffsets();
		for (int i = 0; i < phases.size(); i++) {
			ev.add(new StateEvent.ScheduledPhase(a, phases.get(i), i, castSerial));
		}
		return new TransitionResult(ResultCode.OK, null, state, state, ev);
	}

	/** The spell numbers of this machine (defaults unless the glue sets the user config). */
	public void setKidoConfig(KidoConfig config) {
		this.kidoCfg = config == null ? KidoConfig.defaults() : config;
	}

	public KidoConfig kidoConfig() {
		return kidoCfg;
	}

	/**
	 * Kido (B5): a spell any living player may cast in ANY state (sealed, drawn, released, with any sword or none), so it has
	 * no state, item or release lock check. What it does check: dead or spectator, the rate limit and the sequence, the
	 * master switch and the spell's own switch, the gap between two kido casts, the spell's cooldown and the reiatsu (the bar
	 * never goes to zero, rule R2). Accepted: reiatsu is charged, the cooldown and the gap start and a {@link StateEvent.KidoCast}
	 * is returned for the glue. A cast counts as activity for the shikai idle timer.
	 */
	public TransitionResult castKido(KidoRequest r) {
		RejectReason pre = preCheck(r.clientSeq());
		if (pre != null) {
			return reject(pre);
		}
		long now = clock.nowTick();
		KidoId k = r.kido();
		KidoSpec spec = k == null ? null : kidoCfg.spec(k);
		if (spec == null || !kidoCfg.enabled() || !spec.enabled()) {
			return reject(RejectReason.NOT_IN_STATE);
		}
		if (now < kidoGcdEnd) {
			return reject(RejectReason.GCD);
		}
		Long cd = kidoCooldownEnd.get(k);
		if (cd != null && now < cd) {
			return reject(RejectReason.ON_COOLDOWN);
		}
		if (!reiatsu.canSpend(spec.costTenths())) {
			return reject(RejectReason.NOT_ENOUGH_REIATSU);
		}
		List<StateEvent> ev = new ArrayList<>();
		if (spec.costTenths() > 0) {
			spend(spec.costTenths(), ev);
		}
		long end = now + spec.cooldownTicks();
		kidoCooldownEnd.put(k, end);
		kidoGcdEnd = now + kidoCfg.gcdTicks();
		lastCast = now;
		ev.add(new StateEvent.KidoCast(k, spec, end, nextSeed(now, spec.effectId())));
		return new TransitionResult(ResultCode.OK, null, state, state, ev);
	}

	public int kidoCooldownRemaining(KidoId id) {
		Long end = kidoCooldownEnd.get(id);
		return end == null ? 0 : (int) Math.max(0L, end - clock.nowTick());
	}

	/** Remaining ticks of every spell that is on cooldown, for the owner-only cooldown sync. */
	public Map<KidoId, Integer> kidoCooldownRemainingAll() {
		EnumMap<KidoId, Integer> out = new EnumMap<>(KidoId.class);
		long now = clock.nowTick();
		for (Map.Entry<KidoId, Long> e : kidoCooldownEnd.entrySet()) {
			if (e.getValue() > now) {
				out.put(e.getKey(), (int) (e.getValue() - now));
			}
		}
		return out;
	}

	/**
	 * Shunpo (B4 step 5, round 5): a short teleport along the look direction, in ANY state with any sword or none (like kido). The
	 * machine checks everything that is not geometry (alive, rate limit, transition lock, cooldown, reiatsu), then walks the path through {@code probe}. A path with
	 * no room refuses with {@link RejectReason#NO_ROOM} and costs nothing. The accepted result carries a
	 * {@link StateEvent.ShunpoMove} for the glue (move the player, broadcast the effect).
	 *
	 * @param dir unit look direction ({@link ShunpoPath#direction}); feet position and ground flag are the server's own
	 */
	public TransitionResult shunpo(int clientSeq, CharacterId held, ShunpoPath.Probe probe, double x, double y, double z,
			double[] dir, boolean onGround) {
		RejectReason pre = preCheck(clientSeq);
		if (pre != null) {
			return reject(pre);
		}
		long now = clock.nowTick();
		if (now < transitionLockEnd) {
			return reject(RejectReason.TRANSITION_LOCK);
		}
		if (now < shunpoEnd) {
			return reject(RejectReason.ON_COOLDOWN);
		}
		ShunpoSpec sp = cfg.shunpo();
		if (!reiatsu.canSpend(sp.costTenths())) {
			return reject(RejectReason.NOT_ENOUGH_REIATSU);
		}
		ShunpoPath.Result path = ShunpoPath.resolve(probe, x, y, z, dir[0], dir[1], dir[2], sp, onGround);
		if (path == null) {
			return reject(RejectReason.NO_ROOM);
		}
		List<StateEvent> ev = new ArrayList<>();
		if (sp.costTenths() > 0) {
			spend(sp.costTenths(), ev);
		}
		shunpoEnd = now + sp.cooldownTicks();
		lastCast = now; // moving counts as activity for the shikai idle timer
		ev.add(new StateEvent.ShunpoMove(x, y, z, path.x(), path.y(), path.z(), path.stoppedByWall()));
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

	/** Fixed order inside one tick: mode expiry, reiatsu batch, zero check, bankai cap, shikai idle. */
	private void step(long t, List<StateEvent> ev) {
		if (mode != ShikaiMode.IDLE && t >= modeEnd) {
			setMode(ShikaiMode.IDLE, ev);
		}
		if (t % cfg.regenBatchTicks() == 0) {
			reiatsu = ReiatsuMath.applyBatch(reiatsu, cfg.rate(state));
		}
		if (reiatsu.value() == 0) {
			// bankai no longer ends at zero: it has no drain and spending cannot reach zero, the timer alone ends it (B4 step 4)
			if (state == ZanpakutoState.SHIKAI) {
				revertToSealed(t, Trigger.REIATSU_ZERO, EffectIds.SEAL, cfg.sealLockTicks(), true, ev);
			}
		}
		if (state == ZanpakutoState.BANKAI && t >= bankaiEnd) {
			endBankai(t, Trigger.BANKAI_CAP, ev);
		}
		if (state == ZanpakutoState.SHIKAI && t - lastCast >= cfg.shikaiIdleTicks()) {
			revertToSealed(t, Trigger.SHIKAI_IDLE, EffectIds.SEAL, cfg.sealLockTicks(), true, ev);
		}
	}

	// ------------------------------------------------------------------ Minecraft events

	/**
	 * The character of the zanpakuto in the main hand changed (NONE = none). Round 5: deliberately a no-op. Changing the slot,
	 * dropping the sword or moving it to the off hand does NOT end the released form or its running effects; it only blocks
	 * casting new shikai/bankai abilities (the request carries the held character). Kept so the glue and old tests still call it.
	 */
	public List<StateEvent> onHandChanged(CharacterId held) {
		return List.of();
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
		if (mode != ShikaiMode.BARRIER || barrierPool == 0 || damageTenths <= 0) {
			return damageTenths;
		}
		int wanted = damageTenths * cfg.barrierReductionPercent() / 100;
		if (barrierPool == POOL_UNLIMITED) {
			return damageTenths - wanted; // the default: no cap, the barrier lasts its whole time
		}
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
		CharacterId was = character;
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
		transitionLockEnd = 0;
		gcdEnd = 0;
		settleEnd = 0;
		releaseLockEnd = 0;
		ev.add(new StateEvent.StateChanged(from, state, Trigger.DEV_COMMAND));
		if (from == ZanpakutoState.BANKAI) {
			ev.add(new StateEvent.BankaiEnded(was, Trigger.DEV_COMMAND));
		}
		ev.add(new StateEvent.MirrorComponent(state));
		return ev;
	}

	public void devClearCooldowns() {
		cooldownEnd.clear();
		kidoCooldownEnd.clear();
		kidoGcdEnd = 0;
		gcdEnd = 0;
		shunpoEnd = 0;
		bankaiLockEnd = 0;
		if (state == ZanpakutoState.BANKAI) {
			// dev harness: a long scripted run must not fall out of bankai through the 45 s cap
			bankaiEnd = clock.nowTick() + cfg.bankaiCapTicks();
		}
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

	/** Remaining absorption in tenths of a HP: {@link #POOL_UNLIMITED} (-1) when the config has no cap, 0 without a barrier. */
	public int barrierPoolTenths() {
		return barrierPool;
	}

	/** True while the Byakuya barrier stands: damage is cut and control effects (slow, freeze, knockback ...) do not land. */
	public boolean barrierActive() {
		return mode == ShikaiMode.BARRIER && barrierPool != 0;
	}

	/** Control immunity of the standing barrier (config knob {@code barrierControlImmune}). */
	public boolean barrierControlImmune() {
		return barrierActive() && cfg.barrierControlImmune();
	}

	public boolean isDead() {
		return dead;
	}

	/** Increases whenever running effects must be dropped (seal, auto-revert, reset). */
	public long castSerial() {
		return castSerial;
	}

	public long releaseLockEndTick() {
		return releaseLockEnd;
	}

	/** Tick until which bankai cannot be entered again (0 = no lock). */
	public long bankaiLockEndTick() {
		return bankaiLockEnd;
	}

	/** Remaining ticks of the shunpo cooldown. */
	public int shunpoCooldownRemaining() {
		return (int) Math.max(0L, shunpoEnd - clock.nowTick());
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
			case SEALED -> to == ZanpakutoState.BASE;
			case BASE -> to == ZanpakutoState.SHIKAI || to == ZanpakutoState.SEALED;
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

	/**
	 * T4 / T5 (B4 step 4): the bankai timer ran out. Bankai falls back to SHIKAI (not SEALED): the zanpakuto stays released,
	 * the player keeps the sword, the shikai idle timer restarts and bankai is locked for {@code bankaiReentryTicks}. Abilities
	 * already cast keep running (the cast serial is not bumped); temporary blocks roll back on their own timers.
	 */
	private void endBankai(long t, Trigger trigger, List<StateEvent> ev) {
		ZanpakutoState from = state;
		state = ZanpakutoState.SHIKAI;
		stateSince = t;
		bankaiEnd = 0;
		settleEnd = 0;
		lastCast = t;
		bankaiLockEnd = t + cfg.bankaiReentryTicks();
		transitionLockEnd = t + cfg.transitionLockTicks();
		ev.add(new StateEvent.StateChanged(from, state, trigger));
		ev.add(new StateEvent.BankaiEnded(character, trigger));
		ev.add(new StateEvent.MirrorComponent(state));
		ev.add(new StateEvent.BroadcastEffect(EffectIds.BANKAI_END, nextSeed(t, EffectIds.BANKAI_END)));
	}

	/** T3..T8: any released state back to SEALED. Ability cooldowns are kept. */
	private void revertToSealed(long t, Trigger trigger, int effectId, int releaseLock, boolean transitionLock,
			List<StateEvent> ev) {
		ZanpakutoState from = state;
		CharacterId was = character;
		if (from == ZanpakutoState.BANKAI) {
			bankaiLockEnd = t + cfg.bankaiReentryTicks();
		}
		state = ZanpakutoState.SEALED;
		character = CharacterId.NONE;
		mode = ShikaiMode.IDLE;
		barrierPool = 0;
		bankaiEnd = 0;
		stateSince = t;
		settleEnd = 0;
		castSerial++;
		releaseLockEnd = t + releaseLock;
		transitionLockEnd = transitionLock ? t + cfg.transitionLockTicks() : 0;
		ev.add(new StateEvent.CancelEffects());
		ev.add(new StateEvent.RollbackTempBlocks());
		ev.add(new StateEvent.StateChanged(from, state, trigger));
		if (from == ZanpakutoState.BANKAI) {
			ev.add(new StateEvent.BankaiEnded(was, trigger));
		}
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
		CharacterId was = character;
		if (clearCooldowns) {
			bankaiLockEnd = 0;
			shunpoEnd = 0;
		} else if (from == ZanpakutoState.BANKAI) {
			bankaiLockEnd = now + cfg.bankaiReentryTicks();
		}
		state = ZanpakutoState.SEALED;
		character = CharacterId.NONE;
		mode = ShikaiMode.IDLE;
		barrierPool = 0;
		bankaiEnd = 0;
		stateSince = now;
		transitionLockEnd = 0;
		gcdEnd = 0;
		settleEnd = 0;
		releaseLockEnd = 0;
		castSerial++;
		if (clearCooldowns) {
			cooldownEnd.clear();
			kidoCooldownEnd.clear();
			kidoGcdEnd = 0;
		}
		ev.add(new StateEvent.CancelEffects());
		ev.add(new StateEvent.RollbackTempBlocks());
		if (from != ZanpakutoState.SEALED) {
			ev.add(new StateEvent.StateChanged(from, state, trigger));
			if (from == ZanpakutoState.BANKAI) {
				ev.add(new StateEvent.BankaiEnded(was, trigger));
			}
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
