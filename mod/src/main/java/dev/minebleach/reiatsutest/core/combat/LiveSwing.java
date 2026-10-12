package dev.minebleach.reiatsutest.core.combat;

/**
 * The first person strike taken from the third person animation (round 6). Better Combat plays its strike animation (the katana combo
 * {@code two_handed_slash_horizontal_left / right}, {@code vertical}, {@code stab}) on the local player through playerAnimator even when the
 * first person view hides it, so the live transforms of the bones are there. The client samples them ({@code LiveSwingSampler}, public API
 * {@code AnimationStack.get3DTransform}); this class turns them into a pose of the first person hand: the sword is moved the same way the
 * third person sword moves, so a horizontal cut sweeps level across the view and a vertical one comes down from above.
 *
 * <p>Kinematics of the player model (raw model part space of Minecraft: x towards the entity's left, y DOWN, forward is -z, pixels; the same
 * numbers the model parts hold: pivot, pitch / yaw / roll applied as {@code Rz * Ry * Rx} like {@code ModelPart.rotate}):
 * <pre>
 * R_parent = R_body * R_torso                       (the arms ride on the torso)
 * shoulder = hip + R_body * (R_torso * arm pivot + torso shift - hip)    (the body turns about the hips, the torso about the neck)
 * R_arm    = R_parent * Rz Ry Rx (arm rotation)
 * elbow    = shoulder + R_arm * (0, 6, 0)
 * R_fore   = R_arm * R_bend (axis (cos a, 0, sin a), angle bend; playerAnimator bend of the arm)
 * hand     = elbow + R_fore * (0, 4.5, 0) - head        (head = hip - R_body * hip: the camera does not lean with the body)
 * R_sword  = R_fore * Rz Ry Rx (item bone rotation)
 * </pre>
 * and the camera frame is {@code (x, y, z) -> (-x, -y, z)} (a half turn about z). Only the CHANGE from a rest sample is used: the stance of the
 * player in the same animation stack (our third person pose layer) is the rest, the animation is rotation {@code R_sword(t) R_sword(rest)^-1}
 * and shift {@code hand(t) - hand(rest)} against it, laid on the first person stance. So at the start and at the end of the animation
 * (its own fade in and fade out) the first person hand is exactly on its stance, whatever the third person pose looks like.
 *
 * <p>Numbers (all dev tunable, {@code pose_override.json}, section {@code fp_swing.live}): {@code pos_gain} (x y z, a negative
 * number flips an axis), {@code rot_gain} (x y z gains of the rotation vector, negative flips), {@code max_angle}, {@code max_pos},
 * {@code pitch_comp}, {@code bend}, {@code torso}. UNVERIFIED in game: the axis conventions were derived from the model part code and checked by the tests here only.
 */
public final class LiveSwing {
	/** Pixels to blocks of the player model (16 per block, rendered at 0.9375). */
	public static final float PX = 0.9375f / 16f;
	/** Upper arm length and the distance from the elbow to the centre of the fist, pixels. */
	public static final float UPPER = 6f;
	public static final float FORE = 4.5f;
	/** Height of the hips under the top of the torso, pixels: the pivot of the {@code body} bone. */
	public static final float HIP = 12f;

	private LiveSwing() {
	}

	/**
	 * Raw values of one arm chain (what playerAnimator hands out for the bones): rotations in radians (pitch, yaw, roll), positions in pixels.
	 * {@code armPivot} is the pivot of the arm (absolute, the default is (-5, 2, 0) for the right arm), {@code bend} the bend of the arm
	 * (axis angle and angle, radians), {@code itemRot} the rotation of the item bone.
	 */
	public record Bones(float[] torsoRot, float[] bodyRot, float[] torsoPos, float[] armPivot, float[] armRot, float bendAxis, float bend,
			float[] itemRot) {
		/** The arm hanging down: nothing animates (a rest of zero). */
		public static Bones hanging(boolean leftArm) {
			return new Bones(new float[3], new float[3], new float[3], new float[] {leftArm ? 5f : -5f, 2f, 0f}, new float[3], 0f, 0f, new float[3]);
		}
	}

	/** The sword hand in the camera frame: {@code fist} in blocks, {@code q} the quaternion (x, y, z, w) of the sword orientation. */
	public record Frame(float[] fist, float[] q) {
	}

