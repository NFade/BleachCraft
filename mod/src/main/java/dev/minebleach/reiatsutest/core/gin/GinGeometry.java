package dev.minebleach.reiatsutest.core.gin;

/** Small geometry of the Gin abilities, in plain numbers (the glue reads the world). */
public final class GinGeometry {
	private GinGeometry() {
	}

	/** Signed horizontal angle in degrees from direction (dirX, dirZ) to the vector (dx, dz), in the range -180..180. */
	public static double yawOffsetDeg(double dirX, double dirZ, double dx, double dz) {
		double a = Math.atan2(dz, dx) - Math.atan2(dirZ, dirX);
		double deg = Math.toDegrees(a);
		while (deg > 180.0) {
			deg -= 360.0;
		}
		while (deg < -180.0) {
			deg += 360.0;
		}
		return deg;
	}

	/** Lower edge (degrees from the centre line) of slice {@code i} of {@code n} that together cover {@code arcDeg}. */
	public static double sliceFrom(int i, int n, double arcDeg) {
		return -arcDeg / 2.0 + arcDeg * i / n;
	}

	public static double sliceTo(int i, int n, double arcDeg) {
		return sliceFrom(i + 1, n, arcDeg);
	}

	/** Angle (degrees) a body of half width {@code halfWidth} covers at {@code dist}: a target on the edge of a slice still counts. */
	public static double slackDeg(double dist, double halfWidth) {
		return Math.toDegrees(Math.atan2(halfWidth, Math.max(dist, 0.5)));
	}

	/**
	 * True when {@code offsetDeg} belongs to slice [from, to). The lower edge is inclusive and the upper exclusive, except for the last slice
	 * ({@code last}) whose upper edge is inclusive, so every angle of the arc is in exactly one slice.
	 */
	public static boolean inSlice(double offsetDeg, double from, double to, boolean last) {
		return offsetDeg >= from && (last ? offsetDeg <= to : offsetDeg < to);
	}

	/** A target at {@code offsetDeg} (body slack {@code slackDeg}) is touched by the slice [from, to]. */
	public static boolean sliceHit(double offsetDeg, double from, double to, double slackDeg) {
		return offsetDeg >= from - slackDeg && offsetDeg <= to + slackDeg;
	}

	/** The recast reach: strictly farther than {@code range} loses the sliver. */
	public static boolean outOfRange(double distance, double range) {
		return distance > range;
	}

	/** One step of the Snake Hook pull: the velocity in blocks per tick and whether the caster has arrived. */
	public record PullStep(double vx, double vy, double vz, boolean done) {
	}

	/**
	 * Velocity for one tick that carries the caster at (gx, gy, gz) toward the anchor but never closer than {@code stopShort} (and never
	 * farther in one tick than {@code speed}, nor past the stop point).
	 */
	public static PullStep pull(double gx, double gy, double gz, double ax, double ay, double az, double stopShort, double speed) {
		double dx = ax - gx;
		double dy = ay - gy;
		double dz = az - gz;
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double room = d - stopShort;
		if (room <= 0.05 || d < 1.0E-6) {
			return new PullStep(0, 0, 0, true);
		}
		double step = Math.min(speed, room);
		return new PullStep(dx / d * step, dy / d * step, dz / d * step, false);
	}
}
