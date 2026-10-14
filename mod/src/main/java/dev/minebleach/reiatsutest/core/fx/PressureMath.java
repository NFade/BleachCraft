package dev.minebleach.reiatsutest.core.fx;

/**
 * The visual "reiatsu pressure" that other players see on their screen when somebody releases a bankai (anime reference: the world
 * darkens at the edges, the colour drains toward the caster's tint, the picture heaves with the heartbeat of the pressure, shakes,
 * blurs and is crossed by vertical interference stripes). Only the numbers live here, the screen layers are drawn by the client
 * ({@code PressureFx}). Visual only, no gameplay effect. R11 T7: stronger shake, the blur and the stripes, a floor of strength at
 * 20..30 blocks (the first version vanished there).
 */
public final class PressureMath {
	/** Within this distance of the caster (blocks) the pressure is full. */
	public static final double NEAR = 8.0;
	/** Beyond this distance there is no pressure. */
	public static final double FAR = 48.0;
	/** Up to this distance the strength never drops below {@link #MID_FLOOR}, beyond it the strength falls to 0 at {@link #FAR}. */
	public static final double MID_END = 30.0;
	/** Minimum strength at medium range (20..30 blocks): the effect must stay visible there. */
	public static final double MID_FLOOR = 0.60;
	/** Seconds: the vignette and the grade ramp in, are held, then fade out. */
	public static final double RAMP = 0.7;
	public static final double HOLD = 3.3;
	public static final double FADE = 2.0;
	/** Seconds after the release at which the screen heaves (the heartbeat of the pressure). */
	public static final double[] PULSES = {0.8, 1.7, 2.6, 3.5};
	/** Shake of one pulse in degrees (was 0.35) and how long it rings, seconds. */
	public static final double PULSE_SHAKE_DEG = 0.9;
	public static final double PULSE_SHAKE_SECONDS = 0.45;
	/** Quiet continuous tremor of the view in degrees during the hold. */
	public static final double TREMOR_DEG = 0.15;
	/** Blur radius in screen pixels at full strength on a pulse. */
	public static final double BLUR_MAX_PX = 2.0;
	/** Horizontal tear of the picture on a pulse, pixels (grows with strength). */
	public static final double TEAR_MIN_PX = 2.0;
	public static final double TEAR_MAX_PX = 4.0;
	/** Pulse envelope: rise and exponential decay time constant, seconds. */
	public static final double PULSE_RISE = 0.06;
	public static final double PULSE_DECAY = 0.25;

	private PressureMath() {
	}

	/** 0..1 strength of the pressure at a distance in blocks: 1 up to {@link #NEAR}, smooth falloff to 0 at {@link #FAR}. */
	public static double intensity(double distance) {
		if (!(distance > NEAR)) {
			return 1.0;
		}
		if (distance >= FAR) {
			return 0.0;
		}
		double x = (distance - NEAR) / (FAR - NEAR);
		double s = x * x * (3 - 2 * x);
		return 1.0 - s;
	}

	/**
	 * The strength the screen layers use: {@link #intensity} but never below {@link #MID_FLOOR} up to {@link #MID_END} blocks, then a smooth
	 * fall to 0 at {@link #FAR} (so 20..30 blocks away the pressure is still clearly visible). Monotone non-increasing.
	 */
	public static double strength(double distance) {
		double base = intensity(distance);
		if (!(distance < FAR)) {
			return 0.0;
		}
		double floor = MID_FLOOR;
		if (distance > MID_END) {
			double x = (distance - MID_END) / (FAR - MID_END);
			floor = MID_FLOOR * (1.0 - x * x * (3 - 2 * x));
		}
		return Math.max(base, floor);
	}

	/** Total seconds from the release to the end of the fade. */
	public static double totalTime() {
		return RAMP + HOLD + FADE;
	}

	/** Overall 0..1 envelope at {@code t} seconds after the release: smooth ramp in, hold, smooth fade out, 0 outside. */
	public static double envelope(double t) {
		if (t <= 0 || t >= totalTime()) {
			return 0.0;
		}
		if (t < RAMP) {
			return smooth(t / RAMP);
		}
		if (t <= RAMP + HOLD) {
			return 1.0;
		}
		return 1.0 - smooth((t - RAMP - HOLD) / FADE);
	}

	/** 0..1 heartbeat at {@code t}: sharp rise ({@link #PULSE_RISE}) then an exponential decay after each of {@link #PULSES}, the maximum of them. */
	public static double pulse(double t) {
		double best = 0.0;
		for (double p : PULSES) {
			double d = t - p;
			if (d < 0) {
				continue;
			}
			double v = d < PULSE_RISE ? d / PULSE_RISE : Math.exp(-(d - PULSE_RISE) / PULSE_DECAY);
			best = Math.max(best, v);
		}
		return best;
	}

	/** Blur radius in pixels at time {@code t} for strength {@code k} (0..1): grows with k, the envelope and the pulse; at most {@link #BLUR_MAX_PX}. */
	public static double blurPx(double t, double k) {
		double kk = clamp01(k);
		return BLUR_MAX_PX * kk * envelope(t) * (0.5 + 0.5 * pulse(t));
	}

	/** Horizontal tear in pixels at time {@code t}: 0 between pulses, {@link #TEAR_MIN_PX}..{@link #TEAR_MAX_PX} while one is strong (pulse above 0.35; grows with k). */
	public static double tearPx(double t, double k) {
		double kk = clamp01(k);
		double p = pulse(t);
		if (p < 0.35 || kk <= 0.0) {
			return 0.0;
		}
		return (TEAR_MIN_PX + (TEAR_MAX_PX - TEAR_MIN_PX) * kk) * envelope(t);
	}

	/** The largest number of pulses inside any window of one second: the sharp brightness events per second (the limit is 3). */
	public static int maxPulsesPerSecond() {
		int best = 0;
		for (double start : PULSES) {
			int n = 0;
			for (double p : PULSES) {
				if (p >= start && p < start + 1.0) {
					n++;
				}
			}
			best = Math.max(best, n);
		}
		return best;
	}

	private static double smooth(double x) {
		double c = clamp01(x);
		return c * c * (3 - 2 * c);
	}

	private static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}
}
