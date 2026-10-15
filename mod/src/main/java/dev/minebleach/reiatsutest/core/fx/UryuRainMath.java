package dev.minebleach.reiatsutest.core.fx;

/**
 * The numbers of the Licht Regen square (URYU_VFX_STORYBOARD 2.13), pure math for {@code UryuRainFx}: the world-grid snap of the square, the outline
 * that grows from the corners, the fades of the sky card and the tier counts. No Minecraft types.
 */
public final class UryuRainMath {
	/** Half width of the square, the outline width, the draw time of the outline from the corners to the middles. */
	public static final double HALF = 4.5;
	public static final double OUTLINE_WIDTH = 0.14;
	public static final double OUTLINE_DRAW = 0.25;
	/** The sky card: height over the floor, size (9 + border), peak alpha, fade in time, glow size and alpha. */
	public static final double CARD_HEIGHT = 14.0;
	public static final double CARD_SIZE = 10.5;
	public static final double CARD_ALPHA = 0.35;
	public static final double CARD_RISE = 0.4;
	public static final double CARD_FADE = 0.4;
	public static final double GLOW_SIZE = 12.0;
	public static final double GLOW_ALPHA = 0.18;
	/** The shafts from the corners to the card, the outline fade out. */
	public static final double SHAFT_WIDTH = 0.1;
	public static final double SHAFT_ALPHA = 0.25;
	public static final double OUTLINE_FADE = 0.5;
	/** Glitter fall of the card: per second, size, speed, life. */
	public static final double GLITTER_RATE = 10.0;
	public static final double GLITTER_SPEED = 8.0;
	public static final double GLITTER_LIFE = 1.2;
	/** Haze: per second, size, alpha, life in seconds. */
	public static final double HAZE_RATE = 6.0;
	public static final double HAZE_LIFE = 2.0;
	/** A landing counts when the arrow ended this far over the floor, a landing within this many blocks of the camera always gets the full look. */
	public static final double LAND_HEIGHT = 1.0;
	public static final double NEAR_CAMERA = 4.0;

	private UryuRainMath() {
	}

	/** The square is aligned to the block grid: the centre of the block under the aim point. */
	public static double snap(double v) {
		return Math.floor(v) + 0.5;
	}

	/** Progress 0..1 of the outline drawing {@code age} seconds after the key press (easeOutCubic); the edges run corner to middle. */
	public static double outlineGrow(double age) {
		double t = Math.max(0.0, Math.min(1.0, age / OUTLINE_DRAW));
		double u = 1.0 - t;
		return 1.0 - u * u * u;
	}

	/** Brightness 0..1 of the outline: rises with the drawing, falls over {@link #OUTLINE_FADE} before {@code end}. */
	public static double outlineLevel(double age, double end) {
		return Math.min(1.0, age / 0.1) * clamp01((end - age) / OUTLINE_FADE);
	}

	/** Brightness factor 0..1 of the card, the shafts and the ground glow {@code age} seconds after the release of a rain of {@code life} seconds. */
	public static double cardLevel(double age, double life) {
		return clamp01(age / CARD_RISE) * clamp01((life - age) / CARD_FADE);
	}

	/** Position 0..1 of the bead that runs along an edge (once per second). */
	public static double bead(double age) {
		double f = age % 1.0;
		return f < 0 ? f + 1.0 : f;
	}

	/** The landing of a rain arrow is inside the square (with a margin) and not higher than {@link #LAND_HEIGHT} over the floor. */
	public static boolean isLanding(double cx, double y0, double cz, double x, double y, double z) {
		double m = HALF + 0.75;
		return Math.abs(x - cx) <= m && Math.abs(z - cz) <= m && y - y0 <= LAND_HEIGHT && y - y0 >= -2.0;
	}

	/**
	 * How many of the landings get full sprites. Tier 1 (High) all, below 0.5 (Low) one in three; a landing near the camera always counts.
	 * @param n running number of the landing, @param tier 0..1
	 */
	public static boolean landingDrawn(int n, double tier, boolean near) {
		return near || tier >= 0.5 || n % 3 == 0;
	}

	/** One landing in six (and every near one) also puffs; Low tier only the near ones. */
	public static boolean landingPuff(int n, double tier, boolean near) {
		return near || (tier >= 0.5 && n % 6 == 0);
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
