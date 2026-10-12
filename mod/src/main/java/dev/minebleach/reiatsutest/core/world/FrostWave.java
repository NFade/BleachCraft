package dev.minebleach.reiatsutest.core.world;

import java.util.Arrays;

/**
 * The outward running ring of the territory freeze. All block columns of the disc of {@code radius} around the centre are
 * listed once, sorted by distance (ties broken by angle), so "the ring has reached column i" is a simple cursor. The ring
 * radius grows at {@code speed} blocks per second; a per tick column budget bounds the work, columns that do not fit are
 * simply taken one tick later (the cursor never skips one). Pure, deterministic, no Minecraft.
 */
public final class FrostWave {
	private final int radius;
	private final double speed;
	private final int columnsPerTick;
	private final int[] dx;
	private final int[] dz;
	private final double[] dist;

	public FrostWave(int radius, double speedBlocksPerSecond, int columnsPerTick) {
		this.radius = Math.max(1, radius);
		this.speed = Math.max(0.1, speedBlocksPerSecond);
		this.columnsPerTick = Math.max(1, columnsPerTick);
		int r = this.radius;
		int n = 0;
		long[] keys = new long[(2 * r + 1) * (2 * r + 1)];
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				int d2 = x * x + z * z;
				if (d2 <= r * r) {
					// sort key: squared distance, then the angle in 1/1024 turns, then the cell (to be fully deterministic)
					long ang = Math.round((Math.atan2(z, x) + Math.PI) / (2 * Math.PI) * 1023.0);
					keys[n++] = ((long) d2 << 40) | (ang << 28) | (long) ((x + 64) << 8 | (z + 64));
				}
			}
		}
		keys = Arrays.copyOf(keys, n);
		Arrays.sort(keys);
		dx = new int[n];
		dz = new int[n];
		dist = new double[n];
		for (int i = 0; i < n; i++) {
			long cell = keys[i] & 0xFFFFL;
			dx[i] = (int) (cell >> 8) - 64;
			dz[i] = (int) (cell & 0xFF) - 64;
			dist[i] = Math.sqrt(dx[i] * dx[i] + dz[i] * dz[i]);
		}
	}

	public int radius() {
		return radius;
	}

	public int totalColumns() {
		return dx.length;
	}

	public int dx(int i) {
		return dx[i];
	}

	public int dz(int i) {
		return dz[i];
	}

	public double distance(int i) {
		return dist[i];
	}

	/** Radius of the ring after {@code ageTicks} ticks (grows from 0, stops at the rim). */
	public double radiusAt(int ageTicks) {
		return Math.min(radius, Math.max(0, ageTicks) * speed / 20.0);
	}

	/** Ticks until the ring touches the rim. */
	public int ticksToRim() {
		return (int) Math.ceil(radius / speed * 20.0);
	}

	/** Number of columns (a prefix of the sorted list) the ring has reached at {@code ageTicks}; the centre column is reached at age 0. */
	public int reached(int ageTicks) {
		double r = radiusAt(ageTicks);
		int lo = 0;
		int hi = dist.length;
		while (lo < hi) { // first index with distance > r
			int mid = (lo + hi) >>> 1;
			if (dist[mid] <= r) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/**
	 * The new cursor for one tick: it moves toward {@link #reached} but by at most {@code columnsPerTick}, so a slow server
	 * works the ring off a little later instead of freezing the tick.
	 */
	public int advance(int cursor, int ageTicks) {
		int target = reached(ageTicks);
		return Math.min(target, cursor + columnsPerTick);
	}

	/** True once every column has been taken. */
	public boolean done(int cursor) {
		return cursor >= dx.length;
	}
}
