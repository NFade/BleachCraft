package dev.minebleach.reiatsutest.core.fx;

import dev.minebleach.reiatsutest.core.swarm.V3;
import dev.minebleach.reiatsutest.core.uryu.SeeleCombo;
import dev.minebleach.reiatsutest.core.uryu.UryuBalance;
import java.util.List;

/**
 * Pure tables and maths of the Seele Schneider combo visuals (URYU_VFX_STORYBOARD 2.9, step U-FX 5): WHEN things happen (the times of the three
 * slashes come from {@link SeeleCombo}, so the look can never drift from the server), WHERE (the plane and the arc of each slash from the look,
 * the crescent, the blade basis) and HOW MUCH (counts, widths, sounds, screen effects). {@code UryuSeeleFx} (client) turns it into glow sprites,
 * strips, particles and the blade mesh. No Minecraft types here, so every row is unit tested ({@code SeeleFxMathTest}).
 *
 * <p>Frame of a slash: the packet gives the eye position and the look; the sweep runs in the plane spanned by the look and a tilted "right" axis
 * (slash 1 and 2: +-18 degrees of tilt, right to left and back; slash 3: tilt 90, an overhead chop from above to below), over the 100 degree cone
 * of the server. The Reishi blade is the long {@code uryu_seele_long} mesh, drawn at {@link #BLADE_SCALE}; the energy crescent that the stroke
 * sheds flies out from the blade radius to the reach.
 */
public final class SeeleFxMath {
	// ---------------------------------------------------------------------------------------------------- timings (seconds from the lunge event)
	/** The sweep of one slash (the blade crosses the whole cone), smoothstep. */
	public static final double SWEEP = 0.16;
	/** Seconds the blade needs to turn from where the last stroke ended to the new sweep. */
	public static final double BLEND_IN = 0.06;
	/** The hilt flies from the hip to the free hand (the short dagger leaving the sleeve). */
	public static final double DRAW_HAND = 0.10;
	/** The long blade grows from the hilt: starts here, takes {@link #GROW}, ease out expo. */
	public static final double GROW_FROM = 0.04;
	public static final double GROW = 0.08;
	/** The blade breaks up this long after the lunge event (0.65 s of the last slash plus 0.2 s of rest), taking {@link #DISSOLVE}. */
	public static final double DISSOLVE_AT = 0.85;
	public static final double DISSOLVE = 0.25;
	/** Life of the combo timeline (the harness freezes the FX clock on it). */
	public static final double COMBO_LIFETIME = 1.25;
	/** Seconds a trail point, an outer wave point and the crescent echo live. */
	public static final double INNER_LIFE = 0.28;
	public static final double OUTER_LIFE = 0.30;
	public static final double OUTER_RISE = 0.20;
	public static final double ECHO_LIFE = 0.25;

	// ---------------------------------------------------------------------------------------------------- blade
	public static final double BLADE_MESH_LEN = 0.9;
	/** The mesh is 0.9 m: at 1.5 the blade reads from third person (a 1.35 m Reishi blade); {@code uryu.seeleBlade} tunes it. */
	public static final double BLADE_SCALE = 1.5;
	/** Distance from the pivot (the shoulder of the free arm) to the hilt. */
	public static final double HILT_DIST = 0.45;
	public static final double SHOULDER_SIDE = 0.30;
	public static final double SHOULDER_DOWN = 0.30;
	/** First person: the free hand in camera space (side x, down y, forward z), the sweep pivot of 2.9 "camera + 0.35 right, -0.25 up". */
	public static final double FP_SIDE = 0.35;
	public static final double FP_DOWN = 0.25;
	public static final double FP_FORWARD = 0.15;
	/** The hip the dagger slides out of, below the pivot. */
	public static final double HIP_DOWN = 0.70;
	/** The crescent sprite (128 x 32): the apex sits 8 px above the centre of the cell, in cell widths. */
	public static final double CRESCENT_APEX = 8.0 / 128.0;

