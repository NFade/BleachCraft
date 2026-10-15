package dev.minebleach.reiatsutest.core.combat;

import dev.minebleach.reiatsutest.core.obj.DrawRig;

/**
 * Third person pose of a zanpakuto holder (tp_pose, 2026-10-11). Pure maths, no Minecraft: it solves, for one player and one
 * frame, WHERE the sword (or the sheathed sword in its scabbard) is and HOW the two arms hold it. The client glue
 * ({@code client/model/TpPoseDriver}, {@code TpPoseLayer}) feeds it the draw progress and the block weight and applies the
 * numbers (a playerAnimator layer for the arms, the held item transform for the sword, the scabbard renderer for the saya).
 *
 * <p>The chain of states (all in the model space of the player body, y down, front = -z, +x = the left of the character; the
 * right handed layout is solved, a left hander is its mirror image):
 * <ol>
 * <li>SEALED ({@code p = 0}): the scabbard with the sword in it sits at the left hip (manifest {@code draw.hip}); as in first person the
 * right hand grips the hilt just under the tsuba (the arm crosses the belly) and the left hand holds the scabbard in front of its
 * mouth (round3: the sword arm used to hang free).</li>
 * <li>Draw ({@code 0 < p < 1}, the progress of {@code DrawTracker}, same as in first person): the right hand goes to the hilt
 * and follows it; while the blade slides out along the sori arc the left hand pulls the scabbard back ({@code pull} of the
 * rig, as in first person); once the blade is clear the sword swings to the held pose, the scabbard goes back to the hip and the
 * left hand follows the sword to the lower hilt.</li>
 * <li>Held ({@code p = 1}): the sword stands upright in front of the body in both hands (right hand just under the tsuba,
 * left hand on the lower hilt). Sheathing runs the same path backwards.</li>
 * <li>Block ({@code b > 0}), the user's own pose (2026-10-13, replaces round3 and the short-lived "Pose B"): the sword held diagonally in front
 * of the chest in the frontal plane, hilt low right of centre, tip up and to the LEFT ({@code guard.*}: 59 degrees above the horizontal,
 * 86 degrees to the left of straight ahead), the CUTTING EDGE FORWARD (R11 T1: rolled 90 degrees about the blade, the flat now faces up and to the
 * right) and the sword moved a little to the character's left ({@link #GUARD_SHIFT}, R11 T10) so that both fists are on the hilt. The arms are not
 * solved here: while blocking they are the arms the user posed in Blockbench
 * ({@code guard.arm_r}, {@code guard.arm_l}: pitch, yaw, roll in degrees, dx, dy, dz in px; see {@link #GUARD_ARM_R}), blended in with the
 * eased block weight, and the item correction G still puts the sword exactly on its frame. The rig also turns the torso by -7.6 degrees;
 * that yaw belongs to the whole body, not to the arm solver, and is NOT part of this pose; the weight is eased.</li>
 * </ol>
 * The arms are solved by a closed form IK: the hand centre must be at a point of the sword axis, the arm direction follows from
 * the point (yaw 0), and the shoulder pivot slides along the arm by the missing length (at most {@code arm.max_shift} px).
 * The sword in the hand is then placed exactly on its frame by a rigid correction {@code G} of the item transform, built from the
 * real chain vanilla uses for a held item ({@code ModelPart#rotate}, turn -90 / 180, the hand offset, the display transform).
 */
public final class TpPose {
	private TpPose() {
	}

	/** Dotted key lookup for the tunable numbers; the client answers from {@code pose_override.json} section {@code tp}. */
	public interface Params {
		Params DEFAULTS = new Params() {
			@Override
			public double num(String key, double def) {
				return def;
			}

			@Override
			public double[] vec(String key, double[] def) {
				return def;
			}
		};

		double num(String key, double def);

		double[] vec(String key, double[] def);
	}

