package dev.minebleach.reiatsutest.core.state;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;

/**
 * Shunpo numbers (B4 step 5). Distances in blocks, time in ticks, cost in tenths of a reiatsu point.
 *
 * @param distance     how far the teleport goes along the look direction when nothing is in the way
 * @param minDistance  a shorter path than this is refused (no cost, no cooldown)
 * @param step         sampling step of the path check; smaller than the player width, so thin walls cannot be skipped
 * @param maxDrop      the landing needs ground within this many blocks below (no landing over a pit)
 */
public record ShunpoSpec(int costTenths, int cooldownTicks, double distance, double minDistance, double step, int maxDrop) {
	/** The spec of one character: Uryu (Hirenkyaku, the Quincy flash step) goes {@link UryuBalance#SHUNPO_BONUS} blocks farther; everybody else is unchanged. */
	public ShunpoSpec forCharacter(CharacterId who) {
		if (who == CharacterId.URYU) {
			return new ShunpoSpec(costTenths, cooldownTicks, distance + UryuBalance.SHUNPO_BONUS, minDistance, step, maxDrop);
		}
		return this;
	}
}
