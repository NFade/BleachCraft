package dev.minebleach.reiatsutest.core.combat;

/**
 * Pure rules for "may an ability hit this target" and "why did a hit not deal damage" (dmg_rage_round3). No Minecraft types:
 * {@code server/Targeting} reads the game state into the booleans and acts on the verdict; the verdict names are what the
 * {@code [dmg]} server log prints, so a missing hit can be read from the log.
 */
public final class HitRules {
	private HitRules() {
	}

	/** Why a target is left out of an area query (target selection). */
	public enum Refusal {
		OK,
		/** The caster itself. */
		SELF,
		DEAD,
		SPECTATOR,
		/** Entity type tag {@code reiatsu_test:immune}. */
		IMMUNE_TYPE,
		/** A tamed pet of the caster. */
		OWN_PET,
		/** The target is a player and {@code /reiatsu pvp false} is set ({@code Tuning.affectPlayers}). */
		PLAYERS_OFF,
		/** The target is a player and {@code pvp=false} in server.properties. */
		SERVER_PVP_OFF
	}

	/** What happened to a hit that was handed to {@code LivingEntity.damage}. */
	public enum Outcome {
		/** Health or absorption went down. */
		DAMAGED,
		/** Target is a player in Creative (or otherwise has abilities.invulnerable): vanilla ignores normal damage. */
		CREATIVE,
		/** Another vanilla invulnerability (isInvulnerableTo): spawn/respawn protection, invulnerable flag, same-team etc. */
		INVULNERABLE,
		/** Vanilla refused a player-on-player hit because of the scoreboard team (friendly fire off). */
		TEAM,
		/** Cancelled by the target's sword parry (ZanpakutoManager.allowDamage). */
		PARRY,
		/** Fully absorbed by the target's Byakuya shikai barrier. */
		BARRIER,
		/** The damage was lowered to nothing by a sword block of the target. */
		BLOCKED,
		/** The target is in its post-hit invulnerability frames ({@code timeUntilRegen}) and the new hit was not bigger. */
		IFRAMES,
		/** The damage amount was zero or negative. */
		ZERO_AMOUNT,
		/** damage() returned false for a reason we cannot name (another mod, the mixin shield, ...). */
		REFUSED_UNKNOWN,
		/** damage() returned true but no health was lost (armor/resistance/absorption ate it all). */
		NO_HEALTH_LOST
	}

	/** Target selection. Order matters: the first failing rule is the reason. */
	public static Refusal refusal(boolean self, boolean alive, boolean spectator, boolean immuneType, boolean ownPet,
			boolean targetIsPlayer, boolean affectPlayers, boolean serverPvp) {
		if (self) {
			return Refusal.SELF;
		}
		if (!alive) {
			return Refusal.DEAD;
		}
		if (spectator) {
			return Refusal.SPECTATOR;
		}
		if (immuneType) {
			return Refusal.IMMUNE_TYPE;
		}
		if (ownPet) {
			return Refusal.OWN_PET;
		}
		if (targetIsPlayer) {
			if (!affectPlayers) {
				return Refusal.PLAYERS_OFF;
			}
			if (!serverPvp) {
				return Refusal.SERVER_PVP_OFF;
			}
		}
		return Refusal.OK;
	}

	/**
	 * Explains the result of one {@code damage()} call.
	 *
	 * @param amount          requested hp
	 * @param returned        the boolean returned by damage()
	 * @param hpLost          health + absorption before minus after (can be negative if the target healed in the same tick)
	 * @param vetoReason      what {@code ZanpakutoManager.allowDamage} recorded when it cancelled the hit, or null
	 * @param creativeTarget  target is a player with abilities.invulnerable (Creative)
	 * @param invulnerableTo  {@code isInvulnerableTo(source)} measured before the hit
	 * @param teamBlocked     attacker and target are players of one team with friendly fire off, measured before the hit
	 * @param inIframes       {@code timeUntilRegen > 10} and {@code amount <= lastDamageTaken} before the hit
	 */
	public static Outcome outcome(float amount, boolean returned, float hpLost, Outcome vetoReason, boolean creativeTarget,
			boolean invulnerableTo, boolean teamBlocked, boolean inIframes) {
		if (!(amount > 0.0F)) {
			return Outcome.ZERO_AMOUNT;
		}
		// hpLost > 0 is the truth: a hit that the barrier or a block lowered is re-applied inside the damage hook and the
		// outer damage() then returns false although health went down (the old log said REFUSED_UNKNOWN for those)
		if (hpLost > 0.0F) {
			return Outcome.DAMAGED;
		}
		if (vetoReason != null) {
			return vetoReason;
		}
		if (creativeTarget) {
			return Outcome.CREATIVE;
		}
		if (invulnerableTo) {
			return Outcome.INVULNERABLE;
		}
		if (teamBlocked) {
			return Outcome.TEAM;
		}
		if (inIframes) {
			return Outcome.IFRAMES;
		}
		return returned ? Outcome.NO_HEALTH_LOST : Outcome.REFUSED_UNKNOWN;
	}
}
