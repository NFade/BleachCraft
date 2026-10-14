package dev.minebleach.reiatsutest.core.fx;

import java.util.List;

/**
 * Pure tables and curves of the Blut Arterie release and of the vein overlay (URYU_VFX_STORYBOARD 2.5, 4.2, 4.4; effect id 70). No
 * Minecraft types, so every number of the storyboard is unit tested ({@code UryuBlutTimingTest}). The client side is
 * {@code UryuAuraFx.releaseShikai} (the one shot timeline), {@code UryuQuincyFeature} (the vein overlay on the player model) and the
 * per player state in that feature, which only calls the functions below.
 *
 * <p>Clock: {@code t} is the time in seconds since the release event reached the client. The vein level L (0..1) is the brightness of the
 * overlay: 0 before 0.20 s, ramps with the draw-on sweep, flares at the burst, beats three times, then settles to the state level
 * (0.5, 0.8 at Focus 3). The draw-on itself is the stage of the sweep: the overlay texture exists in {@link #STAGES} versions
 * ({@code uryu_vein/stage_NN.png}), version k shows every vein whose gradient is below (k+1)/STAGES (0 at the fingertips, 1 at the neck).
 */
public final class UryuBlutTiming {
	private UryuBlutTiming() {
	}

	// ------------------------------------------------------------------------------------------ times of the table 2.5 (seconds)
	/** Fists: the dashes converge into the hands, the pentagon draws itself. */
	public static final double T_FISTS = 0.00;
	/** The veins ignite at the fingertips; the sweep to the neck takes {@link #SWEEP_TIME}. */
	public static final double T_IGNITE = 0.20;
	public static final double SWEEP_TIME = 0.35;
	/** Burst: ring, star, dashes, body flash. */
	public static final double T_BURST = 0.55;
	/** Heartbeat: three beats of {@link #BEAT_PERIOD}, then the state level. */
	public static final double T_HEART = 0.90;
	public static final double BEAT_PERIOD = 0.70;
	public static final int BEATS = 3;
	/** Rest: the table ends here (the state level is reached {@link #SETTLE_TIME} after the last beat). */
	public static final double T_REST = 1.20;
	public static final double SETTLE_TIME = 0.40;

	/** Number of overlay texture versions of the draw-on (tools/gen_uryu_textures.py {@code VEIN_STAGES}). */
	public static final int STAGES = 12;
	/** The vein overlay is seen within this many blocks. */
	public static final double VIEW_DISTANCE = 48.0;

	// ------------------------------------------------------------------------------------------ levels
	/** The state level while SHIKAI (arms, neck and chest). */
	public static final double STATE_LEVEL = 0.5;
	/** At Focus 3 the overlay is brighter. */
	public static final double FOCUS3_LEVEL = 0.8;
	/** A shot brightens the overlay to 0.8 (state 0.5) for 0.3 s. */
	public static final double FIRE_LEVEL = 0.8;
	public static final double FIRE_TIME = 0.30;
	/** The lowest level of a heartbeat (it pulses 0.55 to 1.0). */
	public static final double BEAT_LOW = 0.55;
	public static final double BEAT_HIGH = 1.00;
	/** Reduce Motion: the pulse is a constant 0.7 (no beat, no flare, no fire pulse). */
	public static final double RM_CONSTANT = 0.70;
	/** The overlay fades out this long after the state ends (SHIKAI left). */
	public static final double FADE_OUT = 0.40;

	/** 0..1 draw-on progress of the sweep at {@code t} (easeInOutSine over {@link #SWEEP_TIME}, starting at {@link #T_IGNITE}). */
	public static double sweep(double t) {
		return ios((t - T_IGNITE) / SWEEP_TIME);
	}

	/**
	 * The overlay version to draw for a sweep progress: -1 while nothing is lit yet, else 0..{@link #STAGES}-1 (the highest version whose
	 * gradient limit is <= the progress, never below the first one once the sweep started).
	 */
	public static int stage(double sweep) {
		if (sweep <= 1e-6) {
			return -1;
		}
		return Math.max(0, Math.min(STAGES - 1, (int) Math.ceil(sweep * STAGES - 1e-9) - 1));
	}

