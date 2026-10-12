package dev.minebleach.reiatsutest.core.combat;

/** Easing curves of the first person motion (input clamped to 0..1). */
public final class FpEase {
	private FpEase() {
	}

	public static float clamp01(float t) {
		return t < 0f ? 0f : Math.min(t, 1f);
	}

	public static float smooth(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Fast start, slow end (the wind-up and the follow-through). */
	public static float out(float t) {
		t = clamp01(t);
		return 1f - (1f - t) * (1f - t);
	}

	/** Slow start, fast end (the strike accelerates into the hit). */
	public static float in(float t) {
		t = clamp01(t);
		return t * t * (1f + 0.6f * (1f - t));
	}
}