	/** Scabbard on the hip of a right hander: grip point (px), tip direction, scale (manifest {@code draw.hip}). */
	public record Hip(double[] posPx, double[] dir, double scale) {
	}

	/** Display transform of the third person right hand (rotation degrees, translation in blocks, uniform scale). */
	public record Display(double[] rotDeg, double[] transBlocks, double scale) {
	}

	/** Item model frame: {@code x -> t + s * R(q) * x}, item model blocks to body blocks (the centre shift is inside). */
	public record Frame(double[] t, double[] q, double s) {
		static Frame of(TpMat4 m) {
			return new Frame(m.translationPart(), m.quat(), m.scaleFactor());
		}

		public TpMat4 matrix() {
			return TpMat4.translation(t[0], t[1], t[2]).mul(TpMat4.fromQuat(q)).mul(TpMat4.scale(s));
		}
	}

	/** Arm values the playerAnimator layer applies: rotation (radians, ModelPart order) and the shoulder shift (px). */
	public record Arm(double pitch, double yaw, double roll, double dx, double dy, double dz) {
	}

	/**
	 * One frame of the pose, already mirrored for a left hander. {@code main} is the arm of the sword hand, {@code off} the
	 * other one; {@code wMain} and {@code wOff} are the weights of the override against the vanilla pose (0 = vanilla).
	 * {@code saya} is the scabbard mesh frame, {@code sword} the sword mesh frame (in the scabbard, flying, or at the hand when
	 * {@code p >= 1}), {@code gT/gQ} the rigid correction of the held item (translation in blocks, quaternion x y z w) that puts the sword on
	 * {@code sword}.
	 */
	public record Result(Arm main, Arm off, double wMain, double wOff, Frame saya, Frame sword, double[] gT, double[] gQ,
			double[] handTargetMain, double[] handTargetOff, double gS) {
	}

	/**
	 * Facts of the frame. {@code left} = the sword hand is the left one; {@code thin} = slim arms. {@code swordScale} (R11 T10, config
	 * {@code tp.swordScale}) multiplies the size of the sword in the hand, the block and the hold AND of the scabbard on the hip (and the one that
	 * travels with the sword in a sealed block): the effective scale is {@code display.scale() * swordScale}, {@code hip.scale() * swordScale}.
	 * The hands stay put in px; the hilt places of the fists scale with the sword (see {@link #fistR}).
	 */
	public record Input(double p, double b, boolean left, boolean thin, Hip hip, Display display, DrawRig rig, double swordScale) {
		public Input(double p, double b, boolean left, boolean thin, Hip hip, Display display, DrawRig rig) {
			this(p, b, left, thin, hip, display, rig, 1.0);
		}
	}

	private static final double[] CENTRE = {0.5, 0.5, 0.5};

	/** The display scale at which the px numbers below were measured (item display {@code thirdperson_righthand}, scale 1.6). */
	public static final double REF_SCALE = 1.6;
	/** Distance (px, along the blade from the grip centre; negative = toward the pommel) of the centre of the right fist at {@link #REF_SCALE}. */
	public static final double C_R = -0.8;
	/** The same for the left fist: lower on the hilt than the right one. */
	public static final double C_L = -2.4;
	/** Where the tsuba is (saya mouth of the models, 0.066 m from the grip centre) and the pommel (0.19 m), px at {@link #REF_SCALE}. */
	public static final double TSUBA_PX = 1.7;
	public static final double POMMEL_PX = -4.9;

	/** R11 T10: ratio of a model scale to the reference scale: every px place along the hilt is multiplied by it. */
	public static double scaleRatio(double scale) {
		return scale / REF_SCALE;
	}

	public static double tsubaPx(double scale) {
		return TSUBA_PX * scaleRatio(scale);
	}

	public static double pommelPx(double scale) {
		return POMMEL_PX * scaleRatio(scale);
	}

