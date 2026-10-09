package dev.minebleach.reiatsutest.core.reiatsu;

/** Reiatsu bar in tenths of a point. Invariant: 0 <= value <= max (the constructor clamps). */
public record ReiatsuState(int value, int max) {
	public ReiatsuState {
		max = Math.max(0, max);
		value = Math.max(0, Math.min(max, value));
	}

	public static ReiatsuState full(int max) {
		return new ReiatsuState(max, max);
	}

	/** Returns a copy with {@code delta} added, clamped to [0, max]. Overflow safe. */
	public ReiatsuState plus(int delta) {
		long v = (long) value + delta;
		return new ReiatsuState((int) Math.max(0L, Math.min((long) max, v)), max);
	}

	public boolean isFull() {
		return value == max;
	}

	/** Spending may never take the bar to zero (STATE_MACHINE rule R2): value - cost >= 1. */
	public boolean canSpend(int cost) {
		return (long) value - cost >= 1L;
	}
}
