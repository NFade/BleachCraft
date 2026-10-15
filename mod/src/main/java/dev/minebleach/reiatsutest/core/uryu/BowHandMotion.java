package dev.minebleach.reiatsutest.core.uryu;

/**
 * Clock of the draw hand of the first person bow (R12): how far the right hand has come to the string ({@link #blend}, 0 = resting like
 * an empty vanilla hand, 1 = on the nock) and the draw progress it follows ({@link #pull}, 0..1, the same curve as the string).
 * While the bow is drawn the hand comes to the string in {@code readyIn} seconds and follows the live draw; on the release it snaps
 * forward to the resting nock in {@code snap} seconds (ease out), and only then relaxes back to the resting place in {@code readyOut}
 * seconds. Pure state machine: the client feeds it once per frame.
 */
public final class BowHandMotion {
	private float blend;
	private double pull;
	private double last;
	private boolean wasUsing;
	private float snapT = -1f;

	/**
	 * Advances the clock by {@code dt} seconds. {@code using} = the bow is being drawn this frame, {@code drawPull} = its draw progress
	 * (ignored when not using).
	 */
	public void step(float dt, boolean using, double drawPull, float readyIn, float readyOut, float snap) {
		dt = Math.max(0f, dt);
		if (using) {
			wasUsing = true;
			snapT = -1f;
			last = clamp01(drawPull);
			pull = last;
			blend = Math.min(1f, blend + dt / Math.max(1.0e-4f, readyIn));
			return;
		}
		if (wasUsing) {
			wasUsing = false;
			snapT = 0f; // the release: the hand snaps forward from where it was
		}
		if (snapT >= 0f) {
			snapT += dt;
			float k = Math.min(1f, snapT / Math.max(1.0e-4f, snap));
			float e = 1f - (1f - k) * (1f - k) * (1f - k);
			pull = last * (1f - e);
			if (k >= 1f) {
				snapT = -1f;
				pull = 0.0;
			}
			return;
		}
		pull = 0.0;
		blend = Math.max(0f, blend - dt / Math.max(1.0e-4f, readyOut));
	}

	/** 0 = the hand rests, 1 = on the string. */
	public float blend() {
		return blend;
	}

	/** Draw progress 0..1 the hand follows (drops to 0 during the snap after the release). */
	public double pull() {
		return pull;
	}

	/** True during the forward snap after a release. */
	public boolean snapping() {
		return snapT >= 0f;
	}

	/** Forgets everything (the bow was put away). */
	public void reset() {
		blend = 0f;
		pull = 0.0;
		last = 0.0;
		wasUsing = false;
		snapT = -1f;
	}

	private static double clamp01(double v) {
		return v < 0.0 ? 0.0 : Math.min(1.0, v);
	}
}
