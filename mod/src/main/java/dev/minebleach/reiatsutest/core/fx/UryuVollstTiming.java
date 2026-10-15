package dev.minebleach.reiatsutest.core.fx;

import java.util.List;

/**
 * Pure tables and curves of the Quincy: Vollstaendig release (BANKAI, effect id 71) and of the bankai state (wings, bands, emblem, floating
 * flakes), URYU_VFX_STORYBOARD 2.10, 2.11, 2.16. No Minecraft types, so every number of the storyboard is unit tested
 * ({@code UryuVollstTimingTest}). The geometry of the wings is {@link UryuWingMath}. The client side: {@code UryuWingFx} (the one shot timeline,
 * the state glow, the end), {@code UryuWingRenderer} (wings, bracers, boots and flakes on the player model, through the bankai pass of
 * {@code UryuQuincyFeature}).
 *
 * <p>Clock: {@code t} is the time in seconds since the release event reached the client.
 */
public final class UryuVollstTiming {
	private UryuVollstTiming() {
	}

	// ------------------------------------------------------------------------------------------ times of table 2.10 (seconds)
	/** Gather: the sky darkens, the lens aura forms, dashes and motes stream in, the world fills with stars. */
	public static final double T_GATHER = 0.00;
	/** Collapse: everything is sucked into the chest in {@link #COLLAPSE_TIME}, then {@link #SILENCE} of black silence. */
	public static final double T_COLLAPSE = 0.50;
	public static final double COLLAPSE_TIME = 0.15;
	public static final double SILENCE = 0.10;
	/** Burst: flash, ground ring, burst rays behind the back, flakes, radial dashes. */
	public static final double T_BURST = 0.65;
	/** The wings unfold (6 plates staggered {@link #PLATE_STAGGER}): from {@link #T_UNFOLD} for {@link #UNFOLD_TIME}. */
	public static final double T_UNFOLD = 0.65;
	public static final double UNFOLD_TIME = 0.60;
	public static final double PLATE_STAGGER = 0.04;
	/** easeOutBack overshoot of the unfold. */
	public static final double UNFOLD_BACK = 1.2;
	/** Folded pose of a plate: laid against the back, this angle about the root (degrees, negative = down) and this fraction of its length. */
	public static final double FOLD_ANGLE_DEG = -55.0;
	public static final double FOLD_LENGTH = 0.15;
	/** The bands slide on (a mask sweep of {@link #BANDS_SWEEP}), the chest emblem pops. */
	public static final double T_BANDS = 0.85;
	public static final double BANDS_SWEEP = 0.35;
	/** The ground sign (zeichen_star r 4, the pentagon r 4.4 inside it). */
	public static final double T_SIGN = 0.90;
	public static final double SIGN_LIFE = 1.5;
	public static final double SIGN_SIZE = 8.0;
	public static final double SIGN_PEAK = 0.7;
	public static final double SIGN_SPIN = 0.15;
	public static final double PENTAGON_RADIUS = 4.4;
	/** The grade eases back to 0 over {@link #GRADE_OUT_TIME} from {@link #T_GRADE_OUT}; the table ends with the settle lock. */
	public static final double T_GRADE_OUT = 1.00;
	public static final double GRADE_OUT_TIME = 1.20;
	public static final double T_SETTLE = 1.25;
	public static final double LOCK = 2.20;
	/** The release timeline lives a little longer than the lock (the safety release of the layers). */
	public static final double LIFETIME = 2.40;

	// ------------------------------------------------------------------------------------------ screen
	public static final double GRADE_S = 0.6;
	public static final double GRADE_RAMP = 0.5;
	public static final double VIGNETTE = 0.35;
	public static final double VIGNETTE_RM = 0.15;
	public static final double VIGNETTE_RAMP = 0.5;
	public static final double VIGNETTE_FADE = 0.6;
	public static final double FLASH = 0.80;
	public static final double FLASH_RM = 0.40;
	public static final double SHAKE_AMPLITUDE = 1.0;
	public static final double SHAKE_TIME = 0.45;
	public static final double FOV_KICK = 3.5;
	public static final double FOV_KICK_TIME = 0.5;

