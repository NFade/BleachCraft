package dev.minebleach.reiatsutest.core.fx;

/**
 * Pure view gates of the Byakuya shikai petal swarm (R12): what is drawn of the swarm for the viewer, as a function of the petal, its distance and the motion
 * of the camera. The renderer ({@code PetalSwarmRenderer}) keeps the draw calls; the numbers live here so that they can be measured and tested.
 * <ul>
 * <li><b>First person of the owner, resting swarm</b> ({@link #fpScale}): the classic orbit passes through the lower field of view all the time and every turn of
 * the head sweeps petals and glints over the lens. While the owner walks or turns ({@link #activity}) the petals near the lens are removed further, the swarm is
 * thinned and its glow is cut ({@link #fpGlow}). Strikes (attack, release, barrier) are not touched.</li>
 * <li><b>Third person resting ribbons</b> ({@link #tpKeep}, {@link #tpGlow}): fewer petals, weaker glow, and the swarm clears out of the way of the sword
 * ({@link #swordClear}) while the owner strikes.</li>
 * </ul>
 * All defaults are the shipped numbers; the client reads them from {@code fx_override.json} ({@code swarm.*}).
 */
public final class SwarmView {
	private SwarmView() {
	}

	/** Numbers of the first person gate. */
	public static final class Fp {
		/** Inside this distance from the camera nothing is drawn, thinned and shrunk up to {@link #fade} (the old numbers). */
		public double clear = 1.5;
		public double fade = 5.0;
		public double keep = 0.12;
		public double size = 0.35;
		public double glowMin = 0.15;
		/** Resting swarm only: extra clear radius, the fraction of the thinned swarm that stays, and the glow multiplier. */
		public double idleClear = 0.0;
		/** Resting swarm only: the petals shrink to nothing over this distance beyond the clear radius (no pop when the radius grows). */
		public double idleEdge = 0.0;
		public double idleDensity = 1.0;
		public double idleGlow = 0.25;
		/** Resting swarm only: weight of the white star glints of the petals (they flash whenever the head turns or the player walks); halos keep {@link #idleGlow}. */
		public double glint = 0.0;
		/** Resting swarm only, while the owner moves or turns (activity 1): more clear radius, less petals (cut), the glow goes to zero. */
		public double moveClear = 0.0;
		public double moveCut = 0.0;
		public double moveGlowCut = 1.0;
	}

	/** Numbers of the third person gate. */
	public static final class Tp {
		/** Fraction of the resting ribbon petals that is drawn. */
		public double keep = 0.5;
		/** Glow multiplier of the resting ribbons (sparkles, halos). */
		public double glow = 0.4;
		/** Radius around the sword path inside which the swarm gives way while the owner strikes, and the reach of the zone. */
		public double swordClear = 1.1;
		public double swordReach = 2.2;
		/** Size of the resting loop petals as a fraction of the old size. */
		public double size = 0.6;
	}

	/** Hash of petal i in 0..1 (golden ratio sequence: the kept petals of any prefix are evenly spread). */
	public static double hash(int i) {
		return (i * 0.6180339887498949) % 1.0;
	}

	static double smoothstep(double a, double b, double x) {
		if (b <= a) {
			return x < a ? 0 : 1;
		}
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	/**
	 * Size factor of petal {@code i} at distance {@code dist} from the camera in the owner's first person: 0 = not drawn. {@code idle} = the resting swarm
	 * (the strikes keep the old behaviour: only {@code clear}, {@code fade}, {@code keep}, {@code size}); {@code activity} = 0..1 motion of the owner.
	 */
	public static double fpScale(int i, double dist, Fp p, boolean idle, double activity) {
		double clear = p.clear;
		double dens = 1.0;
		if (idle) {
			double m = clamp01(activity);
			clear += p.idleClear + m * p.moveClear;
			dens = p.idleDensity * (1.0 - m * p.moveCut);
		}
		if (dist < clear) {
			return 0;
		}
		double fade = Math.max(p.fade, clear + 0.5);
		double a = smoothstep(clear, fade, dist);
		double kept = (p.keep + (1.0 - p.keep) * a) * dens;
		if (hash(i) > kept) {
			return 0;
		}
		double sz = p.size + (1.0 - p.size) * a;
		return idle && p.idleEdge > 1e-3 ? sz * smoothstep(clear, clear + p.idleEdge, dist) : sz;
	}

	/**
	 * Glow multiplier of a petal of the owner's first person at distance {@code dist}: 1 in the third person; for the owner it rises from {@code glowMin} at the
	 * lens to 1 far away (the old curve), then the resting swarm is cut by {@code idleGlow} and by the motion.
	 */
	public static double fpGlow(double dist, Fp p, boolean idle, double activity) {
		double g = p.glowMin + (1.0 - p.glowMin) * smoothstep(p.clear, p.fade * 1.5, dist);
		if (idle) {
			g *= p.idleGlow * (1.0 - clamp01(activity) * p.moveGlowCut);
		}
		return g;
	}

	/**
	 * Motion of the owner as 0..1: the larger of the walking speed over {@code speedRef} (blocks per second) and the turn rate over {@code turnRef} (degrees per
	 * second, yaw and pitch together).
	 */
	public static double activity(double speed, double turnRate, double speedRef, double turnRef) {
		double a = Math.max(speed / Math.max(1e-6, speedRef), turnRate / Math.max(1e-6, turnRef));
		return clamp01(a);
	}

	/** One step of the smoothed activity: fast up (the petals leave at once), slow down (they come back gently). */
	public static double smoothActivity(double prev, double target, double dt, double up, double down) {
		double k = 1.0 - Math.exp(-Math.max(0, dt) * (target > prev ? up : down));
		return prev + (target - prev) * k;
	}

	/** Third person: is the resting ribbon petal {@code i} drawn at all (a fixed subset, no flicker)? */
	public static boolean tpKeep(int i, Tp p) {
		return hash(i) < p.keep;
	}

	/** Third person glow multiplier of a resting ribbon petal. */
	public static double tpGlow(Tp p) {
		return clamp01(p.glow);
	}

	/**
	 * Size factor 0..1 of a swarm petal near the sword while the owner strikes. {@code (px, py, pz)} = the petal relative to the shoulder, {@code (fx, fy, fz)} = the unit
	 * view direction of the owner, {@code strength} = 0..1 how strongly the owner strikes now. The zone is the segment from the shoulder {@code swordReach} forward
	 * with radius {@code swordClear}: inside it the petals are shrunk away, with a soft edge.
	 */
	public static double swordClear(double px, double py, double pz, double fx, double fy, double fz, double strength, Tp p) {
		if (strength <= 0) {
			return 1;
		}
		double d = px * fx + py * fy + pz * fz;
		double dd = Math.max(0, Math.min(p.swordReach, d));
		double qx = px - fx * dd;
		double qy = py - fy * dd;
		double qz = pz - fz * dd;
		double dist = Math.sqrt(qx * qx + qy * qy + qz * qz);
		double inside = 1.0 - smoothstep(p.swordClear * 0.55, p.swordClear, dist);
		return 1.0 - clamp01(strength) * inside;
	}

	static double clamp01(double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}
}
