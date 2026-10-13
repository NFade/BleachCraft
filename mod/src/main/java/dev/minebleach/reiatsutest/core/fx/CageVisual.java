package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.state.AbilityParams;
import dev.minebleach.reiatsutest.core.state.AbilitySpec;
import java.util.List;

/**
 * The numbers behind the Byakuya petal cage effect (r8 fx2 item 2): the area, the pulse times and the telegraph, all read from the
 * event params the server builds from the ability spec ({@code CastContext.effectParams}: {@code [cx, cy, cz, radius, firstPulseSeconds,
 * pulseCount, pulseSpacingSeconds]}), so the picture matches the real hit sphere and the real hit moments. Nothing here changes a
 * gameplay number; {@link #paramsOf(AbilitySpec, double, double, double)} only repeats the server formula for the tests.
 *
 * <p>The server hit test is a sphere of {@code radius} around the centre against the closest point of the victim's bounding box, so a
 * creature standing on the ground is inside the area up to {@link #groundRingRadius} from the vertical axis of the centre.
 */
public final class CageVisual {
	/** Sensible bounds for params from an old or broken server. */
	public static final double MIN_RADIUS = 0.8;
	public static final int MAX_PULSES = 12;
	public static final double MIN_SPACING = 0.1;

	private CageVisual() {
	}

	/** The cage of one event: centre, radius and the pulse schedule in seconds from the event. */
	public record Plan(double cx, double cy, double cz, double radius, double first, int pulses, double spacing) {
		/** Time of the k-th pulse (0 based) in seconds from the event. */
		public double pulseTime(int k) {
			return first + k * spacing;
		}

		/** Time of the last pulse. */
		public double lastPulse() {
			return pulseTime(pulses - 1);
		}
	}

	/**
	 * Reads the event params; a missing centre falls back to {@code (fx, fy, fz)} (the event position), other missing numbers to the
	 * defaults of the spec at the time of writing (radius 5, first pulse 0.6 s, 6 pulses, 0.5 s apart). Values are clamped to sane bounds.
	 */
	public static Plan parse(float[] params, double fx, double fy, double fz) {
		boolean hasCentre = params != null && params.length >= 3;
		double cx = hasCentre ? params[0] : fx;
		double cy = hasCentre ? params[1] : fy;
		double cz = hasCentre ? params[2] : fz;
		double radius = Math.max(MIN_RADIUS, RukiaFxParams.param(params, 3, 5.0));
		double first = Math.max(0.1, RukiaFxParams.param(params, 4, 0.6));
		int pulses = (int) Math.max(1, Math.min(MAX_PULSES, Math.round(RukiaFxParams.param(params, 5, 6))));
		double spacing = Math.max(MIN_SPACING, RukiaFxParams.param(params, 6, 0.5));
		return new Plan(cx, cy, cz, radius, first, pulses, spacing);
	}

	/** The params the server sends for the cage, from the ability spec: the formula of {@code CastContext.effectParams}. */
	public static float[] paramsOf(AbilitySpec spec, double cx, double cy, double cz) {
		List<Integer> offs = spec.phaseOffsets();
		float first = offs.isEmpty() ? 0f : offs.get(0) / 20f;
		float spacing = offs.size() < 2 ? 0.5f : (offs.get(1) - offs.get(0)) / 20f;
		return new float[] {(float) cx, (float) cy, (float) cz, spec.floatNum(AbilityParams.RADIUS), first, offs.size(), spacing};
	}

	/** All pulse times of the plan. */
	public static double[] pulseTimes(Plan p) {
		double[] t = new double[p.pulses()];
		for (int k = 0; k < t.length; k++) {
			t[k] = p.pulseTime(k);
		}
		return t;
	}

	/**
	 * Radius from the vertical axis of the centre inside which a creature standing on the ground is hit: the sphere reaches the body
	 * (a box {@code bodyHeight} tall) at {@code sqrt(radius^2 - dy^2)} where {@code dy} is how far the centre is above the head; plus the
	 * half width {@code slack} of the box. 0 when the sphere floats too high to reach the ground at all.
	 */
	public static double groundRingRadius(double radius, double centreAboveGround, double bodyHeight, double slack) {
		double dy = Math.max(0.0, centreAboveGround - bodyHeight);
		if (dy >= radius) {
			return 0.0;
		}
		return Math.sqrt(radius * radius - dy * dy) + Math.max(0.0, slack);
	}

	/** Scale of the visuals of pulse {@code k} of {@code n}: the first is a little bigger, the last is the final burst (1.8 times). */
	public static double pulseScale(int k, int n) {
		if (k >= n - 1) {
			return 1.8;
		}
		return k == 0 ? 1.2 : 1.0;
	}

	/** Time of the warning tick before pulse {@code k}: {@code lead} seconds ahead, never before the previous pulse nor before 0.05 s. */
	public static double warnTime(Plan p, int k, double lead) {
		double t = p.pulseTime(k) - Math.max(0.0, lead);
		double floor = k == 0 ? 0.05 : p.pulseTime(k - 1) + 0.05;
		return Math.max(floor, t);
	}

	/** Petals converging on the area in one wave: the area law against the reference radius 5, bounded to 0.5x to 2.2x of {@code base}. */
	public static int convergeCount(int base, double radius) {
		double k = radius / 5.0;
		double area = Math.max(0.5, Math.min(2.2, k * k));
		return (int) Math.round(base * area);
	}

	/**
	 * Speed (blocks per second) of a converging petal that starts {@code distance} from the centre so that it arrives exactly when the
	 * first pulse hits ({@code arrival} seconds from its spawn). The petal flies without drag.
	 */
	public static double convergeSpeed(double distance, double arrival) {
		return arrival <= 1e-6 ? 0.0 : distance / arrival;
	}

	/** Radius of the shockwave ring of a pulse {@code age} seconds after the hit: eases out from 0.5 to {@code radius} in {@code life}. */
	public static double shockRadius(double radius, double age, double life) {
		return BlockFxMath.ringRadius(0.5, radius, age, life);
	}
}
