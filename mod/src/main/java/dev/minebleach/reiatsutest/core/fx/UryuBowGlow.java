package dev.minebleach.reiatsutest.core.fx;

/**
 * The emissive tint hook of the bow in the item model (URYU_VFX_STORYBOARD 2.1 pendant x1.6, 2.2 draw, 2.3 muzzle x2, 2.4 Focus +30 percent):
 * how the effects combine into one multiplier of the emissive pass and how that multiplier recolours a quad. The emissive overlay is drawn
 * through a translucent emissive material whose colour alpha is the intensity, so a multiplier below 1 lowers the alpha, and one above 1
 * pushes the colour towards white and adds a second pass. Pure: no Minecraft types.
 */
public final class UryuBowGlow {
	/** Brightness added at full draw. */
	public static final double DRAW_BONUS = 0.25;
	/** The cap of the composed multiplier. */
	public static final double MAX = 2.6;
	/** Share of (k - 1) that is mixed into white per unit above 1. */
	public static final double WHITE_PER_UNIT = 0.6;

	private UryuBowGlow() {
	}

	/**
	 * The multiplier of a drawn bow: the Focus level, the draw power {@code p} (0 when not drawing), and the muzzle flash {@code flashAge}
	 * seconds ago (negative or beyond 0.08 s: none).
	 */
	public static double bow(int focusStacks, double p, double flashAge) {
		double flash = flashAge < 0 || flashAge > UryuArrowLook.BOW_FLASH_TIME ? 0.0
				: UryuArrowLook.BOW_FLASH_BONUS * (1.0 - flashAge / UryuArrowLook.BOW_FLASH_TIME);
		return Math.min(MAX, UryuFocusLook.bowGlow(focusStacks) + DRAW_BONUS * UryuEase.clamp(p) + flash);
	}

	/** True when the multiplier changes nothing (the hook then draws the mesh as it is). */
	public static boolean neutral(double k) {
		return Math.abs(k - 1.0) < 0.01;
	}

	/** Recolours an ARGB tint for the multiplier: below 1 the alpha falls, above 1 the colour moves towards white (alpha kept). */
	public static int argb(int base, double k) {
		int a = (base >>> 24) & 255;
		int r = (base >> 16) & 255;
		int g = (base >> 8) & 255;
		int b = base & 255;
		if (k <= 1.0) {
			a = (int) Math.round(a * Math.max(0.0, k));
		} else {
			double m = Math.min(1.0, (k - 1.0) * WHITE_PER_UNIT);
			r = (int) Math.round(r + (255 - r) * m);
			g = (int) Math.round(g + (255 - g) * m);
			b = (int) Math.round(b + (255 - b) * m);
		}
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Alpha (0..1) of the second emissive pass that adds the brightness above 1. */
	public static double extraPass(double k) {
		return k <= 1.0 ? 0.0 : UryuEase.clamp(k - 1.0);
	}

	/** A colour with its alpha scaled (the translucent fade of the bow while it forms). */
	public static int scaleAlpha(int argb, double f) {
		int a = (int) Math.round(((argb >>> 24) & 255) * UryuEase.clamp(f));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
