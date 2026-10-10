package dev.minebleach.reiatsutest.core.obj;

/**
 * Geometry of the draw-from-scabbard animation (step B3). Pure maths on item-model-space vertices (blocks, 1 m = 1 block,
 * origin of the model = {@code grip_hand} of the sealed meta at (0.5, 0.5, 0.5)).
 *
 * <p>The sealed hand meshes are baked shifted by {@code -holdOff} so the point the player HOLDS (on the saya, just above
 * the tsuba) sits at the item origin (the fist). Everything below works in that "baked frame". While the sword is drawn the
 * frame follows the SWORD (hilt + blade), so the hand ends on the hilt exactly where the shikai model puts it:
 * <ul>
 * <li>progress {@code p} 0 = sheathed, held by the saya; 1 = drawn (blade just clear of the saya).</li>
 * <li>{@link #sword}: shifted by {@code s(p) * holdOff} (the hand slides from the hold point to the hilt grip, over the first
 * {@code regrip} part of the draw).</li>
 * <li>{@link #saya}: the saya moves relative to the sword along the sori arc, i.e. rotates by {@code -d(p) / R} about the arc
 * centre (the exact inverse of "rotate the drawn sword about the arc centre by +d / R"), then follows the same hand shift;
 * in the last 20 percent it shrinks to nothing so that the swap to the shikai model shows no scabbard pop.</li>
 * </ul>
 * The arc axis is the model X axis (Blender X maps to model X); only that axis is supported.
 */
public final class DrawAnimation {
	public final float regrip;
	public final float overshoot;
	/** Vector from the hilt grip to the hold point, model space. */
	public final float[] holdOff;
	/** Arc centre in the baked frame (hold shift applied). */
	public final float[] centre;
	public final float radius;
	public final float clearTravel;
	/** Middle of the saya in the baked frame, the scale pivot of the final shrink. */
	public final float[] sayaCentre;

	public DrawAnimation(ItemManifest.DrawDef def, ObjMeta meta) {
		if (meta.bladeAxis == null) {
			throw new IllegalArgumentException("draw animation needs blade_axis in the saya model meta");
		}
		ObjMeta.BladeAxis b = meta.bladeAxis;
		float[] grip = meta.empties.getOrDefault("grip_hand", new float[3]);
		if (Math.abs(b.arcAxis()[0]) < 0.999f) {
			throw new IllegalArgumentException("draw animation supports the +X arc axis only");
		}
		regrip = def.regrip();
		overshoot = def.overshoot();
		float[] h = def.hold();
		holdOff = AxisMapper.blenderToObj(new float[] {h[0] - grip[0], h[1] - grip[1], h[2] - grip[2]});
		centre = sub(AxisMapper.blenderToModel(b.arcCenter(), grip), holdOff);
		radius = b.arcRadius();
		clearTravel = b.clearTravel();
		float[] mid = new float[3];
		for (int i = 0; i < 3; i++) {
			mid[i] = b.origin()[i] + b.direction()[i] * b.bladeLength() * 0.5f;
		}
		sayaCentre = sub(AxisMapper.blenderToModel(mid, grip), holdOff);
	}

	private static float[] sub(float[] a, float[] b) {
		return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	public static float smooth(float x) {
		float t = Math.max(0f, Math.min(1f, x));
		return t * t * (3f - 2f * t);
	}

	/** Hand slide from the hold point (0) to the hilt grip (1). */
	public float handFactor(float p) {
		return regrip <= 0f ? 1f : smooth(p / regrip);
	}

	/** Travel of the saya along the blade, metres. */
	public float travel(float p) {
		return overshoot * clearTravel * smooth(p);
	}

	/** Arc angle, radians. */
	public float angle(float p) {
		return travel(p) / radius;
	}

	/** Scale of the saya (1 until 80 percent, then shrinks to 0). */
	public float sayaScale(float p) {
		return 1f - smooth((p - 0.8f) / 0.2f);
	}

	/** Sword vertex (hilt + blade) at progress p; {@code v} is a baked-frame position, modified in place. */
	public void sword(float[] v, float p) {
		float s = handFactor(p);
		v[0] += s * holdOff[0];
		v[1] += s * holdOff[1];
		v[2] += s * holdOff[2];
	}

	/** Saya vertex at progress p (baked-frame position in, in place). */
	public void saya(float[] v, float p) {
		float a = -angle(p);
		rotateAboutArc(v, a);
		float k = sayaScale(p);
		if (k != 1f) {
			float[] c = sayaCentre.clone();
			rotateAboutArc(c, a);
			for (int i = 0; i < 3; i++) {
				v[i] = c[i] + (v[i] - c[i]) * k;
			}
		}
		sword(v, p);
	}

	/** Saya normal (rotation only), in place. */
	public void sayaNormal(float[] n, float p) {
		float a = -angle(p);
		float cos = (float) Math.cos(a);
		float sin = (float) Math.sin(a);
		float y = n[1];
		float z = n[2];
		n[1] = y * cos - z * sin;
		n[2] = y * sin + z * cos;
	}

	/** Rotation about the arc axis (model +X) through {@link #centre}. */
	private void rotateAboutArc(float[] v, float a) {
		float y = v[1] - centre[1];
		float z = v[2] - centre[2];
		float cos = (float) Math.cos(a);
		float sin = (float) Math.sin(a);
		v[1] = centre[1] + y * cos - z * sin;
		v[2] = centre[2] + y * sin + z * cos;
	}
}
