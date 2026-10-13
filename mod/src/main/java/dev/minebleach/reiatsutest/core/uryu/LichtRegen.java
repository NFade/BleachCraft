package dev.minebleach.reiatsutest.core.uryu;

import dev.minebleach.reiatsutest.core.swarm.V3;
import java.util.List;
import java.util.Random;

/**
 * Licht Regen (design/URYU.md section 5): 12 arrows leave over 1.2 s and fall into a circle of radius 4 around the aimed point,
 * 1.2 HP each, armor ignored (damage type chosen by the glue), the caster is safe. Where an arrow lands is picked when it leaves:
 * round robin over the valid targets inside the circle (so the barrage is not a lottery against 0.6 block wide bodies), with a
 * small scatter, or uniformly in the circle when nobody is inside.
 */
public final class LichtRegen {
	private LichtRegen() {
	}

	/** Ticks after the cast at which arrow {@code i} (0..11) leaves: 0, 2, 4 ... 22. */
	public static int leaveTick(int i) {
		return i * UryuBalance.RAIN_TICKS / UryuBalance.RAIN_ARROWS;
	}

	/**
	 * Landing point of arrow {@code i}. {@code targets} are the feet positions of the valid targets inside the circle at the moment the
	 * arrow leaves (may be empty). The point never leaves the circle of radius 4 around {@code center}.
	 */
	public static V3 landingPoint(int i, V3 center, long seed, List<V3> targets) {
		Random rnd = new Random(seed * 31L + i * 7919L + 17L);
		if (targets.isEmpty()) {
			double r = UryuBalance.RAIN_RADIUS * Math.sqrt(rnd.nextDouble());
			double a = rnd.nextDouble() * 2.0 * Math.PI;
			return new V3(center.x() + Math.cos(a) * r, center.y(), center.z() + Math.sin(a) * r);
		}
		V3 t = targets.get(i % targets.size());
		double r = UryuBalance.RAIN_JITTER * Math.sqrt(rnd.nextDouble());
		double a = rnd.nextDouble() * 2.0 * Math.PI;
		double x = t.x() + Math.cos(a) * r;
		double z = t.z() + Math.sin(a) * r;
		double dx = x - center.x();
		double dz = z - center.z();
		double d = Math.sqrt(dx * dx + dz * dz);
		double edge = UryuBalance.RAIN_RADIUS - 1.0E-6;
		if (d > edge) {
			x = center.x() + dx / d * edge;
			z = center.z() + dz / d * edge;
		}
		return new V3(x, t.y(), z);
	}

	/** Where the arrow starts: straight above its landing point. */
	public static V3 startPoint(V3 landing) {
		return new V3(landing.x(), landing.y() + UryuBalance.RAIN_DROP_HEIGHT, landing.z());
	}

	/** Is the point within the circle of the rain (horizontal distance)? */
	public static boolean inside(V3 center, double x, double z) {
		double dx = x - center.x();
		double dz = z - center.z();
		return dx * dx + dz * dz <= UryuBalance.RAIN_RADIUS * UryuBalance.RAIN_RADIUS;
	}
}
