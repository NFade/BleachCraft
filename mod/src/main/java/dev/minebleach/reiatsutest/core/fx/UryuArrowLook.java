package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.uryu.ArrowSim;
import dev.minebleach.reiatsutest.core.uryu.BowDraw;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;

/**
 * The look of the flying Heilig Pfeil (URYU_VFX_STORYBOARD 2.3, and the Z arrow of 2.6 where the numbers are only a scale): mesh scale, the
 * glow layers, the trail, the flake sparks, the muzzle flash, the steering ring and the two hit variants, as tables of numbers over the arrow
 * kind ({@link ArrowSim.Kind} ordinal), the draw power {@code p} (taken from the speed of the arrow, see {@link #drawPower}) and the Focus level.
 * Pure: no Minecraft types.
 */
public final class UryuArrowLook {
	public static final int BASIC = ArrowSim.Kind.BASIC.ordinal();
	public static final int Z = ArrowSim.Kind.FOCUS.ordinal();
	public static final int RAIN = ArrowSim.Kind.RAIN.ordinal();
	public static final int WING = ArrowSim.Kind.WING.ordinal();

	/** Mesh scale: 1.4 + 0.8 p, 0.4 more per Focus stack, at most 3.0; the Z arrow is 2.6. */
	public static final double SCALE_BASE = 1.4;
	public static final double SCALE_PER_P = 0.8;
	public static final double SCALE_PER_FOCUS = 0.4;
	public static final double SCALE_MAX = 3.0;
	public static final double SCALE_Z = 2.6;

	/** The glow strip along the shaft: 0.9 m plus 1.2 m times p, Q3 at 0.5; the pinpoint at the head: 0.3 m, Q1 at 0.6. */
	public static final double STREAK_BASE = 0.9;
	public static final double STREAK_PER_P = 1.2;
	public static final double STREAK_ALPHA = 0.5;
	public static final double STREAK_WIDTH = 0.14;
	public static final double HEAD_SIZE = 0.3;
	public static final double HEAD_ALPHA = 0.6;

	/** The trail: the last 8 packet positions (14 for the Z arrow, so the homing curve is a swept ribbon), width 0.16 (0.28), head 0.7 to tail 0. */
	public static final int TRAIL_POINTS = 8;
	public static final int TRAIL_POINTS_Z = 14;
	public static final double TRAIL_WIDTH = 0.16;
	public static final double TRAIL_WIDTH_Z = 0.28;
	public static final double TRAIL_HEAD_ALPHA = 0.7;

	/** A flake spark every this many blocks along the path (Focus 2 and 3: 2.0, the Z arrow 1.2); life 8 ticks. */
	public static final double FLAKE_SPACING = 2.0;
	public static final double FLAKE_SPACING_Z = 1.2;
	public static final int FLAKE_LIFE_TICKS = 8;
	public static final double FLAKE_SIZE = 0.32;

	/** The ring of a steering (homing) arrow: size 0.6 (1.0), 0.5 m behind the head, one turn per second. */
	public static final double STEER_RING_SIZE = 0.6;
	public static final double STEER_RING_SIZE_Z = 1.0;
	public static final double STEER_RING_BEHIND = 0.5;
	public static final double STEER_RING_ALPHA = 0.35;

	/** The thin whoosh of a flying arrow: every 6 ticks, within 12 blocks. */
	public static final int WHOOSH_TICKS = 6;
	public static final double WHOOSH_RANGE = 12.0;
	public static final double WHOOSH_PITCH = 2.0;
	public static final double WHOOSH_VOLUME = 0.05;

	// ---- muzzle (the first packet of an arrow)
	public static final double MUZZLE_STAR_BASE = 0.5;
	public static final double MUZZLE_STAR_PER_P = 0.7;
	public static final double MUZZLE_STAR_Z = 1.2;
	public static final double MUZZLE_STAR_LIFE = 0.10;
	public static final double MUZZLE_RING_FROM = 0.3;
	public static final double MUZZLE_RING_TO = 1.2;
	public static final double MUZZLE_RING_TO_Z = 2.0;
	public static final double MUZZLE_RING_LIFE = 0.15;
	public static final int MUZZLE_DASHES = 6;
	public static final double MUZZLE_DASH_SPEED = 10.0;
	public static final double MUZZLE_DASH_LIFE = 0.15;
	public static final double MUZZLE_SHOT_PITCH_BASE = 1.6;
	public static final double MUZZLE_SHOT_PITCH_PER_P = 0.4;
	public static final double MUZZLE_SHOT_VOLUME = 0.5;
	/** The bow flashes (its emissive x2) for this long at the muzzle. */
	public static final double BOW_FLASH_TIME = 0.08;
	public static final double BOW_FLASH_BONUS = 1.0;