	// ------------------------------------------------------------------ kinematics

	/** Frame of the sword hand of the arm chain {@code b}. */
	public static Frame frame(Bones b, float bendGain, boolean useTorso) {
		float[] qBody = useTorso ? zyx(b.bodyRot) : new float[] {0f, 0f, 0f, 1f};
		float[] qTorso = useTorso ? zyx(b.torsoRot) : new float[] {0f, 0f, 0f, 1f};
		float[] qParent = FpPose.mul(qBody, qTorso);
		// the torso turns about its own pivot (the neck), the whole body about the hips (playerAnimator: 0.7 blocks above the feet = 12 px under the neck)
		float[] s0 = FpPose.rotate(qTorso, b.armPivot);
		if (useTorso) {
			s0 = add(s0, b.torsoPos);
		}
		float[] hip = {0f, HIP, 0f};
		float[] shoulder = add(hip, FpPose.rotate(qBody, new float[] {s0[0] - hip[0], s0[1] - hip[1], s0[2] - hip[2]}));
		float[] qArm = FpPose.mul(qParent, zyx(b.armRot));
		float[] elbow = add(shoulder, FpPose.rotate(qArm, new float[] {0f, UPPER, 0f}));
		float[] qFore = qArm;
		if (Math.abs(b.bend) > 1e-5f && bendGain != 0f) {
			qFore = FpPose.mul(qArm, FpPose.axisAngle((float) Math.cos(b.bendAxis), 0f, (float) Math.sin(b.bendAxis), b.bend * bendGain));
		}
		float[] hand = add(elbow, FpPose.rotate(qFore, new float[] {0f, FORE, 0f}));
		// the first person camera sits at the head and does not lean with the body animation: the hand is taken relative to the head, which the
		// body bone carries about the hips
		float[] head = add(hip, FpPose.rotate(qBody, new float[] {-hip[0], -hip[1], -hip[2]}));
		hand = new float[] {hand[0] - head[0], hand[1] - head[1], hand[2] - head[2]};
		float[] qSword = FpPose.mul(qFore, zyx(b.itemRot));
		// to the camera frame: (x, y, z) -> (-x, -y, z), in blocks; a half turn about z
		float[] fist = {-hand[0] * PX, -hand[1] * PX, hand[2] * PX};
		float[] q = {-qSword[0], -qSword[1], qSword[2], qSword[3]};
		return new Frame(fist, q);
	}

	/** Quaternion of the model part rotation (pitch about x, yaw about y, roll about z): {@code Rz * Ry * Rx}. */
	static float[] zyx(float[] r) {
		float[] qx = FpPose.axisAngle(1f, 0f, 0f, r[0]);
		float[] qy = FpPose.axisAngle(0f, 1f, 0f, r[1]);
		float[] qz = FpPose.axisAngle(0f, 0f, 1f, r[2]);
		return FpPose.mul(qz, FpPose.mul(qy, qx));
	}

	// ------------------------------------------------------------------ pose

	/**
	 * Gains and limits of {@link #pose}. {@code posGain} and {@code rotGain} are per axis (camera frame), {@code maxAngle} caps the turn of the
	 * sword (degrees), {@code maxPos} the shift of each axis (blocks, soft), {@code pitchComp} the share of the look pitch adjustment of Better Combat
	 * that is taken back out of the bones ({@link #compensate}; the first person hands follow the camera already, so they must not tilt again).
	 */
	public record Gains(float[] posGain, float[] rotGain, float maxAngle, float maxPos, float pitchComp, float bend, boolean torso) {
		public static final Gains DEFAULT = new Gains(new float[] {1f, 1f, 1f}, new float[] {1f, 1f, 1f}, 150f, 0.55f, 1f, 1f, true);

		public static Gains of(FpParams pr) {
			return new Gains(pr.vec("swing.live.pos_gain", DEFAULT.posGain), pr.vec("swing.live.rot_gain", DEFAULT.rotGain),
					pr.num("swing.live.max_angle", DEFAULT.maxAngle), pr.num("swing.live.max_pos", DEFAULT.maxPos), pr.num("swing.live.pitch_comp", DEFAULT.pitchComp),
					pr.num("swing.live.bend", DEFAULT.bend), pr.num("swing.live.torso", 1f) > 0.5f);
		}
	}

