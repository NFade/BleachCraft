package dev.minebleach.reiatsutest.core.combat;

/**
 * Third person pose of an Uryu player for one frame (pure): the values a playerAnimator layer hands to the model parts, from the sampled
 * animations of {@link UryuBowFlow}. Blends the current and the previous animation, follows the camera pitch with the aimed arms, adds the
 * breathing and the tremor, mirrors for a left hander and fades the whole pose in and out against what the model had (the vanilla pose).
 *
 * <p>Values are game axes like {@link UryuPoseTrack}. Parts the animation does not key keep the incoming value (the loose left arm of the idle pose
 * swings with the walk, the legs walk). The head pitch of an animation is a NOD added to the camera pitch (the camera yaw and the camera
 * pitch keep driving the head).
 */
public final class UryuTpPose {
	private static final double DEG = Math.PI / 180.0;

	/** One animation at one tick. */
	public record Layer(UryuPoseTrack track, UryuAnims.Meta meta, double tick) {
	}

	/**
	 * @param cur the current animation
	 * @param prev the animation blended out (null: none)
	 * @param xfade 0..1, share of {@code cur} against {@code prev}
	 * @param weight 0..1, share of the whole pose against the incoming value
	 * @param mirror a left hander: the pose is the mirror image (arms and legs swap, yaw and roll change sign)
	 * @param lookPitch camera pitch in radians, above 0 looks down (vanilla)
	 * @param time seconds, a free running clock for the breathing and the tremor
	 * @param breath amplitude factor of the breathing (1; 0.5 under reduce motion; 0 off)
	 * @param tremor tremor on (false under reduce motion)
	 */
	public record Frame(Layer cur, Layer prev, double xfade, double weight, boolean mirror, double lookPitch, double time, double breath, boolean tremor) {
	}

	private UryuTpPose() {
	}

	private static boolean isArm(String part) {
		return "rightArm".equals(part) || "leftArm".equals(part);
	}

	/** Rotation {@code pitch, yaw, roll} (radians) of a model part this frame, {@code value0} the incoming value. */
	public static double[] rotation(Frame f, String part, double[] value0) {
		if (f.weight <= 0.0 || f.cur == null) {
			return value0;
		}
		String data = f.mirror ? UryuPoseMath.mirrorPart(part) : part;
		double[] in = f.mirror ? UryuPoseMath.mirrorEuler(value0) : value0;
		double[] out = layerRotation(f.cur, f, data, in);
		if (f.prev != null && f.xfade < 1.0) {
			double[] p = layerRotation(f.prev, f, data, in);
			out = UryuPoseMath.lerp(p, out, f.xfade);
		}
		if (f.weight < 1.0) {
			out = UryuPoseMath.lerp(in, out, f.weight);
		}
		return f.mirror ? UryuPoseMath.mirrorEuler(out) : out;
	}

	private static double[] layerRotation(Layer l, Frame f, String data, double[] in) {
		double[] k = l.track.rotation(data, l.tick);
		boolean head = "head".equals(data);
		double[] out = in.clone();
		boolean keyed = false;
		for (int i = 0; i < 3; i++) {
			if (!Double.isNaN(k[i])) {
				out[i] = head ? in[i] + k[i] : k[i];
				keyed = true;
			}
		}
		if (!keyed) {
			return in;
		}
		if (isArm(data) && l.meta.look() > 0.0) {
			out = UryuPoseMath.aimed(out, f.lookPitch * l.meta.look());
		}
		double[] m = micro(l.meta.anim(), data, l.tick, f.time, f.breath, f.tremor);
		if (m != null) {
			out[0] += m[0];
			out[1] += m[1];
			out[2] += m[2];
		}
		return out;
	}