	// ---- hit
	public static final double HIT_STAR_BASE = 0.9;
	public static final double HIT_STAR_PER_FOCUS = 0.5;
	public static final double HIT_STAR_LIFE = 0.18;
	public static final double HIT_RAYS_BASE = 1.0;
	public static final double HIT_RAYS_PER_FOCUS = 0.6;
	public static final double HIT_RAYS_LIFE = 0.14;
	public static final int HIT_DASHES_BASE = 10;
	public static final int HIT_DASHES_PER_FOCUS = 6;
	public static final double HIT_DASH_SPEED = 6.0;
	public static final double HIT_DASH_LIFE = 0.18;
	public static final int HIT_FLAKES = 2;
	/** Focus 3: a ring (size 0.5 to 3.0 in 0.3 s) and a short white flash for the shooter. */
	public static final double HIT_RING_FROM = 0.5;
	public static final double HIT_RING_TO = 3.0;
	public static final double HIT_RING_LIFE = 0.3;
	public static final double HIT_FLASH_PEAK = 0.12;
	/** The block hit (no entity): smaller. */
	public static final double BLOCK_STAR = 0.45;
	public static final int BLOCK_DASHES = 6;
	public static final int BLOCK_FLAKES = 4;
	public static final double SCAR_RADIUS = 0.25;
	public static final double SCAR_LIFE = 0.6;
	/** A block hit leaves a scar only on a floor: the height of the hit is within this of a whole number. */
	public static final double FLOOR_TOLERANCE = 0.06;
	/** An IMPACT packet is matched to the arrow that just ended when that arrow was within this many blocks of the hit. */
	public static final double MATCH_DISTANCE = 4.5;
	/** An arrow whose first position is within this many blocks of the local eye counts as the local player's (the flash is for the shooter). */
	public static final double OWN_ARROW_DISTANCE = 6.0;

	// ---- sounds (vanilla ids; the cue time is 0 = the event)
	public static final String WHOOSH_ID = "entity.phantom.flap";

	/** The muzzle: the thin shot (pitch 1.6 + 0.4 p, volume 0.5) and a chime (1.8, 0.3). */
	public static java.util.List<UryuBowForm.Cue> muzzleCues(double p) {
		return java.util.List.of(new UryuBowForm.Cue(0.0, "entity.arrow.shoot", muzzleShotPitch(p), MUZZLE_SHOT_VOLUME),
				new UryuBowForm.Cue(0.0, "block.amethyst_block.chime", 1.8, 0.3));
	}

	/** The hit sounds of an entity hit at a Focus level (Focus 3 adds the glass break) and of a block hit. */
	public static java.util.List<UryuBowForm.Cue> hitCues(int level, boolean entityHit) {
		if (!entityHit) {
			return java.util.List.of(new UryuBowForm.Cue(0.0, "block.amethyst_block.hit", 1.9, 0.3));
		}
		java.util.List<UryuBowForm.Cue> out = new java.util.ArrayList<>();
		out.add(new UryuBowForm.Cue(0.0, "block.amethyst_block.hit", 1.6, 0.6));
		out.add(new UryuBowForm.Cue(0.0, "entity.arrow.hit", 1.8, 0.35));
		if (level >= 3) {
			out.add(new UryuBowForm.Cue(0.0, "block.amethyst_block.break", 1.4, 0.4));
		}
		return out;
	}

	private UryuArrowLook() {
	}

	/**
	 * Spawn points of the flake sparks along a stretch of flight: one every {@code spacing} blocks. {@code travelled} is the distance flown since the
	 * last spark (carried over by the caller), {@code step} the distance of this tick; returns the fractions (0..1) of the step at which a spark sits
	 * and the new carry. Closed form, so a fast arrow does not skip sparks and a slow one does not clump them.
	 */
	public static double[] sparkFractions(double carry, double step, double spacing) {
		if (spacing <= 0 || step <= 1.0E-9) {
			return new double[] {carry + Math.max(0.0, step)};
		}
		java.util.List<Double> f = new java.util.ArrayList<>();
		double d = carry + step;
		double at = spacing - carry;
		while (at <= step + 1.0E-9) {
			f.add(Math.min(1.0, at / step));
			at += spacing;
		}
		d = d - spacing * f.size();
		double[] out = new double[f.size() + 1];
		for (int i = 0; i < f.size(); i++) {
			out[i] = f.get(i);
		}
		out[f.size()] = Math.max(0.0, d);
		return out;
	}

