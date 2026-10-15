package dev.minebleach.reiatsutest.core.fx;

/**
 * Geometry of the Quincy wings, bracers and boots (URYU_VFX_STORYBOARD 2.10, 2.11, 4.1; the meshes are described in
 * design/results/uryu_fx_models.md). Pure math, no Minecraft types: the renderer ({@code UryuWingRenderer}) and the glow code
 * ({@code UryuWingFx}) place every wing point through {@link #place}, so the arrows of the barrage can leave the VISIBLE tips ({@link #tip}).
 *
 * <p><b>Frames.</b> Mesh frame = the OBJ frame of {@code uryu_wing_l/r}: origin at the shoulder root, +Y up, +Z forward (the wing lies behind the
 * root, z -0.05 to -0.53), +X sideways. Body frame = x LEFT, y UP, z FORWARD, feet at the origin, blocks (the frame of the Rukia ribbons in
 * {@code RukiaFeature}). The tip offsets of {@code UryuBalance.WING_TIPS} are (right positive, height, backwards). The one thing that is not
 * known without the game is whether the OBJ +X is the player's left: {@link #MESH_PLUS_X_IS_LEFT} is that single constant.
 *
 * <p><b>Pose.</b> A wing is rolled about the forward axis through its root (positive = the tip goes up), scaled about the root and, while
 * sneaking, pitched back with the leaning torso. The unfold of the release is per plate ({@link #plate}).
 */
public final class UryuWingMath {
	private UryuWingMath() {
	}

	/**
	 * UNVERIFIED (U-FX 1, 7): the model +X of the wing meshes is the player's LEFT (the Blender wing {@code _l} extends +X and Blender -Y, the
	 * forward, becomes OBJ +Z, so by the right handed frame +X is left). When the game shows the wings on the wrong sides, flip this one constant.
	 */
	public static final boolean MESH_PLUS_X_IS_LEFT = true;

	/** The wing mesh object of the player's left / right side (the file names follow the Blender convention {@code _l} = +X). */
	public static String meshName(boolean left) {
		return left == MESH_PLUS_X_IS_LEFT ? "uryu_wing_l" : "uryu_wing_r";
	}

	// ------------------------------------------------------------------------------------------ the root and the pose
	/** The shoulder root in the body frame: height and distance behind the spine (blocks). */
	public static final double ROOT_Y = 1.35;
	public static final double ROOT_BACK = 0.15;
	/** Sneaking: the torso leans 0.5 rad, the shoulders drop and move forward; the wings follow and fold 25 degrees. */
	public static final double SNEAK_ROOT_Y = 1.155;
	public static final double SNEAK_ROOT_Z = 0.14;
	public static final double SNEAK_PITCH_DEG = 28.6;
	public static final double SNEAK_FOLD_DEG = -25.0;
	/** In the air the wings go up 10 degrees and beat twice as fast. */
	public static final double AIR_DEG = 10.0;
	/** Idle flap: +-4 degrees at 0.25 Hz (0.5 Hz in the air). */
	public static final double FLAP_DEG = 4.0;
	public static final double FLAP_HZ_GROUND = 0.25;
	public static final double FLAP_HZ_AIR = 0.50;
	/** The wing barrage opens the wings: +12 degrees and x1.1 (2.12), a volley flares x1.07 for 0.18 s. */
	public static final double SPREAD_DEG = 12.0;
	public static final double SPREAD_SCALE = 0.10;
	public static final double VOLLEY_SCALE = 0.07;
	public static final double VOLLEY_TIME = 0.18;
	public static final double SPREAD_RISE = 0.20;
	public static final double SPREAD_FALL = 0.40;
	/** The additive overglow: the mesh 6 percent larger about the root, Q3 at 0.3 (x1.6 on a volley). */
	public static final double OVERGLOW_SCALE = 1.06;
	public static final double OVERGLOW_ALPHA = 0.30;
	public static final double OVERGLOW_VOLLEY = 1.6;
	public static final double WING_SCALE_MIN = 0.5;
	public static final double WING_SCALE_MAX = 1.5;