	/** One heartbeat thump, tau 0..1 over {@link #BEAT_PERIOD}: quick rise to 1.0 in 15 percent, exponential decay back to exactly 0.55 (no step at the next beat). */
	public static double thump(double tau) {
		double x = Math.max(0.0, Math.min(1.0, tau));
		double tail = Math.exp(-(1.0 - 0.15) * 4.0);
		double shape = x < 0.15 ? smooth(x / 0.15) : (Math.exp(-(x - 0.15) * 4.0) - tail) / (1.0 - tail);
		return BEAT_LOW + (BEAT_HIGH - BEAT_LOW) * shape;
	}

	/** End of the heartbeat window (the last beat's end), seconds. */
	public static double heartEnd() {
		return T_HEART + BEATS * BEAT_PERIOD;
	}

	/** Time of beat {@code k} (0 based). */
	public static double beatStart(int k) {
		return T_HEART + k * BEAT_PERIOD;
	}

	/**
	 * The vein level of the release at {@code t} seconds after the event. Before {@link #T_IGNITE} 0; while the sweep runs 0.6 to 1.0 with it;
	 * 1.0 at the burst, easing to {@link #BEAT_LOW} until the heartbeat; the beats; then the {@code stateLevel} over {@link #SETTLE_TIME}.
	 * With {@code reduceMotion} the whole window of the release is a constant {@link #RM_CONSTANT}.
	 */
	public static double releaseLevel(double t, boolean reduceMotion, double stateLevel) {
		if (t < T_IGNITE) {
			return 0.0;
		}
		double settleFrom = heartEnd();
		if (t >= settleFrom + SETTLE_TIME) {
			return stateLevel;
		}
		if (t >= settleFrom) {
			double from = reduceMotion ? RM_CONSTANT : thump(1.0);
			return from + (stateLevel - from) * ios((t - settleFrom) / SETTLE_TIME);
		}
		if (reduceMotion) {
			return RM_CONSTANT;
		}
		if (t < T_BURST) {
			return 0.6 + 0.4 * sweep(t);
		}
		if (t < T_HEART) {
			return BEAT_HIGH + (BEAT_LOW - BEAT_HIGH) * ios((t - T_BURST) / (T_HEART - T_BURST));
		}
		int k = (int) Math.min(BEATS - 1, Math.floor((t - T_HEART) / BEAT_PERIOD + 1e-9));
		return thump((t - beatStart(k)) / BEAT_PERIOD);
	}

	/** The level while the state holds: 0.5, or 0.8 at Focus 3. */
	public static double stateLevel(int focus) {
		return focus >= 3 ? FOCUS3_LEVEL : STATE_LEVEL;
	}

	/** 0..1 envelope of the shot brightening, {@code since} seconds after the shot: rise in 0.045 s, ease out to 0 at {@link #FIRE_TIME}. */
	public static double fireEnvelope(double since) {
		if (since < 0 || since >= FIRE_TIME) {
			return 0.0;
		}
		double x = since / FIRE_TIME;
		return x < 0.15 ? x / 0.15 : 1.0 - ios((x - 0.15) / 0.85);
	}

	/** The level with the shot brightening: the state level rises towards {@code min(1, level + 0.3)} (0.5 gives 0.8). */
	public static double withFire(double level, double envelope) {
		double peak = Math.min(1.0, Math.max(level + (FIRE_LEVEL - STATE_LEVEL), level));
		return level + (peak - level) * envelope;
	}

	/** Fade factor after the state ended, {@code since} seconds ago: 1 to 0 over {@link #FADE_OUT}. */
	public static double fadeOut(double since) {
		return 1.0 - ios(since / FADE_OUT);
	}