	/** Default place of the centre of the right fist along the blade for a sword drawn at {@code scale} (px from the grip centre). */
	public static double fistR(double scale) {
		return C_R * scaleRatio(scale);
	}

	/** Default place of the centre of the left fist for a sword drawn at {@code scale}. */
	public static double fistL(double scale) {
		return C_L * scaleRatio(scale);
	}

	// ------------------------------------------------------------------ tunable numbers (px, degrees, right handed layout)

	/**
	 * The user's guard of 2026-10-13 (round3 was pos (0.5, 7.5, -6.0), tilt 40, fwd 5, lat 1.3 / -1.3, c_l -2.4): grip centre (px, body
	 * space: x_body = -x_bb, y_body = 24 - y_bb) = the tsuba point of the user's sword prop (3.9, 18.1, -6.1) in Blockbench px minus
	 * {@link #TSUBA_PX} along the blade. Blade direction (0.50843, -0.86031, -0.03685) in body space. R11 T10: kept as the reference (display scale
	 * 1.6, no shift); the pose now places the grip by {@link #GUARD_TSUBA_POINT} and {@link #GUARD_SHIFT} for the scale in force.
	 */
	public static final double[] GUARD_POS = {-4.7643, 7.3625, -6.0374};
	/** The tsuba point of the user's sword prop in the block (px, body space): the sword grows about this point, the grip centre is placed from it. */
	public static final double[] GUARD_TSUBA_POINT = {-3.9, 5.9, -6.1};
	/**
	 * R11 T10: shift of the sword in the block (px, body space: +x = the character's LEFT, which is the right of the screen seen from the front, +y
	 * down, +z toward the back). Found for the default sword scale 2.0 (1.6 * tp.swordScale 1.25) so that both authored fists lie on the hilt: between
	 * pommel and tsuba (0.4 of the scale ratio inside both ends), 1.9 to 5 px apart along the blade and at most 2.2 px off the axis, for wide and slim
	 * arms. 1.58 px in all, 1.02 px of it along the blade toward the tip. Key {@code guard.shift} replaces it.
	 */
	public static final double[] GUARD_SHIFT = {1.45, -0.35, 0.55};
	/** Tip angle above the horizontal (degrees): 59.35 degrees. */
	public static final double GUARD_TILT = 59.352;
	/**
	 * Blade turned forward (degrees) in the parametrisation {@code tip = (-cos t cos f, -sin t, -cos t sin f)}: {@code f = 175.855} points the tip
	 * 85.9 degrees to the character's LEFT of straight ahead (f = 90 would point straight ahead, f = 0 straight to the right).
	 */
	public static final double GUARD_FWD = 175.855;
	/**
	 * Roll of the cutting edge about the blade (degrees, 0 = edge forward). R11 T1: 0, the edge faces forward (was -89.9: the edge in the frontal
	 * plane, up and to the right, the flat facing forward). The edge is the model +Z axis (checked on the grid of both drawn sword meshes: the
	 * section tapers to a line at +Z). The blade is not exactly in the frontal plane (z -0.037), so the edge, which must stay square to the blade,
	 * is the projection of the forward direction: 2.1 degrees off straight ahead.
	 */
	public static final double GUARD_EDGE = 0.0;
	/** Default lateral and along-the-hilt places of the fists while the arms are still being blended in (px); the final arms are authored. */
	public static final double GUARD_LAT_R = 0.0;
	public static final double GUARD_LAT_L = 0.0;
	public static final double GUARD_C_L = C_L;
	/**
	 * The arms of the block as the user posed them in Blockbench (design/anim/player_game_rig.bbmodel, animation block_poses, pose at 0 s),
	 * converted to the game: pitch/yaw/roll in degrees ({@code pitch = -rx}, {@code yaw = -ry}, {@code roll = +rz}), then dx, dy, dz in px as the
	 * shoulder pivot offset from its rest place ({@code dx = -x_bb}, {@code dy = -y_bb}, {@code dz = z_bb}). Right hander layout: the sword arm,
	 * then the other arm.
	 */
	public static final double[] GUARD_ARM_R = {-43.9005, -15.798, 3.8136, 0.048, -0.003, 0.649};
	public static final double[] GUARD_ARM_L = {-40.034, 51.6452, 16.4722, -1.09, 0.0, -0.7};

