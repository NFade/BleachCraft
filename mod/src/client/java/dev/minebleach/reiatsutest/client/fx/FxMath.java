package dev.minebleach.reiatsutest.client.fx;

/** Easing curves of the storyboard header (oc, ob, oe, iq, ios) and small colour helpers. */
public final class FxMath {
	public static final int LINEAR = 0;
	public static final int OC = 1;
	public static final int OB = 2;
	public static final int OE = 3;
	public static final int IQ = 4;
	public static final int IOS = 5;
	public static final int SMOOTH = 6;

	private static final double C1 = 1.70158;
	private static final double C3 = C1 + 1.0;

	private FxMath() {
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
		x = clamp(x);
		double u = 1 - x;
		return 1 - u * u * u;
	}

	/** easeOutBack. */
	public static double ob(double x) {
		x = clamp(x);
		double u = x - 1;
		return 1 + C3 * u * u * u + C1 * u * u;
	}

	/** easeOutBack with a custom overshoot constant (c1). */
	public static double ob(double x, double c1) {
		x = clamp(x);
		double u = x - 1;
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
		x = clamp(x);
		return -(Math.cos(Math.PI * x) - 1) / 2;
	}

	public static double smoothstep(double e0, double e1, double x) {
		double t = clamp((x - e0) / (e1 - e0));
		return t * t * (3 - 2 * t);
	}

	public static double ease(int kind, double x) {
		return switch (kind) {
			case OC -> oc(x);
			case OB -> ob(x);
			case OE -> oe(x);
			case IQ -> iq(x);
			case IOS -> ios(x);
			case SMOOTH -> smoothstep(0, 1, x);
			default -> clamp(x);
		};
	}

	/** {@code #RRGGBB} to 0..1 floats. */
	public static float[] hex(String h) {
		int v = Integer.parseInt(h.charAt(0) == '#' ? h.substring(1) : h, 16);
		return new float[] {((v >> 16) & 255) / 255f, ((v >> 8) & 255) / 255f, (v & 255) / 255f};
	}

	public static int packRgb(float[] c) {
		return ((int) (clamp(c[0]) * 255 + 0.5) << 16) | ((int) (clamp(c[1]) * 255 + 0.5) << 8) | (int) (clamp(c[2]) * 255 + 0.5);
	}

	public static float[] mix(float[] a, float[] b, double t) {
		return new float[] {(float) lerp(a[0], b[0], t), (float) lerp(a[1], b[1], t), (float) lerp(a[2], b[2], t)};
	}

	/** Distance LOD factor for particles and glow sprites (1.6): 1 up to 24, 0.5 to 48, 0.25 to 96, 0 beyond. */
	public static double lod(double d) {
		return d <= 24 ? 1.0 : d <= 48 ? 0.5 : d <= 96 ? 0.25 : 0.0;
	}

	/** Screen effect strength of a remote caster (1.4 "Distance rule"): 0.5 x clamp((24 - d) / 16, 0, 1). */
	public static double screenStrength(double d) {
		return 0.5 * clamp((24 - d) / 16.0);
	}
}
