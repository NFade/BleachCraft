package dev.minebleach.reiatsutest.core.combat;

/**
 * Pure rule of R11 T11: a zanpakuto in the main hand never breaks grass. A block under the crosshair that is soft vegetation (grass, tall grass,
 * ferns, flowers, dead bushes, sprouts, the frost sprig), has no collision and a hardness of zero is not "mineable" for the sword: the click starts the
 * strike instead of digging, and the server refuses to break it with the sword. Stone, dirt, wood and everything harder is untouched (the player may
 * still dig).
 *
 * <p>R11 review fix 7: the hardness test alone was wider than "grass" (torches, wheat and other crops, sugar cane, saplings, redstone dust, tripwire
 * all have no collision and hardness 0), so a sword in the hand could not break them. The glue now also passes {@code softPlant}: whether the block is
 * on the list of {@code server/SoftPlants} (a block list and tags, decided with the game classes). Only that list is protected.
 * No Minecraft types: the glue reads the game into the arguments.
 */
public final class SwingThruRules {
	private SwingThruRules() {
	}

	/**
	 * @param holdsZanpakuto the main hand holds a zanpakuto
	 * @param softPlant      the block is soft vegetation (replaceable plant / grass-like plant, not a torch, crop, cane or redstone)
	 * @param noCollision    the block has an empty collision shape (you walk through it)
	 * @param hardness       {@code BlockState#getHardness}; negative = unbreakable
	 * @return true when the click must swing the sword and the block must stay
	 */
	public static boolean shouldSwing(boolean holdsZanpakuto, boolean softPlant, boolean noCollision, float hardness) {
		return holdsZanpakuto && softPlant && noCollision && hardness >= 0.0F && hardness <= 1.0e-4F;
	}
}
