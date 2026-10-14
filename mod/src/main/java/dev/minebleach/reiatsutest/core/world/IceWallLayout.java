package dev.minebleach.reiatsutest.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * R11 T5: the cells of the Rukia ice wall, pure geometry (no Minecraft types, CorePurityTest). The server glue ({@code server/IceWall}) gives a
 * {@link Probe} of the world and filters the result (cells with entities, block entities, solid blocks); the wall itself is real blocks.
 *
 * <ul>
 * <li>{@link #distance}: where on the look ray the wall stands: at the range, or one block in front of what the ray hit;</li>
 * <li>{@link #plan}: the axis aligned wall perpendicular to the dominant horizontal direction of the look (no stepped diagonals), the base on the
 * surface (searched down from the aimed point), columns that reach down into a dip so no gap is left, a flat top;</li>
 * <li>{@link #without}: the cells that remain when the blocked ones are skipped;</li>
 * <li>{@link #dueTick} / {@link #goneLayers}: growth bottom to top and the fade of the top layers at the end of the life.</li>
 * </ul>
 */
public final class IceWallLayout {
	/** Returned by {@link #floorY} when there is no ground in reach. */
	public static final int NO_GROUND = Integer.MIN_VALUE;

	private IceWallLayout() {
	}

	/** What the layout needs to know about the world. */
	public interface Probe {
		/** The cell may take a wall block (air or replaceable plants, loaded chunk). */
		boolean passable(int x, int y, int z);

		/** The cell is solid ground the wall can stand on. */
		boolean support(int x, int y, int z);
	}

	/**
	 * One wall cell. {@code layer} counts from the lowest cell of the whole wall (0) up, {@code depth} from the near side (0) to the far side,
	 * {@code lateral} is the column offset across the wall (negative = left of the centre looking along the wall normal).
	 */
	public record WallCell(int x, int y, int z, int layer, int depth, int lateral) {
	}

	/**
	 * The result. {@code normalX}: the wall plane is perpendicular to X (it spans Z). {@code sign}: direction of the look along the normal (+1 or -1),
	 * the depth runs that way. {@code layers}: number of layers (top - lowest + 1). {@code grounded}: false when no ground was found and the base is
	 * the aimed height.
	 */
	public record Plan(boolean normalX, int sign, int centerX, int centerZ, int baseY, boolean grounded, int lowestY, int topY, int layers,
			List<WallCell> cells) {
	}

	/**
	 * Distance from the eye to the wall plane: {@code range} when the ray hit nothing (hitDistance NaN or beyond the range), else one block in
	 * front of the hit, never closer than 1.0 block.
	 */
	public static double distance(double range, double hitDistance) {
		if (Double.isNaN(hitDistance) || hitDistance >= range - 0.05) {
			return range;
		}
		return Math.max(1.0, hitDistance - 1.0);
	}

	/**
	 * The lowest cell of a column that is free and stands on ground. Starts at {@code startY} (moving up to {@code maxUp} cells first when it is
	 * inside a solid block) and looks down {@code maxDown} cells. {@link #NO_GROUND} when there is none.
	 */
	public static int floorY(Probe pr, int x, int z, int startY, int maxDown, int maxUp) {
		int y = startY;
		int up = 0;
		while (!pr.passable(x, y, z) && up < maxUp) {
			y++;
			up++;
		}
		if (!pr.passable(x, y, z)) {
			return NO_GROUND;
		}
		for (int k = 0; k <= maxDown + up; k++) {
			if (pr.support(x, y - 1, z)) {
				return y;
			}
			if (!pr.passable(x, y - 1, z)) {
				// something that is neither free nor ground (water, a fence ...): the surface is here, do not dig past it
				return NO_GROUND;
			}
			y--;
		}
		return NO_GROUND;
	}

	/**
	 * Plans the wall. {@code (px, py, pz)} is the point on the look ray at {@link #distance}; {@code (dx, dz)} the horizontal look direction.
	 */
	public static Plan plan(Probe pr, IceWallConfig cfg, double px, double py, double pz, double dx, double dz) {
		boolean normalX = Math.abs(dx) >= Math.abs(dz);
		int sign = (normalX ? dx : dz) >= 0.0 ? 1 : -1;
		int cx = (int) Math.floor(px);
		int cz = (int) Math.floor(pz);
		int startY = (int) Math.floor(py);
		int ground = floorY(pr, cx, cz, startY, cfg.searchDown(), 2);
		boolean grounded = ground != NO_GROUND;
		int baseY = grounded ? ground : startY;
		int top = baseY + cfg.height() - 1;

		int lo = -((cfg.width() - 1) / 2);
		List<int[]> cols = new ArrayList<>(); // {lateral, bottomY}
		int lowest = baseY;
		for (int i = 0; i < cfg.width(); i++) {
			int lat = lo + i;
			int x = normalX ? cx : cx + lat;
			int z = normalX ? cz + lat : cz;
			int bottom = baseY;
			if (grounded) {
				int g = floorY(pr, x, z, baseY + 1, cfg.fillDown() + 1, 0);
				if (g != NO_GROUND) {
					bottom = Math.max(baseY - cfg.fillDown(), Math.min(g, baseY));
				}
			}
			cols.add(new int[] {lat, bottom});
			lowest = Math.min(lowest, bottom);
		}
		List<WallCell> cells = new ArrayList<>();
		for (int[] col : cols) {
			int lat = col[0];
			for (int depth = 0; depth < cfg.thickness(); depth++) {
				int x = normalX ? cx + sign * depth : cx + lat;
				int z = normalX ? cz + lat : cz + sign * depth;
				for (int y = col[1]; y <= top; y++) {
					cells.add(new WallCell(x, y, z, y - lowest, depth, lat));
				}
			}
		}
		cells.sort(Comparator.comparingInt(WallCell::layer).thenComparingInt(WallCell::depth).thenComparingInt(WallCell::lateral));
		return new Plan(normalX, sign, cx, cz, baseY, grounded, lowest, top, top - lowest + 1, cells);
	}

	/** The cells that remain when {@code blocked} ones (entities, block entities, solid blocks) are skipped. */
	public static List<WallCell> without(List<WallCell> cells, Predicate<WallCell> blocked) {
		List<WallCell> out = new ArrayList<>(cells.size());
		for (WallCell c : cells) {
			if (!blocked.test(c)) {
				out.add(c);
			}
		}
		return out;
	}

	/** The first {@code max} cells (lowest layers first, so a cap never leaves a hole at the bottom). */
	public static List<WallCell> limit(List<WallCell> cells, int max) {
		return cells.size() <= max ? cells : new ArrayList<>(cells.subList(0, Math.max(0, max)));
	}

	/** Ticks after the start of the growth at which layer {@code layer} (0 = lowest) is placed: even steps over {@code growTicks}. */
	public static int dueTick(int layer, int layers, int growTicks) {
		if (layers <= 1) {
			return 0;
		}
		return Math.max(0, Math.min(layer, layers - 1)) * Math.max(1, growTicks) / layers;
	}

	/**
	 * How many of the top layers have vanished at {@code age} ticks after the start. Nothing until {@code lifeTicks - fadeTicks}, then one layer
	 * after the other from the top, all of them at {@code lifeTicks}.
	 */
	public static int goneLayers(int age, int lifeTicks, int fadeTicks, int layers) {
		if (age >= lifeTicks) {
			return layers;
		}
		int start = lifeTicks - Math.max(0, fadeTicks);
		if (fadeTicks <= 0 || age <= start) {
			return 0;
		}
		int gone = (int) Math.ceil((age - start) * (double) layers / fadeTicks);
		return Math.max(0, Math.min(layers, gone));
	}
}
