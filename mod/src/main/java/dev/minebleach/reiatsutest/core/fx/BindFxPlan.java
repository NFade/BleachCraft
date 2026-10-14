package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.uryu.BindEnd;

/**
 * The numbers of the Sprenger bind visual (URYU_VFX_STORYBOARD 2.8): per rooted entity a Zeichen under the feet, three rings around the legs
 * and five chains from the dagger beads to the chest, from the bind moment (0.8 s) until the explosion, a damage break or a cancel. Pure
 * data and functions; {@code UryuBindFx} draws them and {@code BindFxPlanTest} pins them.
 */
public final class BindFxPlan {
	private BindFxPlan() {
	}

	/** Entities that get the chains and rings; the others only the foot sigil. */
	public static final int MAX_FULL = 3;
	/** Entities that make the resonating sound while bound, and its period. */
	public static final int SOUND_SLOTS = 2;
	public static final double SOUND_EVERY = 0.8;
	public static final String SOUND_ID = "block.amethyst_block.resonate";
	public static final double SOUND_PITCH = 0.9;
	public static final double SOUND_VOLUME = 0.3;
	/** The visuals fade in over this time after the bind. */
	public static final double RAMP_IN = 0.15;
	/** Foot sigil: size = 0.8 x width + 0.9, alpha 0.6 to 1.0 flickering at 8 Hz, 0.5 turns per second, peak intensity 0.8. */
	public static final double SIGIL_PER_WIDTH = 0.8;
	public static final double SIGIL_BASE = 0.9;
	public static final double SIGIL_FLICKER_HZ = 8.0;
	public static final double SIGIL_PEAK = 0.8;
	public static final double SIGIL_TURNS = 0.5;
	/** Rings around the legs: heights over the feet for a 1.8 m entity, radius for a 0.6 m wide one, intensity, orbiting bead size. */
	public static final double[] RING_HEIGHTS = {0.12, 0.45, 0.78};
	public static final double RING_RADIUS = 0.4;
	public static final double RING_INTENSITY = 0.5;
	public static final double RING_BEAD = 0.18;
	/** Chains: five, width, intensity, sag of the middle point, the speed (turns per second) of the light that runs down them. */
	public static final int CHAINS = 5;
	public static final double CHAIN_WIDTH = 0.28;
	public static final double CHAIN_INTENSITY = 0.5;
	public static final double CHAIN_SAG = 0.3;
	public static final double CHAIN_PULSE_SPEED = 1.5;
	/** Chest height as a share of the entity height. */
	public static final double CHEST = 0.7;

	/** What happens when the root ends, per reason (storyboard 2.8 "Break"). */
	public record EndLook(double sigilFade, boolean snapChains, boolean shatterRings, int flakes, boolean glassSound, double ringFade) {
	}

	/** Damage: the chains snap, 12 flakes burst, the rings shatter outward, the sigil goes out in 0.15 s, glass breaks. */
	public static final EndLook DAMAGE = new EndLook(0.15, true, true, 12, true, 0.25);
	/** The pentagon's own explosion covers the end: everything goes out in 0.1 s. */
	public static final EndLook EXPLOSION = new EndLook(0.10, false, false, 0, false, 0.10);
	/** Time ran out (the same moment as the explosion on the server): a short quiet fade. */
	public static final EndLook EXPIRE = new EndLook(0.25, false, false, 0, false, 0.25);
	/** Cancelled: everything fades in 0.3 s. */
	public static final EndLook CANCEL = new EndLook(0.30, false, false, 0, false, 0.30);

	public static EndLook endLook(BindEnd reason) {
		return switch (reason) {
			case DAMAGE -> DAMAGE;
			case EXPLOSION -> EXPLOSION;
			case EXPIRE -> EXPIRE;
			default -> CANCEL;
		};
	}

	/** Time the chains take to retract when they snap. */
	public static final double SNAP_TIME = 0.12;

	/** Diameter of the sigil under an entity of the given width. */
	public static double sigilSize(double width) {
		return SIGIL_PER_WIDTH * width + SIGIL_BASE;
	}

