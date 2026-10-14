package dev.minebleach.reiatsutest.core.rage;

/**
 * R11 review fix 2: which Rage reward a landed player hit pays. Pure decision, the glue ({@code server/RageManager}) reads the damage source and the
 * "no reward" mark that {@code Targeting.hurtUnrewarded} sets around the damage call.
 * <ul>
 * <li>{@link #NONE}: the hit is marked as unrewarded (the pulses of the Rukia frost field: damage over time that only exists because the field stands,
 * it must not pay the one-per-cast ability reward nor the "dealt" share);</li>
 * <li>{@link #ABILITY}: an ability or kido hit (damage types FREEZE / INDIRECT_MAGIC with the player as attacker): the fixed reward of the cast;</li>
 * <li>{@link #DEALT}: any other hit (sword, projectile): the share of the damage.</li>
 * </ul>
 */
public enum RageReward {
	NONE, ABILITY, DEALT;

	public static RageReward classify(boolean unrewarded, boolean abilityDamageType) {
		if (unrewarded) {
			return NONE;
		}
		return abilityDamageType ? ABILITY : DEALT;
	}
}
