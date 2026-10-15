package dev.minebleach.reiatsutest.core.fx;

/** Numbers of the Quincy shunpo look (Hirenkyaku, storyboard 2.15). Pure math, no Minecraft imports. */
public final class UryuShunpoMath {
	/** The platform ellipse: the long axis lies along the dash. */
	public static final double DISC_LONG = 1.8;
	public static final double DISC_SHORT = 1.0;
	public static final double DISC_LIFE = 0.5;
	/** Paths longer than this get a mid disc (smaller by {@link #MID_SCALE}) at {@link #MID_DELAY}. */
	public static final double MID_MIN_LENGTH = 6.0;
	public static final double MID_SCALE = 0.7;
	public static final double MID_DELAY = 0.05;
	public static final double ARRIVE_DELAY = 0.10;
	public static final double TRAIL_TIME = 0.4;
	public static final double DASH_LIFE = 0.2;
	public static final int DASHES = 20;
	public static final int RIM_SPECKS = 8;
	public static final int ARRIVE_FLAKES = 6;
	public static final double RING_R = 1.6;
	public static final double RING_TIME = 0.25;
	public static final int AFTERIMAGES = 3;
	public static final double AFTERIMAGE_STAGGER = 0.05;
	public static final float AFTERIMAGE_ALPHA = 0.35f;

	private UryuShunpoMath() {
	}

	/** Dashes along the line: 20 on HIGH ({@code maxFlechettes} 160), fewer on lower tiers, half in calm modes; at least 4. */
	public static int dashCount(int maxFlechettes, boolean calm) {
		int n = Math.max(4, Math.min(DASHES, maxFlechettes / 8));
		return calm ? Math.max(4, n / 2) : n;
	}

	/** Afterimages: the tier value capped at 3, 2 in calm modes, never below 1. */
	public static int imageCount(int tierImages, boolean calm) {
		int n = Math.max(1, Math.min(AFTERIMAGES, tierImages));
		return calm ? Math.min(n, 2) : n;
	}

	public static boolean hasMidDisc(double length) {
		return length > MID_MIN_LENGTH;
	}

	/** Brightness of a disc {@code age} seconds after its birth: a fast rise (0.05 s) and a fade to 0 at {@link #DISC_LIFE}. */
	public static double discLevel(double age) {
		if (age < 0.0 || age >= DISC_LIFE) {
			return 0.0;
		}
		double rise = Math.min(1.0, age / 0.05);
		double fade = Math.pow(1.0 - age / DISC_LIFE, 1.5);
		return rise * fade;
	}

	/** Position along the path (0..1) of dash {@code i} of {@code n}; {@code jitter} 0..1 moves it inside its own cell. */
	public static double dashFraction(int i, int n, double jitter) {
		return (i + Math.max(0.0, Math.min(1.0, jitter))) / Math.max(1, n);
	}

	/** Seconds after the start at which dash {@code i} appears (staggered along the path over {@link #TRAIL_TIME}). */
	public static double dashDelay(double fraction) {
		return TRAIL_TIME * Math.max(0.0, Math.min(1.0, fraction));
	}
}
