package dev.minebleach.reiatsutest.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Frozen vegetation of the Rukia bankai territory freeze: a cross of thin ice blades that replaces grass, ferns and flowers while the
 * field stands. No collision, no drops, not breakable (the original plant is restored when the field ends), it never pops off
 * because it has no support rule.
 */
public class FrostSprigBlock extends Block {
	private static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

	public FrostSprigBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}
}
