package dev.minebleach.reiatsutest.core.state;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;

/**
 * Shunpo numbers (B4 step 5). Distances in blocks, time in ticks, cost in tenths of a reiatsu point.
 *
 * @param distance          how far the teleport goes along the look direction when nothing is in the way
 * @param minDistance       a shorter path than this is refused (no cost, no cooldown)
 * @param step              sampling step of the path check; smaller than the player width, so thin walls cannot be skipped
 * @param maxDrop           the landing needs ground within this many blocks below (no landing over a pit)
 * @param uryuDistanceBonus blocks Uryu's Hirenkyaku goes farther than the shunpo of the others
 * @param uryuCooldownBonus ticks Uryu's Hirenkyaku waits longer (R14: he goes farther, so he can be caught up with)
 */
public record ShunpoSpec(int costTenths, int cooldownTicks, double distance, double minDistance, double step, int maxDrop,
		double uryuDistanceBonus, int uryuCooldownBonus) {
	/** The six pre-R14 numbers; Uryu's bonuses take the code defaults of {@link UryuBalance}. */
	public ShunpoSpec(int costTenths, int cooldownTicks, double distance, double minDistance, double step, int maxDrop) {
		this(costTenths, cooldownTicks, distance, minDistance, step, maxDrop, UryuBalance.SHUNPO_BONUS, UryuBalance.SHUNPO_COOLDOWN_BONUS);
	}

	/** The spec of one character: Uryu (Hirenkyaku, the Quincy flash step) goes {@link #uryuDistanceBonus} blocks farther and waits {@link #uryuCooldownBonus} ticks longer; everybody else is unchanged. */
	public ShunpoSpec forCharacter(CharacterId who) {
		if (who == CharacterId.URYU) {
			return new ShunpoSpec(costTenths, cooldownTicks + uryuCooldownBonus, distance + uryuDistanceBonus, minDistance, step, maxDrop,
					uryuDistanceBonus, uryuCooldownBonus);
		}
		return this;
	}
}