	/** The pose of the root and the wings of one player (everything that is not per plate). */
	public record Rig(double rootY, double rootZ, double pitchRad, double rollDeg, double scale) {
	}

	/**
	 * @param sneak 0..1 (smoothed), @param air 0..1 (smoothed), @param flapPhase the accumulated beat phase in cycles, @param wingScale the config
	 *        scale, @param spread 0..1 the barrage opening, @param volley 0..1 the volley flare
	 */
	public static Rig rig(double sneak, double air, double flapPhase, double wingScale, double spread, double volley) {
		double s = clamp01(sneak);
		double rootY = ROOT_Y + (SNEAK_ROOT_Y - ROOT_Y) * s;
		double rootZ = -ROOT_BACK + (SNEAK_ROOT_Z + ROOT_BACK) * s;
		double roll = FLAP_DEG * Math.sin(2 * Math.PI * flapPhase) + clamp01(air) * AIR_DEG + s * SNEAK_FOLD_DEG + clamp01(spread) * SPREAD_DEG;
		double scale = clampScale(wingScale) * (1.0 + SPREAD_SCALE * clamp01(spread)) * (1.0 + VOLLEY_SCALE * clamp01(volley));
		return new Rig(rootY, rootZ, Math.toRadians(SNEAK_PITCH_DEG) * s, roll, scale);
	}

	public static double clampScale(double wingScale) {
		return Math.max(WING_SCALE_MIN, Math.min(WING_SCALE_MAX, wingScale));
	}

	/** Beats per second of the flap: 0.25 Hz on the ground, 0.5 Hz in the air (blended with the smoothed air value). */
	public static double flapHz(double air) {
		return FLAP_HZ_GROUND + (FLAP_HZ_AIR - FLAP_HZ_GROUND) * clamp01(air);
	}

	/** Exponential approach of {@code current} to {@code target} with the rate per second; never overshoots. */
	public static double approach(double current, double target, double dt, double ratePerSecond) {
		double k = 1.0 - Math.exp(-Math.max(0.0, ratePerSecond) * Math.max(0.0, dt));
		return current + (target - current) * k;
	}

	/** The barrage opening 0..1: rises over {@link #SPREAD_RISE} (easeOutBack, capped at 1) from {@code start}, falls over {@link #SPREAD_FALL} after {@code end}. */
	public static double spread(double now, double start, double end) {
		if (start < 0 && end < 0 || now < start) {
			return 0.0;
		}
		double rise = Math.min(1.0, UryuVollstTiming.easeOutBack((now - start) / SPREAD_RISE, 1.2));
		if (end >= 0 && now > end) {
			double fall = clamp01((now - end) / SPREAD_FALL);
			return rise * (1.0 - fall * fall * (3 - 2 * fall));
		}
		return rise;
	}

	/** The flare of a volley, {@code since} seconds after it: up in 0.04 s, then eased out over {@link #VOLLEY_TIME}; 0 outside. */
	public static double volley(double since) {
		if (since < 0 || since >= VOLLEY_TIME) {
			return 0.0;
		}
		double x = since / VOLLEY_TIME;
		return x < 0.22 ? x / 0.22 : 1.0 - smooth((x - 0.22) / 0.78);
	}

	// ------------------------------------------------------------------------------------------ placing a mesh point

	/**
	 * Places a point of a wing mesh in the body frame. {@code side}: +1 the player's left wing, -1 the right one; {@code (mx, my, mz)} the point
	 * in the frame of the mesh of that side (OBJ); {@code k} the length factor of its plate (1 = unfolded, {@link UryuVollstTiming#FOLD_LENGTH}
	 * = folded); {@code extraRollDeg} the roll of its plate (0 unfolded). Out: x (left), y, z (forward).
	 */
	public static void place(int side, double mx, double my, double mz, double k, double extraRollDeg, Rig rig, double[] out) {
		placeLeftPositive(side, MESH_PLUS_X_IS_LEFT ? mx : -mx, my, mz, k, extraRollDeg, rig, out);
	}

