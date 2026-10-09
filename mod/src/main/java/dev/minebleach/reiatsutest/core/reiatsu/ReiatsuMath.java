package dev.minebleach.reiatsutest.core.reiatsu;

/** Pure reiatsu arithmetic. */
public final class ReiatsuMath {
	/** Default batch length in server ticks (ADR: at most 4 syncs per second). */
	public static final int DEFAULT_BATCH_TICKS = 5;

	private ReiatsuMath() {
	}

	/** value = clamp(value + regen - drain, 0, max). */
	public static ReiatsuState applyBatch(ReiatsuState s, Rate r) {
		return s.plus(r.net());
	}

	/**
	 * Upper bound in ticks until the bar is full when {@code r} applies every batch; 0 when already full;
	 * -1 when the net rate is not positive (never).
	 */
	public static int ticksToFull(ReiatsuState s, Rate r) {
		return ticksToFull(s, r, DEFAULT_BATCH_TICKS);
	}

	public static int ticksToFull(ReiatsuState s, Rate r, int batchTicks) {
		if (s.isFull()) {
			return 0;
		}
		int net = r.net();
		if (net <= 0) {
			return -1;
		}
		int missing = s.max() - s.value();
		int batches = (missing + net - 1) / net;
		return batches * batchTicks;
	}

	/** Ticks until the bar reaches zero under a negative net rate; -1 when the net rate is not negative. */
	public static int ticksToZero(ReiatsuState s, Rate r, int batchTicks) {
		int net = r.net();
		if (net >= 0) {
			return -1;
		}
		int batches = (s.value() + (-net) - 1) / (-net);
		return batches * batchTicks;
	}
}
