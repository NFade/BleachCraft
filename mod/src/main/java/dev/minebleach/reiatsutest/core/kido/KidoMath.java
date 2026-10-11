package dev.minebleach.reiatsutest.core.kido;

/** Pure arithmetic of the spells: flight time, burst falloff, the numbers sent to the client. */
public final class KidoMath {
	/** Damage at the very edge of a burst, as a fraction of the centre damage. */
	public static final double EDGE_FRACTION = 0.4;
	/** Hit kinds of {@code effect_event} param 2. */
	public static final int HIT_NONE = 0;
	public static final int HIT_ENTITY = 1;
	public static final int HIT_BLOCK = 2;
	/** A burst sits this far in front of the surface the ball hit, so its sphere is not centred inside the block (server and client). */
	public static final double SURFACE_BACKOFF = 0.3;

	private KidoMath() {
	}

	/** Server ticks a projectile needs for {@code distance} blocks at {@code speed} blocks per tick; 0 for an instant spell. */
	public static int travelTicks(double distance, double speed) {
		if (speed <= 0.0 || !(distance > 0.0)) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(distance / speed));
	}

	/**
	 * Multiplier of the burst damage at {@code dist} blocks from the centre: 1 in the centre, falling linearly to
	 * {@link #EDGE_FRACTION} at {@code radius}, 0 outside. A burst without radius only hits at distance 0.
	 */
	public static double falloff(double dist, double radius) {
		if (!(dist >= 0.0)) {
			return 0.0;
		}
		if (radius <= 0.0) {
			return dist == 0.0 ? 1.0 : 0.0;
		}
		if (dist > radius) {
			return 0.0;
		}
		return 1.0 - (1.0 - EDGE_FRACTION) * (dist / radius);
	}

	/** Damage of a burst at {@code dist} from the centre. */
	public static double burstDamage(KidoSpec spec, double dist) {
		return spec.damage() * falloff(dist, spec.radius());
	}

	/**
	 * Length of the line of flight really used: the free distance up to the first block or target, never longer than the
	 * range of the spell, never negative.
	 */
	public static double usedLength(double range, double freeDistance) {
		if (!(freeDistance > 0.0)) {
			return 0.0;
		}
		return Math.min(range, freeDistance);
	}

	/**
	 * Parameters of the {@code effect_event} (at most 8): length of the line, travel ticks, burst radius, hit kind, flight speed,
	 * binding ticks. The client simulates the visual from these and the direction vector.
	 */
	public static float[] effectParams(KidoSpec spec, double usedLength, int hitKind) {
		return new float[] {(float) usedLength, travelTicks(usedLength, spec.speed()), (float) spec.radius(), hitKind,
				(float) spec.speed(), spec.statusTicks()};
	}
}
