package dev.minebleach.reiatsutest.client.model;

/**
 * Clock of the first person animations (draw, strike, block, ability gestures). In the game it is the real nano clock; the dev pose
 * harness ({@code Phase6Fp}) switches it to a fixed step clock that only moves when the harness advances it, so a pose can be captured at
 * an exact time of an animation (the contact sheets) independent of the frame rate.
 */
public final class FpClock {
	private static volatile boolean fixed;
	private static long fakeNow = 1_000_000_000_000L;
	private static double pending;
	/** Length of one step of the fixed clock in seconds (one rendered frame advances at most this much). */
	private static final double STEP = 1.0 / 60.0;

	private FpClock() {
	}

	public static long nanos() {
		return fixed ? fakeNow : System.nanoTime();
	}

	/** Called once per rendered frame at the start of the first person pass: moves the fixed clock by one step of the requested advance. */
	static void onFrame() {
		if (fixed && pending > 1e-9) {
			double d = Math.min(pending, STEP);
			pending -= d;
			fakeNow += (long) (d * 1.0e9);
		}
	}

	/** Dev harness: switch between the fixed step clock (starts at its current value) and the real clock. */
	public static void setFixed(boolean on) {
		if (on && !fixed) {
			fakeNow = System.nanoTime();
		}
		fixed = on;
		pending = 0;
	}

	public static boolean isFixed() {
		return fixed;
	}

	/** Dev harness: ask the fixed clock to advance by this many seconds over the next frames. */
	public static void advance(double seconds) {
		pending += Math.max(0, seconds);
	}

	public static boolean idle() {
		return !fixed || pending <= 1e-9;
	}
}
