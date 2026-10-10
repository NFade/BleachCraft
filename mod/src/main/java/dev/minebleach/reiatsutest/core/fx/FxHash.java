package dev.minebleach.reiatsutest.core.fx;

/**
 * Stateless seeded hashing for the closed-form effects (VFX_STORYBOARD 1.7.3: "SplittableRandom(seed ^ i * golden)"): the
 * same (seed, index, salt) gives the same number on every client and in every frame, so swarms, blade rows and storms need
 * no per-element state.
 */
public final class FxHash {
	private static final long GOLDEN = 0x9E3779B97F4A7C15L;

	private FxHash() {
	}

	/** SplitMix64 finalizer. */
	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static long hash(long seed, int index, int salt) {
		return mix(seed * GOLDEN + (long) index * 0xD6E8FEB86659FD93L + (long) salt * 0xA0761D6478BD642FL + 0x1234567L);
	}

	/** Uniform in [0, 1). */
	public static double unit(long seed, int index, int salt) {
		return (hash(seed, index, salt) >>> 11) * 0x1.0p-53;
	}

	/** Uniform in [-1, 1). */
	public static double signed(long seed, int index, int salt) {
		return unit(seed, index, salt) * 2.0 - 1.0;
	}
}
