package dev.minebleach.reiatsutest.server;

import dev.minebleach.reiatsutest.core.state.ShunpoPath;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * The world side of the shunpo path check (B4 step 5): block collisions of the real player box, the world border and loaded
 * chunks decide where the player fits; a landing needs solid (or water) ground within a few blocks below and no lava, fire,
 * magma or cactus. The path logic itself is {@link ShunpoPath}.
 */
final class ShunpoWorld implements ShunpoPath.Probe {
	private final ServerPlayerEntity player;
	private final ServerWorld world;
	private final EntityDimensions dims;

	ShunpoWorld(ServerPlayerEntity player) {
		this.player = player;
		this.world = player.getServerWorld();
		this.dims = player.getDimensions(player.getPose());
	}

	@Override
	public boolean fits(double x, double y, double z) {
		BlockPos feet = BlockPos.ofFloored(x, y, z);
		if (world.isOutOfHeightLimit(feet) || !world.getChunkManager().isChunkLoaded(feet.getX() >> 4, feet.getZ() >> 4)) {
			return false;
		}
		Box box = dims.getBoxAt(x, y, z);
		return world.isSpaceEmpty(player, box);
	}

	@Override
	public boolean safeLanding(double x, double y, double z, int maxDrop) {
		BlockPos feet = BlockPos.ofFloored(x, y, z);
		if (hazard(world.getBlockState(feet)) || hazard(world.getBlockState(feet.up()))) {
			return false;
		}
		for (int d = 0; d <= maxDrop; d++) {
			BlockPos below = BlockPos.ofFloored(x, y - 0.1 - d, z);
			if (world.isOutOfHeightLimit(below)) {
				return false;
			}
			BlockState st = world.getBlockState(below);
			if (st.getFluidState().isIn(FluidTags.LAVA)) {
				return false;
			}
			if (!st.getCollisionShape(world, below).isEmpty()) {
				return !(st.isOf(Blocks.MAGMA_BLOCK) || st.isOf(Blocks.CACTUS) || st.isOf(Blocks.CAMPFIRE) || st.isOf(Blocks.SOUL_CAMPFIRE)
						|| st.isOf(Blocks.SWEET_BERRY_BUSH) || hazard(st));
			}
			if (st.getFluidState().isIn(FluidTags.WATER)) {
				return true; // landing in water is fine
			}
		}
		return false;
	}

	private static boolean hazard(BlockState st) {
		return st.getBlock() instanceof AbstractFireBlock || st.getFluidState().isIn(FluidTags.LAVA) || st.isOf(Blocks.COBWEB)
				|| st.isOf(Blocks.POWDER_SNOW);
	}
}