	// ------------------------------------------------------------------------------------------ counts and sizes
	public static final int GATHER_DASHES = 90;
	public static final int GATHER_MOTES = 60;
	public static final int GATHER_SPECKS = 150;
	public static final int GATHER_SPECKS_LOW = 80;
	public static final double GATHER_RADIUS = 10.0;
	public static final double GATHER_SPEED = 14.0;
	public static final double GATHER_LIFE = 0.6;
	public static final double SPECK_BALL_RADIUS = 6.0;
	public static final double LENS_WIDTH = 1.6;
	public static final double LENS_PEAK = 0.35;
	public static final int COLLAPSE_DASHES = 40;
	public static final int BURST_FLAKES = 24;
	public static final double BURST_FLAKE_SPEED = 7.0;
	public static final double BURST_FLAKE_DRAG = 0.92;
	public static final int BURST_FLAKE_LIFE_TICKS = 40;
	public static final int BURST_DASHES = 120;
	public static final double BURST_DASH_SPEED = 10.0;
	public static final double RING_RADIUS = 14.0;
	public static final double RING_TIME = 0.50;
	public static final double RAYS_SIZE = 5.0;
	public static final double RAYS_LIFE = 0.20;
	/** Stardust that erupts along the wing edges while they unfold (200 over {@link #UNFOLD_TIME}). */
	public static final int UNFOLD_SPECKS = 200;
	public static final double UNFOLD_SPECK_SIZE = 0.15;
	public static final int UNFOLD_SPECK_LIFE_TICKS = 20;
	public static final int BAND_STARS = 6;
	public static final double BAND_STAR_SIZE = 0.35;
	public static final double EMBLEM_POP_SIZE = 0.45;
	public static final double EMBLEM_POP_LIFE = 0.15;

	// ------------------------------------------------------------------------------------------ the state (2.11)
	/** Wing glow breathes 0.8 to 1.0 at 0.5 Hz. */
	public static final double BREATH_LOW = 0.8;
	public static final double BREATH_HZ = 0.5;
	/** The chest emblem: size 0.4, Q3 0.6, pulses 0.7 to 1.0 at 0.5 Hz. */
	public static final double EMBLEM_SIZE = 0.4;
	public static final double EMBLEM_INTENSITY = 0.6;
	public static final double EMBLEM_PULSE_LOW = 0.7;
	public static final double EMBLEM_HZ = 0.5;
	/** The faint body light (glow_soft size 3, Q3 0.08). */
	public static final double BODY_LIGHT_SIZE = 3.0;
	public static final double BODY_LIGHT_INTENSITY = 0.08;
	/** Stardust of the state: 14 specks per second per wing, a star4 per second per wing at the tips. */
	public static final double STATE_SPECKS_PER_WING = 14.0;
	public static final double STATE_SPECK_SIZE = 0.12;
	public static final int STATE_SPECK_LIFE_TICKS = 14;
	public static final double STATE_TIP_STARS_PER_WING = 1.0;
	/** The ambient hum for the owner every 3 s. */
	public static final double AMBIENT_PERIOD = 3.0;
	/** Cap: this many Uryu players are fully drawn, the rest wings only. */
	public static final int FULL_PLAYERS = 3;
	/** Wings, glow and flakes are seen within this many blocks. */
	public static final double VIEW_DISTANCE = 64.0;
	/** The bands (bars scroll) {@code 0.2} texture widths per second. */
	public static final double BAND_SCROLL = 0.2;
	/** The bars of the band texture repeat 3 times around the ring. */
	public static final double BAND_PERIOD = 1.0 / 3.0;
	/** The bands fade out over this long when the bankai ends (the wings are gone at once, as flakes). */
	public static final double BANDS_FADE_OUT = 0.25;
	/** The end: the wings break into flakes (6 per plate, speed 3, gravity 0.03, life 30 ticks), 30 specks, the ZK flashes once. */
	public static final int END_FLAKES_PER_PLATE = 6;
	public static final double END_FLAKE_SPEED = 3.0;
	public static final double END_FLAKE_GRAVITY = 0.03;
	public static final int END_FLAKE_LIFE_TICKS = 30;
	public static final int END_SPECKS = 30;
	public static final double END_SIGN_RADIUS = 3.0;
	public static final double END_SIGN_PEAK = 0.6;
	public static final double END_SIGN_LIFE = 0.3;
	/** The end effect and the state fade take this long in total. */
	public static final double END_TIME = 0.6;

