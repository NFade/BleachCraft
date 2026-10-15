package dev.minebleach.reiatsutest.core.fx;

/**
 * The bow forms from Reishi (URYU_VFX_STORYBOARD 2.1; key J, SEALED to drawn, {@code draw_animation_seconds} 0.45 s): every time and
 * curve of the effect as closed form functions of the time {@code t} in seconds since the start (the DRAW_START event of the client), so
 * the client code only reads them and the unit test pins the table. Pure: no Minecraft types.
 *
 * <p>Timeline: 0.00 gather (dashes, motes, specks converge on the hand, the pendant brightens x1.6), 0.10 the web wheel opens, 0.18 the
 * limbs shoot out as neon outlines, 0.30 the solid bow fades in (the pendant is hidden) while the outlines fade, 0.38 a bright line sweeps
 * the string, 0.42 settle (the wheel shrinks and fades by 0.55, six flakes fly off the tips, a ring at the hand). The item model
 * (pendant, translucent bow, flex bows) follows {@link #pendantShown}, {@link #bowAlpha} and {@link #flexIndex}.
 */
public final class UryuBowForm {
	/** The draw animation of the item (config {@code draw_animation_seconds} default): the item side of the forming. */
	public static final double DURATION = 0.45;
	/** The world side lasts a little longer (the wheel is gone at 0.55). */
	public static final double TOTAL = 0.60;
	/** The reverse (J again, the bow is put away), quieter and shorter. */
	public static final double REVERSE_DURATION = 0.35;

	// ---- event times (seconds)
	public static final double T_WHEEL = 0.10;
	public static final double T_LIMBS = 0.18;
	public static final double T_SOLID = 0.30;
	public static final double T_STRING = 0.38;
	public static final double T_SETTLE = 0.42;
	/** The wheel is gone at this time. */
	public static final double T_WHEEL_END = 0.55;

	/** Times of the discrete events: gather, wheel, limbs, solid, string, settle (the sounds and the spawns of the client). */
	public static final double[] EVENT_TIMES = {0.0, T_WHEEL, T_LIMBS, T_SOLID, T_STRING, T_SETTLE};
	public static final int EV_GATHER = 0;
	public static final int EV_WHEEL = 1;
	public static final int EV_LIMBS = 2;
	public static final int EV_SOLID = 3;
	public static final int EV_STRING = 4;
	public static final int EV_SETTLE = 5;

	// ---- gather (t = 0)
	public static final int GATHER_DASHES = 40;
	public static final int GATHER_MOTES = 24;
	public static final int GATHER_SPECKS = 12;
	public static final double GATHER_RADIUS = 3.0;
	public static final double GATHER_LIFE = 0.30;
	public static final double GATHER_DASH_SIZE = 0.35;
	public static final double GATHER_DASH_INTENSITY = 0.7;
	/** The pendant glows x1.6 through the item emissive tint while the bow gathers. */
	public static final double PENDANT_BOOST = 1.6;
	public static final double PENDANT_RAMP = 0.10;

	// ---- wheel (0.10 to 0.55)
	public static final double WHEEL_FROM = 0.15;
	public static final double WHEEL_TO = 0.90;
	public static final double WHEEL_GROW = 0.25;
	public static final double WHEEL_SETTLE_SIZE = 0.25;
	public static final double WHEEL_ALPHA = 0.9;
	public static final double WHEEL_REV_PER_S = 0.4;
	/** First person: the wheel must not cover the crosshair. */
	public static final double FP_WHEEL_CAP = 0.55;
	public static final double HALO_SIZE = 1.6;
	public static final double HALO_ALPHA = 0.35;
	public static final double STAR_FLASH_TIME = 0.08;

	// ---- limbs (0.18 to 0.45)
	/** Metres each way before the display scale of the bow in the hand. */
	public static final double LIMB_LENGTH = 0.55;
	public static final double LIMB_GROW = 0.20;
	public static final double LIMB_CORE_WIDTH = 0.04;
	public static final double LIMB_GLOW_WIDTH = 0.18;
	public static final int NOTCHES = 6;
	public static final double NOTCH_STAGGER = 0.02;
	public static final double NOTCH_LIFE = 0.10;
	public static final double NOTCH_SIZE = 0.18;

	// ---- solid bow (0.30 to 0.45)
	public static final double SOLID_FADE = 0.15;
	/** The limbs start bent by this many degrees and relax to their rest curve (the three flex meshes bend 3, 6 and 9 degrees). */
	public static final double BEND_START_DEG = 8.0;
	public static final double BEND_BACK = 1.2;
	public static final double FLEX_STEP_DEG = 3.0;
	public static final int FLEX_MAX = 3;

