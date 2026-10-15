package dev.minebleach.reiatsutest.core.fx;

/**
 * The numbers of the Sternenstaub (URYU_VFX_STORYBOARD 2.14), pure math for {@code UryuStarFx}: the flechette flakes (pop out of the air, fly into a
 * rotating ring, compress into a chain along the aim), the travel of the head along the line, the beam fade, the shock ring schedule and the tier counts.
 * Local frame of the flakes: x = right, y = up, z = forward (the aim), origin = the caster's eye. No Minecraft types.
 */
public final class UryuStarMath {
	/** The charge (key press to release) and the moments inside it. */
	public static final double CHARGE = 0.8;
	public static final double LINE_UP = 0.4;
	public static final double FREEZE_FROM = 0.7;
	/** The flakes pop out over this span and fly in for this long (so the ring stands at 0.4 s). */
	public static final double POP_SPAN = 0.2;
	public static final double FLY_TIME = 0.2;
	/** The ring: radius, distance ahead, revolutions per second; the shaft: length, radius, revolutions per second. */
	public static final double RING_RADIUS = 1.8;
	public static final double RING_AHEAD = 2.5;
	public static final double RING_REV = 0.8;
	public static final double SHAFT_LENGTH = 6.0;
	public static final double SHAFT_RADIUS = 0.25;
	public static final double SHAFT_REV = 2.0;
	/** Flight of the head, blocks per second; the chain stretches behind it. */
	public static final double HEAD_SPEED = 140.0;
	public static final double SHAFT_STRETCH = 0.25;
	public static final double SHAFT_MAX = 14.0;
	/** Beam widths (core, body, outer) and the fade of the tail. */
	public static final double CORE_WIDTH = 0.5;
	public static final double BODY_WIDTH = 2.4;
	public static final double OUTER_WIDTH = 3.6;
	public static final double BEAM_HOLD = 0.4;
	public static final double BEAM_FADE = 0.3;
	/** Shock rings along the line: spacing, the most, grow time, final size. */
	public static final double RING_SPACING = 4.0;
	public static final int RING_MAX = 10;
	public static final double SHOCK_TIME = 0.25;
	public static final double SHOCK_SIZE = 2.2;
	/** Side bars: count, the angles (degrees) from the axis, length, fade. */
	public static final int SIDE_BARS = 6;
	public static final double SIDE_ANGLE = 20.0;
	public static final double SIDE_LENGTH = 12.0;
	public static final double SIDE_FADE = 0.3;
	/** The pre-release stardust, the specks along the line, the specks of the end burst, the remains (at the full tier). */
	public static final int SKY_SPECKS = 160;
	public static final int LINE_SPECKS = 300;
	public static final int BURST_SPECKS = 200;
	public static final int REMAINS_SPECKS = 40;
	public static final double SKY_RADIUS = 12.0;
	/** Fall of the shaft flakes after the head has arrived (blocks per second squared). */
	public static final double FLAKE_FALL = 16.0;
	public static final double FLAKE_LIFE_AFTER = 0.8;
	/** The end burst: the X bar length, its grow time, the ground ring radius and time, the flash by distance. */
	public static final double X_LENGTH = 8.0;
	public static final double X_TIME = 0.25;
	public static final double GROUND_RING_R = 10.0;
	public static final double GROUND_RING_TIME = 0.45;
	public static final double FLASH_FULL = 0.80;
	public static final double FLASH_CAP_CALM = 0.40;
	public static final double FLASH_REACH = 60.0;

	private UryuStarMath() {
	}

	public static double clamp01(double v) {
		return v < 0.0 ? 0.0 : Math.min(v, 1.0);
	}

	/** Smoothstep 0..1. */
	public static double ease(double v) {
		double x = clamp01(v);
		return x * x * (3.0 - 2.0 * x);
	}

	/** A stable hash of (flake, salt) in [0, 1). */
	public static double hash01(int i, int salt) {
		long x = i * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L + 0x1234567L;
		x ^= x >>> 31;
		x *= 0x94D049BB133111EBL;
		x ^= x >>> 29;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 32;
		return (x >>> 11) * (1.0 / (1L << 53));
	}

	/** The number of flakes drawn: the tier value, half of it in Reduce Motion / photosensitive mode. */
	public static int flakeCount(int tierFlakes, boolean calm) {
		int n = Math.max(0, tierFlakes);
		return calm ? (n + 1) / 2 : n;
	}

	/** Specks of a group scaled by the stardust cap of the tier (600 at HIGH). */
	public static int speckCount(int base, int maxStardust) {
		double share = clamp01(maxStardust / 600.0);
		return (int) Math.round(base * share);
	}

	/** Side bars and the X burst of the Low tier are dropped when the flake count is under this. */
	public static boolean sideBars(int tierFlakes) {
		return tierFlakes >= 60;
	}

	/** Seconds since the pop of flake {@code i} of {@code n}, or negative before it. */
	public static double popTime(int i, double t) {
		return t - POP_SPAN * hash01(i, 1);
	}