	// ------------------------------------------------------------------------------------------ flakes (the floating flechettes of the state)
	public static final int FLAKES_HIGH = 10;
	public static final int FLAKES_MEDIUM = 6;
	/** One drifts out and is replaced every 0.8 s. */
	public static final double FLAKE_REPLACE = 0.8;
	public static final double FLAKE_RADIUS_MIN = 1.6;
	public static final double FLAKE_RADIUS_MAX = 2.6;
	public static final double FLAKE_HEIGHT_MIN = 0.0;
	public static final double FLAKE_HEIGHT_MAX = 2.8;
	public static final double FLAKE_ORBIT_REV = 0.15;
	public static final double FLAKE_BOB = 0.3;
	public static final double FLAKE_SIZE_MIN = 0.3;
	public static final double FLAKE_SIZE_MAX = 0.5;
	public static final double FLAKE_FADE_IN = 0.4;
	public static final double FLAKE_DRIFT = 0.8;
	/** Length of the flechette mesh in metres (uryu_flechette: 0.40 m). */
	public static final double FLAKE_MESH_LENGTH = 0.40;

	/** A sound of the release: time, vanilla sound id, pitch, volume (the storyboard writes "pitch volume"). */
	public record Cue(double t, String sound, double pitch, double volume) {
	}

	/** Every sound of table 2.10, in time order. */
	public static final List<Cue> CUES = List.of(
			new Cue(0.00, "block.beacon.power_select", 1.5, 0.7),
			new Cue(0.00, "block.conduit.activate", 1.2, 0.4),
			new Cue(0.65, "entity.generic.explode", 1.6, 0.6),
			new Cue(0.65, "block.beacon.activate", 1.9, 0.8),
			new Cue(0.65, "entity.warden.sonic_boom", 1.8, 0.4),
			new Cue(0.65, "entity.ender_dragon.flap", 1.5, 0.9),
			new Cue(0.65, "block.amethyst_block.resonate", 1.3, 0.8),
			new Cue(0.70, "block.glass.break", 1.9, 0.2),
			new Cue(0.80, "block.glass.break", 1.9, 0.2),
			new Cue(0.85, "block.amethyst_block.chime", 1.2, 0.5),
			new Cue(0.90, "block.glass.break", 1.9, 0.2),
			new Cue(0.90, "entity.evoker.prepare_attack", 1.8, 0.3),
			new Cue(0.93, "block.amethyst_block.chime", 1.5, 0.5),
			new Cue(1.01, "block.amethyst_block.chime", 1.8, 0.5),
			new Cue(1.25, "block.beacon.ambient", 1.4, 0.4));

	/** The sounds of the end (2.16): glass, the beacon going down, the crystal resonating low. */
	public static final List<Cue> END_CUES = List.of(
			new Cue(0.0, "block.glass.break", 1.7, 0.5),
			new Cue(0.0, "block.beacon.deactivate", 1.4, 0.5),
			new Cue(0.0, "block.amethyst_block.resonate", 0.8, 0.4));

	// ------------------------------------------------------------------------------------------ curves

