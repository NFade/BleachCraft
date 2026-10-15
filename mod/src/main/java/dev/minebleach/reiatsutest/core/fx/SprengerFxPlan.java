package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import java.util.Locale;
import java.util.Random;

/**
 * The numbers of the Sprenger look (URYU_VFX_STORYBOARD 2.7, canon correction of U-FX 4): five Seele Schneider fall at the corners of a
 * pentagon OUTLINE, five spokes run from a hub to the daggers, the five edges draw one after the other, at 0.8 s the legs are bound and a
 * fence rises, a column charges in the middle, everything dims at 1.75 s, and at the server's explosion a column fills the pentagon, a
 * Zeichen flares on the ground and a ring of dust rolls out. Pure data and functions (no Minecraft types): {@code UryuSprengerFx} reads
 * every time, size and count from here, and {@code SprengerFxPlanTest} pins them. Times are seconds from the TRAP packet unless a function
 * says "since the burst" ({@code a}); lengths are blocks for the reference radius 3 (the explosion table scales with {@code r / 3}).
 */
public final class SprengerFxPlan {
	private SprengerFxPlan() {
	}

	public static final int VERTICES = 5;
	/** The radius the storyboard numbers are written for (the server's {@code SPRENGER_RADIUS}). */
	public static final double REFERENCE_RADIUS = 3.0;

	// ------------------------------------------------------------------ the figure (0.0 to 0.8 s)
	/** The legs are bound / the explosion happens at the server's ticks over 20. */
	public static final double BIND_AT = UryuBalance.SPRENGER_BIND_AT / 20.0;
	public static final double EXPLODE_AT = UryuBalance.SPRENGER_EXPLODE_AT / 20.0;
	/** Daggers: drop time, gap between two throws, start height, model scale of the 0.3 m mesh, hilt height above the floor when standing. */
	public static final double DAGGER_DROP = 0.22;
	public static final double DAGGER_STAGGER = 0.05;
	public static final double DAGGER_HEIGHT = 6.0;
	public static final double DAGGER_SCALE = 3.4;
	public static final double HILT_HEIGHT = 1.1;
	/** Length of the thin streak behind a falling dagger. */
	public static final double FALL_STREAK = 2.5;
	/** Spokes start at 0.30 s and run at 40 blocks per second. */
	public static final double SPOKE_AT = 0.30;
	public static final double SPOKE_SPEED = 40.0;
	/** The five edges draw in order 0-1, 1-2, 2-3, 3-4, 4-0, 0.05 s each, from 0.55 s. */
	public static final double EDGE_AT = 0.55;
	public static final double EDGE_STEP = 0.05;
	/** The floor inside fills with the Q8 blue: from 0.55 s over 0.3 s up to this intensity. */
	public static final double FLOOR_AT = 0.55;
	public static final double FLOOR_TIME = 0.30;
	public static final double FLOOR_PEAK = 0.25;
	/** Diameter of the floor disc (the glow_soft cell is round, so 7.0 covers the circle of radius 3.3 and a bit more). */
	public static final double FLOOR_SIZE = 7.0;

