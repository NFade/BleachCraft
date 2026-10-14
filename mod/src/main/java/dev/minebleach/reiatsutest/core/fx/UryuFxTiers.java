package dev.minebleach.reiatsutest.core.fx;

import java.util.Locale;

/**
 * The per tier values of the Uryu effects (URYU_VFX_STORYBOARD 5.1): caps of the flechette particles and of the stardust specks, the
 * number of flakes of the Sternenstaub, the switches of the wing overglow and of the bands, the extent of the vein overlay, the share of
 * the Licht Regen landings that get a splash and the number of afterimages. {@code FxConfig} copies the row of the active {@code fxTier}
 * into the {@code uryu.*} keys (CUSTOM keeps the keys of the file). Pure data, unit tested.
 */
public final class UryuFxTiers {
	public enum VeinOverlay { FULL, ARMS }

	public record Values(int maxFlechettes, int maxStardust, int starFlakes, boolean wingOverglow, boolean bands, VeinOverlay veinOverlay,
			double rainSplashShare, int afterimages) {
	}

	public static final Values HIGH = new Values(160, 600, 120, true, true, VeinOverlay.FULL, 1.0, 3);
	public static final Values MEDIUM = new Values(80, 300, 60, true, true, VeinOverlay.FULL, 0.5, 2);
	public static final Values LOW = new Values(40, 120, 30, false, false, VeinOverlay.ARMS, 0.25, 0);

	/** The wing scale of the config can be 1.5 at most (storyboard 5.1). */
	public static final double WING_SCALE_MAX = 1.5;
	public static final double WING_SCALE_MIN = 0.5;

	private UryuFxTiers() {
	}

	/** The row of a tier name (LOW, MEDIUM, HIGH, any case); HIGH for CUSTOM, null and anything unknown, like {@code FxConfig.tier()}. */
	public static Values of(String tier) {
		if (tier == null) {
			return HIGH;
		}
		return switch (tier.trim().toUpperCase(Locale.ROOT)) {
			case "LOW" -> LOW;
			case "MEDIUM" -> MEDIUM;
			default -> HIGH;
		};
	}

	public static VeinOverlay parseVein(String s) {
		return s != null && s.trim().equalsIgnoreCase("arms") ? VeinOverlay.ARMS : VeinOverlay.FULL;
	}

	public static double clampWingScale(double v) {
		return Math.max(WING_SCALE_MIN, Math.min(WING_SCALE_MAX, v));
	}
}
