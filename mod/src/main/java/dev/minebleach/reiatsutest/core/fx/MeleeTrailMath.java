package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.combat.SwingMotion;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure maths of the pink melee trail of the Byakuya shikai and bankai (r8 play test wish: when Byakuya hits in melee a blade of petals assembles
 * in the air along the sword path, fast and glowing). Better Combat draws its own swing trail and it cannot be recoloured, so the mod draws its own
 * effect; this class lays out WHERE and WHEN, the client ({@code MeleeTrailFx}) turns it into particles and glow sprites.
 *
 * <p>Frame: local, right handed, x to the right of the view, y up, z forward (along the view), origin at the shoulder; lengths in blocks. The path
 * is the likely track of the tip of the sword for the attack profile ({@link SwingMotion.Profile}, picked from the Better Combat animation name by
 * {@link SwingMotion#profileFor}): a spherical arc of radius {@code reach} between two (yaw, elevation) pairs, or a straight thrust. The swept blade is
 * the radial line from {@link #HAND} of the reach to the full reach at every point of the arc. The trail starts at the hit moment (the end of the
 * upswing): the point at arc parameter {@code u} (0 first, 1 last) appears {@code u * REVEAL} seconds later and lives until the trail is {@link #SECONDS}
 * old, so the blade assembles along the path in a tenth of a second and fades out over the rest. The number of motes is fixed ({@link #BUDGET}).
 * Everything is UNVERIFIED against the real Better Combat animations: the arcs are plausible tracks of the animation names, tunable in the client.
 */
public final class MeleeTrailMath {
	/** Seconds from the hit moment until the last mote has faded. */
	public static final double SECONDS = 0.25;
	/** Seconds the assembly of the trail takes (the last point of the arc appears this late). */
	public static final double REVEAL = 0.10;
	/** The blade starts this fraction of the reach from the shoulder (the hand). */
	public static final double HAND = 0.38;
	/** Default distance from the shoulder to the tip, blocks (arm and katana). */
	public static final double REACH = 1.85;
	public static final int PETALS = 24;
	public static final int GLOWS = 32;
	public static final int ACCENTS = 2;
	/** Most motes of one swing (particles and glow sprites together). */
	public static final int BUDGET = 60;

	public enum Kind { PETAL, GLOW, ACCENT }

	/**
	 * One mote of the trail: local position {@code (x, y, z)}, drift velocity in blocks per second (along the sweep), seconds until it appears,
	 * its life in seconds from its appearance, and the size in blocks.
	 */
	public record Mote(Kind kind, double x, double y, double z, double vx, double vy, double vz, double delay, double life, double size) {
	}

	private MeleeTrailMath() {
	}

	/** Start and end of the arc of a profile: yaw (degrees, positive to the right of the view) and elevation (degrees, positive up). */
	record Arc(double psi0, double psi1, double th0, double th1) {
	}

	static Arc arc(SwingMotion.Profile p) {
		return switch (p) {
			// from the right to the left across the view, a hair of fall
			case H_R -> new Arc(62, -62, 6, -8);
			case H_L -> new Arc(-62, 62, 6, -8);
			// overhead chops: from high on one side down to low in front
			case V_R -> new Arc(22, -4, 72, -40);
			case V_L -> new Arc(-22, 4, 72, -40);
			// rising cut from low on the right to high in front
			case UPPER -> new Arc(26, -6, -48, 66);
			// the thrust is a line, handled apart
			case STAB -> new Arc(0, 0, 0, 0);
		};
	}

	/** Point of the tip path at arc parameter {@code u} (0..1) for reach 1: unit vector (or, for the thrust, the track in front of the shoulder). */
	public static void tip(SwingMotion.Profile p, double u, double[] out) {
		double s = Math.max(0, Math.min(1, u));
		if (p == SwingMotion.Profile.STAB) {
			out[0] = 0.06;
			out[1] = -0.03;
			out[2] = 0.50 + 0.62 * s;
			return;
		}
		Arc a = arc(p);
		// the middle of the swing is the fast part: an ease in out of the parameter keeps the points dense at both ends of the track
		double e = s * s * (3 - 2 * s);
		double psi = Math.toRadians(a.psi0 + (a.psi1 - a.psi0) * e);
		double th = Math.toRadians(a.th0 + (a.th1 - a.th0) * e);
		out[0] = Math.cos(th) * Math.sin(psi);
		out[1] = Math.sin(th);
		out[2] = Math.cos(th) * Math.cos(psi);
	}

	/** Point of the swept blade: arc parameter {@code u}, fraction {@code f} of the way from the hand to the tip (0..1), absolute reach {@code reach}. */
	public static void blade(SwingMotion.Profile p, double u, double f, double reach, double[] out) {
		tip(p, u, out);
		double r = reach * (HAND + (1.0 - HAND) * Math.max(0, Math.min(1, f)));
		out[0] *= r;
		out[1] *= r;
		out[2] *= r;
	}

	/** Seconds after the hit moment at which the point with arc parameter {@code u} appears. */
	public static double appearAt(double u) {
		return Math.max(0, Math.min(1, u)) * REVEAL;
	}

	/** Deterministic value in 0..1 of the mote {@code i} and channel {@code ch} (no Random: the same swing looks the same on every client). */
	static double unitHash(long seed, int i, int ch) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + ch * 0x94D049BB133111EBL;
		h ^= h >>> 30;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 27;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) / (double) (1L << 53);
	}

	/**
	 * The motes of one swing, exactly {@link #PETALS} + {@link #GLOWS} + {@link #ACCENTS} of them (never more than {@link #BUDGET}). {@code scale} scales the
	 * reach, the sizes and the drift ({@code fx.meleeTrail.scale}); the number of motes does not change with it.
	 */
	public static List<Mote> plan(SwingMotion.Profile p, long seed, double reach, double scale) {
		double k = Math.max(0.05, scale);
		double r = reach * k;
		List<Mote> out = new ArrayList<>(PETALS + GLOWS + ACCENTS);
		double[] a = new double[3];
		double[] b = new double[3];
		// the edge of the blade: glow sprites along the path of the tip (outer 15 percent of the blade), a few on the middle of the blade
		for (int i = 0; i < GLOWS; i++) {
			double u = (i + 0.5) / GLOWS;
			double f = i % 4 == 3 ? 0.55 + 0.2 * unitHash(seed, i, 1) : 0.88 + 0.12 * unitHash(seed, i, 1);
			blade(p, u, f, r, a);
			double d = appearAt(u);
			out.add(new Mote(Kind.GLOW, a[0], a[1], a[2], 0, 0, 0, d, SECONDS - d, (0.42 + 0.22 * unitHash(seed, i, 2)) * k));
		}
		// the petals: scattered over the whole swept blade, drifting along the sweep
		for (int i = 0; i < PETALS; i++) {
			double u = (i + unitHash(seed, i, 3)) / PETALS;
			double f = unitHash(seed, i, 4);
			blade(p, u, f, r, a);
			tip(p, Math.min(1, u + 0.02), b);
			double[] c = new double[3];
			tip(p, Math.max(0, u - 0.02), c);
			double tx = b[0] - c[0];
			double ty = b[1] - c[1];
			double tz = b[2] - c[2];
			double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1e-9) {
				tx = 0;
				ty = 0;
				tz = 1;
				tl = 1;
			}
			double speed = (1.2 + 1.0 * unitHash(seed, i, 5)) * k;
			// a little spread across the track so the blade reads as a ribbon, not a line
			double jx = (unitHash(seed, i, 6) - 0.5) * 0.14 * k;
			double jy = (unitHash(seed, i, 7) - 0.5) * 0.14 * k;
			double jz = (unitHash(seed, i, 8) - 0.5) * 0.14 * k;
			double d = appearAt(u);
			out.add(new Mote(Kind.PETAL, a[0] + jx, a[1] + jy, a[2] + jz, tx / tl * speed, ty / tl * speed, tz / tl * speed, d, Math.max(0.05, SECONDS - d),
					(0.22 + 0.10 * unitHash(seed, i, 9)) * k));
		}
		// two bright flares: one where the track starts (the hit), one at its end
		blade(p, 0.0, 1.0, r, a);
		out.add(new Mote(Kind.ACCENT, a[0], a[1], a[2], 0, 0, 0, 0, 0.12, 0.9 * k));
		blade(p, 1.0, 1.0, r, a);
		out.add(new Mote(Kind.ACCENT, a[0], a[1], a[2], 0, 0, 0, REVEAL, SECONDS - REVEAL, 1.1 * k));
		return out;
	}
}
