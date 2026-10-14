package dev.minebleach.reiatsutest.core.uryu;

/**
 * Sternenstaub (design/URYU.md section 5): a giant arrow of flechettes, a line 40 blocks long and 2 wide that pierces everything:
 * 20 HP to the first target on the line, 12 HP to every following one. The cast needs all 3 stacks of Focus and uses them up.
 */
public final class Sternenstaub {
	private Sternenstaub() {
	}

	/** May the ability be cast with this many stacks? Only a full Focus. */
	public static boolean canCast(int focusStacks) {
		return focusStacks >= UryuBalance.STAR_FOCUS;
	}

	/** S-U1: does the cast go off at the release? Only with a full Focus still standing (it was needed at the key press, and must survive the 0.8 s charge). */
	public static boolean releases(int focusStacksAtRelease) {
		return canCast(focusStacksAtRelease);
	}

	/** S-U1: the cooldown (ticks, from the fizzle) of a charge that lost its Focus: half of the full one. */
	public static int fizzleCooldown(int fullCooldownTicks) {
		return (int) Math.round(Math.max(0, fullCooldownTicks) * UryuBalance.STAR_FIZZLE_COOLDOWN_SHARE);
	}

	/** HP for the target at {@code index} along the line, nearest first (0 = the first). */
	public static float damage(int index) {
		return index <= 0 ? UryuBalance.STAR_FIRST : UryuBalance.STAR_FOLLOW;
	}

	/** Total HP of a line through {@code targets} targets (peak burst, must stay at or under 24 for one to two targets in a row). */
	public static float total(int targets) {
		float sum = 0f;
		for (int i = 0; i < targets; i++) {
			sum += damage(i);
		}
		return sum;
	}
}