	/** As {@link #place} with x already measured to the player's left. */
	public static void placeLeftPositive(int side, double x, double y, double z, double k, double extraRollDeg, Rig rig, double[] out) {
		double sc = k * rig.scale();
		double px = x * sc;
		double py = y * sc;
		double pz = z * sc;
		double s = side >= 0 ? 1.0 : -1.0;
		double roll = Math.toRadians(rig.rollDeg() + extraRollDeg);
		double cr = Math.cos(roll);
		double sr = Math.sin(roll);
		double rx = s * (s * px * cr - py * sr);
		double ry = s * px * sr + py * cr;
		double cp = Math.cos(rig.pitchRad());
		double sp = Math.sin(rig.pitchRad());
		double y2 = ry * cp + pz * sp;
		double z2 = -ry * sp + pz * cp;
		out[0] = rx;
		out[1] = rig.rootY() + y2;
		out[2] = rig.rootZ() + z2;
	}

	// ------------------------------------------------------------------------------------------ the tips (the arrows of the barrage leave them)

	/** Mesh-frame (left side) tip points: the upper spar tip and the lowest plate tip ({@code uryu_fx_meta.json}, empties wing_l_tip_upper/lower in OBJ axes). */
	public static final double[] TIP_UPPER = {1.70, 1.00, -0.30};
	public static final double[] TIP_LOWER = {1.25, -0.15, -0.45};

	/**
	 * The offset of the tip of a wing slot from the feet in the convention of {@code UryuBalance.WING_TIPS}: (sideways, right positive; height;
	 * backwards). Slots: 0 upper right, 1 upper left, 2 lower right, 3 lower left. At rest (no flap) it equals {@code WING_TIPS}.
	 */
	public static void tip(int slot, Rig rig, double[] out) {
		int s = Math.floorMod(slot, 4);
		boolean left = (s & 1) == 1;
		double[] m = s < 2 ? TIP_UPPER : TIP_LOWER;
		double[] b = new double[3];
		placeLeftPositive(left ? 1 : -1, left ? m[0] : -m[0], m[1], m[2], 1.0, 0.0, rig, b);
		out[0] = -b[0];
		out[1] = b[1];
		out[2] = -b[2];
	}

	// ------------------------------------------------------------------------------------------ plates and the unfold

	/** The OBJ has 60 quads: 7 slabs of 6, 2 spikes of 5, 2 chips of 4 (tested against the file). */
	public static final int QUADS = 60;
	public static final int GROUPS = 11;
	public static final int SLABS = 7;

	/** The plate group (0..10) of a quad of the wing mesh: slabs 0..6 in file order, then the two spikes, then the two chips. */
	public static int groupOfQuad(int quad) {
		if (quad < 42) {
			return quad / 6;
		}
		if (quad < 47) {
			return 7;
		}
		if (quad < 52) {
			return 8;
		}
		if (quad < 56) {
			return 9;
		}
		return 10;
	}

	/** Seconds after the unfold start at which plate group {@code g} starts: the spar and the root hook first, then the plates outward and down. */
	public static double groupDelay(int g) {
		return switch (g) {
			case 5, 6 -> 0.0; // main spar, root hook
			case 3, 4 -> UryuVollstTiming.PLATE_STAGGER; // the parallel plate and its step
			case 2 -> 2 * UryuVollstTiming.PLATE_STAGGER;
			case 1 -> 3 * UryuVollstTiming.PLATE_STAGGER;
			case 0 -> 4 * UryuVollstTiming.PLATE_STAGGER;
			default -> MAX_DELAY; // spikes and chips
		};
	}

	/** The longest delay: the spikes and chips (5 staggers). */
	public static final double MAX_DELAY = 5 * UryuVollstTiming.PLATE_STAGGER;
	/** How long one plate takes: the unfold minus the longest delay (so the last plate ends exactly at T_UNFOLD + UNFOLD_TIME). */
	public static final double PLATE_TIME = UryuVollstTiming.UNFOLD_TIME - MAX_DELAY;
	/** Each plate flashes Q1 around the moment it reaches its place. */
	public static final double FLASH_BEFORE = 0.03;
	public static final double FLASH_AFTER = 0.10;

