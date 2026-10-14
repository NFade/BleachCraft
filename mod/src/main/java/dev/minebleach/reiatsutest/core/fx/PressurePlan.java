package dev.minebleach.reiatsutest.core.fx;

/**
 * R11 T7: which layers of the reiatsu pressure may run for one event, as a pure function of the strength, the four tuning keys
 * ({@code pressure.shake/blur/stripes/scale}) and the two accessibility options. The client draws only what this plan allows.
 * <ul>
 * <li>{@code photosensitiveSafe}: no stripes, no blur, no tear, no shake (the soft vignette and grade stay).</li>
 * <li>{@code reduceMotion}: no shake, no tremor, no stripes, no tear (a still soft blur stays).</li>
 * </ul>
 *
 * @param strength    0..1 strength of the pressure (distance strength x {@code pressure.scale}), 0 when the whole effect is off
 * @param shakeDeg    amplitude of one pulse shake in degrees (0 when off)
 * @param tremorDeg   amplitude of the continuous tremor in degrees (0 when off)
 * @param blurScale   0..? multiplier of {@link PressureMath#blurPx} (0 when off)
 * @param stripes     strength handed to {@link PressureStripes#layout} (0 when off)
 * @param tearScale   0 or 1: whether the horizontal tear of the pulses may run (needs the stripes key, because the tear is the stripes' companion)
 */
public record PressurePlan(double strength, double shakeDeg, double tremorDeg, double blurScale, double stripes, double tearScale) {
	/** The shipped tuning keys. */
	public static final double DEFAULT_SHAKE = PressureMath.PULSE_SHAKE_DEG;
	public static final double DEFAULT_BLUR = 1.0;
	public static final double DEFAULT_STRIPES = 1.0;
	public static final double DEFAULT_SCALE = 1.0;

	public static final PressurePlan OFF = new PressurePlan(0, 0, 0, 0, 0, 0);

	/**
	 * @param distanceStrength {@link PressureMath#strength} of the distance (0..1)
	 * @param scale            {@code pressure.scale}
	 * @param screenFxScale    the client option of the same name (blur and stripes follow it; the shake follows its own {@code shakeScale} in the screen layer)
	 * @param shakeKey         {@code pressure.shake}: degrees of one pulse (default 0.9)
	 * @param blurKey          {@code pressure.blur}: multiplier (default 1)
	 * @param stripesKey       {@code pressure.stripes}: multiplier of the stripes strength (default 1)
	 */
	public static PressurePlan of(double distanceStrength, double scale, double screenFxScale, double shakeKey, double blurKey,
			double stripesKey, boolean reduceMotion, boolean photosensitiveSafe) {
		double k = clamp01(distanceStrength * Math.max(0.0, scale));
		if (!(k >= 0.03)) {
			return OFF;
		}
		double fxScale = Math.max(0.0, screenFxScale);
		boolean still = reduceMotion || photosensitiveSafe;
		double shake = still ? 0.0 : Math.min(3.0, Math.max(0.0, shakeKey));
		double tremor = still ? 0.0 : PressureMath.TREMOR_DEG * (tremorRatio(shakeKey));
		double blur = photosensitiveSafe ? 0.0 : Math.min(3.0, Math.max(0.0, blurKey)) * fxScale;
		double stripes = still ? 0.0 : clamp01(k * Math.max(0.0, stripesKey) * fxScale);
		double tear = stripes > 0.0 ? 1.0 : 0.0;
		return new PressurePlan(k, shake, tremor, blur, stripes, tear);
	}

	/** The tremor follows the pulse shake key (0.9 -> 1.0), so {@code pressure.shake 0} also silences the tremor. */
	private static double tremorRatio(double shakeKey) {
		return Math.max(0.0, shakeKey) / DEFAULT_SHAKE;
	}

	public boolean any() {
		return shakeDeg > 0 || tremorDeg > 0 || blurScale > 0 || stripes > 0;
	}

	private static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}
}
