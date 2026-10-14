package dev.minebleach.reiatsutest.core.fx;

/**
 * Closed-form curves of Shukei: Hakuteiken (VFX_STORYBOARD 6.3, id 33): wing fan, halo, blade and line, grade colours. Pure functions of
 * the time since the cast (seconds), so the client renderer and the late-join path read the same numbers. All times are in {@link Timing}
 * and can be overridden by the client ({@code FxTune}).
 */
public final class HakuteikenCurves {
	private HakuteikenCurves() {
	}

	/** Moments of the effect (defaults = the storyboard table). */
	public static final class Timing {
		/** Wings and halo bloom start, the fan takes {@code fanSpan} (ease out back), the halo grows over {@code haloSpan}. */
		public double wingStart = 0.50;
		public double fanSpan = 0.45;
		public double fanOvershoot = 1.1;
		public double haloSpan = 0.35;
		public double ignite = 0.45;
		/** Blade forms 1 to 4 m over [bladeStart, bladeStart + bladeSpan]. */
		public double bladeStart = 0.95;
		public double bladeSpan = 0.45;
		public double bladeMin = 1.0;
		public double bladeHeld = 4.0;
		/** Held breath: 0.1 s of stillness (feathers and ripple frozen). */
		public double breathStart = 1.40;
		public double breathSpan = 0.10;
		/** Strike (server line): the blade extends to L in {@code extend} seconds. */
		public double strike = 1.50;
		public double extend = 0.08;
		/** Burst at the line end (server). */
		public double burst = 1.80;
		/** The line vanishes right after the strike (R11 T8: strike + 0.10, gone in 0.08 s; was 2.20 / 0.30) and the blade shrinks back to 1 m. */
		public double lineFade = 1.60;
		public double lineFadeSpan = 0.08;
		/** The glow at the hand (WingHaloRenderer) keeps its old fade: it is not part of the line. */
		public double haloFade = 2.20;
		/** The wings and halo dissolve from the feather tips inward. */
		public double dissolveStart = 2.50;
		public double dissolveEnd = 3.00;
		/** The effect is over (rows re-form 3.0 to 4.2). */
		public double life = 4.2;
		/** Folded wing: all feathers at -12 degrees, length x0.15. */
		public double foldLength = 0.15;
	}

	private static double clamp(double x) {
		return x < 0 ? 0 : Math.min(x, 1);
	}

	private static double ob(double x, double c1) {
		x = clamp(x);
		double u = x - 1;
		return 1 + (c1 + 1) * u * u * u + c1 * u * u;
	}

	private static double oc(double x) {
		double u = 1 - clamp(x);
		return 1 - u * u * u;
	}

	private static double oe(double x) {
		x = clamp(x);
		return x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
	}

	private static double ios(double x) {
		return -(Math.cos(Math.PI * clamp(x)) - 1) / 2;
	}

	/** Fan factor of the wings: 0 folded, 1 the final fan (overshoots to about 1.1 with the ease out back), 0 before the bloom. */
	public static double fan(Timing tm, double t) {
		if (t < tm.wingStart) {
			return 0;
		}
		return ob((t - tm.wingStart) / tm.fanSpan, tm.fanOvershoot);
	}

	/** Halo scale 0.6 to 1.0 (ease out cubic over the bloom), then 1.0. */
	public static double haloScale(Timing tm, double t) {
		if (t < tm.wingStart) {
			return 0.6;
		}
		return 0.6 + 0.4 * oc((t - tm.wingStart) / tm.haloSpan);
	}

	/** Halo alpha 0 to 1 over the bloom; falls with the dissolve. */
	public static double haloAlpha(Timing tm, double t) {
		if (t < tm.wingStart) {
			return 0;
		}
		double in = oc((t - tm.wingStart) / tm.haloSpan);
		double out = 1.0 - ios((t - tm.dissolveStart) / Math.max(1e-3, tm.dissolveEnd - tm.dissolveStart));
		return in * out;
	}

	/** Reach of the dissolve as a fraction of the longest feather: 1.18 (nothing cut) down to 0; the tips go first. */
	public static double dissolveReach(Timing tm, double t) {
		if (t <= tm.dissolveStart) {
			return 1.18;
		}
		return 1.18 * (1.0 - ios((t - tm.dissolveStart) / Math.max(1e-3, tm.dissolveEnd - tm.dissolveStart)));
	}

	/** Alpha of a wing vertex at the radius fraction {@code rho} (r / 6.2 m) while the wings dissolve from the tips inward. */
	public static double wingAlpha(Timing tm, double t, double rho) {
		double x = (dissolveReach(tm, t) - rho) / 0.18;
		x = clamp(x);
		return x * x * (3 - 2 * x);
	}

	/** True while the wings exist at all (they are not drawn before the bloom or after the dissolve). */
	public static boolean wingsVisible(Timing tm, double t) {
		return t >= tm.wingStart && t < tm.dissolveEnd + 0.02;
	}

	/**
	 * Time seen by the feather ripple and the unfurl: stands still during the held breath {@code [breathStart, breathStart + breathSpan]}
	 * (the ripple clock does not advance).
	 */
	public static double rippleTime(Timing tm, double t) {
		if (t < tm.breathStart) {
			return t;
		}
		if (t < tm.breathStart + tm.breathSpan) {
			return tm.breathStart;
		}
		return t - tm.breathSpan;
	}

	/** Feather ripple: offset along the wing normal, {@code 0.08 sin(2 pi 1.2 t - 1.5 r)} with r the distance from the root divided by 6. */
	public static double ripple(double rippleTime, double distFromRoot) {
		return 0.08 * Math.sin(2 * Math.PI * 1.2 * rippleTime - 1.5 * (distFromRoot / 6.0));
	}

