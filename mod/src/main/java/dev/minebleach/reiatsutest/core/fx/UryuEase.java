package dev.minebleach.reiatsutest.core.fx;

/** The easing names of the storyboard header (oc, ob, oe, iq, ios) for the pure Uryu effect tables (no Minecraft types). */
public final class UryuEase {
	private UryuEase() {
	}

	public static double clamp(double x) {
		return x < 0 ? 0 : Math.min(x, 1);
	}

	public static double clamp(double x, double lo, double hi) {
		return x < lo ? lo : Math.min(x, hi);
	}

	public static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** easeOutCubic. */
	public static double oc(double x) {
		double u = 1 - clamp(x);
		return 1 - u * u * u;
	}

	/** easeOutBack with the overshoot constant {@code c1} (1.70158 is the standard one). */
	public static double ob(double x, double c1) {
		double u = clamp(x) - 1;
		return 1 + (c1 + 1) * u * u * u + c1 * u * u;
	}

	/** easeOutExpo. */
	public static double oe(double x) {
		x = clamp(x);
		return x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
	}

	/** easeInQuad. */
	public static double iq(double x) {
		x = clamp(x);
		return x * x;
	}

	/** easeInOutSine. */
	public static double ios(double x) {
		return -(Math.cos(Math.PI * clamp(x)) - 1) / 2;
	}

	public static double smooth(double e0, double e1, double x) {
		double t = clamp((x - e0) / (e1 - e0));
		return t * t * (3 - 2 * t);
	}
}
