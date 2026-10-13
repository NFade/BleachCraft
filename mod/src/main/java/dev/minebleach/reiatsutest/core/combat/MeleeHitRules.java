package dev.minebleach.reiatsutest.core.combat;

/**
 * Pure rule of the melee hit reiatsu (r8 item 5): a landed melee hit with a zanpakuto restores reiatsu. No Minecraft types:
 * {@code server/MeleeReiatsu} reads the game into the booleans.
 *
 * <p>A hit counts when it is a direct melee hit of a player ({@code PLAYER_ATTACK} damage with the player as the attacker, which
 * is what Better Combat strikes and vanilla attacks deal; abilities and kido use FREEZE / INDIRECT_MAGIC and never match), the
 * player holds a zanpakuto in the main hand, and damage really landed (parried, barrier-eaten and missed hits never reach the
 * damage event, a fully absorbed one has zero damage).
 */
public final class MeleeHitRules {
	private MeleeHitRules() {
	}

	/** Reiatsu in tenths of a point (the unit of the bar and of every cost) to give for this hit; 0 when it does not count. */
	public static int reiatsuTenths(boolean playerMelee, boolean holdsZanpakuto, float damageTaken, int perHitTenths) {
		if (!playerMelee || !holdsZanpakuto || !(damageTaken > 0.0F) || perHitTenths <= 0) {
			return 0;
		}
		return perHitTenths;
	}
}