	/** The time a plate group reaches its final place. */
	public static double arrival(int g) {
		return UryuVollstTiming.T_UNFOLD + groupDelay(g) + PLATE_TIME;
	}

	/**
	 * The state of a plate {@code t} seconds after the release event. Out: [0] the length factor k, [1] the extra roll in degrees, [2] the opacity
	 * 0..1 (0 before the plate starts), [3] the arrival flash 0..1.
	 */
	public static void plate(double t, int g, double[] out) {
		double u = (t - UryuVollstTiming.T_UNFOLD - groupDelay(g)) / PLATE_TIME;
		double lin = clamp01(u);
		double e = UryuVollstTiming.easeOutBack(lin, UryuVollstTiming.UNFOLD_BACK);
		out[0] = UryuVollstTiming.FOLD_LENGTH + (1.0 - UryuVollstTiming.FOLD_LENGTH) * e;
		out[1] = UryuVollstTiming.FOLD_ANGLE_DEG * (1.0 - e);
		out[2] = u <= 0 && t < UryuVollstTiming.T_UNFOLD + groupDelay(g) ? 0.0 : UryuVollstTiming.smoothstep(0.0, 0.3, lin);
		double tt = t - arrival(g);
		out[3] = tt < -FLASH_BEFORE || tt > FLASH_AFTER ? 0.0 : tt < 0 ? 1.0 + tt / FLASH_BEFORE : 1.0 - tt / FLASH_AFTER;
	}

	// ------------------------------------------------------------------------------------------ bracers and boots on the model

	/** Model pixels (1/16 of a model unit). The forearm band sits 7 px below the shoulder pivot of the arm (the arm cuboid spans -2 to 10 px). */
	public static final double BRACER_Y_PX = 7.0;
	/** The sole of the boot: 12 px below the pivot of the leg. */
	public static final double BOOT_Y_PX = 12.0;
	/** The player renderer scales the model by 0.9375 (the meshes are in blocks). */
	public static final double MODEL_SCALE = 0.9375;
	/** Radial scale of the bands over the 12 sided rings (circumradius 0.14 .. 0.155 m): bare, and over a chestplate / leggings / boots. */
	public static final double BRACER_RADIAL = 1.50;
	public static final double BRACER_RADIAL_ARMORED = 2.20;
	public static final double BOOT_RADIAL = 1.50;
	public static final double BOOT_RADIAL_ARMORED = 2.10;

	/** Centre of the arm cuboid sideways from its pivot, model pixels (right arm -1, left arm +1, slim half of it). */
	public static double armCentrePx(boolean slim, boolean left) {
		return (left ? 1.0 : -1.0) * (slim ? 0.5 : 1.0);
	}

	/** Circumradius of the unscaled bracer / boot at the bottom, metres. */
	public static final double BRACER_RADIUS = 0.135;
	public static final double BOOT_RADIUS = 0.135;

	/** Half diagonal of a limb cross section (metres) with an armor dilation of {@code dilationPx} on top of the skin layer: where the band has to reach. */
	public static double limbHalfDiagonal(double sizePx, double dilationPx) {
		double half = sizePx / 2.0 + dilationPx;
		return half * Math.sqrt(2.0) * MODEL_SCALE / 16.0;
	}

	// ------------------------------------------------------------------------------------------ the first person guard

	/**
	 * The yaw of the wing rig seen from the owner's own camera (radians): the body yaw limited to {@code maxDeg} around the camera yaw, halved
	 * for Reduce Motion (the Hakuteiken guard, {@link HakuteikenFirstPerson}). The rig is also pushed {@code back} metres further back.
	 */
	public static double guardYaw(double bodyYawRad, double camYawRad, double maxDeg, boolean gentle) {
		return HakuteikenFirstPerson.limitYaw(bodyYawRad, camYawRad, HakuteikenFirstPerson.yawLimit(maxDeg, gentle));
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}

	private static double smooth(double x) {
		double v = clamp01(x);
		return v * v * (3 - 2 * v);
	}
}
