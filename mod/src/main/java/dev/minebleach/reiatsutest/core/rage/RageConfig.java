package dev.minebleach.reiatsutest.core.rage;

import com.google.gson.JsonObject;
import dev.minebleach.reiatsutest.core.config.Cfg;

/**
 * Tunable numbers of the Rage resource (PLAN_2026-10-11 B4, reworked in R11 T3). Pure data, no Minecraft types. All amounts are tenths of a
 * rage point, like the reiatsu bar (1000 = 100.0 points = a full bar).
 *
 * @param maxTenths             size of the bar
 * @param damageTenthsPerHp     rage gained per health point of damage taken (R11: 0, the damage you take gives nothing)
 * @param maxGainPerHitTenths   cap of the gain of one hit TAKEN
 * @param parryGainTenths       fixed gain of one successful parry
 * @param dealtTenthsPerHp      rage gained per health point of damage DEALT by sword hits and projectiles (2 = 0.2 point per hp)
 * @param maxDealtGainPerHitTenths cap of the gain of one dealt hit (25 = 2.5 points)
 * @param decayDelayTicks       ticks without damage taken/dealt or a parry before the bar starts to drain ("out of combat")
 * @param decayIntervalTicks    ticks between two drain steps once draining
 * @param decayTenths           amount lost per drain step
 * @param passiveTenths         passive inflow per step while "in combat" (R11: 0 = off)
 * @param passiveIntervalTicks  ticks between two passive steps
 * @param passiveWindowTicks    how long after the last combat event the passive inflow lasts
 * @param shikaiCostTenths      Rage needed AND spent to enter shikai (rage.shikaiCost, 400 = 40.0 of 100)
 * @param bankaiCostTenths      Rage needed AND spent to enter bankai (rage.bankaiCost, 800 = 80.0 of 100)
 * @param parryGapTicks         R11 T3: a parry pays at most once per this many ticks
 * @param abilityHitTenths      R11 T3: fixed gain of the first landed ability/kido hit of ONE cast (not per target)
 * @param mobMultiplierPercent  R11 T3: share of the dealt-hit gain when the victim is not a player (25 = a quarter)
 * @param targetCapPer10sTenths R11 T3: dealt-hit gain from one and the same target within any 10 s window is capped at this (80 = 8 points)
 * @param bankaiMinShikaiTicks  R11 T3: ticks the player must have spent in shikai before bankai can be requested (400 = 20 s)
 */
