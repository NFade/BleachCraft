package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.uryu.BowDraw;

/**
 * The light of the drawn bow (URYU_VFX_STORYBOARD 2.2: hold right click, 20 ticks to full draw, power {@code p = (u^2 + 2u) / 3},
 * {@code u = ticks / 20}): the string brightens, a pinpoint appears at the nock, Reishi flows into the nock, the tips glint from p 0.5, a ping
 * at full draw and a breathing glow after it. Pure closed form functions of the power (or of the ticks) so the client code only reads them.
 * The p column of the storyboard is the primary one: p 0.5 is reached after 11.6 ticks (the "8 t" next to it in the table does not match
 * the curve).
 */
public final class UryuDrawLight {
	/** Power at which the string is fully bright (0 to 0.1 of the table: 0 to 3 ticks). */
	public static final double STRING_FULL_AT = 0.1;
	/** The pinpoint at the nock. */
	public static final double NOCK_SIZE = 0.12;
	/** Dashes converging on the nock: rate 6 + 26 p per second from a sphere of radius 1.2, life 0.18 s. */
	public static final double DASH_BASE_RATE = 6.0;
	public static final double DASH_RATE_PER_P = 26.0;
	public static final double DASH_RADIUS = 1.2;
	public static final double DASH_LIFE = 0.18;
	/** The glow strip along the arrow: 0.15 m plus 0.45 m times p. */
	public static final double STREAK_BASE = 0.15;
	public static final double STREAK_PER_P = 0.45;
	/** From this power the bow tips glint (twinkle 6 Hz, size 0.22) and a ghost of the wheel sits on the grip (size 0.35, 0.12). */
	public static final double TIPS_AT = 0.5;
	public static final double TIP_SIZE = 0.22;
	public static final double TIP_TWINKLE_HZ = 6.0;
	public static final double GHOST_SIZE = 0.35;
	public static final double GHOST_ALPHA = 0.12;
	/** The full draw ping: ring 0.2 to 0.7 in 0.18 s, star 0.5 for 0.1 s. */
	public static final double PING_RING_FROM = 0.2;
	public static final double PING_RING_TO = 0.7;
	public static final double PING_RING_TIME = 0.18;
	public static final double PING_STAR_SIZE = 0.5;
	public static final double PING_STAR_TIME = 0.10;
	/** The breathing of the glow at full draw: 0.85 to 1.0 at 3 Hz (a constant 0.92 with reduce motion). */
	public static final double BREATH_LOW = 0.85;
	public static final double BREATH_HZ = 3.0;
	public static final double BREATH_REDUCED = 0.92;
	/** Nock distance model: the bow mesh draws the string tips 0.105 behind the grip, the nock follows the real draw (0.04 + 0.26 p), see ObjItemBakedModel. */
	public static final double STRING_BEHIND_GRIP = 0.105;
	public static final double PULL_START = 0.04;
	public static final double PULL_TRAVEL = 0.26;
	/** Display scale of the bow in the hand (third person and first person). */
	public static final double SCALE_THIRD = 1.4;
	public static final double SCALE_FIRST = 1.8;

	private UryuDrawLight() {
	}

	/** The power of a draw held for {@code ticks} (partial ticks allowed): the vanilla curve. */
	public static double power(double ticks) {
		return BowDraw.powerAt(ticks);
	}

	/** The draw time in ticks that gives the power {@code p} (the harness holds the key until it is reached). */
	public static double ticksForPower(double p) {
		p = UryuEase.clamp(p);
		if (p <= 0) {
			return 0.0;
		}
		// u^2 + 2u - 3p = 0
		double u = -1.0 + Math.sqrt(1.0 + 3.0 * p);
		return u * 20.0;
	}

	/** 0 (cool Q2) to 1 (white Q1): the string brightens as it is pulled. */
	public static double stringMix(double p) {
		return UryuEase.clamp(p / STRING_FULL_AT);
	}

	/** Intensity of the pinpoint at the nock: 0 without a draw, full after the first 3 ticks. */
	public static double nockIntensity(double p) {
		return p <= 0 ? 0.0 : UryuEase.clamp(p / STRING_FULL_AT);
	}

	/** Reishi dashes per second flowing into the nock. */
	public static double dashRate(double p) {
		return p <= 0 ? 0.0 : DASH_BASE_RATE + DASH_RATE_PER_P * UryuEase.clamp(p);
	}

	/** Length in metres of the glow strip along the nocked arrow. */
	public static double arrowStreakLength(double p) {
		return STREAK_BASE + STREAK_PER_P * UryuEase.clamp(p);
	}

	public static boolean tipsLit(double p) {
		return p >= TIPS_AT;
	}

	/** Twinkle of a tip glint at time {@code t} (seconds of the FX clock): 0.4 to 1.0 at 6 Hz. */
	public static double tipTwinkle(double t) {
		return 0.7 + 0.3 * Math.sin(2 * Math.PI * TIP_TWINKLE_HZ * t);
	}

	/** Intensity of the ghost wheel on the grip: 0.12 from p 0.5. */
	public static double ghostAlpha(double p) {
		return tipsLit(p) ? GHOST_ALPHA : 0.0;
	}

	public static boolean fullDraw(double p) {
		return p >= 1.0 - 1.0E-9;
	}

	/** The breathing factor of the glow at full draw (before it the glow is at 1). */
	public static double breath(double t, boolean reduceMotion, double p) {
		if (!fullDraw(p)) {
			return 1.0;
		}
		if (reduceMotion) {
			return BREATH_REDUCED;
		}
		return BREATH_LOW + (1.0 - BREATH_LOW) * (0.5 + 0.5 * Math.sin(2 * Math.PI * BREATH_HZ * t));
	}

	/** Size of the full draw ping ring {@code age} seconds after the ping (0 when it is over). */
	public static double pingRingSize(double age) {
		if (age < 0 || age > PING_RING_TIME) {
			return 0.0;
		}
		return UryuEase.lerp(PING_RING_FROM, PING_RING_TO, UryuEase.oc(age / PING_RING_TIME));
	}

	/** Intensity of the ping ring (1 fading to 0 over its time). */
	public static double pingRingAlpha(double age) {
		return age < 0 || age > PING_RING_TIME ? 0.0 : 1.0 - age / PING_RING_TIME;
	}

	/** Intensity of the ping star at the arrowhead (1 fading to 0 over 0.1 s). */
	public static double pingStarAlpha(double age) {
		return age < 0 || age > PING_STAR_TIME ? 0.0 : 1.0 - age / PING_STAR_TIME;
	}

	/** Metres from the grip back to the nock for a draw of power {@code p} and a display scale of the bow in the hand. */
	public static double nockDistance(double p, double displayScale) {
		return displayScale * (STRING_BEHIND_GRIP + PULL_START + PULL_TRAVEL * UryuEase.clamp(p));
	}

	/** Metres from the grip to a bow tip (half the 1.1 m bow) for a display scale. */
	public static double tipDistance(double displayScale) {
		return displayScale * 0.55;
	}
}