	// ---------------------------------------------------------------------------------------------------- lunge
	public static final double[] LUNGE_HEIGHTS = {0.35, 0.95, 1.55};
	public static final int LUNGE_DASHES = 12;
	public static final int START_DASHES = 8;
	public static final double AFTERIMAGE_GAP = 0.04;
	public static final double AFTERIMAGE_STEP = 0.45;
	public static final double AFTERIMAGE_ALPHA = 0.35;
	public static final double AFTERIMAGE_LIFE = 0.30;
	public static final double DISC_SIZE = 1.8;
	public static final double DISC_LIFE = 0.5;

	private SeeleFxMath() {
	}

	/** Length of the lunge strips: the 1.5 block run plus a tail. */
	public static double lungeLength() {
		return UryuBalance.SEELE_LUNGE + 0.5;
	}

	/** Seconds from the lunge event of slash {@code index} (0..2): 0.15, 0.40, 0.65. */
	public static double slashTime(int index) {
		return SeeleCombo.slashTick(Math.max(0, Math.min(UryuBalance.SEELE_SLASHES - 1, index))) / 20.0;
	}

	/** When the blade starts to break up if slash {@code lastIndex} began at time {@code slashBorn}: the 0.2 s of rest of the storyboard. */
	public static double dissolveStart(double slashBorn) {
		return slashBorn + (DISSOLVE_AT - slashTime(UryuBalance.SEELE_SLASHES - 1));
	}

	// ---------------------------------------------------------------------------------------------------- slash profiles
	/**
	 * One stroke of the combo. {@code tiltDeg} of the sweep plane about the look, {@code sign} the direction (+1 from the player's right to the
	 * left, -1 back), {@code reachScale} on the server reach, {@code crescentScale} the size of the crescent echo, {@code wide} a second wider
	 * crescent behind it, {@code chop} the vertical overhead stroke with the ground impact.
	 */
	public record Profile(int index, double tiltDeg, double sign, double reachScale, double crescentScale, boolean wide, boolean chop, double widthMax) {
	}

	private static final Profile[] PROFILES = {
			new Profile(0, 18.0, 1.0, 1.00, 1.0, false, false, 0.55),
			new Profile(1, -18.0, -1.0, 1.05, 1.0, false, false, 0.55),
			new Profile(2, 90.0, 1.0, 1.00, 1.4, true, true, 0.55 * 1.4)
	};

	public static Profile profile(int index) {
		return PROFILES[Math.max(0, Math.min(PROFILES.length - 1, index))];
	}

	/**
	 * The sweep frame: unit {@code look}, the unit in-plane axis {@code plane} (the blade runs from {@code +plane} to {@code -plane} over the cone),
	 * the unit {@code normal} of the swing plane and the half opening angle in radians.
	 */
	public record Frame(V3 look, V3 plane, V3 normal, double halfCone) {
		/** Direction of the blade at sweep progress {@code u} (0 start, 1 end), unit. */
		public V3 dir(double u) {
			double ang = (1.0 - 2.0 * clamp01(u)) * halfCone;
			return look.scale(Math.cos(ang)).add(plane.scale(Math.sin(ang))).normalizedOr(look);
		}
	}

	public static Frame frame(V3 lookIn, Profile p, double coneDeg) {
		V3 look = lookIn.normalizedOr(new V3(0, 0, 1));
		V3 up = Math.abs(look.y()) > 0.95 ? new V3(1, 0, 0) : new V3(0, 1, 0);
		V3 right = look.cross(up).normalizedOr(new V3(1, 0, 0));
		V3 top = right.cross(look).normalizedOr(new V3(0, 1, 0));
		double tilt = Math.toRadians(p.tiltDeg());
		V3 plane = right.scale(Math.cos(tilt)).add(top.scale(Math.sin(tilt))).scale(p.sign()).normalizedOr(right);
		V3 normal = look.cross(plane).normalizedOr(top);
		return new Frame(look, plane, normal, Math.toRadians(coneDeg / 2.0));
	}

	/** The cone of the server as the frame of {@code index}. */
	public static Frame serverFrame(V3 look, int index) {
		return frame(look, profile(index), UryuBalance.SEELE_CONE_DEG);
	}