	static double[] holdPos(Params pr) {
		return pr.vec("hold.pos", new double[] {0.0, 6.5, -5.4});
	}

	static double holdTilt(Params pr) {
		return pr.num("hold.tilt", 12.0);
	}

	/**
	 * Grip centre of the sword in the block (px) for a sword drawn at {@code scale}, blade direction {@code tipUnit}: the tsuba point of the user's
	 * prop minus the tsuba distance of this scale along the blade, plus the shift ({@code guard.shift}). A dev override {@code guard.pos} is absolute.
	 */
	static double[] guardPos(Params pr, double scale, double[] tipUnit) {
		double[] abs = pr.vec("guard.pos", null);
		if (abs != null) {
			return abs;
		}
		double[] sh = pr.vec("guard.shift", GUARD_SHIFT);
		double ts = tsubaPx(scale);
		return new double[] {GUARD_TSUBA_POINT[0] - ts * tipUnit[0] + sh[0], GUARD_TSUBA_POINT[1] - ts * tipUnit[1] + sh[1],
				GUARD_TSUBA_POINT[2] - ts * tipUnit[2] + sh[2]};
	}

	static double guardTilt(Params pr) {
		return pr.num("guard.tilt", GUARD_TILT);
	}

	public static double smooth(double x) {
		double t = Math.max(0, Math.min(1, x));
		return t * t * (3 - 2 * t);
	}

