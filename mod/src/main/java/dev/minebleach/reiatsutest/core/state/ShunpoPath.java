package dev.minebleach.reiatsutest.core.state;

/**
 * Pure path logic of the shunpo (B4 step 5): the direction from the look angles and the walk along it. The world is
 * hidden behind {@link Probe}, so the same code runs in unit tests (voxel grid) and on the server (block collisions).
 */
public final class ShunpoPath {
	private ShunpoPath() {
	}

	/** Answers about the world at a feet position of the player. */
	public interface Probe {
		/** True when the player's bounding box standing at this feet position collides with nothing (and is inside the world). */
		boolean fits(double x, double y, double z);

		/** True when this feet position is a safe landing: ground within {@code maxDrop} below and no hazard. */
		boolean safeLanding(double x, double y, double z, int maxDrop);
	}

	/** The end point of a shunpo and what stopped it. */
	public record Result(double distance, double x, double y, double z, boolean stoppedByWall) {
	}

	/**
	 * Unit direction from the look angles (Minecraft convention: yaw 0 = +Z, pitch positive = looking down). On the ground
	 * a downward look is flattened (the player cannot dash into the floor); straight up or down falls back to the
	 * horizontal facing.
	 */
	public static double[] direction(double yawDeg, double pitchDeg, boolean onGround) {
		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);
		double cp = Math.cos(pitch);
		double x = -Math.sin(yaw) * cp;
		double y = -Math.sin(pitch);
		double z = Math.cos(yaw) * cp;
		if (onGround && y < 0) {
			y = 0;
		}
		double len = Math.sqrt(x * x + y * y + z * z);
		if (len < 1.0e-4 || (x * x + z * z) < 1.0e-6) {
			x = -Math.sin(yaw);
			z = Math.cos(yaw);
			y = 0;
			len = 1.0;
		}
		return new double[] {x / len, y / len, z / len};
	}

	/**
	 * Walks from the feet position along the unit direction. Stops before the first sample where the player no longer fits
	 * (wall, ceiling, border), then steps back along the path until the landing is safe. Returns null when no landing of at
	 * least {@code spec.minDistance()} exists.
	 */
	public static Result resolve(Probe probe, double x, double y, double z, double dx, double dy, double dz, ShunpoSpec spec,
			boolean startedOnGround) {
		double step = Math.max(0.05, spec.step());
		double reach = 0.0;
		boolean wall = false;
		for (double t = step; t <= spec.distance() + 1.0e-9; t += step) {
			if (!probe.fits(x + dx * t, y + dy * t, z + dz * t)) {
				wall = true;
				break;
			}
			reach = t;
		}
		if (!wall) {
			reach = Math.min(reach, spec.distance());
		}
		double t = reach;
		// an airborne start may land in the air (the player keeps falling as before); from the ground the landing is snapped down
		// onto the floor below (looking up gives a shorter dash, never a landing in mid-air) and must be safe
		while (t >= spec.minDistance() - 1.0e-9) {
			double px = x + dx * t;
			double py = y + dy * t;
			double pz = z + dz * t;
			if (!startedOnGround) {
				return new Result(Math.sqrt((px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z)), px, py, pz, wall);
			}
			double sy = snapDown(probe, px, py, pz, spec.maxDrop());
			if (!Double.isNaN(sy) && probe.safeLanding(px, sy, pz, spec.maxDrop())) {
				return new Result(Math.sqrt((px - x) * (px - x) + (sy - y) * (sy - y) + (pz - z) * (pz - z)), px, sy, pz, wall);
			}
			t -= step;
		}
		return null;
	}

	/**
	 * Lowers the feet position until the box rests on something (within {@code maxDrop} blocks); the y of the resting
	 * position, or NaN when there is no floor in range.
	 */
	static double snapDown(Probe probe, double x, double y, double z, int maxDrop) {
		final double fine = 0.05;
		if (!probe.fits(x, y - fine, z)) {
			return y; // already standing
		}
		double yy = y;
		for (double d = fine; d <= maxDrop + 1.0e-9; d += fine) {
			if (!probe.fits(x, y - d, z)) {
				return yy;
			}
			yy = y - d;
		}
		return Double.NaN;
	}
}