	// ------------------------------------------------------------------ bind and charge (0.8 to 1.75 s)
	public static final double BIND_FLASH_TIME = 0.10;
	public static final double FENCE_RISE = 0.20;
	public static final double FENCE_HEIGHT = 3.0;
	/** Additive alpha of the fence curtains (the client multiplies its own gain, {@code spr.fenceGain}). */
	public static final double FENCE_ALPHA = 0.12;
	/** The rune bands that scroll up the curtains: how many, how fast (periods per second), how tall. */
	public static final int RUNE_BANDS = 3;
	public static final double RUNE_SPEED = 0.6;
	public static final double RUNE_HEIGHT = 0.5;
	/** The hub flash at the bind (a six-point flare, no Zeichen): diameter of the reference pentagon's ring wave, peak, life. */
	public static final double HUB_SIZE = 6.4;
	public static final double HUB_PEAK = 0.7;
	public static final double HUB_LIFE = 0.4;
	/** The ring wave of the bind (hub to just past the daggers): reach as a share of r, time, peak brightness. */
	public static final double WAVE_REACH = 1.15;
	public static final double WAVE_TIME = 0.25;
	public static final double WAVE_PEAK = 0.8;
	/** The edges flash white for this long at the explosion. */
	public static final double EDGE_BLAST_TIME = 0.15;
	/** The charge column: it grows over {@link #CHARGE_TIME} from the bind to the anticipation. */
	public static final double CHARGE_TIME = 0.95;
	public static final double CHARGE_HEIGHT = 4.0;
	public static final double CHARGE_R0 = 0.15;
	public static final double CHARGE_R1 = 0.6;
	public static final double CHARGE_I0 = 0.25;
	public static final double CHARGE_I1 = 0.6;
	/** The dust ring of the charge: puffs on a circle of {@code 1.1 r}, restarted every 0.4 s. */
	public static final int CHARGE_DUST = 28;
	public static final double CHARGE_DUST_EVERY = 0.4;
	public static final double CHARGE_DUST_RADIUS = 1.1;
	/** Glitter rising from the floor: 8 per second while charging. */
	public static final double GLITTER_PER_SECOND = 8.0;
	/** The rising hum: 4 pitches (1.0 to 2.0) 0.25 s apart from the bind. */
	public static final int HUM_STEPS = 4;
	public static final double HUM_EVERY = 0.25;
	public static final double HUM_PITCH0 = 1.0;
	public static final double HUM_PITCH1 = 2.0;
	/** Tremor of the owner's view while charging: amplitude in degrees at the first and at the last step. */
	public static final double TREMOR0 = 0.05;
	public static final double TREMOR1 = 0.20;

