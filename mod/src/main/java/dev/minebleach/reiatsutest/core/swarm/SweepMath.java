package dev.minebleach.reiatsutest.core.swarm;

/**
 * Swept sphere against an axis aligned box, for the blade swarm that moves up to 1.5 blocks per tick: testing only the end
 * position would let fast blades pass through thin targets. The segment is walked in steps of at most a quarter of the radius and
 * every sample is tested exactly (closest point of the box), after a cheap slab test of the box grown by the radius.
 */
public final class SweepMath {
	private SweepMath() {
	}

	/** Sphere centre {@code c}, radius {@code r} touches the box. */
	public static boolean sphereTouchesBox(V3 c, double r, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double dx = Math.max(Math.max(minX - c.x(), 0.0), c.x() - maxX);
		double dy = Math.max(Math.max(minY - c.y(), 0.0), c.y() - maxY);
		double dz = Math.max(Math.max(minZ - c.z(), 0.0), c.z() - maxZ);
		return dx * dx + dy * dy + dz * dz <= r * r;
	}

	/**
	 * Fraction t in [0, 1] along {@code a -> b} of the first sampled position where the sphere touches the box, or -1 when it never
	 * does. A zero length segment is a plain overlap test (t = 0 on contact).
	 */
	public static double sweepSphereVsBox(V3 a, V3 b, double r, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		// broad phase: segment against the box grown by r (slab test)
		double tmin = 0.0;
		double tmax = 1.0;
		double[] o = {a.x(), a.y(), a.z()};
		double[] d = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
		double[] lo = {minX - r, minY - r, minZ - r};
		double[] hi = {maxX + r, maxY + r, maxZ + r};
		for (int k = 0; k < 3; k++) {
			if (Math.abs(d[k]) < 1.0E-12) {
				if (o[k] < lo[k] || o[k] > hi[k]) {
					return -1.0;
				}
			} else {
				double t1 = (lo[k] - o[k]) / d[k];
				double t2 = (hi[k] - o[k]) / d[k];
				if (t1 > t2) {
					double tmp = t1;
					t1 = t2;
					t2 = tmp;
				}
				tmin = Math.max(tmin, t1);
				tmax = Math.min(tmax, t2);
				if (tmin > tmax) {
					return -1.0;
				}
			}
		}
		// narrow phase: exact sphere tests at samples that cannot skip a thin box
		double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
		double step = Math.max(r * 0.25, 0.05);
		int n = Math.max(1, (int) Math.ceil(len / step));
		int from = (int) Math.floor(tmin * n);
		int to = Math.min(n, (int) Math.ceil(tmax * n));
		for (int i = Math.max(0, from); i <= to; i++) {
			double t = (double) i / n;
			V3 p = new V3(o[0] + d[0] * t, o[1] + d[1] * t, o[2] + d[2] * t);
			if (sphereTouchesBox(p, r, minX, minY, minZ, maxX, maxY, maxZ)) {
				return t;
			}
		}
		return -1.0;
	}
}