	// ---- string sweep (0.38 to 0.45)
	public static final double STRING_SWEEP = 0.07;

	// ---- settle (0.42)
	public static final int SETTLE_FLAKES = 6;
	public static final double SETTLE_FLAKE_SPEED = 2.0;
	public static final int SETTLE_FLAKE_LIFE_TICKS = 14;
	public static final double SETTLE_RING_SIZE = 0.8;
	public static final double SETTLE_RING_LIFE = 0.2;

	// ---- reverse (the bow is put away)
	public static final double REVERSE_WHEEL_SIZE = 0.15;
	public static final double REVERSE_WHEEL_TIME = 0.10;
	public static final double REVERSE_OUTLINE_TIME = 0.10;
	public static final int REVERSE_DASHES = 16;
	public static final double REVERSE_DASH_LIFE = 0.25;

	// ---- sounds (storyboard 2.1: vanilla ids, confirmed in U-FX 0)
	/** One sound of the forming: time in seconds from the start, vanilla id, pitch, volume (the owner's; other players hear {@link #REMOTE_VOLUME} times it). */
	public record Cue(double t, String id, double pitch, double volume) {
	}

	/** The sounds of the forming, in time order (the unit test pins ids, ordering and levels). */
	public static final java.util.List<Cue> CUES = java.util.List.of(
			new Cue(0.00, "block.amethyst_block.resonate", 1.6, 0.35),
			new Cue(T_WHEEL, "block.beacon.power_select", 1.9, 0.40),
			new Cue(T_LIMBS, "entity.illusioner.cast_spell", 1.8, 0.30),
			new Cue(T_SOLID, "block.amethyst_block.chime", 1.4, 0.45),
			new Cue(T_STRING, "entity.arrow.shoot", 2.0, 0.15));
	/** The reverse (J again): one quiet resonance. */
	public static final Cue REVERSE_CUE = new Cue(0.0, "block.amethyst_block.resonate", 1.2, 0.25);
	/** Other tracking players: everything except sounds at this share (the storyboard: volume x0.6). */
	public static final double REMOTE_VOLUME = 0.6;
	/** Glow of players who are not the owner: x0.6. */
	public static final double REMOTE_GLOW = 0.6;
	/** Tracking range of the effect in blocks. */
	public static final double RANGE = 48.0;

	private UryuBowForm() {
	}

	// ------------------------------------------------------------------ wheel and halo

	/** Size of the wheel in metres (0 when it is not there); first person caps it so it stays off the crosshair. */
	public static double wheelSize(double t, boolean firstPerson) {
		double s;
		if (t < T_WHEEL || t >= T_WHEEL_END) {
			return 0.0;
		} else if (t < T_SETTLE) {
			s = UryuEase.lerp(WHEEL_FROM, WHEEL_TO, UryuEase.oe((t - T_WHEEL) / WHEEL_GROW));
		} else {
			s = UryuEase.lerp(WHEEL_TO, WHEEL_SETTLE_SIZE, (t - T_SETTLE) / (T_WHEEL_END - T_SETTLE));
		}
		return firstPerson ? Math.min(s, FP_WHEEL_CAP) : s;
	}

	/** 0..1 envelope of the wheel (and of the halo behind it): rises in 0.05 s from 0.10, falls to 0 between 0.42 and 0.55. */
	public static double wheelEnvelope(double t) {
		if (t < T_WHEEL || t >= T_WHEEL_END) {
			return 0.0;
		}
		double in = UryuEase.smooth(T_WHEEL, T_WHEEL + 0.05, t);
		double out = t < T_SETTLE ? 1.0 : 1.0 - UryuEase.smooth(T_SETTLE, T_WHEEL_END, t);
		return in * out;
	}

	public static double wheelAlpha(double t) {
		return WHEEL_ALPHA * wheelEnvelope(t);
	}

	public static double haloAlpha(double t) {
		return HALO_ALPHA * wheelEnvelope(t);
	}

	/** Rotation of the wheel in radians: 0.4 turn per second from the moment it opens. */
	public static double wheelRotation(double t) {
		return t < T_WHEEL ? 0.0 : 2 * Math.PI * WHEEL_REV_PER_S * (t - T_WHEEL);
	}

	// ------------------------------------------------------------------ limbs and notches

	/** Metres each limb has grown (one way, before the display scale): 0 to 0.55 in 0.20 s (easeOutExpo) from 0.18. */
	public static double limbLength(double t) {
		return t < T_LIMBS ? 0.0 : LIMB_LENGTH * UryuEase.oe((t - T_LIMBS) / LIMB_GROW);
	}

