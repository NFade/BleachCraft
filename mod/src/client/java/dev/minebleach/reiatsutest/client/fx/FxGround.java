package dev.minebleach.reiatsutest.client.fx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * {@code FxGround.sample(x, z)} (1.7.3): the y of the first solid top face found by scanning from {@code hint + 3} down to
 * {@code hint - 6} (not the height map, so caves and roofs work), cached per column for one second.
 */
public final class FxGround {
	private record Entry(double y, double until) {
	}

	private static final Map<Long, Entry> CACHE = new HashMap<>();
	private static double lastPurge;

	private FxGround() {
	}

	private static long key(int x, int z, int hintBand) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (hintBand & 0xFFFL);
	}

	/** Surface height at (x, z) near {@code hintY} (typically the caster's feet); {@code hintY} itself when nothing solid is found. */
	public static double sample(double x, double z, double hintY) {
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w == null) {
			return hintY;
		}
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int hy = (int) Math.floor(hintY);
		long k = key(bx, bz, Math.floorDiv(hy, 3));
		double now = FxClock.now;
		if (now - lastPurge > 5.0) {
			lastPurge = now;
			CACHE.values().removeIf(e -> e.until < now);
		}
		Entry c = CACHE.get(k);
		if (c != null && c.until >= now) {
			return c.y;
		}
		BlockPos.Mutable m = new BlockPos.Mutable();
		double result = hintY;
		for (int y = hy + 3; y >= hy - 6; y--) {
			m.set(bx, y, bz);
			BlockState bs = w.getBlockState(m);
			VoxelShape shape = bs.getCollisionShape(w, m);
			if (!shape.isEmpty()) {
				result = y + shape.getMax(Direction.Axis.Y);
				break;
			}
		}
		CACHE.put(k, new Entry(result, now + 1.0));
		return result;
	}

	public static void clear() {
		CACHE.clear();
	}
}
