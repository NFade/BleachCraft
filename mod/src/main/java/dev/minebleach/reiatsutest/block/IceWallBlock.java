package dev.minebleach.reiatsutest.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/**
 * The ice wall of the Rukia bankai (R11 T5): a translucent full block with standard friction. It looks like the frost blocks but is its own
 * block, never vanilla ice (that melts in light and leaves water). Faces between two wall blocks are not drawn, so a 2 block thick wall
 * does not look twice as opaque as one layer. It is placed and removed by {@code server/IceWall} through the temporary block journal.
 */
public class IceWallBlock extends Block {
	public IceWallBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
		return stateFrom.isOf(this) || super.isSideInvisible(state, stateFrom, direction);
	}
}
