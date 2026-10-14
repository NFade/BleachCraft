package dev.minebleach.reiatsutest.core.fx;

/**
 * The Focus feedback on the body of Uryu (URYU_VFX_STORYBOARD 2.4): what one, two and three stacks look and sound like, the pop when a stack
 * is gained and the burst when it is lost. Tables and closed form functions over the stack count, the quality (the tier) and the time, so
 * the client code only reads them. Pure: no Minecraft types.
 *
 * <p>Layers: 1 = Reishi streams to the bow grip (6 dashes per second) and the bow glows +30 percent; 2 = a thin ring on the ground, 10 dashes
 * per second and three flakes orbiting the body; 3 = the Quincy cross behind the bow, the pentagon outline on the ground, a rim glow around
 * the body, glints on the bow tips and 14 dashes per second. Reduce motion: no orbiting flakes and no rotation. Tier Low: layers 1 and 2 only,
 * dashes halved.
 */
public final class UryuFocusLook {
	public static final int MAX_STACKS = 3;

	// ---- dashes converging on the hand
	public static final double DASH_RADIUS = 1.6;
	public static final double DASH_LIFE = 0.25;
	public static final double DASH_SIZE = 0.3;
	public static final double DASH_ALPHA = 0.6;
	private static final double[] DASH_RATE = {0.0, 6.0, 10.0, 14.0};
	/** Emissive multiplier of the bow by stack count (stack 1: +30 percent). */
	private static final double[] BOW_GLOW = {1.0, 1.3, 1.45, 1.6};

	/** A new level fades in over this long. */
	public static final double LAYER_FADE_IN = 0.2;

	// ---- layer 2: ground ring and orbiting flakes
	public static final double RING_SIZE = 1.8;
	public static final double RING_ALPHA = 0.35;
	public static final double RING_BREATH_LOW = 0.8;
	public static final double RING_BREATH_HZ = 1.0;
	public static final int ORBIT_FLAKES = 3;
	public static final double ORBIT_RADIUS = 0.9;
	public static final double ORBIT_HEIGHT_LOW = 0.5;
	public static final double ORBIT_HEIGHT_HIGH = 1.6;
	public static final double ORBIT_REV_PER_S = 0.12;
	public static final double ORBIT_SIZE = 0.28;
	public static final double ORBIT_ALPHA = 0.55;

	// ---- layer 3: cross, pentagon, rim glow, tips
	public static final double CROSS_SIZE = 0.9;
	public static final double CROSS_ALPHA = 0.5;
	public static final double CROSS_REV_PER_S = 0.2;
	/** First person: the cross sits close to the camera, so it is smaller and fainter. */
	public static final double CROSS_SIZE_FIRST = 0.5;
	public static final double CROSS_ALPHA_FIRST = 0.35;
	public static final double PENTAGON_SIZE = 2.8;
	public static final double PENTAGON_ALPHA = 0.45;
	public static final double PENTAGON_REV_PER_S = -0.15;
	public static final double BODY_GLOW_SIZE = 2.4;
	public static final double BODY_GLOW_ALPHA = 0.18;
	public static final double BODY_GLOW_HEIGHT = 1.0;
	public static final double TIP_SIZE = 0.22;

	// ---- gain and loss
	public static final double POP_STAR_SIZE = 0.5;
	public static final double POP_STAR_LIFE = 0.15;
	public static final int POP_DASHES = 8;
	public static final double POP_DASH_RADIUS = 1.2;
	public static final double POP_DASH_LIFE = 0.12;
	private static final double[] GAIN_PITCH = {1.0, 1.2, 1.5, 1.9};
	public static final double GAIN_VOLUME = 0.4;
	public static final int RESET_DASHES = 12;
	public static final double RESET_RADIUS = 1.6;
	public static final double RESET_LIFE = 0.15;
	public static final double RESET_PITCH = 1.9;
	public static final double RESET_VOLUME = 0.15;

	// ---- hum
	public static final double HUM_RANGE = 24.0;

	private UryuFocusLook() {
	}

