package dev.minebleach.reiatsutest.client.fx;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/** {@code FxGround.sample} of the storyboard (1.7.3): the first solid top face found scanning from feet + 3 down to feet - 6 (not the heightmap: caves). */
public final class FxGround {
	private FxGround() {
	}

	/** Top face height of the first solid block in the column of (x, z), scanning down from {@code feetY + 3}; {@code feetY} when nothing is found. */
	public static double sample(ClientWorld w, double x, double z, double feetY) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int top = (int) Math.floor(feetY + 3);
		int bottom = (int) Math.floor(feetY - 6);
		for (int y = top; y >= bottom; y--) {
			pos.set(bx, y, bz);
			BlockState st = w.getBlockState(pos);
			if (st.isAir()) {
				continue;
			}
			VoxelShape sh = st.getCollisionShape(w, pos);
			if (!sh.isEmpty()) {
				return y + sh.getMax(Direction.Axis.Y);
			}
		}
		return feetY;
	}

	/** The block state under the column (the one whose top face {@link #sample} returned), for crumb particles. */
	public static BlockState stateAt(ClientWorld w, double x, double z, double groundY) {
		BlockPos pos = BlockPos.ofFloored(x, groundY - 0.05, z);
		return w.getBlockState(pos);
	}
}