	/**
	 * The sweep progress (0..1) at which the blade crosses the direction {@code toTarget} (from the pivot to a target): the angle of the target in
	 * the swing plane against the look, mapped to the cone (0 = the start side, 1 = the end side), clamped. A target on the look is crossed at 0.5.
	 */
	public static double crossProgress(Frame f, V3 toTarget) {
		V3 t = toTarget.normalizedOr(f.look());
		double ang = Math.atan2(t.dot(f.plane()), t.dot(f.look()));
		return clamp01(0.5 - ang / (2.0 * f.halfCone()));
	}

	// ---------------------------------------------------------------------------------------------------- easing
	public static double clamp01(double x) {
		return x < 0 ? 0 : Math.min(x, 1);
	}

	public static double smooth(double x) {
		double u = clamp01(x);
		return u * u * (3 - 2 * u);
	}

	/** Sweep progress (0..1) at {@code age} seconds into a slash. */
	public static double progress(double age) {
		return smooth(age / SWEEP);
	}

	/** Seconds into the sweep at which the blade has reached progress {@code u} (the inverse of {@link #progress}). */
	public static double passTime(double u) {
		double target = clamp01(u);
		double lo = 0;
		double hi = 1;
		for (int i = 0; i < 24; i++) {
			double mid = 0.5 * (lo + hi);
			if (smooth(mid) < target) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return 0.5 * (lo + hi) * SWEEP;
	}

	static double easeOutExpo(double x) {
		double u = clamp01(x);
		return u >= 1 ? 1 : 1 - Math.pow(2, -10 * u);
	}

	static double easeOutCubic(double x) {
		double u = 1 - clamp01(x);
		return 1 - u * u * u;
	}

	static double easeInOutSine(double x) {
		return -(Math.cos(Math.PI * clamp01(x)) - 1) / 2;
	}

	// ---------------------------------------------------------------------------------------------------- blade
	/** How much of the blade has grown at {@code t} seconds from the lunge event (0 before {@link #GROW_FROM}, 1 after {@link #GROW}). */
	public static double bladeGrow(double t) {
		return easeOutExpo((t - GROW_FROM) / GROW);
	}

	/** 1 until {@code dissolveStart}, then the blade shortens from the tip to nothing over {@link #DISSOLVE}. */
	public static double bladeKeep(double t, double dissolveStart) {
		return 1.0 - easeInOutSine((t - dissolveStart) / DISSOLVE);
	}

	/** Visible fraction of the blade length. */
	public static double bladeLength(double t, double dissolveStart) {
		return bladeGrow(t) * bladeKeep(t, dissolveStart);
	}

	/** Progress of the hilt from the hip to the hand at {@code t}. */
	public static double hiltRise(double t) {
		return easeInOutSine(t / DRAW_HAND);
	}

	/** Which side of the player (along his right axis) the free hand is on: -1 for a right hander (the left hand), +1 for a left hander. */
	public static double offhandSide(boolean rightHanded) {
		return rightHanded ? -1.0 : 1.0;
	}

	/** Basis of the blade mesh: {@code {r, d, u}}, local x to {@code r} (the flat face normal, the swing plane normal), local y along the blade, local z to {@code u = r x d}. */
	public static V3[] bladeBasis(V3 dirIn, V3 normalIn) {
		V3 d = dirIn.normalizedOr(new V3(0, 0, 1));
		V3 r = normalIn.sub(d.scale(normalIn.dot(d))).normalizedOr(fallbackPerp(d));
		return new V3[] {r, d, r.cross(d)};
	}

	private static V3 fallbackPerp(V3 d) {
		V3 ref = Math.abs(d.y()) > 0.95 ? new V3(1, 0, 0) : new V3(0, 1, 0);
		return ref.cross(d).normalizedOr(new V3(1, 0, 0));
	}

	/** Blends the blade direction from the one the last stroke ended with to the sweep direction over {@link #BLEND_IN}. */
	public static V3 blendDir(V3 from, V3 to, double age) {
		if (from == null) {
			return to;
		}
		double k = easeInOutSine(age / BLEND_IN);
		return from.scale(1 - k).add(to.scale(k)).normalizedOr(to);
	}

	// ---------------------------------------------------------------------------------------------------- trails
	/** Number of segments of an arc: tier 0 (LOW) 6, 1 (MEDIUM) 9, 2 (HIGH) 12 (2.9: 12, tier Low 6). */
	public static int segments(int tier) {
		return tier <= 0 ? 6 : tier == 1 ? 9 : 12;
	}

	/** Width of the ribbon at fraction {@code f} (0 tail, 1 head): tapering to a point at the tail. */
	public static double trailWidth(double f, double widthMax) {
		return widthMax * (0.12 + 0.88 * Math.pow(clamp01(f), 0.8));
	}

	/** The white core of the ribbon (Q1, 0.12 wide at the head). */
	public static double coreWidth(double f) {
		return 0.12 * (0.25 + 0.75 * clamp01(f));
	}

	/** Brightness of a blade trail point {@code since} seconds after the blade passed it. */
	public static double innerAlpha(double since) {
		if (since < 0) {
			return 0;
		}
		return Math.max(0, 1 - Math.pow(since / INNER_LIFE, 1.4));
	}

	/** Radius of an outer wave point: it leaves the blade radius when the blade passes and runs out to the reach in {@link #OUTER_RISE}. */
	public static double outerRadius(double since, double rIn, double rOut) {
		return rIn + (rOut - rIn) * easeOutCubic(since / OUTER_RISE);
	}

	/** Brightness of an outer wave point {@code since} seconds after the blade passed: a fast rise, then a fade. */
	public static double outerAlpha(double since) {
		if (since < 0 || since > OUTER_LIFE) {
			return 0;
		}
		return Math.min(1.0, since / 0.04) * (1 - Math.pow(since / OUTER_LIFE, 1.5));
	}

	/** The crescent sprite echo of the whole stroke: appears as the sweep ends, then fades over {@link #ECHO_LIFE}. */
	public static double echoAlpha(double age) {
		double rise = clamp01((age - 0.55 * SWEEP) / (0.45 * SWEEP));
		double fall = age <= SWEEP ? 0 : clamp01((age - SWEEP) / ECHO_LIFE);
		return rise * (1 - Math.pow(fall, 1.2));
	}

	/** Radius of the outer wave at the reach: 0.85 of it, as the placeholder (the cone of the server ends at the reach). */
	public static double outerRadiusMax(double reach) {
		return reach * 0.85;
	}

	/** Radius of the blade tip trail: hilt distance plus the blade. */
	public static double innerRadius(double bladeScale) {
		return HILT_DIST + BLADE_MESH_LEN * bladeScale;
	}

	/** Width of the crescent sprite (its chord) for an arc of radius {@code radius} and half angle {@code halfCone}. */
	public static double crescentSize(double radius, double halfCone, double scale) {
		return 2.0 * radius * Math.sin(halfCone) * scale;
	}

	/** Centre of the crescent quad: the apex of the sprite lies at the distance {@code radius} from the pivot along {@code mid}. */
	public static V3 crescentCentre(V3 pivot, V3 mid, double radius, double size) {
		return pivot.add(mid.scale(radius - CRESCENT_APEX * size));
	}

	/** Tangent and the in-plane normal axis of the {@code PLANE} quads of the glow batch (same maths as {@code Emitter.plane}). */
	public static V3[] planeBasis(V3 nIn) {
		V3 n = nIn.normalizedOr(new V3(0, 1, 0));
		double tx = -n.z();
		double tz = n.x();
		double tl = Math.hypot(tx, tz);
		V3 t = tl < 1.0E-4 ? new V3(1, 0, 0) : new V3(tx / tl, 0, tz / tl);
		V3 b0 = new V3(n.y() * t.z() - n.z() * t.y(), n.z() * t.x() - n.x() * t.z(), n.x() * t.y() - n.y() * t.x());
		return new V3[] {t, b0};
	}

	/** The rotation about the normal that turns the sprite's up (v) axis to the in-plane direction {@code want}. */
	public static double planeRot(V3 n, V3 want) {
		V3[] tb = planeBasis(n);
		return Math.atan2(-want.dot(tb[0]), want.dot(tb[1]));
	}

	/** The v (up) axis of a plane quad rotated by {@code rot}. */
	public static V3 planeV(V3 n, double rot) {
		V3[] tb = planeBasis(n);
		return tb[0].scale(-Math.sin(rot)).add(tb[1].scale(Math.cos(rot)));
	}

	/** First person: the width of a ribbon within the reach of the camera is clipped (2.9: 0.5 within 1.5 blocks), growing back to the full width beyond. */
	public static double nearClip(double width, double distToCamera) {
		return Math.min(width, 0.5 + Math.max(0, distToCamera - 1.5));
	}

	/** Where the ground crack of the chop lies: this far in front of the pivot (the arc ends in the floor), never beyond the reach. */
	public static double crackDistance(double reach) {
		return Math.min(2.4, reach * 0.7);
	}

	// ---------------------------------------------------------------------------------------------------- hits
	/** Sparks of one target: {@code burst} the burst_rays size, {@code star} the star4 size, {@code flakes} flechette particles, {@code dashes} radial dashes (the knockback burst), a ground ring. */
	public record Hit(double burst, double star, double glow, int flakes, int dashes, boolean ring) {
	}

	public static Hit hit(int index) {
		return index >= UryuBalance.SEELE_SLASHES - 1 ? new Hit(1.4, 0.9, 2.4, 8, 8, true) : new Hit(1.1, 0.7, 1.8, 6, 0, false);
	}

	/** The ring on the ground of the knockback burst: grows from {@link #RING_FROM} to {@link #RING_TO} blocks in {@link #RING_LIFE} seconds. */
	public static final double RING_FROM = 0.2;
	public static final double RING_TO = 2.2;
	public static final double RING_LIFE = 0.25;
	public static final double CRACK_RADIUS = 1.0;
	/** Flakes shed along the arc of a stroke (not a hit): per slash. */
	public static final int ARC_FLAKES = 3;
	/** Dissolve: flechette flakes and dashes flying back to the hand. */
	public static final int DISSOLVE_FLAKES = 8;
	public static final int DISSOLVE_DASHES = 12;

	// ---------------------------------------------------------------------------------------------------- sounds and screen
	public record Cue(double t, String id, double pitch, double volume) {
	}

	/** The lunge: the throw and the sweep of 2.9 (t = 0). */
	public static List<Cue> lungeCues() {
		return List.of(new Cue(0.0, "item.trident.throw", 1.6, 0.5), new Cue(0.0, "entity.player.attack.sweep", 1.7, 0.5));
	}

	/**
	 * The client layer of a stroke. The server already plays the vanilla sweep at every slash (pitch 1.5 + 0.15 index, volume 0.9), so the sweep of
	 * the storyboard is not repeated here: the chime and a thin whoosh on the first two, the heavy blow on the third.
	 */
	public static List<Cue> slashCues(int index) {
		return switch (Math.max(0, Math.min(2, index))) {
			case 0 -> List.of(new Cue(0.0, "block.amethyst_block.chime", 1.9, 0.4), new Cue(0.0, "item.trident.throw", 2.0, 0.2));
			case 1 -> List.of(new Cue(0.0, "block.amethyst_block.chime", 2.1, 0.35), new Cue(0.0, "item.trident.throw", 2.0, 0.2));
			default -> List.of(new Cue(0.0, "entity.player.attack.strong", 1.2, 0.8), new Cue(0.0, "item.mace.smash_air", 1.8, 0.4));
		};
	}

	/** A target hit by a stroke: the crit and the amethyst hit of 2.9. */
	public static List<Cue> hitCues(int index) {
		return List.of(new Cue(0.0, "entity.player.attack.crit", 1.4, 0.6), new Cue(0.0, "block.amethyst_block.hit", 1.8, 0.5));
	}

	public static Cue dissolveCue() {
		return new Cue(0.0, "block.amethyst_block.break", 1.9, 0.2);
	}

	/** Screen effects of a stroke: shake amplitude (degrees) and time, hitstop of the hit in ms; the lunge has the FOV kick. */
	public record Screen(double shakeDeg, double shakeSecs, double hitstopMs) {
	}

	public static Screen screen(int index) {
		return index >= UryuBalance.SEELE_SLASHES - 1 ? new Screen(0.5, 0.25, 40.0) : new Screen(0.2, 0.15, 0.0);
	}

	public static final double LUNGE_FOV_DEG = 3.0;
	public static final double LUNGE_FOV_SECS = 0.30;
}
