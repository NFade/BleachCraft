package dev.minebleach.reiatsutest.core.rage;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * The Rage of one player: gain from damage dealt (R11: damage taken gives nothing by default), successful parries and ability casts,
 * drain out of combat, spending. Mutable, single threaded (server thread), clock-free: every call gets the current server tick. No
 * Minecraft types.
 *
 * <p>{@link #canSpend} and {@link #spend} pay the shikai and bankai release (round 5), see {@code RageConfig.shikaiCostTenths}.
 *
 * <p>R11 T3 anti-farm rules: a hit on a mob gives {@code mobMultiplierPercent} of the gain, the gain from one target is capped per 10 s
 * ({@code targetCapPer10sTenths}), a parry pays at most once per {@code parryGapTicks}, and an ability pays ONE fixed gain per cast
 * ({@link #armAbility} at the cast, {@link #onAbilityHit} at the first landed hit), however many targets it hits.
 */
public final class RageMeter {
	/** A cast whose hit does not land within this many ticks pays nothing (30 s). */
	public static final int ABILITY_ARM_TICKS = 600;
	/** At most this many casts wait for their reward at once. */
	private static final int MAX_ARMED = 3;

	private RageConfig cfg;
	private RageState state;
	/** Tick of the last damage taken or parry; the drain starts {@code decayDelayTicks} after it. */
	private int lastCombatTick;
	/** Tick of the next drain step, valid while the bar is above zero. */
	private int nextDecayTick;
	/** The passive inflow runs for ticks before this one (set by every hit/parry, not by the dev command). */
	private int passiveUntilTick;
	/** Tick of the next passive step while the window is open. */
	private int nextPassiveTick;
	/** Tick of the last parry that paid; {@code MIN_VALUE / 2} = none yet. */
	private int lastParryPaidTick = Integer.MIN_VALUE / 2;
	/** Per target entity id: {tick, tenths} of the dealt-hit gains inside the last 10 s. */
	private final Map<Integer, ArrayDeque<int[]>> targetGains = new HashMap<>();
	/** Ticks at which casts armed an ability reward (oldest first); entries older than {@link #ABILITY_ARM_TICKS} are dropped. */
	private final ArrayDeque<Integer> armedCasts = new ArrayDeque<>();

	public RageMeter(RageConfig cfg) {
		this.cfg = cfg;
		this.state = RageState.empty(cfg.maxTenths());
	}

	public RageConfig config() {
		return cfg;
	}

	/** Live config reload (R11 T0): the bar keeps its value; the size of the bar is not reloadable. */
	public void setConfig(RageConfig cfg) {
		if (cfg.maxTenths() == this.cfg.maxTenths()) {
			this.cfg = cfg;
		}
	}

	public RageState state() {
		return state;
	}

	public int value() {
		return state.value();
	}

	/**
	 * Damage taken ({@code hp} health points, after armor and block). Gain = hp * damageTenthsPerHp, rounded, capped per hit (R11: the
	 * rate is 0, so nothing is gained, but the hit still counts as combat). Returns the tenths actually added (0 for no damage, NaN or negative input).
	 */
	public int onDamageTaken(float hp, int tick) {
		if (!(hp > 0.0F)) {
			return 0;
		}
		long raw = Math.round((double) hp * cfg.damageTenthsPerHp());
		int gain = (int) Math.min(raw, cfg.maxGainPerHitTenths());
		return gain(gain, tick);
	}

	/**
	 * Damage DEALT by this player with no target information (pre-R11 form, kept for the old tests and the dev paths): gain = hp *
	 * dealtTenthsPerHp, rounded, capped per hit, no mob share and no per-target cap.
	 */
	public int onDamageDealt(float hp, int tick) {
		if (!(hp > 0.0F)) {
			return 0;
		}
		long raw = Math.round((double) hp * cfg.dealtTenthsPerHp());
		int gain = (int) Math.min(raw, cfg.maxDealtGainPerHitTenths());
		return gain(gain, tick);
	}

	/**
	 * R11 T3: damage DEALT to a known target by a sword hit or a projectile. Gain = hp * dealtTenthsPerHp, capped per hit, then scaled by
	 * {@code mobMultiplierPercent} when the victim is not a player, then cut to what is left of the 10 s cap of THIS target. Returns the tenths added.
	 */
	public int onDamageDealt(float hp, int targetId, boolean targetIsMob, int tick) {
		if (!(hp > 0.0F)) {
			return 0;
		}
		long raw = Math.round((double) hp * cfg.dealtTenthsPerHp());
		long gain = Math.min(raw, cfg.maxDealtGainPerHitTenths());
		if (targetIsMob) {
			gain = Math.round(gain * (cfg.mobMultiplierPercent() / 100.0));
		}
		gain = Math.min(gain, targetCapLeft(targetId, tick));
		int give = (int) Math.max(0L, Math.min(gain, Integer.MAX_VALUE));
		if (give > 0) {
			targetGains.computeIfAbsent(targetId, k -> new ArrayDeque<>()).addLast(new int[] {tick, give});
		}
		return gain(give, tick);
	}

	/** Tenths this target may still pay inside the window ending at {@code tick}. */
	private long targetCapLeft(int targetId, int tick) {
		ArrayDeque<int[]> q = targetGains.get(targetId);
		long used = 0;
		if (q != null) {
			while (!q.isEmpty() && (tick - q.peekFirst()[0] >= RageConfig.TARGET_WINDOW_TICKS || tick < q.peekFirst()[0])) {
				q.pollFirst();
			}
			for (int[] e : q) {
				used += e[1];
			}
			if (q.isEmpty()) {
				targetGains.remove(targetId);
			}
		}
		if (targetGains.size() > 64) {
			final int now = tick;
			targetGains.values().removeIf(d -> d.isEmpty() || now - d.peekLast()[0] >= RageConfig.TARGET_WINDOW_TICKS);
		}
		return Math.max(0L, (long) cfg.targetCapPer10sTenths() - used);
	}

	/** An accepted ability or kido cast waits for its first landed hit to pay {@code abilityHitTenths}. */
	public void armAbility(int tick) {
		if (cfg.abilityHitTenths() <= 0) {
			return;
		}
		armedCasts.addLast(tick);
		while (armedCasts.size() > MAX_ARMED) {
			armedCasts.pollFirst();
		}
	}

	/**
	 * A landed ability/kido hit. Pays {@code abilityHitTenths} once for the oldest cast still waiting (further targets of the same cast
	 * pay nothing), scaled by the mob share when the victim is not a player. Returns the tenths added (0 when no cast waits).
	 */
	public int onAbilityHit(boolean targetIsMob, int tick) {
		while (!armedCasts.isEmpty() && (tick - armedCasts.peekFirst() > ABILITY_ARM_TICKS || tick < armedCasts.peekFirst())) {
			armedCasts.pollFirst();
		}
		if (armedCasts.isEmpty()) {
			return gain(0, tick); // a hit is still combat
		}
		armedCasts.pollFirst();
		long gain = cfg.abilityHitTenths();
		if (targetIsMob) {
			gain = Math.round(gain * (cfg.mobMultiplierPercent() / 100.0));
		}
		return gain((int) Math.min(gain, Integer.MAX_VALUE), tick);
	}

	/** Test/dev command: sets the bar directly (clamped); the drain waits, but no passive inflow starts (the value stays put). */
	public void devSet(int tenths, int tick) {
		state = new RageState(tenths, cfg.maxTenths());
		lastCombatTick = tick;
		nextDecayTick = tick + cfg.decayDelayTicks();
		passiveUntilTick = 0;
	}

	/** A successful parry: the fixed gain, at most once per {@code parryGapTicks}. Returns the tenths actually added. */
	public int onParry(int tick) {
		if (tick >= lastParryPaidTick && tick - lastParryPaidTick < cfg.parryGapTicks()) {
			return gain(0, tick); // too soon after the last paying parry: combat, but no gain
		}
		lastParryPaidTick = tick;
		return gain(cfg.parryGainTenths(), tick);
	}

	private int gain(int tenths, int tick) {
		lastCombatTick = tick; // a hit counts as combat even when the bar is already full
		nextDecayTick = tick + cfg.decayDelayTicks();
		if (tick >= passiveUntilTick) {
			nextPassiveTick = tick + cfg.passiveIntervalTicks(); // a new fight: the first passive step comes one interval later
		}
		passiveUntilTick = tick + cfg.passiveWindowTicks();
		if (tenths <= 0) {
			return 0;
		}
		int before = state.value();
		state = state.plus(tenths);
		return state.value() - before;
	}

	/**
	 * Once per server tick (or with any larger step, it catches up): the passive inflow while the combat window is open, then the
	 * out-of-combat drain. Returns true if the value changed.
	 */
	public boolean tick(int now) {
		int before = state.value();
		if (cfg.passiveTenths() > 0) {
			while (nextPassiveTick <= now && nextPassiveTick < passiveUntilTick) {
				state = state.plus(cfg.passiveTenths());
				nextPassiveTick += cfg.passiveIntervalTicks();
			}
		}
		if (state.value() != 0 && cfg.decayTenths() != 0 && now >= nextDecayTick) {
			long steps = 1L + ((long) now - nextDecayTick) / cfg.decayIntervalTicks();
			long lost = steps * cfg.decayTenths();
			state = state.plus((int) -Math.min(lost, Integer.MAX_VALUE));
			nextDecayTick += (int) (steps * cfg.decayIntervalTicks());
		}
		return state.value() != before;
	}

	public boolean canSpend(int cost) {
		return state.canSpend(cost);
	}

	/** Pays {@code cost} tenths if the bar holds that much; returns false and changes nothing otherwise. */
	public boolean spend(int cost) {
		if (!state.canSpend(cost)) {
			return false;
		}
		if (cost > 0) {
			state = state.plus(-cost);
		}
		return true;
	}

	/** Death, respawn, logout: back to empty. */
	public void reset() {
		state = RageState.empty(cfg.maxTenths());
		lastCombatTick = 0;
		nextDecayTick = 0;
		passiveUntilTick = 0;
		nextPassiveTick = 0;
		lastParryPaidTick = Integer.MIN_VALUE / 2;
		targetGains.clear();
		armedCasts.clear();
	}

	public int lastCombatTick() {
		return lastCombatTick;
	}
}