	/** Flicker factor of the sigil, 0.6 to 1.0 at 8 Hz. */
	public static double sigilFlicker(double t, boolean reduceMotion) {
		if (reduceMotion) {
			return 0.8;
		}
		return 0.6 + 0.4 * (0.5 + 0.5 * Math.sin(2.0 * Math.PI * SIGIL_FLICKER_HZ * t));
	}

	/** Rotation (radians) of the sigil {@code t} seconds after the bind. */
	public static double sigilRotation(double t, boolean reduceMotion) {
		return reduceMotion ? 0.0 : 2.0 * Math.PI * SIGIL_TURNS * t;
	}

	/** Fade in over {@link #RAMP_IN}. */
	public static double rampIn(double t) {
		double x = Math.max(0.0, Math.min(1.0, t / RAMP_IN));
		return x * x * (3.0 - 2.0 * x);
	}

	/** Ring {@code k} height for an entity of height {@code h} (scaled from the 1.8 m reference, kept between 0.6 and 2.0 times). */
	public static double ringHeight(int k, double h) {
		return RING_HEIGHTS[k] * heightScale(h);
	}

	/** Ring radius for an entity of width {@code w}: 0.4 for 0.6 and growing with it. */
	public static double ringRadius(double w) {
		return RING_RADIUS * Math.max(0.8, w / 0.6);
	}

	public static double heightScale(double h) {
		return Math.max(0.6, Math.min(2.0, h / 1.8));
	}

	/** Orbit speed in turns per second of ring {@code k}: alternately +1 and -1. */
	public static double ringTurns(int k) {
		return k % 2 == 0 ? 1.0 : -1.0;
	}

	/** The middle point of a chain from {@code from} to {@code to}: halfway and {@link #CHAIN_SAG} lower (two segments make the sag). */
	public static double[] chainMiddle(double[] from, double[] to) {
		return new double[] {(from[0] + to[0]) * 0.5, (from[1] + to[1]) * 0.5 - CHAIN_SAG, (from[2] + to[2]) * 0.5};
	}

	/** A point at {@code u} (0 to 1) along the two segments from, middle, to. */
	public static double[] chainPoint(double[] from, double[] to, double u) {
		double[] m = chainMiddle(from, to);
		double c = Math.max(0.0, Math.min(1.0, u));
		double[] a = c < 0.5 ? from : m;
		double[] b = c < 0.5 ? m : to;
		double f = c < 0.5 ? c * 2.0 : (c - 0.5) * 2.0;
		return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
	}

	/** Where the light running down chain {@code i} is {@code t} seconds after the bind (0 at the dagger, 1 at the chest), the chains staggered by a fifth. */
	public static double pulseAt(double t, int i) {
		double p = CHAIN_PULSE_SPEED * t + (double) i / CHAINS;
		return p - Math.floor(p);
	}

	/** How much of a snapping chain is still there {@code c} seconds after the damage (1 whole, 0 gone). */
	public static double snapRemaining(double c) {
		return Math.max(0.0, 1.0 - c / SNAP_TIME);
	}

	/** Indices of the (at most {@link #MAX_FULL}) nearest of the given distances, nearest first. */
	public static int[] nearest(double[] distances, int count) {
		int n = Math.min(count, distances.length);
		int[] out = new int[n];
		boolean[] used = new boolean[distances.length];
		for (int k = 0; k < n; k++) {
			int best = -1;
			for (int i = 0; i < distances.length; i++) {
				if (!used[i] && (best < 0 || distances[i] < distances[best])) {
					best = i;
				}
			}
			used[best] = true;
			out[k] = best;
		}
		return out;
	}

	/** Intensity factor of the sigil after the end: 1 while bound, then out over the look's sigil fade. */
	public static double endFade(double sinceEnd, double fade) {
		if (sinceEnd < 0.0) {
			return 1.0;
		}
		double x = Math.max(0.0, Math.min(1.0, sinceEnd / Math.max(1e-3, fade)));
		return 1.0 - x * x * (3.0 - 2.0 * x);
	}

	/** Radius factor of the rings that shatter outward {@code c} seconds after the damage: 1 growing to 3 within the ring fade. */
	public static double ringBurst(double c, double fade) {
		return 1.0 + 2.0 * Math.max(0.0, Math.min(1.0, c / Math.max(1e-3, fade)));
	}
}
