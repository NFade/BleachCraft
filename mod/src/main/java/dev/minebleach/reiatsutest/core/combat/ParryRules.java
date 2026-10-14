package dev.minebleach.reiatsutest.core.combat;

/**
 * Pure rule of the parry reiatsu (R11 T2): a successful parry restores reiatsu to the defender, but not twice inside the gap (a flood of
 * fast attacks parried in the same instant is one reward). No Minecraft types.
 */
public final class ParryRules {
	/** "Never rewarded yet" for {@link #reiatsuTenths}. */
	public static final int NEVER = Integer.MIN_VALUE;

	private ParryRules() {
	}

	/**
	 * @param perParryTenths reward of one parry in tenths of a point
	 * @param tick           server tick of this parry
	 * @param lastRewardTick tick of the previous rewarded parry of this defender, or {@link #NEVER}
	 * @param gapTicks       minimum ticks between two rewards
	 * @return the tenths to give (0 inside the gap or for a non-positive reward)
	 */
	public static int reiatsuTenths(int perParryTenths, int tick, int lastRewardTick, int gapTicks) {
		if (perParryTenths <= 0) {
			return 0;
		}
		if (lastRewardTick != NEVER && tick >= lastRewardTick && tick - lastRewardTick < gapTicks) {
			return 0;
		}
		return perParryTenths;
	}
}
