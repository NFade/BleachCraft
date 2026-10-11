package dev.minebleach.reiatsutest.core.rage;

/** Rage bar in tenths of a point. Invariant: 0 <= value <= max (the constructor clamps). Unlike reiatsu it may be spent to zero. */
public record RageState(int value, int max) {
	public RageState {
		max = Math.max(0, max);
		value = Math.max(0, Math.min(max, value));
	}

	public static RageState empty(int max) {
		return new RageState(0, max);
	}

	/** Returns a copy with {@code delta} added, clamped to [0, max]. Overflow safe. */
	public RageState plus(int delta) {
		long v = (long) value + delta;
		return new RageState((int) Math.max(0L, Math.min((long) max, v)), max);
	}

	public boolean isFull() {
		return max > 0 && value == max;
	}

	/** True if {@code cost} can be paid in full. A free action (cost <= 0) is always possible. */
	public boolean canSpend(int cost) {
		return cost <= 0 || value >= cost;
	}
}
