package dev.minebleach.reiatsutest.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/**
 * The ice wall of the Rukia bankai (R11 T5): a translucent full collider block with standard friction. It is its own block, never vanilla ice (that melts
 * in light and leaves water). It is placed and removed by {@code server/IceWall} through the temporary block journal.
 *
 * <p>Looks (R11, design/results/r11_ice_crystals.md): the client replaces the model of this block ({@code client/model/IceWallCrystalPlugin}): every block is
 * one cell of a crystal column made from OBJ pieces modelled in Blender, picked and bent by the block position ({@code core/world/IceCrystalField}). The
 * server knows nothing about it: collision, strength and the rollback stay those of a full block. {@code isSideInvisible} only matters for the plain
 * JSON cube that is drawn when the OBJ model cannot be loaded (faces between two wall blocks are not drawn there).
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
