package dev.minebleach.reiatsutest.core.obj;

/**
 * Pose of the SWORD during the first person draw / sheathe (B4 step 2). Pure maths in item model space (blocks, the model
 * origin (0.5, 0.5, 0.5) = {@code grip_hand}, +Y = blade axis, as {@link AxisMapper} maps it).
 *
 * <p>Rig: the scabbard is in the left hand and never moves; it sits at the "stow" pose {@code Z} (a rigid transform of the
 * item model space; the sheathed sword is the drawn sword transformed by {@code Z}, so blade and saya coincide). The sword
 * has two phases:
 * <ul>
 * <li>{@code p in [0, slideEnd]}: it slides out of the saya ALONG THE SORI ARC: {@code Z} after a rotation about the arc axis
 * (model +X) through the arc centre of the blade ({@code blade_axis.sori_arc}, centre 3.03 m) by {@code travel / radius}.
 * Never a straight pull: a straight pull clips the curved blade through the saya wall after 0.1 m (LOG, saya export).</li>
 * <li>{@code p in [slideEnd, 1]}: the blade is clear; the sword swings from that pose to the held pose (identity: the item
 * model with its display transform) with a rigid blend (slerp of the rotation, lerp of the translation, smoothstep).</li>
 * </ul>
 * A {@link Rigid} maps {@code v -> R(q) v + t}.
 */
public final class DrawRig {
	/** Rotation quaternion (x, y, z, w) and translation. */
	public record Rigid(float[] q, float[] t) {
		public static final Rigid IDENTITY = new Rigid(new float[] {0, 0, 0, 1}, new float[3]);

		public float[] apply(float[] v) {
			float[] r = rotate(q, v);
			return new float[] {r[0] + t[0], r[1] + t[1], r[2] + t[2]};
		}

		public float[] rotateNormal(float[] n) {
			return rotate(q, n);
		}

		/** this after other: v -> this(other(v)). */
		public Rigid after(Rigid other) {
			float[] rt = rotate(q, other.t);
			return new Rigid(mul(q, other.q), new float[] {rt[0] + t[0], rt[1] + t[1], rt[2] + t[2]});
		}
	}

	public final float slideEnd;
	/** Arc centre (model space) and radius, metres = blocks. */
	public final float centreY;
	public final float centreZ;
	public final float radius;
	/** Travel along the blade after which the whole blade has left the saya (with a small margin). */
	public final float slideTravel;
	public final Rigid stow;
	public final float[] origin = {0.5f, 0.5f, 0.5f};

	public DrawRig(ObjMeta saya, Rigid stow, float slideEnd) {
		if (saya.bladeAxis == null) {
			throw new IllegalArgumentException("draw rig needs blade_axis in the saya model meta");
		}
		ObjMeta.BladeAxis b = saya.bladeAxis;
		if (Math.abs(b.arcAxis()[0]) < 0.999f) {
			throw new IllegalArgumentException("draw rig supports the +X arc axis only");
		}
		float[] grip = saya.empties.getOrDefault("grip_hand", new float[3]);
		float[] c = AxisMapper.blenderToModel(b.arcCenter(), grip);
		this.centreY = c[1];
		this.centreZ = c[2];
		this.radius = b.arcRadius();
		this.slideTravel = b.clearTravel() * 1.04f; // 4 percent margin: the kissaki is clear of the mouth at the end of the slide
		this.stow = stow;
		this.slideEnd = slideEnd;
	}

	/** Test constructor with explicit arc numbers. */
	public DrawRig(float centreY, float centreZ, float radius, float slideTravel, Rigid stow, float slideEnd) {
		this.centreY = centreY;
		this.centreZ = centreZ;
		this.radius = radius;
		this.slideTravel = slideTravel;
		this.stow = stow;
		this.slideEnd = slideEnd;
	}

	public static float smooth(float x) {
		float t = Math.max(0f, Math.min(1f, x));
		return t * t * (3f - 2f * t);
	}

	/** Rotation by {@code a} radians about the arc axis (model +X) through the arc centre. */
	public Rigid arc(float a) {
		float s = (float) Math.sin(a / 2.0);
		float c = (float) Math.cos(a / 2.0);
		float[] q = {s, 0, 0, c};
		float[] ctr = {0, centreY, centreZ};
		float[] rc = rotate(q, ctr);
		return new Rigid(q, new float[] {ctr[0] - rc[0], ctr[1] - rc[1], ctr[2] - rc[2]});
	}

	/** Slide arc angle at the end of the slide phase. */
	public float clearAngle() {
		return slideTravel / radius;
	}

	/** Pose of the sword at draw progress p: 0 sheathed (= stow), 1 held (identity). */
	public Rigid at(float p) {
		if (p <= 0f) {
			return stow;
		}
		if (p >= 1f) {
			return Rigid.IDENTITY;
		}
		if (p <= slideEnd) {
			return stow.after(arc(clearAngle() * (p / slideEnd)));
		}
		Rigid clear = stow.after(arc(clearAngle()));
		float s = smooth((p - slideEnd) / (1f - slideEnd));
		float[] q = slerp(clear.q, Rigid.IDENTITY.q, s);
		float[] t = {clear.t[0] * (1 - s), clear.t[1] * (1 - s), clear.t[2] * (1 - s)};
		return new Rigid(q, t);
	}

	/** Where the grip point (the fist) is at progress p, model space; the held pose has it at the origin (0.5, 0.5, 0.5). */
	public float[] gripAt(float p) {
		return at(p).apply(origin);
	}

	// ------------------------------------------------------------------ quaternion helpers (x, y, z, w)

	public static float[] mul(float[] a, float[] b) {
		return new float[] {
			a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
			a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
			a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
			a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	public static float[] conj(float[] q) {
		return new float[] {-q[0], -q[1], -q[2], q[3]};
	}

	/** Rotates v by the unit quaternion q. */
	public static float[] rotate(float[] q, float[] v) {
		float[] p = {v[0], v[1], v[2], 0};
		float[] r = mul(mul(q, p), conj(q));
		return new float[] {r[0], r[1], r[2]};
	}

	public static float[] slerp(float[] a, float[] b, float s) {
		float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
		float[] bb = b;
		if (dot < 0f) {
			dot = -dot;
			bb = new float[] {-b[0], -b[1], -b[2], -b[3]};
		}
		float ka;
		float kb;
		if (dot > 0.9995f) {
			ka = 1 - s;
			kb = s;
		} else {
			float th = (float) Math.acos(dot);
			float sn = (float) Math.sin(th);
			ka = (float) Math.sin((1 - s) * th) / sn;
			kb = (float) Math.sin(s * th) / sn;
		}
		float[] r = {ka * a[0] + kb * bb[0], ka * a[1] + kb * bb[1], ka * a[2] + kb * bb[2], ka * a[3] + kb * bb[3]};
		float n = (float) Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + r[3] * r[3]);
		return new float[] {r[0] / n, r[1] / n, r[2] / n, r[3] / n};
	}

	/** Quaternion of a rotation by {@code angle} radians about the unit axis. */
	public static float[] axisAngle(float x, float y, float z, float angle) {
		float s = (float) Math.sin(angle / 2.0);
		return new float[] {x * s, y * s, z * s, (float) Math.cos(angle / 2.0)};
	}
}