	/**
	 * The draw power of a basic arrow, read back from its speed in blocks per tick (the server launches it at
	 * {@code ARROW_SPEED * speedFactor(p) * focusFactor(stacks)}); every other kind flies at its fixed speed, so 1.
	 */
	public static double drawPower(double speedPerTick, int focus, int kind) {
		if (kind != BASIC) {
			return 1.0;
		}
		double full = UryuBalance.ARROW_SPEED * UryuBalance.focusFactor(focus);
		double share = speedPerTick / full;
		double min = UryuBalance.BOW_MIN_SPEED_SHARE;
		return UryuEase.clamp((share - min) / (1.0 - min));
	}

	/** Round trip check of {@link #drawPower} against the real launch speed of a draw power. */
	public static double launchSpeed(double p, int focus) {
		return UryuBalance.ARROW_SPEED * BowDraw.speedFactor(p) * UryuBalance.focusFactor(focus);
	}

	public static double meshScale(int kind, double p, int focus) {
		if (kind == Z) {
			return SCALE_Z;
		}
		return Math.min(SCALE_MAX, SCALE_BASE + SCALE_PER_P * UryuEase.clamp(p) + SCALE_PER_FOCUS * Math.max(0, Math.min(3, focus)));
	}

	public static double streakLength(double p) {
		return STREAK_BASE + STREAK_PER_P * UryuEase.clamp(p);
	}

	public static int trailPoints(int kind) {
		return kind == Z ? TRAIL_POINTS_Z : kind == RAIN ? 3 : TRAIL_POINTS;
	}

	public static double trailWidth(int kind) {
		return kind == Z ? TRAIL_WIDTH_Z : kind == RAIN ? 0.10 : TRAIL_WIDTH;
	}

	/** Intensity of trail piece {@code k} of {@code n} (0 at the head): 0.7 falling linearly to 0 at the tail. */
	public static double trailAlpha(int k, int n) {
		return n <= 0 ? 0.0 : TRAIL_HEAD_ALPHA * (1.0 - (double) k / n);
	}

	/** Width of trail piece {@code k} of {@code n}: the full width at the head, a quarter of it at the tail. */
	public static double trailPieceWidth(int kind, int k, int n) {
		return trailWidth(kind) * (1.0 - 0.75 * (double) k / Math.max(1, n));
	}

	/** Blocks between two flake sparks, or 0 when the arrow sheds none (Focus below 2 and not a Z arrow). */
	public static double flakeSpacing(int kind, int focus) {
		if (kind == Z) {
			return FLAKE_SPACING_Z;
		}
		return focus >= 2 ? FLAKE_SPACING : 0.0;
	}

	public static double steerRingSize(int kind) {
		return kind == Z ? STEER_RING_SIZE_Z : STEER_RING_SIZE;
	}

	/** True for the kinds that get a muzzle flash at their first packet (the rain has none, the wing arrows come from the wing tips). */
	public static boolean hasMuzzle(int kind) {
		return kind == BASIC || kind == Z;
	}

	public static double muzzleStarSize(int kind, double p) {
		return kind == Z ? MUZZLE_STAR_Z : MUZZLE_STAR_BASE + MUZZLE_STAR_PER_P * UryuEase.clamp(p);
	}

	public static double muzzleRingSize(int kind) {
		return kind == Z ? MUZZLE_RING_TO_Z : MUZZLE_RING_TO;
	}

	public static double muzzleShotPitch(double p) {
		return MUZZLE_SHOT_PITCH_BASE + MUZZLE_SHOT_PITCH_PER_P * UryuEase.clamp(p);
	}

	// ---- hit

	/** The Focus level the hit is drawn at: the Z arrow hits one level higher (2.6), at most 3. */
	public static int hitLevel(int kind, int focus) {
		int f = Math.max(0, Math.min(3, focus));
		return kind == Z ? Math.min(3, f + 1) : f;
	}

	public static double hitStarSize(int level) {
		return HIT_STAR_BASE + HIT_STAR_PER_FOCUS * level;
	}

	public static double hitRaysSize(int level) {
		return HIT_RAYS_BASE + HIT_RAYS_PER_FOCUS * level;
	}

	public static int hitDashes(int level) {
		return HIT_DASHES_BASE + HIT_DASHES_PER_FOCUS * level;
	}

	public static boolean hitRing(int level) {
		return level >= 3;
	}

	/** True when the height of a block hit is on a whole number: a floor (or a ceiling) was struck and the scar can lie on it. */
	public static boolean floorHit(double y) {
		double f = y - Math.floor(y);
		return f <= FLOOR_TOLERANCE || f >= 1.0 - FLOOR_TOLERANCE;
	}
}
