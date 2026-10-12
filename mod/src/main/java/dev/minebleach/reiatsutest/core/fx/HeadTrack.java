package dev.minebleach.reiatsutest.core.fx;

/**
 * Recorded path of the attack swarm head (world positions at swarm time stamps), filled from the server's head packets and read by
 * {@link SwarmMath} so that the blade ribbon follows the server flight. Linear interpolation between samples, clamped to the ends.
 * Pure data, no Minecraft types.
 */
public final class HeadTrack {
	private static final int CAP = 256;

	private final double[] t = new double[CAP];
	private final double[] x = new double[CAP];
	private final double[] y = new double[CAP];
	private final double[] z = new double[CAP];
	private int n;
	private boolean closed;
	private double endTime;

	public int size() {
		return n;
	}

	public double startTime() {
		return n == 0 ? 0.0 : t[0];
	}

	public double lastTime() {
		return n == 0 ? 0.0 : t[n - 1];
	}

	/** Adds a sample; times must grow (an equal or older time is moved just after the last one). Ignored once closed. */
	public void add(double time, double px, double py, double pz) {
		if (closed) {
			return;
		}
		if (n > 0 && time <= t[n - 1]) {
			time = t[n - 1] + 1.0E-4;
		}
		if (n == CAP) {
			System.arraycopy(t, 1, t, 0, CAP - 1);
			System.arraycopy(x, 1, x, 0, CAP - 1);
			System.arraycopy(y, 1, y, 0, CAP - 1);
			System.arraycopy(z, 1, z, 0, CAP - 1);
			n--;
		}
		t[n] = time;
		x[n] = px;
		y[n] = py;
		z[n] = pz;
		n++;
	}

	/** The head reached the caster again (or the flight was cut) at {@code time}. */
	public void close(double time) {
		if (!closed) {
			closed = true;
			endTime = n == 0 ? time : Math.max(time, t[n - 1]);
		}
	}

	public boolean closed() {
		return closed;
	}

	public double endTime() {
		return endTime;
	}

	private int segment(double time) {
		// index k with t[k] <= time < t[k+1], clamped to [0, n - 2]
		int lo = 0;
		int hi = n - 2;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (t[mid] <= time) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	/** Position at {@code time}; false when there are no samples. */
	public boolean at(double time, double[] out) {
		if (n == 0) {
			return false;
		}
		if (n == 1 || time <= t[0]) {
			out[0] = x[0];
			out[1] = y[0];
			out[2] = z[0];
			return true;
		}
		if (time >= t[n - 1]) {
			out[0] = x[n - 1];
			out[1] = y[n - 1];
			out[2] = z[n - 1];
			return true;
		}
		int k = segment(time);
		double u = (time - t[k]) / (t[k + 1] - t[k]);
		out[0] = x[k] + (x[k + 1] - x[k]) * u;
		out[1] = y[k] + (y[k + 1] - y[k]) * u;
		out[2] = z[k] + (z[k + 1] - z[k]) * u;
		return true;
	}

	/** Speed in blocks per second of the segment around {@code time}. */
	public double speedAt(double time) {
		if (n < 2) {
			return 0.0;
		}
		int k = segment(Math.max(t[0], Math.min(t[n - 1], time)));
		double dt = t[k + 1] - t[k];
		double dx = x[k + 1] - x[k];
		double dy = y[k + 1] - y[k];
		double dz = z[k + 1] - z[k];
		return Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
	}

	/** Unit direction of travel around {@code time}; false when the head is standing still. */
	public boolean dirAt(double time, double[] out) {
		if (n < 2) {
			return false;
		}
		int k = segment(Math.max(t[0], Math.min(t[n - 1], time)));
		double dx = x[k + 1] - x[k];
		double dy = y[k + 1] - y[k];
		double dz = z[k + 1] - z[k];
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0E-6) {
			return false;
		}
		out[0] = dx / l;
		out[1] = dy / l;
		out[2] = dz / l;
		return true;
	}
}
