package dev.minebleach.reiatsutest.core.combat;

/**
 * Pure math of the Byakuya barrier push (r8 item 6): while the barrier stands everybody who comes within the radius of the owner
 * is shoved outward (horizontal) with a small upward part, repeated every few ticks. No damage. No Minecraft types:
 * {@code server/BarrierPush} finds the entities and writes the velocity this class computes.
 */
public final class BarrierPush {
	private BarrierPush() {
	}

	/** Ids that never get pushed are decided by the server glue; this is the geometric test: inside the cylinder around the owner. */
	public static boolean inside(double dx, double dy, double dz, double halfWidth, double radius, double height) {
		double reach = radius + Math.max(0.0, halfWidth);
		return dx * dx + dz * dz <= reach * reach && dy >= -height && dy <= height;
	}

	/**
	 * Velocity (x, y, z) after one push. The horizontal part is the old velocity halved (like vanilla knockback, so repeated pushes
	 * do not pile up without end) plus {@code strength} along the outward direction (dx, dz) = entity minus owner, cut by the
	 * entity's knockback resistance (0..1). The vertical part is at least {@code up} when the entity stands on the ground and is
	 * left alone in the air, so a push never launches anybody. A (nearly) zero offset uses {@code fallbackAngle} as the direction.
	 */
	public static double[] velocity(double vx, double vy, double vz, double dx, double dz, double strength, double up, double resistance,
			boolean onGround, double fallbackAngle) {
		double len = Math.sqrt(dx * dx + dz * dz);
		double ox;
		double oz;
		if (len < 1.0E-4) {
			ox = Math.cos(fallbackAngle);
			oz = Math.sin(fallbackAngle);
		} else {
			ox = dx / len;
			oz = dz / len;
		}
		double s = Math.max(0.0, strength) * (1.0 - Math.max(0.0, Math.min(1.0, resistance)));
		double ny = onGround ? Math.max(vy, Math.max(0.0, up)) : vy;
		return new double[] {vx * 0.5 + ox * s, ny, vz * 0.5 + oz * s};
	}

	/**
	 * Steady-state outward speed (blocks per tick) a pushed walker keeps while pushes arrive every {@code intervalTicks}: the
	 * velocity before each push is halved and the push adds {@code strength}, with ground friction {@code friction} per tick in between.
	 * Used by the tests to show that a sprinter ({@code 0.28} blocks per tick) cannot beat the push.
	 */
	public static double averageOutwardSpeed(double strength, int intervalTicks, double friction) {
		int n = Math.max(1, intervalTicks);
		double v = 0.0;
		double sum = 0.0;
		for (int round = 0; round < 200; round++) { // converge on the repeating pattern
			v = v * 0.5 + strength;
			sum = 0.0;
			double x = v;
			for (int t = 0; t < n; t++) {
				sum += x;
				x *= friction;
			}
			v = x;
		}
		return sum / n;
	}
}
