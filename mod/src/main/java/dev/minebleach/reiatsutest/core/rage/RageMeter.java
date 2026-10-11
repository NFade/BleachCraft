package dev.minebleach.reiatsutest.core.rage;

/**
 * The Rage of one player: gain from damage taken and from successful parries, drain out of combat, spending. Mutable, single
 * threaded (server thread), clock-free: every call gets the current server tick. No Minecraft types.
 *
 * <p>Extension point for abilities (B4 follow-up): {@link #canSpend} and {@link #spend}. No ability uses them yet.
 */
public final class RageMeter {
	private final RageConfig cfg;
	private RageState state;
	/** Tick of the last damage taken or parry; the drain starts {@code decayDelayTicks} after it. */
	private int lastCombatTick;
	/** Tick of the next drain step, valid while the bar is above zero. */
	private int nextDecayTick;

	public RageMeter(RageConfig cfg) {
		this.cfg = cfg;
		this.state = RageState.empty(cfg.maxTenths());
	}

	public RageConfig config() {
		return cfg;
	}

	public RageState state() {
		return state;
	}

	public int value() {
		return state.value();
	}

	/**
	 * Damage taken ({@code hp} health points, after armor and block). Gain = hp * damageTenthsPerHp, rounded, capped per hit.
	 * Returns the tenths actually added (0 for no damage, NaN or negative input).
	 */
	public int onDamageTaken(float hp, int tick) {
		if (!(hp > 0.0F)) {
			return 0;
		}
		long raw = Math.round((double) hp * cfg.damageTenthsPerHp());
		int gain = (int) Math.min(raw, cfg.maxGainPerHitTenths());
		return gain(gain, tick);
	}

	/** A successful parry: the fixed gain. Returns the tenths actually added. */
	public int onParry(int tick) {
		return gain(cfg.parryGainTenths(), tick);
	}

	private int gain(int tenths, int tick) {
		lastCombatTick = tick; // a hit counts as combat even when the bar is already full
		nextDecayTick = tick + cfg.decayDelayTicks();
		if (tenths <= 0) {
			return 0;
		}
		int before = state.value();
		state = state.plus(tenths);
		return state.value() - before;
	}

	/**
	 * Out-of-combat drain, call once per server tick (or with any larger step, it catches up). Returns true if the value changed.
	 */
	public boolean tick(int now) {
		if (state.value() == 0 || cfg.decayTenths() == 0 || now < nextDecayTick) {
			return false;
		}
		int before = state.value();
		long steps = 1L + ((long) now - nextDecayTick) / cfg.decayIntervalTicks();
		long lost = steps * cfg.decayTenths();
		state = state.plus((int) -Math.min(lost, Integer.MAX_VALUE));
		nextDecayTick += (int) (steps * cfg.decayIntervalTicks());
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
	}

	public int lastCombatTick() {
		return lastCombatTick;
	}
}
