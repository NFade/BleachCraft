package dev.minebleach.reiatsutest.core.obj;

/**
 * Coordinate conventions (ADR section 5). Blender (Z-up) to OBJ with forward=-Z, up=Y:
 * (x, y, z) -> (x, z, -y), a proper rotation (det +1) so winding is preserved.
 * OBJ -> Minecraft item model space: p_mc = (p_obj - grip_obj) + (0.5, 0.5, 0.5); v_mc = 1 - v.
 */
public final class AxisMapper {
	private AxisMapper() {
	}

	public static float[] blenderToObj(float x, float y, float z) {
		return new float[] {x, z, -y};
	}

	public static float[] blenderToObj(float[] p) {
		return blenderToObj(p[0], p[1], p[2]);
	}

	public static float[] objToBlender(float x, float y, float z) {
		return new float[] {x, -z, y};
	}

	/** OBJ position to Minecraft item model space (blocks, block centre = grip). */
	public static float[] objToModel(float[] pObj, float[] gripObj) {
		return new float[] {
			pObj[0] - gripObj[0] + 0.5f,
			pObj[1] - gripObj[1] + 0.5f,
			pObj[2] - gripObj[2] + 0.5f
		};
	}

	/** Blender position straight to model space. */
	public static float[] blenderToModel(float[] pBlender, float[] gripBlender) {
		return objToModel(blenderToObj(pBlender), blenderToObj(gripBlender));
	}

	/** OBJ V points up, Minecraft V points down. */
	public static float flipV(float v) {
		return 1.0f - v;
	}
}
