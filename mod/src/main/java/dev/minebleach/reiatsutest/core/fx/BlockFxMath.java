package dev.minebleach.reiatsutest.core.fx;

import java.util.Random;

/**
 * Pure maths of the block and parry feedback (r8 fx2 item 1, Sekiro style guard sparks), shared by the server that picks the contact
 * point and the client that draws the sparks. Three variants travel in {@code params[0]} of effect event 41:
 * {@link #BLOCK} (a sword blocked a sword), {@link #PARRY} (the timed block, bigger and brighter) and {@link #ABILITY} (the block cut an
 * ability hit by its 30 percent; lighter, without the blade scrape when the source is not a blade).
 *
 * <p>Everything here is a budget or a direction table: spark counts are capped ({@link #MAX_SPARKS}), distance cuts them, and the
 * velocity tables are deterministic in the seed so every client draws the same sparks. No Minecraft types; the numbers are the defaults,
 * the client multiplies them by FxTune scales at runtime.
 */
public final class BlockFxMath {
	public static final int BLOCK = 0;
	public static final int PARRY = 1;
	public static final int ABILITY = 2;
	/** Hard cap of sparks of one event, whatever the damage and the tuning scale. */
	public static final int MAX_SPARKS = 120;
	/** Beyond this distance (blocks) from the camera the event draws no sparks at all (the sound is positional and fades by itself). */
	public static final double FAR = 64.0;

	private BlockFxMath() {
	}

	/** True for the three known variants. */
	public static boolean known(int variant) {
		return variant == BLOCK || variant == PARRY || variant == ABILITY;
	}

	/** Variant code of an event param (rounded, unknown values read as BLOCK). */
	public static int variantOf(float param) {
		int v = Math.round(param);
		return known(v) ? v : BLOCK;
	}

	/**
	 * Spark budget of one event: BLOCK {@code 24 + 3 x damage} (damage capped at 8, so 24 to 48), PARRY {@code 70 + 4 x damage} (70 to 102),
	 * ABILITY {@code 10 + damage} (10 to 22), times {@code scale}, never above {@link #MAX_SPARKS}, never negative. A non finite damage reads as 0.
	 */
	public static int sparkCount(int variant, double damage, double scale) {
		double d = Double.isFinite(damage) ? Math.max(0.0, Math.min(8.0, damage)) : 0.0;
		double base = switch (variant) {
			case PARRY -> 70.0 + 4.0 * d;
			case ABILITY -> 10.0 + 1.5 * d;
			default -> 24.0 + 3.0 * d;
		};
		double s = Double.isFinite(scale) ? Math.max(0.0, scale) : 1.0;
		return (int) Math.max(0, Math.min(MAX_SPARKS, Math.round(base * s)));
	}

	/** Share of the sparks kept at {@code distance} blocks from the camera: 1 up to 24, 0.5 up to 48, 0.25 up to {@link #FAR}, 0 beyond. */
	public static double distanceFactor(double distance) {
		if (!(distance >= 0)) {
			return 1.0;
		}
		return distance <= 24.0 ? 1.0 : distance <= 48.0 ? 0.5 : distance <= FAR ? 0.25 : 0.0;
	}

	/** The sparks of a block fly in this many waves (the scrape slides along the blade), the first wave carrying the larger share. */
	public static int[] waves(int total, int waveCount) {
		int n = Math.max(1, waveCount);
		int[] out = new int[n];
		if (total <= 0) {
			return out;
		}
		// weights 1.0, 0.7, 0.49 ...: the sparks die away along the scrape
		double sum = 0;
		double[] w = new double[n];
		for (int i = 0; i < n; i++) {
			w[i] = Math.pow(0.7, i);
			sum += w[i];
		}
		int given = 0;
		for (int i = 0; i < n; i++) {
			out[i] = (int) Math.floor(total * w[i] / sum);
			given += out[i];
		}
		out[0] += total - given; // the rounding rest goes to the first wave
		return out;
	}

	/**
	 * Unit blade axis of the guard: across the defender's front (perpendicular to the unit horizontal vector {@code (nx, nz)} towards the
	 * attacker) tilted up by {@code tiltRad}; {@code flip} mirrors it to the other side. Falls back to +x when the vector is degenerate.
	 */
	public static double[] bladeAxis(double nx, double nz, double tiltRad, boolean flip) {
		double l = Math.hypot(nx, nz);
		double rx = 1.0;
		double rz = 0.0;
		if (l > 1e-6) {
			rx = -nz / l;
			rz = nx / l;
		}
		double s = flip ? -1.0 : 1.0;
		double c = Math.cos(tiltRad);
		return new double[] {s * rx * c, Math.sin(tiltRad), s * rz * c};
	}

