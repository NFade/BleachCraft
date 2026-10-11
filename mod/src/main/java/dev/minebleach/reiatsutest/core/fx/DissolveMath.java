package dev.minebleach.reiatsutest.core.fx;

/**
 * Byakuya shikai blade dissolve (plan G-glow): after the pink hold the drawn blade glows pink and breaks into petals along its length.
 * Pure math, no Minecraft types. The blade runs from s = 0 (saya mouth, the tsuba) to s = 1 (tip); the front moves from the tip
 * (top of the held blade, tip first is the default) down to the hilt, or the other way round. Points with s below 0 are the hilt and never dissolve.
 */
public final class DissolveMath {
	/** The front starts and ends a little outside the blade so the first and last quads are fully covered. */
	public static final double MARGIN = 0.05;

	private DissolveMath() {
	}

	/** Smoothstep of the progress: the front starts and stops slowly. */
	public static double ease(double p) {
		double x = clamp01(p);
		return x * x * (3 - 2 * x);
	}

	/**
	 * Position of the dissolve front on the blade axis. Hilt first: moves from {@code -MARGIN} to {@code 1 + MARGIN}; tip first: the
	 * reverse. Blade points on the far side of it (towards the hilt when hilt first) are gone.
	 */
	public static double boundary(double p, boolean tipFirst) {
		double f = ease(p);
		double lo = -MARGIN;
		double hi = 1 + MARGIN;
		return tipFirst ? hi - (hi - lo) * f : lo + (hi - lo) * f;
	}

	/** A blade point at {@code s} survives while the front has not reached it. */
	public static boolean keep(double s, double boundary, boolean tipFirst) {
		return tipFirst ? s <= boundary : s >= boundary;
	}

	/** Position of a point along the blade, 0 at {@code base} and 1 at {@code base + length * dir}, for a unit {@code dir} and {@code length > 0}. */
	public static double along(double x, double y, double z, double bx, double by, double bz, double dx, double dy, double dz, double length) {
		if (length <= 1e-9) {
			return 0;
		}
		return ((x - bx) * dx + (y - by) * dy + (z - bz) * dz) / length;
	}

	/** Alpha of the pink glow shell over the remaining blade: full at the start, fading out over the last 30 percent of the dissolve. */
	public static double shellAlpha(double p) {
		double x = clamp01(p);
		double t = clamp01((x - 0.7) / 0.3);
		return 1 - t * t * (3 - 2 * t);
	}

	/** Mix of the hilt colour from pink (0) back to its own colour (1) across the dissolve, so the hilt does not change colour at the end. */
	public static double hiltMix(double p) {
		return ease(p);
	}

	private static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}
}