	// ------------------------------------------------------------------------------------------ overlay colour (vertex colour of the two passes)
	/** Alpha of the solid translucent pass (it must read on a bright sky: rule 5): 0.85 at the state level, 1.0 from level 0.64. */
	public static double solidAlpha(double level) {
		return Math.max(0.0, Math.min(1.0, 0.30 + 1.1 * level));
	}

	/** Gain of the additive glow pass (the veins bloom at night): 0.7 at the state level, 1.0 from 0.72. */
	public static double glowGain(double level) {
		return Math.max(0.0, Math.min(1.0, 1.4 * level));
	}

	/** True when the overlay is too dim to draw. */
	public static boolean invisible(double level) {
		return level < 0.02;
	}

	// ------------------------------------------------------------------------------------------ model parts and their inflation
	/** The parts of the player model that carry veins. */
	public enum Part { BODY, RIGHT_ARM, LEFT_ARM }

	/** Parts drawn at the tier: FULL = chest and both arms, ARMS (Low) = arms only. */
	public static List<Part> parts(UryuFxTiers.VeinOverlay overlay) {
		return overlay == UryuFxTiers.VeinOverlay.ARMS ? List.of(Part.RIGHT_ARM, Part.LEFT_ARM) : List.of(Part.BODY, Part.RIGHT_ARM, Part.LEFT_ARM);
	}

	/** The outer skin layer (sleeve, jacket) sticks out by this many pixels; the overlay must be outside it or the sleeves hide the veins. */
	public static final double OUTER_LAYER_PX = 0.25;
	/** Extra clearance beyond the outer layer, pixels. */
	public static final double CLEARANCE_PX = 0.05;

	/** Scale on x (width) of an arm: wide 4 px arms 1.16, slim 3 px arms 1.22 (the same absolute reach). */
	public static double armScaleX(boolean slim) {
		return slim ? 1.22 : 1.16;
	}

	/** Scale on z (depth, 4 px) of an arm. */
	public static double armScaleZ() {
		return 1.16;
	}

	/** Scale on x (8 px) and z (4 px) of the torso. */
	public static double bodyScaleX() {
		return 1.08;
	}

	public static double bodyScaleZ() {
		return 1.16;
	}

	/** Pivot shift in pixels that keeps the scaled arm's centre where it was: the cuboid centre is at -1 / -0.5 (right wide / slim) and +1 / +0.5 (left). */
	public static double armPivotShiftX(boolean slim, boolean left) {
		double c = (slim ? 0.5 : 1.0) * (left ? 1.0 : -1.0);
		return -c * (armScaleX(slim) - 1.0);
	}

	/** Pixels the scaled cuboid of {@code size} px reaches beyond the unscaled one on each side. */
	public static double reach(double sizePx, double scale) {
		return sizePx * (scale - 1.0) * 0.5;
	}

	// ------------------------------------------------------------------------------------------ the release timeline (storyboard 2.5)
	/** A sound cue of the release. */
	public record Cue(double t, String sound, double pitch, double volume) {
	}

	/** All sounds of the table, time ordered (the heartbeat x3 at 0.90, 1.60, 2.30). */
	public static final List<Cue> CUES = List.of(
			new Cue(0.00, "block.beacon.power_select", 1.7, 0.5),
			new Cue(0.20, "block.respawn_anchor.charge", 1.6, 0.4),
			new Cue(0.55, "block.beacon.activate", 1.7, 0.7),
			new Cue(0.55, "entity.player.attack.sweep", 1.8, 0.4),
			new Cue(0.90, "entity.warden.heartbeat", 1.6, 0.25),
			new Cue(1.60, "entity.warden.heartbeat", 1.6, 0.25),
			new Cue(2.30, "entity.warden.heartbeat", 1.6, 0.25));