	/** Ease-out-back with overshoot {@code c1} (0 at 0, 1 at 1). */
	public static double easeOutBack(double x, double c1) {
		double v = Math.max(0.0, Math.min(1.0, x));
		double c3 = c1 + 1.0;
		double u = v - 1.0;
		return 1.0 + c3 * u * u * u + c1 * u * u;
	}

	public static double smoothstep(double e0, double e1, double x) {
		double v = Math.max(0.0, Math.min(1.0, (x - e0) / (e1 - e0)));
		return v * v * (3 - 2 * v);
	}

	/** The grade level (0..1 of {@link #GRADE_S}): ramps in over {@link #GRADE_RAMP}, holds, eases back from {@link #T_GRADE_OUT}. */
	public static double gradeLevel(double t) {
		if (t <= 0) {
			return 0.0;
		}
		double in = smoothstep(0.0, GRADE_RAMP, t);
		double out = 1.0 - smoothstep(T_GRADE_OUT, T_GRADE_OUT + GRADE_OUT_TIME, t);
		return in * out;
	}

	/** Progress 0..1 of the band mask sweep. */
	public static double bandProgress(double t) {
		return Math.max(0.0, Math.min(1.0, (t - T_BANDS) / BANDS_SWEEP));
	}

	/** Width of the soft edge of the band mask (a fraction of the band). */
	public static final double BAND_EDGE = 0.35;

	/**
	 * Opacity of a point of a band for the sweep progress {@code p}: {@code h01} is 0 at the end of the band that is nearest to the hand (or the
	 * sole) and 1 at the inner end. At p = 0 everything is 0, at p = 1 everything is 1, the front moves inward.
	 */
	public static double bandAlpha(double p, double h01) {
		double e = p * (1.0 + BAND_EDGE);
		return Math.max(0.0, Math.min(1.0, (e - h01) / BAND_EDGE));
	}

	/** The U shift of the band texture (in rect widths, 0..BAND_PERIOD) at the FX time {@code now}: the bars scroll, the pattern repeats every period. */
	public static double bandScroll(double now) {
		double s = now * BAND_SCROLL;
		return (s % BAND_PERIOD + BAND_PERIOD) % BAND_PERIOD;
	}

	/**
	 * The scrolled U (as a fraction of the rect width) of a corner of one ring quad. The shift is applied per quad: the whole quad is moved by
	 * {@code shift} and then back by whole periods until its smallest corner lies in the first period, so no corner leaves the rect (the quads
	 * of the bracer are 1/12 of the width) and the bars stay continuous from quad to quad because the texture repeats every period.
	 */
	public static double scrolledU(double u, double quadMinU, double shift) {
		double moved = quadMinU + shift;
		double wrapped = moved - BAND_PERIOD * Math.floor(moved / BAND_PERIOD + 1e-12);
		return u - quadMinU + wrapped;
	}

	/** The emblem level: the pop at {@link #T_BANDS} (white flare), then Q3 pulsing 0.7 to 1.0 at 0.5 Hz; 0 before the bands. */
	public static double emblemLevel(double t, double now) {
		if (t < T_BANDS) {
			return 0.0;
		}
		double fadeIn = smoothstep(T_BANDS, T_BANDS + 0.15, t);
		double pulse = EMBLEM_PULSE_LOW + (1.0 - EMBLEM_PULSE_LOW) * (0.5 + 0.5 * Math.sin(2 * Math.PI * EMBLEM_HZ * now));
		return EMBLEM_INTENSITY * pulse * fadeIn;
	}

	/** The breathing of the wing glow, 0.8 to 1.0 at 0.5 Hz. */
	public static double breath(double now) {
		return BREATH_LOW + (1.0 - BREATH_LOW) * (0.5 + 0.5 * Math.sin(2 * Math.PI * BREATH_HZ * now));
	}

	/** The number of floating flakes at a tier (0 on Low: wings and emblem only). */
	public static int flakeCount(boolean low, boolean medium) {
		return low ? 0 : medium ? FLAKES_MEDIUM : FLAKES_HIGH;
	}

