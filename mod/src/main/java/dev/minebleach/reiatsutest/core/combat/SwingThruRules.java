package dev.minebleach.reiatsutest.core.combat;

/**
 * Pure rule of R11 T11: a zanpakuto in the main hand never breaks grass. A block under the crosshair that has no collision and a hardness of
 * zero (grass, tall grass, ferns, flowers, dead bushes, sprouts) is not "mineable" for the sword: the click starts the strike instead of
 * digging, and the server refuses to break it with the sword. Stone, dirt, wood and everything harder is untouched (the player may still dig).
 * No Minecraft types: the glue reads the game into the arguments.
 */
public final class SwingThruRules {
	private SwingThruRules() {
	}

	/**
	 * @param holdsZanpakuto the main hand holds a zanpakuto
	 * @param noCollision    the block has an empty collision shape (you walk through it)
	 * @param hardness       {@code BlockState#getHardness}; negative = unbreakable
	 * @return true when the click must swing the sword and the block must stay
	 */
	public static boolean shouldSwing(boolean holdsZanpakuto, boolean noCollision, float hardness) {
		return holdsZanpakuto && noCollision && hardness >= 0.0F && hardness <= 1.0e-4F;
	}
}
