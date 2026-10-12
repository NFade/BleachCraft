package dev.minebleach.reiatsutest.core.fx;

/**
 * Flight of the fragments of the Byakuya shikai dissolve (plan G-glow, round 2): every blade fragment that comes loose becomes a petal
 * that spirals up and sideways, tumbling, and is gone after its life. Closed form of (seed, fragment index, age): the model-space
 * fragments on the hand and the world-space petals of the same fragment index follow the same path, so one turns into the other
 * without a jump. Pure math, no Minecraft types. Distances are in blade lengths (the renderer multiplies by the blade length it
 * has: model units for the fragments in the hand, blocks for the petals).
 */
public final class DissolveSwirl {
	/** Layout of a parameter array (see {@link #params}). */
	public static final int PHI0 = 0;
	public static final int OMEGA = 1;
	public static final int RADIUS = 2;
	public static final int LIFT = 3;
	public static final int LIFE = 4;
	public static final int SIZE = 5;
	public static final int SPIN = 6;
	public static final int AX = 7;
	public static final int AY = 8;
	public static final int AZ = 9;
	public static final int PHASE = 10;
	public static final int COUNT = 11;

	private DissolveSwirl() {
	}

	/**
	 * Parameters of fragment {@code idx}. {@code spiral} scales the turning rate (1 = about one turn per second), {@code radius} and
	 * {@code lift} are in blade lengths (outward reach and upward speed per second), {@code life} in seconds. All draws are hashed
	 * from {@code (seed, idx)}, so the result does not depend on the order of the calls.
	 */
	public static void params(long seed, int idx, double spiral, double radius, double lift, double life, double[] out) {
		double r0 = FxHash.unit(seed, idx, 1);
		double r1 = FxHash.unit(seed, idx, 2);
		double r2 = FxHash.unit(seed, idx, 3);
		double r3 = FxHash.unit(seed, idx, 4);
		double r4 = FxHash.unit(seed, idx, 5);
		double r5 = FxHash.unit(seed, idx, 6);
		double dir = r5 < 0.85 ? 1.0 : -1.0; // most fragments turn the same way, a few against it
		out[PHI0] = r0 * Math.PI * 2;
		out[OMEGA] = dir * Math.PI * 2 * spiral * (0.75 + 0.9 * r1);
		out[RADIUS] = radius * (0.45 + 0.8 * r2);
		out[LIFT] = lift * (0.6 + 0.8 * r3);
		out[LIFE] = life * (0.75 + 0.5 * r4);
		out[SIZE] = 0.8 + 0.45 * FxHash.unit(seed, idx, 7);
		out[SPIN] = (3.0 + 7.0 * FxHash.unit(seed, idx, 8)) * (FxHash.unit(seed, idx, 9) < 0.5 ? -1 : 1);
		double ax = FxHash.signed(seed, idx, 10);
		double ay = FxHash.signed(seed, idx, 11);
		double az = FxHash.signed(seed, idx, 12);
		double l = Math.sqrt(ax * ax + ay * ay + az * az);
		if (l < 1e-6) {
			ax = 0;
			ay = 1;
			az = 0;
			l = 1;
		}
		out[AX] = ax / l;
		out[AY] = ay / l;
		out[AZ] = az / l;
		out[PHASE] = FxHash.unit(seed, idx, 13) * Math.PI * 2;
	}

	/**
	 * Offset of a fragment {@code tau} seconds after it came loose, in blade lengths: {@code out[0]} and {@code out[2]} across the
	 * blade axis (a spiral that opens up), {@code out[1]} along it (up). Zero at {@code tau} 0.
	 */
	public static void offset(double[] prm, double tau, double[] out) {
		double t = Math.max(0, tau);
		double u = Math.min(1.0, t / prm[LIFE]);
		double open = 1 - (1 - u) * (1 - u); // opens quickly, then slowly
		double r = prm[RADIUS] * open;
		double phi = prm[PHI0] + prm[OMEGA] * t;
		double h = prm[LIFT] * t * (1 - 0.45 * u);
		out[0] = r * Math.cos(phi);
		out[1] = h;
		out[2] = r * Math.sin(phi);
	}

	/** Size envelope 0..1: grows over {@code ramp} seconds, shrinks to a third over the last 30 percent of the life. */
	public static double sizeEnvelope(double[] prm, double tau, double ramp) {
		if (tau < 0) {
			return 0;
		}
		double grow = smooth(0, Math.max(1e-3, ramp), tau);
		double u = Math.min(1.0, tau / prm[LIFE]);
		return grow * (1 - 0.67 * smooth(0.7, 1.0, u));
	}

	/** Brightness/alpha envelope 0..1: full until {@code from} of the life, then gone at the end of it. */
	public static double alphaEnvelope(double[] prm, double tau, double from) {
		if (tau < 0) {
			return 0;
		}
		double u = Math.min(1.0, tau / prm[LIFE]);
		return 1 - smooth(from, 1.0, u);
	}

	/** Heat of a fresh fragment: 1 at the break, 0 after {@code cool} seconds (white hot to pink). */
	public static double heat(double tau, double cool) {
		if (tau < 0) {
			return 0;
		}
		return 1 - smooth(0, Math.max(1e-3, cool), tau);
	}

	/** Petals to use for a quality tier: about 110 at 0.25, 200 at 0.5, 300 at 1; scaled by {@code motionScale}; clamped to [20, min(400, cap)]. */
	public static int petalCount(double quality, double motionScale, int cap) {
		double q = Math.max(0.0, Math.min(1.0, quality));
		double base = q >= 0.5 ? 200 + 200 * (q - 0.5) : 110 + 360 * (q - 0.25);
		if (q < 0.25) {
			base = 110 * q / 0.25;
		}
		int n = (int) Math.round(base * motionScale);
		return Math.max(20, Math.min(Math.min(400, Math.max(20, cap)), n));
	}

	private static double smooth(double e0, double e1, double x) {
		double t = x <= e0 ? 0 : x >= e1 ? 1 : (x - e0) / (e1 - e0);
		return t * t * (3 - 2 * t);
	}
}
