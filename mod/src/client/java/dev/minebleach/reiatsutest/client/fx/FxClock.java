package dev.minebleach.reiatsutest.client.fx;

import net.minecraft.client.MinecraftClient;

/**
 * The FX clock in seconds: advances once per rendered world frame (not per tick), so timelines, glow sprites and screen
 * effects hit their times to the frame. Frozen by the dev hook ({@code -Dreiatsu.fx.freezeAt}) and paused with the game.
 */
public final class FxClock {
	/** Seconds since start of the session (frozen while {@link #frozen}). */
	public static double now;
	/** Seconds advanced in the current frame. */
	public static double dt;
	public static boolean frozen;
	public static long frame;
	private static long lastNanos;

	private FxClock() {
	}

	/** Raw wall dt for this frame, clamped to 0.1 s and zero while the game is paused. */
	static double rawDt() {
		long t = System.nanoTime();
		double d = lastNanos == 0 ? 0 : (t - lastNanos) / 1.0e9;
		lastNanos = t;
		if (d > 0.1) {
			d = 0.1;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.isPaused()) {
			d = 0;
		}
		return d;
	}

	static void advance(double d) {
		dt = d;
		now += d;
		frame++;
	}

	public static void freeze() {
		frozen = true;
	}

	public static void unfreeze() {
		frozen = false;
		lastNanos = System.nanoTime();
	}
}