	// ------------------------------------------------------------------ anticipation and explosion
	public static final double ANTICIPATION_AT = 1.75;
	public static final double ANTICIPATION_RAMP = 0.04;
	public static final double ANTICIPATION_DIM = 0.2;
	/** The dimmed state holds until here, then the edges relight to {@link #ANTICIPATION_REBOUND} at the explosion. */
	public static final double ANTICIPATION_HOLD_UNTIL = 1.90;
	public static final double ANTICIPATION_REBOUND = 0.5;
	public static final double NEEDLE_RADIUS = 0.08;
	public static final double GRADE_S = 0.4;
	public static final double GRADE_RAMP = 0.15;
	public static final double GRADE_RANGE = 16.0;
	public static final double GRADE_FADE = 0.6;
	/** The explosion: hit stop in ms, flash peaks (normal, early), shake, FOV kick (degrees, owner only), the column. */
	public static final double HITSTOP_MS = 60.0;
	public static final double FLASH = 0.55;
	public static final double FLASH_EARLY = 0.65;
	public static final double SHAKE_AMPLITUDE = 1.0;
	public static final double SHAKE_TIME = 0.5;
	public static final double FOV_KICK = 3.0;
	public static final double FOV_KICK_TIME = 0.4;
	/** The column of the explosion: core radius is 0.87 r, outer r, height reached in 0.12 s, then the width shrinks to 0.3 over 0.5 s. */
	public static final double COLUMN_CORE_FACTOR = 0.87;
	public static final double COLUMN_HEIGHT = 40.0;
	public static final double COLUMN_RISE = 0.12;
	public static final double COLUMN_SHRINK_TO = 0.3;
	public static final double COLUMN_SHRINK_TIME = 0.5;
	public static final double COLUMN_LIFE = 0.75;
	/** The ragged edge: narrower beams at seeded points inside the pentagon. */
	public static final double RAGGED_R0 = 0.5;
	public static final double RAGGED_R1 = 1.0;
	public static final double RAGGED_H0 = 8.0;
	public static final double RAGGED_H1 = 22.0;
	public static final double RAGGED_STAGGER = 0.02;
	/** burst_rays on the ground (diameter, life), the ground shock ring (final diameter factor of r, life), the Zeichen flare (diameter factor of r, life, turns per second). */
	public static final double RAYS_SIZE = 9.0;
	public static final double RAYS_LIFE = 0.3;
	public static final double SHOCK_RING_FACTOR = 6.0;
	public static final double SHOCK_RING_LIFE = 0.4;
	public static final double FLARE_FACTOR = 4.0;
	public static final double FLARE_EARLY_SCALE = 1.2;
	public static final double FLARE_PEAK = 0.9;
	public static final double FLARE_LIFE = 0.6;
	public static final double FLARE_TURNS = 0.4;
	/** The five ray streaks from the pentagram points outward: width, length, life. */
	public static final double RAY_WIDTH = 0.5;
	public static final double RAY_LENGTH = 6.0;
	public static final double RAY_LIFE = 0.25;
	/** After the burst: the fence shatters and the edges start to fade, they fade over 0.3 s; the scar lasts 6 s. */
	public static final double SHATTER_AT = 0.15;
	public static final double EDGE_FADE = 0.30;
	public static final int SHATTER_FLAKES = 20;
	public static final double SCAR_FACTOR = 3.4 / 3.0;
	public static final double SCAR_ALPHA = 0.6;
	public static final double SCAR_LIFE = 6.0;
	public static final double AFTERMATH_FROM = 0.30;
	public static final double AFTERMATH_TO = 1.0;
	public static final double AFTERMATH_GLITTER_PER_SECOND = 20.0;
	public static final double AFTERMATH_DUST_PER_SECOND = 6.0;
	/** How long the state of an exploded pentagon is kept (the longest aftermath effect). */
	public static final double AFTERMATH_END = 1.6;
	/** A pentagon whose BURST never comes (cancelled, no bound entity to tell the client) is let go this long after the expected explosion. */
	public static final double CANCEL_GRACE = 0.25;
	/** Cancelled pentagon: the daggers sink in 0.2 s (the last 0.15 s shrinking), edges and fence fade in 0.3 s, no explosion. */
	public static final double CANCEL_SINK = 0.20;
	public static final double CANCEL_FADE = 0.30;
	public static final double CANCEL_SINK_DEPTH = 1.2;
	/** Early detonation: the core burns white. */
	public static final double EARLY_CORE_INTENSITY = 1.0;
	public static final double NORMAL_CORE_INTENSITY = 0.9;

	// ------------------------------------------------------------------ explosion sounds
	/** One sound of a table: vanilla id, pitch, volume, seconds from the event. */
	public record Sfx(String id, double pitch, double volume, double at) {
	}

	/** The explosion: 1.2 s of low, glassy and heavy sounds, three crystal breaks staggered. */
	public static final Sfx[] EXPLOSION_SOUNDS = {
			new Sfx("entity.generic.explode", 1.2, 1.0, 0.0),
			new Sfx("block.beacon.deactivate", 1.8, 0.5, 0.0),
			new Sfx("item.mace.smash_ground_heavy", 1.0, 0.8, 0.0),
			new Sfx("block.amethyst_cluster.break", 0.8, 0.8, 0.0),
			new Sfx("block.amethyst_cluster.break", 0.8, 0.8, 0.07),
			new Sfx("block.amethyst_cluster.break", 0.8, 0.8, 0.14),
	};
	public static final Sfx SHATTER_SOUND = new Sfx("block.glass.break", 1.4, 0.4, SHATTER_AT);
	public static final Sfx BIND_SOUND_A = new Sfx("block.beacon.activate", 1.4, 0.6, BIND_AT);
	public static final Sfx BIND_SOUND_B = new Sfx("block.amethyst_block.resonate", 0.8, 0.7, BIND_AT);
	public static final Sfx SPOKE_SOUND = new Sfx("block.beacon.power_select", 1.8, 0.4, SPOKE_AT);
	public static final String HUM_SOUND = "block.beacon.ambient";
	public static final double HUM_VOLUME = 0.25;
	public static final String LAND_SOUND = "item.trident.hit_ground";
	public static final double LAND_PITCH = 1.4;
	public static final double LAND_PITCH_STEP = 0.08;
	public static final double LAND_VOLUME = 0.35;
	public static final String EDGE_SOUND = "block.amethyst_cluster.place";
	public static final double EDGE_SOUND_PITCH = 1.4;
	public static final double EDGE_SOUND_VOLUME = 0.4;
	public static final double EDGE_SOUND_STEP = 0.06;