	/** The turn and the shift of the animation against the rest, in the camera frame (before any mirroring): {@code q} and {@code move}. */
	public record Delta(float[] q, float[] move) {
		public static final Delta NONE = new Delta(new float[] {0f, 0f, 0f, 1f}, new float[3]);

		/** Size of the change: the angle in degrees plus the shift in centimetres of a block (for the "is it moving" test). */
		public float magnitude() {
			float w = Math.max(-1f, Math.min(1f, Math.abs(q[3])));
			float deg = (float) Math.toDegrees(2.0 * Math.acos(w));
			float len = (float) Math.sqrt(move[0] * move[0] + move[1] * move[1] + move[2] * move[2]);
			return deg + len * 100f;
		}
	}

	/**
	 * Takes the look pitch adjustment of Better Combat out of the bones of a strike. Its attack layer adds, in the third person pass (read from its
	 * adjustment function, {@code createAttackAdjustment}): {@code -0.75 p} to the pitch of the {@code body} bone and {@code +0.25 p} to the pitch of
	 * both arms, {@code p} the look pitch in radians (down positive), so that a strike follows the look direction of the third person model. The first
	 * person hands are drawn in the camera frame already, so with the adjustment left in a swing would tilt a second time when the player looks up or
	 * down. {@code k} scales the correction (0 = off). Only for bones sampled while the Better Combat layer drives the sword.
	 */
	public static Bones compensate(Bones b, float pitchRad, float k) {
		if (k == 0f || pitchRad == 0f) {
			return b;
		}
		float[] body = b.bodyRot.clone();
		float[] arm = b.armRot.clone();
		body[0] += 0.75f * pitchRad * k;
		arm[0] -= 0.25f * pitchRad * k;
		return new Bones(b.torsoRot, body, b.torsoPos, b.armPivot, arm, b.bendAxis, b.bend, b.itemRot);
	}

	/** Change of the sword hand from {@code rest} to {@code cur}, limited by {@code g}. */
	public static Delta delta(Frame rest, Frame cur, Gains g) {
		float[] dq = FpPose.mul(cur.q, conj(rest.q));
		dq = gained(dq, g.rotGain, g.maxAngle);
		float[] mv = new float[3];
		for (int i = 0; i < 3; i++) {
			mv[i] = softLimit((cur.fist[i] - rest.fist[i]) * g.posGain[i], g.maxPos);
		}
		return new Delta(dq, mv);
	}

	/**
	 * The absolute first person pose: the stance (right hander layout) with the animation laid on it. For a left hander the delta (computed for the
	 * real left arm) is mirrored into the right hander layout first. Rotation: {@code q = dq * q_stance} (a world turn of the whole hand about the
	 * pivot), shift: stance shift plus the animation shift.
	 */
	public static FpPose pose(FpPose stance, Delta d, boolean leftHander) {
		float[] dq = d.q;
		float[] mv = d.move;
		if (leftHander) {
			dq = new float[] {dq[0], -dq[1], -dq[2], dq[3]};
			mv = new float[] {-mv[0], mv[1], mv[2]};
		}
		float[] q = FpPose.mul(dq, stance.quaternion());
		float[] e = eulerXyzDeg(q);
		return new FpPose(new float[] {stance.pos()[0] + mv[0], stance.pos()[1] + mv[1], stance.pos()[2] + mv[2]}, e);
	}

	/**
	 * {@code a} to {@code b} by {@code w} (0..1): the shift linearly, the turn by the shortest way between the two rotations (a plain blend of
	 * Euler angles passes wild values when a strike pose is a half turn away from the stance in the numbers, though not in the rotation).
	 */
	public static FpPose blend(FpPose a, FpPose b, float w) {
		if (w <= 0f) {
			return a;
		}
		if (w >= 1f) {
			return b;
		}
		float[] q = FpGeometry.slerp(a.quaternion(), b.quaternion(), w);
		float[] p = new float[3];
		for (int i = 0; i < 3; i++) {
			p[i] = a.pos()[i] + (b.pos()[i] - a.pos()[i]) * w;
		}
		return new FpPose(p, eulerXyzDeg(q));
	}

	// ------------------------------------------------------------------ small maths

