package dev.minebleach.reiatsutest.core.combat;

import java.util.Map;

/**
 * R11 review fix 9: sane ranges for the {@code tp.*} keys that a player may type into the {@code "tp"} section of the client config. Before, only
 * {@code swordScale} and {@code guard.shift} were clamped and a hand typed {@code "hold.tilt": 1e9} became the pose. Every key the solver reads
 * ({@link TpPose}) has a range here; a value outside is clamped, a missing / non finite / wrongly shaped value and a key that is not in the table give the
 * default. The ranges are generous (a tuning room beyond the shipped numbers), they only keep the numbers where the maths and the picture stay valid:
 * px keys within a body size, angles within a turn, the yaw of the arm short of 90 degrees (the solver divides by its cosine). Pure Java.
 */
public final class TpKeyLimits {
	private static final Map<String, double[]> NUM = Map.ofEntries(
			Map.entry("swordScale", new double[] {0.5, 2.5}),
			Map.entry("hold.tilt", new double[] {-90, 90}),
			Map.entry("hold.edge", new double[] {-180, 180}),
			Map.entry("guard.tilt", new double[] {-90, 90}),
			Map.entry("guard.fwd", new double[] {0, 360}),
			Map.entry("guard.edge", new double[] {-180, 180}),
			Map.entry("grip.lat_r", new double[] {-8, 8}),
			Map.entry("grip.lat_l", new double[] {-8, 8}),
			Map.entry("guard.lat_r", new double[] {-8, 8}),
			Map.entry("guard.lat_l", new double[] {-8, 8}),
			Map.entry("sealed.lat_r", new double[] {-8, 8}),
			Map.entry("saya.lat", new double[] {-8, 8}),
			Map.entry("grip.c_r", new double[] {-16, 16}),
			Map.entry("grip.c_l", new double[] {-16, 16}),
			Map.entry("guard.c_l", new double[] {-16, 16}),
			Map.entry("sealed.c_r", new double[] {-16, 16}),
			Map.entry("saya.c", new double[] {-16, 16}),
			Map.entry("arm.max_shift", new double[] {0, 16}),
			Map.entry("arm.yaw_r", new double[] {-80, 80}),
			Map.entry("arm.yaw_l", new double[] {-80, 80}),
			Map.entry("draw.pull", new double[] {0, 1}),
			Map.entry("guard.in_s", new double[] {0.01, 2}),
			Map.entry("guard.out_s", new double[] {0.01, 2}),
			Map.entry("hold.fade_s", new double[] {0.01, 2}),
			Map.entry("debug", new double[] {0, 1}));

	/** Vector keys: component count and the limit (absolute) of the first three components and of the rest. */
	private record VecLimit(int size, double first, double rest) {
	}

	private static final Map<String, VecLimit> VEC = Map.of(
			"hold.pos", new VecLimit(3, 16, 16),
			"guard.pos", new VecLimit(3, 16, 16),
			"guard.shift", new VecLimit(3, 8, 8),
			"guard.arm_r", new VecLimit(6, 180, 16),
			"guard.arm_l", new VecLimit(6, 180, 16));

	private TpKeyLimits() {
	}

	/** {@code swordScale.<item>} counts as {@code swordScale}. */
	private static String normal(String key) {
		return key != null && key.startsWith("swordScale.") && key.length() > "swordScale.".length() ? "swordScale" : key;
	}

	/** Lower and upper limit of a numeric key, or null when the key is not one the pose reads. */
	public static double[] numRange(String key) {
		double[] r = NUM.get(normal(key));
		return r == null ? null : r.clone();
	}

	/** The value clamped into the range of its key; {@code def} when the key is unknown or the value is not a finite number. */
	public static double num(String key, double value, double def) {
		double[] r = NUM.get(normal(key));
		if (r == null || Double.isNaN(value) || Double.isInfinite(value)) {
			return def;
		}
		return Math.max(r[0], Math.min(r[1], value));
	}

	/** Number of components of a vector key, or 0 when the key is unknown. */
	public static int vecSize(String key) {
		VecLimit l = VEC.get(key);
		return l == null ? 0 : l.size();
	}

	/**
	 * The vector with every component clamped; {@code def} when the key is unknown, the array is shorter than the key needs or a component is not a
	 * finite number. Extra components are dropped.
	 */
	public static double[] vec(String key, double[] value, double[] def) {
		VecLimit l = VEC.get(key);
		if (l == null || value == null || value.length < l.size()) {
			return def;
		}
		double[] out = new double[l.size()];
		for (int i = 0; i < out.length; i++) {
			double v = value[i];
			if (Double.isNaN(v) || Double.isInfinite(v)) {
				return def;
			}
			double lim = i < 3 ? l.first() : l.rest();
			out[i] = Math.max(-lim, Math.min(lim, v));
		}
		return out;
	}
}
