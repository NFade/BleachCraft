package dev.minebleach.reiatsutest.core.fx;

/** Small closed-form curves of the Byakuya bankai release (VFX_STORYBOARD 6.1): hilt drop, ripples, fault lines, light sweep. */
public final class BankaiCurves {
	public static final double DROP_TIME = 0.18;
	public static final double SINK_START = 0.18;
	public static final double SINK_TIME = 0.5;
	public static final double SINK_DEPTH = 0.5;
	public static final double FAULT_START = 0.9;
	public static final double SWEEP_SPEED = 40.0;
	public static final double SWEEP_WIDTH = 3.0;

	private BankaiCurves() {
	}

	private static double clamp(double x) {
		return x < 0 ? 0 : Math.min(x, 1);
	}

	/** Height of the ground hilt above its rest point: falls from hand height (1.1 above the ground) in 0.18 s (easeInQuad), then sinks. */
	public static double hiltHeight(double t, double handHeight) {
		if (t < 0) {
			return handHeight;
		}
		if (t < DROP_TIME) {
			double x = t / DROP_TIME;
			return handHeight * (1 - x * x);
		}
		double x = clamp((t - SINK_START) / SINK_TIME);
		double oc = 1 - (1 - x) * (1 - x) * (1 - x);
		return -SINK_DEPTH * oc;
	}

	/** Radius of ripple k (0..2): start times 0.18, 0.40, 0.62; max radius 6, 4.5, 3; easeOutCubic over 1.0 s. */
	public static double rippleRadius(int k, double t) {
		double start = k == 0 ? 0.18 : k == 1 ? 0.40 : 0.62;
		double max = k == 0 ? 6.0 : k == 1 ? 4.5 : 3.0;
		double x = clamp((t - start) / 1.0);
		return max * (1 - (1 - x) * (1 - x) * (1 - x));
	}

	/** Ripple alpha 1 to 0. */
	public static double rippleAlpha(int k, double t) {
		double start = k == 0 ? 0.18 : k == 1 ? 0.40 : 0.62;
		if (t < start) {
			return 0;
		}
		return 1 - clamp((t - start) / 1.0);
	}

	/** Time at which the fault line passes a blade at distance d from the release point (the line runs a little ahead of the eruption). */
	public static double faultTime(double dist, double speed) {
		return FAULT_START + dist / speed;
	}

	/** Brightness bonus (0..0.35) of the moonlight sweep at a blade of distance d, t seconds from the release, for rows complete at {@code completion}. */
	public static double sweepBoost(double dist, double t, double completion) {
		double front = (t - completion) * SWEEP_SPEED;
		if (front < -SWEEP_WIDTH) {
			return 0;
		}
		double x = Math.abs(dist - front) / (SWEEP_WIDTH * 0.5);
		return x >= 1 ? 0 : 0.35 * (1 - x * x);
	}

	/** Breathing of the tip emissive while the rows stand: 0.8 to 1.0 at 0.4 Hz. */
	public static double tipBreath(double t) {
		return 0.9 + 0.1 * Math.sin(2 * Math.PI * 0.4 * t);
	}

	/** Night grade strength 0..1 of the first release moments: 0 before 0.3 s, ease in-out to 1 at 0.9 s. */
	public static double gradeRamp(double t) {
		double x = clamp((t - 0.3) / 0.6);
		return -(Math.cos(Math.PI * x) - 1) / 2;
	}
}
