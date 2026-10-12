package dev.minebleach.reiatsutest.core.swarm;

/** Steering helpers of the Senbonzakura blade flight: limited turning of a direction, limited radial speed, cone validation. */
public final class SteerMath {
	private SteerMath() {
	}

	/** Angle in radians (0..pi) between two non-zero vectors. */
	public static double angle(V3 a, V3 b) {
		double la = a.length();
		double lb = b.length();
		if (!(la > 1.0E-12) || !(lb > 1.0E-12)) {
			return 0.0;
		}
		double c = a.dot(b) / (la * lb);
		return Math.acos(Math.max(-1.0, Math.min(1.0, c)));
	}

	/**
	 * Turns the unit vector {@code cur} toward the unit vector {@code want} by at most {@code maxRad} (great circle, constant
	 * rate). Returns {@code want} when it is within reach. Always a unit vector.
	 */
	public static V3 turnToward(V3 cur, V3 want, double maxRad) {
		V3 c = cur.normalizedOr(want);
		V3 w = want.normalizedOr(c);
		double ang = angle(c, w);
		if (ang <= maxRad || ang < 1.0E-9) {
			return w;
		}
		if (maxRad <= 0.0) {
			return c;
		}
		V3 axis = c.cross(w);
		if (axis.lengthSq() < 1.0E-12) {
			// opposite directions: any perpendicular axis, picked deterministically
			axis = Math.abs(c.y()) < 0.9 ? c.cross(new V3(0, 1, 0)) : c.cross(new V3(1, 0, 0));
		}
		axis = axis.normalizedOr(new V3(0, 1, 0));
		// rotate c about axis by maxRad (axis is perpendicular to c, so the Rodrigues formula reduces to this)
		V3 perp = axis.cross(c);
		V3 out = c.scale(Math.cos(maxRad)).add(perp.scale(Math.sin(maxRad)));
		return out.normalizedOr(c);
	}

	/**
	 * Largest turn per tick (radians) at distance {@code r}: the angular limit, and a linear limit so that a far swarm does not
	 * sweep faster than {@code maxLateralPerTick} sideways.
	 */
	public static double maxTurnPerTick(double turnRadPerTick, double maxLateralPerTick, double r) {
		double lateral = maxLateralPerTick / Math.max(r, 1.0);
		return Math.max(0.0, Math.min(turnRadPerTick, lateral));
	}

	/** Moves {@code cur} toward {@code target} by at most {@code maxStep} (either direction). */
	public static double stepToward(double cur, double target, double maxStep) {
		double d = target - cur;
		if (Math.abs(d) <= maxStep) {
			return target;
		}
		return cur + Math.signum(d) * maxStep;
	}

	/** {@code dir} when it lies within {@code maxRad} of {@code axis}, otherwise {@code axis}. */
	public static V3 withinCone(V3 dir, V3 axis, double maxRad) {
		return angle(dir, axis) <= maxRad ? dir : axis;
	}
}
