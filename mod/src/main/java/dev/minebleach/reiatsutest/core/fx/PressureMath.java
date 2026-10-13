package dev.minebleach.reiatsutest.core.fx;

/**
 * The visual "reiatsu pressure" that other players see on their screen when somebody releases a bankai (anime reference: the world
 * darkens at the edges, the colour drains toward the caster's tint, the picture heaves with the heartbeat of the pressure). Only the
 * numbers live here, the screen layers are drawn by the client ({@code PressureFx}). Visual only, no gameplay effect.
 */
public final class PressureMath {
	/** Within this distance of the caster (blocks) the pressure is full. */
	public static final double NEAR = 8.0;
	/** Beyond this distance there is no pressure. */
	public static final double FAR = 48.0;
	/** Seconds: the vignette and the grade ramp in, are held, then fade out. */
	public static final double RAMP = 0.7;
	public static final double HOLD = 3.3;
	public static final double FADE = 2.0;
	/** Seconds after the release at which the screen heaves (the heartbeat of the pressure). */
	public static final double[] PULSES = {0.8, 1.7, 2.6, 3.5};

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

	/** Total seconds from the release to the end of the fade. */
	public static double totalTime() {
		return RAMP + HOLD + FADE;
	}
}