	/** Visibility of the flakes of the state: they appear with the burst. */
	public static double flakeEnvelope(double t) {
		return smoothstep(T_BURST, T_BURST + 0.35, t);
	}

	/**
	 * The flake {@code i} of {@code count} at the FX time {@code now}, in the body frame (x left, y up, z forward, feet at the origin, blocks).
	 * Out: x, y, z, size (blocks), alpha 0..1, spin angle (radians), tilt (radians). The cycle of a flake is {@code count * 0.8} s: it fades in,
	 * orbits (radius 1.6 to 2.6, {@value #FLAKE_ORBIT_REV} rev/s, bobbing), then drifts outward and shrinks away; the next one takes its slot.
	 * A flake is a pure function of (i, count, now).
	 */
	public static void flake(int i, int count, double now, boolean calm, double[] out) {
		double cycle = Math.max(1, count) * FLAKE_REPLACE;
		double shifted = now + i * FLAKE_REPLACE;
		double gen = Math.floor(shifted / cycle);
		double tau = shifted - gen * cycle;
		long seed = (long) gen * 7919L + i * 104729L + 13L;
		double r0 = hash(seed, 1);
		double r1 = hash(seed, 2);
		double r2 = hash(seed, 3);
		double r3 = hash(seed, 4);
		double radius = FLAKE_RADIUS_MIN + (FLAKE_RADIUS_MAX - FLAKE_RADIUS_MIN) * r0;
		double h = FLAKE_HEIGHT_MIN + (FLAKE_HEIGHT_MAX - FLAKE_HEIGHT_MIN) * r1;
		double speed = FLAKE_ORBIT_REV * (calm ? 0.5 : 1.0);
		double ang = 2 * Math.PI * (r2 + speed * now);
		double bob = FLAKE_BOB * (calm ? 0.5 : 1.0) * Math.sin(2 * Math.PI * (0.3 + 0.2 * r3) * now + 6.28 * r2);
		double fadeIn = smoothstep(0.0, FLAKE_FADE_IN, tau);
		double drift = smoothstep(cycle - FLAKE_DRIFT, cycle, tau);
		double r = radius + 1.0 * drift;
		out[0] = Math.cos(ang) * r;
		out[1] = h + bob + 0.4 * drift;
		out[2] = Math.sin(ang) * r;
		out[3] = FLAKE_SIZE_MIN + (FLAKE_SIZE_MAX - FLAKE_SIZE_MIN) * r3;
		out[4] = fadeIn * (1.0 - drift);
		out[5] = 2 * Math.PI * (r1 + 0.1 * now * (r0 < 0.5 ? 1 : -1));
		out[6] = (r2 - 0.5) * 1.2;
	}

	/** Deterministic hash to [0, 1) of (seed, salt). */
	public static double hash(long seed, int salt) {
		long x = seed * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
		x ^= x >>> 30;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 27;
		x *= 0x94D049BB133111EBL;
		x ^= x >>> 31;
		return (x >>> 11) / (double) (1L << 53);
	}

	/**
	 * The time since the release event of a player's wings. {@code eventAge}: seconds since event 71 (negative = none came); {@code bankaiAge}:
	 * seconds since the BANKAI attachment was seen (100 and more = seen settled). Returns t of the table; below 0 means "nothing yet" (the
	 * attachment came and the event may still arrive within {@link #PENDING_WAIT}).
	 */
	public static double releaseTime(double eventAge, double bankaiAge) {
		if (eventAge >= 0) {
			return eventAge; // (the record of an event is dropped when the bankai ends, so it never belongs to an older bankai)
		}
		if (bankaiAge >= 50.0) {
			return 1000.0;
		}
		if (bankaiAge < PENDING_WAIT) {
			return -1.0;
		}
		return T_UNFOLD + (bankaiAge - PENDING_WAIT);
	}

	/** A BANKAI attachment that comes without an event waits this long for it before the wings unfold by themselves. */
	public static final double PENDING_WAIT = 0.25;
}