	private static int clampStacks(int stacks) {
		return Math.max(0, Math.min(MAX_STACKS, stacks));
	}

	/** Highest layer drawn at a quality (the tier: 1.0 high, 0.5 medium, 0.25 low): low keeps layers 1 and 2 only. */
	public static int maxLayer(double quality) {
		return quality >= 0.5 ? 3 : 2;
	}

	/** Share of the dashes kept at a quality: 1, 0.75, 0.5. */
	public static double dashShare(double quality) {
		return quality >= 1.0 ? 1.0 : quality >= 0.5 ? 0.75 : 0.5;
	}

	/** Dashes per second converging on the hand for a stack count at a quality. */
	public static double dashRate(int stacks, double quality) {
		return DASH_RATE[clampStacks(stacks)] * dashShare(quality);
	}

	/** True when layer {@code layer} (1 to 3) is drawn for {@code stacks} at a quality. */
	public static boolean layerOn(int stacks, int layer, double quality) {
		return clampStacks(stacks) >= layer && layer <= maxLayer(quality);
	}

	/** Emissive multiplier of the bow for a stack count. */
	public static double bowGlow(int stacks) {
		return BOW_GLOW[clampStacks(stacks)];
	}

	/** Fade in of the newest layer: 0 at the gain, 1 after 0.2 s. */
	public static double layerFade(double ageSinceGain) {
		return UryuEase.smooth(0.0, LAYER_FADE_IN, ageSinceGain);
	}

	/** Breathing of the ground ring: 0.8 to 1.0 at 1 Hz (a constant 0.9 with reduce motion). */
	public static double ringBreath(double t, boolean reduceMotion) {
		if (reduceMotion) {
			return 0.5 * (RING_BREATH_LOW + 1.0);
		}
		return RING_BREATH_LOW + (1.0 - RING_BREATH_LOW) * (0.5 + 0.5 * Math.sin(2 * Math.PI * RING_BREATH_HZ * t));
	}

	/** Rotation in radians of a glyph that turns {@code revPerSecond} (0 with reduce motion: static glyphs). */
	public static double rotation(double revPerSecond, double t, boolean reduceMotion) {
		return reduceMotion ? 0.0 : 2 * Math.PI * revPerSecond * t;
	}

	/** Offset (x, y, z) of orbiting flake {@code i} around the body at time {@code t}: radius 0.9, height 0.5 to 1.6. */
	public static double[] orbitOffset(int i, double t) {
		double a = 2 * Math.PI * ((double) i / ORBIT_FLAKES + ORBIT_REV_PER_S * t);
		double bob = 0.5 + 0.5 * Math.sin(2 * Math.PI * 0.2 * t + i * 2.1);
		double h = UryuEase.lerp(ORBIT_HEIGHT_LOW, ORBIT_HEIGHT_HIGH, bob);
		return new double[] {Math.cos(a) * ORBIT_RADIUS, h, Math.sin(a) * ORBIT_RADIUS};
	}

	/** Pitch of the chime when the stack count reaches {@code stacks} (1.2, 1.5, 1.9). */
	public static double gainPitch(int stacks) {
		return GAIN_PITCH[clampStacks(stacks)];
	}

	/** Seconds between two hums at a stack count (2 s at 2 stacks, 1.4 s at 3), 0 when there is none. */
	public static double humInterval(int stacks) {
		return stacks >= 3 ? 1.4 : stacks == 2 ? 2.0 : 0.0;
	}

	public static double humPitch(int stacks) {
		return stacks >= 3 ? 2.0 : 1.8;
	}

	public static double humVolume(int stacks) {
		return stacks >= 3 ? 0.12 : 0.08;
	}

	/** True when the change from {@code from} to {@code to} stacks is a gain (a pop and a chime). */
	public static boolean isGain(int from, int to) {
		return clampStacks(to) > clampStacks(from);
	}

	/** True when it is a loss (a burst of dashes and a glass tick). */
	public static boolean isLoss(int from, int to) {
		return clampStacks(to) < clampStacks(from);
	}
}
