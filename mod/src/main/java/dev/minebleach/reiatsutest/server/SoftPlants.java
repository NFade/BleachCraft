package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;

/**
 * R11 review fixes: the one list of "soft vegetation" shared by the sword guard ({@link GrassGuard}) and the ice wall ({@link TempBlocks#wallReplaceable}).
 * Grass, tall grass, ferns, flowers (small and tall), dead bushes, nether sprouts and the mod's frost sprig (what the frost field turns grass into).
 * Deliberately NOT in the list: torches, crops (wheat, carrots, potatoes, nether wart), sugar cane, saplings, redstone dust, tripwire, berry bushes, vines, snow.
 */
public final class SoftPlants {
	private SoftPlants() {
	}

	public static boolean isSoftPlant(BlockState st) {
		if (st.isOf(ModBlocks.FROST_SPRIG)) {
			return true;
		}
		return st.isOf(Blocks.SHORT_GRASS) || st.isOf(Blocks.TALL_GRASS) || st.isOf(Blocks.FERN) || st.isOf(Blocks.LARGE_FERN) || st.isOf(Blocks.DEAD_BUSH)
				|| st.isOf(Blocks.NETHER_SPROUTS) || st.isIn(BlockTags.SMALL_FLOWERS) || st.isIn(BlockTags.TALL_FLOWERS);
	}
}