	/** Intensity of the outline strips: 1 from 0.18 to 0.30, then fading to 0 at 0.45 as the solid bow takes over. */
	public static double limbAlpha(double t) {
		if (t < T_LIMBS || t >= DURATION) {
			return 0.0;
		}
		return t < T_SOLID ? 1.0 : 1.0 - (t - T_SOLID) / SOLID_FADE;
	}

	/** Time at which notch {@code k} (0..5) pops. */
	public static double notchTime(int k) {
		return T_LIMBS + NOTCH_STAGGER * k;
	}

	/** Position of notch {@code k} as a fraction of the limb length, 1/6 to 1. */
	public static double notchFraction(int k) {
		return (k + 1.0) / NOTCHES;
	}

	/** Intensity of the star glint of notch {@code k}: 1 at its time, 0 after 0.1 s. */
	public static double notchAlpha(int k, double t) {
		double age = t - notchTime(k);
		return age < 0 || age > NOTCH_LIFE ? 0.0 : 1.0 - age / NOTCH_LIFE;
	}

	// ------------------------------------------------------------------ item side

	/** True while the item shows the pendant instead of the bow (before the solid bow starts to fade in). */
	public static boolean pendantShown(double t) {
		return t < T_SOLID;
	}

	/** Emissive multiplier of the pendant while the bow gathers: 1 to 1.6 in 0.10 s, held until the pendant goes. */
	public static double pendantBoost(double t) {
		if (t <= 0) {
			return 1.0;
		}
		return 1.0 + (PENDANT_BOOST - 1.0) * UryuEase.smooth(0.0, PENDANT_RAMP, t);
	}

	/** Alpha of the solid bow: 0 until 0.30, 1 at 0.45. */
	public static double bowAlpha(double t) {
		return UryuEase.clamp((t - T_SOLID) / SOLID_FADE);
	}

	/** True while the item side of the forming runs (the pendant or the fading bow replaces the normal bow). */
	public static boolean itemForming(double t) {
		return t >= 0 && t < DURATION;
	}

	/** The bend of the limbs in degrees: 8 until 0.30, relaxing to 0 at 0.45 with a back ease (never negative). */
	public static double bendDegrees(double t) {
		if (t < T_SOLID) {
			return BEND_START_DEG;
		}
		if (t >= DURATION) {
			return 0.0;
		}
		return Math.max(0.0, BEND_START_DEG * (1.0 - UryuEase.ob((t - T_SOLID) / SOLID_FADE, BEND_BACK)));
	}

	/** Which baked bow to draw: 0 the rest bow, 1 to 3 the flex bows (bent 3, 6, 9 degrees). */
	public static int flexIndex(double t) {
		return (int) UryuEase.clamp(Math.round(bendDegrees(t) / FLEX_STEP_DEG), 0, FLEX_MAX);
	}

	// ------------------------------------------------------------------ string sweep

	/** The bright sweep along the string from one tip to the other: 0 to 1 over 0.07 s from 0.38; -1 when it is not running. */
	public static double stringSweep(double t) {
		if (t < T_STRING || t > T_STRING + STRING_SWEEP) {
			return -1.0;
		}
		return (t - T_STRING) / STRING_SWEEP;
	}

	// ------------------------------------------------------------------ gather geometry

	/** Unit direction of dash {@code i} of {@code n} (a Fibonacci sphere: even over the sphere, the same on every client). */
	public static double[] gatherDirection(int i, int n) {
		double y = 1.0 - 2.0 * (i + 0.5) / n;
		double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
		double phi = i * 2.399963229728653; // golden angle
		return new double[] {Math.cos(phi) * r, y, Math.sin(phi) * r};
	}

	// ------------------------------------------------------------------ reverse

	/** Intensity of the reverse wheel (size 0.15 for 0.1 s): 1 fading to 0. */
	public static double reverseWheelAlpha(double t) {
		return t < 0 || t >= REVERSE_WHEEL_TIME ? 0.0 : 1.0 - t / REVERSE_WHEEL_TIME;
	}

	/** Intensity of the bow outline that reappears for 0.1 s in the reverse. */
	public static double reverseOutlineAlpha(double t) {
		return t < 0 || t >= REVERSE_OUTLINE_TIME ? 0.0 : 1.0 - t / REVERSE_OUTLINE_TIME;
	}

	/** Position of reverse dash {@code i} along the bow (-1 bottom tip to +1 top tip), spread evenly. */
	public static double reverseDashPosition(int i) {
		return REVERSE_DASHES <= 1 ? 0.0 : -1.0 + 2.0 * i / (REVERSE_DASHES - 1.0);
	}
}
