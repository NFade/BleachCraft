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
 * {@code FxGround.sample} of the storyboard (1.7.3): the y of the first solid top face found by scanning from
 * {@code hint + 3} down to {@code hint - 6} (not the height map, so caves and roofs work).
 * <p>Two entry points (merged from phase6-fx and p6rukia): {@link #sample(ClientWorld, double, double, double)} is the
 * uncached one with an explicit world (ground rows, Swarm/Byakuya); {@link #sample(double, double, double)} uses the
 * client world and caches per column for one second (Rukia shikai/bankai, decals, mesh pass).
 */
public final class FxGround {
	private record Entry(double y, double until) {
	}

	private static final Map<Long, Entry> CACHE = new HashMap<>();
	private static double lastPurge;

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

	private static long key(int x, int z, int hintBand) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (hintBand & 0xFFFL);
	}

	/** Cached (1 s) surface height at (x, z) near {@code hintY} (typically the caster's feet); {@code hintY} itself when nothing solid is found. */
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
		double result = sample(w, x, z, hintY);
		CACHE.put(k, new Entry(result, now + 1.0));
		return result;
	}

	public static void clear() {
		CACHE.clear();
	}
}