	private static double[] normalize(double[] v) {
		double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return new double[] {v[0] / n, v[1] / n, v[2] / n};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	/** Rotation of {@code v} about the unit axis by the angle (Rodrigues). */
	private static double[] rotAbout(double[] v, double[] axis, double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		double[] k = cross(axis, v);
		double d = dot(axis, v) * (1 - c);
		return new double[] {v[0] * c + k[0] * s + axis[0] * d, v[1] * c + k[1] * s + axis[1] * d, v[2] * c + k[2] * s + axis[2] * d};
	}

	/**
	 * Item axes in body space: model +Y (blade) = {@code tip}, model +Z (edge) = {@code edge} made orthogonal, model +X their
	 * cross product (the convention of {@code ScabbardRenderer#basis}).
	 */
	static TpMat4 orient(double[] tip, double[] edge) {
		double[] y = normalize(tip);
		double[] z = normalize(new double[] {edge[0] - dot(edge, y) * y[0], edge[1] - dot(edge, y) * y[1], edge[2] - dot(edge, y) * y[2]});
		double[] x = cross(y, z);
		return TpMat4.fromAxes(x, y, z);
	}

	/** Frame matrix {@code T(o px) * Q * S(s) * T(-centre)}: item model blocks to body blocks. */
	static TpMat4 frame(double[] oPx, TpMat4 q, double s) {
		return TpMat4.translation(oPx[0] / 16, oPx[1] / 16, oPx[2] / 16).mul(q).mul(TpMat4.scale(s))
				.mul(TpMat4.translation(-0.5, -0.5, -0.5));
	}

	/** The scabbard at the hip, the way {@code ScabbardRenderer} always placed it. */
	static TpMat4 hipFrame(Hip h) {
		return hipFrame(h, 1.0);
	}

	/**
	 * The scabbard at the hip grown by {@code ks} (R11 T10) about the centre of its mouth: the mouth (the tsuba of the sword in it, {@link #tsubaPx} from
	 * the grip centre) stays where it was, so the scabbard does not sink into the hip.
	 */
	static TpMat4 hipFrame(Hip h, double ks) {
		TpMat4 q = orient(h.dir(), new double[] {0, -1, 0});
		double[] d = normalize(h.dir());
		double back = (ks - 1) * tsubaPx(h.scale());
		double[] pos = {h.posPx()[0] - d[0] * back, h.posPx()[1] - d[1] * back, h.posPx()[2] - d[2] * back};
		return frame(pos, q, h.scale() * ks);
	}

	/** Upright sword in front of the body, edge forward. */
	public static TpMat4 holdFrame(Params pr, double scale) {
		double t = Math.toRadians(holdTilt(pr));
		double[] tip = {0, -Math.cos(t), -Math.sin(t)};
		double[] edge = rotAbout(new double[] {0, 0, -1}, normalize(tip), Math.toRadians(pr.num("hold.edge", 0)));
		return frame(holdPos(pr), orient(tip, edge), scale);
	}

	/**
	 * Sword held diagonally in front of the chest (the user's pose): the grip low right of centre, the blade in the frontal plane rising to
	 * the character's LEFT, the tip up by {@code guard.tilt} degrees; the second angle {@code guard.fwd} is measured in the horizontal plane
	 * from the character's own right (90 = straight ahead, 180 = straight to the left), so 175.9 puts the tip 85.9 degrees left of
	 * straight ahead. {@code guard.edge} rolls the cutting edge about the blade (0 = forward).
	 */
	public static TpMat4 guardFrame(Params pr, double scale) {
		double t = Math.toRadians(guardTilt(pr));
		double f = Math.toRadians(pr.num("guard.fwd", GUARD_FWD));
		double[] tip = {-Math.cos(t) * Math.cos(f), -Math.sin(t), -Math.cos(t) * Math.sin(f)};
		double[] edge = rotAbout(new double[] {0, 0, -1}, normalize(tip), Math.toRadians(pr.num("guard.edge", GUARD_EDGE)));
		return frame(guardPos(pr, scale, normalize(tip)), orient(tip, edge), scale);
	}

	private static TpMat4 arcMat(DrawRig rig, double angle) {
		DrawRig.Rigid r = rig.arc((float) angle);
		return TpMat4.translation(r.t()[0], r.t()[1], r.t()[2])
				.mul(TpMat4.fromQuat(new double[] {r.q()[0], r.q()[1], r.q()[2], r.q()[3]}));
	}

	/** Rigid blend about the grip point (model centre): grip position lerp, rotation slerp, uniform scale lerp. */
	static TpMat4 blend(TpMat4 a, TpMat4 b, double u) {
		if (u <= 0) {
			return a;
		}
		if (u >= 1) {
			return b;
		}
		double[] pa = a.point(CENTRE);
		double[] pb = b.point(CENTRE);
		double[] q = TpMat4.slerp(a.quat(), b.quat(), u);
		double s = a.scaleFactor() + (b.scaleFactor() - a.scaleFactor()) * u;
		double[] p = {pa[0] + (pb[0] - pa[0]) * u, pa[1] + (pb[1] - pa[1]) * u, pa[2] + (pb[2] - pa[2]) * u};
		return TpMat4.translation(p[0], p[1], p[2]).mul(TpMat4.fromQuat(q)).mul(TpMat4.scale(s)).mul(TpMat4.translation(-0.5, -0.5, -0.5));
	}

	/** Point on the sword axis of frame {@code f}: {@code c} px along the blade from the grip, {@code lat} px sideways (item +X). */
	static double[] handPoint(TpMat4 f, double lat, double c) {
		double[] g = f.point(CENTRE);
		double s = f.scaleFactor();
		double[] dx = f.dir(1, 0, 0);
		double[] dy = f.dir(0, 1, 0);
		return new double[] {
			g[0] * 16 + (dx[0] * lat + dy[0] * c) / s,
			g[1] * 16 + (dx[1] * lat + dy[1] * c) / s,
			g[2] * 16 + (dx[2] * lat + dy[2] * c) / s};
	}

	// ------------------------------------------------------------------ arm solver

	private record ArmSol(Arm arm, double[] pivot) {
	}

	/**
	 * Closed form arm solve (virtual right handed space). The arm axis points from the rest shoulder to the target, yaw is the
	 * given twist, the shoulder pivot is then moved so that the hand centre lands exactly on the target (limited by maxShift).
	 *
	 * @param main true for the right arm (x = -5), false for the left arm
	 */
	private static ArmSol solveArm(boolean main, boolean thin, double[] target, double yawDeg, double maxShift) {
		double sx = main ? -1 : 1;
		double[] p0 = {5 * sx, thin ? 2.5 : 2.0, 0};
		double[] hc = {sx * (thin ? 0.5 : 1.0), 8.0, 0};
		double[] d = {target[0] - p0[0], target[1] - p0[1], target[2] - p0[2]};
		double[] a = normalize(d);
		double yaw = Math.toRadians(yawDeg);
		double cy = Math.cos(yaw);
		double sp = Math.max(-1, Math.min(1, a[2] / cy));
		double pitch = Math.asin(sp);
		double cp = Math.cos(pitch);
		double roll = Math.atan2(a[1], a[0]) - Math.atan2(cp, sp * Math.sin(yaw));
		TpMat4 r = TpMat4.rotZYX(roll, yaw, pitch);
		double[] h = r.dir(hc[0], hc[1], hc[2]);
		double[] shift = {target[0] - h[0] - p0[0], target[1] - h[1] - p0[1], target[2] - h[2] - p0[2]};
		double n = Math.sqrt(shift[0] * shift[0] + shift[1] * shift[1] + shift[2] * shift[2]);
		if (n > maxShift) {
			double k = maxShift / n;
			shift = new double[] {shift[0] * k, shift[1] * k, shift[2] * k};
		}
		double[] pivot = {p0[0] + shift[0], p0[1] + shift[1], p0[2] + shift[2]};
		return new ArmSol(new Arm(pitch, yaw, roll, shift[0], shift[1], shift[2]), pivot);
	}

	/** Hand centre of an arm solution (pivot plus the rotated centre of the lower 4 px of the arm cube), virtual right handed space. */
	private static double[] handCentre(boolean main, boolean thin, ArmSol s) {
		double sx = main ? -1 : 1;
		double[] h = TpMat4.rotZYX(s.arm().roll(), s.arm().yaw(), s.arm().pitch()).dir(sx * (thin ? 0.5 : 1.0), 8.0, 0);
		return new double[] {s.pivot()[0] + h[0], s.pivot()[1] + h[1], s.pivot()[2] + h[2]};
	}

	/**
	 * Blends an authored arm (degrees and px, see {@link #GUARD_ARM_R}) over the solved one with weight {@code k}: rotation and shoulder shift
	 * are interpolated component wise, so {@code k = 0} is exactly the IK solution and {@code k = 1} exactly the authored arm.
	 */
	private static ArmSol authoredArm(boolean main, boolean thin, ArmSol ik, double[] a, double k) {
		Arm i = ik.arm();
		double pitch = i.pitch() + (Math.toRadians(a[0]) - i.pitch()) * k;
		double yaw = i.yaw() + (Math.toRadians(a[1]) - i.yaw()) * k;
		double roll = i.roll() + (Math.toRadians(a[2]) - i.roll()) * k;
		double dx = i.dx() + (a[3] - i.dx()) * k;
		double dy = i.dy() + (a[4] - i.dy()) * k;
		double dz = i.dz() + (a[5] - i.dz()) * k;
		double sx = main ? -1 : 1;
		double[] pivot = {5 * sx + dx, (thin ? 2.5 : 2.0) + dy, dz};
		return new ArmSol(new Arm(pitch, yaw, roll, dx, dy, dz), pivot);
	}

	/**
	 * The mirror image of an item frame for a left hander: the grip point and the axes are reflected across x = 0, the model centre
	 * stays the pivot of the item (reflection about its own centre plane, the mesh is symmetric across it).
	 */
	static TpMat4 mirrorFrame(TpMat4 f) {
		return f.mul(TpMat4.translation(0.5, 0.5, 0.5)).mirrorX().mul(TpMat4.translation(-0.5, -0.5, -0.5));
	}

	private static Arm mirror(Arm a) {
		return new Arm(a.pitch(), -a.yaw(), -a.roll(), -a.dx(), a.dy(), a.dz());
	}

	private static double[] lerp(double[] a, double[] b, double u) {
		return new double[] {a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u};
	}

	// ------------------------------------------------------------------ the pose

	/** Smallest item display scale the solver accepts (the maths divides by the scale of the sword frame). */
	public static final double MIN_ITEM_SCALE = 0.05;

	/** R11 review fix 9: a display scale of 0, negative or NaN (a resource pack) would give NaN poses: it falls back to the reference scale. */
	static double saneScale(double s) {
		return Double.isNaN(s) || Double.isInfinite(s) || s < MIN_ITEM_SCALE ? REF_SCALE : s;
	}

	private static Input sane(Input in) {
		double ds = saneScale(in.display().scale());
		double hs = saneScale(in.hip().scale());
		if (ds == in.display().scale() && hs == in.hip().scale()) {
			return in;
		}
		return new Input(in.p(), in.b(), in.left(), in.thin(), new Hip(in.hip().posPx(), in.hip().dir(), hs),
				new Display(in.display().rotDeg(), in.display().transBlocks(), ds), in.rig(), in.swordScale());
	}

	/** Solves one frame. All numbers of the layout come from {@code pr} (defaults inside). */
	public static Result solve(Input input, Params pr) {
		Input in = sane(input);
		double p = Math.max(0, Math.min(1, in.p()));
		double b = Math.max(0, Math.min(1, in.b()));
		DrawRig rig = in.rig();
		double slide = Math.max(1e-3, rig.slideEnd);
		double cl = rig.clearAngle();
		double pull = pr.num("draw.pull", 0.5); // share of the blade travel done by the left hand pulling the scabbard back
		double ts = Math.min(1, p / slide);
		double u = p <= slide ? 0 : smooth((p - slide) / (1 - slide));

		double ks = Double.isNaN(in.swordScale()) ? 1.0 : Math.max(0.1, in.swordScale());
		double ds = in.display().scale() * ks; // effective scale of the sword in the hand, the hold and the block
		double hs = in.hip().scale() * ks; // effective scale of the scabbard (and of the sword in it)
		TpMat4 hip = hipFrame(in.hip(), ks);
		TpMat4 hold = holdFrame(pr, ds);
		TpMat4 guard = guardFrame(pr, ds);
		double bs = smooth(b); // eased block weight: soft start and stop of the guard

		// scabbard: pulled back along the sori arc by the left hand while the blade slides, then back to the hip
		TpMat4 saya = hip.mul(arcMat(rig, -cl * pull * ts * (1 - u)));
		saya = blend(saya, guard, bs * (1 - p));
		// sword: slides out of the scabbard, then swings to the held pose
		TpMat4 clear = hip.mul(arcMat(rig, cl * (1 - pull)));
		TpMat4 draw = p <= slide ? hip.mul(arcMat(rig, cl * (1 - pull) * ts)) : blend(clear, hold, u);
		TpMat4 sword = blend(draw, guard, bs);

		// hands. sl: how much the left hand has come over to the sword (draw finished or block); until then the left hand holds the
		// scabbard and the right hand grips the hilt alone (round3: the sealed sword is held like in first person)
		double sl = Math.max(u, bs);
		double gripLatR = pr.num("grip.lat_r", -1.8) * (1 - bs) + pr.num("guard.lat_r", GUARD_LAT_R) * bs;
		double latR = pr.num("sealed.lat_r", 0.0) * (1 - sl) + gripLatR * sl;
		double latL = pr.num("grip.lat_l", 1.8) * (1 - bs) + pr.num("guard.lat_l", GUARD_LAT_L) * bs;
		double cR = pr.num("sealed.c_r", fistR(hs)) * (1 - sl) + pr.num("grip.c_r", fistR(ds)) * sl;
		double cL = pr.num("grip.c_l", fistL(ds)) * (1 - bs) + pr.num("guard.c_l", GUARD_C_L * scaleRatio(ds)) * bs;
		double maxShift = pr.num("arm.max_shift", 6.0);
		double[] tR = handPoint(sword, latR, cR);
		double[] tSwordL = handPoint(sword, latL, cL);
		double[] tSaya = handPoint(saya, pr.num("saya.lat", 0.0), pr.num("saya.c", 4.5 * scaleRatio(hs)));
		double[] tL = lerp(tSaya, tSwordL, sl);
		ArmSol main = solveArm(true, in.thin(), tR, pr.num("arm.yaw_r", 0), maxShift);
		ArmSol off = solveArm(false, in.thin(), tL, pr.num("arm.yaw_l", 0), maxShift);
		if (bs > 0) {
			// the block arms are authored (the user's own pose), blended in with the eased weight; hand targets follow the real hand centres
			main = authoredArm(true, in.thin(), main, pr.vec("guard.arm_r", GUARD_ARM_R), bs);
			off = authoredArm(false, in.thin(), off, pr.vec("guard.arm_l", GUARD_ARM_L), bs);
			tR = handCentre(true, in.thin(), main);
			tL = handCentre(false, in.thin(), off);
		}
		double wMain = 1.0; // the sword arm is posed in every state: on the hilt of the sealed sword, in the draw, held, blocking

		// the item correction G: arm chain * turn/offset of the held item * G * display = sword frame
		TpMat4 chain = TpMat4.translation((main.pivot()[0] + (in.thin() ? 0.5 : 0)) / 16, main.pivot()[1] / 16, main.pivot()[2] / 16)
				.mul(TpMat4.rotZYX(main.arm().roll(), main.arm().yaw(), main.arm().pitch()))
				.mul(TpMat4.rotX(-Math.PI / 2)).mul(TpMat4.rotY(Math.PI)).mul(TpMat4.translation(1 / 16.0, 0.125, -0.625));
		Display d = in.display();
		TpMat4 disp = TpMat4.translation(d.transBlocks()[0], d.transBlocks()[1], d.transBlocks()[2])
				.mul(TpMat4.rotXYZ(Math.toRadians(d.rotDeg()[0]), Math.toRadians(d.rotDeg()[1]), Math.toRadians(d.rotDeg()[2])))
				.mul(TpMat4.scale(d.scale()));
		TpMat4 g = chain.inverse().mul(sword).mul(TpMat4.translation(0.5, 0.5, 0.5)).mul(disp.inverse());

		Frame fSaya = Frame.of(saya);
		Frame fSword = Frame.of(sword);
		double[] gT = g.translationPart();
		double[] gQ = g.quat();
		double gS = g.scaleFactor();
		if (in.left()) {
			fSaya = Frame.of(mirrorFrame(saya));
			fSword = Frame.of(mirrorFrame(sword));
			TpMat4 gm = g.mirrorX();
			gT = gm.translationPart();
			gQ = gm.quat();
			return new Result(mirror(main.arm()), mirror(off.arm()), wMain, 1.0, fSaya, fSword, gT, gQ,
					new double[] {-tR[0], tR[1], tR[2]}, new double[] {-tL[0], tL[1], tL[2]}, gS);
		}
		return new Result(main.arm(), off.arm(), wMain, 1.0, fSaya, fSword, gT, gQ, tR, tL, gS);
	}
}
