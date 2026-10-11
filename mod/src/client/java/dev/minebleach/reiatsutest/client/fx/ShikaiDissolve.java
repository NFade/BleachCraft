package dev.minebleach.reiatsutest.client.fx;

import dev.minebleach.reiatsutest.core.fx.DissolveMath;
import java.util.HashMap;
import java.util.Map;

/**
 * Byakuya shikai release, part 2 (plan G-glow). Right after the pink hold ({@link ReleaseHold}) the drawn blade does not vanish: it keeps
 * a pink glow and breaks into petals from the top of the blade down to the hilt (the tip is up in the display transforms;
 * {@code release.dissolveTipFirst} 0 reverses it to hilt first), over
 * {@code release.dissolveTime} seconds. The item model asks this class for the progress and the colours; the geometry clip is in
 * {@code ObjItemBakedModel}. Clocked by the FX clock like the hold. {@code release.dissolveTime} 0 turns the dissolve off.
 */
public final class ShikaiDissolve {
	/** entity id -> {start (FX clock seconds), duration}. */
	private static final Map<Integer, double[]> RUNS = new HashMap<>();
	/** Harness statistics. */
	public static int started;

	private ShikaiDissolve() {
	}

	/** Starts the dissolve {@code delay} seconds from now (the end of the pink hold). */
	public static void start(int entityId, double delay) {
		double dur = FxTune.d("release.dissolveTime", 0.6);
		if (dur <= 0.01) {
			return;
		}
		RUNS.put(entityId, new double[] {FxClock.now + Math.max(0, delay), dur});
		started++;
	}

	public static void clear() {
		RUNS.clear();
	}

	public static boolean tipFirst() {
		return FxTune.d("release.dissolveTipFirst", 1.0) > 0.5;
	}

	/** 0..1 through the dissolve, or -1 before it starts and after it ends (the entry is then removed). */
	public static double progress(int entityId) {
		double[] r = RUNS.get(entityId);
		if (r == null) {
			return -1;
		}
		double p = (FxClock.now - r[0]) / r[1];
		if (p >= 1.0) {
			RUNS.remove(entityId);
			return -1;
		}
		return p < 0 ? -1 : p;
	}

	public static boolean active(int entityId) {
		return progress(entityId) >= 0;
	}

	/** Colour multiplier of the blade that is still there: the pink of the hold ({@code release.pinkColor}), ARGB. */
	public static int bladeTint() {
		return argb(FxMath.hex(FxTune.s("release.pinkColor", "#FF6EC0")), 0.0, 1.0);
	}

	/** Colour multiplier of the hilt: pink at the start, back to white (its own colour) at the end. */
	public static int hiltTint(double p) {
		float[] pink = FxMath.hex(FxTune.s("release.pinkColor", "#FF6EC0"));
		return argb(pink, DissolveMath.hiltMix(p), 1.0);
	}

	/** Colour of the glow shell over the remaining blade: pink with the alpha of the dissolve curve times {@code release.dissolveGlow}. */
	public static int shellArgb(double p) {
		double a = DissolveMath.shellAlpha(p) * FxTune.d("release.dissolveGlow", 0.45);
		float[] pink = FxMath.hex(FxTune.s("release.pinkColor", "#FF6EC0"));
		int alpha = (int) (255 * Math.max(0, Math.min(1, a)));
		return (alpha << 24) | (argb(pink, 0.0, 1.0) & 0x00FFFFFF);
	}

	/** ARGB with alpha 255: {@code colour} lerped towards white by {@code whiteMix}, then scaled by {@code gain}. */
	private static int argb(float[] colour, double whiteMix, double gain) {
		int r = channel(colour[0], whiteMix, gain);
		int g = channel(colour[1], whiteMix, gain);
		int b = channel(colour[2], whiteMix, gain);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int channel(float c, double whiteMix, double gain) {
		double v = (c + (1.0 - c) * whiteMix) * gain;
		return (int) (255 * Math.max(0, Math.min(1, v)));
	}
}