	// 0.00: fists
	public static final int FIST_DASHES = 30;
	public static final double FIST_RADIUS = 1.4;
	/** The dashes need this long to reach the hand. */
	public static final double FIST_TRAVEL = 0.20;
	public static final double PENTAGON_RADIUS = 1.8;
	/** One edge of the pentagon outline per this many seconds. */
	public static final double PENTAGON_EDGE_TIME = 0.04;
	public static final double PENTAGON_INTENSITY = 0.7;
	// 0.20: veins ignite
	public static final int SPARKLES = 6;
	public static final double SPARKLE_SIZE = 0.25;
	public static final double SPARKLE_LIFE = 0.12;
	// 0.55: burst
	public static final double RING_RADIUS = 4.0;
	public static final double RING_TIME = 0.35;
	public static final double STAR_SIZE = 3.4;
	public static final double STAR_PEAK = 0.6;
	/** Revolutions per second of the ground star. */
	public static final double STAR_SPIN = 0.25;
	public static final double STAR_LIFE = 1.3;
	public static final int BURST_DASHES = 30;
	public static final double BURST_SPEED = 7.0;
	public static final double BURST_DRAG = 0.88;
	public static final int BURST_LIFE_TICKS = 12;
	public static final double BODY_FLASH_SIZE = 3.0;
	public static final double BODY_FLASH_PEAK = 0.5;
	public static final double BODY_FLASH_LIFE = 0.12;
	/** Screen: FLASH 0.35 (Reduce Motion cap 0.25, 0.07 s), SHAKE(0.5, 0.25), VIGNETTE dark 0.20 ramp 0.3 (Reduce Motion 0.10), released with a 0.6 s fade at the burst. */
	public static final double FLASH = 0.35;
	public static final double FLASH_RM = 0.25;
	public static final double SHAKE_AMPLITUDE = 0.5;
	public static final double SHAKE_TIME = 0.25;
	public static final double VIGNETTE = 0.20;
	public static final double VIGNETTE_RM = 0.10;
	public static final double VIGNETTE_RAMP = 0.3;
	public static final double VIGNETTE_FADE = 0.6;
	/** Budget of the release: 90 glow sprites, no particles. */
	public static final int GLOW_BUDGET = 90;

	// state aura
	/** Motes per second that drift up from the forearms while SHIKAI, their rise speed in blocks per second and life in ticks. */
	public static final double MOTES_PER_SECOND = 4.0;
	public static final double MOTE_RISE = 0.6;
	public static final int MOTE_LIFE_TICKS = 16;

	/** The count of glow sprites the release spawns at full quality (fist dashes, pentagon beads, ground ring, sparkles, 2 vein traces, burst ring, star, burst dashes, body flash). */
	public static int glowSprites() {
		return FIST_DASHES + 5 /* pentagon beads */ + 1 /* ground ring */ + SPARKLES + 2 /* vein_trace on the forearms */ + 1 /* ring_soft */ + 1 /* star */ + BURST_DASHES + 1 /* flash */;
	}

	/**
	 * The pentagon of the feet outline: vertex {@code k} (0..4) of a regular pentagon of {@code radius} around the origin, the first vertex
	 * pointing along {@code (fx, fz)} (the horizontal facing), counter clockwise seen from above (x east, z south): {@code {x, z}}.
	 */
	public static double[] pentagonVertex(double radius, int k, double fx, double fz) {
		double l = Math.hypot(fx, fz);
		double ux = l < 1e-6 ? 0.0 : fx / l;
		double uz = l < 1e-6 ? 1.0 : fz / l;
		double a = Math.PI * 2.0 * (k % 5) / 5.0;
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new double[] {radius * (ux * c - uz * s), radius * (ux * s + uz * c)};
	}

	/** Start time of the pentagon edge {@code k}: one edge per {@link #PENTAGON_EDGE_TIME}. */
	public static double pentagonEdgeStart(int k) {
		return k * PENTAGON_EDGE_TIME;
	}

	/** Time the outline is closed (all five edges drawn). */
	public static double pentagonClosed() {
		return 5 * PENTAGON_EDGE_TIME;
	}

	private static double smooth(double x) {
		double t = Math.max(0.0, Math.min(1.0, x));
		return t * t * (3 - 2 * t);
	}

	private static double ios(double x) {
		double t = Math.max(0.0, Math.min(1.0, x));
		return -(Math.cos(Math.PI * t) - 1) / 2;
	}
}
