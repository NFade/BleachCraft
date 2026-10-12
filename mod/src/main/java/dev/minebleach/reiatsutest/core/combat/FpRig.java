package dev.minebleach.reiatsutest.core.combat;

/**
 * Where the parts of the held zanpakuto are in the hand frame of the first person item renderer, right hander layout (x to the right,
 * y up, z towards the camera, origin = the resting main hand frame). Pure maths: the display transform of the item model
 * ({@code firstperson_righthand}: rotation Rx*Ry*Rz in degrees, translation in blocks, uniform scale) plus the held lift. The item
 * model space is the usual one of the zanpakuto OBJ items: the grip at (0.5, 0.5, 0.5), +Y along the blade, one unit = one metre of
 * the model (about 2.4 blocks in the hand frame).
 *
 * <p>Used to lay the stance against the screen ({@link FpGeometry}), by the reach limits of the gestures ({@link FpClamp}) and by the
 * tests; the client builds it from the real display transform of the item.
 */
public final class FpRig {
	/** The display transform of the zanpakuto items (sode_no_shirayuki_display.json, senbonzakura_display.json) and the held lift of the manifests. */
	public static final FpRig DEFAULT = new FpRig(new float[] {-172.0f, 1.0f, -173.1f}, new float[] {2.14f / 16f, -0.29f / 16f, -5f / 16f}, 2.4f,
			new float[] {0f, 0.06f, 0f});
	/** Model space y of the underside of the tsuba (obj y 0.24 of the drawn sword, grip at 0.19). */
	public static final float TSUBA_Y = 0.55f;
	/** Model space y of the pommel (obj y 0). */
	public static final float POMMEL_Y = 0.31f;
	/** Model space y of the tip of the drawn sword (obj y 0.9785). */
	public static final float TIP_Y = 1.2885f;
	/** Radius of the tsuba in blocks (0.036 of the model, scale 2.4). */
	public static final float TSUBA_RADIUS = 0.036f * 2.4f;

	private final float[] rot;
	private final float[] trans;
	private final float scale;
	private final float[] lift;
	private final float[] q;

	/** {@code rotDeg} x y z of the display transform, {@code trans} in blocks (already divided by 16), uniform {@code scale}, hand frame {@code lift}. */
	public FpRig(float[] rotDeg, float[] trans, float scale, float[] lift) {
		this.rot = rotDeg.clone();
		this.trans = trans.clone();
		this.scale = scale;
		this.lift = lift.clone();
		this.q = FpPose.of(0f, 0f, 0f, rotDeg[0], rotDeg[1], rotDeg[2]).quaternion();
	}

	public float scale() {
		return scale;
	}

	public float[] lift() {
		return lift.clone();
	}

	/** Model space point to the hand frame (display transform, then the lift of the held sword). */
	public float[] hand(float x, float y, float z) {
		float[] v = FpPose.rotate(q, new float[] {(x - 0.5f) * scale, (y - 0.5f) * scale, (z - 0.5f) * scale});
		return new float[] {trans[0] + v[0] + lift[0], trans[1] + v[1] + lift[1], trans[2] + v[2] + lift[2]};
	}

	/** The point of the blade axis {@code s} model units from the grip (negative: towards the pommel), hand frame. */
	public float[] along(float s) {
		return hand(0.5f, 0.5f + s, 0.5f);
	}

	/** Unit direction of the blade axis (grip to tip) in the hand frame. */
	public float[] axis() {
		float[] a = along(0f);
		float[] b = along(1f);
		float x = b[0] - a[0];
		float y = b[1] - a[1];
		float z = b[2] - a[2];
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return new float[] {x / l, y / l, z / l};
	}

	/** Sample points of the sword along the blade axis for the near plane checks: pommel, grip, tsuba, then the blade to the tip. */
	public float[][] bladePoints() {
		float[][] r = new float[7][];
		float[] ys = {POMMEL_Y, 0.5f, TSUBA_Y, 0.8f, 1.0f, 1.15f, TIP_Y};
		for (int i = 0; i < ys.length; i++) {
			r[i] = hand(0.5f, ys[i], 0.5f);
		}
		return r;
	}

	@Override
	public String toString() {
		return "FpRig[rot=" + java.util.Arrays.toString(rot) + ", scale=" + scale + "]";
	}
}
