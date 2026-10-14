package dev.minebleach.reiatsutest.core.fx;

import java.util.Locale;

/**
 * Selectable layouts of the Byakuya bankai blade rows ({@code bankai.layout} in fx_override.json), R12 task 1.
 * <p>
 * The corridor numbers (half width, start) are the fit of the anime frame {@code refs/byakuya_bankai_3.png} ({@link BladeRowFit},
 * {@code tools/measure_bankai_rows.py}): the bases stand about 3.25 blocks either side of the axis, the nearest pair 0.85 blocks behind the feet,
 * the blades are 4.95 blocks tall against the 1.8 of Byakuya (the mesh is 5.5, scale 1.0 keeps "about 3 heights"). Only the density is a choice,
 * because the pitch needs the focal length of the shot, which one frame does not give (1.4 blocks at a 70 degree field of view, 2.1 at 50, 3.2 at 35):
 * {@link #DENSE} 1.0, {@link #BOOK} 1.5 (the default), {@link #ANIME} 2.1. The blades per side (24 / 20 / 15) make the row about 23 / 28 / 29 blocks long: the frame shows about 18 to 28 blades per side before they dissolve in the dark. Explicit {@code rows.*} keys still override every preset.
 */
public final class BladeRowPresets {
	private BladeRowPresets() {
	}

	/** {@code perSide}: blades per side at the High tier; {@code startAhead}: first blade relative to the feet along the look, negative = behind. */
	public record Preset(String key, int perSide, double halfWidth, double pitch, double startAhead) {
		/** Distance from the first to the last blade of a row, blocks. */
		public double length() {
			return pitch * (perSide - 1);
		}
	}

	public static final double MEASURED_HALF_WIDTH = 3.25;
	public static final double MEASURED_START_AHEAD = -0.85;

	/** Tight: pages of a book packed close, a long corridor. */
	public static final Preset DENSE = new Preset("dense", 24, MEASURED_HALF_WIDTH, 1.0, MEASURED_START_AHEAD);
	/** Between the two; the default. */
	public static final Preset BOOK = new Preset("book", 20, MEASURED_HALF_WIDTH, 1.5, MEASURED_START_AHEAD);
	/** The pitch of the frame at a 50 degree field of view (the R9 numbers were nearly this). */
	public static final Preset ANIME = new Preset("anime", 15, MEASURED_HALF_WIDTH, 2.1, MEASURED_START_AHEAD);
	/** r7: the lane that was in the game before R12 (7 blocks wide, first blade 2 behind the feet). */
	public static final Preset R7 = new Preset("r7", BladeRowLayout.DEFAULT_PER_SIDE, BladeRowLayout.DEFAULT_HALF_WIDTH, BladeRowLayout.DEFAULT_PITCH, BladeRowLayout.DEFAULT_START_AHEAD);
	/** R9 measurement kept under its old key. */
	public static final Preset R9 = new Preset(BladeRowLayout.LAYOUT_R9, BladeRowLayout.R9_PER_SIDE, BladeRowLayout.R9_HALF_WIDTH, BladeRowLayout.R9_PITCH, BladeRowLayout.R9_START_AHEAD);

	public static final Preset DEFAULT = BOOK;

	private static final Preset[] ALL = {DENSE, BOOK, ANIME, R7, R9};

	/** Preset for a {@code bankai.layout} value; empty or unknown gives {@link #DEFAULT}. */
	public static Preset byKey(String key) {
		if (key != null) {
			String k = key.trim().toLowerCase(Locale.ROOT);
			for (Preset p : ALL) {
				if (p.key().equals(k)) {
					return p;
				}
			}
		}
		return DEFAULT;
	}

	public static Preset[] all() {
		return ALL.clone();
	}
}
