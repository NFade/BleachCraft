package dev.minebleach.reiatsutest.core.fx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * R11 T7: the vertical interference stripes of the reiatsu pressure as a pure, deterministic layout: (seed, time, strength, screen width in
 * pixels) gives the same list of rectangles on every call. The client only draws it. The layout is renewed {@link #SLOTS_PER_SECOND} times a
 * second, but every stripe fades in and out inside its slot ({@link #slotFade}), so the brightness never jumps: the picture changes
 * position about twelve times a second while the luminance moves softly. The covered area is tiny ({@link #MAX_COVERAGE}), so it is not a
 * flash in the photosensitivity sense; the client also drops the stripes entirely under reduce motion and photosensitive safe
 * ({@link PressurePlan}).
 */
public final class PressureStripes {
	/** The layout changes this many times a second. */
	public static final int SLOTS_PER_SECOND = 12;
	public static final int MIN_COUNT = 6;
	public static final int MAX_COUNT = 14;
	/** Width of a stripe in screen pixels. */
	public static final int MIN_WIDTH_PX = 2;
	public static final int MAX_WIDTH_PX = 6;
	/** Alpha of a stripe: 0.10 between the pulses to 0.30 on a pulse (times the strength and the envelope). */
	public static final double ALPHA_MIN = 0.10;
	public static final double ALPHA_MAX = 0.30;
	/** Upper bound of {@link #coverage} of one layout on a window at least 640 px wide: 14 stripes of 6 px at alpha 0.30 is 0.039. */
	public static final double MAX_COVERAGE = 0.04;
	/** Fraction of the stripes that run over the whole height. */
	public static final double FULL_HEIGHT_SHARE = 0.45;

	public static final int TONE_DARK = 0;
	public static final int TONE_LIGHT = 1;
	public static final int TONE_TINT = 2;

	/** One stripe: the rectangle as fractions of the screen (0..1, y down), the tone and the alpha (already faded inside its slot). */
	public record Stripe(double x0, double x1, double y0, double y1, int tone, double alpha) {
		public double width(int screenWidthPx) {
			return (x1 - x0) * screenWidthPx;
		}
	}

	private PressureStripes() {
	}

	/** Index of the layout slot at {@code time} seconds (changes {@link #SLOTS_PER_SECOND} times a second). */
	public static int slot(double time) {
		return (int) Math.floor(time * SLOTS_PER_SECOND);
	}

	/** 0..1 soft in/out inside a slot: a stripe is invisible at both ends of its slot, so no step in brightness. */
	public static double slotFade(double time) {
		double phase = time * SLOTS_PER_SECOND - Math.floor(time * SLOTS_PER_SECOND);
		double in = smooth(phase / 0.3);
		double out = smooth((1.0 - phase) / 0.3);
		return Math.min(in, out);
	}

	/** Number of stripes for a strength: 6 at the weakest visible, 14 at full. */
	public static int count(double strength) {
		double k = clamp01(strength);
		return MIN_COUNT + (int) Math.round((MAX_COUNT - MIN_COUNT) * k);
	}

	/**
	 * The stripes at {@code time} seconds after the release. Empty before the release, after {@link PressureMath#totalTime()} and for a
	 * strength under 0.01. {@code screenWidthPx} (the real framebuffer width) fixes the widths in whole pixels.
	 */
	public static List<Stripe> layout(long seed, double time, double strength, int screenWidthPx) {
		if (!(strength >= 0.01) || time <= 0 || time >= PressureMath.totalTime() || screenWidthPx < 16) {
			return Collections.emptyList();
		}
		double k = clamp01(strength);
		double env = PressureMath.envelope(time);
		double a = k * env * (ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * PressureMath.pulse(time)) * slotFade(time);
		if (!(a > 0.0)) {
			return Collections.emptyList();
		}
		int slot = slot(time);
		long s = FxHash.mix(seed ^ 0x5742495045L);
		int n = count(k);
		List<Stripe> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int w = MIN_WIDTH_PX + (int) Math.floor(FxHash.unit(s, slot * 64 + i, 1) * (MAX_WIDTH_PX - MIN_WIDTH_PX + 1));
			w = Math.min(MAX_WIDTH_PX, w);
			int xpx = (int) Math.floor(FxHash.unit(s, slot * 64 + i, 2) * (screenWidthPx - w + 1));
			xpx = Math.max(0, Math.min(screenWidthPx - w, xpx));
			double y0 = 0.0;
			double y1 = 1.0;
			if (FxHash.unit(s, slot * 64 + i, 3) >= FULL_HEIGHT_SHARE) {
				double h = 0.15 + 0.45 * FxHash.unit(s, slot * 64 + i, 4);
				y0 = (1.0 - h) * FxHash.unit(s, slot * 64 + i, 5);
				y1 = y0 + h;
			}
			double t = FxHash.unit(s, slot * 64 + i, 6);
			int tone = t < 0.35 ? TONE_DARK : t < 0.65 ? TONE_LIGHT : TONE_TINT;
			double jitter = 0.8 + 0.2 * FxHash.unit(s, slot * 64 + i, 7);
			out.add(new Stripe((double) xpx / screenWidthPx, (double) (xpx + w) / screenWidthPx, y0, y1, tone, a * jitter));
		}
		return out;
	}

	/** Sum over the stripes of (width fraction x height fraction x alpha): how much of the screen brightness one layout can move. */
	public static double coverage(List<Stripe> stripes) {
		double sum = 0;
		for (Stripe s : stripes) {
			sum += (s.x1() - s.x0()) * (s.y1() - s.y0()) * s.alpha();
		}
		return sum;
	}

	private static double smooth(double x) {
		double c = clamp01(x);
		return c * c * (3 - 2 * c);
	}

	private static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}
}