public record RageConfig(int maxTenths, int damageTenthsPerHp, int maxGainPerHitTenths, int parryGainTenths,
		int dealtTenthsPerHp, int maxDealtGainPerHitTenths, int decayDelayTicks, int decayIntervalTicks, int decayTenths,
		int passiveTenths, int passiveIntervalTicks, int passiveWindowTicks, int shikaiCostTenths, int bankaiCostTenths,
		int parryGapTicks, int abilityHitTenths, int mobMultiplierPercent, int targetCapPer10sTenths, int bankaiMinShikaiTicks) {
	public static final String SECTION = "rage";
	/** The target cap window: 10 s. */
	public static final int TARGET_WINDOW_TICKS = 200;
	/** Size of the bar; not configurable (the HUD and the attachment assume 100.0). */
	public static final int MAX_TENTHS = 1000;

	/**
	 * The pre-R11 fourteen numbers; the R11 ones take the neutral values (no parry gap, no ability reward, mobs count fully, no target cap,
	 * no minimum shikai time), so older tests keep their meaning.
	 */
	public RageConfig(int maxTenths, int damageTenthsPerHp, int maxGainPerHitTenths, int parryGainTenths,
			int dealtTenthsPerHp, int maxDealtGainPerHitTenths, int decayDelayTicks, int decayIntervalTicks, int decayTenths,
			int passiveTenths, int passiveIntervalTicks, int passiveWindowTicks, int shikaiCostTenths, int bankaiCostTenths) {
		this(maxTenths, damageTenthsPerHp, maxGainPerHitTenths, parryGainTenths, dealtTenthsPerHp, maxDealtGainPerHitTenths, decayDelayTicks,
				decayIntervalTicks, decayTenths, passiveTenths, passiveIntervalTicks, passiveWindowTicks, shikaiCostTenths, bankaiCostTenths,
				0, 0, 100, 100_000, 0);
	}

	public RageConfig {
		if (maxTenths <= 0) {
			throw new IllegalArgumentException("maxTenths <= 0: " + maxTenths);
		}
		if (damageTenthsPerHp < 0 || maxGainPerHitTenths < 0 || parryGainTenths < 0 || dealtTenthsPerHp < 0 || maxDealtGainPerHitTenths < 0
				|| decayDelayTicks < 0 || decayTenths < 0) {
			throw new IllegalArgumentException("negative rage value");
		}
		if (decayIntervalTicks <= 0) {
			throw new IllegalArgumentException("decayIntervalTicks <= 0: " + decayIntervalTicks);
		}
		if (passiveIntervalTicks <= 0) {
			throw new IllegalArgumentException("passiveIntervalTicks <= 0: " + passiveIntervalTicks);
		}
		if (passiveTenths < 0 || passiveWindowTicks < 0 || shikaiCostTenths < 0 || bankaiCostTenths < 0) {
			throw new IllegalArgumentException("negative rage value");
		}
		if (shikaiCostTenths > maxTenths || bankaiCostTenths > maxTenths) {
			throw new IllegalArgumentException("a release cost is above the bar size");
		}
		if (parryGapTicks < 0 || abilityHitTenths < 0 || mobMultiplierPercent < 0 || targetCapPer10sTenths < 0 || bankaiMinShikaiTicks < 0) {
			throw new IllegalArgumentException("negative rage value");
		}
	}

	/**
	 * Defaults (R11 T3, UNVERIFIED by play): bar 100.0; damage TAKEN gives nothing; damage DEALT gives 0.2 point per hp capped at 2.5 per
	 * hit (mobs count 25 percent, at most 8.0 per target per 10 s); a parry gives 4.0, at most once per second; the first landed hit of
	 * an ability or kido cast gives 2.0 (per cast, not per target); no passive inflow; drain of 0.5 per second after 8 s out of combat.
	 * Shikai costs 40, bankai 80 (the bar is paid, not only checked), and bankai needs 20 s spent in shikai.
	 */
	public static RageConfig defaults() {
		return new RageConfig(MAX_TENTHS, 0, 250, 40, 2, 25, 160, 20, 5, 0, 10, 120, 400, 800, 20, 20, 25, 80, 400);
	}

	/** The cost of entering a state in tenths: shikai and bankai are priced, everything else is free. */
	public int releaseCostTenths(boolean bankai) {
		return bankai ? bankaiCostTenths : shikaiCostTenths;
	}

	/** The {@code "rage"} section of the root config (missing keys = defaults, bad values = defaults, numbers clamped, never throws). */
	public static RageConfig fromRoot(JsonObject root) {
		RageConfig d = defaults();
		JsonObject o = Cfg.obj(root, SECTION);
		int max = d.maxTenths;
		return new RageConfig(max,
				Cfg.integer(o, "damageTenthsPerHp", d.damageTenthsPerHp, 0, 1000),
				Cfg.integer(o, "maxGainPerHitTenths", d.maxGainPerHitTenths, 0, max),
				Cfg.integer(o, "parryGainTenths", d.parryGainTenths, 0, max),
				Cfg.integer(o, "dealtTenthsPerHp", d.dealtTenthsPerHp, 0, 1000),
				Cfg.integer(o, "maxDealtGainPerHitTenths", d.maxDealtGainPerHitTenths, 0, max),
				Cfg.integer(o, "decayDelayTicks", d.decayDelayTicks, 0, 72_000),
				Cfg.integer(o, "decayIntervalTicks", d.decayIntervalTicks, 1, 72_000),
				Cfg.integer(o, "decayTenths", d.decayTenths, 0, max),
				Cfg.integer(o, "passiveTenths", d.passiveTenths, 0, max),
				Cfg.integer(o, "passiveIntervalTicks", d.passiveIntervalTicks, 1, 72_000),
				Cfg.integer(o, "passiveWindowTicks", d.passiveWindowTicks, 0, 72_000),
				Cfg.integer(o, "shikaiCostTenths", d.shikaiCostTenths, 0, max),
				Cfg.integer(o, "bankaiCostTenths", d.bankaiCostTenths, 0, max),
				Cfg.integer(o, "parryGapTicks", d.parryGapTicks, 0, 1200),
				Cfg.integer(o, "abilityHitTenths", d.abilityHitTenths, 0, max),
				Cfg.integer(o, "mobMultiplierPercent", d.mobMultiplierPercent, 0, 1000),
				Cfg.integer(o, "targetCapPer10sTenths", d.targetCapPer10sTenths, 0, 10_000),
				Cfg.integer(o, "bankaiMinShikaiTicks", d.bankaiMinShikaiTicks, 0, 72_000));
	}

	/** The {@code "rage"} object, for writing the numbers in force into a fresh file. */
	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("damageTenthsPerHp", damageTenthsPerHp);
		o.addProperty("maxGainPerHitTenths", maxGainPerHitTenths);
		o.addProperty("dealtTenthsPerHp", dealtTenthsPerHp);
		o.addProperty("maxDealtGainPerHitTenths", maxDealtGainPerHitTenths);
		o.addProperty("mobMultiplierPercent", mobMultiplierPercent);
		o.addProperty("targetCapPer10sTenths", targetCapPer10sTenths);
		o.addProperty("parryGainTenths", parryGainTenths);
		o.addProperty("parryGapTicks", parryGapTicks);
		o.addProperty("abilityHitTenths", abilityHitTenths);
		o.addProperty("passiveTenths", passiveTenths);
		o.addProperty("passiveIntervalTicks", passiveIntervalTicks);
		o.addProperty("passiveWindowTicks", passiveWindowTicks);
		o.addProperty("decayDelayTicks", decayDelayTicks);
		o.addProperty("decayIntervalTicks", decayIntervalTicks);
		o.addProperty("decayTenths", decayTenths);
		o.addProperty("shikaiCostTenths", shikaiCostTenths);
		o.addProperty("bankaiCostTenths", bankaiCostTenths);
		o.addProperty("bankaiMinShikaiTicks", bankaiMinShikaiTicks);
		return o;
	}
}
