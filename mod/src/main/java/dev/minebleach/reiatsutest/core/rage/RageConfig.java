package dev.minebleach.reiatsutest.core.rage;

/**
 * Tunable numbers of the Rage resource (PLAN_2026-10-11 B4). Pure data, no Minecraft types. All amounts are tenths of a rage
 * point, like the reiatsu bar (1000 = 100.0 points = a full bar).
 *
 * @param maxTenths             size of the bar
 * @param damageTenthsPerHp     rage gained per health point of damage taken (5 = 0.5 point per hp, i.e. 50 percent of the damage)
 * @param maxGainPerHitTenths   cap of the gain of one hit, so a huge hit cannot fill the bar alone
 * @param parryGainTenths       fixed gain of one successful parry
 * @param dealtTenthsPerHp      rage gained per health point of damage DEALT (4 = 0.4 point per hp), sword hits and abilities alike
 * @param maxDealtGainPerHitTenths cap of the gain of one dealt hit (80 = 8 points)
 * @param decayDelayTicks       ticks without damage taken/dealt or a parry before the bar starts to drain ("out of combat")
 * @param decayIntervalTicks    ticks between two drain steps once draining
 * @param decayTenths           amount lost per drain step
 * @param passiveTenths         passive inflow per step while "in combat" (a hit dealt, a hit taken or a parry happened within {@code passiveWindowTicks})
 * @param passiveIntervalTicks  ticks between two passive steps
 * @param passiveWindowTicks    how long after the last combat event the passive inflow lasts
 * @param shikaiCostTenths      Rage needed AND spent to enter shikai (rage.shikaiCost, 400 = 40.0 of 100)
 * @param bankaiCostTenths      Rage needed AND spent to enter bankai (rage.bankaiCost, 800 = 80.0 of 100)
 */
public record RageConfig(int maxTenths, int damageTenthsPerHp, int maxGainPerHitTenths, int parryGainTenths,
		int dealtTenthsPerHp, int maxDealtGainPerHitTenths, int decayDelayTicks, int decayIntervalTicks, int decayTenths,
		int passiveTenths, int passiveIntervalTicks, int passiveWindowTicks, int shikaiCostTenths, int bankaiCostTenths) {
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
	}

	/**
	 * Defaults (round 5, 2026-10-12; the rate is about 2.5 times the round 3 one, UNVERIFIED by play): bar 100.0; damage TAKEN gives
	 * 1.5 point per hp capped at 25 per hit; damage DEALT gives 1.0 point per hp capped at 15 per hit; a parry gives 30; passive
	 * inflow +1.0 per second while a hit was dealt/taken/parried within the last 6 s; drain of 0.5 per second after 8 s out of
	 * combat. Shikai costs 40, bankai 80 (the bar is paid, not only checked).
	 */
	public static RageConfig defaults() {
		return new RageConfig(1000, 15, 250, 300, 10, 150, 160, 20, 5, 5, 10, 120, 400, 800);
	}

	/** The cost of entering a state in tenths: shikai and bankai are priced, everything else is free. */
	public int releaseCostTenths(boolean bankai) {
		return bankai ? bankaiCostTenths : shikaiCostTenths;
	}
}