	// ------------------------------------------------------------------ geometry

	/** Angle (radians) of the pentagon vertex {@code i}: -90 degrees + 72 i (vertex 0 points to -z). */
	public static double vertexAngle(int i) {
		return Math.toRadians(-90.0 + 72.0 * i);
	}

	public static double vertexX(double cx, double r, int i) {
		return cx + Math.cos(vertexAngle(i)) * r;
	}

	public static double vertexZ(double cz, double r, int i) {
		return cz + Math.sin(vertexAngle(i)) * r;
	}

	/** Length of one edge of a regular pentagon with circumradius {@code r}. */
	public static double edgeLength(double r) {
		return 2.0 * r * Math.sin(Math.PI / VERTICES);
	}

	/** Distance from the centre to the middle of an edge. */
	public static double apothem(double r) {
		return r * Math.cos(Math.PI / VERTICES);
	}

	/** True when {@code (dx, dz)} (relative to the centre) lies inside the pentagon by at least {@code margin} blocks from every edge. */
	public static boolean insidePentagon(double dx, double dz, double r, double margin) {
		double a = apothem(r);
		for (int i = 0; i < VERTICES; i++) {
			double mid = Math.toRadians(-90.0 + 72.0 * i + 36.0);
			double d = dx * Math.cos(mid) + dz * Math.sin(mid);
			if (d > a - margin) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ the figure

	/** Time the dagger {@code i} starts to fall. */
	public static double dropAt(int i) {
		return DAGGER_STAGGER * i;
	}

	/** Time the dagger {@code i} lands (tip in the ground). */
	public static double landAt(int i) {
		return dropAt(i) + DAGGER_DROP;
	}

	/** Visible length of a spoke at time {@code t}: 0 before 0.30 s, then 40 blocks per second up to the dagger. */
	public static double spokeLength(double t, double r) {
		return clamp(SPOKE_SPEED * (t - SPOKE_AT), 0.0, r);
	}

	/** Time the head of the spokes has arrived (the beads at the daggers light). */
	public static double spokesDoneAt(double r) {
		return SPOKE_AT + r / SPOKE_SPEED;
	}

	/** When edge {@code i} (vertex i to vertex i+1) starts to draw. */
	public static double edgeStart(int i) {
		return EDGE_AT + EDGE_STEP * i;
	}

	/** 0 to 1: how much of edge {@code i} is drawn at time {@code t}. */
	public static double edgeProgress(int i, double t) {
		return clamp((t - edgeStart(i)) / EDGE_STEP, 0.0, 1.0);
	}

	/** The five edges are all drawn at this time. */
	public static double edgesDoneAt() {
		return edgeStart(VERTICES - 1) + EDGE_STEP;
	}

	/** Intensity of the Q8 floor fill (0 to {@link #FLOOR_PEAK}). */
	public static double floorFill(double t) {
		return FLOOR_PEAK * smooth((t - FLOOR_AT) / FLOOR_TIME);
	}

	/** The flash of the five edges at the bind: 1 at 0.8 s, 0 after 0.1 s. */
	public static double bindFlash(double t) {
		double x = (t - BIND_AT) / BIND_FLASH_TIME;
		return x < 0.0 || x > 1.0 ? 0.0 : 1.0 - x;
	}

	/** Pulse of the edges while charging (existing formula): {@code 1 - depth + depth sin(t (6 + 22 k^2))}, depth 0.35, almost still with reduce motion. */
	public static double pulse(double t, double life, boolean reduceMotion) {
		double k = clamp(t / Math.max(0.1, life), 0.0, 1.0);
		double depth = reduceMotion ? 0.08 : 0.35;
		return (1.0 - depth) + depth * Math.sin(t * (6.0 + 22.0 * k * k));
	}

	/** Dimming of everything around the anticipation: 1, then 0.2 from 1.75 s, relighting to 0.5 at the explosion. */
	public static double dim(double t) {
		if (t < ANTICIPATION_AT) {
			return 1.0;
		}
		if (t < ANTICIPATION_AT + ANTICIPATION_RAMP) {
			return lerp(1.0, ANTICIPATION_DIM, (t - ANTICIPATION_AT) / ANTICIPATION_RAMP);
		}
		if (t < ANTICIPATION_HOLD_UNTIL) {
			return ANTICIPATION_DIM;
		}
		return lerp(ANTICIPATION_DIM, ANTICIPATION_REBOUND, clamp((t - ANTICIPATION_HOLD_UNTIL) / (EXPLODE_AT - ANTICIPATION_HOLD_UNTIL), 0.0, 1.0));
	}

	/** Brightness of the edges and spokes at time {@code t} of a pentagon of {@code life} seconds (before the dim). */
	public static double edgeIntensity(double t, double life, boolean reduceMotion) {
		double k = clamp(t / Math.max(0.1, life), 0.0, 1.0);
		double ramp = clamp(t / 0.25, 0.0, 1.0);
		return ramp * (0.55 + 0.45 * k) * pulse(t, life, reduceMotion) + bindFlash(t) * 0.6;
	}

	/** Height of the fence curtains: 0 before the bind, 3.0 after 0.2 s (easeOutCubic). */
	public static double fenceHeight(double t) {
		return FENCE_HEIGHT * oc((t - BIND_AT) / FENCE_RISE);
	}

	/** Position 0..1 up the curtain of rune band {@code k} (a band travels up and starts again). */
	public static double runePhase(double t, int k) {
		double p = RUNE_SPEED * t + (double) k / RUNE_BANDS;
		return p - Math.floor(p);
	}

	/** Brightness of a rune band at a phase: fades in and out so it never pops. */
	public static double runeAlpha(double phase) {
		return Math.sin(Math.PI * clamp(phase, 0.0, 1.0));
	}

	/**
	 * Brightness of the hub flash at the bind (a six-point flare and a ring wave, NEVER the Zeichen pentagram: canon correction of U-FX 4, the
	 * Zeichen belongs to the explosion). Gone after 0.4 s.
	 */
	public static double hubFlare(double t) {
		double x = (t - BIND_AT) / HUB_LIFE;
		return x < 0.0 || x > 1.0 ? 0.0 : HUB_PEAK * (1.0 - x) * (1.0 - x);
	}

	/** Radius of the ring wave that runs from the hub to the daggers at the bind: 0 at 0.8 s, 1.15 r after {@link #WAVE_TIME} (easeOutCubic). */
	public static double bindWaveRadius(double t, double r) {
		return WAVE_REACH * r * oc((t - BIND_AT) / WAVE_TIME);
	}

	/** Brightness of the bind ring wave: 0 before the bind and after the wave, bright while it runs out. */
	public static double bindWave(double t) {
		double x = (t - BIND_AT) / WAVE_TIME;
		return x < 0.0 || x > 1.0 ? 0.0 : WAVE_PEAK * (1.0 - x);
	}

	/** White flash of the five edges at the explosion ({@code since} = seconds since the burst): 1 at once, 0 after {@link #EDGE_BLAST_TIME}. */
	public static double edgeBlast(double since) {
		if (since < 0.0) {
			return 0.0;
		}
		return clamp(1.0 - since / EDGE_BLAST_TIME, 0.0, 1.0);
	}

	// ------------------------------------------------------------------ the charge column

	/** Height of the charging column {@code a} seconds after the bind. */
	public static double chargeHeight(double a) {
		return CHARGE_HEIGHT * iq(a / CHARGE_TIME);
	}

	/** Full width (twice the radius) of the charging column. */
	public static double chargeWidth(double a) {
		return 2.0 * lerp(CHARGE_R0, CHARGE_R1, iq(a / CHARGE_TIME));
	}

	public static double chargeIntensity(double a) {
		return lerp(CHARGE_I0, CHARGE_I1, iq(a / CHARGE_TIME));
	}

	/** Pitch of hum step {@code k} (0 to 3): 1.0 up to 2.0. */
	public static double humPitch(int k) {
		return lerp(HUM_PITCH0, HUM_PITCH1, (double) k / (HUM_STEPS - 1));
	}

	/** Amplitude of tremor step {@code k}: 0.05 growing to 0.2 degrees. */
	public static double tremor(int k) {
		return lerp(TREMOR0, TREMOR1, (double) k / (HUM_STEPS - 1));
	}

	// ------------------------------------------------------------------ the explosion ({@code a} = seconds since the burst)

	/** Height of the column of light {@code a} seconds after the burst: 0 to 40 in 0.12 s (easeOutExpo). */
	public static double columnHeight(double a) {
		return COLUMN_HEIGHT * oe(a / COLUMN_RISE);
	}

	/** Width factor of the column: 1 until it has risen, then down to 0.3 over 0.5 s. */
	public static double columnWidthScale(double a) {
		return lerp(1.0, COLUMN_SHRINK_TO, clamp((a - COLUMN_RISE) / COLUMN_SHRINK_TIME, 0.0, 1.0));
	}

	/** Brightness of the column: full, fading over the last 0.25 s of its life. */
	public static double columnIntensity(double a) {
		return clamp((COLUMN_LIFE - a) / 0.25, 0.0, 1.0);
	}

	public static double coreRadius(double r) {
		return COLUMN_CORE_FACTOR * r;
	}

	/** Diameter of the Zeichen flare of the explosion for a pentagon of radius {@code r}. */
	public static double flareSize(double r, boolean early) {
		return FLARE_FACTOR * r * (early ? FLARE_EARLY_SCALE : 1.0);
	}

	/** Diameter the ground shock ring ends at (the ring of radius 9 for r = 3). */
	public static double shockRingSize(double r) {
		return SHOCK_RING_FACTOR * r;
	}

	/** Length of ray streak {@code a} seconds after the burst. */
	public static double rayLength(double a, double r) {
		return RAY_LENGTH * (r / REFERENCE_RADIUS) * oe(a / RAY_LIFE);
	}

	/** Scar radius for a pentagon of radius {@code r}. */
	public static double scarRadius(double r) {
		return r * SCAR_FACTOR;
	}

	/** One ragged beam inside the pentagon: offset from the centre, radius, height, delay in seconds. */
	public record Ragged(double dx, double dz, double radius, double height, double delay) {
	}

	/** The seeded ragged beams: {@code n} points inside the pentagon (1 block from the edges at most 0.3 for the small ones), staggered 0.02 s. */
	public static Ragged[] ragged(long seed, double r, int n) {
		Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + 77);
		Ragged[] out = new Ragged[n];
		double k = r / REFERENCE_RADIUS;
		for (int i = 0; i < n; i++) {
			double radius = (RAGGED_R0 + rnd.nextDouble() * (RAGGED_R1 - RAGGED_R0)) * k;
			double dx = 0.0;
			double dz = 0.0;
			for (int tries = 0; tries < 40; tries++) {
				double ang = rnd.nextDouble() * Math.PI * 2.0;
				double rad = Math.sqrt(rnd.nextDouble()) * r * 0.9;
				dx = Math.cos(ang) * rad;
				dz = Math.sin(ang) * rad;
				if (insidePentagon(dx, dz, r, radius * 0.5)) {
					break;
				}
				dx = 0.0;
				dz = 0.0;
			}
			double h = (RAGGED_H0 + rnd.nextDouble() * (RAGGED_H1 - RAGGED_H0)) * k;
			out[i] = new Ragged(dx, dz, radius, h, RAGGED_STAGGER * i);
		}
		return out;
	}

	// ------------------------------------------------------------------ cancel

	/** Seconds of pentagon age after which a missing BURST means the pentagon was cancelled. */
	public static double cancelTimeout(double life) {
		return life + CANCEL_GRACE;
	}

	/** Depth the dagger has sunk {@code c} seconds after the cancel (0 to {@link #CANCEL_SINK_DEPTH}). */
	public static double sinkDepth(double c) {
		return CANCEL_SINK_DEPTH * ic(c / CANCEL_SINK);
	}

	/** Brightness factor of the edges, spokes and fence {@code c} seconds after the cancel or {@code c} seconds after the shatter. */
	public static double fadeOut(double c, double fadeTime) {
		return 1.0 - smooth(c / Math.max(1e-3, fadeTime));
	}

	// ------------------------------------------------------------------ screen

	/** Strength of a remote screen effect at distance {@code d} (the 1.4 distance rule): 0.5 x clamp((24 - d) / 16). */
	public static double remoteStrength(double d) {
		return 0.5 * clamp((24.0 - d) / 16.0, 0.0, 1.0);
	}

	/** Screen factor of the explosion for the local player: 1 for the owner and the bound, the distance rule (from the pentagon edge) for everybody else. */
	public static double screenFactor(boolean involved, double distanceToEdge) {
		return involved ? 1.0 : remoteStrength(Math.max(0.0, distanceToEdge));
	}

	/** The grade of the anticipation applies to the owner, the bound and everybody within 16 blocks. */
	public static boolean gradeApplies(boolean involved, double distance) {
		return involved || distance <= GRADE_RANGE;
	}

	// ------------------------------------------------------------------ tiers

	/** Counts per quality tier (storyboard: Low has no glitter, dust x0.5 and the fence alpha only; spawn P 180 at High). */
	public record Detail(int dust, int motes, int stars, int ragged, boolean glitter, boolean runes) {
	}

	public static final Detail HIGH = new Detail(40, 140, 80, 8, true, true);
	public static final Detail MEDIUM = new Detail(30, 100, 56, 6, true, true);
	public static final Detail LOW = new Detail(20, 70, 40, 4, false, false);

	/** The row of a tier name (any case); HIGH for CUSTOM, null and unknown names, like {@code FxConfig.tier()}. */
	public static Detail detail(String tier) {
		if (tier == null) {
			return HIGH;
		}
		return switch (tier.trim().toUpperCase(Locale.ROOT)) {
			case "LOW" -> LOW;
			case "MEDIUM" -> MEDIUM;
			default -> HIGH;
		};
	}

	// ------------------------------------------------------------------ easing and helpers (the FxMath ones, core copies)

	static double clamp(double x, double lo, double hi) {
		return x < lo ? lo : Math.min(x, hi);
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** easeOutCubic. */
	static double oc(double x) {
		double u = 1.0 - clamp(x, 0.0, 1.0);
		return 1.0 - u * u * u;
	}

	/** easeInQuad. */
	static double iq(double x) {
		double c = clamp(x, 0.0, 1.0);
		return c * c;
	}

	/** easeOutExpo. */
	static double oe(double x) {
		double c = clamp(x, 0.0, 1.0);
		return c >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * c);
	}

	/** easeInCubic (sinking). */
	static double ic(double x) {
		double c = clamp(x, 0.0, 1.0);
		return c * c * c;
	}

	static double smooth(double x) {
		double c = clamp(x, 0.0, 1.0);
		return c * c * (3.0 - 2.0 * c);
	}
}
