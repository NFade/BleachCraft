package dev.minebleach.reiatsutest.core.rage;

/**
 * R11 review fix 2: which Rage reward a landed player hit pays. Pure decision, the glue ({@code server/RageManager}) reads the damage source and the
 * "no reward" mark that {@code Targeting.hurtUnrewarded} sets around the damage call.
 * <ul>
 * <li>{@link #NONE}: the hit is marked as unrewarded (the pulses of the Rukia frost field: damage over time that only exists because the field stands,
 * it must not pay the one-per-cast ability reward nor the "dealt" share);</li>
 * <li>{@link #ABILITY}: an ability or kido hit (damage types FREEZE / INDIRECT_MAGIC with the player as attacker): the fixed reward of the cast;</li>
 * <li>{@link #ARROW}: R13, a basic arrow of the Heilig Bogen: Rage proportional to the damage the arrow was shot with ({@code uryu.rage});</li>
 * <li>{@link #DEALT}: any other hit (sword, projectile): the share of the damage.</li>
 * </ul>
 */
public enum RageReward {
	NONE, ABILITY, DEALT, ARROW;

	/** R13: what the Uryu code says about the damage call it wraps (set around {@code UryuTargets.hurt}). */
	public enum UryuMark {
		/** Not an Uryu hit (or not marked): the normal rules. */
		NONE,
		/** A basic arrow: {@link RageReward#ARROW}. */
		BOW,
		/** One arrow or blast of an ability (Z, wings, Licht Regen, Sternenstaub, Sprenger, Seele): the fixed reward of the cast. */
		CAST
	}

	public static RageReward classify(boolean unrewarded, boolean abilityDamageType) {
		if (unrewarded) {
			return NONE;
		}
		return abilityDamageType ? ABILITY : DEALT;
	}

	/** Same with the Uryu mark; {@code castRewardOn} is {@code uryu.rage.abilityArrowsPayCastReward}. */
	public static RageReward classify(boolean unrewarded, boolean abilityDamageType, UryuMark mark, boolean castRewardOn) {
		if (unrewarded) {
			return NONE;
		}
		return switch (mark) {
			case BOW -> ARROW;
			case CAST -> castRewardOn ? ABILITY : DEALT;
			case NONE -> abilityDamageType ? ABILITY : DEALT;
		};
	}
}