	/**
	 * Shift of the part pivot from the incoming pivot in pixels: {@code value0} plus the sampled shift (the shoulder pivots follow the turned chest).
	 * Returns {@code value0} itself when no animation of this frame moves the part.
	 */
	public static double[] position(Frame f, String part, double[] value0) {
		if (f.weight <= 0.0 || f.cur == null) {
			return value0;
		}
		String data = f.mirror ? UryuPoseMath.mirrorPart(part) : part;
		boolean any = f.cur.track.keysPosition(data) || f.prev != null && f.xfade < 1.0 && f.prev.track.keysPosition(data);
		if (!any) {
			return value0;
		}
		double[] d = layerShift(f.cur, data);
		if (f.prev != null && f.xfade < 1.0) {
			double[] p = layerShift(f.prev, data);
			for (int i = 0; i < 3; i++) {
				d[i] = p[i] + (d[i] - p[i]) * f.xfade;
			}
		}
		double k = f.weight;
		double mx = f.mirror ? -1.0 : 1.0;
		return new double[] {value0[0] + mx * d[0] * k, value0[1] + d[1] * k, value0[2] + d[2] * k};
	}

	private static double[] layerShift(Layer l, String data) {
		double[] s = l.track.position(data, l.tick);
		double[] out = new double[3];
		for (int i = 0; i < 3; i++) {
			out[i] = Double.isNaN(s[i]) ? 0.0 : s[i];
		}
		return out;
	}

	/** Final rotation of the bow arm (right hander layout) this frame: what {@link UryuPoseMath#bowCorrection} needs. */
	public static double[] bowArm(Frame f) {
		Frame right = f.mirror ? new Frame(f.cur, f.prev, f.xfade, f.weight, false, f.lookPitch, f.time, f.breath, f.tremor) : f;
		return rotation(right, "rightArm", UryuPoseMath.IDLE_ARM.clone());
	}

	/** Weight 0..1 of the bow axis following the bow arm this frame (the blend of the two animations; 0 when the pose is faded out). */
	public static double axis(Frame f) {
		if (f.cur == null) {
			return 0.0;
		}
		double a = f.cur.meta.axis();
		if (f.prev != null && f.xfade < 1.0) {
			a = f.prev.meta.axis() + (a - f.prev.meta.axis()) * f.xfade;
		}
		return a * f.weight;
	}

	/**
	 * Breathing and tremor on top of the keys, radians {@code pitch, yaw, roll}, or null: the idle breathes (the torso 0.8 degrees and the bow arm
	 * 1.2 degrees at 0.22 Hz), the full draw trembles on the string arm (0.6 degrees at 9 Hz from tick 14), the raised bow of the Licht Regen shakes
	 * (0.8 degrees at 10 Hz while it is up) and the slow draw of the Sternenstaub trembles (1.0 degree at 12 Hz).
	 */
	public static double[] micro(String anim, String data, double tick, double time, double breath, boolean tremor) {
		switch (anim) {
			case UryuAnims.IDLE -> {
				if (breath <= 0.0 || !("torso".equals(data) || "rightArm".equals(data))) {
					return null;
				}
				double a = ("torso".equals(data) ? 0.8 : 1.2) * DEG * breath;
				return new double[] {a * Math.sin(2.0 * Math.PI * 0.22 * time), 0.0, 0.0};
			}
			case UryuAnims.DRAW -> {
				if (!tremor || !"leftArm".equals(data)) {
					return null;
				}
				double ramp = ramp(tick, 14.0, 20.0, 1e9, 1e9);
				return shake(0.6, 9.0, time, ramp);
			}
			case "licht_regen" -> {
				if (!tremor || !isArm(data)) {
					return null;
				}
				return shake(0.8, 10.0, time, ramp(tick, 13.0, 17.0, 68.0, 72.0));
			}
			case "sternenstaub" -> {
				if (!tremor || !isArm(data)) {
					return null;
				}
				return shake(1.0, 12.0, time, ramp(tick, 12.0, 14.0, 15.0, 17.0));
			}
			default -> {
				return null;
			}
		}
	}

	private static double[] shake(double deg, double hz, double time, double ramp) {
		if (ramp <= 0.0) {
			return null;
		}
		double a = deg * DEG * ramp;
		return new double[] {a * Math.sin(2.0 * Math.PI * hz * time), a * Math.cos(2.0 * Math.PI * hz * time * 1.31), 0.0};
	}

	/** 0 before {@code a}, up to 1 at {@code b}, 1 until {@code c}, down to 0 at {@code d}. */
	private static double ramp(double t, double a, double b, double c, double d) {
		if (t <= a || t >= d) {
			return 0.0;
		}
		if (t < b) {
			return (t - a) / (b - a);
		}
		if (t <= c) {
			return 1.0;
		}
		return (d - t) / (d - c);
	}
}
