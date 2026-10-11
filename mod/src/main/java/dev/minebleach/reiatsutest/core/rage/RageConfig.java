package dev.minebleach.reiatsutest.core.rage;

/**
 * Tunable numbers of the Rage resource (PLAN_2026-10-11 B4). Pure data, no Minecraft types. All amounts are tenths of a rage
 * point, like the reiatsu bar (1000 = 100.0 points = a full bar).
 *
 * @param maxTenths             size of the bar
 * @param damageTenthsPerHp     rage gained per health point of damage taken (5 = 0.5 point per hp, i.e. 50 percent of the damage)
 * @param maxGainPerHitTenths   cap of the gain of one hit, so a huge hit cannot fill the bar alone
 * @param parryGainTenths       fixed gain of one successful parry
 * @param decayDelayTicks       ticks without damage taken or a parry before the bar starts to drain ("out of combat")
 * @param decayIntervalTicks    ticks between two drain steps once draining
 * @param decayTenths           amount lost per drain step
 */
public record RageConfig(int maxTenths, int damageTenthsPerHp, int maxGainPerHitTenths, int parryGainTenths,
		int decayDelayTicks, int decayIntervalTicks, int decayTenths) {
	public RageConfig {
		if (maxTenths <= 0) {
			throw new IllegalArgumentException("maxTenths <= 0: " + maxTenths);
		}
		if (damageTenthsPerHp < 0 || maxGainPerHitTenths < 0 || parryGainTenths < 0 || decayDelayTicks < 0 || decayTenths < 0) {
			throw new IllegalArgumentException("negative rage value");
		}
		if (decayIntervalTicks <= 0) {
			throw new IllegalArgumentException("decayIntervalTicks <= 0: " + decayIntervalTicks);
		}
	}

	/**
	 * Defaults (no number in the plan, so these are ours; UNVERIFIED by play): bar 100.0, 0.5 point per hp taken capped at 10
	 * per hit (a 20 hp hit gives the cap), 15 per parry, drain of 1.0 per second after 5 s out of combat. About 7 parries or
	 * 10 capped hits fill the bar; a full bar drains in 100 s.
	 */
	public static RageConfig defaults() {
		return new RageConfig(1000, 5, 100, 150, 100, 20, 10);
	}
}
