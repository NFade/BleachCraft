package dev.minebleach.reiatsutest.client.fx;

/**
 * Maps server ticks to FX seconds. Every {@code effect_event} carries the server tick of its cast and is received at a known FX
 * time; an {@code entity_fx} only names an end tick. The latest effect event is the reference, so a shell can end exactly when the
 * server says without comparing tick counters of different clocks (1.1 "Clock rule"). Without a reference the caller's fallback is used.
 */
public final class FxServerClock {
	private static boolean valid;
	private static int refTick;
	private static double refFx;

	private FxServerClock() {
	}

	public static void sync(int serverTick) {
		refTick = serverTick;
		refFx = FxClock.now;
		valid = true;
	}

	/** FX time of a server tick (never in the past, at most 12 s ahead), or now + fallback when no event arrived yet. */
	public static double fxTimeOf(int tick, double fallbackSeconds) {
		if (!valid) {
			return FxClock.now + fallbackSeconds;
		}
		double t = refFx + (tick - refTick) / 20.0;
		if (t > FxClock.now + 12.0 || t < FxClock.now - 30.0) {
			return FxClock.now + fallbackSeconds;
		}
		return Math.max(t, FxClock.now + 0.05);
	}

	public static boolean valid() {
		return valid;
	}

	public static void reset() {
		valid = false;
	}
}