	/** Contact point of a hit on a defender: at {@code heightFrac} of his height, {@code forward} blocks towards the attacker (unit horizontal {@code (nx, nz)}). */
	public static double[] contactPoint(double x, double y, double z, double height, double nx, double nz, double forward, double heightFrac) {
		double l = Math.hypot(nx, nz);
		double ux = l > 1e-6 ? nx / l : 0.0;
		double uz = l > 1e-6 ? nz / l : 0.0;
		return new double[] {x + ux * forward, y + height * heightFrac, z + uz * forward};
	}

	/**
	 * Velocities (blocks per second) of {@code count} guard sparks sliding along {@code axis} (unit): a share {@code slideBias} (0.5 to 1)
	 * leaves in the +axis direction, the rest back along -axis; each also gets a little push out of the blade ({@code normal}, unit, towards
	 * the attacker), a sideways fan and a small lift. Deterministic in {@code seed}. Rows are {vx, vy, vz}.
	 */
	public static double[][] slideVelocities(long seed, int count, double[] axis, double[] normal, double speedMin, double speedMax, double slideBias, double fan,
			double lift) {
		int n = Math.max(0, Math.min(count, MAX_SPARKS));
		double[][] out = new double[n][3];
		Random r = new Random(seed * 0x9E3779B97F4A7C15L + 17);
		double[] side = cross(axis, normal);
		double lo = Math.min(speedMin, speedMax);
		double hi = Math.max(speedMin, speedMax);
		double bias = Math.max(0.0, Math.min(1.0, slideBias));
		for (int i = 0; i < n; i++) {
			double sign = r.nextDouble() < bias ? 1.0 : -1.0;
			double speed = lo + (hi - lo) * r.nextDouble();
			double along = sign * speed * (0.65 + 0.35 * r.nextDouble());
			double outward = speed * (0.05 + 0.30 * r.nextDouble());
			double jitter = (r.nextDouble() - 0.5) * fan * speed;
			double up = lift * speed * r.nextDouble();
			out[i][0] = axis[0] * along + normal[0] * outward + side[0] * jitter;
			out[i][1] = axis[1] * along + normal[1] * outward + side[1] * jitter + up;
			out[i][2] = axis[2] * along + normal[2] * outward + side[2] * jitter;
		}
		return out;
	}

	/**
	 * Velocities of {@code count} sparks of a parry burst: directions spread over the cone of half angle {@code coneRad} around the unit
	 * {@code normal} (uniform in solid angle), speeds between {@code speedMin} and {@code speedMax}. Deterministic in {@code seed}.
	 */
	public static double[][] burstVelocities(long seed, int count, double[] normal, double coneRad, double speedMin, double speedMax) {
		int n = Math.max(0, Math.min(count, MAX_SPARKS));
		double[][] out = new double[n][3];
		Random r = new Random(seed * 0xBF58476D1CE4E5B9L + 29);
		double[] u = perpendicular(normal);
		double[] v = cross(normal, u);
		double cMax = Math.cos(Math.max(0.05, Math.min(Math.PI, coneRad)));
		double lo = Math.min(speedMin, speedMax);
		double hi = Math.max(speedMin, speedMax);
		for (int i = 0; i < n; i++) {
			double cosT = 1.0 - (1.0 - cMax) * r.nextDouble();
			double sinT = Math.sqrt(Math.max(0.0, 1.0 - cosT * cosT));
			double phi = r.nextDouble() * Math.PI * 2.0;
			double speed = lo + (hi - lo) * r.nextDouble();
			for (int k = 0; k < 3; k++) {
				out[i][k] = speed * (normal[k] * cosT + sinT * (Math.cos(phi) * u[k] + Math.sin(phi) * v[k]));
			}
		}
		return out;
	}

	/** Radius of the n-th (0 based) parry ring after {@code age} of {@code life} seconds: eases out from {@code from} to {@code to}. */
	public static double ringRadius(double from, double to, double age, double life) {
		if (life <= 1e-6) {
			return to;
		}
		double x = Math.max(0.0, Math.min(1.0, age / life));
		double u = 1.0 - x;
		return from + (to - from) * (1.0 - u * u * u);
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double[] perpendicular(double[] n) {
		double[] helper = Math.abs(n[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
		double[] p = cross(n, helper);
		double l = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
		return l < 1e-9 ? new double[] {1, 0, 0} : new double[] {p[0] / l, p[1] / l, p[2] / l};
	}
}
