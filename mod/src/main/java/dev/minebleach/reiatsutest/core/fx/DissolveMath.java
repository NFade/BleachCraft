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

	/** Inverse of {@link #ease}: the progress at which the eased value is {@code f} (closed form of the smoothstep cubic). */
	public static double invEase(double f) {
		double y = clamp01(f);
		return 0.5 - Math.sin(Math.asin(1 - 2 * y) / 3);
	}

	/**
	 * Progress (0..1) at which the front reaches blade position {@code s}: the moment a fragment at {@code s} comes loose. Tip first:
	 * s = 1 goes first; hilt first: s = 0 goes first. {@code s} outside [-MARGIN, 1 + MARGIN] is clamped to the ends.
	 */
	public static double detachProgress(double s, boolean tipFirst) {
		double lo = -MARGIN;
		double hi = 1 + MARGIN;
		double f = tipFirst ? (hi - s) / (hi - lo) : (s - lo) / (hi - lo);
		return invEase(f);
	}

	/**
	 * Glow level of the blade over the whole release, about 0.12 to 1.5. {@code hold} is the progress of the pink hold (0..1, then stays
	 * 1), {@code p} the dissolve progress (-1 before it starts). It rises through the hold to 1, swells a little more at the start of
	 * the dissolve (the build up before the break, peak near p = 0.28) and falls to zero over the last 18 percent.
	 */
	public static double glowLevel(double hold, double p) {
		if (p < 0) {
			double h = clamp01(hold);
			return 0.12 + 0.88 * Math.pow(h * h * (3 - 2 * h), 1.4);
		}
		double x = clamp01(p);
		double bump = Math.sin(Math.PI * clamp01(x / 0.55));
		return (1.0 + 0.5 * bump) * (1 - smooth(0.82, 1.0, x));
	}

	/** Flash at the start of the dissolve (the break): rises over the first 6 percent, gone by 30 percent; 0 to 1. */
	public static double breakFlash(double p) {
		if (p < 0) {
			return 0;
		}
		return smooth(0.0, 0.06, p) * (1 - smooth(0.06, 0.30, p));
	}

	/** Remaining glow of the hilt after the dissolve: 1 right at its end, 0 after {@code tail} seconds. */
	public static double hiltAfterglow(double secondsSinceEnd, double tail) {
		if (secondsSinceEnd < 0) {
			return 1;
		}
		return 1 - smooth(0.0, Math.max(0.01, tail), secondsSinceEnd);
	}

	private static double smooth(double e0, double e1, double x) {
		double t = x <= e0 ? 0 : x >= e1 ? 1 : (x - e0) / (e1 - e0);
		return t * t * (3 - 2 * t);
	}

	private static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}
}