	/** Maximum radius of the wing meshes (the longest feather tip), used to normalise {@link #wingAlpha}. */
	public static final double WING_MAX_R = 6.2;

	/** Angle above horizontal in degrees of feather i (0 = top): 48, 40.5, ..., -12. */
	public static double featherAngleDeg(int i) {
		return 48.0 - 7.5 * i;
	}

	/** Length in metres of feather i: 6.0 for the top one, 7.5 percent of 6.0 less per feather, 2.4 for the last. */
	public static double featherLength(int i) {
		return 6.0 - 0.45 * i;
	}

	/**
	 * Fan deformation of a wing vertex of the exported mesh (local player frame, root at the origin, {@code side} = +1 for the left wing which
	 * extends along +x, -1 for the right one). The mesh is the fully fanned wing; {@code f} = 0 folds every feather to -12 degrees at
	 * {@code foldLength} of its length, 1 is the mesh itself. The forward curve {@code z} scales with the square of the radius like the model.
	 * Writes the new local position.
	 */
	public static void fanVertex(Timing tm, int side, double x, double y, double z, double f, double[] out) {
		double xs = side * x;
		double r = Math.sqrt(xs * xs + y * y);
		if (r < 1e-6) {
			out[0] = x;
			out[1] = y;
			out[2] = z;
			return;
		}
		double phi = Math.atan2(y, xs);
		double phi0 = Math.toRadians(-12.0);
		double phiN = phi0 + (phi - phi0) * f;
		double k = tm.foldLength + (1.0 - tm.foldLength) * f;
		double rn = r * k;
		out[0] = side * rn * Math.cos(phiN);
		out[1] = rn * Math.sin(phiN);
		out[2] = z * k * k;
	}

	// ------------------------------------------------------------------------------------------ blade and line

	/** Length of the blade body in metres: 0 before the blade forms, then 1 to 4 m, the strike extends it to {@code lineLen}, the fade shrinks it back to 1 m. */
	public static double bladeLength(Timing tm, double t, double lineLen) {
		if (t < tm.bladeStart) {
			return 0;
		}
		double grow = tm.bladeMin + (tm.bladeHeld - tm.bladeMin) * oc((t - tm.bladeStart) / tm.bladeSpan);
		if (t < tm.strike) {
			return grow * Math.min(1.0, (t - tm.bladeStart) / 0.06 + 0.2);
		}
		double extended = tm.bladeHeld + (lineLen - tm.bladeHeld) * oe((t - tm.strike) / tm.extend);
		if (t < tm.lineFade) {
			return extended;
		}
		double k = ios((t - tm.lineFade) / tm.lineFadeSpan);
		return Math.max(tm.bladeMin, extended + (tm.bladeMin - extended) * k);
	}

	/** Reach of the white line from the blade origin: 0 before the strike, then {@code lineLen} over {@code extend} (ease out expo). */
	public static double lineReach(Timing tm, double t, double lineLen) {
		if (t < tm.strike) {
			return 0;
		}
		return lineLen * oe((t - tm.strike) / tm.extend);
	}

	/** Brightness of the line: 1 from the strike, fading over {@code lineFadeSpan} from {@code lineFade}. */
	public static double lineAlpha(Timing tm, double t) {
		if (t < tm.strike) {
			return 0;
		}
		if (t < tm.lineFade) {
			return 1;
		}
		return 1.0 - ios((t - tm.lineFade) / tm.lineFadeSpan);
	}

	/** Strength of the blade glow strip pulse: 0.8 to 1.2 at 6 Hz. */
	public static double bladePulse(double t) {
		return 1.0 + 0.2 * Math.sin(2 * Math.PI * 6.0 * t);
	}

	// ------------------------------------------------------------------------------------------ grade

	private static final double[][] KEYS = {
			{0.00, -1, -1, -1},
			{0.40, 0x3C / 255.0, 0x48 / 255.0, 0x70 / 255.0},
			{1.00, 0x3C / 255.0, 0x48 / 255.0, 0x70 / 255.0},
			{1.30, 0x2E / 255.0, 0x38 / 255.0, 0x58 / 255.0},
			{1.40, 0x2E / 255.0, 0x38 / 255.0, 0x58 / 255.0},
			{1.50, 0x2A / 255.0, 0x33 / 255.0, 0x4F / 255.0},
			{2.20, 0x2A / 255.0, 0x33 / 255.0, 0x4F / 255.0},
			{3.50, -2, -2, -2}};

	/**
	 * Grade colour at time t: from the night grade of the rows ({@code base}) to {@code #3C4870} in 0.4 s, {@code #2E3858} at 1.0 to 1.3, a little
	 * darker at the held breath, and back to the night colour over 1.3 s from 2.2 (the keys with -1 / -2 take {@code base}).
	 */
	public static void gradeColour(double t, double[] base, double[] out) {
		for (int k = 0; k < KEYS.length - 1; k++) {
			double[] a = KEYS[k];
			double[] b = KEYS[k + 1];
			if (t <= b[0] || k == KEYS.length - 2) {
				double x = a[0] == b[0] ? 1.0 : clamp((t - a[0]) / (b[0] - a[0]));
				x = ios(x);
				for (int c = 0; c < 3; c++) {
					double va = a[1] < 0 ? base[c] : a[1 + c];
					double vb = b[1] < 0 ? base[c] : b[1 + c];
					out[c] = va + (vb - va) * x;
				}
				return;
			}
		}
	}
}