	/**
	 * Position of flake {@code i} of {@code n} at charge time {@code t} (seconds since the key press), written to {@code out} = {x, y, z, alpha} in the
	 * local frame. From 0.7 s the world holds its breath: the time stops. {@code ringRev} is the ring speed (lower in Reduce Motion).
	 */
	public static void flake(int i, int n, double t, double ringRev, double[] out) {
		double te = Math.min(t, FREEZE_FROM);
		double k = n <= 1 ? 0.0 : (double) i / (n - 1);
		double pop = POP_SPAN * hash01(i, 1);
		double a0 = hash01(i, 2) * Math.PI * 2.0;
		double el = (hash01(i, 3) - 0.5) * 1.2;
		double rad = 3.0 + 5.0 * hash01(i, 4);
		double sx = rad * Math.cos(el) * Math.cos(a0);
		double sy = rad * Math.sin(el);
		double sz = RING_AHEAD * 0.5 + rad * Math.cos(el) * Math.sin(a0);
		double th = Math.PI * 2.0 * (k + ringRev * te);
		double rx = RING_RADIUS * Math.cos(th);
		double ry = RING_RADIUS * Math.sin(th);
		double rz = RING_AHEAD;
		double ph = Math.PI * 2.0 * (SHAFT_REV * te + 3.0 * k);
		double lx = SHAFT_RADIUS * Math.cos(ph);
		double ly = SHAFT_RADIUS * Math.sin(ph);
		double lz = RING_AHEAD + SHAFT_LENGTH * k;
		double fly = ease((te - pop) / FLY_TIME);
		double mix = ease((te - LINE_UP) / 0.2);
		double px = sx + (rx - sx) * fly;
		double py = sy + (ry - sy) * fly;
		double pz = sz + (rz - sz) * fly;
		out[0] = px + (lx - px) * mix;
		out[1] = py + (ly - py) * mix;
		out[2] = pz + (lz - pz) * mix;
		out[3] = clamp01((te - pop) / 0.05);
	}

	/** Local position of flake {@code i} of {@code n} on the chain behind the head after the release ({@code p} = distance of the head along the line, {@code tau} = seconds since the release). Written as {x, y (along the axis perpendicular), back, alpha}: x, y = spiral offset, z = distance behind the head. */
	public static void chain(int i, int n, double p, double tau, double[] out) {
		double k = n <= 1 ? 0.0 : (double) i / (n - 1);
		double ph = Math.PI * 2.0 * (SHAFT_REV * (FREEZE_FROM + tau) + 3.0 * k);
		out[0] = SHAFT_RADIUS * Math.cos(ph);
		out[1] = SHAFT_RADIUS * Math.sin(ph);
		out[2] = shaftLength(p) * (1.0 - k);
		out[3] = 1.0;
	}

	/** Length of the chain when the head is {@code p} blocks out: it stretches behind the head. */
	public static double shaftLength(double p) {
		return Math.min(SHAFT_MAX, SHAFT_LENGTH + SHAFT_STRETCH * Math.max(0.0, p - headStart()));
	}

	/** Where the head is when it leaves: the front of the chain. */
	public static double headStart() {
		return RING_AHEAD + SHAFT_LENGTH;
	}

	/** Distance of the head from the eye {@code tau} seconds after the release on a line of length {@code len}. */
	public static double headDistance(double len, double tau) {
		double s = Math.min(len, headStart());
		return Math.min(len, s + HEAD_SPEED * Math.max(0.0, tau));
	}

	/** Seconds from the release to the arrival of the head at the end point. */
	public static double arrivalTime(double len) {
		return Math.max(0.0, len - Math.min(len, headStart())) / HEAD_SPEED;
	}

	/** The share of the line, from the muzzle, that the tail of the beam has already left: 0 until the beam fades, then it eats the beam toward the head. */
	public static double tailFraction(double len, double tau) {
		double start = arrivalTime(len) + BEAM_HOLD;
		return clamp01((tau - start) / BEAM_FADE);
	}

	/** Seconds after which the whole effect is over (beam gone, flakes fallen, the burst faded). */
	public static double totalTime(double len) {
		return arrivalTime(len) + BEAM_HOLD + BEAM_FADE + FLAKE_LIFE_AFTER;
	}

	/** Seconds after the release at which shock ring number {@code k} (1 based) starts: when the head passes its place. */
	public static double ringStart(int k) {
		return Math.max(0.0, RING_SPACING * k - headStart()) / HEAD_SPEED;
	}

	/** Number of shock rings on a line of this length. */
	public static int ringCount(double len) {
		return Math.max(0, Math.min(RING_MAX, (int) Math.floor(len / RING_SPACING)));
	}

	/** Size of a shock ring {@code age} seconds after it started, 0 to 2.2 in a quarter second. */
	public static double ringSize(double age) {
		return SHOCK_SIZE * ease(age / SHOCK_TIME);
	}

	/** Level of the side bars {@code tau} seconds after the release (they fade in 0.3 s). */
	public static double sideLevel(double tau) {
		return clamp01(1.0 - tau / SIDE_FADE);
	}

	/** The flash at the end point by the distance of the viewer, capped in the calm modes. */
	public static double flashAt(double distance, boolean calm) {
		double f = FLASH_FULL * (0.3 + 0.7 * clamp01(1.0 - distance / FLASH_REACH));
		return calm ? Math.min(f, FLASH_CAP_CALM) : f;
	}

	/** Level of the end-point diamond (0.9 to 0 in 0.4 s) and the flash sphere (1 to 0 in 0.5 s). */
	public static double diamondLevel(double age) {
		return 0.9 * clamp01(1.0 - age / 0.4);
	}

	public static double sphereLevel(double age) {
		return clamp01(1.0 - age / 0.5);
	}
}
