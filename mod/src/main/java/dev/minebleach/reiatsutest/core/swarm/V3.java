package dev.minebleach.reiatsutest.core.swarm;

/** Immutable 3D vector for the pure blade flight logic (no Minecraft types in core/). */
public record V3(double x, double y, double z) {
	public static final V3 ZERO = new V3(0, 0, 0);

	public V3 add(V3 o) {
		return new V3(x + o.x, y + o.y, z + o.z);
	}

	public V3 sub(V3 o) {
		return new V3(x - o.x, y - o.y, z - o.z);
	}

	public V3 scale(double k) {
		return new V3(x * k, y * k, z * k);
	}

	public double dot(V3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public V3 cross(V3 o) {
		return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
	}

	public double lengthSq() {
		return x * x + y * y + z * z;
	}

	public double length() {
		return Math.sqrt(lengthSq());
	}

	public double distanceTo(V3 o) {
		return sub(o).length();
	}

	public boolean isFinite() {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
	}

	/** Unit vector, or {@code fallback} when this one is (almost) zero or not finite. */
	public V3 normalizedOr(V3 fallback) {
		double l = length();
		if (!(l > 1.0E-9) || !isFinite()) {
			return fallback;
		}
		return new V3(x / l, y / l, z / l);
	}

	public V3 lerp(V3 o, double t) {
		return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
	}
}