	static float[] conj(float[] q) {
		return new float[] {-q[0], -q[1], -q[2], q[3]};
	}

	private static float[] add(float[] a, float[] b) {
		return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
	}

	/** Rotation vector gains on a unit quaternion, the angle capped at {@code maxDeg}. */
	static float[] gained(float[] q, float[] gain, float maxDeg) {
		float[] u = q;
		if (u[3] < 0f) {
			u = new float[] {-u[0], -u[1], -u[2], -u[3]};
		}
		float s = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
		if (s < 1e-6f) {
			return new float[] {0f, 0f, 0f, 1f};
		}
		float ang = 2f * (float) Math.atan2(s, u[3]);
		float[] rv = {u[0] / s * ang * gain[0], u[1] / s * ang * gain[1], u[2] / s * ang * gain[2]};
		float a = (float) Math.sqrt(rv[0] * rv[0] + rv[1] * rv[1] + rv[2] * rv[2]);
		if (a < 1e-6f) {
			return new float[] {0f, 0f, 0f, 1f};
		}
		float capped = softCap(a, (float) Math.toRadians(maxDeg));
		return FpPose.axisAngle(rv[0] / a, rv[1] / a, rv[2] / a, capped);
	}

	/** Soft symmetric limit of a shift: {@code v} itself up to 60 percent of {@code max}, then a smooth approach that never passes {@code max}. */
	static float softLimit(float v, float max) {
		return Math.signum(v) * softCap(Math.abs(v), max);
	}

	/** {@code a >= 0} untouched up to 60 percent of {@code max}, then {@code tanh} bent towards {@code max} (continuous with a continuous slope). */
	static float softCap(float a, float max) {
		if (max <= 1e-6f) {
			return a;
		}
		float knee = 0.6f * max;
		if (a <= knee) {
			return a;
		}
		float room = max - knee;
		return knee + room * (float) Math.tanh((a - knee) / room);
	}

	/** Euler angles (degrees x y z) of a unit quaternion for {@code Rx * Ry * Rz}, the order of {@link FpPose#rot()}. */
	public static float[] eulerXyzDeg(float[] q) {
		float x = q[0];
		float y = q[1];
		float z = q[2];
		float w = q[3];
		float r00 = 1f - 2f * (y * y + z * z);
		float r01 = 2f * (x * y - z * w);
		float r02 = 2f * (x * z + y * w);
		float r11 = 1f - 2f * (x * x + z * z);
		float r12 = 2f * (y * z - x * w);
		float r21 = 2f * (y * z + x * w);
		float r22 = 1f - 2f * (x * x + y * y);
		double b = Math.asin(Math.max(-1.0, Math.min(1.0, r02)));
		double a;
		double c;
		if (Math.abs(r02) < 0.999999f) {
			a = Math.atan2(-r12, r22);
			c = Math.atan2(-r01, r00);
		} else {
			// gimbal (yaw +-90): only a +- c is defined, put it all on x
			a = r02 > 0f ? Math.atan2(2f * (x * y + z * w), r11) : Math.atan2(r21, r11);
			c = 0.0;
		}
		return new float[] {(float) Math.toDegrees(a), (float) Math.toDegrees(b), (float) Math.toDegrees(c)};
	}

	// ------------------------------------------------------------------ the life of one strike

	/**
	 * Decision helper of one strike on the client: tracks whether the animation was seen and when it died, so the first person hand knows when to
	 * hand back to the stance. Pure state machine (the clock is passed in).
	 */
	public static final class Run {
		private boolean seen;
		private float quiet;
		private boolean over;

		/**
		 * One frame: {@code active} is true while the animation moves the sword (the item bone is driven or the change is above {@code eps}),
		 * {@code t} the seconds since the start. Returns the state: 0 running, 1 over (hand back to the stance), 2 never seen (use the fallback).
		 */
		public int step(boolean active, float t, float dt, float gate, float tail, float maxSec) {
			if (over) {
				return 1;
			}
			if (active) {
				seen = true;
				quiet = 0f;
			} else if (seen) {
				quiet += dt;
			}
			if (!seen && t > gate) {
				return 2;
			}
			if (seen && quiet >= tail || t > maxSec) {
				over = true;
				return 1;
			}
			return 0;
		}

		public boolean seen() {
			return seen;
		}
	}
}
